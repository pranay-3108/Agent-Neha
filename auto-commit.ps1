Set-Location "C:\Users\prana\Downloads\Neha_SentinelDroid_0.6.0_UI_AGENT\neha06_repair5"

git add .

$changes = git status --porcelain

if ($changes) {
    $time = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    git commit -m "Auto update: $time"
    git push origin main
    Write-Host "Changes committed and pushed."
}
else {
    Write-Host "No changes found."
}