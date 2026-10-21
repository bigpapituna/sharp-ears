"""Record every fixed line of the app with the Kokoro voice, into app/src/main/assets/voice/<key>.ogg (Ogg Opus).

The line list comes from SpokenCatalogTest (it writes app/build/voice-lines.txt: key, text and speed; 2.1 records the kid
lines at 0.85, the pup speaks slower to kids):
    gradlew :app:testDebugUnitTest --tests "*SpokenCatalogTest*"
    C:\\dev\\voice-tools\\venv\\Scripts\\python -I scripts\\render_voice.py [speaker id, default 3 = af_heart, Roy's pick (Voice 1)]

Only new lines are recorded; changing the speaker records them all again. Lines no longer in the list are deleted.

    python -I scripts/render_voice.py --check
changes nothing and needs no model: it prints the lines a run would record and the files it would delete, says whether
the list is older than the code, and exits 1 when a run is needed. A run refuses a list older than the code (it would
delete recordings of lines still in use) unless --force is given.
"""
import sys
from pathlib import Path

M = Path(r"C:\dev\voice-tools\models\kokoro-multi-lang-v1_0")   # full-precision model: the PC has time, the phone does not
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/voice"
DUMP = ROOT / "app/build/voice-lines.txt"
flags = {a for a in sys.argv[1:] if a.startswith("--")}
args = [a for a in sys.argv[1:] if not a.startswith("--")]
speaker = int(args[0]) if args else 3
en = "gb" if 20 <= speaker <= 27 else "us"

rows = [l.split("\t") for l in DUMP.read_text(encoding="utf-8").split("\n") if l]
lines = [(r[0], r[1]) for r in rows]
speeds = {r[0]: float(r[2]) if len(r) > 2 else 1.0 for r in rows}
keys = {k for k, _ in lines}
marker = OUT / "speaker.txt"
respeak = not marker.exists() or marker.read_text().strip() != str(speaker)
# The list is written by a test from the Kotlin sources: older than them, it may lack lines still in use.
stale = DUMP.stat().st_mtime < max(f.stat().st_mtime for f in (ROOT / "app/src/main/java").rglob("*.kt"))

if "--check" in flags:
    files = {f.stem for f in OUT.glob("*.ogg")}
    missing = [(k, t) for k, t in lines if k not in files]
    orphans = sorted(files - keys)
    print(f"lines {len(lines)} ({sum(1 for s in speeds.values() if s != 1.0)} kid at 0.85) | files {len(files)} | "
          f"speaker {marker.read_text().strip() if marker.exists() else 'none'} (asked {speaker})")
    print("list: " + ("STALE, older than the code: run SpokenCatalogTest first" if stale else "newer than the code"))
    if respeak:
        print(f"speaker change: all {len(files)} files would be recorded again")
    print(f"to record {len(missing)}:")
    for k, t in missing:
        print(f"  {k}  {speeds[k]}  {t}")
    print(f"to delete {len(orphans)}:")
    for k in orphans:
        print(f"  {k}")
    sys.exit(1 if missing or orphans or stale or respeak else 0)
if stale and "--force" not in flags:
    sys.exit(f"{DUMP} is older than the code: run SpokenCatalogTest first (or pass --force)")

import numpy as np
import sherpa_onnx
import soundfile as sf

OUT.mkdir(parents=True, exist_ok=True)
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
        model=str(M / "model.onnx"), voices=str(M / "voices.bin"), tokens=str(M / "tokens.txt"),
        data_dir=str(M / "espeak-ng-data"), lexicon=f"{M / f'lexicon-{en}-en.txt'},{M / 'lexicon-zh.txt'}", lang=f"en-{en}"),
    num_threads=8)))
# Deleted only once the model has loaded: a run that cannot record leaves the recordings as they were.
if respeak:
    for f in OUT.glob("*.ogg"):
        f.unlink()
for f in OUT.glob("*.ogg"):
    if f.stem not in keys:
        f.unlink()

todo = [(k, t) for k, t in lines if not (OUT / f"{k}.ogg").exists()]
print(f"{len(lines)} lines, {len(todo)} to record ({sum(1 for k, _ in todo if speeds[k] != 1.0)} slower, for kids), speaker {speaker}", flush=True)
for i, (k, text) in enumerate(todo, 1):
    a = tts.generate(text, sid=speaker, speed=speeds[k])
    samples = np.asarray(a.samples, dtype=np.float32)
    assert a.sample_rate == 24000 and len(samples) > 2400, f"no audio for {text!r}"
    sf.write(OUT / f"{k}.ogg", samples, a.sample_rate, format="OGG", subtype="OPUS")
    if i % 50 == 0 or i == len(todo):
        print(f"{i}/{len(todo)}", flush=True)
marker.write_text(f"{speaker}\n")
size = sum(f.stat().st_size for f in OUT.glob("*.ogg"))
print(f"done: {len(list(OUT.glob('*.ogg')))} files, {size / 1e6:.1f} MB")
