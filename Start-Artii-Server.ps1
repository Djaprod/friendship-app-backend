param([switch]$Lan)
$ErrorActionPreference='Stop'
$artiiNode=(Get-Command node -ErrorAction SilentlyContinue).Source
if(-not $artiiNode){$artiiNode=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'}
if(-not(Test-Path -LiteralPath $artiiNode)){throw 'Install Node.js 22 or newer, then run this script again.'}
$env:HOST=if($Lan){'0.0.0.0'}else{'127.0.0.1'}
$env:PORT='8787'
Set-Location -LiteralPath (Join-Path $PSScriptRoot 'server')
if($Lan){Write-Host 'Use one of these PC addresses in Artii, followed by :8787';Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'} | ForEach-Object {Write-Host ('http://'+$_.IPAddress+':8787')}}
Write-Host 'Keep this window open. Press Ctrl+C to stop your Artii test server.'
& $artiiNode 'server.mjs'
