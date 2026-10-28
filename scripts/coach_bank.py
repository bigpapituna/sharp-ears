"""2.16 coach voice: converts Roy's ElevenLabs coach banks into the afterdark source set (CLAUDE.md "2.16 coach voice").

Layout read (coach_bank_v2): <bank>/<voice>/index.json ([{file, moment, for, text}], for = "any", "w" or "m") and
<bank>/<voice>/<file>.mp3. Every folder with an index.json is a voice; files and folders starting with _ are ignored. The bank
is never modified.

Layout written: app/src/afterdark/assets/coach/<voice>/<stem>.ogg (Ogg Opus, mono, 48 kHz, 24 kb/s, levelled to -23 LUFS with a
two-pass linear loudnorm, metadata dropped), .../<voice>/lines.json ([{id, moment, for, caption, ms}]: the caption is the text
with every [...] delivery tag taken out) and .../<voice>/voice.json ({id, name, order}: the name Settings shows and where in its list, from NAMES below or
made from the folder's name). A new voice folder is a new option in Settings with no change to the app. Voices no longer in the
bank are removed.

The course narrated in a coach's voice (2.16 hook, NaturalVoice via Extras.narration): <bank>/../course_<voice>/index.json
([{key, orig, coach, for}], key = SpokenCatalog.key(orig)) with <key>.mp3 (for "any"), <key>_w.mp3 and <key>_m.mp3, becomes
app/src/afterdark/assets/coach/<voice>/course/<key>[_w|_m].ogg (same settings). A voice with no such folder keeps an empty
course folder: its lines fall back to the normal voice. 2.16.2: with app/build/voice-lines.txt there (SpokenCatalogTest writes
it: run the tests first), only keys still in the catalog ship (orphans are skipped and named), the grown-up lines (speed 1.0)
with no recording are listed (they fall back to the normal voice), and course .ogg files no longer wanted are removed. The
course's files convert in parallel (about 2,600 for one voice).

Run from the repo root:  python scripts/coach_bank.py [bank] [ffmpeg.exe]
"""
import json, os, re, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BANK = Path(sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\roy_y\OneDrive\Desktop\Claude Code\tts-bakeoff\elevenlabs\coach_bank_v2")
FFMPEG = sys.argv[2] if len(sys.argv) > 2 else (r"C:\Users\roy_y\AppData\Local\Microsoft\WinGet\Packages"
    r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.2-full_build\bin\ffmpeg.exe")
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "afterdark" / "assets" / "coach"
CATALOG = ROOT / "app" / "build" / "voice-lines.txt"      # key, text, speed (1.0 grown-up, 0.85 kid), tab-separated
MOMENTS = ["start", "run5", "run10", "run20", "run40", "run", "recovery", "halfway", "hard_passed", "steady", "focus5", "focus10",
           "focus15", "focus20", "focus30", "nudge", "step_passed", "mistake", "hard", "finish", "stars3", "stars2", "comeback",
           "streak", "goodnight"]
# The names Settings shows (short and human); a folder not listed gets one made from its name ("female_au": "Australian female").
NAMES = {"male_us": "Liam (American)", "male_irish": "Rory (Irish)", "female_us": "Claire (American)", "female_au": "Sienna (Australian)"}
PLACES = {"us": "American", "irish": "Irish", "au": "Australian", "uk": "British", "gb": "British", "ca": "Canadian", "nz": "New Zealand"}
TARGET = "I=-23:TP=-2:LRA=11"


def name_of(voice: str) -> str:
    if voice in NAMES:
        return NAMES[voice]
    parts = voice.split("_")
    sex = parts[0] if parts[0] in ("male", "female") else ""
    place = " ".join(PLACES.get(p, p.capitalize()) for p in parts if p != sex)
    return f"{place} {sex}".strip() or voice


def caption(text: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"\[[^\]]*\]", "", text)).strip()


def measure(src: Path) -> dict:
    r = subprocess.run([FFMPEG, "-hide_banner", "-nostats", "-i", str(src), "-af", f"loudnorm={TARGET}:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return json.loads(r.stderr[r.stderr.rindex("{"):r.stderr.rindex("}") + 1])


def convert(src: Path, dst: Path):
    m = measure(src)
    norm = (f"loudnorm={TARGET}:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
            f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-map_metadata", "-1", "-af", norm + ",aresample=48000",
                    "-ac", "1", "-c:a", "libopus", "-b:a", "24k", "-application", "voip", str(dst)], check=True)


def duration_ms(path: Path) -> int:
    r = subprocess.run([FFMPEG, "-hide_banner", "-i", str(path), "-f", "null", "-"], capture_output=True, text=True, errors="replace")
    t = re.findall(r"time=(\d+):(\d+):([\d.]+)", r.stderr)[-1]
    return int((int(t[0]) * 3600 + int(t[1]) * 60 + float(t[2])) * 1000)


def voice(folder: Path):
    v = folder.name
    out = OUT / v
    out.mkdir(parents=True, exist_ok=True)
    index = json.loads((folder / "index.json").read_text(encoding="utf-8"))
    lines = []
    for e in index:
        f = e["file"]
        if f.startswith("_") or "/" in f or "\\" in f:
            continue
        if e["moment"] not in MOMENTS:
            sys.exit(f"{v}: unknown moment {e['moment']} in {f}")
        if e.get("for", "any") not in ("any", "w", "m"):
            sys.exit(f"{v}: unknown for {e.get('for')} in {f}")
        dst = out / (Path(f).stem + ".ogg")
        if not dst.exists():
            convert(folder / f, dst)
        lines.append({"id": Path(f).stem, "moment": e["moment"], "for": e.get("for", "any"), "caption": caption(e["text"]), "ms": duration_ms(dst)})
    for m in MOMENTS:
        for sex in ("w", "m"):
            if not any(l["moment"] == m and l["for"] in ("any", sex) for l in lines):
                sys.exit(f"{v}: no line for {m} for {sex}")
    keep = {l["id"] + ".ogg" for l in lines}
    for old in out.glob("*.ogg"):
        if old.name not in keep:
            old.unlink()
    (out / "lines.json").write_text(json.dumps(lines, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    (out / "voice.json").write_text(json.dumps({"id": v, "name": name_of(v), "order": list(NAMES).index(v) if v in NAMES else 99}, ensure_ascii=False) + "\n", encoding="utf-8")
    size = sum(p.stat().st_size for p in out.glob("*.ogg"))
    print(f"{v} ({name_of(v)}): {len(lines)} lines, {size / 1e6:.2f} MB")
    course(v, out / "course")
    return v


def catalog():
    """The spoken catalog as {key: (text, speed)}, or None when the tests have not written it."""
    if not CATALOG.exists():
        return None
    out = {}
    for line in CATALOG.read_text(encoding="utf-8").splitlines():
        k, text, speed = line.split("\t")
        out[k] = (text, float(speed))
    return out


def course(v: str, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    (out / ".gitkeep").touch()          # git keeps the empty folder; aapt leaves dot files out of the APK
    folder = BANK.parent / f"course_{v}"
    if not (folder / "index.json").exists():
        return
    cat = catalog()
    if cat is None:
        print(f"{v} course: no {CATALOG} (run SpokenCatalogTest first): converting every recording, orphans unchecked")
    jobs, orphans, keys = [], [], set()
    for e in json.loads((folder / "index.json").read_text(encoding="utf-8")):
        who = e.get("for", "any")
        if who not in ("any", "w", "m"):
            sys.exit(f"{v} course: unknown for {who} in {e['key']}")
        stem = e["key"] if who == "any" else f"{e['key']}_{who}"
        if cat is not None and e["key"] not in cat:
            orphans.append(f"{stem}: {e.get('orig', '')}")
            continue
        src = folder / f"{stem}.mp3"
        if not src.exists():
            print(f"{v} course: missing {src.name}")
            continue
        jobs.append((src, out / f"{stem}.ogg"))
        keys.add(e["key"])
    todo = [(s, d) for s, d in jobs if not d.exists()]
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        for i, _ in enumerate(pool.map(lambda j: convert(*j), todo), 1):
            if i % 200 == 0:
                print(f"{v} course: {i}/{len(todo)} converted")
    keep = {d.name for _, d in jobs}
    for old in out.glob("*.ogg"):
        if old.name not in keep:
            old.unlink()
    size = sum(d.stat().st_size for _, d in jobs)
    print(f"{v} course: {len(jobs)} narrated files for {len(keys)} lines, {size / 1e6:.2f} MB ({len(todo)} converted now)")
    if orphans:
        print(f"{v} course: {len(orphans)} orphans skipped (key no longer in the catalog):")
        for o in orphans:
            print(f"  {o}")
    if cat is not None:
        grown = {k for k, (_, speed) in cat.items() if speed == 1.0}
        missing = sorted(grown - keys, key=lambda k: cat[k][0])
        print(f"{v} course: {len(grown & keys)} of {len(grown)} grown-up lines narrated, {len(missing)} fall back to the normal voice")
        for k in missing:
            print(f"  {k}: {cat[k][0]}")


def main():
    voices = [voice(d) for d in sorted(BANK.iterdir()) if d.is_dir() and not d.name.startswith("_") and (d / "index.json").exists()]
    if not voices:
        sys.exit("no voice folders")
    for stale in list(OUT.iterdir()):
        if stale.is_dir() and stale.name not in voices:
            shutil.rmtree(stale)
        elif stale.is_file():
            stale.unlink()          # the first layout's flat files
    size = sum(p.stat().st_size for p in OUT.rglob("*.ogg"))
    print(f"done: {len(voices)} voices, {size / 1e6:.2f} MB of audio")


if __name__ == "__main__":
    main()
