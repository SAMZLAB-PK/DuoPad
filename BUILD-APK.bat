@echo off
setlocal EnableExtensions
title UniPoint Pro - Build APK
cd /d "%~dp0"

set "GRADLE_JVM=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"

echo ===============================================
echo  UniPoint Pro - memory-safe debug build
echo ===============================================
echo.
echo Gradle JVM: %GRADLE_JVM%
echo Workers: 2, parallel build disabled
echo.

call gradlew.bat --stop >nul 2>&1
call gradlew.bat --no-daemon --no-parallel --max-workers=2 "-Dorg.gradle.jvmargs=%GRADLE_JVM%" :app:assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED. APK was not exported.
  echo.
  echo If Gradle still reports 768 MiB, check:
  echo   %USERPROFILE%\.gradle\gradle.properties
  echo and remove/replace any old org.gradle.jvmargs line.
  pause
  exit /b 1
)

set "SRC=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "DST=%CD%\UniPoint-Pro-2.3-debug.apk"
if not exist "%SRC%" (
  echo Build reported success but APK was not found at:
  echo %SRC%
  pause
  exit /b 2
)

copy /Y "%SRC%" "%DST%" >nul
if errorlevel 1 (
  echo Failed to copy APK to project root.
  pause
  exit /b 3
)

echo.
echo BUILD SUCCESSFUL
echo APK: %DST%
echo.
echo To install over USB manually:
echo   adb install -r -d "%DST%"
echo.
pause
