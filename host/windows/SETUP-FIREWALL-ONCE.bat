@echo off
setlocal
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo This one-time firewall setup needs Administrator permission.
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -Verb RunAs -FilePath '%~f0' -Wait"
  exit /b
)
netsh advfirewall firewall show rule name="DuoPad PC Host TCP 27845" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="DuoPad PC Host TCP 27845" dir=in action=allow protocol=TCP localport=27845 profile=private >nul
netsh advfirewall firewall show rule name="DuoPad Discovery UDP 27846" >nul 2>&1
if %errorlevel% neq 0 netsh advfirewall firewall add rule name="DuoPad Discovery UDP 27846" dir=in action=allow protocol=UDP localport=27846 profile=private >nul
echo DuoPad private-LAN firewall rules configured.
timeout /t 3
