@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Stop-Companion.ps1"
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" pause
exit /b %EXITCODE%
