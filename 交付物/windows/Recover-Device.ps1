$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$CompanionRoot = Join-Path (Join-Path $ProjectRoot $CodeDir) "companion"
$Executable = Join-Path $CompanionRoot ".venv\Scripts\life-health-companion.exe"
if (-not (Test-Path $Executable)) { throw "Run Setup-Companion.cmd first." }
& $Executable recover-pair
Read-Host "Press Enter after the new phone completes pairing"
