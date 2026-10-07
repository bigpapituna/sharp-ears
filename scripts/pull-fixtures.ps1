# Copies mic fixtures (WAV + MIDI truth) from the phone into the JVM test resources.
# Usage: .\scripts\pull-fixtures.ps1   (honors $env:ANDROID_SERIAL)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$dest = Join-Path $root 'app\src\test\resources\fixtures'
New-Item -ItemType Directory -Force $dest | Out-Null
$src = '/sdcard/Android/data/com.bigpapituna.sharpears/files/fixtures'
$files = (& $adb shell ls $src) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.(wav|notes)$' }
foreach ($f in $files) {
    $local = Join-Path $dest $f
    if (Test-Path $local) { continue }   # never overwrite: local copies may carry corrections (e.g. truth offsets)
    & $adb pull "$src/$f" $local 2>&1 | Out-Null; Write-Host "pulled $f"
}
Get-ChildItem $dest | Select-Object Name, @{n='KB'; e={[int]($_.Length / 1KB)}}
