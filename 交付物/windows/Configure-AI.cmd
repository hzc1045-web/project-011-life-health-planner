@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Configure-AI.ps1"
if errorlevel 1 (
  echo.
  echo Configuration failed. Review the error above.
  pause
)
endlocal
