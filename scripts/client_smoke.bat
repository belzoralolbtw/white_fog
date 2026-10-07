@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set CI=1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0client_smoke.ps1"
exit /b %ERRORLEVEL%
