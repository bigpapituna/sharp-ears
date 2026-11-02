"""2.19.0: QA B-02 repeat. Launch Read/Listen lessons N times (force-stop between) and count lines said by the phone's TTS,
the listing misses AssetIndex rescued, and the clip prepare times (B-07). Usage: python b02.py [N]"""
import re, sys, time, statistics
from lib import *
N = int(sys.argv[1]) if len(sys.argv) > 1 else 60
LESSONS = ["black-keys", "first-melody", "rhythm-eighths", "broken", "independence"]
tot = dict(launches=0, recorded=0, system=0, model=0, rescued=0, bad_launches=0)
preps, first_preps, firsts = [], [], []
out = open(os.path.join(OUT, "b02-launches.txt"), "w", encoding="utf8")
for i in range(N):
    lid = LESSONS[i % len(LESSONS)]
    try:
        start("--es", "lesson", lid, "--ei", "step", "0"); time.sleep(9)
        L = log(); adb("shell", "am", "force-stop", PKG)
    except Exception as e:
        print(i, lid, "adb failed:", repr(e), file=out); continue
    src = [m[1] for ln in L for m in [re.search(r"voice: u\d+ sounds \d+ ms after it was asked \((\w+)\)", ln)] if m]
    resc = sum(1 for ln in L if "the listing missed" in ln)
    pp = [int(m[1]) for ln in L for m in [re.search(r"clip: \S+ started, prepared in (\d+) ms", ln)] if m]
    tot["launches"] += 1; tot["rescued"] += resc
    for s in src: tot[s if s in ("system", "model") else "recorded"] += 1
    if "system" in src: tot["bad_launches"] += 1
    preps += pp; first_preps += pp[:1]
    out.write(f"{i} {lid} sources={src} rescued={resc} prep={pp}\n"); out.flush()
q = lambda xs, p: sorted(xs)[min(len(xs) - 1, int(p * len(xs)))] if xs else None
summary = dict(tot, prep_median=statistics.median(preps) if preps else None, prep_p95=q(preps, .95), prep_max=max(preps) if preps else None,
               over500=sum(1 for p in preps if p > 500), clips=len(preps), first_prep_median=statistics.median(first_preps) if first_preps else None,
               first_prep_max=max(first_preps) if first_preps else None)
out.write(str(summary) + "\n"); print(summary)
