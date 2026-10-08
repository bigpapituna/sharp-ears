"""Cut the Golden Coach Pup sheet (art/mascot/sheet.jpg, 5 x 2 poses) into transparent WebPs for the app.

Background: light, low-saturation pixels reachable from the edge of each cell. The pup is the largest remaining shape;
props that touch it (keyboard, trophy, book, pillow) come along, loose sparkles and the pose numbers do not.
    python -I scripts/cut_mascot.py [out dir, default app/src/main/res/drawable-nodpi]
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/res/drawable-nodpi"
NAMES = ["welcome", "practicing", "great_job", "mistake", "learning", "level_up", "streak", "listening", "get_ready", "resting"]
CENTERS = [160, 440, 712, 968, 1228]
ROWS = [(28, 325), (392, 672)]


def components(mask):
    """Labels of 4-connected True regions, and their sizes."""
    h, w = mask.shape
    lab = np.zeros((h, w), np.int32)
    sizes = [0]
    for y0, x0 in zip(*np.nonzero(mask)):
        if lab[y0, x0]:
            continue
        n = len(sizes); sizes.append(0)
        q = deque([(y0, x0)]); lab[y0, x0] = n
        while q:
            y, x = q.popleft(); sizes[n] += 1
            for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n; q.append((yy, xx))
    return lab, sizes


def cut(cell):
    a = np.asarray(cell.convert("RGB")).astype(np.int16)
    hi, lo = a.max(2), a.min(2)
    light = (lo > 175) & (hi - lo < 60)                      # pastel background, its stars and the floor shadow
    lab, _ = components(light)
    edge = set(lab[0]) | set(lab[-1]) | set(lab[:, 0]) | set(lab[:, -1])
    bg = np.isin(lab, [e for e in edge if e])
    lab, sizes = components(~bg)
    keep = lab == int(np.argmax(sizes[1:]) + 1)
    alpha = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
    out = cell.convert("RGBA"); out.putalpha(alpha)
    return out.crop(out.getbbox())


sheet = Image.open(ROOT / "art/mascot/sheet.jpg")
OUT.mkdir(parents=True, exist_ok=True)
i = 0
for top, bottom in ROWS:
    for c in CENTERS:
        pup = cut(sheet.crop((max(c - 140, 0), top, min(c + 140, sheet.width), bottom)))
        pup.save(OUT / f"pup_{NAMES[i]}.webp", lossless=True)
        print(NAMES[i], pup.size)
        i += 1
