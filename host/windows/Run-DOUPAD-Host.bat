@echo off
setlocal
title DOUPAD Host
cd /d "%~dp0\..\python"
where py >nul 2>&1 || (echo Python 3.10+ required.& pause & exit /b 1)

netsh advfirewall firewall show rule name="DOUPAD Host TCP 27845" >nul 2>&1
if %errorlevel% neq 0 (
  echo First run detected. Opening one-time firewall setup...
  call "%~dp0Install-and-Run-DOUPAD.bat"
  exit /b
)
netsh advfirewall firewall show rule name="DOUPAD Discovery UDP 27846" >nul 2>&1
if %errorlevel% neq 0 (
  echo First run detected. Opening one-time firewall setup...
  call "%~dp0Install-and-Run-DOUPAD.bat"
  exit /b
)

py -c "import qrcode" >nul 2>&1
if %errorlevel% neq 0 py -m pip install --disable-pip-version-check -q qrcode
py unipoint_host.py --port 27845
pause
