$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
Write-Host "Lab server listens only on 127.0.0.1:8765. The emulator reaches it through 10.0.2.2:8765."
python .\tools\lab_server.py
