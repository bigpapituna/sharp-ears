import sys, re, json, os, time, traceback
from lib import *
WALK = os.path.join(OUT, "walk"); os.makedirs(WALK, exist_ok=True)
INTERACTIVE = ("PLAY", "FROM_MEMORY", "PERFORM", "RAMP", "PEDAL", "DYNAMICS")
NEXT_XY = (1747, 1980)

def groups_needed(step):
    ns = needed(step)
    gs = {}
    for m, t, d, h in ns: gs.setdefault(t, []).append(m)
    return sorted(gs.items())

def wrong_for(ms):
    w = max(ms) + 1
    while w in ms or w > 96 or w < 36: w = (w + 2) if w < 90 else w - 5
    return w

def make_script(step, kind):
    """Script for a wait / perform step, played perfectly with wrong keys sprinkled in wait mode."""
    items = []
    gs = groups_needed(step)
    if not gs: return None, 0
    if kind in ("PLAY", "FROM_MEMORY"):
        L = 2000
        idxs = {0, len(gs) // 2, len(gs) - 1}
        for gi, (t, ms) in enumerate(gs):
            if gi in idxs: items.append((L + t + 60, wrong_for(ms), 80))
            for m in ms: items.append((L + t + 220, m, 80))
        end = L + gs[-1][0] + 220
    else:  # PERFORM / RAMP first pass (RAMP handled by caller)
        bar = step["beats"] * 60000.0 / step["bpm"]
        L = int(2 * bar)
        for t, ms in gs:
            for m in ms: items.append((int(L + t + 25), m, 80))
        end = L + gs[-1][0]
    return midi_str(items), end

def ramp_script(step):
    gs = groups_needed(step)
    bpm = step["bpm"]; bar_full = step["beats"] * 60000.0 / bpm
    items = []; tp = 25; span = step["endMs"]
    for k, sp in enumerate([0.5, 0.6, 0.7, 0.8, 0.9, 1.0]):
        for t, ms in gs:
            for m in ms: items.append((int(tp + (2 * bar_full + t) / sp + 25), m, 80))
        tp = tp + (2 * bar_full + span) / sp + RAMP_GAP
    return midi_str(items)
RAMP_GAP = 1850

def expected_voice(step, scene=None):
    return step["scenes"][scene] if scene is not None else step["text"].replace("\n\n", " ")

class Session:
    def __init__(self, lid, i0):
        self.lid, self.i0 = lid, i0
        self.L = lesson(lid)
        self.rows = []     # per-step records
        self.cur = None
        self.notes = []
    cont_tapped = False
    def run(self):
        L, i0 = self.L, self.i0
        step = L["steps"][i0]
        off = 0  # 2.19.0 no longer prepends the first-run steps to "sit" (2.18 needed 2)
        extras = ["--es", "lesson", self.lid, "--ei", "step", str(i0 + off)]
        script = None
        if step["kind"] in INTERACTIVE:
            if step["kind"] == "RAMP": script = ramp_script(step)
            else: script, _ = make_script(step, step["kind"])
            if script: extras += ["--es", "midi", script]
        self.script = script
        t_launch = time.time(); out = start(*extras)
        self.t_launch = t_launch
        seen = 0; opened = {}; last_scene = {}; tapped = {}; result = "timeout"; lines = []
        deadline = t_launch + self.budget(step)
        stop = False; n_open = 0
        while time.time() < deadline and not stop:
            time.sleep(1.5)
            lines = log()
            cur = None
            for ln in lines:
                m = re.search(r"lesson (\S+) step (\d+)/(\d+) (\w+)", ln)
                if m and m[1] == self.lid: cur = (int(m[2]), int(m[3]), m[4], ts(ln))
            if cur is None: continue
            k, N, kind, t_open = cur
            idx = k - 1 - (N - len(L["steps"]))        # first-run steps are prepended
            # done?
            if any(re.search(r"lesson %s done" % re.escape(self.lid), ln) for ln in lines): result = "done"; time.sleep(4); break
            if idx != i0 and kind in INTERACTIVE and idx > i0: result = "reached %d" % idx; break
            if idx < i0 and False: pass
            if kind == "READ" and idx >= i0:
                # scene logic
                sc = [ (int(re.search(r"scene (\d+)/(\d+)", ln)[1]), int(re.search(r"scene (\d+)/(\d+)", ln)[2]), ts(ln)) for ln in lines if re.search(r"step %d scene \d+/\d+" % k, ln) and "lesson %s" % self.lid in ln]
                if sc and sc[-1][0] == sc[-1][1] and k not in tapped:
                    # wait for the clip to end after last scene line
                    t_last = sc[-1][2]
                    ends = [ts(ln) for ln in lines if ("QA clip end" in ln or re.search(r"clip: \S+ end$", ln)) and ts(ln) and ts(ln) >= t_last]
                    now_dev = ts(lines[-1])
                    if ends or now_dev - t_last > 22000:
                        time.sleep(1.2); tap(*NEXT_XY); tapped[k] = time.time()
            if kind in INTERACTIVE and idx == i0:
                ev = [ln for ln in lines if re.search(r"lesson %s step %d ended" % (re.escape(self.lid), k), ln)]
                if ev and "cont" not in tapped:
                    t_end = ts(ev[-1]); now_dev = ts(lines[-1])
                    # the cheer bar waits for Continue (and the feedback line); give it a moment, then tap
                    if now_dev - t_end > 9000:
                        tap(1040, 1965); tapped["cont"] = time.time(); self.cont_tapped = True
                elif ev and "cont" in tapped and "cont2" not in tapped and time.time() - tapped["cont"] > 5:
                    tap(1747, 1980); tapped["cont2"] = time.time()
        self.lines = lines; self.result = result
        path = os.path.join(WALK, f"{self.lid}-{i0}.log")
        open(path, "w", encoding="utf8").write("\n".join(lines))
        return result
    def budget(self, step):
        k = step["kind"]
        n = len(step["scenes"])
        if k in ("READ", "LISTEN"):
            tot = 40
            for s2 in self.L["steps"][self.i0:]:
                if s2["kind"] in INTERACTIVE: break
                tot += 14 + 11 * len(s2["scenes"]) + (s2["endMs"] / 1000.0 * 1.3 if s2.get("endMs") else 0) + 6
            return tot
        if k in ("PLAY", "FROM_MEMORY"): return 40 + (step["endMs"] / 1000.0 if step.get("endMs") else 20) * 1.3
        if k == "PERFORM": return 40 + 2 * (2 * step["beats"] * 60 / step["bpm"]) + step["endMs"] / 1000.0
        if k == "RAMP": return 70 + sum((2 * step["beats"] * 60 / step["bpm"] / 0.5 if j == 0 else 3) + step["endMs"] / 1000.0 / s for j, s in enumerate([.5, .6, .7, .8, .9, 1.0]))
        return 60

if __name__ == "__main__":
    lid, i0 = sys.argv[1], int(sys.argv[2])
    s = Session(lid, i0); print(s.run(), s.script)
