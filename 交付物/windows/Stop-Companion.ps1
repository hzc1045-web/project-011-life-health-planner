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
    $allProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
    $queue = [System.Collections.Generic.Queue[int]]::new()
    $tree = [System.Collections.Generic.List[int]]::new()
    $queue.Enqueue([int]$CompanionPid)
    while ($queue.Count -gt 0) {
      $current = $queue.Dequeue()
      if ($tree.Contains($current)) { continue }
      $tree.Add($current)
      foreach ($child in $allProcesses | Where-Object { [int]$_.ParentProcessId -eq $current }) {
        if ($child.CommandLine -like "*$Executable*serve*") {
          $queue.Enqueue([int]$child.ProcessId)
        }
      }
    }
    foreach ($processId in ($tree | Sort-Object -Descending)) {
      Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
  } else {
    Write-Warning "Ignored a stale PID file that refers to another process."
  }
}
Remove-Item -LiteralPath $PidFile -Force
Write-Host "Companion stopped."
