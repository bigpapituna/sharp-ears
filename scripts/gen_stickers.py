"""Placeholder stickers (2.1, world team): the 30 subjects of art/GEMINI-BRIEF.md section C as flat shapes.

Each sticker is a few coloured shapes in the pup's style (thick dark brown outline on every shape, a soft shade on
the lower right, a highlight on the upper left), inside a thick white die-cut border with a hairline edge, on a
transparent 512 px square. Drawn at 4x and scaled down, so edges are smooth. The painted Gemini stickers replace
these one for one under the same names.

Output: app/src/main/res/drawable-nodpi/sticker_<place>_<n>.webp (place: garden, pond, concert; n: 1..10)
    python -I scripts/gen_stickers.py [--sheet out.png]
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


SUBJECTS = {
    "garden": [garden_1, garden_2, garden_3, garden_4, garden_5, garden_6, garden_7, garden_8, garden_9, garden_10],
    "pond": [pond_1, pond_2, pond_3, pond_4, pond_5, pond_6, pond_7, pond_8, pond_9, pond_10],
    "concert": [concert_1, concert_2, concert_3, concert_4, concert_5, concert_6, concert_7, concert_8, concert_9, concert_10],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="also write a contact sheet of all 30 here")
    ap.add_argument("--only", help="one sticker, e.g. pond_3")
    args = ap.parse_args()
    RES.mkdir(parents=True, exist_ok=True)
    made = []
    for place, fns in SUBJECTS.items():
        for n, fn in enumerate(fns, start=1):
            name = f"sticker_{place}_{n}"
            if args.only and args.only != f"{place}_{n}":
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
