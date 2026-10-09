@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
setlocal
set CI=1
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0runner.ps1" -Mode api
exit /b %ERRORLEVEL%
