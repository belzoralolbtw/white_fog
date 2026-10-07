@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set CI=1
setlocal enabledelayedexpansion

rem ==================================================================
rem  Sandbox self-test: станция «Плоский камень» и камушки (этап 1.4).
rem  НЕ трогает основной build.gradle и src/. Компилирует только tests/station.
rem  Завершается сам: внутренний hard-timeout реализован watchdog-потоком в SelfTest.
rem  Результат: logs\station_selftest_<stamp>.txt (+ .result, machine-readable).
rem ==================================================================

set HERE=%~dp0
for %%I in ("%HERE%..\..") do set ROOT=%%~fI
set SRC=%HERE%src
set OUT=%HERE%build
set LOGDIR=%ROOT%\logs

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set STAMP=%%i
set LOG=%LOGDIR%\station_selftest_%STAMP%.txt
set RES=%LOGDIR%\station_selftest_%STAMP%.result

if exist "%OUT%" rmdir /s /q "%OUT%" >nul 2>&1
mkdir "%OUT%"

> "%LOG%" echo [station_selftest] stamp=%STAMP%
>>"%LOG%" echo [station_selftest] root=%ROOT%
>>"%LOG%" echo [station_selftest] note=logic-only sandbox, NOT a runtime proof

dir /b /s "%SRC%\*.java" > "%OUT%\sources.txt"
javac -encoding UTF-8 -d "%OUT%" @"%OUT%\sources.txt" >>"%LOG%" 2>&1
set RC=!ERRORLEVEL!
if !RC! NEQ 0 (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo detail=javac_failed
	>>"%RES%" echo exit=!RC!
	echo [station_selftest] javac FAILED exit=!RC!
	exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%OUT%" com.whitefog.tests.station.SelfTest >>"%LOG%" 2>&1
set RC=!ERRORLEVEL!

if !RC! EQU 0 (
	echo status=SUCCESS>>"%LOG%"
	> "%RES%" echo status=SUCCESS
) else if !RC! EQU 124 (
	echo status=TIMEOUT>>"%LOG%"
	> "%RES%" echo status=TIMEOUT
) else (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo exit=!RC!
)

echo [station_selftest] java exit=!RC!
echo [station_selftest] log=%LOG%
exit /b !RC!
