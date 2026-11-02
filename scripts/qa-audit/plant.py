import subprocess, json, re, sys
from lib import *
def run_as_write(path, content):
    subprocess.run([ADB, "-s", SER, "shell", f"run-as {PKG} sh -c 'cat > {path}'"], input=content.encode("utf8"), check=True)
def run_as_read(path): return adb("shell", "run-as", PKG, "cat", path)
def xml_set(xml, name, kind, value):
    xml = re.sub(r'\s*<(string|boolean|long|int)\s+name="%s"[^>]*?(/>|>.*?</string>)' % re.escape(name), "", xml, flags=re.S)
    line = f'    <string name="{name}">{value}</string>\n' if kind == "string" else f'    <boolean name="{name}" value="{str(value).lower()}" />\n'
    return xml.replace("</map>", line + "</map>")
def plant_coach(profile_file, voice, for_who="w"):
    adb("shell", "am", "force-stop", PKG)
    try: xml = run_as_read(f"shared_prefs/{profile_file}.xml")
    except Exception: xml = ""
    if "<map" not in xml: xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
    for k, kind, v in (("coachVoice", "string", voice), ("coachFor", "string", for_who), ("coachNotice", "bool", True)):
        xml = xml_set(xml, k, "string" if kind == "string" else "bool", v)
    run_as_write(f"shared_prefs/{profile_file}.xml", xml)
def clear_coach(profile_file):
    adb("shell", "am", "force-stop", PKG)
    xml = run_as_read(f"shared_prefs/{profile_file}.xml")
    for k in ("coachVoice", "coachFor", "coachNotice"):
        xml = re.sub(r'\s*<(string|boolean)\s+name="%s"[^>]*?(/>|>.*?</string>)' % k, "", xml, flags=re.S)
    run_as_write(f"shared_prefs/{profile_file}.xml", xml)
def add_profiles():
    adb("shell", "am", "force-stop", PKG)
    x = run_as_read("shared_prefs/profiles.xml")
    m = re.search(r'<string name="roster">(.*?)</string>', x, re.S)
    import html
    r = json.loads(html.unescape(m[1]))
    ids = {p["id"] for p in r["profiles"]}
    base = dict(avatar=1, kind="KID", reader=True, dailyGoalMin=8, hintSeconds=[4, 8, 12], colourNotes=True, micOffsetDb=3.0, createdAt=1793601300000, level="NEVER", named=True)
    if "k1" not in ids: r["profiles"].append(dict(id="k1", name="Mia", band="LITTLE", **base))
    if "k2" not in ids: r["profiles"].append(dict(id="k2", name="Leo", band="BIG", **{**base, "colourNotes": False, "dailyGoalMin": 10, "hintSeconds": [6, 12, 18]}))
    out = html.escape(json.dumps(r, separators=(",", ":")), quote=True)
    run_as_write("shared_prefs/profiles.xml", f"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"roster\">{out}</string>\n</map>\n")
if __name__ == "__main__":
    a = sys.argv[1]
    if a == "coach": plant_coach("progress", sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "w")
    if a == "clear": clear_coach("progress")
    if a == "kids": add_profiles()
def mic_off(profile_file="progress"):
    """2.19.0: the walk plays by the pretend MIDI keyboard; a tap that landed on the mic switch turned it on for every later step."""
    adb("shell", "am", "force-stop", PKG)
    xml = run_as_read(f"shared_prefs/{profile_file}.xml")
    xml = re.sub(r'\s*<boolean\s+name="micDefault"[^>]*/>', "", xml)
    run_as_write(f"shared_prefs/{profile_file}.xml", xml.replace("</map>", '    <boolean name="micDefault" value="false" />\n</map>'))
if __name__ == "__main__" and sys.argv[1] == "micoff": clear_coach("progress"); mic_off()
