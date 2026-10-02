param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root

Write-Host "SentinelDroid build root: $root"

function Find-AndroidSdk {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:ANDROID_SDK_ROOT) { $candidates.Add($env:ANDROID_SDK_ROOT) }
    if ($env:ANDROID_HOME) { $candidates.Add($env:ANDROID_HOME) }

    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path $localProperties) {
        foreach ($line in Get-Content $localProperties -ErrorAction SilentlyContinue) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+?)\s*$') {
                $value = $matches[1].Trim()
                if ($value) {
                    $candidates.Add($value.Replace('\\', '\'))
                }
                break
            }
        }
    }

    $localAppData = $env:LOCALAPPDATA
    $userProfile = $env:USERPROFILE
    $programFiles = $env:ProgramFiles

    if ($localAppData) {
        $candidates.Add((Join-Path $localAppData 'Android\Sdk'))
        $candidates.Add((Join-Path $localAppData 'Android\sdk'))
    }
    if ($userProfile) {
        $candidates.Add((Join-Path $userProfile 'Android\Sdk'))
        $candidates.Add((Join-Path $userProfile 'Android\sdk'))
    }
    if ($programFiles) {
        $candidates.Add((Join-Path $programFiles 'Android\Sdk'))
        $candidates.Add((Join-Path $programFiles 'Android\sdk'))
    }

    $adb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adb) {
        $candidates.Add((Split-Path (Split-Path $adb.Source -Parent) -Parent))
    }

    $sdkManager = Get-Command sdkmanager.bat -ErrorAction SilentlyContinue
    if ($sdkManager) {
        $p = Split-Path $sdkManager.Source -Parent
        if ((Split-Path $p -Leaf) -eq 'bin') { $p = Split-Path $p -Parent }
        if ((Split-Path $p -Leaf) -eq 'cmdline-tools') { $p = Split-Path $p -Parent }
        if ((Split-Path $p -Leaf) -eq 'latest') { $p = Split-Path $p -Parent }
        $candidates.Add($p)
    }

    # Android Studio/IDEA may persist the SDK path in workspace.xml, other.xml,
    # or jdk.table.xml rather than in the project. Search only the usual config roots.
    $configRoots = @()
    if ($env:APPDATA) {
        $configRoots += (Join-Path $env:APPDATA 'Google')
        $configRoots += (Join-Path $env:APPDATA 'JetBrains')
    }
    if ($env:LOCALAPPDATA) {
        $configRoots += (Join-Path $env:LOCALAPPDATA 'Google')
        $configRoots += (Join-Path $env:LOCALAPPDATA 'JetBrains')
    }

    foreach ($configRoot in $configRoots) {
        if (-not (Test-Path $configRoot)) { continue }
        try {
            $files = Get-ChildItem $configRoot -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.Name -in @('workspace.xml', 'other.xml', 'jdk.table.xml') -and
                    $_.FullName -match '(?i)AndroidStudio|AndroidStudioPreview|JetBrains'
                } |
                Select-Object -First 200

            foreach ($file in $files) {
                $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
                if (-not $content) { continue }

                # Look for values assigned to sdk-related settings.
                $matches = [regex]::Matches(
                    $content,
                    '(?i)(?:android\.sdk\.path|sdk\.dir|Android SDK)[^>]{0,500}?value="([A-Za-z]:\\[^"]+)"'
                )

                foreach ($match in $matches) {
                    $candidates.Add($match.Groups[1].Value)
                }

                # Some IDE state stores the path as plain text without a nearby key.
                $pathMatches = [regex]::Matches(
                    $content,
                    '(?i)[A-Za-z]:\\[^"<>]*\\Android\\(?:Sdk|sdk)'
                )

                foreach ($match in $pathMatches) {
                    $candidates.Add($match.Value)
                }
            }
        } catch {
            # Configuration discovery is best-effort.
        }
    }

    # Common SDK roots on secondary drives. We only inspect shallow, well-known names.
    try {
        foreach ($drive in Get-PSDrive -PSProvider FileSystem) {
            $rootPath = $drive.Root
            foreach ($name in @('Android\Sdk', 'Android\sdk', 'Sdk', 'sdk')) {
                $candidates.Add((Join-Path $rootPath $name))
            }
        }
    } catch {
        # Continue with the collected candidates.
    }

    $seen = @{}
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        try {
            $resolved = (Resolve-Path $candidate -ErrorAction Stop).Path
            if ($seen.ContainsKey($resolved)) { continue }
            $seen[$resolved] = $true

            $androidJar = Join-Path $resolved 'platforms\android-36\android.jar'
            $buildToolsRoot = Join-Path $resolved 'build-tools'

            # API 36 is the required platform for this project. Build Tools
            # patch versions vary across Android Studio installations, so
            # discovery must not require one exact directory such as 36.0.0.
            # Gradle/AGP will select an installed compatible Build Tools version.
            if (Test-Path $androidJar) {
                return $resolved
            }
        } catch {
            # Try the next candidate.
        }
    }

    return $null
}

# ------------------------------------------------------------
# Java
# ------------------------------------------------------------

$java = Get-Command java.exe -ErrorAction SilentlyContinue

if (-not $java) {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\java.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\java.exe')
    )

    $found = $candidates |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1

    if ($found) {
        $env:JAVA_HOME = Split-Path (Split-Path $found -Parent) -Parent
        $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
        $java = Get-Command java.exe -ErrorAction SilentlyContinue
    }
}

if (-not $java) {
    throw "Java was not found. Install/use the JDK bundled with Android Studio (JDK 17)."
}

# java -version writes to stderr. Calling java through cmd prevents
# PowerShell from converting the version output into NativeCommandError.
$javaRaw = cmd.exe /c "java -version 2>&1"
$javaVersion = ($javaRaw | Select-Object -First 1).ToString().Trim()
Write-Host "Java: $javaVersion"

# ------------------------------------------------------------
# Android SDK
# ------------------------------------------------------------

$sdk = Find-AndroidSdk

if (-not $sdk) {
    throw "Android SDK API 36 was not found. Set ANDROID_SDK_ROOT/ANDROID_HOME, or create local.properties with sdk.dir=<SDK path>."
}

$sdkForProperties = $sdk.Replace('\', '\\')
Set-Content `
    -Path (Join-Path $root 'local.properties') `
    -Value ("sdk.dir=" + $sdkForProperties) `
    -Encoding ASCII

Write-Host "Android SDK: $sdk"
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk

$buildToolsRoot = Join-Path $sdk 'build-tools'
$hasBuildTools = $false
if (Test-Path $buildToolsRoot) {
    $hasBuildTools = @(
        Get-ChildItem $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                (Test-Path (Join-Path $_.FullName 'aapt2.exe')) -or
                (Test-Path (Join-Path $_.FullName 'd8.bat')) -or
                (Test-Path (Join-Path $_.FullName 'd8.exe'))
            }
    ).Count -gt 0
}
if (-not $hasBuildTools) {
    Write-Warning "Android SDK API 36 was found, but no usable Build Tools installation was detected. Android Gradle Plugin may report a clearer missing-build-tools error."
}

# ------------------------------------------------------------
# Gradle 9.6.0
# ------------------------------------------------------------

$gradleVersion = '9.6.0'
$toolsDir = Join-Path $root '.tools'
$gradleHome = Join-Path $toolsDir ("gradle-$gradleVersion")
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'

if (-not (Test-Path $gradleExe)) {
    $globalGradle = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($globalGradle) {
        $globalRaw = cmd.exe /c "`"$($globalGradle.Source)`" --version 2>&1"
        $globalMatch = $globalRaw | Select-String 'Gradle ([0-9.]+)' | Select-Object -First 1
        $globalVersion = if ($globalMatch) { $globalMatch.Matches.Groups[1].Value } else { '' }
        if ($globalVersion -eq $gradleVersion) {
            $gradleExe = $globalGradle.Source
        }
    }
}

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zip = Join-Path $toolsDir ("gradle-$gradleVersion-bin.zip")
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

    Write-Host "Downloading Gradle $gradleVersion..."
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force

    if (-not (Test-Path $gradleExe)) {
        throw "Gradle download/extraction failed: $gradleExe not found."
    }
}

# ------------------------------------------------------------
# Task forwarding
# ------------------------------------------------------------

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @(
        ':app:assembleDebug',
        ':labtarget:assembleDebug'
    )
} else {
    $normalized = New-Object System.Collections.Generic.List[string]

    foreach ($arg in $GradleArgs) {
        switch ($arg) {
            'assembleSniper' { $normalized.Add(':app:assembleSniper'); continue }
            'assembleRelease' { $normalized.Add(':app:assembleRelease'); continue }
            'assembleDebug' { $normalized.Add(':app:assembleDebug'); continue }
            'assemble' { $normalized.Add(':app:assemble'); continue }
            default { $normalized.Add($arg) }
        }
    }

    $GradleArgs = $normalized.ToArray()
}

Write-Host "Running Gradle: $($GradleArgs -join ' ')"

& $gradleExe --no-daemon @GradleArgs

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Gradle task(s) completed successfully."
