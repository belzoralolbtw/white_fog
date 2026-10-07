@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
set CI=1
setlocal enabledelayedexpansion

rem ==================================================================
rem  Sandbox self-test: центрирование GUI-иконки white_fog:small_stone.
rem  НЕ трогает основной build.gradle и src/. Компилирует только tests/item_gui_center.
rem  Использует РЕАЛЬНЫЙ ItemTransform.apply/PoseStack из clientonly-deobf 26.2 (+ joml),
rem  поэтому единицы translation и порядок операций не «угаданы», а взяты из движка.
rem  Завершается сам: watchdog-поток в GuiIconCenterSelfTest (20 c, exit 124 = TIMEOUT).
rem  Результат: logs\item_gui_center_selftest_<stamp>.txt (+ .result, machine-readable).
rem ==================================================================

set HERE=%~dp0
for %%I in ("%HERE%..\..") do set ROOT=%%~fI
set SRC=%HERE%src
set OUT=%HERE%build
set LOGDIR=%ROOT%\logs

if not exist "%LOGDIR%" mkdir "%LOGDIR%"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set STAMP=%%i
set LOG=%LOGDIR%\item_gui_center_selftest_%STAMP%.txt
set RES=%LOGDIR%\item_gui_center_selftest_%STAMP%.result

if exist "%OUT%" rmdir /s /q "%OUT%" >nul 2>&1
mkdir "%OUT%"

> "%LOG%" echo [item_gui_center_selftest] stamp=%STAMP%
>>"%LOG%" echo [item_gui_center_selftest] root=%ROOT%
>>"%LOG%" echo [item_gui_center_selftest] note=matrix-proof via real 26.2 ItemTransform.apply, NOT a pixel render proof

rem ---- locate classpath jars (real 26.2 ItemTransform + joml) ----
set MCJAR=%USERPROFILE%\.gradle\caches\fabric-loom\26.2\minecraft-client.jar
if not exist "%MCJAR%" set MCJAR=
if not defined MCJAR (
	for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-clientonly-deobf\26.2\*.jar" 2^>nul') do if not defined MCJAR set MCJAR=%%i
)
set COMJAR=
if not exist "%USERPROFILE%\.gradle\caches\fabric-loom\26.2\minecraft-client.jar" (
	for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common-deobf\26.2\*.jar" 2^>nul') do if not defined COMJAR set COMJAR=%%i
)
set JOMLJAR=
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\org.joml\joml\1.10.8\*.jar" 2^>nul') do if not defined JOMLJAR set JOMLJAR=%%i
set DFUJAR=
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.mojang\datafixerupper\10.0.21\*.jar" 2^>nul') do if not defined DFUJAR set DFUJAR=%%i

set CP=%MCJAR%
if defined COMJAR set CP=%CP%;%COMJAR%
if defined JOMLJAR set CP=%CP%;%JOMLJAR%
if defined DFUJAR set CP=%CP%;%DFUJAR%

>>"%LOG%" echo [item_gui_center_selftest] mc=%MCJAR%
>>"%LOG%" echo [item_gui_center_selftest] joml=%JOMLJAR%
>>"%LOG%" echo [item_gui_center_selftest] dfu=%DFUJAR%

if not defined MCJAR (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo detail=minecraft_client_jar_not_found
	echo [item_gui_center_selftest] MC jar not found
	exit /b 1
)
if not defined JOMLJAR (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo detail=joml_jar_not_found
	echo [item_gui_center_selftest] joml jar not found
	exit /b 1
)

dir /b /s "%SRC%\*.java" > "%OUT%\sources.txt"
javac -encoding UTF-8 -cp "%CP%" -d "%OUT%" @"%OUT%\sources.txt" >>"%LOG%" 2>&1
set RC=!ERRORLEVEL!
if !RC! NEQ 0 (
	echo status=FAILURE>>"%LOG%"
	> "%RES%" echo status=FAILURE
	>>"%RES%" echo detail=javac_failed
	>>"%RES%" echo exit=!RC!
	echo [item_gui_center_selftest] javac FAILED exit=!RC!
	exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%OUT%;%CP%" com.whitefog.tests.guiicon.GuiIconCenterSelfTest "%ROOT%" >>"%LOG%" 2>&1
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

echo [item_gui_center_selftest] java exit=!RC!
echo [item_gui_center_selftest] log=%LOG%
exit /b !RC!
