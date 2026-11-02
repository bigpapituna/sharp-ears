import re, json, os, sys, collections
from lib import *
WALK = os.path.join(OUT, "walk")

def norm(t): return " ".join(t.split())
def parse_session(lid, i0):
    p = os.path.join(WALK, f"{lid}-{i0}.log")
    if not os.path.exists(p): return None
    return open(p, encoding="utf8").read().splitlines()

def segments(lines, L):
    """-> list of dict(k, N, kind, idx, lines[], t0). A READ step's scene 1 lines come just before its open line."""
    opens = []
    for i, ln in enumerate(lines):
        m = re.search(r"SharpEars: lesson (\S+) step (\d+)/(\d+) (\w+)", ln)
        if m and m[1] == L["id"]: opens.append((i, int(m[2]), int(m[3]), m[4]))
    segs = []
    for j, (i, k, N, kind) in enumerate(opens):
        start = i
        if kind == "READ":
            for q in range(i, max(i - 8, -1), -1):
                if re.search(r"lesson %s step %d scene 1/" % (re.escape(L["id"]), k), lines[q]): start = q; break
        nxt = opens[j + 1][0] if j + 1 < len(opens) else len(lines)
        nstart = nxt
        if j + 1 < len(opens) and opens[j + 1][3] == "READ":
            for q in range(nxt, max(nxt - 8, -1), -1):
                if re.search(r"step %d scene 1/" % opens[j + 1][1], lines[q]): nstart = q; break
        segs.append(dict(k=k, N=N, kind=kind, idx=k - 1 - (N - len(L["steps"])), lines=lines[start:nstart], t0=ts(lines[start])))
    return segs

def msg(ln): return ln.split("SharpEars: ", 1)[-1] if "SharpEars: " in ln else ln

def analyze_step(L, seg, scripted):
    s = L["steps"][seg["idx"]] if 0 <= seg["idx"] < len(L["steps"]) else None
    issues = []; info = {}
    lines = seg["lines"]; t0 = seg["t0"]
    voice = []
    for ln in lines:
        m = re.search(r"voice: \((recorded|narrated [^)]*)\) (.*)", ln)
        if m: voice.append((ts(ln), "rec", m[2])); continue
        m = re.search(r"voice: (?!\(|finished|stop|starting|using|engine|u\d|over|held|dropped|shown|mic)(.*)", ln)
        if m and "SharpEars: voice:" in ln: voice.append((ts(ln), "phone", m[1]))
    info["voice"] = voice
    clips = [(ts(ln), re.search(r"prep=(\d+)", ln)[1]) for ln in lines if "QA clip started" in ln and "prep=" in ln] +         [(ts(ln), re.search(r"prepared in (\d+) ms", ln)[1]) for ln in lines if re.search(r"clip: \S+ started, prepared in \d+ ms", ln)]
    info["prep"] = [int(p) for _, p in clips]
    for _, p in clips:
        if int(p) > 500: issues.append(("minor", f"clip prep {p} ms (>500)"))
    if s is None: return info, [("major", "step index outside course")]
    # crash/anr
    for ln in lines:
        if "FATAL" in ln or "AndroidRuntime" in ln or "ANR in" in ln: issues.append(("blocker", "crash/ANR: " + msg(ln)[:120]))
    kind = s["kind"]
    if kind == "READ":
        scs = [ln for ln in lines if re.search(r"step %d scene (\d+)/(\d+)" % seg["k"], ln)]
        info["scenes"] = len(scs)
        if len(scs) != len(s["scenes"]): issues.append(("major", f"READ shows {len(scs)} of {len(s['scenes'])} scenes"))
        spoken = [v for v in voice]
        exp = [norm(x) for x in s["scenes"]]
        got = [norm(v[2]) for v in spoken if v[1] == "rec"]
        for e in exp:
            if e not in got: issues.append(("major", f"scene line not voiced as recorded: {e[:70]}"))
        for v in spoken:
            if v[1] == "phone": issues.append(("major", f"phone TTS voice used for: {v[2][:70]}"))
        # lines in log whose text doesn't match any scene
    elif kind in ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP"):
        res = collections.Counter(re.search(r"-> (\w+) at", ln)[1] for ln in lines if re.search(r"input \S+ -> (\w+) at", ln))
        info["inputs"] = dict(res)
        ended = [ln for ln in lines if re.search(r"step %d ended" % seg["k"], ln)]
        info["ended"] = msg(ended[-1]) if ended else None
        if not ended and scripted: issues.append(("major", "step never ended with scripted perfect play"))
        if ended and "passed=true" not in ended[-1] and scripted: issues.append(("major", "scripted perfect play did not pass: " + msg(ended[-1])[:140]))
    return info, issues

NOTE_RE = re.compile(r"QA piano noteOn (\d+) v(\d+)")
APP_NOTE_RE = re.compile(r"app note (\S+) at -?\d+ ms on")
def name_to_midi(n):
    try: return noteNumber(n)
    except Exception: return None
def noteNumber(name):
    L = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}; pc = L[name[0]]; i = 1
    while i < len(name) and name[i] in "#b": pc += 1 if name[i] == "#" else -1; i += 1
    return pc + 12 * (int(name[i:]) + 1)

def deep(L, seg, scripted, wrong_expected):
    """step-kind specific checks; returns (info, issues)"""
    info, issues = analyze_step(L, seg, scripted)
    s = L["steps"][seg["idx"]] if 0 <= seg["idx"] < len(L["steps"]) else None
    if s is None: return info, issues
    lines = seg["lines"]; kind = s["kind"]
    # instruction voiced?
    if kind != "READ":
        instr = norm(s["text"].replace("\n\n", " "))
        first_voice = [v for v in info["voice"]][:3]
        if not any(norm(v[2]) == instr for v in info["voice"]):
            held = [msg(ln) for ln in lines if "voice:" in ln and ("held" in ln or "dropped" in ln or "shown, not said" in ln)]
            issues.append(("minor", "instruction line not voiced: " + instr[:60] + (" (" + held[0][:80] + ")" if held else "")))
        else:
            v = next(v for v in info["voice"] if norm(v[2]) == instr)
            info["lat_instr_ms"] = v[0] - seg["t0"]
            if v[1] == "phone": issues.append(("major", "instruction voiced by phone TTS: " + instr[:60]))
    if kind == "LISTEN":
        pl = [i for i, ln in enumerate(lines) if "transport play from" in ln and "mode=LISTEN" in ln]
        st = [i for i, ln in enumerate(lines) if "transport stopped at" in ln]
        if not pl: issues.append(("major", "LISTEN never played (no transport play)")); return info, issues
        a = pl[0]; b = next((i for i in st if i > a), len(lines))
        got = [(ts(ln), int(NOTE_RE.search(ln)[1])) for ln in lines[a:b] if NOTE_RE.search(ln)]
        if not got:   # 2.19.0: no QA patch; the app's own "app note <name> at <ms> ms on" line
            got = [(ts(ln), name_to_midi(m[1])) for ln in lines[a:b] for m in [APP_NOTE_RE.search(ln)] if m]
        exp = sorted([(n[1], n[0]) for n in s["notes"]])
        info["listen"] = dict(exp=len(exp), got=len(got), dur=(ts(lines[b]) - ts(lines[a])) if b < len(lines) else None)
        from collections import Counter
        ce, cg = Counter(m for _, m in exp), Counter(m for _, m in got)
        miss = ce - cg; extra = cg - ce
        if miss: issues.append(("major", f"LISTEN did not sound {sum(miss.values())} of {len(exp)} notes: {dict(miss)}"))
        if extra: issues.append(("minor", f"LISTEN sounded {sum(extra.values())} extra notes: {dict(extra)}"))
        if not miss and not extra and got:
            gs = sorted(got); es = exp
            # compare onset gaps
            d = [ (gs[i][0] - gs[0][0]) - (es[i][0] - es[0][0]) for i in range(len(es)) ]
            info["listen"]["max_dev"] = max(abs(x) for x in d)
            if max(abs(x) for x in d) > 200: issues.append(("minor", f"LISTEN note timing off by up to {max(abs(x) for x in d)} ms"))
            order_ok = [m for _, m in gs] == [m for _, m in sorted(es, key=lambda x: (x[0], x[1]))]
        # did the step end/advance?
        if not any(re.search(r"step %d ended" % seg["k"], ln) for ln in lines): issues.append(("major", "LISTEN step did not end/advance"))
    if kind in ("PLAY", "FROM_MEMORY"):
        gs = {}
        for n in needed(s): gs.setdefault(n[1], set()).add(n[0])
        exp_groups = [sorted(v) for k, v in sorted(gs.items())]
        info['groups'] = len(exp_groups)
        done = sum(1 for ln in lines if re.search(r"input \S+ -> GROUP_DONE", ln))
        wrong = sum(1 for ln in lines if re.search(r"input \S+ -> WRONG", ln))
        ign = [msg(ln) for ln in lines if re.search(r"input \S+ -> IGNORED", ln)]
        info.update(done=done, wrong=wrong, ignored=len(ign))
        if done < len(exp_groups) and scripted: issues.append(("major", f"only {done} of {len(exp_groups)} groups accepted with right notes played"))
        if len(ign) > (info.get("inj_wrong", 0) - info.get("exp_wrong", 0)) and scripted: issues.append(("minor", f"{len(ign)} notes ignored (early/late/out of range): {ign[0][:60]}"))
        sc = next((re.search(r"midi: debug script (\S+)", ln)[1] for ln in lines if "midi: debug script" in ln), None)
        rng = next((re.search(r"range=([A-G][#b]?\d)\.\.([A-G][#b]?\d)", ln) for ln in lines if "player open" in ln and "range=" in ln), None)
        if sc and rng and scripted:
            lo, hi = noteNumber(rng[1]), noteNumber(rng[2])
            right_t = {2000 + n[1] + 220 for n in needed(s)}
            items = [(int(x.split(":")[0]), int(x.split(":")[1].split("/")[0])) for x in sc.split(",")]
            wrongs = [m for t, m in items if t not in right_t]
            exp_w = sum(1 for m in wrongs if lo <= m <= hi)
            info["inj_wrong"] = len(wrongs); info["exp_wrong"] = exp_w
            if wrong != exp_w: issues.append(("major", f"{wrong} WRONG flags for {exp_w} injected in-range wrong notes ({len(wrongs)} injected)"))
            if sum(1 for ln in lines if re.search(r"input \S+ -> IGNORED", ln)) > len(wrongs) - exp_w:
                pass
    if kind in ("PERFORM", "RAMP"):
        rec = re.findall(r"Recap\(good=(\d+), ok=(\d+), miss=(\d+), biasMs=(-?\d+)", "\n".join(lines))
        info["recaps"] = rec
        wrong = sum(1 for ln in lines if re.search(r"input \S+ -> WRONG", ln))
        info["wrong"] = wrong
        if wrong and scripted: issues.append(("major", f"{wrong} right notes flagged WRONG in {kind}"))
        for g, o, m, b in rec:
            if int(m) > 0 and scripted and kind == "PERFORM": issues.append(("major", f"{m} notes missed with perfect scripted play (good={g} ok={o})"))
    return info, issues

def analyze_lesson(lid):
    L = lesson(lid); out = []
    idxs = [0] + [i for i, s in enumerate(L["steps"]) if s["kind"] in ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP") and i != 0]
    seen = {}
    for i0 in idxs:
        lines = parse_session(lid, i0)
        if lines is None: continue
        segs = segments(lines, L)
        for seg in segs:
            idx = seg["idx"]
            scripted = (idx == i0 and L["steps"][i0]["kind"] in ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP"))
            if idx in seen and not scripted: continue
            wrong_exp = None
            if scripted and seg["kind"] in ("PLAY", "FROM_MEMORY"):
                n = len(set(n[1] for n in needed(L["steps"][idx]))); wrong_exp = len({0, n // 2, n - 1})
            info, issues = deep(L, seg, scripted, wrong_exp)
            seen[idx] = dict(idx=idx, kind=seg["kind"], scripted=scripted, info=info, issues=issues, session=i0)
        # session-level
        sess_issues = []
        for ln in lines:
            if "FATAL" in ln or "ANR in" in ln: sess_issues.append(("blocker", "crash/ANR in session %d: %s" % (i0, msg(ln)[:100])))
        out.append((i0, sess_issues))
    return seen, out
