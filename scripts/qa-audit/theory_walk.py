import sys, json, re, os, time
from lib import *; from ui import *
T = json.load(open(os.path.join(OUT, "theory.json"), encoding="utf8"))
TW = os.path.join(OUT, "theory"); os.makedirs(TW, exist_ok=True)
NAMES = ["C", "C sharp", "D", "D sharp", "E", "F", "F sharp", "G", "G sharp", "A", "A sharp", "B"]
def keydesc(m): return (f"{NAMES[m % 12]} {m // 12 - 1}" if "sharp" in NAMES[m % 12] else f"{NAMES[m % 12]}{m // 12 - 1}")
def watch(lid, n):
    start("--es", "screen", "theory", "--es", "theory", lid)
    t0 = time.time(); lines = []
    while time.time() - t0 < n * 11 + 20:
        time.sleep(3); lines = log()
        sc = [l for l in lines if re.search(r"scene %d/%d" % (n, n), l)]
        if sc:
            tl = ts(sc[-1]); ends = [l for l in lines if ("QA clip end" in l or re.search(r"clip: \S+ end$", l)) and ts(l) >= tl]
            if ends: time.sleep(2); break
    lines = log(); open(os.path.join(TW, lid + "-watch.log"), "w", encoding="utf8").write("\n".join(lines)); return lines
def quiz(lid, qs):
    start("--es", "screen", "theory", "--es", "theory", lid, "--es", "part", "quiz"); time.sleep(8)
    done_q = 0; answered = []; first_wrong = False; guard = 0
    while guard < len(qs) * 2 + 4:
        guard += 1
        nodes = dump()
        cont = find(nodes, "Continue")
        if cont is None:
            # a question is open
            lines = log(); cur = [re.search(r"question (\d+)/(\d+) (\w+)", l) for l in lines if "theory:" in l and " question " in l]; cur = [c for c in cur if c]
            if not cur:
                # maybe finished
                if any("Done" in (n["text"] or "") or "Lesson" in (n["text"] or "") for n in nodes): break
                time.sleep(2); continue
            qi = int(cur[-1][1]) - 1; q = qs[qi]
            # first question of the run: answer wrong once
            wrong_first = not first_wrong; first_wrong = True
            if q["t"] in ("choice", "listen"):
                opts = q["options"]; ai = q["answer"]
                pick = ai
                if wrong_first: pick = (ai + 1) % len(opts)
                ok = tap_text(opts[pick], nodes=nodes)
                if not ok: answered.append((qi, "option not found: " + opts[pick])); 
            else:
                m = q["target"]; 
                if wrong_first: m = q["target"] + 2 if (q["target"] + 2) % 12 not in (1, 3, 6, 8, 10) else q["target"] + 1
                cands = [n for n in nodes if n["desc"] and re.fullmatch(r"[A-G]( sharp ?)?\d", n["desc"])]
                def pc(n):
                    d = n["desc"]; base = d.rstrip("0123456789").strip(); return NAMES.index(base)
                want = [n for n in cands if n["desc"] == keydesc(m)] or [n for n in cands if pc(n) == m % 12]
                if want: tap(want[0]["cx"], want[0]["cy"])
                else: answered.append((qi, "key not found %s" % keydesc(m)))
            time.sleep(2.5)
        else:
            tap(cont["cx"], cont["cy"]); time.sleep(2)
    lines = log(); open(os.path.join(TW, lid + "-quiz.log"), "w", encoding="utf8").write("\n".join(lines)); return lines, answered
if __name__ == "__main__":
    ids = sys.argv[1:] or [x["id"] for x in T]
    for x in T:
        if x["id"] not in ids: continue
        if not os.path.exists(os.path.join(TW, x["id"] + "-watch.log")):
            watch(x["id"], len(x["scenes"]))
        if not os.path.exists(os.path.join(TW, x["id"] + "-quiz.log")):
            lines, ans = quiz(x["id"], x["quiz"])
            open(os.path.join(OUT, "theory-progress.txt"), "a").write(f"{x['id']} questions answered {len([l for l in lines if 'answered' in l])} notfound={ans}\n")
