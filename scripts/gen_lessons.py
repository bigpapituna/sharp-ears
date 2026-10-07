import json, os, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
def chords(*names): return [{"type": "chord", "chord": n} for n in names]
packs = [
    ("01-major-triads", "Major triads", 1, [], "The three-note major chord in the common keys.",
     chords("C", "F", "G", "D", "A", "E", "Bb", "Eb")),
    ("02-minor-triads", "Minor triads", 1, [], "Minor chords: lower the middle note of a major chord by a half step.",
     chords("Am", "Dm", "Em", "Bm", "F#m", "Cm", "Gm", "Fm")),
    ("03-all-keys", "Triads in every key", 2, ["01-major-triads", "02-minor-triads"], "The remaining major and minor chords, black-key roots included.",
     chords("Ab", "Db", "B", "F#", "C#m", "G#m", "Ebm", "Bbm")),
    ("04-sevenths", "Seventh chords", 3, ["01-major-triads", "02-minor-triads"], "Major 7, minor 7 and dominant 7: the colours of jazz and soul.",
     chords("Cmaj7", "Fmaj7", "Gmaj7", "Dm7", "Em7", "Am7", "G7", "C7", "D7", "A7")),
    ("05-sus-dim", "Sus and diminished", 3, ["01-major-triads", "02-minor-triads"], "Suspended chords leave out the third; diminished chords stack two minor thirds.",
     chords("Csus2", "Csus4", "Dsus2", "Dsus4", "Gsus4", "Asus2", "Bdim", "F#dim")),
    ("06-progressions", "Progressions in five keys", 4, ["04-sevenths"], "Play whole progressions in wait mode: right hand chords, left hand roots.",
     [{"type": "progression", "progression": p, "key": k} for p, k in [(0, 0), (0, 7), (0, 5), (2, 0), (2, 7), (1, 0), (1, 5), (3, 0), (3, 7)]]),
]
for pid, title, level, req, about, items in packs:
    json.dump({"id": pid, "title": title, "level": level, "requires": req, "about": about, "items": items},
              open(os.path.join(out, pid + ".json"), "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print(len(packs), "packs")
