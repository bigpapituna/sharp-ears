# Copies stuck moments (wait mode on the same notes for 5 s or more, with the mic on) from the phone to the PC,
# then replays them through the note matcher and writes the review to app\build\stall-review.txt.
# Usage: .\scripts\pull-stalls.ps1 [-Serial 10.0.0.171:5555] [-Clear]   (-Clear deletes them from the phone afterwards)
# Since 2.10.1 they live in the app's own storage (files/recordings/stalls, data/Recordings.kt), which only the debug
# build's run-as can read: each file goes through /data/local/tmp, then is removed from there.
param([string]$Serial = "", [switch]$Clear)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.bigpapituna.sharpears'
$a = @(); if ($Serial) { $a = @("-s", $Serial) }
$root = Split-Path $PSScriptRoot -Parent
$dest = Join-Path $root 'app\src\test\resources\stalls'
New-Item -ItemType Directory -Force $dest | Out-Null
$src = 'files/recordings/stalls'
$files = (& $adb @a shell run-as $pkg ls $src 2>$null) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.(wav|txt)$' }
$new = 0
foreach ($f in $files) {
    $local = Join-Path $dest $f
    if (Test-Path $local) { continue }
    & $adb @a shell "run-as $pkg cat '$src/$f' > '/data/local/tmp/se-$f'"
    & $adb @a pull "/data/local/tmp/se-$f" $local 2>&1 | Out-Null
    & $adb @a shell "rm -f '/data/local/tmp/se-$f'"
    $new++
}
Write-Host "$new new file(s); $((Get-ChildItem $dest -Filter *.wav).Count) stuck moment(s) on the PC"
if ($Clear -and $files) { & $adb @a shell "run-as $pkg sh -c 'rm -f $src/*'"; Write-Host "cleared on the phone" }
Set-Location $root
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "*StallReviewTest*" --rerun -q | Out-Null
Get-Content app\build\stall-review.txt
