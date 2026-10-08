"""Record every fixed line of the app with the Kokoro voice, into app/src/main/assets/voice/<key>.ogg (Ogg Opus).

The line list comes from SpokenCatalogTest (it writes app/build/voice-lines.txt):
    gradlew :app:testDebugUnitTest --tests "*SpokenCatalogTest*"
    C:\\dev\\voice-tools\\venv\\Scripts\\python -I scripts\\render_voice.py [speaker id, default 3 = af_heart, Roy's pick (Voice 1)]

Only new lines are recorded; changing the speaker records them all again. Lines no longer in the list are deleted.
"""
import sys
from pathlib import Path

import numpy as np
import sherpa_onnx
import soundfile as sf

M = Path(r"C:\dev\voice-tools\models\kokoro-multi-lang-v1_0")   # full-precision model: the PC has time, the phone does not
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/voice"
speaker = int(sys.argv[1]) if len(sys.argv) > 1 else 3
en = "gb" if 20 <= speaker <= 27 else "us"

lines = [l.split("\t", 1) for l in (ROOT / "app/build/voice-lines.txt").read_text(encoding="utf-8").splitlines() if l]
OUT.mkdir(parents=True, exist_ok=True)
marker = OUT / "speaker.txt"
if not marker.exists() or marker.read_text().strip() != str(speaker):
    for f in OUT.glob("*.ogg"):
        f.unlink()
keys = {k for k, _ in lines}
for f in OUT.glob("*.ogg"):
    if f.stem not in keys:
        f.unlink()

todo = [(k, t) for k, t in lines if not (OUT / f"{k}.ogg").exists()]
print(f"{len(lines)} lines, {len(todo)} to record, speaker {speaker}", flush=True)
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
        model=str(M / "model.onnx"), voices=str(M / "voices.bin"), tokens=str(M / "tokens.txt"),
        data_dir=str(M / "espeak-ng-data"), lexicon=f"{M / f'lexicon-{en}-en.txt'},{M / 'lexicon-zh.txt'}", lang=f"en-{en}"),
    num_threads=8)))
for i, (k, text) in enumerate(todo, 1):
    a = tts.generate(text, sid=speaker, speed=1.0)
    samples = np.asarray(a.samples, dtype=np.float32)
    assert a.sample_rate == 24000 and len(samples) > 2400, f"no audio for {text!r}"
    sf.write(OUT / f"{k}.ogg", samples, a.sample_rate, format="OGG", subtype="OPUS")
    if i % 50 == 0 or i == len(todo):
        print(f"{i}/{len(todo)}", flush=True)
marker.write_text(f"{speaker}\n")
size = sum(f.stat().st_size for f in OUT.glob("*.ogg"))
print(f"done: {len(list(OUT.glob('*.ogg')))} files, {size / 1e6:.1f} MB")
