"""2.19.0: the coach's held reaction (item 3) by mic injection on a second emulator (default emulator-5566): a wrong key, then
the right notes, with a coach planted. Prints the coach, mic and input lines. Usage: python hold_check.py [serial] [tag]"""
import re, subprocess, sys, time, os
SER = sys.argv[1] if len(sys.argv) > 1 else "emulator-5566"
assert SER != "emulator-5554"
TAG = sys.argv[2] if len(sys.argv) > 2 else "after"
ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"); PKG = "com.bigpapituna.sharpears"
def adb(*a, inp=None): return subprocess.run([ADB, "-s", SER, *a], input=inp, capture_output=True).stdout.decode("utf8", "replace")
def setp(xml, name, val, kind):
    xml = re.sub(r'\s*<(string|boolean)\s+name="%s"[^>]*?(/>|>.*?</string>)' % name, "", xml, flags=re.S)
    line = f'    <string name="{name}">{val}</string>\n' if kind == "s" else f'    <boolean name="{name}" value="{val}" />\n'
    return xml.replace("</map>", line + "</map>")
adb("shell", "am", "force-stop", PKG)
adb("shell", "pm", "grant", PKG, "android.permission.RECORD_AUDIO")
xml = adb("shell", "run-as", PKG, "cat", "shared_prefs/progress.xml")
was = re.search(r'<boolean name="micDefault" value="(\w+)"', xml)
for n, v, k in (("coachVoice", "male_us", "s"), ("coachFor", "w", "s"), ("coachNotice", "true", "b"), ("micDefault", "true", "b")):
    xml = setp(xml, n, v, k)
adb("shell", f"run-as {PKG} sh -c 'cat > shared_prefs/progress.xml'", inp=xml.encode("utf8"))
out = []
# fingers-right step 3: a wrong A4, then the walk up and down (C D E F G F E D C), a pause in the middle (r,r,r: 2.1 s).
for lid, step, inj in (("fingers-right", "2", "synth:69,60,62,64,65,r,r,r,67,65,64,62,60@700~1500"),):
    adb("logcat", "-c"); adb("shell", "am", "start", "-W", "-n", PKG + "/.MainActivity", "--es", "lesson", lid, "--ei", "step", step, "--es", "inject", inj)
    time.sleep(55)
    L = [l for l in adb("logcat", "-d", "-v", "time", "-s", "SharpEars:D").splitlines()
         if re.search(r"coach:|mic: deaf|mic: hears|input |ended passed|auto-start|inject|NoteOn|lesson %s step" % lid, l)]
    out += [f"== {lid} step {step} {inj}"] + [l[6:200] for l in L]
adb("shell", "am", "force-stop", PKG)
xml = adb("shell", "run-as", PKG, "cat", "shared_prefs/progress.xml")
for n in ("coachVoice", "coachFor", "coachNotice"):
    xml = re.sub(r'\s*<(string|boolean)\s+name="%s"[^>]*?(/>|>.*?</string>)' % n, "", xml, flags=re.S)
xml = setp(xml, "micDefault", was[1] if was else "false", "b")       # the profile's own mic choice back
adb("shell", f"run-as {PKG} sh -c 'cat > shared_prefs/progress.xml'", inp=xml.encode("utf8"))
p = os.path.join(os.path.dirname(__file__), "..", "..", "app", "build", f"hold-{TAG}.txt")
open(p, "w", encoding="utf8").write("\n".join(out)); print("\n".join(out))
