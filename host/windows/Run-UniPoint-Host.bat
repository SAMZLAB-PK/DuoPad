@echo off
setlocal
title UniPoint Pro Host
cd /d "%~dp0\..\python"
where py >nul 2>&1 || (echo Python 3.10+ required.&pause&exit /b 1)
py unipoint_host.py --port 27845
pause
