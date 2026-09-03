$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$CompanionRoot = Join-Path (Join-Path $ProjectRoot $CodeDir) "companion"
$Python = (py -3.13 -c "import sys; print(sys.executable)").Trim()
if (-not $Python) { throw "Python 3.13 is required." }
$Venv = Join-Path $CompanionRoot ".venv"
if (-not (Test-Path $Venv)) { & $Python -m venv $Venv }
$VenvPython = Join-Path $Venv "Scripts\python.exe"
& $VenvPython -m pip install --upgrade pip
& $VenvPython -m pip install -e "$CompanionRoot[dev]"
Write-Host "Companion environment is ready."
