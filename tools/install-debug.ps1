$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$lab = Join-Path $root 'labtarget\build\outputs\apk\debug\labtarget-debug.apk'

if (-not (Test-Path $apk)) { throw "SentinelDroid APK not found. Run .\build.bat first." }

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    $sdk = $env:ANDROID_SDK_ROOT
    if (-not $sdk) { $sdk = $env:ANDROID_HOME }
    if (-not $sdk) {
        $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path $defaultSdk) { $sdk = $defaultSdk }
    }
    $adbPath = if ($sdk) { Join-Path $sdk 'platform-tools\adb.exe' } else { $null }
    if ($adbPath -and (Test-Path $adbPath)) {
        $adb = $adbPath
    }
}
if (-not $adb) { throw "adb was not found. Android Studio's SDK platform-tools are required." }

& $adb devices
Write-Host "Installing SentinelDroid..."
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "SentinelDroid installation failed." }

if (Test-Path $lab) {
    Write-Host "Installing lab target..."
    & $adb install -r $lab
    if ($LASTEXITCODE -ne 0) { throw "Lab target installation failed." }
}

Write-Host "Starting SentinelDroid..."
& $adb shell monkey -p com.sentineldroid 1 | Out-Host
Write-Host "Install complete."
