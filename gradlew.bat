@echo off
setlocal
REM SentinelDroid bootstrap wrapper. It forwards all arguments to the project build helper.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" %*
exit /b %ERRORLEVEL%
