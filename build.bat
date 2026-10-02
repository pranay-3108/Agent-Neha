@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" %*
if errorlevel 1 (
  echo.
  echo SentinelDroid build failed.
  exit /b 1
)
echo.
echo SentinelDroid build completed.
