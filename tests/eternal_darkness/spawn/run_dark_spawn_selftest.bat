@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
setlocal
set CI=1
set PAGER=cat
set GIT_PAGER=cat
set TERM=dumb
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0runner.ps1" -Mode selftest %*
exit /b %ERRORLEVEL%
