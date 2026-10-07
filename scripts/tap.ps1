# Taps an on-screen element by its exact text or content description, using a uiautomator dump.
# Usage: .\scripts\tap.ps1 "Wait"      (honors $env:ANDROID_SERIAL)
param([Parameter(Mandatory)][string]$Label)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
$xml = (& $adb shell cat /sdcard/ui.xml) -join ''
# Compose often puts the text on a child node; walk nodes and take the first whose text or description matches.
foreach ($n in [regex]::Matches($xml, '<node [^>]*>')) {
    $v = $n.Value
    if ($v -match "(text|content-desc)=`"$([regex]::Escape($Label))`"" -and $v -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $x = ([int]$Matches[1] + [int]$Matches[3]) / 2; $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
        & $adb shell input tap $x $y
        Write-Host "tapped '$Label' at $x,$y"
        exit 0
    }
}
Write-Error "no element labelled '$Label' on screen"; exit 1
