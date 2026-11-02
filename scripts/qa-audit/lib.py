import json, subprocess, time, re, os, sys
ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
SER = "emulator-5562"
PKG = "com.bigpapituna.sharpears"
# 2.19.0: QA_OUT / QA_SCR move a re-run's logs elsewhere (the course dump is read from QA_COURSE, else OUT/course.json)
OUT = os.environ.get("QA_OUT", r"C:\dev\piano-wt\qa-audit\app\build\qa")
SCR = os.environ.get("QA_SCR", r"C:\dev\piano-wt\qa-audit\app\build\screens")
os.makedirs(OUT, exist_ok=True); os.makedirs(SCR, exist_ok=True)
assert SER == "emulator-5562"
def adb(*a, text=True, timeout=60):
    r = subprocess.run([ADB, "-s", SER, *a], capture_output=True, timeout=timeout)
    return r.stdout.decode("utf8", "replace") if text else r.stdout
def start(*extras):
    adb("shell", "am", "force-stop", PKG); adb("logcat", "-c")
    return adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity", *extras)
def log():
    keep = (" SharpEars:", "AndroidRuntime", "FATAL", "ANR in", "am_crash", "am_anr")
    return [l for l in adb("logcat", "-d", "-v", "threadtime").splitlines() if any(k in l for k in keep)]
def ts(line):
    m = re.match(r"\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)", line)
    return ((int(m[1]) * 60 + int(m[2])) * 60 + int(m[3])) * 1000 + int(m[4]) if m else None
def shot(name):
    d = adb("exec-out", "screencap", "-d", "4619827259835644672", "-p", text=False)
    p = os.path.join(SCR, name); open(p, "wb").write(d); return p
def tap(x, y): adb("shell", "input", "tap", str(x), str(y))
COURSE = json.load(open(os.environ.get("QA_COURSE", os.path.join(OUT, "course.json")), encoding="utf8"))
LESSONS = [l for l in COURSE if not l["playground"]]
def lesson(i): return next(l for l in COURSE if l["id"] == i)
def needed(step):
    p = step["practice"]
    return [n for n in step["notes"] if p == "BOTH" or (p == "RIGHT" and n[3] == 1) or (p == "LEFT" and n[3] == 0)]
def midi_str(items): return ",".join(f"{t}:{k}/{v}" for t, k, v in sorted(items))
