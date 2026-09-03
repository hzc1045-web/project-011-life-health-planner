$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$Executable = Join-Path (Join-Path (Join-Path $ProjectRoot $CodeDir) "companion") ".venv\Scripts\life-health-companion.exe"
if (-not (Test-Path $Executable)) { throw "Run Setup-Companion.cmd first." }
& $Executable set-key
Read-Host "Press Enter to close"
