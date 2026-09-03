$ErrorActionPreference = "Stop"
$Runtime = Join-Path $env:LOCALAPPDATA "LifeHealthPlannerCompanion"
$PidFile = Join-Path $Runtime "companion.pid"
if (-not (Test-Path $PidFile)) { Write-Host "Companion is not running."; exit 0 }
$CompanionPid = Get-Content $PidFile
$Process = Get-Process -Id $CompanionPid -ErrorAction SilentlyContinue
if ($Process) { Stop-Process -Id $Process.Id }
Remove-Item -LiteralPath $PidFile -Force
Write-Host "Companion stopped."
