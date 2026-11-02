import re, xml.etree.ElementTree as ET
from lib import *
def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    x = adb("exec-out", "cat", "/sdcard/u.xml")
    try: root = ET.fromstring(x)
    except Exception: return []
    out = []
    for n in root.iter("node"):
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not b: continue
        x0, y0, x1, y1 = map(int, b.groups())
        out.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""), clickable=n.get("clickable") == "true", cx=(x0 + x1) // 2, cy=(y0 + y1) // 2, b=(x0, y0, x1, y1), cls=n.get("class", "")))
    return out
def find(nodes, text, exact=True):
    for n in nodes:
        for t in (n["text"], n["desc"]):
            if t and ((t == text) if exact else (text.lower() in t.lower())): return n
    return None
def tap_text(text, exact=True, nodes=None):
    nodes = nodes or dump(); n = find(nodes, text, exact)
    if n: tap(n["cx"], n["cy"]); return True
    return False
