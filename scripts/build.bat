@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set CI=1
setlocal enabledelayedexpansion

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set STAMP=%%i
if not exist logs mkdir logs
set LOG=logs\build_%STAMP%.txt

echo [build] logging to %LOG%
call gradlew.bat build --no-daemon --console=plain > "%LOG%" 2>&1
set RC=!ERRORLEVEL!

echo [build] gradlew exit code = !RC!
echo.>> "%LOG%"
echo BUILD_EXIT_CODE=!RC! >> "%LOG%"
if !RC! EQU 0 (echo status=SUCCESS >> "%LOG%") else (echo status=FAILURE >> "%LOG%")
echo [build] log written to %LOG%
exit /b !RC!
