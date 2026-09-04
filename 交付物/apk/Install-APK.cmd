@echo off
setlocal
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0life-health-planner-0.1.2.apk"
if not exist "%ADB%" (
  echo ADB was not found. Install Android SDK Platform Tools first.
  pause
  exit /b 1
)
if not exist "%APK%" (
  echo APK was not found next to this installer.
  pause
  exit /b 1
)
"%ADB%" devices
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo Installation failed. Enable USB debugging and approve this computer on the phone.
  pause
  exit /b 1
)
echo Installation completed.
pause
