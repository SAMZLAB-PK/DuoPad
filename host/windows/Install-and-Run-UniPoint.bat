@echo off
setlocal
title UniPoint Pro - Windows Host Setup
cd /d "%~dp0\..\python"

where py >nul 2>&1
if %errorlevel% neq 0 (
  echo Python was not found.
  echo Install Python 3.10 or newer, enable "Add Python to PATH", then run this file again.
  pause
  exit /b 1
)

echo [1/2] Configuring Windows Firewall for UniPoint TCP 27845 + UDP 27846...
netsh advfirewall firewall show rule name="UniPoint Host TCP 27845" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="UniPoint Host TCP 27845" dir=in action=allow protocol=TCP localport=27845 profile=any >nul 2>&1
netsh advfirewall firewall show rule name="UniPoint Discovery UDP 27846" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="UniPoint Discovery UDP 27846" dir=in action=allow protocol=UDP localport=27846 profile=any >nul 2>&1
if %errorlevel% neq 0 (
  echo NOTE: Firewall rules could not be added automatically.
  echo Right-click this BAT and choose "Run as administrator" if the phone cannot discover this PC.
)

echo [2/2] Starting UniPoint Host...
echo Windows input control uses the built-in native backend; no pip install is required.
echo Keep this window open while using UniPoint Network PC mode.
echo.
py unipoint_host.py --port 27845
pause
