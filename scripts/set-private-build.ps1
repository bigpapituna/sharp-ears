# Turns the private build on or off in secrets.properties at the repo root (gitignored), where app/build.gradle.kts reads
# PRIVATE_BUILD into BuildConfig.PRIVATE_BUILD at build time. With it true, songs marked privateOnly
# (engine/chords/PopSongsTemp.kt, personal use only) show in the song lists; without it (the default) they never do.
#
#   .\scripts\set-private-build.ps1            writes PRIVATE_BUILD=true
#   .\scripts\set-private-build.ps1 -Off       writes PRIVATE_BUILD=false (the public default)
#
# Every other line of secrets.properties is kept. Rebuild and install after: .\gradlew.bat installDebug
param([switch]$Off)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'

$kept = @()
if (Test-Path $file) { $kept = @(Get-Content $file | Where-Object { $_ -notmatch '^\s*PRIVATE_BUILD\s*=' }) }
$value = if ($Off) { 'false' } else { 'true' }
Set-Content -Path $file -Value ($kept + "PRIVATE_BUILD=$value") -Encoding ascii

# The file must never reach git: check the ignore rule is still there.
git -C $root check-ignore -q secrets.properties; if ($LASTEXITCODE -ne 0) { throw 'secrets.properties is not gitignored: fix .gitignore before building.' }
Write-Host "PRIVATE_BUILD=$value saved to secrets.properties (gitignored). Rebuild and install: .\gradlew.bat installDebug"
