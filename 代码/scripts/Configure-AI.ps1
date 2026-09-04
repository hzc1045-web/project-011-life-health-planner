$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$Executable = Join-Path (Join-Path (Join-Path $ProjectRoot $CodeDir) "companion") ".venv\Scripts\life-health-companion.exe"
$StopScript = Join-Path $PSScriptRoot "Stop-Companion.ps1"
$StartScript = Join-Path $PSScriptRoot "Start-Companion.ps1"

if (-not (Test-Path -LiteralPath $Executable)) { throw "Run Setup-Companion.cmd first." }

Write-Host "Life Health Planner - AI provider setup"
Write-Host "1. Save DeepSeek official API key"
Write-Host "2. Save AI Xiaozhan API key (third party)"
Write-Host "3. Use DeepSeek official"
Write-Host "4. Use AI Xiaozhan (third party)"
Write-Host "5. Show status"
$Choice = Read-Host "Select 1-5"
$Restart = $false

switch ($Choice) {
  "1" {
    & $Executable set-key --provider deepseek
  }
  "2" {
    Write-Warning "AI Xiaozhan is a third party. Health context will be sent to subkkai.com when active."
    $Confirm = Read-Host "Type I AGREE to continue"
    if ($Confirm -ne "I AGREE") { throw "Third-party confirmation was not provided." }
    & $Executable set-key --provider subkkai
  }
  "3" {
    & $Executable use-provider deepseek
    $Restart = $true
  }
  "4" {
    Write-Warning "AI Xiaozhan is a third party. Health context will be sent to subkkai.com."
    $Confirm = Read-Host "Type I AGREE to switch"
    if ($Confirm -ne "I AGREE") { throw "Third-party confirmation was not provided." }
    & $Executable use-provider subkkai --acknowledge-third-party
    $Restart = $true
  }
  "5" {
    & $Executable status
  }
  default {
    throw "Invalid selection."
  }
}

if ($LASTEXITCODE -ne 0) { throw "AI provider command failed." }
if ($Restart) {
  & $StopScript
  & $StartScript
}
Read-Host "Press Enter to close"
