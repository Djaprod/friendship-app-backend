param([string]$Device)
$ErrorActionPreference='Stop'
$artiiAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if(-not(Test-Path -LiteralPath $artiiAdb)){throw 'Android SDK platform-tools are missing. Install them in Android Studio.'}
$artiiDevices=@(& $artiiAdb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object {$_.Matches[0].Groups[1].Value})
if($Device){if($Device -notin $artiiDevices){throw 'That device is not connected or has not authorized USB debugging.'}}
elseif($artiiDevices.Count -eq 1){$Device=$artiiDevices[0]}
elseif($artiiDevices.Count -eq 0){throw 'Connect your phone by USB, enable USB debugging in Developer options, and accept the phone authorization prompt. Then run this again.'}
else{throw 'More than one device is connected. Run this script with -Device followed by the intended device ID from adb devices.'}
& $artiiAdb -s $Device reverse tcp:8787 tcp:8787
if($LASTEXITCODE -ne 0){throw 'Could not connect the phone to the local test server.'}
Write-Host 'Phone connected. Open Artii and create an account or log in. Leave this window and the USB cable connected.'
try{$artiiHealth=Invoke-RestMethod -Uri 'http://127.0.0.1:8787/health' -TimeoutSec 2}catch{$artiiHealth=$null}
if($artiiHealth.app -eq 'Artii'){
 if($artiiHealth.version -ne '0.7.0'){throw 'An older Artii server is running. Close its server window, then run this script again.'}
 Write-Host 'The updated Artii test server is already running.'
}else{& (Join-Path $PSScriptRoot 'Start-Artii-Server.ps1')}
