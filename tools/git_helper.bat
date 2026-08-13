@echo off
chcp 65001 >nul
cd /d "%~dp0\.."
call git_helper.bat
