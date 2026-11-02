import re, os, glob, collections
from lib import *
CW = os.path.join(OUT, "coach")
res = collections.defaultdict(lambda: collections.Counter()); fallbacks = collections.defaultdict(set); coachlines = collections.defaultdict(list)
over = []
for p in sorted(glob.glob(CW + "/*.log")):
    b = os.path.basename(p)[:-4]; v = b.split("-")[0] if b.startswith("male_us") or b.startswith("female") or b.startswith("male_irish") else b
    v = re.match(r"(male_us|male_irish|female_us|female_au)", b)[1]
    ls = open(p, encoding="utf8").read().splitlines()
    for ln in ls:
        m = re.search(r"voice: \(narrated ([^)]*)\) (.*)", ln)
        if m:
            res[v]["narrated"] += 1
            if f"coach/{v}/" not in m[1]: res[v]["WRONG-VOICE-FILE"] += 1; fallbacks[v].add("wrong voice file: " + m[1])
            continue
        m = re.search(r"voice: \(recorded\) (.*)", ln)
        if m: res[v]["recorded(pup)"] += 1; fallbacks[v].add("pup: " + m[1][:60]); continue
        m = re.search(r"SharpEars: voice: (?!\(|finished|stop|starting|using|engine|u\d|over|held|dropped|shown|mic)(.*)", ln)
        if m: res[v]["phone"] += 1; fallbacks[v].add("phone: " + m[1][:60])
        m = re.search(r"coach: (\w+) (\S+) \"(.*?)\" (\d+) ms", ln)
        if m: coachlines[v].append((m[1], m[2], m[3]))
        if "clip: " in ln and "failed" in ln: res[v]["clip failed"] += 1
for v in res: print(v, dict(res[v]))
for v in fallbacks: print(v, sorted(fallbacks[v])[:12])
for v in coachlines: print(v, coachlines[v][:10])
