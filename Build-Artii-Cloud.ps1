param([string]$ConfigFile = "$PSScriptRoot/.env.mobile", [switch]$Release)
$ErrorActionPreference = 'Stop'
$studioJava = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath "$studioJava/bin/java.exe")) { $env:JAVA_HOME = $studioJava }
$line = Get-Content -LiteralPath $ConfigFile | Where-Object { $_ -match '^ARTII_API_URL=' } | Select-Object -First 1
if (-not $line) { throw 'Copy .env.mobile.example to .env.mobile and set the public server URL.' }
$origin = $line.Substring('ARTII_API_URL='.Length).Trim().Trim('"').TrimEnd('/')
$uri = [Uri]$origin
if ($uri.Scheme -ne 'https' -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/' -or $uri.Host -in @('localhost','127.0.0.1')) { throw 'Use a public HTTPS origin without a path or credentials.' }
$health = Invoke-RestMethod "$origin/health" -TimeoutSec 30
if (-not $health.ok -or $health.storage -ne 'postgres') { throw 'The deployed server is not using PostgreSQL.' }
if ($Release) {
  if (-not $health.features.social.google -or -not $health.features.social.facebook -or -not $health.features.social.apple -or -not $health.features.callsRelay -or -not $health.features.purchases) { throw 'Release blocked: configure all three social providers, TURN calls, and Google Play purchases on the server first.' }
}
$task = if ($Release) { ':app:bundleRelease' } else { ':app:assembleDebug' }
Push-Location $PSScriptRoot
try {
  & "$PSScriptRoot/gradlew.bat" $task "-PARTII_API_URL=$origin"
  if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
} finally { Pop-Location }
Write-Host 'Cloud-connected build completed. Release bundles still require your own signing key.'
