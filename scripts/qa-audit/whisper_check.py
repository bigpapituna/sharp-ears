import os, json, re, hashlib, sys, difflib
sys.path.insert(0, os.path.dirname(__file__))
from lib import *
import site, glob
for sp in site.getsitepackages():
    for d in glob.glob(os.path.join(sp, 'nvidia', '*', 'bin')):
        os.add_dll_directory(d); os.environ['PATH'] = d + os.pathsep + os.environ['PATH']
from faster_whisper import WhisperModel
base = r"C:\dev\piano-wt\qa-audit\app\src\afterdark\assets\coach"
def key(t): return hashlib.sha1(" ".join(t.split()).encode()).hexdigest()[:16]
lines = {}
for l in LESSONS:
    for i, s in enumerate(l["steps"]):
        for t in s["lines"]: lines.setdefault(t, (l["id"], i))
    lines.setdefault(l["next"], (l["id"], "next"))
def norm(t):
    t = t.lower().replace("-", " ")
    t = re.sub(r"\bpercent\b", "%", t)
    return re.sub(r"[^a-z0-9 ]", "", t).split()
model = WhisperModel("small.en", device="cuda", compute_type="float16")
out = []
for v in ["male_us", "male_irish", "female_us", "female_au"]:
    d = os.path.join(base, v, "course"); files = set(os.listdir(d))
    for t, (lid, si) in lines.items():
        k = key(t)
        for suf in ("", "_w", "_m"):
            f = k + suf + ".ogg"
            if f not in files: continue
            segs, info = model.transcribe(os.path.join(d, f), language="en", beam_size=1, vad_filter=False)
            hyp = " ".join(s.text for s in segs)
            a, b = norm(t), norm(hyp)
            sm = difflib.SequenceMatcher(None, a, b)
            ratio = sm.ratio()
            out.append(dict(voice=v, file=f, lesson=lid, step=si, text=t, heard=hyp.strip(), ratio=round(ratio, 3), dur=round(info.duration, 2), words=len(a)))
json.dump(out, open(os.path.join(OUT, "whisper.json"), "w", encoding="utf8"), indent=0)
print(len(out), "checked")
