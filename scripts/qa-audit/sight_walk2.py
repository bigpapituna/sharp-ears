import sys, re, os, time, json
from lib import *; from ui import *
SW = os.path.join(OUT, "sight"); os.makedirs(SW, exist_ok=True)
def midi_of(n):
    L = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}; pc = L[n[0]]; i = 1
    while i < len(n) and n[i] in "#b": pc += 1 if n[i] == "#" else -1; i += 1
    return pc + 12 * (int(n[i:]) + 1)
def toks(s):
    out = []; t = 0.0
    for tk in s.split():
        n, b = tk.split(":"); b = float(b)
        if n != "r": out.append((t, midi_of(n)))
        t += b
    return out
def piece(level, bpm):
    start("--es", "screen", "sight", "--ei", "level", str(level), "--es", "layout", "full", "--es", "midi", "100:60/80"); time.sleep(7)
    ls = log(); m = [re.search(r"sight: level \d+ .*?: (.*?) \| (.*)$", l) for l in ls if re.search(r"sight: level \d+ ", l)]
    m = [x for x in m if x][-1]; return m[1].strip(), m[2].strip()
def run_piece(level):
    bpm = 66.0 if level == 1 else 60.0
    r, l = piece(level, bpm); beat = 60000.0 / bpm
    items = []
    gs = {}
    for t, m in toks(r): gs.setdefault(t, []).append(m)
    for t, m in toks(l): gs.setdefault(t, []).append(m)
    keys = sorted(gs)
    for gi, t in enumerate(keys):
        tt = int(2000 + t * beat)
        if gi in (0, len(keys) - 1): items.append((tt + 60, max(gs[t]) + 1 if max(gs[t]) < 83 else min(gs[t]) - 1, 80))
        for m in gs[t]: items.append((tt + 220, m, 80))
    start("--es", "screen", "sight", "--ei", "level", str(level), "--es", "layout", "full", "--es", "midi", ",".join(f"{t}:{k}/{v}" for t, k, v in sorted(items)))
    time.sleep(6); tap(273, 643); time.sleep(len(keys) * beat / 1000 + 14)
    shot(f"qa-sight-{level}-end.png"); ls = log()
    open(os.path.join(SW, f"level{level}.log"), "w", encoding="utf8").write("\n".join(ls)); return r, l, ls
def run_drill(level, n=30, wait_end=False):
    start("--es", "screen", "sight", "--ei", "level", str(level), "--es", "layout", "full", "--es", "midi", "100:60/80"); time.sleep(7)
    # the level chip may already be selected by the route
    stn = [n for n in dump() if n['text'].startswith('Start')]; tap(stn[0]['cx'], stn[0]['cy']) if stn else tap(1038, 1141); time.sleep(4); ls = log()
    note = [re.search(r"sight drill: notes (.*)$", l) for l in ls if "sight drill: notes" in l]
    bars = [re.search(r"bars (.*)$", l) for l in ls if "sight drill: level" in l and "bars" in l]
    if level < 8:
        codes = note[-1][1].split()[:n]
        items = []; t = 1500
        for i, c in enumerate(codes):
            m = midi_of(c)
            if i in (2, 9): items.append((t, m + 1 if (m + 1) % 12 not in (1, 3, 6, 8, 10) else m + 2, 80)); t += 500
            items.append((t, m, 80)); t += 1100
    else:
        bs = bars[-1][1].split(" | ")[:6]; items = []; bar_ms = 4 * 60000 / 72; t0 = bar_ms   # one bar count-in
        for bi, b in enumerate(bs):
            tt = 0.0
            for tk in b.split():
                nme, bt = tk.split(":"); bt = float(bt)
                if nme != "r": items.append((int(t0 + bi * bar_ms + tt * 60000 / 72 + 20), midi_of(nme), 80))
                tt += bt
    start("--es", "screen", "sight", "--ei", "level", str(level), "--es", "layout", "full", "--es", "midi", ",".join(f"{t}:{k}/{v}" for t, k, v in sorted(items)))
    time.sleep(7); stn = [n for n in dump() if n['text'].startswith('Start')]; tap(stn[0]['cx'], stn[0]['cy']) if stn else tap(1038, 1141); time.sleep((items[-1][0] / 1000.0) + 6)
    shot(f"qa-sight-{level}-mid.png")
    if wait_end: time.sleep(max(0, 125 - items[-1][0] / 1000.0 - 6)); shot(f"qa-sight-{level}-end.png")
    ls = log(); open(os.path.join(SW, f"level{level}.log"), "w", encoding="utf8").write("\n".join(ls)); return ls
if __name__ == "__main__":
    out = open(os.path.join(OUT, "sight-progress2.txt"), "a", encoding="utf8")
    for lv in (1, 2):
        SKIP = False
        r, l, ls = run_piece(lv)
        res = [x[6:200] for x in ls if "finished" in x or "ended" in x or "run report" in x or "sight:" in x][-4:]
        wr = sum(1 for x in ls if "-> WRONG" in x); gd = sum(1 for x in ls if "GROUP_DONE" in x)
        out.write(f"L{lv}: right={r} | left={l} GROUP_DONE={gd} WRONG={wr} :: {res}\n"); out.flush()
    for lv in (6, 7, 8):
        ls = run_drill(lv, wait_end=(lv == 3))
        d = [x[6:210] for x in ls if "sight drill:" in x]
        right = sum(1 for x in ls if "sight drill: right" in x); wrong = sum(1 for x in ls if "sight drill: wrong" in x); hit = sum(1 for x in ls if "drill: hit" in x); miss = sum(1 for x in ls if "drill: missed" in x)
        out.write(f"L{lv}: right={right} wrong={wrong} hit={hit} missed={miss} tail={d[-3:]}\n"); out.flush()
