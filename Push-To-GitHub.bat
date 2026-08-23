@echo off
chcp 65001 >nul
title Push zu GitHub - Event-PVP-Plugin v1.1.0

cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Push-To-GitHub.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] Das Skript wurde mit Fehlercode %ERRORLEVEL% beendet.
    pause
)
