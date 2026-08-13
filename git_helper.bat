@echo off
setlocal EnableDelayedExpansion

:: Git Helper Batch Script fuer Event-PVP-Plugin
:: Ermoeglicht schnelle und komfortable Ausfuehrung gaengiger Git-Befehle

:MENU
cls
echo ===============================================================================
echo                      GIT HELPER AND MANAGEMENT MENU
echo ===============================================================================
echo.
echo   [ STATUS UND ANZEIGE ]
echo     1. Git Status anzeigen                    (git status)
echo     2. Diff: Ungestagte Aenderungen          (git diff)
echo     3. Diff: Gestagte Aenderungen (Staged)   (git diff --staged)
echo     4. Commit-Historie kompakt (Graph)        (git log --graph --oneline -n 15)
echo     5. Commit-Historie detailliert            (git log -n 5)
echo.
echo   [ STAGING UND COMMITS ]
echo     6. Alle Aenderungen stagen               (git add .)
echo     7. Commit mit Nachricht erstellen        (git commit -m "...")
echo     8. Quick Commit: Alles adden + committen (git add . + git commit)
echo     9. Interaktives Staging                  (git add -i)
echo.
echo   [ BRANCHES UND SYNC ]
echo    10. Branches auflisten                    (git branch -a)
echo    11. Zu Branch wechseln / neu erstellen    (git checkout / switch)
echo    12. Git Pull (Aktualisieren von Remote)   (git pull)
echo    13. Git Push (Zu Remote hochladen)        (git push)
echo    14. Git Fetch (Prune)                     (git fetch --all --prune)
echo.
echo   [ BEREINIGUNG UND STASH ]
echo    15. Git Stash: Aenderungen merken/holen   (stash / pop / list)
echo    16. Pycache und *.pyc aus Git loeschen    (git rm --cached ...)
echo    17. Unstage: Alle Dateien unstagen        (git reset)
echo    18. Hard-Reset / Aenderungen verwerfen    (git restore .) [VORSICHT]
echo.
echo    0. Beenden
echo ===============================================================================
set "choice="
set /p choice="Bitte Option waehlen [0-18]: "

if not defined choice goto MENU
if "%choice%"=="0" goto END
if "%choice%"=="1" goto STATUS
if "%choice%"=="2" goto DIFF
if "%choice%"=="3" goto DIFF_STAGED
if "%choice%"=="4" goto LOG_GRAPH
if "%choice%"=="5" goto LOG_DETAILED
if "%choice%"=="6" goto ADD_ALL
if "%choice%"=="7" goto COMMIT
if "%choice%"=="8" goto QUICK_COMMIT
if "%choice%"=="9" goto ADD_INTERACTIVE
if "%choice%"=="10" goto BRANCH_LIST
if "%choice%"=="11" goto BRANCH_SWITCH
if "%choice%"=="12" goto PULL
if "%choice%"=="13" goto PUSH
if "%choice%"=="14" goto FETCH
if "%choice%"=="15" goto STASH_MENU
if "%choice%"=="16" goto CLEAN_PYCACHE
if "%choice%"=="17" goto RESET_STAGED
if "%choice%"=="18" goto RESTORE_WORKSPACE

echo.
echo [!] Ungueltige Eingabe! Bitte eine Zahl zwischen 0 und 18 waehlen.
ping -n 2 127.0.0.1 >nul
goto MENU

:: -----------------------------------------------------------------------------
:STATUS
cls
echo [ GIT STATUS ]
echo -------------------------------------------------------------------------------
git status
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:DIFF
cls
echo [ GIT DIFF (Ungestagte Aenderungen) ]
echo -------------------------------------------------------------------------------
git diff
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:DIFF_STAGED
cls
echo [ GIT DIFF --STAGED (Bereits zum Commit vorgemerkt) ]
echo -------------------------------------------------------------------------------
git diff --staged
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:LOG_GRAPH
cls
echo [ COMMIT HISTORIE (Graph) ]
echo -------------------------------------------------------------------------------
git log --graph --oneline --decorate -n 20
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:LOG_DETAILED
cls
echo [ DOKUMENTIERTE COMMIT HISTORIE (Letzte 5 Commits) ]
echo -------------------------------------------------------------------------------
git log -n 5
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:ADD_ALL
cls
echo [ GIT ADD . ]
echo -------------------------------------------------------------------------------
git add .
echo Alle aktuellen Aenderungen wurden gestagt.
echo.
git status --short
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:COMMIT
cls
echo [ GIT COMMIT ]
echo -------------------------------------------------------------------------------
git status --short
echo.
set "commit_msg="
set /p commit_msg="Commit-Nachricht eingeben (leer lassen zum Abbrechen): "
if not defined commit_msg (
    echo Vorgang abgebrochen.
    pause
    goto MENU
)
git commit -m "%commit_msg%"
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:QUICK_COMMIT
cls
echo [ QUICK COMMIT: ADD ALL + COMMIT ]
echo -------------------------------------------------------------------------------
git status --short
echo.
set "commit_msg="
set /p commit_msg="Commit-Nachricht eingeben (leer lassen zum Abbrechen): "
if not defined commit_msg (
    echo Vorgang abgebrochen.
    pause
    goto MENU
)
git add .
git commit -m "%commit_msg%"
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:ADD_INTERACTIVE
cls
echo [ INTERAKTIVES STAGING (git add -i) ]
echo -------------------------------------------------------------------------------
git add -i
goto MENU

:: -----------------------------------------------------------------------------
:BRANCH_LIST
cls
echo [ BRANCHES ]
echo -------------------------------------------------------------------------------
git branch -a -v
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:BRANCH_SWITCH
cls
echo [ BRANCH WECHSELN / ERSTELLEN ]
echo -------------------------------------------------------------------------------
git branch
echo.
echo  1. Zu existierendem Branch wechseln
echo  2. Neuen Branch erstellen und wechseln
echo  0. Zurueck
echo.
set "bchoice="
set /p bchoice="Auswahl: "
if "%bchoice%"=="1" (
    set "bname="
    set /p bname="Branch-Name eingeben: "
    if defined bname git checkout !bname!
)
if "%bchoice%"=="2" (
    set "bname="
    set /p bname="Neuer Branch-Name: "
    if defined bname git checkout -b !bname!
)
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:PULL
cls
echo [ GIT PULL ]
echo -------------------------------------------------------------------------------
git pull
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:PUSH
cls
echo [ GIT PUSH ]
echo -------------------------------------------------------------------------------
git push
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:FETCH
cls
echo [ GIT FETCH --ALL --PRUNE ]
echo -------------------------------------------------------------------------------
git fetch --all --prune
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:STASH_MENU
cls
echo [ GIT STASH ]
echo -------------------------------------------------------------------------------
echo  1. Stash anlegen (Aenderungen zwischenspeichern)
echo  2. Stash Pop (Letzten Zwischenstand wiederherstellen)
echo  3. Stash Liste anzeigen
echo  0. Zurueck
echo.
set "schoice="
set /p schoice="Auswahl: "
if "%schoice%"=="1" (
    set "smsg="
    set /p smsg="Optionale Stash-Beschreibung: "
    if not defined smsg (
        git stash
    ) else (
        git stash push -m "!smsg!"
    )
)
if "%schoice%"=="2" git stash pop
if "%schoice%"=="3" git stash list
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:CLEAN_PYCACHE
cls
echo [ PYCACHE UND BYTECODE AUS GIT ENTFERNEN ]
echo -------------------------------------------------------------------------------
echo Suche nach getrackten __pycache__ oder *.pyc Dateien...
powershell -Command "git ls-files | Select-String -Pattern '(pycache|\.pyc)' | ForEach-Object { git rm --cached $_.Line }"
echo.
echo Fertig! Vergiss nicht, diesen Schritt zu committen falls Dateien entfernt wurden.
echo -------------------------------------------------------------------------------
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:RESET_STAGED
cls
echo [ UNSTAGE ALL (git reset) ]
echo -------------------------------------------------------------------------------
git reset
echo Alle vorgemerkten Dateien wurden ungestagt (Dateiinhalte bleiben erhalten).
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:RESTORE_WORKSPACE
cls
echo [ HARD RESET / AENDERUNGEN VERWERFEN ]
echo -------------------------------------------------------------------------------
echo [!] WARNUNG: Dies verwirft ALLE ungestagten Aenderungen im gesamten Workspace!
echo.
set "confirm="
set /p confirm="Bist du SICHER? (tippe 'JA' ein zum Fortfahren): "
if /i "%confirm%"=="JA" (
    git restore .
    echo Ungestagte Aenderungen wurden verworfen.
) else (
    echo Vorgang abgebrochen.
)
echo.
pause
goto MENU

:: -----------------------------------------------------------------------------
:END
cls
echo Bis bald!
exit /b 0
