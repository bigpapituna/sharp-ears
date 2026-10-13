# Theory Academy screenshots on the emulator (debug build): each argument is lesson:scene:delaySeconds[:part].
# .\scripts\theory-shots.ps1 read-staff:1:1.5 sc-circle:0:4  -> out\theory-<lesson>-<scene>.png
param([string[]]$Shots)
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
$serial = if ($env:ANDROID_SERIAL) { $env:ANDROID_SERIAL } else { "emulator-5554" }
New-Item -ItemType Directory -Force out | Out-Null
foreach ($s in $Shots) {
    $p = $s.Split(":")
    $extra = @()
    if ($p.Length -gt 3) { $extra = @("--es", "part", $p[3], "--ei", "question", $p[1]) } else { $extra = @("--ei", "scene", $p[1]) }
    adb -s $serial shell am start -S -n com.bigpapituna.sharpears/.MainActivity --es screen theory --es theory $p[0] @extra | Out-Null
    Start-Sleep -Milliseconds ([int]([double]$p[2] * 1000) + 1200)
    $name = "out\theory-$($p[0])-$($p[1])$(if ($p.Length -gt 3) { '-' + $p[3] }).png"
    adb -s $serial shell screencap -p /sdcard/s.png
    adb -s $serial pull /sdcard/s.png $name | Out-Null
    Write-Output $name
}
