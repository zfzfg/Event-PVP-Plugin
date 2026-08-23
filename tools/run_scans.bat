@echo off
title Event-PVP Plugin - Localization and Audit Suite

rem Always pin the project root so invocations from inside tools\ or root are identical.
pushd "%~dp0.."

:menu
cls
echo =======================================================================
echo         EVENT-PVP PLUGIN - LOCALIZATION AND QUALITY AUDIT SUITE
echo =======================================================================
echo.
echo   [1] Standard: Full Suite (i18n Audit + Console Check + Untranslated + Self-Tests)
echo   [2] Full Suite + Export Reports to reports\ (Markdown + JSON, incl. Self-Test results)
echo   [3] Only i18n Rule Detectors (all 11 detectors D1-D11, incl. web panel D10/D11)
echo   [4] Only Critical i18n Findings (D1, D2, D3, D5, D6)
echo   [5] Only Console and Logger Language Check
echo   [6] Only Untranslated Values Report
echo   [7] Show Discovered Message Helpers and Lookup Chains
echo   [8] Run Automated Tests (Pytest Suite and Module Selection)
echo   [9] Write Accepted Baseline (freeze current findings)
echo.
echo   [L] Loose Ends: markierte Anbindungen ohne Ziel
echo   [K] Loose Ends + verwaiste Uebersetzungsschluessel
echo   [G] Git ^& GitHub Helper: Commits, Status ^& Push zu GitHub
echo.
echo   [0] Exit
echo.
echo =======================================================================

where choice >nul 2>&1
if errorlevel 1 goto menu_fallback
choice /c 1234567890LKG /n /m "Bitte Option waehlen (0-9, L, K, G): "
set "choice=%errorlevel%"
rem choice liefert die Position in der Liste, nicht das Zeichen - 11=L, 12=K, 13=G.
if "%choice%"=="11" set "choice=L"
if "%choice%"=="12" set "choice=K"
if "%choice%"=="13" set "choice=G"
goto menu_dispatch

:menu_fallback
set "choice="
set /p choice="Bitte Option waehlen (0-9, L, K, G): "

:menu_dispatch
if "%choice%"=="1" goto full_suite
if "%choice%"=="2" goto export_suite
if "%choice%"=="3" goto only_i18n
if "%choice%"=="4" goto critical_i18n
if "%choice%"=="5" goto only_console
if "%choice%"=="6" goto only_untranslated
if "%choice%"=="7" goto list_helpers
if "%choice%"=="8" goto test_menu
if "%choice%"=="9" goto write_baseline
if /i "%choice%"=="L" goto loose_ends
if /i "%choice%"=="K" goto loose_ends_i18n
if /i "%choice%"=="G" goto git_helper_launch
if "%choice%"=="10" goto quit
if "%choice%"=="0" goto quit
goto menu

:git_helper_launch
cls
call "%~dp0..\git_helper.bat"
goto menu

:full_suite
cls
python "%~dp0i18n_audit.py" --no-baseline
goto pause_return

:export_suite
cls
python "%~dp0i18n_audit.py" --no-baseline --export-markdown --export-json
echo.
echo Berichte exportiert nach: reports\i18n_audit_report.md, reports\i18n_audit_report.json, reports\untranslated_values.md
goto pause_return

:only_i18n
cls
python "%~dp0i18n_audit.py" --no-baseline --only-i18n
goto pause_return

:critical_i18n
cls
python "%~dp0i18n_audit.py" --no-baseline --only-i18n --severity critical
goto pause_return

:only_console
cls
python "%~dp0i18n_audit.py" --only-console
goto pause_return

:only_untranslated
cls
python "%~dp0i18n_audit.py" --only-untranslated
goto pause_return

:list_helpers
cls
python "%~dp0i18n_audit.py" --list-helpers
goto pause_return

:write_baseline
cls
python "%~dp0i18n_audit.py" --write-baseline
goto pause_return

:loose_ends
cls
echo [Anbindungen ohne Ziel - markierte Stellen]
echo.
python "%~dp0find_loose_ends.py"
goto pause_return

:loose_ends_i18n
cls
echo [Anbindungen ohne Ziel + verwaiste Uebersetzungsschluessel]
echo.
python "%~dp0find_loose_ends.py" --check-i18n
goto pause_return

:test_menu
cls
echo =======================================================================
echo                    AUTOMATED REGRESSION TEST SELECTION
echo =======================================================================
echo.
echo   [1] Run All Tests (Complete Suite: 76 tests)
echo   [2] Key Resolution Detectors (D1, D2, D9)
echo   [3] YAML and Bundle Parity Detectors (D3, D4, D8)
echo   [4] Hardcoded Text and Display Detectors (D5, D6, D7)
echo   [5] Console and Logger Language Check Tests
echo   [6] Untranslated Values Tests
echo   [7] CLI and Dispatcher Tests
echo   [8] Back to Main Menu
echo.
echo =======================================================================

where choice >nul 2>&1
if errorlevel 1 goto test_fallback
choice /c 12345678 /n /m "Test-Auswahl (1-8): "
set "tchoice=%errorlevel%"
goto test_dispatch

:test_fallback
set "tchoice="
set /p tchoice="Test-Auswahl (1-8): "

:test_dispatch
if "%tchoice%"=="1" goto test_all
if "%tchoice%"=="2" goto test_keys
if "%tchoice%"=="3" goto test_yaml
if "%tchoice%"=="4" goto test_hardcoded
if "%tchoice%"=="5" goto test_console
if "%tchoice%"=="6" goto test_untrans
if "%tchoice%"=="7" goto test_cli
if "%tchoice%"=="8" goto menu
goto test_menu

:test_all
cls
echo [Running All Tests]
python -m pytest "%~dp0tests" -v
goto pause_return

:test_keys
cls
echo [Running Key Detector Tests: D1, D2, D9]
python -m pytest "%~dp0tests/test_detectors_keys.py" -v
goto pause_return

:test_yaml
cls
echo [Running YAML and Bundle Parity Tests: D3, D4, D8]
python -m pytest "%~dp0tests/test_detectors_yaml.py" -v
goto pause_return

:test_hardcoded
cls
echo [Running Hardcoded Text Detector Tests: D5, D6, D7]
python -m pytest "%~dp0tests/test_detectors_hardcoded.py" -v
goto pause_return

:test_console
cls
echo [Running Console and Logger Language Tests]
python -m pytest "%~dp0tests/test_console_check.py" -v
goto pause_return

:test_untrans
cls
echo [Running Untranslated Values Tests]
python -m pytest "%~dp0tests/test_untranslated.py" -v
goto pause_return

:test_cli
cls
echo [Running CLI and Dispatcher Tests]
python -m pytest "%~dp0tests/test_cli.py" -v
goto pause_return

:pause_return
echo.
echo =======================================================================
echo   Fertig. Das Fenster bleibt offen - du kannst hochscrollen.
echo.
echo   [M] Zurueck zum Menue        [B] Beenden
echo =======================================================================
where choice >nul 2>&1
if errorlevel 1 goto pause_fallback
choice /c MB /n /m "Auswahl (M/B): "
if errorlevel 2 goto quit
goto menu

:pause_fallback
set "answer="
set /p answer="Auswahl (M/B): "
if /i "%answer%"=="B" goto quit
if /i "%answer%"=="M" goto menu
goto pause_fallback

:quit
popd
echo.
echo Beendet.
exit /b 0
