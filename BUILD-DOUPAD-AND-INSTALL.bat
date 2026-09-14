@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
call BUILD-DOUPAD-3.2.0.bat
if errorlevel 1 exit /b %errorlevel%
set "APK=%CD%\DOUPAD-3.2.0-connectivity-debug.apk"
if not defined ANDROID_SDK_ROOT if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo ADB not found. APK is ready at:
  echo %APK%
  pause
  exit /b 0
)
"%ADB%" start-server >nul 2>&1
set "SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do if "%%B"=="device" if not defined SERIAL set "SERIAL=%%A"
if not defined SERIAL (
  echo No authorized USB Android device found.
  echo APK is ready at: %APK%
  pause
  exit /b 0
)
"%ADB%" -s "%SERIAL%" install -r -d "%APK%"
if errorlevel 1 (
  echo Install failed. If signature mismatch occurs, uninstall the old beta first:
  echo   "%ADB%" -s "%SERIAL%" uninstall com.samz.doupad.beta
  pause
  exit /b 30
)
"%ADB%" -s "%SERIAL%" shell monkey -p com.samz.doupad.beta -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo DOUPAD installed and launched on %SERIAL%.
pause
