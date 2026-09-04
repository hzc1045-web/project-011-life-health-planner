$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$Executable = Join-Path (Join-Path (Join-Path $ProjectRoot $CodeDir) "companion") ".venv\Scripts\life-health-companion.exe"
$Runtime = Join-Path $env:LOCALAPPDATA "LifeHealthPlannerCompanion"
$PidFile = Join-Path $Runtime "companion.pid"
if (-not (Test-Path $PidFile)) { Write-Host "Companion is not running."; exit 0 }
$CompanionPid = Get-Content $PidFile
$Process = Get-Process -Id $CompanionPid -ErrorAction SilentlyContinue
if ($Process) {
  $ProcessInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $CompanionPid" -ErrorAction SilentlyContinue
  if ($ProcessInfo -and $ProcessInfo.CommandLine -like "*$Executable*serve*") {
    Stop-Process -Id $Process.Id -Force
  } else {
    Write-Warning "Ignored a stale PID file that refers to another process."
  }
}
Remove-Item -LiteralPath $PidFile -Force
Write-Host "Companion stopped."
