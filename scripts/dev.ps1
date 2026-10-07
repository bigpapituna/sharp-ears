# Build, install, launch, and screenshot Sharp Ears. Usage: .\scripts\dev.ps1 [-NoBuild] [-Wait 3]
param([switch]$NoBuild, [int]$Wait = 3)
# adb prints progress on stderr, so check exit codes instead of using ErrorActionPreference=Stop.
# With several devices attached, set $env:ANDROID_SERIAL (adb and Gradle both honor it).
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (-not $NoBuild) { & .\gradlew.bat installDebug; if ($LASTEXITCODE) { exit $LASTEXITCODE } }
adb shell am start -n com.bigpapituna.sharpears/.MainActivity | Out-Null
Start-Sleep -Seconds $Wait
New-Item -ItemType Directory -Force out | Out-Null
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png out\screen.png 2>&1 | Out-Null
if ($LASTEXITCODE) { Write-Error "screenshot pull failed"; exit 1 }
Write-Host "Screenshot: $root\out\screen.png"
adb logcat -d -s AndroidRuntime:E SharpEars:D | Select-Object -Last 40
