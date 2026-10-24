# Gives the debug build's routes a key (security review 2026-10-22, finding 1; engine/DebugRoutes.kt). Writes a new random
# 24-character DEBUG_ROUTE_KEY into secrets.properties at the repo root (gitignored); app/build.gradle.kts keeps only its
# SHA-256 in BuildConfig. From the next build on, `--es screen ...` and every other debug extra need the key: launch them
# with .\scripts\adb-route.ps1, which reads it from the file. The key is never printed.
#
#   .\scripts\set-debug-key.ps1           writes a new key (replacing an old one)
#   .\scripts\set-debug-key.ps1 -Remove   takes it out (the next build takes routes from any adb launch again)
#
# Every other line of secrets.properties is kept. Rebuild and install after: .\gradlew.bat installDebug
param([switch]$Remove)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'

$kept = @()
if (Test-Path $file) { $kept = @(Get-Content $file | Where-Object { $_ -notmatch '^\s*DEBUG_ROUTE_KEY\s*=' }) }

if ($Remove) {
    Set-Content -Path $file -Value $kept -Encoding ascii
} else {
    # Letters and digits only (safe on adb's command line), from the system's cryptographic generator; bytes past the last
    # whole run of 62 are thrown away so every character is equally likely.
    $alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $byte = New-Object byte[] 1
    $key = New-Object System.Text.StringBuilder
    try {
        while ($key.Length -lt 24) {
            $rng.GetBytes($byte)
            if ($byte[0] -lt 248) { [void]$key.Append($alphabet[$byte[0] % 62]) }
        }
    } finally { $rng.Dispose() }
    Set-Content -Path $file -Value ($kept + "DEBUG_ROUTE_KEY=$($key.ToString())") -Encoding ascii
    $key = $null
}

# The file must never reach git: check the ignore rule is still there.
git -C $root check-ignore -q secrets.properties; if ($LASTEXITCODE -ne 0) { throw 'secrets.properties is not gitignored: fix .gitignore before building.' }
Write-Host 'Saved.'
