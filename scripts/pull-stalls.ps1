# Copies stuck moments (wait mode on the same notes for 5 s or more, with the mic on) from the phone to the PC,
# then replays them through the note matcher and writes the review to app\build\stall-review.txt.
# Usage: .\scripts\pull-stalls.ps1 [-Serial 10.0.0.171:5555] [-Clear]   (-Clear deletes them from the phone afterwards)
param([string]$Serial = "", [switch]$Clear)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$a = @(); if ($Serial) { $a = @("-s", $Serial) }
$root = Split-Path $PSScriptRoot -Parent
$dest = Join-Path $root 'app\src\test\resources\stalls'
New-Item -ItemType Directory -Force $dest | Out-Null
$src = '/sdcard/Android/data/com.bigpapituna.sharpears/files/stalls'
$files = (& $adb @a shell ls $src 2>$null) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.(wav|txt)$' }
$new = 0
foreach ($f in $files) {
    $local = Join-Path $dest $f
    if (Test-Path $local) { continue }
    & $adb @a pull "$src/$f" $local 2>&1 | Out-Null; $new++
}
Write-Host "$new new file(s); $((Get-ChildItem $dest -Filter *.wav).Count) stuck moment(s) on the PC"
if ($Clear -and $files) { & $adb @a shell "rm -f $src/*"; Write-Host "cleared on the phone" }
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "*StallReviewTest*" --rerun -q | Out-Null
Get-Content app\build\stall-review.txt
