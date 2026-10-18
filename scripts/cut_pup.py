"""Cut the single-image pup poses (art/mascot/pup-*.jpg, Gemini, white background) into transparent WebPs.

Same idea as cut_mascot.py: the background is the light region reachable from the edge; what remains is the pup plus
anything it touches. Loose motion strokes and "zzz" letters are small and dropped; a prop that matters (the butterfly)
is kept because it is above the size floor. Output is scaled to PUP_PX on the long side.
    python -I scripts/cut_pup.py [out dir, default app/src/main/res/drawable-nodpi]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_mascot import components  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/res/drawable-nodpi"
PUP_PX = 720
FLOOR = 0.003  # share of the image a component needs to survive (the butterfly is ~0.5%, a motion stroke ~0.04%)


def cut(img):
    a = np.asarray(img.convert("RGB")).astype(np.int16)
    hi, lo = a.max(2), a.min(2)
    light = (lo > 200) & (hi - lo < 40)
    lab, _ = components(light)
    edge = set(lab[0]) | set(lab[-1]) | set(lab[:, 0]) | set(lab[:, -1])
    lsizes = components(light)[1]
    # Background: light regions touching the edge, plus big enclosed ones (inside the tail-chase loop); eye whites are tiny.
    bg = np.isin(lab, [e for e in edge if e] + [i for i, n in enumerate(lsizes) if i and n >= 0.01 * lab.size])
    lab, sizes = components(~bg)
    big = [i for i, s in enumerate(sizes) if i and s >= FLOOR * lab.size]
    keep = np.isin(lab, big)
    alpha = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
    out = img.convert("RGBA"); out.putalpha(alpha)
    out = out.crop(out.getbbox())
    k = PUP_PX / max(out.size)
    return out.resize((round(out.width * k), round(out.height * k)), Image.LANCZOS) if k < 1 else out


OUT.mkdir(parents=True, exist_ok=True)
for src in sorted((ROOT / "art/mascot").glob("pup-*.jpg")):
    name = "pup_" + src.stem[4:].replace("-", "_")
    pup = cut(Image.open(src))
    pup.save(OUT / f"{name}.webp", quality=92, method=6)
    print(name, pup.size, (OUT / f"{name}.webp").stat().st_size // 1024, "KB")
