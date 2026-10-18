"""World sounds (2.1, world team): short synthesized jingles and effects, no samples, nothing copyrighted.

Bell-like notes are a sine with a few decaying partials and a touch of triangle; effects are shaped noise. Every
file is peak-normalised to 0.8 (no clipping), with a 3 ms fade at both ends so nothing pops. 44.1 kHz mono PCM16.

Output: app/src/main/res/raw/<name>.wav
    tick            a soft wooden tick for every note played (40 ms)
    jingle_common   a sticker drops: two notes up (0.6 s)
    jingle_rare     a rare sticker: a quick arpeggio with a bell on top (0.95 s)
    jingle_shiny    a shiny sticker: a two-octave run into a shimmering chord with twinkles (1.45 s)
    chime_star      one star on a recap; played faster for the second and third star (0.8 s)
    munch           the pup eats the treats (0.7 s)
    egg_crack       the mystery egg cracks (0.35 s)
    pop             a sticker lands in the album, an item goes on the pup (0.22 s)
    python -I scripts/gen_jingles.py
"""
import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app/src/main/res/raw"
RATE = 44_100
PEAK = 0.8


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def mix(into, sound, at):
    start = int(at * RATE)
    if len(into) < start + len(sound):
        into.extend([0.0] * (start + len(sound) - len(into)))
    for i, v in enumerate(sound):
        into[start + i] += v


def freq(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def tri(x):
    """A triangle wave with period 2 pi, -1..1."""
    p = (x / (2 * math.pi)) % 1.0
    return 4 * abs(p - 0.5) - 1


def bell(midi, seconds, gain=1.0, decay=0.35, bright=0.35):
    """A soft bell: the fundamental, an octave and a twelfth that die faster, a little triangle for body."""
    f = freq(midi)
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        attack = min(1.0, t / 0.004)
        e = math.exp(-t / decay)
        v = math.sin(2 * math.pi * f * t) * e
        v += bright * 0.5 * math.sin(2 * math.pi * f * 2 * t) * math.exp(-t / (decay * 0.5))
        v += bright * 0.25 * math.sin(2 * math.pi * f * 3.01 * t) * math.exp(-t / (decay * 0.3))
        v += 0.15 * tri(2 * math.pi * f * t) * e
        out.append(gain * attack * v)
    return out


def noise_burst(seconds, decay, cutoff, gain, seed, high=False):
    """Noise through a one-pole low-pass (or the high-pass left over), with an exponential decay."""
    r = random.Random(seed)
    n = int(RATE * seconds)
    a = math.exp(-2 * math.pi * cutoff / RATE)
    low = 0.0
    out = []
    for i in range(n):
        t = i / RATE
        x = r.uniform(-1, 1)
        low = (1 - a) * x + a * low
        v = (x - low) if high else low
        out.append(gain * v * math.exp(-t / decay) * min(1.0, t / 0.002))
    return out


def finish(samples):
    peak = max(abs(v) for v in samples) or 1.0
    k = PEAK / peak
    out = [v * k for v in samples]
    fade = int(RATE * 0.003)
    for i in range(min(fade, len(out))):
        out[i] *= i / fade
        out[-1 - i] *= i / fade
    return out


def write(name, samples):
    RAW.mkdir(parents=True, exist_ok=True)
    data = finish(samples)
    with wave.open(str(RAW / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767)) for v in data))
    print(f"{name}.wav  {len(data) / RATE:.2f} s  peak {max(abs(v) for v in data):.2f}")


def tick():
    n = int(RATE * 0.04)
    out = []
    for i in range(n):
        t = i / RATE
        v = 0.6 * math.sin(2 * math.pi * 1650 * t) * math.exp(-t / 0.004)
        v += 0.5 * math.sin(2 * math.pi * 820 * t) * math.exp(-t / 0.008)
        out.append(v * min(1.0, t / 0.0006))
    return out


def jingle_common():
    out = silence(0.6)
    mix(out, bell(76, 0.35, 0.8, decay=0.12), 0.0)          # E5
    mix(out, bell(81, 0.5, 1.0, decay=0.2), 0.11)           # A5
    return out


def jingle_rare():
    out = silence(0.95)
    for k, m in enumerate([72, 76, 79, 84]):                 # C5 E5 G5 C6
        mix(out, bell(m, 0.4, 0.7, decay=0.12), k * 0.075)
    mix(out, bell(88, 0.65, 0.9, decay=0.28, bright=0.5), 0.3)   # E6 on top
    mix(out, bell(84, 0.65, 0.5, decay=0.28), 0.3)
    return out


def jingle_shiny():
    out = silence(1.45)
    run = [72, 76, 79, 83, 86, 91, 96]                       # C5 E5 G5 B5 D6 G6 C7
    for k, m in enumerate(run):
        mix(out, bell(m, 0.3, 0.55, decay=0.09), k * 0.055)
    # The chord shimmers: a slow tremolo on C6 E6 G6 C7.
    t0 = len(run) * 0.055
    for m, g in [(84, 0.6), (88, 0.5), (91, 0.5), (96, 0.35)]:
        b = bell(m, 1.0, g, decay=0.45, bright=0.45)
        mix(out, [v * (0.75 + 0.25 * math.sin(2 * math.pi * 7 * i / RATE)) for i, v in enumerate(b)], t0)
    r = random.Random(7)
    for k in range(6):                                       # twinkles
        mix(out, bell(r.choice([98, 100, 103, 105]), 0.12, 0.25, decay=0.03), t0 + 0.12 + k * 0.11)
    return out


def chime_star():
    out = silence(0.8)
    mix(out, bell(79, 0.8, 1.0, decay=0.32, bright=0.55), 0.0)    # G5
    mix(out, bell(91, 0.4, 0.25, decay=0.12), 0.0)               # a glint an octave up
    return out


def munch():
    out = silence(0.7)
    for k, at in enumerate([0.0, 0.2, 0.4]):
        mix(out, noise_burst(0.12, 0.03, 1800, 1.0, seed=k), at)
        mix(out, [0.5 * math.sin(2 * math.pi * 140 * i / RATE) * math.exp(-i / RATE / 0.03) for i in range(int(RATE * 0.1))], at)
    return out


def egg_crack():
    out = silence(0.35)
    mix(out, noise_burst(0.05, 0.008, 3000, 1.0, seed=11, high=True), 0.0)
    mix(out, noise_burst(0.06, 0.012, 2500, 0.8, seed=12, high=True), 0.09)
    mix(out, bell(84, 0.2, 0.35, decay=0.05), 0.12)
    return out


def pop():
    n = int(RATE * 0.22)
    out = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = 300 + 700 * math.exp(-t / 0.03)
        phase += 2 * math.pi * f / RATE
        out.append(math.sin(phase) * math.exp(-t / 0.05) * min(1.0, t / 0.002))
    return out


def main():
    for name, fn in [("tick", tick), ("jingle_common", jingle_common), ("jingle_rare", jingle_rare), ("jingle_shiny", jingle_shiny),
                     ("chime_star", chime_star), ("munch", munch), ("egg_crack", egg_crack), ("pop", pop)]:
        write(name, fn())


if __name__ == "__main__":
    main()
