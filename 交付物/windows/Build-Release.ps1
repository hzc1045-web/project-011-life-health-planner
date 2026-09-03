$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$CodeDir = [string][char]0x4EE3 + [char]0x7801
$AndroidRoot = Join-Path (Join-Path $ProjectRoot $CodeDir) "android"
$CompanionRoot = Join-Path (Join-Path $ProjectRoot $CodeDir) "companion"
$Python = Join-Path $CompanionRoot ".venv\Scripts\python.exe"
$Keystore = Join-Path $env:LOCALAPPDATA "LifeHealthPlannerSigning\release.jks"
$JavaRoot = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
  Where-Object Name -Like "jdk-17*" |
  Sort-Object Name -Descending |
  Select-Object -First 1 -ExpandProperty FullName
$Password = & $Python -c "import keyring; print(keyring.get_password('LifeHealthPlannerSigning','release-keystore') or '')"
if (-not $Password) { throw "Release signing credential is unavailable." }
if (-not (Test-Path -LiteralPath $Keystore)) { throw "Release keystore is unavailable." }
if (-not $JavaRoot) { throw "JDK 17 is unavailable." }
$env:LHP_KEYSTORE_PATH = $Keystore
$env:LHP_KEYSTORE_PASSWORD = $Password
$env:LHP_KEY_ALIAS = "life-health-planner"
$env:LHP_KEY_PASSWORD = $Password
$env:JAVA_HOME = $JavaRoot
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
Push-Location $AndroidRoot
try {
  & ".\gradlew.bat" "assembleRelease"
  if ($LASTEXITCODE -ne 0) { throw "Android release build failed." }
} finally {
  Pop-Location
  $Password = $null
}
$SourceApk = Join-Path $AndroidRoot "app\build\outputs\apk\release\app-release.apk"
$DeliverablesDir = [string][char]0x4EA4 + [char]0x4ED8 + [char]0x7269
$DeliveryDir = Join-Path (Join-Path $ProjectRoot $DeliverablesDir) "apk"
$DeliveryApk = Join-Path $DeliveryDir "life-health-planner-0.1.0.apk"
Copy-Item -LiteralPath $SourceApk -Destination $DeliveryApk -Force
$Sha256 = [System.Security.Cryptography.SHA256]::Create()
$Stream = [System.IO.File]::OpenRead($DeliveryApk)
try {
  $Hash = [System.BitConverter]::ToString($Sha256.ComputeHash($Stream)).Replace("-", "")
} finally {
  $Stream.Dispose()
  $Sha256.Dispose()
}
$HashLines = @(
  "$Hash  life-health-planner-0.1.0.apk",
  "",
  "Signing certificate SHA-256: EE2BF508C03934EC5DDEE42234C247D80BD5EBAB92A5283D116EA4846FC431AF"
)
[System.IO.File]::WriteAllLines((Join-Path $DeliveryDir "SHA256.txt"), $HashLines, [System.Text.Encoding]::ASCII)
Write-Host "Signed APK copied to the delivery folder."
