# Puts the GitHub token the phone uses to send stuck moments, flags, crashes and summaries into Sharp Ears' private
# storage on the phone. The token never goes into the code or the repo.
# Make it at github.com > Settings > Developer settings > Fine-grained tokens:
#   Repository access: only bigpapituna/sharp-ears.  Permissions: Contents read and write, Issues read and write.
# Usage: .\scripts\set-report-token.ps1 [-Serial 10.0.0.171:5555]     (asks for the token; nothing is shown or saved here)
param([string]$Serial = "", [string]$Repo = "bigpapituna/sharp-ears")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$a = @(); if ($Serial) { $a = @("-s", $Serial) }
$pkg = "com.bigpapituna.sharpears"
$secure = Read-Host "Paste the token" -AsSecureString
$token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if (-not $token) { Write-Host "No token given."; exit 1 }
$xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=`"token`">$token</string><string name=`"repo`">$Repo</string><boolean name=`"enabled`" value=`"true`" /></map>"
$tmp = New-TemporaryFile
try {
    [IO.File]::WriteAllText($tmp, $xml)
    & $adb @a shell am force-stop $pkg                       # the app would overwrite the file on exit
    & $adb @a push $tmp /data/local/tmp/reports.xml | Out-Null
    & $adb @a shell "run-as $pkg sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/reports.xml shared_prefs/reports.xml && chmod 660 shared_prefs/reports.xml'; rm /data/local/tmp/reports.xml"
} finally { Remove-Item $tmp -Force; $token = $null }
Write-Host "Done. Open Sharp Ears: Microphone setup shows 'Send them for review'."
