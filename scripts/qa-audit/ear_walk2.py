import sys, re, os, time, json
from lib import *; from ui import *
SUF = "-r2"
EW = os.path.join(OUT, "ear"); os.makedirs(EW, exist_ok=True)
NAMES = ["C", "C sharp", "D", "D sharp", "E", "F", "F sharp", "G", "G sharp", "A", "A sharp", "B"]
def midi_of(n):
    L = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}; pc = L[n[0]]; i = 1
    while i < len(n) and n[i] in "#b": pc += 1 if n[i] == "#" else -1; i += 1
    return pc + 12 * (int(n[i:]) + 1)
def keydesc(m): return (f"{NAMES[m % 12]} {m // 12 - 1}" if "sharp" in NAMES[m % 12] else f"{NAMES[m % 12]}{m // 12 - 1}")
def newest_round(game, level, after):
    ls = log(); r = [(ts(l), re.search(r"round (\d+): .*?\((.*)\)\s*$", l)) for l in ls if re.search(r"ear: %s level %d round \d+:" % (game, level), l)]
    r = [(t, m) for t, m in r if m and int(m[1]) > after]
    return r[-1][1] if r else None, ls
def play(game, level, rounds):
    start("--es", "screen", "ear", "--es", "game", game, "--ei", "level", str(level)); time.sleep(6)
    seen = 0; notes = []
    for k in range(rounds):
        m = None
        for _ in range(20):
            m, ls = newest_round(game, level, seen)
            if m: break
            time.sleep(1.5)
        if not m: notes.append(f"round {seen+1} never started"); break
        seen = int(m[1]); ans = m[2]
        if game in ("degree",): time.sleep(8)
        nodes = dump(); wrong_first = (k == 0)
        try:
            if game == "pitch":
                note = ans.split(")")[0]; pc = midi_of(note) % 12
                lbl = NAMES[pc].replace(" sharp", "")
                cands = [n for n in nodes if n["text"] and n["cy"] < 1000 and n["cy"] > 700]
                def pick(pcx):
                    base = NAMES[pcx]; letter = base[0]; sharp = "sharp" in base
                    for n in cands:
                        t = n["text"]
                        if t.startswith(letter) and (("♯" in t or "#" in t) == sharp): return n
                    return None
                n = pick(pc)
                if wrong_first:
                    others = [c for c in cands if len(c["text"]) <= 4 and c is not n]
                    n = others[0] if others else n
                if n: tap(n["cx"], n["cy"])
                else: notes.append(f"round {seen}: no button for {note} labels={[c['text'] for c in cands]}")
            elif game == "degree":
                word = ans.split(" ")[0]
                cands = [n for n in nodes if n["text"] and 780 < n["cy"] < 860]
                tgt = [n for n in cands if n["text"] == word]
                if wrong_first: tgt = [n for n in cands if n["text"] != word][:1]
                if tgt: tap(tgt[0]["cx"], tgt[0]["cy"])
                else: notes.append(f"round {seen}: no button {word} in {[c['text'] for c in cands]}")
            elif game == "prog":
                num = ans.split(" (")[0]
                cands = [n for n in nodes if n["text"] and 1050 < n["cy"] < 1160 and re.fullmatch(r"[ivIV ]+", n["text"])]
                tgt = [n for n in cands if n["text"] == num]
                if wrong_first: tgt = [n for n in cands if n["text"] != num][:1]
                if tgt: tap(tgt[0]["cx"], tgt[0]["cy"])
                else: notes.append(f"round {seen}: no button {num} in {[c['text'] for c in cands]}")
            elif game == "melody":
                names = ans.split(" in ")[0].split(); time.sleep(1.0 + 0.7 * len(names))
                nodes = dump()
                for i, nm in enumerate(names):
                    mm = midi_of(nm); 
                    if wrong_first and i == 0: mm2 = mm + 1; w = [n for n in nodes if n["desc"] == keydesc(mm2)]; 
                    w = [n for n in nodes if n["desc"] == keydesc(mm)]
                    if wrong_first and i == 0:
                        ww = [n for n in nodes if n["desc"] == keydesc(mm + 1)]
                        if ww: tap(ww[0]["cx"], ww[0]["cy"]); time.sleep(0.4)
                        time.sleep(len(names) * 0.7 + 1.5)
                    if w: tap(w[0]["cx"], w[0]["cy"]); time.sleep(0.35)
                    else: notes.append(f"round {seen}: no key {nm}")
            elif game == "rhythm":
                time.sleep(12)   # demo + count in
                for i in range(5): tap(1038, 1577); time.sleep(0.5)
        except Exception as e: notes.append(f"round {seen} exc {e}")
        time.sleep(4.5)
        # a Continue / Next button after the answer?
        nodes = dump()
        for t in ("Next", "Continue", "Next round"):
            n = find(nodes, t)
            if n: tap(n["cx"], n["cy"]); break
        time.sleep(1.5)
    shot(f"qa-ear-{game}-{level}-end.png")
    nodes = dump(); texts = [n["text"] for n in nodes if n["text"]][:40]
    tap(1918, 215); time.sleep(2.5)
    ls = log(); open(os.path.join(EW, f"{game}-{level}{SUF}.log"), "w", encoding="utf8").write("\n".join(ls))
    return notes, texts
if __name__ == "__main__":
    out = open(os.path.join(OUT, "ear-progress.txt"), "a", encoding="utf8")
    for game in ("degree",):
        for level in (0, 1, 2):
            if os.path.exists(os.path.join(EW, f"{game}-{level}{SUF}.log")): continue
            notes, texts = play(game, level, 10 if (game, level) == ("pitch", 0) else 4)
            out.write(f"{game} L{level+1}: notes={notes} texts={texts[:14]}\n"); out.flush()
