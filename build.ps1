# Builds the debug APK and copies it to C:\AA\AANext.apk
# The JDK fails to open its loopback socket when the temp path contains non-ASCII characters
# (the user folder here has an "e with diaeresis"), so force an ASCII temp dir.
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force "$PSScriptRoot\.tmp" | Out-Null
$env:TMP = "$PSScriptRoot\.tmp"
$env:TEMP = "$PSScriptRoot\.tmp"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$PSScriptRoot\.tmp -Djava.io.tmpdir=$PSScriptRoot\.tmp"
cmd /c "`"$PSScriptRoot\gradlew.bat`" -p `"$PSScriptRoot`" assembleDebug --console=plain 2>&1"
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED"; exit 1 }
Copy-Item "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" "$PSScriptRoot\..\AANext.apk" -Force
Write-Host "APK: $((Resolve-Path "$PSScriptRoot\..\AANext.apk").Path)"
