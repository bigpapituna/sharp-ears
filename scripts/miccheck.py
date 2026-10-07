"""Scores a live Mic check from logcat: the mic's detections against MIDI truth.

Usage: adb logcat -d -s SharpEars:D > out/miccheck.log ; python scripts/miccheck.py out/miccheck.log
Truth lines:  "truth NoteOn C4 midi=60 vel=64"   (MIDI, while the mic drives wait mode)
Mic lines:    "NoteOn C4 midi=60 src=mic rise=-31.2dB"
A truth note counts as heard if the mic reported the same key within 300 ms after it.
A mic strike with no truth note of that key within 300 ms before it is a false trigger.
"""
import re
import sys
from datetime import datetime
from statistics import median

WINDOW = 0.300
line_re = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*SharpEars: (truth NoteOn|NoteOn) (\S+) midi=(\d+).*?(src=mic)?")


def parse(path):
    truth, mic = [], []
    for line in open(path, encoding="utf-8", errors="replace"):
        m = line_re.search(line)
        if not m:
            continue
        t = datetime.strptime("2026-" + m.group(1), "%Y-%m-%d %H:%M:%S.%f").timestamp()
        midi = int(m.group(4))
        if m.group(2) == "truth NoteOn":
            truth.append((t, midi))
        elif "src=mic" in line:
            mic.append((t, midi))
    return truth, mic


def main(path):
    truth, mic = parse(path)
    used = set()
    latencies = []
    for t, k in truth:
        hit = next((i for i, (tm, km) in enumerate(mic) if i not in used and km == k and 0 <= tm - t <= WINDOW), None)
        if hit is not None:
            used.add(hit)
            latencies.append((mic[hit][0] - t) * 1000)
    false = [m for i, m in enumerate(mic) if i not in used and not any(k == m[1] and 0 <= m[0] - t <= WINDOW for t, k in truth)]
    n = len(truth)
    print(f"truth notes {n}, heard {len(latencies)} ({100 * len(latencies) / max(n, 1):.0f}%), "
          f"false triggers {len(false)}, median latency {median(latencies):.0f} ms" if latencies else
          f"truth notes {n}, heard 0, false triggers {len(false)}")
    if false:
        print("false:", ", ".join(f"midi {k}" for _, k in false))


if __name__ == "__main__":
    main(sys.argv[1])
