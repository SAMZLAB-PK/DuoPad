@echo off
setlocal
title DOUPAD Host - First Run Setup

net session >nul 2>&1
if %errorlevel% neq 0 (
  echo DOUPAD needs administrator permission once to configure Windows Firewall.
  echo Requesting elevation...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

cd /d "%~dp0\..\python"
where py >nul 2>&1 || (
  echo [ERROR] Python 3.10 or newer was not found.
  echo Install Python from python.org and enable Add Python to PATH.
  pause
  exit /b 1
)

echo [1/3] Configuring Windows Firewall...
netsh advfirewall firewall show rule name="DOUPAD Host TCP 27845" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="DOUPAD Host TCP 27845" dir=in action=allow protocol=TCP localport=27845 profile=private,domain >nul
netsh advfirewall firewall show rule name="DOUPAD Discovery UDP 27846" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="DOUPAD Discovery UDP 27846" dir=in action=allow protocol=UDP localport=27846 profile=private,domain >nul

echo [2/3] Preparing QR pairing support...
py -c "import qrcode" >nul 2>&1
if %errorlevel% neq 0 py -m pip install --disable-pip-version-check -q -r requirements.txt

echo [3/3] Starting DOUPAD Host...
echo.
py unipoint_host.py --port 27845
pause
