$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

$toolsDir = Join-Path $root '.tools'
$gradleVersion = '9.6.0'
$gradleHome = Join-Path $toolsDir ("gradle-$gradleVersion")
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'
$zip = Join-Path $toolsDir ("gradle-$gradleVersion-bin.zip")
$url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
if (-not (Test-Path $gradleExe)) {
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
}

& $gradleExe wrapper --gradle-version $gradleVersion
Write-Host "Gradle wrapper files generated in the project root."
