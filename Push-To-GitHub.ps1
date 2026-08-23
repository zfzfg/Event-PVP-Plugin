# Push-To-GitHub.ps1 - Event-PVP-Plugin v1.1.0
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
Set-Location $scriptDir

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   Event-PVP-Plugin - Push zu GitHub (Version 1.1.0)  " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "Arbeitsverzeichnis: $scriptDir" -ForegroundColor Gray

# 1. Prüfe Git-Installation
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "`n[FEHLER] 'git' wurde nicht im System-Pfad gefunden!" -ForegroundColor Red
    Write-Host "Bitte installiere Git oder fuege es zu den Umgebungsvariablen hinzu." -ForegroundColor Yellow
    Read-Host "`nDruecke Enter zum Beenden"
    exit 1
}

# 2. Remote prüfen / konfigurieren
$remoteUrl = "https://github.com/zfzfg/Event-PVP-Plugin.git"
$currentRemote = git remote get-url origin 2>$null
if (-not $currentRemote) {
    Write-Host "`n[INFO] Fuege Remote 'origin' hinzu: $remoteUrl" -ForegroundColor Yellow
    git remote add origin $remoteUrl
}

# Branch auf main sicherstellen
git branch -M main

# 3. Prüfe Git-Status
Write-Host "`n[1/4] Pruefe Git-Status..." -ForegroundColor Yellow
git status --short

# 4. Füge alle Änderungen hinzu
Write-Host "`n[2/4] Fuege alle Aenderungen hinzu (git add -A)..." -ForegroundColor Yellow
git add -A

# Prüfe, ob es Änderungen zum Committen gibt
$stagedDiff = git diff --cached --name-only
if (-not $stagedDiff) {
    Write-Host "[HINWEIS] Keine uncommitteten Aenderungen vorhanden. Arbeitsverzeichnis ist sauber." -ForegroundColor Green
} else {
    # 5. Commit erstellen
    Write-Host "`n[3/4] Erstelle Commit..." -ForegroundColor Yellow
    $defaultMessage = "Release v1.1.0: Full Purpur 26.2 migration, test suite & docs update"
    Write-Host "Standard-Commit-Nachricht: '$defaultMessage'" -ForegroundColor Gray
    
    $userMessage = Read-Host "Eigene Commit-Nachricht eingeben (oder Enter fuer Standard)"
    $commitMsg = if ([string]::IsNullOrWhiteSpace($userMessage)) { $defaultMessage } else { $userMessage }
    
    git commit -m $commitMsg
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[FEHLER] Commit konnte nicht erstellt werden!" -ForegroundColor Red
        Read-Host "`nDruecke Enter zum Beenden"
        exit $LASTEXITCODE
    }
    Write-Host "Commit erfolgreich erstellt: $commitMsg" -ForegroundColor Green
}

# 6. Push zu GitHub
Write-Host "`n[4/4] Pushe zum GitHub-Repository (origin main)..." -ForegroundColor Yellow
git push -u origin main

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[HINWEIS] Normaler Push fehlgeschlagen (evtl. nicht-uebereinstimmender Verlauf auf GitHub)." -ForegroundColor Yellow
    $forceChoice = Read-Host "Moechtest du ein Force-Push durchfuehren, um GitHub mit der Version 1.1.0 zu ueberschreiben? (j/n)"
    if ($forceChoice -eq "j" -or $forceChoice -eq "y" -or $forceChoice -eq "ja" -or $forceChoice -eq "yes") {
        Write-Host "Fuehre Force-Push aus (git push -u origin main --force)..." -ForegroundColor Yellow
        git push -u origin main --force
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n======================================================" -ForegroundColor Green
    Write-Host "   ERFOLG: Alle Aenderungen wurden gepusht!           " -ForegroundColor Green
    Write-Host "======================================================" -ForegroundColor Green
} else {
    Write-Host "`n======================================================" -ForegroundColor Red
    Write-Host "   FEHLER beim Pushen zu GitHub (Exit-Code: $LASTEXITCODE)! " -ForegroundColor Red
    Write-Host "   Bitte pruefe deine Internetverbindung & GitHub-Berechtigung." -ForegroundColor Red
    Write-Host "======================================================" -ForegroundColor Red
}

Write-Host "`nDruecke eine beliebige Taste zum Beenden..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
