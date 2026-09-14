@echo off
setlocal EnableExtensions EnableDelayedExpansion
title UniPoint Pro - Build and USB Install
cd /d "%~dp0"

set "GRADLE_JVM=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"

echo ===============================================
echo  UniPoint Pro - Build + USB Install
echo ===============================================
echo.
echo Gradle JVM: %GRADLE_JVM%
echo Workers: 2, parallel build disabled
echo.

call gradlew.bat --stop >nul 2>&1
call gradlew.bat --no-daemon --no-parallel --max-workers=2 "-Dorg.gradle.jvmargs=%GRADLE_JVM%" :app:assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Installation was not attempted.
  echo.
  echo If Gradle still reports 768 MiB, open:
  echo   %USERPROFILE%\.gradle\gradle.properties
  echo and replace the old org.gradle.jvmargs value with:
  echo   org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
  pause
  exit /b 1
)

set "SRC=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "DST=%CD%\UniPoint-Pro-2.3-debug.apk"
if not exist "%SRC%" (
  echo Build succeeded but APK was not found:
  echo %SRC%
  pause
  exit /b 2
)
copy /Y "%SRC%" "%DST%" >nul

where "%ADB%" >nul 2>&1
if errorlevel 1 (
  if not exist "%ADB%" (
    echo.
    echo APK built successfully, but ADB was not found.
    echo APK: %DST%
    echo Install manually after adding Android SDK platform-tools to PATH.
    pause
    exit /b 0
  )
)

"%ADB%" start-server >nul 2>&1
set "SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" if not defined SERIAL set "SERIAL=%%A"
)

if not defined SERIAL (
  echo.
  echo APK built successfully, but no authorized USB Android device was found.
  echo Unlock the phone, accept the USB debugging prompt, then run:
  echo   "%ADB%" devices
  echo   "%ADB%" install -r -d "%DST%"
  pause
  exit /b 0
)

echo.
echo Installing on device: %SERIAL%
"%ADB%" -s "%SERIAL%" install -r -d "%DST%"
if errorlevel 1 (
  echo.
  echo INSTALL FAILED.
  echo If the message is INSTALL_FAILED_UPDATE_INCOMPATIBLE, run:
  echo   "%ADB%" -s "%SERIAL%" uninstall com.unipoint.pro.debug
  echo then run this file again.
  pause
  exit /b 4
)

echo.
echo Launching UniPoint Pro on the USB device...
"%ADB%" -s "%SERIAL%" shell monkey -p com.unipoint.pro.debug -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo.
echo ===============================================
echo  BUILD + INSTALL SUCCESSFUL
echo ===============================================
echo APK: %DST%
echo Device: %SERIAL%
echo Package: com.unipoint.pro.debug
echo.
pause
