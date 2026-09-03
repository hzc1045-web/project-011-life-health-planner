$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$CompanionRoot = Join-Path (Join-Path $ProjectRoot $CodeDir) "companion"
$Executable = Join-Path $CompanionRoot ".venv\Scripts\life-health-companion.exe"
$Runtime = Join-Path $env:LOCALAPPDATA "LifeHealthPlannerCompanion"
$PidFile = Join-Path $Runtime "companion.pid"
if (-not (Test-Path $Executable)) { throw "Run Setup-Companion.cmd first." }
New-Item -ItemType Directory -Path $Runtime -Force | Out-Null
if (Test-Path $PidFile) {
  $ExistingPid = Get-Content $PidFile -ErrorAction SilentlyContinue
  if ($ExistingPid -and (Get-Process -Id $ExistingPid -ErrorAction SilentlyContinue)) {
    Write-Host "Companion is already running."
    exit 0
  }
}
$Process = Start-Process -FilePath $Executable -ArgumentList "serve" -WorkingDirectory $CompanionRoot -WindowStyle Hidden -PassThru
$Process.Id | Set-Content -Path $PidFile -Encoding ASCII
Start-Sleep -Seconds 2
try {
  Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8765/status" -TimeoutSec 5 | Out-Null
  $TailscaleCommand = Get-Command "tailscale" -ErrorAction SilentlyContinue
  $Tailscale = if ($TailscaleCommand) {
    $TailscaleCommand.Source
  } else {
    Join-Path $env:ProgramFiles "Tailscale\tailscale.exe"
  }
  if (Test-Path -LiteralPath $Tailscale) {
    $TailscaleStatus = & $Tailscale status --json 2>$null | ConvertFrom-Json
    if ($TailscaleStatus.BackendState -eq "Running" -and $TailscaleStatus.Self.DNSName) {
      & $Tailscale serve --bg --yes --https=8443 "http://127.0.0.1:8765" | Out-Null
      if ($LASTEXITCODE -ne 0) { throw "Tailscale HTTPS publishing failed." }
      Write-Host "Private HTTPS endpoint: https://$($TailscaleStatus.Self.DNSName.TrimEnd('.')):8443"
    } else {
      Write-Warning "Tailscale is not signed in. Local features are available, but phone pairing is offline."
    }
  } else {
    Write-Warning "Tailscale is not installed. Local features are available, but phone pairing is offline."
  }
  Start-Process "http://127.0.0.1:8765/setup"
  Write-Host "Companion started."
} catch {
  throw "Companion did not become healthy."
}
