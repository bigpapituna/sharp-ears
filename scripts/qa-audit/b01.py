"""2.19.0: re-run the 26 steps of QA bug B-01 (chords and two hands in Perform, Ramp, Pedal) and count WRONG flags."""
import re, sys
from lib import *
import walk, special
STEPS = [("progression", 4)] + [(l, 2) for l in ("five-c", "five-g", "five-f", "five-d", "five-a", "hanon-1")] + [("contrary", 3)] + \
    [(l, 4) for l in ("rep-mary", "rep-buns", "rep-row", "rep-london", "rep-yankee", "rep-macdonald", "rep-lightly", "rep-wewish",
                      "rep-eine", "rep-gymno", "rep-toccata", "rep-scarborough")] + [("t2-scales", 1), ("t2-scales", 2), ("t2-scales", 3), ("t2-broken", 3)]
def count(lines):
    wrong = sum(1 for ln in lines if re.search(r"input \S+ -> WRONG", ln))
    rep = [ln.split("SharpEars: ", 1)[-1][:160] for ln in lines if re.search(r"Report\(|wrongKeys=|ramp: pass", ln)]
    said = [ln.split("SharpEars: ", 1)[-1][:120] for ln in lines if "voice:" in ln and ("missed" in ln or "Clean" in ln or "percent" in ln)]
    return wrong, rep, said
rows = []
for lid, i in STEPS:
    if sys.argv[1:] and lid not in sys.argv[1:]: continue
    try:
        s = walk.Session(lid, i); r = s.run(); w, rep, said = count(s.lines)
    except Exception as e:
        rows.append((lid, i, "?", f"adb failed {e!r}"[:60], 0, "", "")); continue
    rows.append((lid, i, lesson(lid)["steps"][i]["kind"], r, w, rep[-1] if rep else "", said[-1] if said else ""))
    print(rows[-1], flush=True)
P = 8000
for lid, i0, chords in (("pedal", 2, [(0, [60, 64, 67]), (4000, [60, 64, 69]), (8000, [60, 65, 69]), (12000, [59, 62, 67])]),
                        ("t2-pedal-progression", 3, [(0, [60, 64, 67]), (4000, [59, 62, 67]), (8000, [60, 64, 69]), (12000, [60, 65, 69])])):
    if sys.argv[1:] and lid not in sys.argv[1:]: continue
    items = [(P + s + 20 + 6 * k, m, 80) for s, ms in chords for k, m in enumerate(ms)]
    ped = ",".join(f"{P+s+100}:p1,{P+s+3700}:p0" for s, _ in chords)
    lines = special.run(lid, i0, midi_str(items) + "," + ped, 45); w, rep, said = count(lines)
    rows.append((lid, i0, "PEDAL", "-", w, rep[-1] if rep else "", said[-1] if said else "")); print(rows[-1], flush=True)
open(os.path.join(OUT, "b01-summary.txt"), "w", encoding="utf8").write("\n".join(map(str, rows)))
print("steps", len(rows), "with WRONG flags", sum(1 for r in rows if r[4]), "total WRONG", sum(r[4] for r in rows))
