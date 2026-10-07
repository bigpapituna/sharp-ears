# Rebuilds app/src/main/assets/samples from the VSCO 2 Community Edition upright piano (CC0).
# Samples exist every 4 semitones (MIDI 21, 25, ... 105, plus 108); the Sampler pitch-shifts the keys between.
# Needs ffmpeg on PATH. Output: p<midi>.ogg, mono, 2 s with a 0.4 s fade, ~10 KB each.
param([string]$Dynamic = 'dyn2')
$src = 'https://raw.githubusercontent.com/sgossner/VSCO-2-CE/master/Keys/Upright%20Piano'
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'app\src\main\assets\samples'
$tmp = Join-Path $env:TEMP 'sharpears-samples'
New-Item -ItemType Directory -Force $out, $tmp | Out-Null
for ($i = 0; $i -le 44; $i += 2) {
    $midi = 21 + 2 * $i
    if ($i -eq 44) { $midi = 108 }
    $wav = Join-Path $tmp ("{0:D3}.wav" -f $i)
    Invoke-WebRequest ("$src/Player_{0}_rr1_{1:D3}.wav" -f $Dynamic, $i) -OutFile $wav -UseBasicParsing
    ffmpeg -loglevel error -y -i $wav -ac 1 -ar 44100 -t 2 -af "afade=t=out:st=1.6:d=0.4" -c:a libvorbis -q:a 3 (Join-Path $out "p$midi.ogg")
    if ($LASTEXITCODE) { throw "ffmpeg failed on $wav" }
}
Remove-Item $tmp -Recurse -Force
Get-ChildItem $out | Measure-Object Length -Sum | ForEach-Object { "{0} files, {1:N0} KB" -f $_.Count, ($_.Sum / 1KB) }
