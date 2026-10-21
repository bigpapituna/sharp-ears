"""The pups the email brief uses (design D): small palette PNGs for <img> tags, cut from res/drawable-nodpi.

Writes app/src/main/assets/email/pup-<pose>.png (sticker, crown, flying) and pup-<pose>-<n>.png (dancing, clapping,
thinking, n = the avatar's sweatband colour 0 to 7, painted with scripts/tint_avatars.py's tint). Each is IMG_W px wide,
transparent, reduced to a 96-colour palette (about 15 KB), so an email that inlines its three pictures stays near 60 KB
and the same files can be hosted at an HTTPS address (EMAIL_IMAGE_BASE) for mail apps that drop data: pictures.
    python -I scripts/cut_email_pups.py
"""
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))      # -I leaves the script's folder off the path
import tint_avatars as t  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/email"
IMG_W = 200
COLOURS = 96
TINTED = ["dancing", "clapping", "thinking"]
PLAIN = ["sticker", "crown", "flying"]


def save(img, name):
    k = IMG_W / img.width
    small = img.resize((IMG_W, round(img.height * k)), Image.LANCZOS)
    # A palette with transparency: quantize the RGBA image (fast octree keeps alpha), then optimise the PNG.
    q = small.quantize(colors=COLOURS, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    q.save(OUT / name, "PNG", optimize=True)
    print(name, small.size, (OUT / name).stat().st_size)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for pose in PLAIN:
        save(Image.open(t.RES / f"pup_{pose}.webp").convert("RGBA"), f"pup-{pose}.png")
    for pose in TINTED:
        base = Image.open(t.RES / f"pup_{pose}.webp").convert("RGBA")
        for i, c in enumerate(t.COLOURS):
            save(base if i == 0 else t.tinted(base, c), f"pup-{pose}-{i}.png")


if __name__ == "__main__":
    main()
