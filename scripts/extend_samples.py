"""
2.19.0: long notes no longer fade after 2 s. The piano samples (scripts/samples.ps1: VSCO 2 CE upright, CC0) were cut to 2 s
with a fade from 1.6 s, so a whole note at a slow tempo died early. This stretches each app/src/main/assets/samples/p<midi>.ogg
into assets/samples-long, SECONDS long, by looping its own sustain: the attack and the first 1.5 s stay as recorded, then a pitch-synchronous loop of the
tone just before the old fade (its length the lag with the best match between 0.2 and 0.6 s, crossfaded with a Hann window) carries
on with the note's own decay, slowed to half its rate (a piano's aftersound), between 2 and 6 dB a second, and fades out over the last 0.5 s. No new
dependency in the app: SoundPool plays it as before, and a note is cut at its noteOff (StageSound) with a short release.

Mono 44.1 kHz 16-bit: 8 s decodes to 706 KB, under SoundPool's 1 MB per sound. Needs ffmpeg and numpy. Run once:
    python scripts/extend_samples.py            (reads assets/samples, writes assets/samples-long)

The 2 s originals stay in assets/samples and load first: SoundPool decodes the 8 s set about 5 times slower (2.2 s to 12 s on
the emulator), so the Stage plays the short set at once and swaps in the long one when it has loaded (audio/Stage.kt).
"""
import os, subprocess, sys
import numpy as np

SR = 44100
SECONDS = 8.0
SPLICE = 1.50           # the recording is kept up to here (the old fade began at 1.6 s)
XFADE = 0.06            # crossfade between loop copies and at the splice
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "samples")          # the 2 s originals, loaded first (fast)
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "samples-long")     # the 8 s ones, swapped in once loaded


def decode(data: bytes) -> np.ndarray:
    pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", "pipe:0", "-f", "s16le", "-ac", "1", "-ar", str(SR), "pipe:1"],
                         input=data, capture_output=True, check=True).stdout
    return np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768.0


def encode(x: np.ndarray, path: str):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ac", "1", "-ar", str(SR), "-i", "pipe:0",
                    "-c:a", "libvorbis", "-q:a", "3", path], input=pcm, check=True)


def rms(x):
    return float(np.sqrt(np.mean(x * x)) + 1e-12)


def best_lag(x, end, lo, hi, w):
    """The loop length in [lo, hi] samples whose copy one lag back matches the tone ending at [end] best."""
    ref = x[end - w:end]
    best, score = hi, -2.0
    for lag in range(lo, hi):
        seg = x[end - w - lag:end - lag]
        c = float(np.dot(ref, seg) / (np.linalg.norm(ref) * np.linalg.norm(seg) + 1e-12))
        if c > score: best, score = lag, c
    return best, score


def extend(x: np.ndarray, midi: int) -> np.ndarray:
    n_out = int(SECONDS * SR)
    s = int(SPLICE * SR)
    xf = int(XFADE * SR)
    # The decay: RMS in 50 ms windows from 0.4 s to the splice, a line through their dB.
    win = int(0.05 * SR)
    t = np.arange(int(0.4 * SR), s - win, win)
    db = np.array([20 * np.log10(rms(x[i:i + win])) for i in t])
    slope = np.polyfit(t / SR, db, 1)[0] if len(t) > 3 else -10.0       # dB per second, negative
    slope = float(np.clip(slope * 0.5, -6.0, -2.0))       # audible to the end of an 8 s note: at most 6 dB a second
    # The loop: 0.2 to 0.6 s of the tone just before the splice, a whole number of its own periods by correlation: copy k+1
    # starts where copy k's last XFADE matches best, and the two are crossfaded there (Hann in + out sums to one).
    lo, hi = int(0.20 * SR), int(0.60 * SR)
    lag, score = best_lag(x, s, lo, hi, xf)
    seg = x[s - lag - xf:s].copy()
    # Flatten the copy's own decay (a line in dB from its first half to its second), so the tiles do not pump.
    half = len(seg) // 2
    drop = 20 * np.log10(rms(seg[half:]) / rms(seg[:half]))
    seg *= 10 ** (-drop * (np.arange(len(seg)) / len(seg) - 0.5) / 20)
    seg /= rms(seg)
    fade_in = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, xf))
    seg[:xf] *= fade_in
    seg[-xf:] *= fade_in[::-1]
    n_tail = n_out - s + xf
    tail = np.zeros(n_tail + len(seg))
    for pos in range(0, n_tail, lag):
        tail[pos:pos + len(seg)] += seg
    tail = tail[:n_tail]
    level = rms(x[s - lag:s])
    tt = np.arange(n_tail) / SR
    tail *= level * 10 ** (slope * tt / 20)
    out = np.zeros(n_out)
    out[:s] = x[:s]
    out[s - xf:s] *= fade_in[::-1]
    out[s - xf:] += tail
    fade = int(0.5 * SR)
    out[-fade:] *= np.linspace(1, 0, fade) ** 2
    print(f"p{midi}: decay {slope:.1f} dB/s, loop {lag / SR * 1000:.0f} ms (match {score:.2f}), peak {np.max(np.abs(out)):.2f}")
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    names = sorted(f for f in os.listdir(SRC) if f.endswith(".ogg"))
    for name in names:
        midi = int(name[1:-4])
        data = open(os.path.join(SRC, name), "rb").read()
        x = decode(data)
        if len(x) > 2.2 * SR: sys.exit(f"{name} is not a 2 s original")
        encode(extend(x, midi), os.path.join(OUT, name))
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in names)
    print(f"{len(names)} files, {total // 1024} KB")


if __name__ == "__main__":
    main()
