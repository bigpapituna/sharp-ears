# Opens a debug route on a debug build with a route key (security review 2026-10-22, finding 1; engine/DebugRoutes.kt):
# reads DEBUG_ROUTE_KEY from secrets.properties (never printed) and runs
#   adb -s <serial> shell am start -n com.bigpapituna.sharpears/.MainActivity --es dk <key> <the rest>
#
#   .\scripts\adb-route.ps1 -Serial emulator-5558 --es screen settings
#   .\scripts\adb-route.ps1 -Serial RFCY7029SWM --es profile p1 --es screen email --es seed code
#   $env:ANDROID_SERIAL = 'emulator-5558'; .\scripts\adb-route.ps1 --es lesson first-song --ei step 3
#
# The device must be named (-Serial first, or ANDROID_SERIAL), so a route never lands on the wrong phone. With no key in
# secrets.properties it launches without one, as a keyless build expects. Everything after the serial goes to `am start`
# as it is: adb splits on spaces, so a value with spaces needs quotes inside quotes, as with adb itself.

# No param block on purpose: PowerShell would read am's own flags (-S, -W, -a) as abbreviations of a parameter.
$ErrorActionPreference = 'Stop'
$rest = @($args)
$serial = $env:ANDROID_SERIAL
if ($rest.Count -ge 2 -and $rest[0] -eq '-Serial') { $serial = $rest[1]; $rest = @($rest | Select-Object -Skip 2) }
if (-not $serial) { throw 'Name the device: .\scripts\adb-route.ps1 -Serial <serial> --es screen ... (or set $env:ANDROID_SERIAL).' }

$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'
$key = $null
if (Test-Path $file) {
    $line = Get-Content $file | Where-Object { $_ -match '^\s*DEBUG_ROUTE_KEY\s*=' } | Select-Object -Last 1
    if ($line) { $key = ($line -replace '^\s*DEBUG_ROUTE_KEY\s*=', '').Trim() }
}
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }

$am = @('-s', $serial, 'shell', 'am', 'start', '-n', 'com.bigpapituna.sharpears/.MainActivity')
if ($key) { $am += @('--es', 'dk', $key) }
else { Write-Host 'No DEBUG_ROUTE_KEY in secrets.properties: launching without a key (a keyless build takes it; a keyed one ignores it).' }
& $adb @($am + $rest)
$key = $null; $am = $null
exit $LASTEXITCODE
