"""Cut the painted hand frames (art/hand/hand-right-1..6.jpg, hand-left-1..3.jpg; Gemini, made by the owner) into
transparent WebPs for the app, and write the fingertip table the app places them with.

Background: near-white pixels reachable from the image edge (the frames are on plain white). The edge is feathered
from how far each rim pixel is from white, and the white is taken back out of its colour so no halo shows on the
dark stage. The right frames share one camera; each is shifted so its sweater cuff is centred. Left 4 to 6 are right
4 to 6 mirrored. The painted left 1 to 3 hold their fingers where the mirrored right ones do but their cuff sits
apart, so they are lined up by their fingertips (index to ring), which is what the keys see. All frames are cropped
to one box, so a cross-fade between poses only moves the fingers. The sleeve fades out over its last rows.

No painted frame presses finger 2 or 4, so they are made from the resting frame (right 3): the finger's strip, from
its tip down to its base knuckle, is squashed toward the knuckle (foreshortened, as the painted middle-finger press
in right 4 is), darkened a touch, and blended back in with a feathered base. Also writes a blurred silhouette per
hand for the soft shadow.
    python -I scripts/cut_hand.py [--debug]   (--debug: out/hand-debug/*.png with the anchors drawn on, and
                                               out/hand-press-compare.png: rest, middle press, index and ring made)
"""
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art/hand"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
KT = ROOT / "app/src/main/java/com/bigpapituna/sharpears/ui/motion/HandAnchors.kt"
DEBUG = "--debug" in sys.argv

SIZE = 1024
MAX_H = 768
CUFF_X = 535.5            # the right frames' cuff centre at row 960 (its mask runs 369..702)

# Hand-measured in each source JPG (px, 1024 square): the middle of each nail, thumb first, where the fingertip meets
# a key. Read off the frames (the top of each fingertip, then about 22 px down to the nail's middle; for a curled
# finger the top of the curl) and checked with --debug.
ANCHORS = {
    "right-1": [(286, 388), (399, 164), (510, 116), (599, 163), (704, 272)],
    "right-2": [(302, 379), (397, 209), (494, 180), (588, 211), (694, 292)],
    "right-3": [(296, 390), (397, 209), (494, 180), (589, 210), (694, 292)],
    "right-4": [(296, 390), (397, 209), (503, 249), (589, 210), (694, 292)],
    "right-5": [(301, 410), (397, 209), (494, 180), (588, 210), (694, 292)],
    "right-6": [(296, 390), (397, 209), (494, 180), (587, 211), (688, 324)],
    "left-1": [(734, 388), (622, 164), (513, 116), (417, 162), (313, 273)],
    "left-2": [(742, 380), (626, 209), (526, 179), (428, 211), (321, 292)],
    "left-3": [(750, 387), (618, 209), (515, 181), (427, 213), (325, 296)],
}
# The middle of the wrist where it enters the cuff (right frames; the painted left ones in their own source).
WRIST = {"right": (536, 797), "left-1": (487, 797), "left-2": (535, 797), "left-3": (535, 797)}

# The made presses: each finger's strip in right 3 (an outline running down its gaps, measured row by row from the
# mask), the base knuckle row it is squashed toward, and how much it keeps of its length. Tuned side by side with
# right 4, whose middle finger drops about 70 px; 0.78 reads the same at the size the app draws.
PRESS = {
    2: dict(outline=[(350, 160), (448, 160), (463, 300), (476, 350), (479, 410), (366, 410), (362, 300)], base=410),
    4: dict(outline=[(548, 160), (662, 160), (665, 300), (668, 350), (658, 410), (570, 410), (571, 360), (576, 300), (566, 250)], base=410),
}
PRESS_KEEP = 0.78
PRESS_DARKEN = 0.97


def background(a):
    """Near-white pixels connected to the image edge."""
    hi, lo = a.max(2), a.min(2)
    light = (lo >= 232) & (hi - lo <= 24)
    bg = np.zeros_like(light)
    bg[0], bg[-1], bg[:, 0], bg[:, -1] = light[0], light[-1], light[:, 0], light[:, -1]
    while True:
        g = bg.copy()
        g[1:] |= bg[:-1]; g[:-1] |= bg[1:]; g[:, 1:] |= bg[:, :-1]; g[:, :-1] |= bg[:, 1:]
        g &= light
        if (g == bg).all():
            return bg
        bg = g


def largest(mask):
    """The largest 4-connected region of [mask]."""
    h, w = mask.shape
    lab = np.zeros((h, w), np.int32)
    best, best_n, n = 0, 0, 0
    for y0, x0 in zip(*np.nonzero(mask)):
        if lab[y0, x0]:
            continue
        n += 1; size = 0
        q = deque([(y0, x0)]); lab[y0, x0] = n
        while q:
            y, x = q.popleft(); size += 1
            for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n; q.append((yy, xx))
        if size > best_n:
            best, best_n = n, size
    return lab == best


def cut(path):
    """Premultiplied RGBA floats (0..1) of the frame at [path]: background gone, edge feathered."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    fg = ~background(a.astype(np.int16))
    # JPEG specks in the white: keep the one big shape (found on a quarter-size copy, which is quick).
    small = np.asarray(Image.fromarray((fg * 255).astype(np.uint8)).resize((256, 256), Image.NEAREST)) > 127
    keep = Image.fromarray((largest(small) * 255).astype(np.uint8)).resize((SIZE, SIZE), Image.NEAREST)
    fg &= np.asarray(keep.filter(ImageFilter.MaxFilter(9))) > 127
    # Off-white JPEG mush caught in the narrow crotches between fingers: near the background and nearly white.
    hi, lo = a.max(2), a.min(2)
    near = np.asarray(Image.fromarray((~fg * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(13))) > 127
    fg &= ~(near & (lo >= 215) & (hi - lo <= 28))
    # The rim: a band either side of the mask edge gets its alpha from how far it is from white.
    m = Image.fromarray((fg * 255).astype(np.uint8))
    inner = np.asarray(m.filter(ImageFilter.MinFilter(7))) > 127
    outer = np.asarray(m.filter(ImageFilter.MaxFilter(3))) > 127
    band = outer & ~inner
    # The paint behind each rim pixel: the colour of the shape just inside it (a normalised blur of the inner pixels).
    w = Image.fromarray((inner * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))
    w = np.asarray(w).astype(np.float32)[..., None] / 255.0
    sums = np.dstack([np.asarray(Image.fromarray((a[..., c] * inner).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))) for c in range(3)])
    paint = np.clip(sums.astype(np.float32) / np.maximum(w, 1e-3), 0, 255)
    # A rim pixel is paint over white: c = f * paint + (1 - f) * 255. Solve f (least squares over the channels) and
    # keep the paint's colour, so no pale fringe shows on the dark stage.
    d = 255.0 - paint
    f = np.clip(((255.0 - a) * d).sum(2) / np.maximum((d * d).sum(2), 1.0), 0, 1)
    # Deep in a gap between fingers the shape is too far off to say what paint is there: let it go.
    f = np.where(w[..., 0] < 0.08, 0.0, f)
    alpha = np.where(inner, 1.0, np.where(band, f, 0.0))
    rgb = np.where(band[..., None], paint, a) / 255.0
    alpha = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.5))).astype(np.float32) / 255.0
    return np.dstack([rgb * alpha[..., None], alpha])


def press(rest, finger):
    """[rest] (premultiplied) with [finger]'s strip squashed toward its knuckle: the finger pressing its key."""
    spec = PRESS[finger]
    base = spec["base"]
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon(spec["outline"], fill=255)
    mask = np.asarray(mask.filter(ImageFilter.GaussianBlur(1.5))).astype(np.float32) / 255.0
    # Feather the base: all strip at the top, all hand at the knuckle row, so the squash meets the hand without a seam.
    rows = np.arange(SIZE, dtype=np.float32)
    mask *= np.clip((base - rows) / 60.0, 0, 1)[:, None]
    strip = rest * mask[..., None]
    # Squash toward the base row: output row y shows input row base + (y - base) / keep.
    src = np.clip(base + (rows - base) / PRESS_KEEP, 0, SIZE - 1)
    lo = np.floor(src).astype(int); hi = np.minimum(lo + 1, SIZE - 1); t = (src - lo)[:, None, None]
    moved = strip[lo] * (1 - t) + strip[hi] * t
    moved[..., :3] *= PRESS_DARKEN
    return rest * (1 - mask[..., None]) + moved


def pressed_anchors(finger):
    pts = list(ANCHORS["right-3"])
    x, y = pts[finger - 1]
    base = PRESS[finger]["base"]
    pts[finger - 1] = (x, base + (y - base) * PRESS_KEEP)
    return pts


def mirror(img):
    return img[:, ::-1].copy()


def shift(img, dx):
    out = np.zeros_like(img)
    if dx >= 0:
        out[:, dx:] = img[:, :SIZE - dx]
    else:
        out[:, :SIZE + dx] = img[:, -dx:]
    return out


def to_image(pm):
    """Premultiplied floats to a straight-alpha RGBA image."""
    a = pm[..., 3:4]
    rgb = np.where(a > 1e-4, pm[..., :3] / np.maximum(a, 1e-4), 0)
    return Image.fromarray((np.dstack([rgb, a]) * 255 + 0.5).clip(0, 255).astype(np.uint8), "RGBA")


def mean_x(pts):
    return sum(p[0] for p in pts[1:4]) / 3


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    # Every frame as (name, premultiplied image already mirrored, its anchors and wrist in that image, dx).
    right = {i: cut(ART / f"hand-right-{i}.jpg") for i in range(1, 7)}
    right["press2"] = press(right[3], 2)
    right["press4"] = press(right[3], 4)
    r_pts = {i: list(ANCHORS[f"right-{i}"]) for i in range(1, 7)}
    r_pts["press2"] = pressed_anchors(2)
    r_pts["press4"] = pressed_anchors(4)
    order = [1, 2, 3, 4, 5, 6, "press2", "press4"]
    dx_r = round(512 - CUFF_X)
    dx_m = round(512 - (SIZE - 1 - CUFF_X))
    flip = lambda pts: [(SIZE - 1 - x, y) for x, y in pts]
    frames = []
    for k in order:
        frames.append((f"hand_r_{k}", right[k], r_pts[k] + [WRIST["right"]], dx_r))
    for k in order:
        if k in (1, 2, 3):
            img = cut(ART / f"hand-left-{k}.jpg")
            pts = list(ANCHORS[f"left-{k}"])
            dx = round(mean_x(flip(r_pts[k])) + dx_m - mean_x(pts))
            frames.append((f"hand_l_{k}", img, pts + [WRIST[f"left-{k}"]], dx))
        else:
            frames.append((f"hand_l_{k}", mirror(right[k]), flip(r_pts[k] + [WRIST["right"]]), dx_m))
    shifted = [(n, shift(img, dx), [(x + dx, y) for x, y in pts]) for n, img, pts, dx in frames]
    # One crop box for all: the union of every frame's shape, a little margin, centred on x 512.
    ys, xs = np.nonzero(np.max([img[..., 3] for _, img, _ in shifted], axis=0) > 0.02)
    half = max(512 - xs.min(), xs.max() - 512) + 8
    crop = (512 - half, max(ys.min() - 8, 0), 512 + half, SIZE)
    scale = MAX_H / (crop[3] - crop[1])
    w = round((crop[2] - crop[0]) * scale)
    table = []
    shadows = {}
    for name, img, pts in shifted:
        pm = img[crop[1]:crop[3], crop[0]:crop[2]].copy()
        fade = 70
        ramp = np.ones(pm.shape[0], np.float32); ramp[-fade:] = np.linspace(1, 0, fade) ** 1.5
        pm *= ramp[:, None, None]
        im = to_image(pm).resize((w, MAX_H), Image.LANCZOS)
        im.save(OUT / f"{name}.webp", quality=90, method=6)
        uv = [((x - crop[0]) / (crop[2] - crop[0]), (y - crop[1]) / (crop[3] - crop[1])) for x, y in pts]
        table.append((name, uv))
        if name in ("hand_r_3", "hand_l_3"):
            # The soft shadow: the resting hand's silhouette, blurred, at a quarter size (it is drawn scaled up).
            sil = Image.new("RGBA", im.size, (0, 0, 0, 0))
            sil.putalpha(im.getchannel("A"))
            shadows[name[5]] = sil.resize((im.width // 4, im.height // 4), Image.BILINEAR).filter(ImageFilter.GaussianBlur(3))
        print(name, im.size, f"{(OUT / f'{name}.webp').stat().st_size // 1024} KB")
        if DEBUG:
            dbg = Image.new("RGBA", im.size, (40, 36, 34, 255))
            dbg.alpha_composite(im)
            d = ImageDraw.Draw(dbg)
            for k, (u, v) in enumerate(uv):
                x, y = u * im.width, v * im.height
                col = (0, 200, 255, 255) if k < 5 else (255, 60, 60, 255)
                d.ellipse((x - 6, y - 6, x + 6, y + 6), outline=col, width=3)
                d.text((x + 9, y - 6), str(k + 1) if k < 5 else "w", fill=col)
            (ROOT / "out/hand-debug").mkdir(parents=True, exist_ok=True)
            dbg.convert("RGB").save(ROOT / f"out/hand-debug/{name}.png")
    for h, sh in shadows.items():
        sh.save(OUT / f"hand_shadow_{h}.webp", quality=80, method=6)
        print(f"hand_shadow_{h}", sh.size)
    if DEBUG:
        # Rest, the painted middle press, and the two made presses, side by side on the light and the dark stage.
        names = ["hand_r_3", "hand_r_4", "hand_r_press2", "hand_r_press4"]
        tiles = [Image.open(OUT / f"{n}.webp") for n in names]
        tw, th = tiles[0].size
        top = int(th * 0.5)
        sheet = Image.new("RGBA", (tw * 4, top * 2), (0, 0, 0, 255))
        for row, bg in enumerate([(250, 246, 240, 255), (28, 24, 22, 255)]):
            for k, t in enumerate(tiles):
                cell = Image.new("RGBA", (tw, top), bg)
                cell.alpha_composite(t.crop((0, 0, tw, top)))
                sheet.paste(cell, (k * tw, row * top))
        d = ImageDraw.Draw(sheet)
        for k, n in enumerate(["3 rest", "4 middle (painted)", "press 2 (made)", "press 4 (made)"]):
            d.text((k * tw + 8, 6), n, fill=(200, 60, 40, 255))
        sheet.convert("RGB").save(ROOT / "out/hand-press-compare.png")
    rows = []
    for name, uv in table:
        xs_ = ", ".join(f"{u:.4f}f" for u, _ in uv)
        ys_ = ", ".join(f"{v:.4f}f" for _, v in uv)
        rows.append(f"    /* {name} */ HandAnchors(floatArrayOf({xs_}), floatArrayOf({ys_})),")
    ids = ", ".join(f"R.drawable.{n}" for n, _ in table)
    KT.write_text(f"""package com.bigpapituna.sharpears.ui.motion

import com.bigpapituna.sharpears.R

// Generated by scripts/cut_hand.py from the hand-measured points in it; edit those and run it again.

/**
 * Where a painted hand frame's fingertips and wrist sit, in 0..1 of the frame ({w} x {MAX_H} px): [x] and [y] hold
 * fingers 1 (thumb) to 5 at 0..4 (the middle of each nail, where the tip meets a key) and the wrist at 5.
 */
internal class HandAnchors(val x: FloatArray, val y: FloatArray)

/** A frame's width over its height. */
internal const val HAND_FRAME_ASPECT = {w / MAX_H:.5f}f

/** The frames: right 1..6, right press 2, right press 4, then the same for the left hand. */
internal val HAND_FRAMES = intArrayOf({ids})

/** Anchors for [HAND_FRAMES], in the same order. */
internal val HAND_ANCHORS = arrayOf(
{chr(10).join(rows)}
)
""", encoding="utf-8")
    print("crop", crop, "wrote", KT.relative_to(ROOT))


main()
