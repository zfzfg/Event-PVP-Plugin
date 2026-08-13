@echo off
title Event-PVP Plugin - Dateien und Zeilen in src

rem Always pin the project root so invocations from inside tools\ or root are identical.
pushd "%~dp0.."

where python >nul 2>&1
if errorlevel 1 goto no_python

cls
python "%~dp0count_src_lines.py" %*
goto done

:no_python
echo.
echo FEHLER: "python" wurde nicht im PATH gefunden.
echo Bitte Python installieren oder zum PATH hinzufuegen (python.org/downloads).
echo.

:done
popd
echo.
echo =======================================================================
echo   Fertig. Das Fenster bleibt offen - du kannst hochscrollen.
echo =======================================================================
pause >nul
exit /b 0
