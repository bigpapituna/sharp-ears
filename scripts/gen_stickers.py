"""Placeholder stickers (2.1, world team): the 30 subjects of art/GEMINI-BRIEF.md section C as flat shapes; 2.3 adds 40 more,
ten for each new place (the workshop, the treehouse, the night sky, the fair).

Each sticker is a few coloured shapes in the pup's style (thick dark brown outline on every shape, a soft shade on
the lower right, a highlight on the upper left), inside a thick white die-cut border with a hairline edge, on a
transparent 512 px square. Drawn at 4x and scaled down, so edges are smooth. The painted Gemini stickers replace
these one for one under the same names.

Output: app/src/main/res/drawable-nodpi/sticker_<place>_<n>.webp (place: garden, pond, concert, workshop, treehouse,
nightsky, fair; n: 1..10)
    python -I scripts/gen_stickers.py [--sheet out.png] [--only fair_3] [--place fair]
2.5 adds the stones' own stickers, one per Playground lesson and per Basics lesson (engine/world/LevelStickers.kt),
sticker_lv_<lesson id with _ for ->.webp, in the same style:
    python -I scripts/gen_stickers.py --levels [--only pond_3] [--sheet out.png]
The names and order match engine/world/Stickers.kt.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable-nodpi"
PX = 512
S = 4                       # supersampling
N = PX * S
INK = (84, 50, 30)          # the pup's outline brown
LINE = 8                    # outline width, px at 512
BORDER = 22                 # white border, px at 512
EDGE = 2.5                  # the hairline around the border

# Pastel colours, as in the pup art (honey gold, cream, soft reds and blues).
RED, RED_D = (232, 96, 84), (196, 66, 60)
PINK, PINK_D = (246, 168, 178), (222, 128, 146)
ORANGE, ORANGE_D = (246, 166, 84), (222, 128, 54)
YELLOW, YELLOW_D = (252, 216, 96), (234, 182, 58)
GOLD, GOLD_D = (244, 190, 80), (214, 150, 46)
GREEN, GREEN_D = (128, 196, 112), (88, 156, 84)
LEAF, LEAF_D = (104, 178, 96), (70, 140, 70)
TEAL, TEAL_D = (96, 194, 184), (62, 156, 150)
BLUE, BLUE_D = (116, 174, 230), (78, 136, 200)
SKY, SKY_D = (184, 222, 244), (140, 192, 226)
PURPLE, PURPLE_D = (170, 140, 220), (130, 102, 190)
WOOD, WOOD_D = (196, 140, 92), (160, 104, 64)
SAND, SAND_D = (232, 196, 140), (206, 162, 104)
CREAM, CREAM_D = (252, 244, 226), (226, 210, 184)
WHITE, WHITE_D = (255, 255, 255), (222, 226, 236)
SILVER, SILVER_D = (210, 216, 228), (164, 172, 190)
GREY, GREY_D = (168, 176, 196), (128, 138, 162)
DARK, DARK_D = (74, 62, 66), (52, 42, 46)
EYE = (60, 38, 26)


# ---- masks (coordinates in 512 units) ------------------------------------------------------------------------

def blank():
    return Image.new("L", (N, N), 0)


def ellipse(cx, cy, rx, ry=None, angle=0.0):
    ry = rx if ry is None else ry
    m = blank()
    ImageDraw.Draw(m).ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=255)
    return m.rotate(angle, resample=Image.BICUBIC, center=(cx * S, cy * S)) if angle else m


def poly(points):
    m = blank()
    ImageDraw.Draw(m).polygon([(x * S, y * S) for x, y in points], fill=255)
    return m


def rrect(x0, y0, x1, y1, r):
    m = blank()
    ImageDraw.Draw(m).rounded_rectangle([x0 * S, y0 * S, x1 * S, y1 * S], radius=r * S, fill=255)
    return m


def stroke(points, w):
    """A thick polyline with round ends and joints."""
    m = blank()
    d = ImageDraw.Draw(m)
    pts = [(x * S, y * S) for x, y in points]
    d.line(pts, fill=255, width=int(w * S), joint="curve")
    r = w * S / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    return m


def arc(cx, cy, r, a0, a1, w, steps=64):
    """A thick arc from angle a0 to a1 (degrees, 0 = right, clockwise on screen)."""
    pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps)))
           for i in range(steps + 1)]
    return stroke(pts, w)


def star(cx, cy, r, inner=0.48, points=5, rot=-90.0):
    pts = []
    for i in range(points * 2):
        rr = r if i % 2 == 0 else r * inner
        a = math.radians(rot + i * 180.0 / points)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return poly(pts)


def drop(cx, cy, r):
    """A raindrop: a circle with a point on top."""
    return union(ellipse(cx, cy, r), poly([(cx - r * 0.86, cy - r * 0.5), (cx, cy - r * 2.1), (cx + r * 0.86, cy - r * 0.5)]))


def union(*ms):
    out = ms[0]
    for m in ms[1:]:
        out = ImageChops.lighter(out, m)
    return out


def minus(a, b):
    return ImageChops.subtract(a, b)


def inter(a, b):
    return ImageChops.darker(a, b)


def shift(m, dx, dy):
    out = blank()
    out.paste(m, (int(dx * S), int(dy * S)))
    return out


def grow(m, px):
    """The mask grown by about px (512 units): a blur thresholded low, so corners come out round."""
    sigma = px * S / 2.05
    a = np.asarray(m.filter(ImageFilter.GaussianBlur(sigma)))
    return Image.fromarray(np.where(a > 5, 255, 0).astype(np.uint8))


# ---- painting -----------------------------------------------------------------------------------------------

class Sticker:
    def __init__(self):
        self.im = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    def fill(self, mask, colour, alpha=255):
        layer = Image.new("RGBA", (N, N), colour + (alpha,))
        self.im.paste(layer, (0, 0), mask if alpha == 255 else mask.point(lambda v: v * alpha // 255))

    def shape(self, mask, colour, shade=None, light=False, outline=True, lw=LINE, shade_at=(-0.07, -0.08)):
        """One shape: its outline, its colour, a shade on the lower right and a highlight on the upper left."""
        if outline:
            self.fill(grow(mask, lw), INK)
        self.fill(mask, colour)
        box = mask.getbbox()
        if box is None:
            return
        w, h = (box[2] - box[0]) / S, (box[3] - box[1]) / S
        if shade:
            inner = shift(mask, shade_at[0] * w, shade_at[1] * h)
            self.fill(minus(mask, inner), shade)
        if light:
            cx, cy = (box[0] + box[2]) / 2 / S, (box[1] + box[3]) / 2 / S
            spot = ellipse(cx - w * 0.22, cy - h * 0.24, w * 0.12, h * 0.08, angle=35)
            self.fill(inter(spot, mask), (255, 255, 255), alpha=150)

    def line(self, mask, colour=INK):
        """A drawn line (a stroke mask) with no outline of its own."""
        self.fill(mask, colour)

    def eye(self, cx, cy, r):
        self.fill(ellipse(cx, cy, r), EYE)
        self.fill(ellipse(cx - r * 0.32, cy - r * 0.35, r * 0.34), (255, 255, 255))

    def finish(self):
        """The white die-cut border with its hairline edge, then down to 512 px."""
        alpha = self.im.getchannel("A").point(lambda v: 255 if v > 0 else 0)
        border = grow(alpha, BORDER)
        edge = grow(alpha, BORDER + EDGE)
        out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        out.paste(Image.new("RGBA", (N, N), (214, 200, 186, 255)), (0, 0), edge)
        out.paste(Image.new("RGBA", (N, N), (255, 255, 255, 255)), (0, 0), border)
        out.alpha_composite(self.im)
        return out.resize((PX, PX), Image.LANCZOS)


# ---- the 30 subjects ----------------------------------------------------------------------------------------

def grass(s, y=430):
    s.shape(union(*[poly([(x - 16, y + 20), (x, y - 34 - (x * 7) % 20), (x + 16, y + 20)]) for x in range(150, 380, 38)]), GREEN, GREEN_D)


def garden_1(s):  # a red tulip
    s.shape(stroke([(256, 455), (256, 250)], 20), LEAF, outline=True)
    s.shape(ellipse(196, 360, 30, 82, angle=35), LEAF, LEAF_D)
    s.shape(ellipse(318, 372, 28, 76, angle=-38), LEAF, LEAF_D)
    s.shape(ellipse(206, 196, 52, 98, angle=18), RED, RED_D)
    s.shape(ellipse(306, 196, 52, 98, angle=-18), RED, RED_D)
    s.shape(union(ellipse(256, 210, 70, 84), poly([(196, 150), (226, 92), (256, 146), (286, 92), (316, 150), (316, 220), (196, 220)])), RED, RED_D, light=True)


def garden_2(s):  # a smiling sun
    rays = union(*[poly([(256 + 150 * math.cos(a - 0.17), 256 + 150 * math.sin(a - 0.17)),
                         (256 + 214 * math.cos(a), 256 + 214 * math.sin(a)),
                         (256 + 150 * math.cos(a + 0.17), 256 + 150 * math.sin(a + 0.17))])
                   for a in [i * math.pi / 6 for i in range(12)]])
    s.shape(rays, ORANGE, ORANGE_D)
    s.shape(ellipse(256, 256, 146), YELLOW, YELLOW_D, light=True)
    s.eye(206, 236, 16); s.eye(306, 236, 16)
    s.fill(ellipse(180, 292, 24, 14), PINK); s.fill(ellipse(332, 292, 24, 14), PINK)
    s.line(arc(256, 270, 54, 25, 155, 10))


def garden_3(s):  # a blue bird on a branch
    s.shape(stroke([(70, 410), (250, 392), (450, 352)], 26), WOOD, WOOD_D)
    s.shape(ellipse(420, 312, 40, 18, angle=-30), LEAF, LEAF_D)
    s.shape(poly([(150, 300), (66, 250), (90, 330)]), BLUE_D)
    s.shape(ellipse(240, 300, 112, 86, angle=-8), BLUE, BLUE_D, light=True)
    s.shape(ellipse(256, 330, 66, 44, angle=-8), SKY, outline=False)
    s.shape(ellipse(212, 288, 64, 40, angle=-25), BLUE_D)
    s.shape(ellipse(330, 214, 66), BLUE, BLUE_D, light=True)
    s.shape(poly([(386, 200), (446, 220), (388, 238)]), ORANGE, ORANGE_D)
    s.eye(342, 202, 13)
    s.fill(ellipse(326, 236, 14, 9), PINK)
    s.line(stroke([(232, 378), (226, 398)], 9)); s.line(stroke([(266, 378), (262, 398)], 9))


def garden_4(s):  # a ladybird
    for side in (-1, 1):
        s.line(stroke([(256 + side * 30, 150), (256 + side * 58, 92)], 8))
        s.shape(ellipse(256 + side * 62, 86, 14), DARK)
    s.shape(ellipse(256, 168, 82, 62), DARK, DARK_D)
    s.fill(ellipse(226, 160, 12), (255, 255, 255)); s.fill(ellipse(286, 160, 12), (255, 255, 255))
    s.fill(ellipse(229, 162, 6), EYE); s.fill(ellipse(289, 162, 6), EYE)
    s.shape(ellipse(256, 300, 166, 150), RED, RED_D, light=True)
    s.line(stroke([(256, 160), (256, 446)], 9))
    for x, y, r in [(186, 250, 30), (326, 250, 30), (176, 352, 26), (336, 352, 26), (226, 412, 20), (290, 412, 20)]:
        s.fill(ellipse(x, y, r), DARK)


def garden_5(s):  # a watering can
    s.shape(stroke([(320, 320), (452, 168)], 30), TEAL, TEAL_D)
    s.shape(ellipse(458, 160, 34, 22, angle=-50), TEAL_D)
    s.shape(arc(186, 222, 80, 180, 360, 22), TEAL_D)
    s.shape(rrect(96, 210, 336, 430, 40), TEAL, TEAL_D, light=True)
    s.shape(ellipse(216, 210, 120, 26), TEAL_D)
    s.fill(ellipse(216, 210, 96, 16), (60, 120, 116))
    s.line(stroke([(110, 300), (322, 300)], 7), TEAL_D)
    for x, y in [(470, 230), (448, 268), (488, 272)]:
        s.shape(drop(x, y, 12), SKY, outline=True, lw=5)


def garden_6(s):  # a snail with a spiral shell
    s.shape(union(rrect(70, 340, 430, 420, 40), ellipse(400, 330, 56, 66)), SAND, SAND_D, light=True)
    for side in (-14, 22):
        s.line(stroke([(392 + side, 278), (380 + side * 2.2, 196)], 9))
        s.shape(ellipse(380 + side * 2.2, 192, 15), DARK)
    s.eye(388, 314, 11); s.eye(424, 314, 11)
    s.line(arc(410, 340, 22, 30, 150, 7))
    s.shape(ellipse(230, 272, 128), ORANGE, ORANGE_D, light=True)
    pts = [(230 + (10 + 6.8 * t) * math.cos(t * 0.72), 272 + (10 + 6.8 * t) * math.sin(t * 0.72)) for t in [i * 0.1 for i in range(0, 160)]]
    s.line(stroke(pts, 11), ORANGE_D)


def garden_7(s):  # a dog house with a red roof
    s.shape(poly([(110, 250), (256, 130), (402, 250), (402, 440), (110, 440)]), SAND, SAND_D, light=True)
    for y in (300, 350, 400):
        s.line(stroke([(118, y), (394, y)], 6), SAND_D)
    s.shape(union(stroke([(256, 120), (84, 268)], 46), stroke([(256, 120), (428, 268)], 46)), RED, RED_D)
    s.shape(union(rrect(196, 300, 316, 446, 0), ellipse(256, 304, 60)), DARK, outline=True)
    s.shape(rrect(214, 196, 298, 232, 12), CREAM, CREAM_D)
    s.line(stroke([(232, 214), (280, 214)], 9), WOOD_D)


def garden_8(s):  # a wooden fence with three posts
    s.shape(rrect(70, 240, 442, 280, 10), WOOD, WOOD_D)
    s.shape(rrect(70, 350, 442, 390, 10), WOOD, WOOD_D)
    for x in (120, 256, 392):
        s.shape(poly([(x - 40, 160), (x, 108), (x + 40, 160), (x + 40, 452), (x - 40, 452)]), SAND, SAND_D, light=True)
        s.fill(ellipse(x, 300, 7), WOOD_D); s.fill(ellipse(x, 400, 7), WOOD_D)
    s.shape(union(*[poly([(x - 14, 470), (x, 420 - (x * 13) % 26), (x + 14, 470)]) for x in range(84, 440, 34)]), GREEN, GREEN_D)


def garden_9(s):  # a butterfly with yellow wings
    s.shape(ellipse(170, 186, 104, 86, angle=-30), YELLOW, YELLOW_D, light=True)
    s.shape(ellipse(342, 186, 104, 86, angle=30), YELLOW, YELLOW_D, light=True)
    s.shape(ellipse(186, 328, 74, 62, angle=25), YELLOW, YELLOW_D)
    s.shape(ellipse(326, 328, 74, 62, angle=-25), YELLOW, YELLOW_D)
    for x, y, r in [(150, 176, 30), (362, 176, 30), (190, 336, 20), (322, 336, 20)]:
        s.fill(ellipse(x, y, r), ORANGE); s.fill(ellipse(x, y, r * 0.45), (255, 250, 230))
    s.shape(ellipse(256, 262, 24, 120), DARK, DARK_D)
    s.line(arc(226, 112, 40, 200, 330, 8)); s.line(arc(286, 112, 40, 210, 340, 8))
    s.fill(ellipse(198, 96, 11), DARK); s.fill(ellipse(326, 98, 11), DARK)


def garden_10(s):  # a rainbow over a cloud
    for i, c in enumerate([RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE]):
        s.shape(arc(256, 330, 200 - i * 26, 180, 360, 26), c, outline=(i == 0), lw=LINE)
    s.line(arc(256, 330, 213, 180, 360, LINE * 1.2))
    s.line(arc(256, 330, 200 - 6 * 26 + 13, 180, 360, LINE))
    s.shape(union(ellipse(90, 350, 52), ellipse(140, 328, 60), ellipse(176, 372, 44), rrect(50, 360, 214, 410, 25)), WHITE, WHITE_D, light=True)
    s.shape(union(ellipse(422, 350, 52), ellipse(372, 328, 60), ellipse(336, 372, 44), rrect(298, 360, 462, 410, 25)), WHITE, WHITE_D, light=True)


def pond_1(s):  # a green frog on a lily pad
    pad = minus(ellipse(256, 392, 196, 74), poly([(256, 392), (420, 330), (452, 380)]))
    s.shape(pad, LEAF, LEAF_D)
    s.line(stroke([(256, 392), (160, 360)], 5), LEAF_D); s.line(stroke([(256, 392), (180, 430)], 5), LEAF_D)
    s.shape(ellipse(160, 380, 44, 24), GREEN_D); s.shape(ellipse(352, 380, 44, 24), GREEN_D)
    s.shape(ellipse(256, 290, 132, 100), GREEN, GREEN_D, light=True)
    s.shape(ellipse(256, 320, 76, 50), (200, 232, 170), outline=False)
    for x in (196, 316):
        s.shape(ellipse(x, 196, 46), GREEN, GREEN_D)
        s.fill(ellipse(x, 196, 30), (255, 255, 255)); s.eye(x + 4, 200, 16)
    s.line(arc(256, 262, 64, 25, 155, 9))
    s.fill(ellipse(190, 286, 18, 10), PINK); s.fill(ellipse(322, 286, 18, 10), PINK)


def pond_2(s):  # a yellow duckling
    s.shape(ellipse(256, 420, 190, 36), SKY, SKY_D)
    s.shape(union(ellipse(250, 330, 150, 96), poly([(380, 300), (430, 260), (396, 340)])), YELLOW, YELLOW_D, light=True)
    s.shape(ellipse(222, 330, 70, 44, angle=-12), YELLOW_D)
    s.shape(ellipse(186, 190, 92), YELLOW, YELLOW_D, light=True)
    s.shape(union(ellipse(170, 108, 14, 26, angle=-20), ellipse(194, 104, 12, 24, angle=15)), YELLOW_D)
    s.shape(ellipse(96, 206, 48, 22, angle=8), ORANGE, ORANGE_D)
    s.eye(160, 176, 14)
    s.fill(ellipse(196, 222, 20, 12), PINK)


def pond_3(s):  # a goldfish
    s.shape(union(poly([(330, 256), (460, 150), (430, 256), (460, 362)])), ORANGE, ORANGE_D)
    s.shape(ellipse(240, 160, 66, 30, angle=-20), ORANGE_D)
    s.shape(ellipse(230, 262, 160, 112), GOLD, GOLD_D, light=True)
    for x in (230, 280):
        for y in (232, 292):
            s.line(arc(x, y, 26, 300, 420, 6), ORANGE_D)
    s.shape(ellipse(214, 330, 40, 20, angle=30), ORANGE_D)
    s.fill(ellipse(140, 236, 26), (255, 255, 255)); s.eye(142, 240, 15)
    s.line(arc(110, 280, 18, 300, 400, 7))
    for x, y, r in [(92, 150, 16), (64, 100, 11), (104, 64, 8)]:
        s.shape(ellipse(x, y, r), SKY, outline=True, lw=5)


def pond_4(s):  # a dragonfly
    for a, b in [(-24, 1), (-6, 1), (24, -1), (6, -1)]:
        cx = 256 - b * 112
        cy = 196 if abs(a) > 12 else 262
        s.shape(ellipse(cx, cy, 116, 34, angle=a), SKY, SKY_D, light=True)
    s.shape(union(*[ellipse(256, 230 + i * 34, 22 - i * 1.6, 22) for i in range(7)]), TEAL, TEAL_D)
    s.shape(ellipse(256, 168, 46, 40), TEAL, TEAL_D, light=True)
    s.fill(ellipse(232, 158, 20), (255, 255, 255)); s.fill(ellipse(280, 158, 20), (255, 255, 255))
    s.eye(234, 160, 12); s.eye(282, 160, 12)


def pond_5(s):  # a paper boat
    s.shape(union(*[ellipse(x, 420, 46, 26) for x in range(70, 460, 76)]), SKY, SKY_D)
    s.shape(poly([(76, 300), (436, 300), (378, 404), (134, 404)]), WHITE, WHITE_D, light=True)
    s.shape(poly([(256, 92), (360, 300), (152, 300)]), CREAM, CREAM_D, light=True)
    s.line(stroke([(256, 100), (256, 296)], 6), CREAM_D)
    s.line(stroke([(134, 404), (200, 300)], 6), WHITE_D); s.line(stroke([(378, 404), (312, 300)], 6), WHITE_D)
    s.fill(poly([(256, 150), (300, 250), (256, 250)]), (232, 224, 210))


def pond_6(s):  # a turtle
    for x in (150, 330):
        s.shape(ellipse(x, 372, 40, 30), GREEN, GREEN_D)
    s.shape(poly([(110, 330), (60, 350), (110, 356)]), GREEN, GREEN_D)
    s.shape(ellipse(404, 286, 62, 56), GREEN, GREEN_D, light=True)
    s.eye(422, 274, 12)
    s.line(arc(418, 300, 22, 30, 140, 7))
    shell = minus(ellipse(240, 330, 166, 152), rrect(60, 340, 420, 500, 0))
    s.shape(union(shell, rrect(74, 318, 406, 350, 16)), TEAL, TEAL_D, light=True)
    s.shape(rrect(70, 318, 410, 352, 16), (110, 176, 96), outline=True)
    for x, y in [(240, 240), (170, 280), (310, 280)]:
        s.line(poly([(x - 34, y), (x - 17, y - 30), (x + 17, y - 30), (x + 34, y), (x + 17, y + 30), (x - 17, y + 30)]), TEAL_D)


def pond_7(s):  # a water lily flower
    s.shape(minus(ellipse(256, 380, 200, 76), poly([(256, 380), (60, 330), (40, 400)])), LEAF, LEAF_D)
    for i in range(8):
        a = -90 + i * 45
        r = 108 if i % 2 == 0 else 96
        s.shape(ellipse(256 + r * 0.6 * math.cos(math.radians(a)), 270 + r * 0.45 * math.sin(math.radians(a)), 46, 82,
                        angle=-(a + 90)), PINK, PINK_D)
    for i in range(5):
        a = -90 + i * 72 + 36
        s.shape(ellipse(256 + 40 * math.cos(math.radians(a)), 262 + 30 * math.sin(math.radians(a)), 32, 60, angle=-(a + 90)), (252, 206, 214), PINK_D)
    s.shape(ellipse(256, 266, 40, 30), YELLOW, YELLOW_D, light=True)


def pond_8(s):  # a fishing rod with a bobber
    s.shape(union(*[ellipse(x, 440, 50, 22) for x in range(250, 470, 70)]), SKY, SKY_D)
    s.line(stroke([(420, 96), (430, 160), (424, 260), (396, 380)], 5), DARK)
    s.shape(stroke([(70, 450), (420, 92)], 22), WOOD, WOOD_D)
    s.shape(stroke([(70, 450), (150, 368)], 30), DARK)
    s.shape(ellipse(170, 376, 38), SILVER, SILVER_D, light=True)
    s.fill(ellipse(170, 376, 12), SILVER_D)
    bob = ellipse(396, 400, 40)
    s.shape(bob, WHITE, WHITE_D)
    s.fill(inter(bob, rrect(340, 340, 460, 400, 0)), RED)
    s.line(stroke([(358, 400), (434, 400)], 6))
    s.shape(rrect(388, 340, 404, 362, 4), DARK)


def pond_9(s):  # a little wooden bridge
    s.shape(union(*[ellipse(x, 448, 54, 22) for x in range(60, 470, 74)]), SKY, SKY_D)
    s.shape(arc(256, 520, 236, 205, 335, 46), WOOD, WOOD_D)
    for a in range(212, 336, 20):
        x = 256 + 236 * math.cos(math.radians(a)); y = 520 + 236 * math.sin(math.radians(a))
        s.line(stroke([(x - 2, y - 20), (x + 2, y + 20)], 5), WOOD_D)
    s.shape(arc(256, 470, 236, 210, 330, 14), SAND, SAND_D)
    for a in (214, 244, 274, 304, 330):
        x = 256 + 236 * math.cos(math.radians(a)); y0 = 520 + 236 * math.sin(math.radians(a))
        y1 = 470 + 236 * math.sin(math.radians(a))
        s.shape(stroke([(x, y0 - 18), (x, y1)], 18), SAND, SAND_D)


def pond_10(s):  # a rain cloud with three drops
    s.shape(union(ellipse(170, 216, 82), ellipse(270, 170, 104), ellipse(360, 222, 80), rrect(96, 220, 430, 296, 40)), SILVER, SILVER_D, light=True)
    s.eye(222, 236, 12); s.eye(312, 236, 12)
    s.line(arc(266, 250, 30, 30, 150, 7))
    for x, y in [(170, 392), (262, 420), (354, 392)]:
        s.shape(drop(x, y, 26), BLUE, BLUE_D, light=True)


def concert_1(s):  # a gold star
    s.shape(star(256, 270, 210, 0.5), GOLD, GOLD_D, light=True)
    s.eye(222, 262, 15); s.eye(290, 262, 15)
    s.fill(ellipse(198, 302, 20, 12), PINK); s.fill(ellipse(314, 302, 20, 12), PINK)
    s.line(arc(256, 290, 34, 25, 155, 8))


def concert_2(s):  # a trophy
    s.shape(arc(146, 196, 62, 90, 270, 24), GOLD_D)
    s.shape(arc(366, 196, 62, 270, 450, 24), GOLD_D)
    s.shape(rrect(176, 400, 336, 456, 12), WOOD, WOOD_D)
    s.shape(poly([(226, 330), (286, 330), (300, 404), (212, 404)]), GOLD, GOLD_D)
    s.shape(inter(ellipse(256, 150, 124, 196), rrect(100, 96, 412, 360, 0)), GOLD, GOLD_D, light=True)
    s.shape(ellipse(256, 98, 124, 22), GOLD_D)
    s.shape(star(256, 210, 50), YELLOW, outline=True, lw=6)


def concert_3(s):  # a red velvet curtain
    left = poly([(70, 120), (250, 120), (226, 220), (176, 300), (166, 456), (70, 456)])
    right = poly([(262, 120), (442, 120), (442, 456), (346, 456), (336, 300), (286, 220)])
    for half in (left, right):
        s.shape(half, RED, RED_D, light=True)
    for x in (110, 150, 362, 402):
        s.line(stroke([(x, 136), (x, 448)], 9), RED_D)
    s.shape(rrect(52, 82, 460, 128, 22), GOLD, GOLD_D)
    s.shape(ellipse(176, 304, 30, 22), GOLD, GOLD_D); s.shape(ellipse(336, 304, 30, 22), GOLD, GOLD_D)


def concert_4(s):  # a microphone
    s.shape(stroke([(256, 300), (256, 420)], 30), DARK, DARK_D)
    s.shape(rrect(176, 412, 336, 452, 18), DARK, DARK_D)
    s.shape(arc(256, 216, 112, 20, 160, 16), SILVER_D)
    s.shape(rrect(214, 236, 298, 330, 18), DARK, DARK_D)
    head = ellipse(256, 176, 96, 104)
    s.shape(head, SILVER, SILVER_D, light=True)
    for k in range(-3, 4):
        s.line(inter(stroke([(256 + k * 26, 60), (256 + k * 26, 290)], 5), head), SILVER_D)
        s.line(inter(stroke([(140, 176 + k * 26), (372, 176 + k * 26)], 5), head), SILVER_D)


def concert_5(s):  # a small drum
    s.shape(stroke([(140, 90), (290, 210)], 18), SAND, SAND_D); s.shape(ellipse(140, 90, 20), RED, RED_D)
    s.shape(stroke([(380, 90), (230, 210)], 18), SAND, SAND_D); s.shape(ellipse(380, 90, 20), RED, RED_D)
    body = union(rrect(96, 230, 416, 410, 0), ellipse(256, 410, 160, 44))
    s.shape(body, RED, RED_D, light=True)
    pts = [(96 + i * 40, 262 if i % 2 == 0 else 378) for i in range(9)]
    s.line(inter(stroke(pts, 10), body), YELLOW)
    s.shape(rrect(92, 380, 420, 404, 10), GOLD, GOLD_D)
    s.shape(ellipse(256, 230, 160, 44), CREAM, CREAM_D)


def concert_6(s):  # a grand piano seen from the side
    s.shape(poly([(110, 210), (400, 90), (408, 112), (130, 236)]), DARK, DARK_D)
    s.shape(stroke([(270, 168), (300, 250)], 8), GOLD_D)
    for x in (140, 410):
        s.shape(rrect(x - 14, 300, x + 14, 430, 6), DARK, DARK_D)
    body = union(rrect(80, 230, 300, 312, 18), ellipse(370, 268, 80, 46), rrect(300, 222, 380, 314, 0))
    s.shape(body, DARK, DARK_D, light=True)
    s.shape(rrect(52, 232, 116, 262, 6), WHITE, WHITE_D)
    for x in (66, 82, 98):
        s.line(stroke([(x, 236), (x, 248)], 6), DARK)
    s.shape(rrect(250, 412, 300, 426, 6), GOLD, GOLD_D)
    s.line(stroke([(275, 312), (275, 412)], 7), DARK)


def concert_7(s):  # a spotlight
    beam = poly([(300, 170), (470, 60), (470, 340), (330, 236)])
    s.fill(beam, (255, 238, 170), alpha=200)
    s.shape(stroke([(200, 330), (200, 420)], 22), DARK, DARK_D)
    s.shape(union(poly([(120, 456), (280, 456), (240, 412), (160, 412)])), DARK, DARK_D)
    s.shape(arc(200, 250, 92, 120, 240, 18), SILVER_D)
    lamp = poly([(140, 186), (300, 150), (334, 246), (172, 300)])
    s.shape(lamp, GREY, GREY_D, light=True)
    s.shape(ellipse(318, 198, 36, 54, angle=-20), YELLOW, YELLOW_D)
    s.fill(ellipse(312, 186, 12, 18, angle=-20), (255, 255, 255))


def concert_8(s):  # a bouquet of flowers
    for x0, x1 in [(256, 150), (256, 256), (256, 362)]:
        s.shape(stroke([(256, 420), (x1, 180)], 14), LEAF, LEAF_D)
    s.shape(ellipse(170, 270, 26, 54, angle=40), LEAF, LEAF_D); s.shape(ellipse(342, 270, 26, 54, angle=-40), LEAF, LEAF_D)
    for (x, y), c, cd in [((150, 170), PINK, PINK_D), ((362, 170), PURPLE, PURPLE_D), ((256, 130), YELLOW, YELLOW_D)]:
        s.shape(union(*[ellipse(x + 36 * math.cos(a), y + 36 * math.sin(a), 30) for a in [i * math.pi * 2 / 5 for i in range(5)]]), c, cd)
        s.shape(ellipse(x, y, 22), ORANGE, ORANGE_D)
    s.shape(poly([(150, 290), (362, 290), (282, 470), (230, 470)]), SKY, SKY_D, light=True)
    s.shape(union(ellipse(222, 330, 30, 20, angle=20), ellipse(290, 330, 30, 20, angle=-20)), RED, RED_D)
    s.shape(ellipse(256, 332, 14), RED_D)


def concert_9(s):  # a ticket stub
    t = rrect(66, 150, 446, 362, 22)
    t = minus(t, ellipse(66, 256, 34))
    t = minus(t, ellipse(446, 256, 34))
    t = t.rotate(-8, resample=Image.BICUBIC, center=(N / 2, N / 2))
    s.shape(t, YELLOW, YELLOW_D, light=True)
    dash = union(*[rrect(330, y, 342, y + 16, 4) for y in range(160, 356, 30)]).rotate(-8, resample=Image.BICUBIC, center=(N / 2, N / 2))
    s.line(inter(dash, t), ORANGE_D)
    s.shape(star(200, 262, 58).rotate(-8, resample=Image.BICUBIC, center=(N / 2, N / 2)), RED, RED_D, lw=6)
    s.line(stroke([(380, 220), (400, 300)], 10), ORANGE_D)


def concert_10(s):  # fireworks
    for (cx, cy, r, c) in [(176, 196, 120, RED), (348, 178, 104, BLUE), (270, 338, 112, YELLOW)]:
        for i in range(12):
            a = i * math.pi / 6
            s.shape(stroke([(cx + r * 0.32 * math.cos(a), cy + r * 0.32 * math.sin(a)), (cx + r * math.cos(a), cy + r * math.sin(a))], 14), c, lw=5)
            s.shape(ellipse(cx + r * 1.12 * math.cos(a), cy + r * 1.12 * math.sin(a), 9), c, lw=4)
        s.shape(star(cx, cy, 30), WHITE, lw=5)


# ---- 2.3: the workshop, the treehouse, the night sky and the fair (ten each) ----------------------------------------------

def workshop_1(s):  # a hammer
    s.shape(stroke([(150, 440), (330, 190)], 34), WOOD, WOOD_D, light=True)
    head = poly([(250, 150), (350, 78), (430, 190), (330, 262)])
    s.shape(head, SILVER, SILVER_D, light=True)
    s.shape(poly([(250, 150), (196, 120), (176, 160), (270, 230), (300, 214)]), GREY, GREY_D)
    s.line(stroke([(300, 120), (380, 230)], 6), SILVER_D)


def workshop_2(s):  # a saw
    blade = poly([(170, 160), (440, 300), (430, 360), (150, 260)])
    s.shape(blade, SILVER, SILVER_D, light=True)
    teeth = union(*[poly([(150 + i * 28, 262 + i * 9.6), (164 + i * 28, 300 + i * 9.6), (178 + i * 28, 266 + i * 9.6)]) for i in range(10)])
    s.shape(teeth, SILVER_D, outline=True, lw=4)
    handle = minus(rrect(60, 130, 210, 290, 40), ellipse(132, 208, 34, 26))
    s.shape(handle.rotate(20, resample=Image.BICUBIC, center=(135 * S, 210 * S)), RED, RED_D, light=True)
    s.fill(ellipse(400, 316, 8), SILVER_D)


def workshop_3(s):  # a tuning fork, singing
    s.shape(union(stroke([(206, 110), (206, 300)], 34), stroke([(306, 110), (306, 300)], 34), arc(256, 300, 50, 0, 180, 34)), GOLD, GOLD_D, light=True)
    s.shape(stroke([(256, 350), (256, 450)], 30), GOLD, GOLD_D)
    s.shape(ellipse(256, 456, 26, 16), GOLD_D)
    for k, r in enumerate((60, 100)):
        s.line(arc(256, 150, r + 70, 200, 250, 9), BLUE)
        s.line(arc(256, 150, r + 70, 290, 340, 9), BLUE)
    s.shape(star(110, 90, 26), YELLOW, outline=True, lw=5)
    s.shape(star(410, 96, 20), YELLOW, outline=True, lw=5)


def workshop_4(s):  # a glue pot with its brush
    s.shape(stroke([(300, 250), (380, 70)], 18), WOOD, WOOD_D)
    s.shape(ellipse(386, 62, 20, 26, angle=-25), CREAM, CREAM_D)
    s.shape(union(rrect(130, 230, 382, 440, 50), ellipse(256, 440, 126, 26)), BLUE, BLUE_D, light=True)
    s.shape(ellipse(256, 232, 126, 30), BLUE_D)
    s.shape(ellipse(256, 236, 106, 20), CREAM, outline=False)
    s.shape(union(ellipse(170, 238, 26, 30), stroke([(170, 250), (166, 300)], 26)), CREAM, CREAM_D)
    s.shape(rrect(170, 316, 342, 380, 12), YELLOW, YELLOW_D)
    s.line(stroke([(196, 348), (316, 348)], 9), ORANGE_D)


def workshop_5(s):  # a wooden ruler
    r = rrect(60, 200, 452, 300, 14).rotate(-20, resample=Image.BICUBIC, center=(N / 2, N / 2))
    s.shape(r, YELLOW, YELLOW_D, light=True)
    marks = union(*[rrect(80 + i * 23, 200, 85 + i * 23, 200 + (40 if i % 4 == 0 else 22), 2) for i in range(16)])
    s.line(inter(marks.rotate(-20, resample=Image.BICUBIC, center=(N / 2, N / 2)), r), WOOD_D)


def workshop_6(s):  # a piano key: white, with its black neighbour
    s.shape(rrect(170, 70, 320, 450, 26), WHITE, WHITE_D, light=True)
    s.shape(rrect(282, 60, 372, 300, 18), DARK, DARK_D, light=True)
    s.fill(ellipse(240, 400, 30, 14), (236, 230, 222))


def workshop_7(s):  # a screwdriver
    s.shape(stroke([(250, 270), (400, 90)], 18), SILVER, SILVER_D)
    s.shape(poly([(388, 104), (432, 50), (444, 62), (402, 116)]), SILVER_D)
    h = union(rrect(150, 240, 250, 420, 42)).rotate(-40, resample=Image.BICUBIC, center=(200 * S, 330 * S))
    s.shape(h, ORANGE, ORANGE_D, light=True)
    for k in (-1, 0, 1):
        s.line(stroke([(186 + k * 22, 300 - k * 16), (236 + k * 22, 360 - k * 16)], 7), ORANGE_D)


def workshop_8(s):  # a paintbrush with a drop of paint
    s.shape(stroke([(140, 430), (300, 200)], 30), RED, RED_D, light=True)
    s.shape(poly([(286, 214), (330, 160), (360, 182), (316, 236)]), SILVER, SILVER_D)
    s.shape(poly([(330, 160), (400, 70), (440, 96), (360, 182)]), CREAM, CREAM_D)
    s.shape(poly([(380, 100), (420, 50), (452, 76), (414, 126)]), BLUE, BLUE_D)
    s.shape(drop(380, 410, 30), BLUE, BLUE_D, light=True)


def workshop_9(s):  # a toolbox
    s.shape(arc(256, 210, 70, 180, 360, 22), GREY, GREY_D)
    s.shape(rrect(90, 200, 422, 420, 26), RED, RED_D, light=True)
    s.shape(rrect(90, 200, 422, 250, 18), RED_D)
    s.shape(rrect(226, 240, 286, 280, 10), SILVER, SILVER_D)
    s.shape(stroke([(160, 186), (200, 110)], 16), SILVER, SILVER_D)
    s.shape(ellipse(204, 104, 20, 14, angle=-60), SILVER_D)
    s.shape(stroke([(340, 196), (350, 120)], 14), WOOD, WOOD_D)


def workshop_10(s):  # a work lamp, lit
    s.fill(poly([(250, 200), (110, 420), (330, 420)]), (255, 238, 170), alpha=170)
    s.shape(rrect(150, 420, 330, 456, 14), DARK, DARK_D)
    s.shape(union(stroke([(240, 430), (300, 300)], 18), stroke([(300, 300), (260, 180)], 18)), GREY, GREY_D)
    s.shape(ellipse(300, 300, 18), SILVER_D)
    shade = poly([(200, 130), (310, 110), (330, 210), (180, 230)])
    s.shape(shade.rotate(15, resample=Image.BICUBIC, center=(255 * S, 170 * S)), GREEN, GREEN_D, light=True)
    s.shape(ellipse(250, 222, 50, 18, angle=10), YELLOW, YELLOW_D)


def treehouse_1(s):  # an acorn
    s.shape(stroke([(262, 120), (284, 70)], 18), WOOD_D)
    s.shape(ellipse(256, 300, 112, 140), ORANGE, ORANGE_D, light=True)
    s.shape(union(ellipse(256, 196, 140, 70), rrect(126, 180, 386, 230, 30)), WOOD, WOOD_D, light=True)
    for x in range(150, 380, 34):
        s.line(stroke([(x, 186), (x + 20, 220)], 6), WOOD_D)
    s.eye(222, 300, 14); s.eye(290, 300, 14)
    s.line(arc(256, 320, 30, 30, 150, 8))
    s.fill(ellipse(196, 336, 18, 10), PINK); s.fill(ellipse(316, 336, 18, 10), PINK)


def treehouse_2(s):  # a squirrel with a big tail
    s.shape(union(ellipse(360, 230, 80, 150, angle=-20), ellipse(400, 130, 70, 60)), ORANGE, ORANGE_D, light=True)
    s.shape(ellipse(230, 330, 100, 110), ORANGE, ORANGE_D, light=True)
    s.shape(ellipse(220, 360, 56, 64), CREAM, outline=False)
    s.shape(ellipse(196, 200, 78, 70), ORANGE, ORANGE_D, light=True)
    s.shape(poly([(150, 160), (150, 100), (190, 140)]), ORANGE_D)
    s.shape(poly([(220, 140), (240, 84), (256, 140)]), ORANGE_D)
    s.eye(176, 196, 13)
    s.fill(ellipse(140, 220, 12), DARK)
    s.shape(ellipse(200, 300, 30, 40), WOOD, WOOD_D)
    s.shape(ellipse(200, 268, 30, 12), WOOD_D)


def treehouse_3(s):  # an owl on a branch
    s.shape(stroke([(70, 420), (450, 400)], 26), WOOD, WOOD_D)
    s.shape(union(ellipse(256, 270, 130, 150), poly([(150, 150), (170, 90), (210, 140)]), poly([(362, 150), (342, 90), (302, 140)])), WOOD, WOOD_D, light=True)
    s.shape(ellipse(256, 320, 84, 90), SAND, outline=False)
    for x in (206, 306):
        s.shape(ellipse(x, 220, 44), CREAM, CREAM_D)
        s.eye(x, 222, 22)
    s.shape(poly([(240, 254), (272, 254), (256, 290)]), ORANGE, ORANGE_D)
    for x in (220, 290):
        s.shape(union(*[ellipse(x + k * 14, 412, 8, 14) for k in (-1, 0, 1)]), ORANGE, ORANGE_D, lw=5)


def treehouse_4(s):  # a rope swing on a branch
    s.shape(stroke([(40, 90), (470, 110)], 34), WOOD, WOOD_D, light=True)
    s.shape(ellipse(420, 70, 40, 24, angle=-20), LEAF, LEAF_D)
    for x in (170, 342):
        s.line(stroke([(x, 100), (x + (0 if x < 200 else 0), 380)], 12), WOOD_D)
        s.line(stroke([(x, 100), (x, 380)], 6), SAND)
    s.shape(rrect(130, 370, 382, 412, 12), WOOD, WOOD_D, light=True)


def treehouse_5(s):  # a big green leaf
    leaf = union(ellipse(256, 240, 120, 190, angle=-30))
    s.shape(leaf, LEAF, LEAF_D, light=True)
    s.line(stroke([(140, 430), (330, 110)], 12), LEAF_D)
    for t in (0.35, 0.55, 0.75):
        x = 140 + (330 - 140) * t; y = 430 + (110 - 430) * t
        s.line(stroke([(x, y), (x - 70, y - 30)], 7), LEAF_D)
        s.line(stroke([(x, y), (x + 50, y + 40)], 7), LEAF_D)


def treehouse_6(s):  # a bird's nest with three eggs
    for x, c in ((196, SKY), (256, BLUE), (316, SKY)):
        s.shape(ellipse(x, 250, 46, 58), c, BLUE_D, light=True)
    nest = minus(ellipse(256, 320, 190, 110), rrect(40, 140, 470, 290, 0))
    s.shape(union(nest, rrect(70, 290, 442, 320, 15)), WOOD, WOOD_D, light=True)
    for k in range(7):
        s.line(arc(256, 260 + k * 6, 170 - k * 8, 30, 150, 7), WOOD_D)


def treehouse_7(s):  # a wooden ladder
    for x in (170, 342):
        s.shape(stroke([(x - 30, 456), (x + 20, 60)], 30), WOOD, WOOD_D, light=True)
    for k in range(5):
        y = 400 - k * 76
        t = (456 - y) / (456 - 60)
        s.shape(stroke([(140 + 50 * t, y), (312 + 50 * t, y)], 22), SAND, SAND_D)


def treehouse_8(s):  # a lantern, lit
    s.shape(arc(256, 120, 50, 180, 360, 12), DARK)
    s.shape(ellipse(256, 280, 150), (255, 243, 204), outline=False)
    s.shape(rrect(176, 130, 336, 166, 12), DARK, DARK_D)
    s.shape(rrect(186, 160, 326, 390, 30), YELLOW, YELLOW_D, light=True)
    s.line(stroke([(256, 166), (256, 388)], 8), DARK)
    s.line(stroke([(188, 276), (324, 276)], 8), DARK)
    s.shape(ellipse(256, 290, 22, 34), ORANGE, ORANGE_D)
    s.shape(rrect(176, 384, 336, 420, 12), DARK, DARK_D)


def treehouse_9(s):  # a pinecone
    s.shape(stroke([(256, 110), (256, 60)], 16), WOOD_D)
    s.shape(ellipse(256, 280, 120, 170), WOOD, WOOD_D, light=True)
    for row in range(6):
        y = 150 + row * 50
        n = 3 if row in (0, 5) else 4
        for k in range(n):
            x = 256 + (k - (n - 1) / 2) * 56
            s.shape(arc(x, y, 30, 20, 160, 9), WOOD_D, outline=False)


def treehouse_10(s):  # a kite with a bow tail
    kite = poly([(256, 50), (380, 190), (256, 330), (132, 190)])
    s.shape(kite, RED, RED_D, light=True)
    s.fill(inter(kite, poly([(256, 50), (380, 190), (256, 190)])), YELLOW)
    s.fill(inter(kite, poly([(256, 330), (132, 190), (256, 190)])), YELLOW)
    s.line(stroke([(256, 50), (256, 330)], 7)); s.line(stroke([(132, 190), (380, 190)], 7))
    s.line(stroke([(256, 330), (230, 380), (290, 420), (250, 470)], 6))
    for x, y, c in ((236, 384, BLUE), (284, 420, GREEN), (252, 462, PURPLE)):
        s.shape(union(poly([(x - 26, y - 14), (x, y), (x - 26, y + 14)]), poly([(x + 26, y - 14), (x, y), (x + 26, y + 14)])), c, lw=5)


def nightsky_1(s):  # a sleepy crescent moon
    moon = minus(ellipse(256, 256, 190), ellipse(330, 210, 160))
    s.shape(moon, YELLOW, YELLOW_D, light=True)
    s.line(arc(150, 250, 20, 20, 160, 8))
    s.fill(ellipse(146, 290, 16, 10), PINK)
    s.line(arc(196, 330, 24, 40, 140, 8))
    s.shape(star(400, 380, 32), YELLOW, outline=True, lw=5)


def nightsky_2(s):  # a comet with its tail
    for i, c in enumerate((PURPLE, BLUE, SKY)):
        s.shape(stroke([(120 + i * 20, 400 - i * 40), (300, 210)], 60 - i * 16), c, outline=(i == 0), lw=LINE)
    s.shape(star(330, 180, 110, 0.55), YELLOW, YELLOW_D, light=True)
    s.eye(310, 170, 12); s.eye(352, 172, 12)
    s.line(arc(330, 196, 18, 30, 150, 6))


def nightsky_3(s):  # a night owl, one eye winking
    s.shape(union(ellipse(256, 280, 140, 160), poly([(140, 150), (160, 80), (210, 135)]), poly([(372, 150), (352, 80), (302, 135)])), PURPLE, PURPLE_D, light=True)
    s.shape(ellipse(256, 330, 90, 96), (210, 196, 236), outline=False)
    s.shape(ellipse(200, 226, 48), CREAM, CREAM_D); s.eye(200, 230, 24)
    s.shape(ellipse(312, 226, 48), CREAM, CREAM_D); s.line(arc(312, 218, 24, 20, 160, 9))
    s.shape(poly([(240, 266), (272, 266), (256, 300)]), ORANGE, ORANGE_D)
    s.shape(star(420, 90, 28), YELLOW, outline=True, lw=5)


def nightsky_4(s):  # a firefly, glowing
    s.shape(ellipse(256, 330, 150), (255, 246, 196), outline=False)
    s.shape(ellipse(170, 200, 80, 46, angle=30), SKY, SKY_D, light=True)
    s.shape(ellipse(342, 200, 80, 46, angle=-30), SKY, SKY_D, light=True)
    s.shape(ellipse(256, 330, 64, 80), YELLOW, YELLOW_D, light=True)
    s.shape(ellipse(256, 230, 56, 50), DARK, DARK_D)
    s.fill(ellipse(236, 224, 12), (255, 255, 255)); s.fill(ellipse(276, 224, 12), (255, 255, 255))
    s.fill(ellipse(238, 226, 6), EYE); s.fill(ellipse(278, 226, 6), EYE)
    s.line(arc(226, 160, 34, 200, 320, 7)); s.line(arc(286, 160, 34, 220, 340, 7))


def nightsky_5(s):  # a telescope on its stand
    for a, b in (((256, 300), (150, 450)), ((256, 300), (362, 450)), ((256, 300), (256, 456))):
        s.shape(stroke([a, b], 18), WOOD, WOOD_D)
    tube = rrect(120, 170, 420, 240, 30).rotate(28, resample=Image.BICUBIC, center=(270 * S, 205 * S))
    s.shape(tube, BLUE, BLUE_D, light=True)
    s.shape(ellipse(392, 128, 34, 50, angle=28), GOLD, GOLD_D)
    s.shape(ellipse(256, 282, 26), GOLD_D)
    s.shape(star(110, 100, 30), YELLOW, outline=True, lw=5)


def nightsky_6(s):  # a ringed planet
    s.shape(ellipse(256, 256, 130), PINK, PINK_D, light=True)
    s.line(arc(256, 230, 100, 200, 340, 12), (240, 150, 170))
    s.line(arc(256, 280, 110, 20, 160, 12), (240, 150, 170))
    ring = minus(ellipse(256, 270, 220, 60, angle=-12), ellipse(256, 262, 170, 36, angle=-12))
    s.shape(inter(ring, rrect(0, 262, 512, 512, 0)), YELLOW, YELLOW_D)
    s.shape(star(420, 96, 26), YELLOW, outline=True, lw=5)


def nightsky_7(s):  # a shooting star
    for i, c in enumerate((PINK, YELLOW, SKY)):
        s.shape(stroke([(80, 420 - i * 46), (300, 220 - i * 30)], 40), c, outline=True, lw=6)
    s.shape(star(330, 190, 140, 0.5), GOLD, GOLD_D, light=True)
    s.eye(304, 180, 14); s.eye(356, 182, 14)
    s.fill(ellipse(286, 214, 14, 9), PINK); s.fill(ellipse(376, 216, 14, 9), PINK)
    s.line(arc(330, 206, 22, 30, 150, 7))


def nightsky_8(s):  # a sleepy cloud
    s.shape(union(ellipse(170, 270, 90), ellipse(270, 220, 110), ellipse(360, 276, 86), rrect(90, 270, 440, 350, 40)), WHITE, WHITE_D, light=True)
    s.line(arc(216, 276, 22, 20, 160, 8)); s.line(arc(306, 276, 22, 20, 160, 8))
    s.fill(ellipse(196, 316, 16, 9), PINK); s.fill(ellipse(328, 316, 16, 9), PINK)
    s.shape(star(420, 130, 26), YELLOW, outline=True, lw=5)


def nightsky_9(s):  # a paper lantern floating up
    s.shape(ellipse(256, 240, 160), (255, 236, 204), outline=False)
    body = union(ellipse(256, 230, 120, 140))
    s.shape(body, ORANGE, ORANGE_D, light=True)
    for x in (196, 256, 316):
        s.line(inter(arc(x, 230, 140, 250, 290, 6), body), ORANGE_D)
    s.shape(rrect(196, 80, 316, 110, 10), RED, RED_D)
    s.shape(rrect(196, 352, 316, 380, 10), RED, RED_D)
    s.line(stroke([(256, 380), (256, 450)], 8), RED_D)
    s.shape(ellipse(256, 456, 14), YELLOW, YELLOW_D, lw=5)


def nightsky_10(s):  # a sleeping cat curled up
    s.shape(union(ellipse(256, 320, 170, 110), ellipse(370, 330, 70, 60)), GREY, GREY_D, light=True)
    s.shape(stroke([(110, 360), (150, 410), (300, 430), (390, 400)], 40), GREY, GREY_D)
    s.shape(union(ellipse(170, 280, 84, 74), poly([(110, 240), (112, 180), (160, 220)]), poly([(220, 230), (236, 172), (250, 236)])), GREY, GREY_D, light=True)
    s.line(arc(146, 284, 16, 20, 160, 7)); s.line(arc(196, 284, 16, 20, 160, 7))
    s.fill(ellipse(170, 306, 8, 6), PINK)
    for k, (x, y) in enumerate(((330, 180), (380, 140), (420, 100))):
        z = 12 + k * 4
        s.line(stroke([(x - z, y - z), (x + z, y - z), (x - z, y + z), (x + z, y + z)], 7), PURPLE_D)


def fair_1(s):  # a red balloon
    s.line(stroke([(256, 330), (236, 380), (276, 420), (250, 470)], 6))
    s.shape(union(ellipse(256, 200, 130, 150), poly([(236, 340), (276, 340), (256, 320)])), RED, RED_D, light=True)
    s.shape(poly([(240, 352), (272, 352), (256, 336)]), RED_D)


def fair_2(s):  # a fair ticket
    t = rrect(80, 160, 432, 352, 20)
    t = minus(t, ellipse(80, 256, 30)); t = minus(t, ellipse(432, 256, 30))
    t = t.rotate(10, resample=Image.BICUBIC, center=(N / 2, N / 2))
    s.shape(t, PINK, PINK_D, light=True)
    s.shape(star(220, 252, 52).rotate(10, resample=Image.BICUBIC, center=(N / 2, N / 2)), YELLOW, YELLOW_D, lw=6)
    dash = union(*[rrect(320, y, 330, y + 16, 4) for y in range(170, 350, 30)]).rotate(10, resample=Image.BICUBIC, center=(N / 2, N / 2))
    s.line(inter(dash, t), PINK_D)


def fair_3(s):  # a box of popcorn
    s.shape(union(*[ellipse(x, y, 40) for x, y in ((180, 170), (236, 140), (296, 150), (346, 180), (210, 200), (290, 200))]), CREAM, CREAM_D, light=True)
    box = poly([(150, 210), (362, 210), (330, 450), (182, 450)])
    s.shape(box, WHITE, WHITE_D)
    for k in range(3):
        x0 = 150 + 70 * k + 12; x1 = x0 + 36
        s.fill(inter(box, poly([(x0, 210), (x1, 210), (x1 - 10, 450), (x0 - 4, 450)])), RED)
    s.line(stroke([(150, 210), (362, 210)], 6))


def fair_4(s):  # a carousel horse on its pole
    s.shape(stroke([(256, 40), (256, 470)], 18), GOLD, GOLD_D)
    s.shape(union(ellipse(250, 270, 120, 70), ellipse(360, 190, 46, 60, angle=30), stroke([(330, 240), (360, 180)], 50)), WHITE, WHITE_D, light=True)
    for x, a in ((170, 20), (210, -10), (300, 10), (330, -20)):
        s.shape(stroke([(x, 320), (x + a, 400)], 22), WHITE, WHITE_D)
    s.shape(union(*[ellipse(320 + k * 6, 150 + k * 20, 16, 30) for k in range(4)]), PINK, PINK_D, lw=5)
    s.shape(rrect(200, 220, 280, 260, 12), RED, RED_D)
    s.eye(374, 180, 10)
    s.shape(stroke([(130, 270), (90, 330)], 22), PINK, PINK_D)


def fair_5(s):  # candy floss on a stick
    s.shape(stroke([(256, 300), (256, 470)], 16), SAND, SAND_D)
    s.shape(union(ellipse(256, 180, 120, 110), ellipse(180, 220, 70), ellipse(332, 220, 70), ellipse(256, 110, 70)), PINK, PINK_D, light=True)
    for x, y in ((210, 160), (290, 140), (250, 230), (330, 240)):
        s.line(arc(x, y, 24, 200, 340, 6), (255, 210, 222))


def fair_6(s):  # a ferris wheel, lit
    s.shape(union(stroke([(256, 256), (150, 460)], 22), stroke([(256, 256), (362, 460)], 22)), SILVER, SILVER_D)
    s.shape(arc(256, 230, 170, 0, 360, 22), PURPLE, PURPLE_D)
    for k in range(8):
        a = k * math.pi / 4
        s.line(stroke([(256, 230), (256 + 168 * math.cos(a), 230 + 168 * math.sin(a))], 8), PURPLE_D)
    for k, c in enumerate((RED, YELLOW, BLUE, GREEN, PINK, ORANGE, TEAL, RED)):
        a = k * math.pi / 4
        x, y = 256 + 170 * math.cos(a), 230 + 170 * math.sin(a)
        s.shape(rrect(x - 26, y, x + 26, y + 40, 10), c, lw=5)
    s.shape(ellipse(256, 230, 30), GOLD, GOLD_D)


def fair_7(s):  # a prize ribbon
    for side in (-1, 1):
        s.shape(poly([(256 + side * 20, 260), (256 + side * 110, 460), (256 + side * 60, 430), (256 + side * 40, 470), (256 - side * 20, 270)]), BLUE, BLUE_D)
    s.shape(ellipse(256, 200, 130), BLUE, BLUE_D, light=True)
    s.shape(ellipse(256, 200, 90), WHITE, WHITE_D)
    s.shape(star(256, 200, 62), GOLD, GOLD_D, lw=5)


def fair_8(s):  # a teddy bear
    for x in (170, 342):
        s.shape(ellipse(x, 120, 44), WOOD, WOOD_D); s.shape(ellipse(x, 120, 22), SAND, outline=False)
    s.shape(ellipse(256, 350, 120, 110), WOOD, WOOD_D, light=True)
    s.shape(ellipse(256, 370, 70, 64), SAND, outline=False)
    for x in (140, 372):
        s.shape(ellipse(x, 340, 40, 56, angle=(20 if x < 256 else -20)), WOOD, WOOD_D)
    s.shape(ellipse(256, 190, 110, 96), WOOD, WOOD_D, light=True)
    s.shape(ellipse(256, 226, 46, 36), SAND, outline=False)
    s.fill(ellipse(256, 210, 16, 12), DARK)
    s.eye(214, 180, 12); s.eye(298, 180, 12)
    s.shape(union(poly([(200, 270), (256, 290), (200, 310)]), poly([(312, 270), (256, 290), (312, 310)])), RED, RED_D, lw=5)


def fair_9(s):  # juggling balls in the air
    s.line(arc(256, 330, 160, 200, 340, 7), DARK)
    for x, y, c in ((120, 300, RED), (256, 150, YELLOW), (392, 300, BLUE)):
        s.shape(ellipse(x, y, 62), c, None, light=True)
        s.line(arc(x, y, 50, 200, 300, 9), (255, 255, 255))


def fair_10(s):  # a rosette for the winner
    for side in (-1, 1):
        s.shape(poly([(256 + side * 30, 270), (256 + side * 100, 470), (256 + side * 60, 440), (256 + side * 30, 470), (256 - side * 10, 280)]), RED, RED_D)
    pleats = union(*[ellipse(256 + 120 * math.cos(k * math.pi / 8), 210 + 120 * math.sin(k * math.pi / 8), 36) for k in range(16)])
    s.shape(union(pleats, ellipse(256, 210, 120)), RED, RED_D, light=True)
    s.shape(ellipse(256, 210, 84), GOLD, GOLD_D)
    s.shape(star(256, 210, 56), YELLOW, YELLOW_D, lw=5)


# ---- 2.5: the stones' own stickers, one per lesson (engine/world/LevelStickers.kt, art/GEMINI-BRIEF.md C2) ----------

def face(s, cx, cy, r, blush=True, smile=True):
    """Two eyes, pink cheeks and a smile for a creature or a thing with a face."""
    s.eye(cx - r * 0.38, cy - r * 0.1, r * 0.13); s.eye(cx + r * 0.38, cy - r * 0.1, r * 0.13)
    if blush:
        s.fill(ellipse(cx - r * 0.62, cy + r * 0.2, r * 0.16, r * 0.1), PINK); s.fill(ellipse(cx + r * 0.62, cy + r * 0.2, r * 0.16, r * 0.1), PINK)
    if smile:
        s.line(arc(cx, cy + r * 0.05, r * 0.3, 25, 155, max(6, r * 0.07)))


def note(s, x, y, c=DARK, r=30, flag=True):
    """A music note: a head, a stem and a flag."""
    s.shape(ellipse(x, y, r, r * 0.78, angle=-20), c, None)
    s.shape(stroke([(x + r * 0.85, y - 4), (x + r * 0.85, y - r * 3.4)], 12), c)
    if flag:
        s.shape(poly([(x + r * 0.85, y - r * 3.4), (x + r * 2.2, y - r * 2.6), (x + r * 2.0, y - r * 2.1), (x + r * 0.85, y - r * 2.7)]), c)


def medal(s, ribbon, ribbon_d, inner):
    """A concert medal on a ribbon, the page's colour on the ribbon and in the middle."""
    s.shape(poly([(150, 40), (230, 40), (290, 250), (210, 250)]), ribbon, ribbon_d)
    s.shape(poly([(362, 40), (282, 40), (222, 250), (302, 250)]), ribbon, ribbon_d)
    s.shape(ellipse(256, 330, 140), GOLD, GOLD_D, light=True)
    s.shape(ellipse(256, 330, 100), YELLOW, YELLOW_D)
    s.shape(star(256, 330, 70), inner, None, lw=6)


def lv_playground_1(s):  # a see-saw, one end up and one down
    s.shape(poly([(256, 300), (200, 420), (312, 420)]), RED, RED_D, light=True)
    s.shape(stroke([(80, 380), (432, 220)], 34), WOOD, WOOD_D)
    s.shape(rrect(70, 330, 120, 380, 10), BLUE, BLUE_D)
    s.shape(rrect(392, 170, 442, 220, 10), BLUE, BLUE_D)
    s.shape(ellipse(256, 300, 22), DARK)
    for x, y in ((420, 120), (90, 450)):
        s.shape(star(x, y, 30), YELLOW, YELLOW_D, lw=5)


def lv_playground_2(s):  # piano keys: two black, then three black
    s.shape(rrect(60, 130, 452, 400, 30), WHITE, WHITE_D, light=True)
    for x in (116, 172, 228, 284, 340, 396):
        s.line(stroke([(x, 150), (x, 390)], 6), GREY)
    for x in (100, 156, 268, 324, 380):
        s.shape(rrect(x - 4, 130, x + 36, 290, 8), DARK, lw=5)


def lv_playground_3(s):  # a red cat, C's animal
    for side in (-1, 1):
        s.shape(poly([(256 + side * 60, 170), (256 + side * 140, 70), (256 + side * 150, 200)]), RED, RED_D)
    s.shape(ellipse(256, 270, 160, 140), RED, RED_D, light=True)
    s.shape(ellipse(256, 320, 70, 50), (250, 200, 196), outline=False)
    face(s, 256, 260, 150)
    s.fill(poly([(240, 300), (272, 300), (256, 318)]), PINK_D)
    for side in (-1, 1):
        for k in (-1, 1):
            s.line(stroke([(256 + side * 90, 320 + k * 10), (256 + side * 190, 300 + k * 30)], 6))


def lv_playground_4(s):  # three paw prints walking up the steps
    s.shape(poly([(60, 440), (60, 360), (200, 360), (200, 280), (330, 280), (330, 200), (452, 200), (452, 440)]), SAND, SAND_D, light=True)
    for x, y in ((130, 320), (264, 240), (392, 160)):
        s.shape(ellipse(x, y + 10, 34, 28), WOOD_D, lw=5)
        for dx, dy in ((-30, -26), (-10, -42), (12, -42), (32, -26)):
            s.shape(ellipse(x + dx, y + dy, 11), WOOD_D, lw=4)


def lv_playground_5(s):  # a hot cross bun
    s.shape(ellipse(256, 290, 190, 150), ORANGE, ORANGE_D, light=True)
    s.shape(ellipse(256, 270, 150, 100), (240, 176, 100), outline=False)
    s.shape(union(stroke([(150, 230), (362, 320)], 26), stroke([(362, 230), (150, 320)], 26)), CREAM, CREAM_D, lw=5)
    s.shape(rrect(70, 400, 442, 440, 18), WHITE, WHITE_D)


def lv_playground_6(s):  # a parrot that copies
    s.shape(poly([(250, 380), (180, 470), (240, 460), (290, 400)]), BLUE, BLUE_D)
    s.shape(ellipse(256, 300, 110, 140), GREEN, GREEN_D, light=True)
    s.shape(ellipse(230, 320, 60, 100, angle=10), LEAF_D)
    s.shape(ellipse(270, 170, 90), RED, RED_D, light=True)
    s.shape(poly([(330, 160), (400, 190), (340, 220)]), YELLOW, YELLOW_D)
    s.eye(290, 150, 14)
    s.shape(stroke([(160, 440), (360, 440)], 18), WOOD, WOOD_D)
    note(s, 410, 330, PURPLE, 18)


def lv_playground_7(s):  # two crossed drumsticks
    for a, b in (((110, 420), (400, 110)), ((402, 420), (112, 110))):
        s.shape(stroke([a, b], 30), WOOD, WOOD_D)
        s.shape(ellipse(b[0], b[1], 40), CREAM, CREAM_D, light=True)
    s.shape(star(256, 380, 40), YELLOW, YELLOW_D, lw=5)


def lv_playground_8(s):  # a fluffy little lamb
    for x in (190, 230, 290, 330):
        s.shape(rrect(x - 14, 340, x + 14, 440, 10), DARK)
    fluff = union(*[ellipse(256 + 120 * math.cos(k * math.pi / 5), 290 + 80 * math.sin(k * math.pi / 5), 54) for k in range(10)], ellipse(256, 290, 120, 90))
    s.shape(fluff, WHITE, WHITE_D, light=True)
    s.shape(ellipse(150, 230, 64, 56), DARK, DARK_D)
    s.shape(ellipse(110, 200, 30, 16, angle=-30), DARK)
    s.fill(ellipse(140, 220, 10), (255, 255, 255)); s.fill(ellipse(142, 222, 5), EYE)
    s.shape(union(*[ellipse(170 + k * 26, 180, 22) for k in range(3)]), WHITE, WHITE_D, lw=5)


def lv_playground_9(s):  # a cosy mitten for the left paw
    s.shape(union(rrect(140, 150, 360, 400, 100), ellipse(380, 240, 50, 74, angle=-25)), RED, RED_D, light=True)
    s.shape(rrect(130, 360, 370, 450, 24), WHITE, WHITE_D)
    for x in (170, 220, 270, 320):
        s.line(stroke([(x, 370), (x, 440)], 7), WHITE_D)
    s.shape(star(240, 260, 44), WHITE, None, lw=5)


def lv_playground_10(s):  # a goat, G's animal
    for side in (-1, 1):
        s.shape(arc(256 + side * 70, 150, 60, 180 if side < 0 else 0, 300 if side < 0 else -120, 26), SAND_D)
    s.shape(ellipse(256, 270, 120, 140), TEAL, TEAL_D, light=True)
    for side in (-1, 1):
        s.shape(ellipse(256 + side * 130, 230, 54, 22, angle=side * 20), TEAL_D)
    s.shape(ellipse(256, 340, 70, 56), (176, 226, 220), outline=False)
    face(s, 256, 250, 120)
    s.shape(poly([(236, 400), (276, 400), (256, 470)]), WHITE, WHITE_D, lw=5)


def lv_playground_11(s):  # a soft feather
    s.shape(ellipse(256, 240, 90, 200, angle=30), SKY, SKY_D, light=True)
    s.line(stroke([(140, 450), (360, 70)], 10), BLUE_D)
    for k in range(6):
        y = 140 + k * 45
        s.line(stroke([(256 + (240 - y) * 0.55, y), (256 + (240 - y) * 0.55 - 70, y - 40)], 5), WHITE)
    s.shape(drop(380, 380, 22), PINK, outline=True, lw=5)


def lv_playground_12(s):  # the garden's concert medal
    medal(s, GREEN, GREEN_D, RED)


def lv_pond_1(s):  # a twinkling star with a face
    s.shape(star(256, 270, 200, inner=0.52), YELLOW, YELLOW_D, light=True)
    face(s, 256, 280, 150)
    for x, y in ((80, 90), (440, 110), (430, 440)):
        s.shape(star(x, y, 26, inner=0.4, points=4), WHITE, None, lw=4)


def lv_pond_2(s):  # a rowing boat with oars
    s.shape(union(*[ellipse(x, 440, 50, 24) for x in range(70, 460, 70)]), SKY, SKY_D)
    s.shape(stroke([(110, 260), (300, 420)], 16), WOOD_D)
    s.shape(stroke([(402, 260), (212, 420)], 16), WOOD_D)
    s.shape(poly([(70, 320), (442, 320), (390, 420), (122, 420)]), RED, RED_D, light=True)
    s.shape(rrect(70, 300, 442, 330, 12), WOOD, WOOD_D)
    s.shape(ellipse(110, 260, 30, 16, angle=40), WOOD); s.shape(ellipse(402, 260, 30, 16, angle=-40), WOOD)


def lv_pond_3(s):  # a frog singing
    s.shape(ellipse(230, 420, 180, 50), LEAF, LEAF_D)
    s.shape(ellipse(230, 320, 140, 110), GREEN, GREEN_D, light=True)
    for x in (170, 290):
        s.shape(ellipse(x, 220, 44), GREEN, GREEN_D)
        s.fill(ellipse(x, 220, 28), (255, 255, 255)); s.eye(x + 4, 222, 15)
    s.shape(ellipse(230, 340, 46, 34), (180, 60, 70))
    s.shape(ellipse(230, 350, 26, 16), PINK, outline=False)
    note(s, 380, 230, BLUE, 22)
    note(s, 430, 130, PURPLE, 16, flag=False)


def lv_pond_4(s):  # a drum on a lily pad
    s.shape(minus(ellipse(256, 400, 200, 70), poly([(256, 400), (430, 350), (456, 400)])), LEAF, LEAF_D)
    s.shape(rrect(140, 220, 372, 380, 30), RED, RED_D, light=True)
    s.shape(ellipse(256, 220, 116, 40), CREAM, CREAM_D)
    for x in (170, 230, 290, 350):
        s.line(stroke([(x, 250), (x + 30, 370)], 6), YELLOW)
    s.shape(stroke([(330, 160), (430, 70)], 16), WOOD, WOOD_D)
    s.shape(ellipse(430, 70, 22), CREAM, CREAM_D)


def lv_pond_5(s):  # music notes, a ta and a ti-ti
    note(s, 140, 380, DARK, 40, flag=False)
    s.shape(ellipse(290, 380, 40, 31, angle=-20), BLUE)
    s.shape(ellipse(410, 350, 40, 31, angle=-20), BLUE)
    s.shape(stroke([(324, 376), (324, 150)], 14), BLUE)
    s.shape(stroke([(444, 346), (444, 120)], 14), BLUE)
    s.shape(poly([(318, 150), (450, 120), (450, 160), (318, 190)]), BLUE)


def lv_pond_6(s):  # a farm cow
    for side in (-1, 1):
        s.shape(ellipse(256 + side * 150, 170, 50, 24, angle=side * -20), WHITE, WHITE_D)
        s.shape(poly([(256 + side * 70, 140), (256 + side * 110, 70), (256 + side * 120, 150)]), CREAM, CREAM_D)
    s.shape(ellipse(256, 240, 140, 130), WHITE, WHITE_D, light=True)
    s.shape(ellipse(190, 190, 50, 40), DARK, outline=False)
    s.shape(ellipse(256, 340, 110, 70), PINK, PINK_D)
    s.fill(ellipse(220, 340, 14, 18), PINK_D); s.fill(ellipse(292, 340, 14, 18), PINK_D)
    s.eye(210, 230, 15); s.eye(302, 230, 15)


def lv_pond_7(s):  # stepping stones over the water
    s.shape(ellipse(256, 300, 220, 150), SKY, SKY_D)
    for x, y, r in ((130, 360, 60), (256, 280, 56), (380, 210, 52)):
        s.shape(ellipse(x, y, r, r * 0.6), GREY, GREY_D, light=True)
    s.line(arc(200, 330, 40, 200, 340, 6), WHITE)


def lv_pond_8(s):  # a high five: two paws together
    for x, c, cd in ((180, SAND, SAND_D), (332, GOLD, GOLD_D)):
        s.shape(ellipse(x, 320, 80, 90), c, cd, light=True)
        for dx, dy in ((-60, -80), (-22, -110), (22, -110), (60, -80)):
            s.shape(ellipse(x + dx, 320 + dy, 26), c, cd, lw=6)
        s.shape(ellipse(x, 330, 40, 34), PINK, PINK_D, lw=5)
    s.shape(star(256, 120, 40, points=4, inner=0.35), YELLOW, None, lw=5)


def lv_pond_9(s):  # a triple scoop: three notes at once
    s.shape(poly([(180, 330), (332, 330), (256, 480)]), ORANGE, ORANGE_D, light=True)
    for k in range(5):
        s.line(stroke([(196 + k * 30, 340), (240 + k * 12, 440)], 4), ORANGE_D)
    for y, c, cd in ((290, RED, RED_D), (200, YELLOW, YELLOW_D), (110, GREEN, GREEN_D)):
        s.shape(ellipse(256, y, 92, 70), c, cd, light=True)


def lv_pond_10(s):  # London Bridge
    s.shape(union(*[ellipse(x, 450, 50, 22) for x in range(70, 460, 70)]), SKY, SKY_D)
    for x in (130, 382):
        s.shape(rrect(x - 50, 120, x + 50, 440, 8), GREY, GREY_D, light=True)
        s.shape(poly([(x - 60, 130), (x, 50), (x + 60, 130)]), BLUE, BLUE_D)
        s.shape(rrect(x - 18, 190, x + 18, 250, 14), DARK)
    s.shape(rrect(70, 300, 442, 340, 8), WOOD, WOOD_D)
    s.line(arc(256, 330, 140, 200, 340, 10), BLUE_D)


def lv_pond_11(s):  # a wishing wand
    s.shape(stroke([(120, 440), (300, 220)], 22), PURPLE, PURPLE_D)
    s.shape(star(330, 180, 120), YELLOW, YELLOW_D, light=True)
    for x, y in ((150, 120), (440, 330), (210, 300)):
        s.shape(star(x, y, 24, inner=0.4, points=4), PINK, None, lw=4)


def lv_pond_12(s):  # the pond's concert medal
    medal(s, BLUE, BLUE_D, TEAL)


def lv_hall_1(s):  # three letter blocks (no letters: colours for C, D, E)
    for x, y, c, cd in ((150, 330, RED, RED_D), (360, 330, ORANGE, ORANGE_D), (256, 160, YELLOW, YELLOW_D)):
        s.shape(rrect(x - 90, y - 90, x + 90, y + 90, 16), c, cd, light=True)
        s.shape(rrect(x - 56, y - 56, x + 56, y + 56, 10), WHITE, WHITE_D, lw=5)
        s.shape(ellipse(x, y, 24), c, None, lw=4)


def lv_hall_2(s):  # a happy note
    s.shape(ellipse(210, 360, 110, 86, angle=-20), PURPLE, PURPLE_D, light=True)
    s.shape(stroke([(305, 340), (305, 80)], 30), PURPLE)
    s.shape(poly([(300, 80), (420, 150), (400, 200), (300, 140)]), PURPLE, PURPLE_D)
    face(s, 210, 360, 100)


def lv_hall_3(s):  # a little sailboat
    s.shape(union(*[ellipse(x, 440, 50, 22) for x in range(70, 460, 70)]), SKY, SKY_D)
    s.shape(poly([(100, 360), (412, 360), (360, 430), (152, 430)]), WOOD, WOOD_D, light=True)
    s.shape(stroke([(256, 360), (256, 80)], 12), WOOD_D)
    s.shape(poly([(262, 90), (262, 340), (420, 340)]), WHITE, WHITE_D, light=True)
    s.shape(poly([(250, 130), (250, 340), (130, 340)]), RED, RED_D)


def lv_hall_4(s):  # a cap with a feather in it
    s.shape(union(ellipse(256, 300, 160, 110), rrect(80, 300, 432, 360, 26)), BLUE, BLUE_D, light=True)
    s.shape(ellipse(256, 360, 190, 40), BLUE_D)
    s.shape(ellipse(330, 180, 50, 150, angle=30), RED, RED_D, light=True)
    s.line(stroke([(270, 300), (400, 60)], 8), RED_D)


def lv_hall_5(s):  # a hand print
    s.shape(union(ellipse(256, 320, 110, 120), *[ellipse(256 + dx, 320 + dy, 30, 70, angle=a)
            for dx, dy, a in ((-120, -60, 30), (-70, -170, 8), (0, -190, 0), (70, -170, -8), (130, -110, -25))]), ORANGE, ORANGE_D, light=True)
    s.fill(ellipse(256, 340, 50, 46), (250, 196, 140))


def lv_hall_6(s):  # a little mouse, quiet
    s.line(arc(380, 400, 70, 200, 360, 10), PINK_D)
    s.shape(ellipse(256, 330, 140, 100), GREY, GREY_D, light=True)
    for x in (180, 330):
        s.shape(ellipse(x, 200, 64), GREY, GREY_D); s.shape(ellipse(x, 200, 40), PINK, outline=False)
    s.shape(ellipse(256, 270, 100, 84), GREY, GREY_D, light=True)
    face(s, 256, 270, 90)
    s.fill(ellipse(256, 300, 12, 9), PINK_D)


def lv_hall_7(s):  # a trumpet
    s.shape(union(stroke([(110, 280), (340, 280)], 40), poly([(320, 280), (450, 170), (450, 390)])), GOLD, GOLD_D, light=True)
    s.shape(ellipse(450, 280, 26, 112), YELLOW, YELLOW_D)
    for x in (190, 230, 270):
        s.shape(rrect(x - 12, 210, x + 12, 270, 8), GOLD_D, lw=5)
    s.shape(rrect(80, 260, 116, 300, 10), GOLD_D)
    note(s, 140, 160, BLUE, 18)


def lv_hall_8(s):  # a soft pillow for the rest
    s.shape(rrect(80, 170, 432, 380, 90), PURPLE, PURPLE_D, light=True)
    for x, y in ((90, 180), (422, 180), (90, 370), (422, 370)):
        s.shape(ellipse(x, y, 30), PURPLE, PURPLE_D, lw=5)
    s.line(arc(256, 275, 50, 20, 160, 8), PURPLE_D)
    for x, y, r in ((380, 110, 26), (430, 70, 18)):
        s.line(stroke([(x - r, y - r), (x + r, y - r), (x - r, y + r), (x + r, y + r)], 8), BLUE_D)


def lv_hall_9(s):  # a xylophone
    cols = [(RED, RED_D), (ORANGE, ORANGE_D), (YELLOW, YELLOW_D), (GREEN, GREEN_D), (BLUE, BLUE_D)]
    for i, (c, cd) in enumerate(cols):
        x = 110 + i * 72
        h = 130 - i * 14
        s.shape(rrect(x - 26, 260 - h, x + 26, 260 + h, 12), c, cd, light=True)
    s.shape(stroke([(70, 330), (442, 300)], 12), WOOD_D)
    s.shape(stroke([(330, 440), (430, 360)], 12), WOOD)
    s.shape(ellipse(430, 360, 20), RED, RED_D, lw=5)


def lv_hall_10(s):  # two hearts together
    for cx, c, cd in ((200, RED, RED_D), (320, PINK, PINK_D)):
        s.shape(union(ellipse(cx - 50, 220, 64), ellipse(cx + 50, 220, 64), poly([(cx - 112, 240), (cx + 112, 240), (cx, 380)])), c, cd, light=True)


def lv_hall_11(s):  # a song book
    s.shape(poly([(256, 150), (440, 120), (440, 400), (256, 430)]), WHITE, WHITE_D, light=True)
    s.shape(poly([(256, 150), (72, 120), (72, 400), (256, 430)]), CREAM, CREAM_D, light=True)
    for y in (190, 240, 290, 340):
        s.line(stroke([(100, y - 10), (230, y + 4)], 4), GREY)
        s.line(stroke([(282, y + 4), (412, y - 10)], 4), GREY)
    s.fill(ellipse(160, 240, 14, 10), DARK); s.fill(ellipse(340, 280, 14, 10), DARK)
    s.shape(rrect(240, 100, 272, 440, 8), RED, RED_D)


def lv_hall_12(s):  # a rose for the recital
    s.shape(stroke([(256, 460), (256, 270)], 18), LEAF, LEAF_D)
    s.shape(ellipse(200, 380, 50, 22, angle=-30), LEAF, LEAF_D); s.shape(ellipse(310, 340, 50, 22, angle=30), LEAF, LEAF_D)
    s.shape(ellipse(256, 200, 110, 100), RED, RED_D, light=True)
    s.line(arc(256, 200, 60, 0, 300, 10), RED_D)
    s.line(arc(256, 200, 30, 100, 400, 8), RED_D)


def lv_workshop_1(s):  # a piano bench
    s.shape(rrect(80, 200, 432, 270, 20), DARK, DARK_D, light=True)
    s.shape(rrect(70, 180, 442, 220, 18), RED, RED_D, light=True)
    for x in (110, 400):
        s.shape(rrect(x - 16, 260, x + 16, 440, 8), DARK)
    s.line(stroke([(110, 380), (400, 380)], 10), DARK)


def lv_workshop_2(s):  # soap bubbles
    for x, y, r in ((200, 280, 130), (370, 170, 80), (380, 380, 60), (110, 120, 44)):
        s.shape(ellipse(x, y, r), SKY, SKY_D)
        s.fill(ellipse(x, y, r * 0.86), (232, 244, 252))
        s.line(arc(x, y, r * 0.65, 200, 260, max(6, r * 0.1)), WHITE)


def lv_workshop_3(s):  # a finger puppet
    s.shape(rrect(170, 160, 342, 450, 80), PURPLE, PURPLE_D, light=True)
    s.shape(ellipse(256, 210, 100), YELLOW, YELLOW_D, light=True)
    face(s, 256, 220, 90)
    s.shape(union(*[ellipse(256 + dx, 110 + dy, 30) for dx, dy in ((-60, 10), (-20, -10), (20, -10), (60, 10))]), ORANGE, ORANGE_D)
    for y in (330, 390):
        s.line(stroke([(180, y), (332, y)], 8), PURPLE_D)


def lv_workshop_4(s):  # a little house
    s.shape(rrect(120, 220, 392, 440, 10), CREAM, CREAM_D, light=True)
    s.shape(poly([(90, 240), (256, 90), (422, 240)]), RED, RED_D)
    s.shape(rrect(220, 320, 292, 440, 10), WOOD, WOOD_D)
    s.shape(rrect(310, 260, 370, 320, 6), SKY, SKY_D)
    s.shape(rrect(140, 260, 200, 320, 6), SKY, SKY_D)
    s.shape(ellipse(256, 180, 26), YELLOW, YELLOW_D, lw=5)


def lv_workshop_5(s):  # a mailbox with a letter
    s.shape(rrect(240, 300, 272, 460, 8), WOOD, WOOD_D)
    s.shape(union(rrect(110, 160, 400, 320, 20), ellipse(255, 170, 145, 60)), BLUE, BLUE_D, light=True)
    s.shape(rrect(360, 100, 380, 200, 6), RED, RED_D, lw=5)
    s.shape(rrect(370, 100, 440, 140, 6), RED, RED_D, lw=5)
    s.shape(rrect(150, 210, 290, 300, 8), WHITE, WHITE_D)
    s.line(stroke([(150, 210), (220, 260), (290, 210)], 6), WHITE_D)


def lv_workshop_6(s):  # a thumbs up
    s.shape(union(rrect(150, 230, 380, 440, 50), ellipse(230, 160, 50, 110, angle=-10)), GOLD, GOLD_D, light=True)
    for y in (280, 330, 380):
        s.line(stroke([(300, y), (380, y)], 6), GOLD_D)
    s.shape(rrect(100, 250, 160, 440, 20), BLUE, BLUE_D)


def lv_workshop_7(s):  # a toy car on a bridge
    s.shape(arc(256, 470, 230, 200, 340, 34), WOOD, WOOD_D)
    s.shape(union(rrect(90, 210, 422, 320, 40), rrect(160, 140, 350, 230, 40)), RED, RED_D, light=True)
    s.shape(rrect(190, 160, 320, 215, 18), SKY, SKY_D, lw=5)
    for x in (160, 352):
        s.shape(ellipse(x, 320, 46), DARK); s.shape(ellipse(x, 320, 18), GREY, outline=False)


def lv_workshop_8(s):  # an elephant, heavy and gentle
    s.shape(ellipse(256, 300, 170, 140), GREY, GREY_D, light=True)
    for x in (140, 372):
        s.shape(ellipse(x, 250, 80, 100), GREY_D)
        s.shape(ellipse(x, 250, 50, 70), PINK, outline=False)
    s.shape(ellipse(256, 240, 110, 100), GREY, GREY_D, light=True)
    s.shape(stroke([(256, 290), (256, 400), (300, 440)], 50), GREY, GREY_D)
    face(s, 256, 230, 100)


def lv_workshop_9(s):  # a bouncy ball
    s.shape(ellipse(256, 200, 140), RED, RED_D, light=True)
    s.line(arc(256, 200, 140, 90, 270, 18), YELLOW)
    s.shape(ellipse(256, 440, 120, 20), GREY_D, outline=False)
    for x in (130, 382):
        s.line(stroke([(x, 340), (x + (256 - x) * 0.2, 400)], 8), GREY)


def lv_workshop_10(s):  # a sneaker on a pedal
    s.shape(rrect(70, 380, 442, 440, 16), GOLD, GOLD_D)
    s.shape(union(rrect(110, 250, 430, 380, 50), rrect(110, 160, 270, 300, 40)), BLUE, BLUE_D, light=True)
    s.shape(rrect(100, 350, 440, 390, 18), WHITE, WHITE_D)
    for x in (170, 210, 250):
        s.line(stroke([(x, 200), (x + 40, 260)], 7), WHITE)


def lv_workshop_11(s):  # a tin can phone
    s.line(arc(256, 140, 180, 20, 160, 7), DARK)
    for x in (100, 412):
        s.shape(rrect(x - 60, 240, x + 60, 420, 14), SILVER, SILVER_D, light=True)
        s.shape(ellipse(x, 240, 60, 20), GREY, GREY_D, lw=5)
        for y in (300, 360):
            s.line(stroke([(x - 60, y), (x + 60, y)], 5), SILVER_D)
    note(s, 230, 300, PURPLE, 18)


def lv_workshop_12(s):  # the workshop's concert medal
    medal(s, ORANGE, ORANGE_D, WOOD)


def lv_treehouse_1(s):  # a caterpillar inching along
    for i, c in enumerate([GREEN, LEAF, GREEN, LEAF, GREEN]):
        x = 100 + i * 70
        y = 330 - (60 if i == 2 else 30 if i in (1, 3) else 0)
        s.shape(ellipse(x, y, 60), c, LEAF_D, light=True)
    s.shape(ellipse(420, 290, 70), GREEN, LEAF_D, light=True)
    face(s, 420, 290, 64)
    for side in (-1, 1):
        s.line(stroke([(420 + side * 20, 230), (420 + side * 40, 170)], 6))


def lv_treehouse_2(s):  # a marching ant
    for x, r in ((150, 70), (256, 56), (360, 80)):
        s.shape(ellipse(x, 300, r, r * 0.8), RED, RED_D, light=True)
    face(s, 360, 290, 70)
    for x in (200, 256, 310):
        s.line(stroke([(x, 330), (x - 30, 430)], 9)); s.line(stroke([(x, 330), (x + 30, 430)], 9))
    s.line(stroke([(380, 230), (420, 150)], 7)); s.line(stroke([(340, 230), (320, 150)], 7))


def lv_treehouse_3(s):  # a hedgehog, tucked and round
    spikes = union(*[poly([(256 + 120 * math.cos(a - 0.2), 300 + 110 * math.sin(a - 0.2)), (256 + 200 * math.cos(a), 300 + 180 * math.sin(a)),
                          (256 + 120 * math.cos(a + 0.2), 300 + 110 * math.sin(a + 0.2))]) for a in [math.pi + k * math.pi / 9 for k in range(10)]])
    s.shape(spikes, WOOD_D)
    s.shape(ellipse(256, 320, 160, 120), WOOD, WOOD_D)
    s.shape(ellipse(256, 350, 110, 80), SAND, SAND_D, light=True)
    face(s, 256, 340, 90)
    s.fill(ellipse(256, 360, 12), DARK)


def lv_treehouse_4(s):  # little weights
    s.shape(rrect(130, 240, 382, 272, 14), SILVER, SILVER_D)
    for x in (130, 382):
        s.shape(rrect(x - 40, 170, x + 40, 340, 24), PURPLE, PURPLE_D, light=True)
    s.shape(star(256, 140, 36, points=4, inner=0.35), YELLOW, None, lw=4)


def lv_treehouse_5(s):  # hopscotch squares
    for x, y, c in ((256, 400, RED), (256, 300, ORANGE), (190, 200, YELLOW), (322, 200, GREEN), (256, 100, BLUE)):
        s.shape(rrect(x - 60, y - 46, x + 60, y + 46, 8), c, None, light=True)
    s.shape(ellipse(420, 400, 30), PINK, PINK_D, lw=5)


def lv_treehouse_6(s):  # a bunch of grapes
    s.shape(stroke([(256, 150), (290, 70)], 16), WOOD, WOOD_D)
    s.shape(ellipse(330, 110, 60, 30, angle=-20), LEAF, LEAF_D)
    for row, n in enumerate((4, 3, 2, 1)):
        for k in range(n):
            x = 256 + (k - (n - 1) / 2) * 70
            s.shape(ellipse(x, 200 + row * 62, 40), PURPLE, PURPLE_D, light=True)


def lv_treehouse_7(s):  # a fox
    for side in (-1, 1):
        s.shape(poly([(256 + side * 50, 180), (256 + side * 150, 70), (256 + side * 150, 230)]), ORANGE, ORANGE_D)
    s.shape(poly([(80, 200), (432, 200), (256, 430)]), ORANGE, ORANGE_D, light=True)
    s.shape(poly([(140, 260), (256, 290), (256, 430)]), WHITE, outline=False)
    s.shape(poly([(372, 260), (256, 290), (256, 430)]), WHITE, outline=False)
    s.eye(200, 270, 15); s.eye(312, 270, 15)
    s.fill(ellipse(256, 410, 18, 14), DARK)


def lv_treehouse_8(s):  # a hand mirror
    s.shape(stroke([(256, 300), (256, 460)], 34), PINK, PINK_D)
    s.shape(ellipse(256, 200, 150, 160), PINK, PINK_D, light=True)
    s.shape(ellipse(256, 200, 116, 126), SKY, SKY_D)
    s.line(stroke([(190, 140), (230, 110)], 12), WHITE); s.line(stroke([(190, 190), (270, 120)], 12), WHITE)


def lv_treehouse_9(s):  # a monkey
    for x in (110, 402):
        s.shape(ellipse(x, 240, 54), WOOD, WOOD_D); s.shape(ellipse(x, 240, 30), SAND, outline=False)
    s.shape(ellipse(256, 250, 150, 140), WOOD, WOOD_D, light=True)
    s.shape(union(ellipse(210, 230, 64, 70), ellipse(302, 230, 64, 70), ellipse(256, 310, 110, 70)), SAND, SAND_D)
    face(s, 256, 240, 110)
    s.fill(ellipse(240, 300, 6), WOOD_D); s.fill(ellipse(272, 300, 6), WOOD_D)


def lv_treehouse_10(s):  # an echo bat
    s.shape(poly([(256, 280), (60, 180), (110, 260), (70, 330), (160, 320), (256, 360), (352, 320), (442, 330), (402, 260), (452, 180)]), PURPLE_D, DARK_D)
    s.shape(ellipse(256, 290, 70, 80), PURPLE, PURPLE_D, light=True)
    for side in (-1, 1):
        s.shape(poly([(256 + side * 30, 230), (256 + side * 60, 170), (256 + side * 66, 250)]), PURPLE, PURPLE_D)
    face(s, 256, 290, 66)
    for r in (40, 70):
        s.line(arc(400, 120, r, 200, 300, 6), SKY_D)


def lv_treehouse_11(s):  # a magnifying glass
    s.shape(stroke([(330, 330), (440, 450)], 40), WOOD, WOOD_D)
    s.shape(ellipse(220, 210, 150), GOLD, GOLD_D)
    s.shape(ellipse(220, 210, 116), SKY, SKY_D)
    s.line(arc(220, 210, 80, 200, 260, 14), WHITE)


def lv_treehouse_12(s):  # the treehouse's concert medal
    medal(s, LEAF, LEAF_D, WOOD)


def lv_night_1(s):  # a ticking alarm clock
    for x in (150, 362):
        s.shape(ellipse(x, 110, 50), GOLD, GOLD_D)
    s.shape(ellipse(256, 270, 170), RED, RED_D, light=True)
    s.shape(ellipse(256, 270, 130), WHITE, WHITE_D)
    s.line(stroke([(256, 270), (256, 180)], 12)); s.line(stroke([(256, 270), (320, 300)], 12))
    for x in (160, 352):
        s.shape(stroke([(x, 400), (x + (x - 256) * 0.3, 460)], 18), DARK)


def lv_night_2(s):  # two maracas
    for x, a, c, cd in ((180, 20, ORANGE, ORANGE_D), (332, -20, PINK, PINK_D)):
        s.shape(stroke([(x, 300), (x + (256 - x) * 0.4, 460)], 26), WOOD, WOOD_D)
        s.shape(ellipse(x, 210, 90, 120, angle=a), c, cd, light=True)
        for k in range(3):
            s.fill(ellipse(x - 30 + k * 30, 200 + (k % 2) * 40, 10), YELLOW)


def lv_night_3(s):  # a sleepy bear
    for x in (150, 362):
        s.shape(ellipse(x, 140, 50), WOOD, WOOD_D); s.shape(ellipse(x, 140, 26), SAND, outline=False)
    s.shape(ellipse(256, 270, 160, 150), WOOD, WOOD_D, light=True)
    s.shape(ellipse(256, 320, 70, 54), SAND, SAND_D)
    s.line(arc(200, 250, 26, 20, 160, 8)); s.line(arc(312, 250, 26, 20, 160, 8))
    s.fill(ellipse(256, 300, 18, 14), DARK)
    s.shape(stroke([(380, 220), (380, 340)], 22), PINK, PINK_D)
    s.shape(ellipse(380, 210, 26), PINK, PINK_D, lw=5)


def lv_night_4(s):  # a metronome
    s.shape(poly([(160, 440), (352, 440), (300, 80), (212, 80)]), WOOD, WOOD_D, light=True)
    s.shape(poly([(200, 400), (312, 400), (280, 130), (232, 130)]), CREAM, CREAM_D)
    s.shape(stroke([(256, 400), (320, 150)], 10), DARK)
    s.shape(rrect(290, 190, 330, 230, 6), GOLD, GOLD_D, lw=5)


def lv_night_5(s):  # a rocket going up
    s.shape(union(ellipse(256, 240, 90, 170), poly([(256, 40), (200, 140), (312, 140)])), WHITE, WHITE_D, light=True)
    s.shape(poly([(256, 40), (206, 130), (306, 130)]), RED, RED_D)
    for side in (-1, 1):
        s.shape(poly([(256 + side * 80, 300), (256 + side * 150, 400), (256 + side * 70, 380)]), RED, RED_D)
    s.shape(ellipse(256, 230, 40), SKY, SKY_D)
    s.shape(drop(256, 440, 40), ORANGE, outline=True)
    s.shape(drop(256, 430, 20), YELLOW, outline=False)


def lv_night_6(s):  # odd socks
    for x, c, cd, a in ((170, RED, RED_D, 10), (342, BLUE, BLUE_D, -10)):
        s.shape(union(rrect(x - 50, 100, x + 50, 330, 24), ellipse(x + (40 if a > 0 else -40), 360, 90, 60)), c, cd, light=True)
        s.shape(rrect(x - 56, 90, x + 56, 140, 14), WHITE, WHITE_D)
    for y in (190, 240):
        s.line(stroke([(126, y), (214, y)], 10), YELLOW)
    s.fill(ellipse(330, 220, 14), YELLOW); s.fill(ellipse(360, 280, 14), YELLOW)


def lv_night_7(s):  # a thunder cloud
    s.shape(union(ellipse(160, 230, 90), ellipse(270, 190, 110), ellipse(370, 240, 90), rrect(90, 230, 440, 320, 44)), GREY, GREY_D, light=True)
    s.shape(poly([(270, 300), (200, 420), (260, 410), (220, 490), (330, 370), (270, 380), (310, 300)]), YELLOW, YELLOW_D)


def lv_night_8(s):  # a racing rabbit
    for x in (230, 280):
        s.shape(ellipse(x, 120, 30, 90, angle=(x - 255) * 0.3), WHITE, WHITE_D); s.shape(ellipse(x, 130, 14, 60), PINK, outline=False)
    s.shape(ellipse(256, 340, 130, 100), WHITE, WHITE_D, light=True)
    s.shape(ellipse(256, 240, 90, 80), WHITE, WHITE_D, light=True)
    face(s, 256, 240, 80)
    for y in (300, 350, 400):
        s.line(stroke([(60, y), (110, y)], 9), SKY_D)


def lv_night_9(s):  # a long snake
    pts = [(80 + t * 3.4, 300 + 80 * math.sin(t / 18)) for t in range(0, 100)]
    s.shape(stroke(pts, 60), GREEN, GREEN_D)
    for k in range(0, 100, 12):
        x, y = pts[k]
        s.fill(ellipse(x, y, 10), YELLOW)
    s.shape(ellipse(420, 270, 50, 40), GREEN, GREEN_D)
    s.eye(430, 260, 10)
    s.line(stroke([(466, 280), (496, 290)], 5), RED)


def lv_night_10(s):  # an umbrella, sunny or rainy
    s.shape(stroke([(256, 200), (256, 420), (300, 440)], 14), DARK)
    s.shape(union(minus(ellipse(256, 230, 200, 160), rrect(40, 230, 470, 420, 0)), rrect(56, 220, 456, 236, 4)), RED, RED_D, light=True)
    for x in (156, 256, 356):
        s.line(stroke([(x, 230), (x + (x - 256) * 0.1, 80)], 6), RED_D)
    s.shape(drop(110, 330, 16), SKY, outline=True, lw=4)
    s.shape(ellipse(420, 360, 40), YELLOW, YELLOW_D)


def lv_night_11(s):  # a black cat
    for side in (-1, 1):
        s.shape(poly([(256 + side * 60, 170), (256 + side * 140, 70), (256 + side * 150, 200)]), DARK, DARK_D)
    s.shape(ellipse(256, 280, 160, 140), DARK, DARK_D, light=True)
    for x in (196, 316):
        s.fill(ellipse(x, 260, 26, 30), YELLOW); s.fill(ellipse(x, 262, 8, 22), EYE)
    s.fill(poly([(240, 310), (272, 310), (256, 326)]), PINK)
    for side in (-1, 1):
        s.line(stroke([(256 + side * 90, 330), (256 + side * 190, 320)], 6), GREY)


def lv_night_12(s):  # the night sky's concert medal
    medal(s, PURPLE, PURPLE_D, BLUE)


def lv_fair_1(s):  # a bunch of balloons
    for x, y, c, cd in ((170, 170, RED, RED_D), (340, 160, BLUE, BLUE_D), (256, 230, YELLOW, YELLOW_D)):
        s.line(stroke([(x, y + 100), (256, 450)], 5), DARK)
        s.shape(ellipse(x, y, 80, 96), c, cd, light=True)


def lv_fair_2(s):  # a pinwheel
    s.shape(stroke([(256, 260), (256, 460)], 18), WOOD, WOOD_D)
    for k, (c, cd) in enumerate([(RED, RED_D), (YELLOW, YELLOW_D), (BLUE, BLUE_D), (GREEN, GREEN_D)]):
        a = k * math.pi / 2
        p0 = (256, 230)
        p1 = (256 + 160 * math.cos(a), 230 + 160 * math.sin(a))
        p2 = (256 + 160 * math.cos(a + math.pi / 2.6), 230 + 160 * math.sin(a + math.pi / 2.6))
        s.shape(poly([p0, p1, p2]), c, cd)
    s.shape(ellipse(256, 230, 22), WHITE, WHITE_D, lw=5)


def lv_fair_3(s):  # the strongman's bell
    s.shape(rrect(230, 120, 282, 440, 10), RED, RED_D)
    for y in range(160, 420, 50):
        s.line(stroke([(232, y), (280, y)], 5), WHITE)
    s.shape(union(ellipse(256, 110, 80, 60), rrect(176, 100, 336, 140, 10)), GOLD, GOLD_D, light=True)
    s.shape(ellipse(256, 150, 20), GOLD_D)
    s.shape(rrect(170, 420, 342, 460, 12), WOOD, WOOD_D)
    for x, y in ((140, 80), (380, 60), (390, 150)):
        s.shape(star(x, y, 26, inner=0.4, points=4), YELLOW, None, lw=4)


def lv_fair_4(s):  # a camera
    s.shape(rrect(70, 160, 442, 400, 40), DARK, DARK_D, light=True)
    s.shape(rrect(170, 110, 290, 170, 14), DARK, DARK_D)
    s.shape(ellipse(256, 280, 100), GREY, GREY_D)
    s.shape(ellipse(256, 280, 66), BLUE_D)
    s.fill(ellipse(230, 254, 20), (255, 255, 255))
    s.shape(rrect(360, 190, 410, 230, 8), YELLOW, YELLOW_D, lw=5)


def lv_sit(s):  # a cushion
    s.shape(rrect(80, 200, 432, 380, 80), TEAL, TEAL_D, light=True)
    s.shape(ellipse(256, 290, 24), TEAL_D, lw=5)
    for x, y in ((90, 210), (422, 210), (90, 370), (422, 370)):
        s.shape(ellipse(x, y, 26), YELLOW, YELLOW_D, lw=5)


def lv_find_c(s):  # a treasure map
    s.shape(poly([(80, 120), (200, 90), (320, 130), (440, 100), (440, 400), (320, 430), (200, 390), (80, 420)]), SAND, SAND_D, light=True)
    s.line(stroke([(120, 360), (180, 300), (250, 320), (300, 240)], 8), RED)
    s.line(stroke([(330, 190), (390, 250)], 16), RED); s.line(stroke([(390, 190), (330, 250)], 16), RED)
    s.shape(star(150, 170, 26), YELLOW, YELLOW_D, lw=4)


def lv_fingers_right(s):  # a glove
    s.shape(union(rrect(160, 250, 360, 440, 50), *[rrect(x - 26, y, x + 26, 300, 26) for x, y in ((190, 110), (240, 80), (290, 90), (340, 130))],
                  ellipse(130, 300, 30, 70, angle=40)), YELLOW, YELLOW_D, light=True)
    s.shape(rrect(150, 400, 370, 450, 16), RED, RED_D)


def lv_fingers_left(s):  # two mittens on a string
    s.line(arc(256, 150, 160, 200, 340, 8), DARK)
    for x, c, cd in ((150, BLUE, BLUE_D), (362, PINK, PINK_D)):
        s.shape(union(rrect(x - 70, 180, x + 70, 400, 70), ellipse(x + (80 if x > 256 else -80), 260, 30, 56)), c, cd, light=True)
        s.shape(rrect(x - 74, 360, x + 74, 420, 18), WHITE, WHITE_D)


def lv_beat(s):  # a conductor's baton
    s.shape(stroke([(110, 420), (400, 110)], 14), WHITE, WHITE_D)
    s.shape(stroke([(100, 430), (160, 370)], 40), WOOD, WOOD_D)
    for r in (60, 110):
        s.line(arc(400, 110, r, 110, 200, 7), GOLD_D)
    note(s, 300, 400, BLUE, 20)


def lv_white_keys(s):  # an alphabet train: an engine and two cars
    s.shape(stroke([(40, 420), (470, 420)], 10), DARK)
    for x, c, cd in ((130, RED, RED_D), (260, ORANGE, ORANGE_D), (390, YELLOW, YELLOW_D)):
        s.shape(rrect(x - 56, 260, x + 56, 390, 14), c, cd, light=True)
        s.shape(rrect(x - 30, 290, x + 30, 350, 8), WHITE, WHITE_D, lw=5)
        for dx in (-30, 30):
            s.shape(ellipse(x + dx, 400, 24), DARK)
    s.shape(rrect(84, 190, 130, 260, 8), RED_D)
    s.shape(ellipse(107, 160, 30, 20), GREY, outline=False)


LEVELS = {name[3:]: fn for name, fn in list(globals().items()) if name.startswith("lv_") and callable(fn)}


SUBJECTS = {
    "garden": [garden_1, garden_2, garden_3, garden_4, garden_5, garden_6, garden_7, garden_8, garden_9, garden_10],
    "pond": [pond_1, pond_2, pond_3, pond_4, pond_5, pond_6, pond_7, pond_8, pond_9, pond_10],
    "concert": [concert_1, concert_2, concert_3, concert_4, concert_5, concert_6, concert_7, concert_8, concert_9, concert_10],
    # 2.3
    "workshop": [workshop_1, workshop_2, workshop_3, workshop_4, workshop_5, workshop_6, workshop_7, workshop_8, workshop_9, workshop_10],
    "treehouse": [treehouse_1, treehouse_2, treehouse_3, treehouse_4, treehouse_5, treehouse_6, treehouse_7, treehouse_8, treehouse_9, treehouse_10],
    "nightsky": [nightsky_1, nightsky_2, nightsky_3, nightsky_4, nightsky_5, nightsky_6, nightsky_7, nightsky_8, nightsky_9, nightsky_10],
    "fair": [fair_1, fair_2, fair_3, fair_4, fair_5, fair_6, fair_7, fair_8, fair_9, fair_10],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="also write a contact sheet of every sticker made here")
    ap.add_argument("--only", help="one sticker, e.g. pond_3")
    ap.add_argument("--place", help="one place's ten, e.g. fair")
    ap.add_argument("--levels", action="store_true", help="2.5: the stones' own stickers (sticker_lv_<lesson>.webp) instead")
    args = ap.parse_args()
    RES.mkdir(parents=True, exist_ok=True)
    made = []
    if args.levels:
        for key, fn in LEVELS.items():
            name = f"sticker_lv_{key}"
            if args.only and args.only != key:
                continue
            s = Sticker()
            fn(s)
            img = s.finish()
            img.save(RES / f"{name}.webp", "WEBP", quality=88, method=6)
            made.append((name, img))
            print(name, (RES / f"{name}.webp").stat().st_size, "bytes")
    for place, fns in ({} if args.levels else SUBJECTS).items():
        for n, fn in enumerate(fns, start=1):
            name = f"sticker_{place}_{n}"
            if args.only and args.only != f"{place}_{n}":
                continue
            if args.place and args.place != place:
                continue
            s = Sticker()
            fn(s)
            img = s.finish()
            img.save(RES / f"{name}.webp", "WEBP", quality=90, method=6)
            made.append((name, img))
            print(name, (RES / f"{name}.webp").stat().st_size, "bytes")
    if args.sheet and made:
        cell = 200
        sheet = Image.new("RGBA", (cell * 10, cell * ((len(made) + 9) // 10)), (250, 246, 240, 255))
        for i, (_, img) in enumerate(made):
            sheet.alpha_composite(img.resize((cell, cell), Image.LANCZOS), ((i % 10) * cell, (i // 10) * cell))
        sheet.save(args.sheet)


if __name__ == "__main__":
    main()
