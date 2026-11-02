"""2.19.0: pass counts of a Road walk (run_all.py's logs in OUT/walk): every Play, From memory, Perform and Ramp step scripted
with right notes should end passed=true; Perform and Ramp should log no WRONG key (QA B-01); Listen steps (analyze.py)
should sound every note. Prints per level and the totals."""
import glob, re, os
from lib import *
import walk
from analyze import analyze_lesson
SCRIPTED = ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP")
levels = {}
tot = dict(lessons=0, steps=0, passed=0, perform_steps=0, perform_wrong=0, perform_clean=0, listen=0, listen_ok=0, issues=0)
bad = []
for l in LESSONS:
    lid = l["id"]; lv = l.get("level", "?")
    logs = glob.glob(os.path.join(walk.WALK, f"{lid}-*.log"))
    if not logs: continue
    tot["lessons"] += 1; row = levels.setdefault(lv, [0, 0, 0])
    row[0] += 1
    for i, s in enumerate(l["steps"]):
        if s["kind"] not in SCRIPTED: continue
        p = os.path.join(walk.WALK, f"{lid}-{i}.log")
        if not os.path.exists(p): continue
        L = open(p, encoding="utf8").read().splitlines()
        k = i + 1  # 2.19.0: "sit" no longer has the first-run steps in front (2.18 added 2)
        ended = [x for x in L if re.search(r"lesson %s step %d ended" % (re.escape(lid), k), x)]
        ok = any("passed=true" in x for x in ended)
        tot["steps"] += 1; row[1] += 1
        if ok: tot["passed"] += 1; row[2] += 1
        else: bad.append((lid, i, s["kind"], ended[-1].split("SharpEars: ")[-1][:120] if ended else "never ended"))
        if s["kind"] in ("PERFORM", "RAMP"):
            w = sum(1 for x in L if re.search(r"input \S+ -> WRONG", x))
            tot["perform_steps"] += 1; tot["perform_wrong"] += w; tot["perform_clean"] += (w == 0)
    try:
        seen, _ = analyze_lesson(lid)
        for kk, v in seen.items():
            if v.get("kind") == "LISTEN":
                tot["listen"] += 1
                li = v.get("info", {}).get("listen") or v.get("listen")
                if not [i for i in v["issues"] if "LISTEN" in i[1] or "missing" in i[1]]: tot["listen_ok"] += 1
    except Exception as e:
        tot["issues"] += 1
for lv, (n, st, ps) in levels.items(): print(f"{lv}: {n} lessons, {ps}/{st} scripted steps passed")
print(tot)
for b in bad: print("NOT PASSED", b)
