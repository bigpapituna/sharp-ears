"""Profile avatars (2.1): pup_wear_base with its sweatband's red stripes in eight colours.

The band is found by colour: bright saturated reds above the eyes (the tongue and cheeks are below them, the dark
outline is too dark), the big connected patches (the two red stripes), grown by two pixels into their soft edge.
Each red pixel takes the avatar colour at the pixel's own brightness, so the stripes keep their shading; the white stripes stay white. Avatar 0 keeps the painted red.
Output: app/src/main/res/drawable-nodpi/pup_avatar_<n>.webp at AVATAR_PX on the long side.
    python -I scripts/tint_avatars.py
Colours match ui/family/Avatar.kt AVATAR_COLOURS (red, blue, green, purple, yellow, pink, orange, teal).
"""
import colorsys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable-nodpi"
AVATAR_PX = 360
BAND_BOTTOM = 0.33          # share of the height: the band sits above this, the tongue and cheeks below
COLOURS = ["#D2443F", "#3E7BDB", "#3EA35A", "#8B5CD6", "#F2C12E", "#E8609E", "#F08A2E", "#1FA69A"]


def band_mask(rgb, alpha):
    h, w, _ = rgb.shape
    f = rgb / 255.0
    mx, mn = f.max(2), f.min(2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    r, g, b = f[..., 0], f[..., 1], f[..., 2]
    reddish = (r >= g) & (r >= b) & ((g - b) < 0.12) & (r - np.maximum(g, b) > 0.08)
    seed = reddish & (sat > 0.3) & (mx > 0.5) & (alpha > 10)     # the dark brown outline is below 0.45
    seed[int(h * BAND_BOTTOM):] = False
    # The big 4-connected patches of seed pixels: the band's two red stripes (stray red pixels on the ears are tiny).
    lab = np.zeros((h, w), np.int32)
    sizes, n = {}, 0
    for y, x in zip(*np.nonzero(seed)):
        if lab[y, x]:
            continue
        n += 1
        stack, count = [(y, x)], 0
        lab[y, x] = n
        while stack:
            cy, cx = stack.pop()
            count += 1
            for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                if 0 <= ny < h and 0 <= nx < w and seed[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n
                    stack.append((ny, nx))
        sizes[n] = count
    biggest = max(sizes.values())
    mask = np.isin(lab, [i for i, c in sizes.items() if c >= 0.2 * biggest])
    # Grow two pixels into the anti-aliased edge where the red is weaker.
    soft = reddish & (sat > 0.12) & (mx > 0.45) & (alpha > 10)
    for _ in range(2):
        grown = mask.copy()
        grown[1:] |= mask[:-1]; grown[:-1] |= mask[1:]; grown[:, 1:] |= mask[:, :-1]; grown[:, :-1] |= mask[:, 1:]
        mask = mask | (grown & soft)
    return mask, sat


def tinted(img, colour):
    a = np.asarray(img.convert("RGBA")).astype(np.float64)
    rgb, alpha = a[..., :3], a[..., 3]
    mask, sat = band_mask(rgb, alpha)
    tr, tg, tb = (int(colour[i:i + 2], 16) / 255.0 for i in (1, 3, 5))
    th, ts, tv = colorsys.rgb_to_hsv(tr, tg, tb)
    v = rgb.max(2) / 255.0
    ref = np.percentile(v[mask], 90)            # the band's bright red maps to the target colour itself
    out = rgb.copy()
    ys, xs = np.nonzero(mask)
    for y, x in zip(ys, xs):
        k = min(1.0, v[y, x] / ref)
        nr, ng, nb = colorsys.hsv_to_rgb(th, ts, min(1.0, tv * k))
        w = min(1.0, sat[y, x] / 0.45)          # edge pixels blend toward the outline
        out[y, x] = (1 - w) * rgb[y, x] + w * np.array([nr, ng, nb]) * 255
    a[..., :3] = out
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def main():
    base = Image.open(RES / "pup_wear_base.webp").convert("RGBA")
    k = AVATAR_PX / max(base.size)
    for i, c in enumerate(COLOURS):
        img = base if i == 0 else tinted(base, c)
        img = img.resize((round(base.width * k), round(base.height * k)), Image.LANCZOS)
        img.save(RES / f"pup_avatar_{i}.webp", "WEBP", quality=88, method=6)
        print(f"pup_avatar_{i}.webp {c} {img.size}")


if __name__ == "__main__":
    main()
