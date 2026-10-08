# Puts the Kokoro voice model (int8, about 155 MB) into the app's private storage, files/kokoro, so the phone can
# speak lines that are not recorded. Installs and updates of the app keep it; uninstalling removes it.
#   scripts\install-voice.ps1 [-Serial 10.0.0.171:5555]
param([string]$Serial = "", [string]$Model = "C:\dev\voice-tools\models\kokoro-int8-multi-lang-v1_0")
# adb prints progress on stderr, so check exit codes instead of using ErrorActionPreference=Stop.
$pkg = "com.bigpapituna.sharpears"
$adb = @(); if ($Serial) { $adb = @("-s", $Serial) }
$tmp = "/data/local/tmp/kokoro"

& adb @adb shell "rm -rf $tmp && mkdir -p $tmp"
foreach ($f in "model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt", "espeak-ng-data") {
    Write-Host "pushing $f"
    & adb @adb push "$Model\$f" "$tmp/" | Out-Null
    if ($LASTEXITCODE) { throw "push failed: $f" }
}
& adb @adb shell "chmod -R a+rX $tmp && run-as $pkg sh -c 'rm -rf files/kokoro && mkdir -p files && cp -r $tmp files/kokoro' && rm -rf $tmp"
& adb @adb shell "run-as $pkg ls -la files/kokoro"
Write-Host "Installed. Restart the app to load the voice."
