import sys, os, json, time, traceback
from lib import *
import walk
only = sys.argv[1:]  # optional lesson ids
prog = os.path.join(OUT, "walk-progress.txt")
def note(msg):
    open(prog, "a", encoding="utf8").write(time.strftime("%H:%M:%S ") + msg + "\n")
for l in LESSONS:
    if only and l["id"] not in only: continue
    idxs = [0] + [i for i, s in enumerate(l["steps"]) if s["kind"] in ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP") and i != 0]
    for i in idxs:
        p = os.path.join(walk.WALK, f"{l['id']}-{i}.log")
        if os.path.exists(p) and not only: continue
        try:
            s = walk.Session(l["id"], i)
            r = s.run()
            note(f"{l['id']} step {i} {l['steps'][i]['kind']}: {r}")
        except Exception as e:
            note(f"{l['id']} step {i} EXC {e!r}")
            traceback.print_exc()
note("ALL DONE")
