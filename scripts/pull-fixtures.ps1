# Copies mic fixtures (WAV + MIDI truth) from the phone into the JVM test resources.
# Usage: .\scripts\pull-fixtures.ps1   (honors $env:ANDROID_SERIAL)
# Since 2.10.1 they live in the app's own storage (files/recordings/fixtures, data/Recordings.kt), which only the debug
# build's run-as can read: each file goes through /data/local/tmp, then is removed from there.
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.bigpapituna.sharpears'
$root = Split-Path $PSScriptRoot -Parent
$dest = Join-Path $root 'app\src\test\resources\fixtures'
New-Item -ItemType Directory -Force $dest | Out-Null
$src = 'files/recordings/fixtures'
$files = (& $adb shell run-as $pkg ls $src) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.(wav|notes)$' }
foreach ($f in $files) {
    $local = Join-Path $dest $f
    if (Test-Path $local) { continue }   # never overwrite: local copies may carry corrections (e.g. truth offsets)
    & $adb shell "run-as $pkg cat '$src/$f' > '/data/local/tmp/se-$f'"
    & $adb pull "/data/local/tmp/se-$f" $local 2>&1 | Out-Null
    & $adb shell "rm -f '/data/local/tmp/se-$f'"
    Write-Host "pulled $f"
}
Get-ChildItem $dest | Select-Object Name, @{n='KB'; e={[int]($_.Length / 1KB)}}
