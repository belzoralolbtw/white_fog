@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set CI=1
setlocal enabledelayedexpansion

rem ==================================================================
rem  Sandbox self-test for the portable dynamic-light root-cause fix:
rem    * removal of the wrong "level instanceof ClientLevel" gate
rem      (RenderSectionRegion is passed during mesh baking);
rem    * falloff/attenuation and section-rebuild radius policy;
rem    * entity (hand/player model) block-light contribution.
rem  Does NOT touch the main build.gradle or src/. Compiles only
rem  tests\portable_light_dynamic.
rem  Self-terminating: hard internal timeout is a watchdog thread in SelfTest (20 c).
rem  Result: logs\portable_light_dynamic_selftest_<stamp>.txt (+ .result).
rem ==================================================================

set HERE=%~dp0
for %%I in ("%HERE%..\..") do set ROOT=%%~fI
set SRC=%HERE%src
set OUT=%HERE%build
set LOGDIR=%ROOT%\logs

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set STAMP=%%i
set LOG=%LOGDIR%\portable_light_dynamic_selftest_%STAMP%.txt
set RES=%LOGDIR%\portable_light_dynamic_selftest_%STAMP%.result

if exist "%OUT%" rmdir /s /q "%OUT%" >nul 2>&1
mkdir "%OUT%"

> "%LOG%" echo [portable_light_dynamic_selftest] stamp=%STAMP%
>>"%LOG%" echo [portable_light_dynamic_selftest] root=%ROOT%
>>"%LOG%" echo [portable_light_dynamic_selftest] note=logic-only sandbox, NOT a runtime proof

dir /b /s "%SRC%\*.java" > "%OUT%\sources.txt"
javac -encoding UTF-8 -d "%OUT%" @"%OUT%\sources.txt" >>"%LOG%" 2>&1
set RC=!ERRORLEVEL!
if !RC! NEQ 0 (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo detail=javac_failed
	>>"%RES%" echo exit=!RC!
	echo [portable_light_dynamic_selftest] javac FAILED exit=!RC!
	exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%OUT%" com.whitefog.tests.portabledynamic.SelfTest >>"%LOG%" 2>&1
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

echo [portable_light_dynamic_selftest] java exit=!RC!
echo [portable_light_dynamic_selftest] log=%LOG%
exit /b !RC!
