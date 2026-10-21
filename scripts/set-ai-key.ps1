# Puts the OpenRouter key for the AI parent brief into secrets.properties at the repo root (gitignored), where
# app/build.gradle.kts reads it into BuildConfig.AI_KEY at build time (docs/PARENT-BRIEF-PLAN.md, "Option B").
#
#   .\scripts\set-ai-key.ps1            asks for the key (typed or pasted, never shown), keeps the model and base URL
#   .\scripts\set-ai-key.ps1 -Remove    takes the key out again (the next build writes every brief on the phone)
#
# The key never goes on the command line, into the console, a log or git. Set a spending cap on it at openrouter.ai and
# turn their prompt logging off first (CLAUDE.md, "2.4.0 parent brief", the manual check). Rebuild and install after.
param([switch]$Remove)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'

# Keep every other line of an existing file (AI_MODEL, AI_BASE_URL, anything added later).
$kept = @()
if (Test-Path $file) { $kept = @(Get-Content $file | Where-Object { $_ -notmatch '^\s*AI_KEY\s*=' }) }
if (-not ($kept | Where-Object { $_ -match '^\s*AI_MODEL\s*=' })) { $kept += 'AI_MODEL=anthropic/claude-haiku-5.5' }
if (-not ($kept | Where-Object { $_ -match '^\s*AI_BASE_URL\s*=' })) { $kept += 'AI_BASE_URL=https://openrouter.ai/api/v1' }

if ($Remove) {
    Set-Content -Path $file -Value $kept -Encoding ascii
    Write-Host "AI key removed from secrets.properties. Rebuild: the app writes every brief on the phone."
    return
}

$secure = Read-Host -AsSecureString 'OpenRouter key (sk-or-..., not shown)'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
if ($key -notmatch '^sk-or-[A-Za-z0-9_\-]{20,}$') { throw 'That does not look like an OpenRouter key (sk-or-...). Nothing was written.' }

Set-Content -Path $file -Value (@("AI_KEY=$key") + $kept) -Encoding ascii
$key = $null
# The file must never reach git: check the ignore rule is still there.
$ignored = git -C $root check-ignore -q secrets.properties; if ($LASTEXITCODE -ne 0) { throw 'secrets.properties is not gitignored: fix .gitignore before building.' }
Write-Host "Key saved to secrets.properties (gitignored). Build and install as usual: .\gradlew.bat installDebug"
