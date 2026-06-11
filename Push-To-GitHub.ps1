
Write-Host "=== Push zu GitHub ===" -ForegroundColor Cyan

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "Aktuelles Verzeichnis: $scriptPath" -ForegroundColor Gray

Write-Host "`n1. Prüfe Git-Status..." -ForegroundColor Yellow
git status

Write-Host "`n2. Füge alle Änderungen hinzu..." -ForegroundColor Yellow
git add .

Write-Host "`n3. Erstelle Commit..." -ForegroundColor Yellow
$commitMessage = "Update vom $(Get-Date -Format 'dd.MM.yyyy HH:mm')"
git commit -m $commitMessage

Write-Host "`n4. Pushe zu GitHub..." -ForegroundColor Yellow
git push -u origin main

Write-Host "`n=== Fertig! ===" -ForegroundColor Green

Write-Host "`nDrücke eine Taste zum Beenden..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

