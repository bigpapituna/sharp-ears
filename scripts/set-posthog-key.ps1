# Puts the tester analytics settings into secrets.properties at the repo root (gitignored), where app/build.gradle.kts reads
# them into BuildConfig.POSTHOG_KEY, POSTHOG_HOST and DIARY_TO at build time (CLAUDE.md, "Tester analytics"). A build with
# POSTHOG_KEY also compiles the PostHog SDK in; a build without it has no PostHog code at all.
#
#   .\scripts\set-posthog-key.ps1                  asks for the Project API key (typed or pasted, never shown), the host and
#                                                  the diary's address (Enter keeps what is there)
#   .\scripts\set-posthog-key.ps1 -ApiHost https://eu.i.posthog.com
#                                                  the EU cloud instead of the default https://us.i.posthog.com
#   .\scripts\set-posthog-key.ps1 -DiaryOnly       sets only the diary's address; the key stays as it is
#   .\scripts\set-posthog-key.ps1 -Remove          takes the key, host and diary address out (the next build has no SDK,
#                                                  no "Help Roy test" outside debug builds and no diary switch)
#
# The key never goes on the command line, into the console, a log or git. The diary address is Roy's own: the diary is only
# ever sent there, through the Brevo key set by set-email-key.ps1. Rebuild and install after: .\gradlew.bat installDebug
param([switch]$Remove, [string]$ApiHost = '', [switch]$DiaryOnly)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'
$mine = '^\s*(POSTHOG_KEY|POSTHOG_HOST|DIARY_TO)\s*='

# Keep every other line of an existing file (the AI and email keys, anything added later).
$kept = @()
$old = @{}
if (Test-Path $file) {
    $kept = @(Get-Content $file | Where-Object { $_ -notmatch $mine })
    Get-Content $file | Where-Object { $_ -match $mine } | ForEach-Object { $k, $v = $_ -split '=', 2; $old[$k.Trim()] = $v.Trim() }
}

function Save($lines) {
    Set-Content -Path $file -Value $lines -Encoding ascii
    # The file must never reach git: check the ignore rule is still there.
    git -C $root check-ignore -q secrets.properties; if ($LASTEXITCODE -ne 0) { throw 'secrets.properties is not gitignored: fix .gitignore before building.' }
}

if ($Remove) {
    Save $kept
    Write-Host "Tester analytics removed from secrets.properties. Rebuild: no PostHog SDK and no diary in the next build."
    return
}

$key = $old['POSTHOG_KEY']
$hostValue = if ($ApiHost) { $ApiHost } elseif ($old['POSTHOG_HOST']) { $old['POSTHOG_HOST'] } else { 'https://us.i.posthog.com' }
if (-not $DiaryOnly) {
    $secure = Read-Host -AsSecureString 'PostHog Project API key (phc_..., not shown)'
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    if ($key -notmatch '^phc_[A-Za-z0-9_\-]{20,}$') { throw 'That does not look like a PostHog Project API key (phc_...). Nothing was written.' }
    if ($hostValue -notmatch '^https://[A-Za-z0-9.\-]+(/.*)?$') { throw "The host must be an https address (got $hostValue). Nothing was written." }
}

$diary = $old['DIARY_TO']
$typed = Read-Host "Address the daily diary goes to (Roy's own; Enter keeps '$diary', '-' for none)"
if ($typed -eq '-') { $diary = '' }
elseif ($typed) {
    if ($typed.Trim() -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') { throw 'That does not look like an email address. Nothing was written.' }
    $diary = $typed.Trim()
}

$lines = @()
if ($key) { $lines += "POSTHOG_KEY=$key"; $lines += "POSTHOG_HOST=$hostValue" }
if ($diary) { $lines += "DIARY_TO=$diary" }
Save ($lines + $kept)
$key = $null
Write-Host "Saved to secrets.properties (gitignored). Build and install as usual: .\gradlew.bat installDebug"
