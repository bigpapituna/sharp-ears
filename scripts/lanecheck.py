"""Compares falling-note blocks in a screenshot with the positions the app logged (laneNote lines).

Usage: python scripts/lanecheck.py out/screen.png out/lane.log
For each logged note that sits fully inside the lane, walks down the note's column from its middle
and finds where the note color ends. That edge must be within 10 px of the logged bottom.
Exits 1 if any checked note is off. Needs Pillow.
"""
import re
import sys

from PIL import Image

TOL = 10


def note_colored(px):
    r, g, b = px[:3]
    orange = r > 150 and 90 < g < 190 and b < 120
    blue = b > 150 and r < 120 and g > 100
    return orange or blue


def main(png, log):
    img = Image.open(png).convert("RGB")
    text = open(log, encoding="utf-8", errors="replace").read()
    dump = re.findall(r"laneDump pos=(\d+) top=(\d+) height=(\d+)", text)
    if not dump:
        sys.exit("no laneDump line in log")
    pos, lane_top, lane_h = map(int, dump[-1])
    lane_bottom = lane_top + lane_h
    notes = re.findall(r"laneNote (\S+) hand=(\w+) x=(\d+) top=(-?\d+) bottom=(-?\d+)", text)
    checked = bad = 0
    for name, hand, x, top, bottom in notes:
        x, top, bottom = int(x), int(top), int(bottom)
        if top < lane_top + 5 or bottom > lane_bottom - 15 or bottom - top < 24:
            continue  # clipped at an edge or too short to measure
        y = (top + bottom) // 2
        if not note_colored(img.getpixel((x, y))):
            print(f"FAIL {name} {hand}: no note color at its middle ({x},{y})")
            bad += 1
            checked += 1
            continue
        while y < lane_bottom and note_colored(img.getpixel((x, y))):
            y += 1
        off = y - bottom
        checked += 1
        ok = abs(off) <= TOL
        bad += not ok
        print(f"{'ok  ' if ok else 'FAIL'} {name:4} {hand:5} x={x} expected bottom={bottom} found={y} off={off:+d}")
    print(f"position {pos} ms: {checked} notes checked, {bad} off by more than {TOL} px")
    sys.exit(1 if bad or not checked else 0)


if __name__ == "__main__":
    main(*sys.argv[1:3])
