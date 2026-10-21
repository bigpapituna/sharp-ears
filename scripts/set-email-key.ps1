# Puts the Brevo key and the verified sender for the parent brief by email into secrets.properties at the repo root
# (gitignored), where app/build.gradle.kts reads them into BuildConfig.EMAIL_KEY, EMAIL_FROM and EMAIL_FROM_NAME at build
# time (engine/brief/email/INTEGRATION.md, ui/brief/email/INTEGRATION.md).
#
#   .\scripts\set-email-key.ps1                 asks for the key (typed or pasted, never shown), then the sender address and name
#   .\scripts\set-email-key.ps1 -Remove         takes the key and sender out again (the next build cannot send email)
#   .\scripts\set-email-key.ps1 -ImageBase https://pictures.example.org/email
#                                               also sets where the pup pictures are hosted (https; blank keeps them inside the email)
#
# The key never goes on the command line, into the console, a log or git. At brevo.com: Senders, domains and dedicated IPs
# (verify the sender address), then SMTP and API, API keys (make a key), and turn on two-factor sign-in for the account.
# Rebuild and install after: .\gradlew.bat installDebug
param([switch]$Remove, [string]$ImageBase = '')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root 'secrets.properties'
$mine = '^\s*(EMAIL_KEY|EMAIL_FROM|EMAIL_FROM_NAME|EMAIL_IMAGE_BASE)\s*='

# Keep every other line of an existing file (AI_KEY, AI_MODEL, anything added later). EMAIL_BASE_URL is kept too.
$kept = @()
if (Test-Path $file) { $kept = @(Get-Content $file | Where-Object { $_ -notmatch $mine }) }

if ($Remove) {
    Set-Content -Path $file -Value $kept -Encoding ascii
    Write-Host "Email key and sender removed from secrets.properties. Rebuild: the app cannot send email."
    return
}

$secure = Read-Host -AsSecureString 'Brevo API key (xkeysib-..., not shown)'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim() }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
if ($key -notmatch '^xkeysib-[A-Za-z0-9_\-]{20,}$') { throw 'That does not look like a Brevo API key (xkeysib-...). Nothing was written.' }

$from = (Read-Host 'Verified sender address in Brevo (the From of every email)').Trim()
if ($from -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') { throw 'That does not look like an email address. Nothing was written.' }
$name = (Read-Host 'Sender name (Enter for "The pup at Sharp Ears")').Trim()
if ($name -eq '') { $name = 'The pup at Sharp Ears' }
if ($ImageBase -ne '' -and $ImageBase -notmatch '^https://') { throw 'The picture address must start with https://. Nothing was written.' }

$lines = @("EMAIL_KEY=$key", "EMAIL_FROM=$from", "EMAIL_FROM_NAME=$name")
if ($ImageBase -ne '') { $lines += "EMAIL_IMAGE_BASE=$($ImageBase.TrimEnd('/'))" }
Set-Content -Path $file -Value ($lines + $kept) -Encoding ascii
$key = $null
# The file must never reach git: check the ignore rule is still there.
git -C $root check-ignore -q secrets.properties; if ($LASTEXITCODE -ne 0) { throw 'secrets.properties is not gitignored: fix .gitignore before building.' }
Write-Host "Saved to secrets.properties (gitignored). Build and install as usual: .\gradlew.bat installDebug"
