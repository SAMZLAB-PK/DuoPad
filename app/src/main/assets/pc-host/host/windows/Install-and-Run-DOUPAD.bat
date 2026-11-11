@echo off
setlocal
title DuoPad Pro - Windows Host v4
REM Do NOT run the input/file/screen Host with Administrator rights.
cd /d "%~dp0\..\python"
where py >nul 2>&1 || (
  echo [ERROR] Python 3.10+ is required. Install Python and check Add to PATH.
  pause
  exit /b 1
)
echo [1/3] Checking PC Host dependencies...
py -c "import qrcode, mss, PIL" >nul 2>&1
if %errorlevel% neq 0 py -m pip install --disable-pip-version-check -r requirements.txt
if %errorlevel% neq 0 (
  echo Dependencies failed. Check internet and Python setup.
  pause
  exit /b 1
)
echo [2/3] One-time Windows Firewall configuration...
netsh advfirewall firewall show rule name="DuoPad PC Host TCP 27845" >nul 2>&1
if %errorlevel% neq 0 call "%~dp0SETUP-FIREWALL-ONCE.bat"
echo [3/3] Starting DuoPad PC Host as a normal Windows user...
py unipoint_host.py --port 27845
pause
