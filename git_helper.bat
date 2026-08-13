@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

:: =============================================================================
::  Git Helper fuer das Event-PVP-Plugin
::  ---------------------------------------------------------------------------
::  Ein Menue rund um die haeufigsten Git-Befehle. Jeder Punkt zeigt vor der
::  Ausfuehrung, WAS passiert und WELCHER Befehl dahinter steckt - damit man
::  nebenbei lernt, was man eigentlich tut.
::
::  Bedienung : Zahl eintippen + Enter. 0 beendet das Programm.
::  Sicherheit: Punkte mit [!] veraendern/verwerfen Daten und fragen nach.
:: =============================================================================

:: --- ANSI-Farbcodes vorbereiten (Windows 10/11 Terminal) ---------------------
for /f %%A in ('echo prompt $E ^| cmd') do set "ESC=%%A"
set "C_RESET=%ESC%[0m"
set "C_TITLE=%ESC%[1;36m"
set "C_GROUP=%ESC%[1;33m"
set "C_KEY=%ESC%[1;32m"
set "C_TEXT=%ESC%[0;37m"
set "C_DIM=%ESC%[0;90m"
set "C_WARN=%ESC%[1;31m"
set "C_OK=%ESC%[1;32m"
set "C_INFO=%ESC%[1;34m"

:: --- In den Ordner des Skripts wechseln, damit Git das richtige Repo trifft --
pushd "%~dp0"

:: --- Vorabpruefung: Ist Git da und sind wir ueberhaupt in einem Repository? --
where git >nul 2>&1
if errorlevel 1 (
    echo %C_WARN%[FEHLER^^!]%C_RESET% Git wurde nicht gefunden.
    echo Bitte Git installieren und sicherstellen, dass es in der PATH-Variable steht.
    pause
    popd
    exit /b 1
)
git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo %C_WARN%[FEHLER^^!]%C_RESET% "%CD%" ist kein Git-Repository.
    pause
    popd
    exit /b 1
)

:: =============================================================================
::  HAUPTMENUE
:: =============================================================================
:MENU
cls
call :HEADER

echo   %C_GROUP%[ 1 ] ANSCHAUEN  - nichts wird veraendert%C_RESET%
echo     %C_KEY%1%C_RESET%  Status              %C_TEXT%Welche Dateien sind geaendert / vorgemerkt?%C_RESET%   %C_DIM%git status%C_RESET%
echo     %C_KEY%2%C_RESET%  Diff (offen)        %C_TEXT%Zeilenweise Aenderungen, die NOCH NICHT vorgemerkt sind%C_RESET%
echo     %C_KEY%3%C_RESET%  Diff (vorgemerkt)   %C_TEXT%Was genau im naechsten Commit landen wuerde%C_RESET%
echo     %C_KEY%4%C_RESET%  Verlauf kompakt     %C_TEXT%Letzte 20 Commits als Baum-Grafik%C_RESET%
echo     %C_KEY%5%C_RESET%  Verlauf ausfuehrlich %C_TEXT%Letzte 5 Commits mit Autor, Datum, Text%C_RESET%
echo.
echo   %C_GROUP%[ 2 ] VORMERKEN UND SPEICHERN%C_RESET%
echo     %C_KEY%6%C_RESET%  Alles vormerken     %C_TEXT%Legt alle Aenderungen in die Staging-Area%C_RESET%
echo     %C_KEY%7%C_RESET%  Commit             %C_TEXT%Speichert NUR das Vorgemerkte als neuen Stand%C_RESET%
echo     %C_KEY%8%C_RESET%  Schnell-Commit      %C_TEXT%Vormerken + Commit in einem Schritt%C_RESET%
echo     %C_KEY%9%C_RESET%  Auswahl vormerken   %C_TEXT%Git fragt Datei fuer Datei (fuer saubere Commits)%C_RESET%
echo.
echo   %C_GROUP%[ 3 ] BRANCHES UND SERVER%C_RESET%
echo    %C_KEY%10%C_RESET%  Branches anzeigen   %C_TEXT%Alle lokalen und entfernten Zweige%C_RESET%
echo    %C_KEY%11%C_RESET%  Branch wechseln     %C_TEXT%Umschalten oder neuen Zweig anlegen%C_RESET%
echo    %C_KEY%12%C_RESET%  Pull                %C_TEXT%Neuigkeiten vom Server holen und einbauen%C_RESET%
echo    %C_KEY%13%C_RESET%  Push                %C_TEXT%Eigene Commits zum Server hochladen%C_RESET%
echo    %C_KEY%14%C_RESET%  Fetch + Prune       %C_TEXT%Serverstand abgleichen, geloeschte Branches aufraeumen%C_RESET%
echo.
echo   %C_GROUP%[ 4 ] AUFRAEUMEN%C_RESET%
echo    %C_KEY%15%C_RESET%  Stash               %C_TEXT%Aenderungen kurz zur Seite legen und spaeter zurueckholen%C_RESET%
echo    %C_KEY%16%C_RESET%  Pycache entfernen   %C_TEXT%Versehentlich getrackte __pycache__ / *.pyc austragen%C_RESET%
echo    %C_KEY%17%C_RESET%  Vormerkung loesen   %C_TEXT%Staging leeren - Dateiinhalte bleiben erhalten%C_RESET%
echo    %C_KEY%18%C_RESET%  %C_WARN%[^^!]%C_RESET% Aenderungen verwerfen %C_TEXT%Loescht alle nicht committeten Aenderungen%C_RESET%
echo.
echo     %C_KEY%0%C_RESET%  Beenden
echo   %C_DIM%---------------------------------------------------------------------------%C_RESET%
set "choice="
set /p choice="  %C_INFO%Option [0-18]:%C_RESET% "

if not defined choice goto MENU
if "%choice%"=="0" goto END
if "%choice%"=="1"  goto STATUS
if "%choice%"=="2"  goto DIFF
if "%choice%"=="3"  goto DIFF_STAGED
if "%choice%"=="4"  goto LOG_GRAPH
if "%choice%"=="5"  goto LOG_DETAILED
if "%choice%"=="6"  goto ADD_ALL
if "%choice%"=="7"  goto COMMIT
if "%choice%"=="8"  goto QUICK_COMMIT
if "%choice%"=="9"  goto ADD_INTERACTIVE
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
echo   %C_WARN%[^^!] "%choice%" ist keine gueltige Option.%C_RESET% Bitte eine Zahl von 0 bis 18 eingeben.
ping -n 2 127.0.0.1 >nul
goto MENU

:: =============================================================================
::  HILFSROUTINEN
:: =============================================================================

:: --- Kopfzeile mit Live-Status des Repositories ------------------------------
:HEADER
set "H_BRANCH=(unbekannt)"
for /f "delims=" %%B in ('git rev-parse --abbrev-ref HEAD 2^>nul') do set "H_BRANCH=%%B"

set "H_REMOTE=kein Remote"
for /f "delims=" %%R in ('git rev-parse --abbrev-ref --symbolic-full-name @{u} 2^>nul') do set "H_REMOTE=%%R"

set /a H_CHANGED=0
for /f %%C in ('git status --porcelain 2^>nul ^| find /c /v ""') do set /a H_CHANGED=%%C

set /a H_STAGED=0
for /f %%C in ('git diff --cached --name-only 2^>nul ^| find /c /v ""') do set /a H_STAGED=%%C

set "H_SYNC="
for /f "tokens=1,2" %%A in ('git rev-list --left-right --count HEAD...@{u} 2^>nul') do set "H_SYNC=%%A voraus / %%B zurueck"
if not defined H_SYNC set "H_SYNC=nicht vergleichbar"

echo %C_TITLE%===============================================================================%C_RESET%
echo %C_TITLE%   GIT HELPER  -  Event-PVP-Plugin%C_RESET%
echo %C_TITLE%===============================================================================%C_RESET%
echo   %C_DIM%Ordner  :%C_RESET% %CD%
echo   %C_DIM%Branch  :%C_RESET% %C_OK%%H_BRANCH%%C_RESET%   %C_DIM%Remote:%C_RESET% %H_REMOTE%   %C_DIM%(%H_SYNC%)%C_RESET%
echo   %C_DIM%Zustand :%C_RESET% %H_CHANGED% Datei(en) geaendert, davon %H_STAGED% zum Commit vorgemerkt
echo %C_TITLE%-------------------------------------------------------------------------------%C_RESET%
echo.
exit /b 0

:: --- Einheitlicher Abschnittskopf:  call :SECTION "Titel" "Erklaerung" "Befehl"
:SECTION
cls
echo %C_TITLE%-------------------------------------------------------------------------------%C_RESET%
echo   %C_TITLE%%~1%C_RESET%
echo %C_TITLE%-------------------------------------------------------------------------------%C_RESET%
echo   %C_TEXT%%~2%C_RESET%
echo   %C_DIM%Befehl: %~3%C_RESET%
echo %C_DIM%...............................................................................%C_RESET%
echo.
exit /b 0

:: --- Fussleiste + Warten ----------------------------------------------------
:FOOTER
echo.
echo %C_DIM%...............................................................................%C_RESET%
pause
exit /b 0

:: =============================================================================
::  1-5  ANSCHAUEN
:: =============================================================================
:STATUS
call :SECTION "STATUS" "Uebersicht: was ist geaendert, was ist vorgemerkt, was kennt Git noch gar nicht." "git status"
git status
call :FOOTER
goto MENU

:DIFF
call :SECTION "DIFF - offene Aenderungen" "Zeigt Zeile fuer Zeile, was du geaendert, aber noch NICHT vorgemerkt hast. Gruen = neu, Rot = entfernt." "git diff"
git diff
call :FOOTER
goto MENU

:DIFF_STAGED
call :SECTION "DIFF - vorgemerkte Aenderungen" "Genau dieser Inhalt landet im naechsten Commit. Ideal als letzte Kontrolle vor dem Speichern." "git diff --staged"
git diff --staged
call :FOOTER
goto MENU

:LOG_GRAPH
call :SECTION "VERLAUF (kompakt)" "Die letzten 20 Commits als Baum - eine Zeile pro Commit, Verzweigungen sichtbar." "git log --graph --oneline --decorate -n 20"
git log --graph --oneline --decorate -n 20
call :FOOTER
goto MENU

:LOG_DETAILED
call :SECTION "VERLAUF (ausfuehrlich)" "Die letzten 5 Commits mit Autor, Datum und vollstaendiger Nachricht." "git log -n 5"
git log -n 5
call :FOOTER
goto MENU

:: =============================================================================
::  6-9  VORMERKEN UND SPEICHERN
:: =============================================================================
:ADD_ALL
call :SECTION "ALLES VORMERKEN" "Legt jede Aenderung in die Staging-Area. Gespeichert wird damit noch nichts - dazu braucht es einen Commit." "git add ."
git add .
echo   %C_OK%Vorgemerkt.%C_RESET% Aktueller Stand:
echo.
git status --short
call :FOOTER
goto MENU

:COMMIT
call :SECTION "COMMIT" "Speichert NUR die vorgemerkten Dateien als neuen Stand. Nicht vorgemerktes bleibt unberuehrt." "git commit -m \"...\""
git diff --cached --quiet
if not errorlevel 1 (
    echo   %C_WARN%Es ist nichts vorgemerkt.%C_RESET% Nutze zuerst Option 6 oder 9 - oder Option 8 fuer beides.
    call :FOOTER
    goto MENU
)
echo   Diese Dateien werden committet:
echo.
git diff --cached --name-status
echo.
set "commit_msg="
set /p commit_msg="  Commit-Nachricht (leer = Abbruch): "
if not defined commit_msg (
    echo   %C_DIM%Abgebrochen - es wurde nichts gespeichert.%C_RESET%
    call :FOOTER
    goto MENU
)
git commit -m "%commit_msg%"
call :FOOTER
goto MENU

:QUICK_COMMIT
call :SECTION "SCHNELL-COMMIT" "Merkt ALLE Aenderungen vor und committet sie sofort. Praktisch, aber weniger kontrolliert als 6 + 7." "git add . ^&^& git commit -m \"...\""
echo   Diese Aenderungen wuerden mitgenommen:
echo.
git status --short
echo.
set "commit_msg="
set /p commit_msg="  Commit-Nachricht (leer = Abbruch): "
if not defined commit_msg (
    echo   %C_DIM%Abgebrochen - es wurde nichts gespeichert.%C_RESET%
    call :FOOTER
    goto MENU
)
git add .
git commit -m "%commit_msg%"
call :FOOTER
goto MENU

:ADD_INTERACTIVE
call :SECTION "AUSWAHL VORMERKEN" "Git fuehrt dich durch ein eigenes Menue und du bestimmst pro Datei, was vorgemerkt wird. Mit 'q' verlaesst du es." "git add -i"
git add -i
call :FOOTER
goto MENU

:: =============================================================================
::  10-14  BRANCHES UND SERVER
:: =============================================================================
:BRANCH_LIST
call :SECTION "BRANCHES" "Alle Zweige, lokal und auf dem Server. Der Stern markiert den Zweig, auf dem du gerade arbeitest." "git branch -a -v"
git branch -a -v
call :FOOTER
goto MENU

:BRANCH_SWITCH
call :SECTION "BRANCH WECHSELN / ANLEGEN" "Wechseln geht nur sauber, wenn keine offenen Aenderungen im Weg sind - notfalls vorher Option 15 (Stash) nutzen." "git switch [-c] <name>"
git branch
echo.
echo     %C_KEY%1%C_RESET%  Zu vorhandenem Branch wechseln
echo     %C_KEY%2%C_RESET%  Neuen Branch anlegen und dorthin wechseln
echo     %C_KEY%0%C_RESET%  Zurueck
echo.
set "bchoice="
set /p bchoice="  Auswahl: "
if "%bchoice%"=="1" goto BRANCH_SWITCH_EXISTING
if "%bchoice%"=="2" goto BRANCH_SWITCH_NEW
goto MENU

:BRANCH_SWITCH_EXISTING
set "bname="
set /p bname="  Branch-Name: "
if not defined bname goto MENU
git switch "%bname%"
call :FOOTER
goto MENU

:BRANCH_SWITCH_NEW
set "bname="
set /p bname="  Name des neuen Branches: "
if not defined bname goto MENU
git switch -c "%bname%"
call :FOOTER
goto MENU

:PULL
call :SECTION "PULL" "Holt die Commits vom Server und baut sie in deinen Branch ein. Bei Konflikten meldet Git sich - dann von Hand aufloesen." "git pull"
git pull
call :FOOTER
goto MENU

:PUSH
call :SECTION "PUSH" "Laedt deine lokalen Commits zum Server hoch. Fehlt der Upstream-Branch, wird er hier angeboten." "git push"
git push
if errorlevel 1 (
    echo.
    echo   %C_WARN%Push fehlgeschlagen.%C_RESET% Haeufigster Grund: dieser Branch existiert auf dem Server noch nicht.
    set "pchoice="
    set /p pchoice="  Branch jetzt auf origin anlegen und pushen? (j/n): "
    if /i "!pchoice!"=="j" git push -u origin HEAD
)
call :FOOTER
goto MENU

:FETCH
call :SECTION "FETCH + PRUNE" "Aktualisiert nur das Wissen ueber den Server - dein Arbeitsstand bleibt unangetastet. Prune entfernt Verweise auf geloeschte Branches." "git fetch --all --prune"
git fetch --all --prune
echo.
echo   %C_OK%Serverstand abgeglichen.%C_RESET%
call :FOOTER
goto MENU

:: =============================================================================
::  15-18  AUFRAEUMEN
:: =============================================================================
:STASH_MENU
call :SECTION "STASH" "Legt deine Aenderungen auf einen Stapel und stellt den letzten Commit-Stand her. Praktisch, um schnell den Branch zu wechseln." "git stash / git stash pop / git stash list"
echo     %C_KEY%1%C_RESET%  Aenderungen weglegen        %C_DIM%(Arbeitsverzeichnis wird sauber)%C_RESET%
echo     %C_KEY%2%C_RESET%  Zuletzt Weggelegtes zurueck %C_DIM%(Eintrag wird dabei entfernt)%C_RESET%
echo     %C_KEY%3%C_RESET%  Stapel anzeigen
echo     %C_KEY%0%C_RESET%  Zurueck
echo.
set "schoice="
set /p schoice="  Auswahl: "
if "%schoice%"=="1" goto STASH_PUSH
if "%schoice%"=="2" goto STASH_POP
if "%schoice%"=="3" goto STASH_LIST
goto MENU

:STASH_PUSH
set "smsg="
set /p smsg="  Kurze Beschreibung (leer = ohne Namen): "
if not defined smsg (git stash) else (git stash push -m "%smsg%")
call :FOOTER
goto MENU

:STASH_POP
git stash pop
call :FOOTER
goto MENU

:STASH_LIST
git stash list
call :FOOTER
goto MENU

:CLEAN_PYCACHE
call :SECTION "PYCACHE AUS GIT ENTFERNEN" "Traegt __pycache__-Ordner und *.pyc aus der Versionsverwaltung aus. Die Dateien auf der Festplatte bleiben bestehen." "git rm --cached <datei>"
echo   Suche nach getrackten Cache-Dateien...
echo.
powershell -NoProfile -Command "$f = git ls-files | Where-Object { $_ -match '(__pycache__|\.pyc$)' }; if (-not $f) { Write-Host '  Nichts gefunden - alles sauber.' } else { $f | ForEach-Object { git rm --cached --quiet -- $_; Write-Host ('  entfernt: ' + $_) }; Write-Host ''; Write-Host ('  ' + $f.Count + ' Datei(en) ausgetragen.') }"
echo.
echo   %C_INFO%Hinweis:%C_RESET% Damit das dauerhaft wirkt, jetzt committen (Option 7) und die
echo   Muster in die .gitignore aufnehmen.
call :FOOTER
goto MENU

:RESET_STAGED
call :SECTION "VORMERKUNG LOESEN" "Leert die Staging-Area. Deine Aenderungen bleiben vollstaendig erhalten - sie sind nur nicht mehr fuer den Commit vorgemerkt." "git reset"
git reset
echo.
echo   %C_OK%Staging-Area geleert.%C_RESET% Dateiinhalte sind unveraendert.
call :FOOTER
goto MENU

:RESTORE_WORKSPACE
call :SECTION "[^^!] AENDERUNGEN VERWERFEN" "Setzt alle nicht vorgemerkten Dateien auf den letzten Commit zurueck. Das laesst sich NICHT rueckgaengig machen." "git restore ."
echo   %C_WARN%WARNUNG:%C_RESET% Folgende Aenderungen gehen unwiederbringlich verloren:
echo.
git status --short
echo.
echo   %C_DIM%Tipp: Mit Option 15 (Stash) kannst du sie stattdessen aufheben.%C_RESET%
echo.
set "confirm="
set /p confirm="  Zum Fortfahren JA eintippen: "
if /i "%confirm%"=="JA" (
    git restore .
    echo   %C_OK%Aenderungen verworfen.%C_RESET%
) else (
    echo   %C_DIM%Abgebrochen - nichts wurde veraendert.%C_RESET%
)
call :FOOTER
goto MENU

:: =============================================================================
:END
cls
echo.
echo   %C_OK%Bis bald^^!%C_RESET%
echo.
popd
exit /b 0
