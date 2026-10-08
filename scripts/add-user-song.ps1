# Puts a personal MIDI file into the app's private song folder over adb (debug builds only, via run-as),
# the same place the in-app "Import a MIDI file" copies to. Personal files never go into the repo.
#   New library song:   .\scripts\add-user-song.ps1 path\to\song.mid -Id user-clair-obscur-medley -Title "Clair Obscur medley" -Composer "Lorien Testard"
#   Fill a bundled slot (Alicia): .\scripts\add-user-song.ps1 path\to\alicia.mid -Slot alicia
# Honors $env:ANDROID_SERIAL.
param([Parameter(Mandatory)][string]$File, [string]$Id, [string]$Title, [string]$Composer = "", [string]$Slot)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.bigpapituna.sharpears"
$name = if ($Slot) { $Slot } else { $Id }
if (-not $name) { Write-Error "give -Id (new song) or -Slot (bundled entry)"; exit 1 }
& $adb push $File "/data/local/tmp/$name.mid" | Out-Null
& $adb shell "run-as $pkg sh -c 'mkdir -p files/user && cp /data/local/tmp/$name.mid files/user/$name.mid'"
& $adb shell rm "/data/local/tmp/$name.mid"
if (-not $Slot) {
    # Merge the entry into files/user/index.json (the format SongRepository writes).
    $raw = (& $adb shell "run-as $pkg cat files/user/index.json" 2>$null) -join "`n"
    $index = if ($raw -match '"songs"') { $raw | ConvertFrom-Json } else { [pscustomobject]@{ songs = @() } }
    $songs = @($index.songs | Where-Object { $_.id -ne $Id })
    $songs += [pscustomobject]@{ id = $Id; file = "user/$Id.mid"; title = $Title; composer = $Composer; difficulty = "imported"
        hands = "auto"; license = "personal use, not bundled"; attribution = "Added from your computer" }
    $tmp = New-TemporaryFile
    [IO.File]::WriteAllText($tmp, ([pscustomobject]@{ songs = $songs } | ConvertTo-Json -Depth 4), (New-Object Text.UTF8Encoding $false))
    & $adb push $tmp "/data/local/tmp/index.json" | Out-Null
    & $adb shell "run-as $pkg sh -c 'cp /data/local/tmp/index.json files/user/index.json'"
    & $adb shell rm /data/local/tmp/index.json
    Remove-Item $tmp
}
& $adb shell "run-as $pkg ls -l files/user"
