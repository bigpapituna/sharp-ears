"""
2.19.0: pacing with a coach voice on (Roy: "a delay in audio"). For each step: step open -> the coach's start line (if any)
-> the step's own instruction starts; and at the step's end: ended -> the coach's line, ended -> the feedback line, and the
moment the last of them is done (its start plus its length). Also lists the coach's held reactions (2.19.0) and drops.
Usage: python pace.py <tag> [voice]   (plants the coach on the grown-up profile, clears it at the end)
"""
import re, sys, os
from lib import *
import walk, plant

TAG, VOICE = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "male_us")
STEPS = [("first-melody", 2), ("first-melody", 4), ("fingers-right", 2), ("bass", 2), ("together", 3), ("rep-eine", 4)]
PW = os.path.join(OUT, "pace-" + TAG); os.makedirs(PW, exist_ok=True)
walk.WALK = PW


def msg(ln): return ln.split("SharpEars: ", 1)[-1]


def first(lines, pat, after):
    for ln in lines:
        t = ts(ln)
        if t is not None and t >= after and re.search(pat, msg(ln)): return t, msg(ln)
    return None, None


plant.plant_coach("progress", VOICE, "w")
rows = []
for lid, i in STEPS:
    if not lesson(lid)["steps"][i]["kind"] in walk.INTERACTIVE: continue
    s = walk.Session(lid, i); r = s.run(); L = s.lines
    step = lesson(lid)["steps"][i]; k = i + 1
    t_open, _ = first(L, r"lesson %s step %d/" % (re.escape(lid), k), 0)
    if t_open is None: rows.append((lid, k, "no open")); continue
    words = re.escape(step["text"].replace("\n\n", " ")[:24])
    t_start, start_line = first(L, r"coach: start ", t_open)
    t_instr, _ = first(L, r"voice: (\((recorded|narrated [^)]*)\) )?" + words, t_open)
    t_auto, _ = first(L, r"lesson step auto-start", t_open)
    t_end, end_line = first(L, r"lesson %s step %d ended" % (re.escape(lid), k), t_open)
    row = dict(lesson=lid, step=k, kind=step["kind"], result=r,
               open_to_instruction=(t_instr - t_open) if t_instr else None,
               open_to_play=(t_auto - t_open) if t_auto else None,
               start_line=(t_start - t_open) if t_start else None)
    if t_end:
        fb = re.search(r'feedback="(.*)"', end_line)
        fb = fb[1] if fb else ""
        t_fb, _ = first(L, r"voice: (\([^)]*\) )?" + re.escape(fb[:20]), t_end) if fb else (None, None)
        t_coach, cl = first(L, r"coach: (step_passed|mistake|recovery|run|nudge|hard|halfway|steady|focus|hard_passed) \S+ \"", t_end)
        done = []
        if t_coach:
            m = re.search(r'" (\d+) ms', cl); done.append(t_coach + int(m[1]) if m else t_coach)
        row.update(end_to_feedback=(t_fb - t_end) if t_fb else None, end_to_coach=(t_coach - t_end) if t_coach else None,
                   coach_line=(cl or "")[:70], end_to_coach_done=(max(done) - t_end) if done else None)
    row["held"] = [msg(l)[:110] for l in L if re.search(r"coach: .*(held|said at a pause|said for|dropped|left to the pup)", msg(l))]
    rows.append(row); print(row, flush=True)
plant.clear_coach("progress")
open(os.path.join(OUT, f"pace-{TAG}.txt"), "w", encoding="utf8").write("\n".join(map(str, rows)))
