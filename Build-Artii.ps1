$ErrorActionPreference='Stop'
$artiiStudio=Join-Path $env:ProgramFiles 'Android/Android Studio'
$env:JAVA_HOME=Join-Path $artiiStudio 'jbr'
if(-not(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))){throw 'Set JAVA_HOME to a Java runtime supported by Gradle 9.1, or build from Android Studio.'}
Set-Location -LiteralPath $PSScriptRoot
& (Join-Path $PSScriptRoot 'gradlew.bat') ':app:assembleDebug' ':app:lintDebug'
if($LASTEXITCODE -ne 0){throw 'The Android build failed. See the errors above.'}
Write-Host 'APK: app/build/outputs/apk/debug/app-debug.apk'
