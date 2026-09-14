@echo off
setlocal EnableExtensions EnableDelayedExpansion
title DOUPAD 3.2.0 Connectivity - Build APK
cd /d "%~dp0"

set "APP_NAME=DOUPAD"
set "VERSION=3.2.0-connectivity"
set "PACKAGE=com.samz.doupad.beta"
set "OUT_NAME=DOUPAD-3.2.0-connectivity-debug.apk"
set "GRADLE_JVM=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"

rem ---- Java check ----
where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found.
  echo Install Android Studio ^(recommended^) or JDK 17/21, then run this file again.
  pause
  exit /b 10
)

rem ---- Android SDK auto-detection ----
if not defined ANDROID_SDK_ROOT if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
if not defined ANDROID_SDK_ROOT if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-35\android.jar" set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_SDK_ROOT if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platforms\android-35\android.jar" set "ANDROID_SDK_ROOT=%USERPROFILE%\AppData\Local\Android\Sdk"

if not defined ANDROID_SDK_ROOT (
  echo [ERROR] Android SDK not found.
  echo Open Android Studio ^> SDK Manager and install:
  echo   - Android SDK Platform 35
  echo   - Android SDK Build-Tools 35.0.0
  echo   - Android SDK Platform-Tools
  echo Then run this file again.
  pause
  exit /b 11
)
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

if not exist "%ANDROID_SDK_ROOT%\platforms\android-35\android.jar" (
  echo [ERROR] Android SDK Platform 35 is missing from:
  echo   %ANDROID_SDK_ROOT%
  pause
  exit /b 12
)

> local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=\\%

set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"

echo ========================================================
echo  DOUPAD 3.2.0 Connectivity - Debug APK Build
echo ========================================================
echo SDK: %ANDROID_SDK_ROOT%
echo Package: %PACKAGE%
echo Output: %OUT_NAME%
echo.

call gradlew.bat --stop >nul 2>&1
call gradlew.bat --no-daemon --no-parallel --max-workers=2 "-Dorg.gradle.jvmargs=%GRADLE_JVM%" :app:clean :app:testDebugUnitTest :app:assembleDebug
if errorlevel 1 (
  echo.
  echo [ERROR] BUILD FAILED.
  echo Scroll up to the first Gradle error; do not use an older APK as a substitute.
  pause
  exit /b 20
)

set "SRC=%CD%\app\build\outputs\apk\debug\app-debug.apk"
set "DST=%CD%\%OUT_NAME%"
if not exist "%SRC%" (
  echo [ERROR] Gradle succeeded but APK not found at:
  echo   %SRC%
  pause
  exit /b 21
)
copy /Y "%SRC%" "%DST%" >nul

for %%F in ("%DST%") do set "APK_SIZE=%%~zF"
if %APK_SIZE% LSS 1000000 (
  echo [ERROR] APK is unexpectedly small: %APK_SIZE% bytes
  pause
  exit /b 22
)

echo.
echo ========================================================
echo  BUILD SUCCESSFUL
echo ========================================================
echo APK: %DST%
echo Size: %APK_SIZE% bytes
echo Package: %PACKAGE%
echo.

if exist "%ADB%" (
  echo Optional install command:
  echo   "%ADB%" install -r -d "%DST%"
) else (
  echo ADB not found; APK itself was built successfully.
)
echo.
pause
