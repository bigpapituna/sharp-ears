"""Write the simple public-domain tunes as two-track MIDI files (track 1 right hand, track 2 left hand).

Usage: python scripts/gen_songs.py [ids...]   -> app/src/main/assets/songs/<id>.mid
Tunes are traditional or long out of copyright; these arrangements (melody + one bass note per chord) are ours.
Token format: <note><octave>:<beats>, "r" is a rest, "+" joins notes into a chord (D5+F#4:4).
"""
import os, struct, sys

NAMES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
PPQ = 480


def midi(tok):
    n = NAMES[tok[0]]
    i = 1
    while i < len(tok) and tok[i] in '#b':
        n += 1 if tok[i] == '#' else -1
        i += 1
    return n + 12 * (int(tok[i:]) + 1)


def events(spec):
    t = 0.0
    out = []
    for tok in spec.split():
        name, beats = tok.split(':')
        if name != 'r':
            for p in name.split('+'):
                out.append((round(t * PPQ), round((t + float(beats)) * PPQ) - 8, midi(p)))
        t += float(beats)
    return out


def vlq(v):
    b = [v & 0x7f]
    v >>= 7
    while v:
        b.insert(0, (v & 0x7f) | 0x80)
        v >>= 7
    return bytes(b)


def track(evts, chan, name, tempo=None, ts=None):
    msgs = []
    if tempo:
        msgs.append((0, b'\xff\x51\x03' + int(60e6 / tempo).to_bytes(3, 'big')))
    if ts:
        msgs.append((0, b'\xff\x58\x04' + bytes([ts[0], {2: 1, 4: 2, 8: 3}[ts[1]], 24, 8])))
    msgs.append((0, b'\xff\x03' + vlq(len(name)) + name.encode()))
    for on, off, p in evts:
        msgs.append((on, bytes([0x90 | chan, p, 80])))
        msgs.append((off, bytes([0x80 | chan, p, 0])))
    msgs.sort(key=lambda m: (m[0], m[1][0] & 0xf0 == 0x90))  # note-offs before note-ons at the same tick
    body, last = b'', 0
    for tick, m in msgs:
        body += vlq(tick - last) + m
        last = tick
    body += b'\x00\xff\x2f\x00'
    return b'MTrk' + struct.pack('>I', len(body)) + body


def write(path, tempo, ts, rh, lh):
    data = b'MThd' + struct.pack('>IHHH', 6, 1, 2, PPQ)
    data += track(events(rh), 0, 'right', tempo, ts) + track(events(lh), 1, 'left')
    open(path, 'wb').write(data)


SONGS = {
    'ode-to-joy': (100, (4, 4),
        # A A' B A''
        'E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:0.5 D4:2 '
        'E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:0.5 C4:2 '
        'D4:1 D4:1 E4:1 C4:1 D4:1 E4:0.5 F4:0.5 E4:1 C4:1 D4:1 E4:0.5 F4:0.5 E4:1 D4:1 C4:1 D4:1 G3:2 '
        'E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:0.5 C4:2',
        'C3:4 G2:4 C3:4 G2:4 C3:4 G2:4 C3:4 G2:2 C3:2 '
        'G2:4 G2:2 C3:2 G2:2 C3:2 C3:2 G2:2 '
        'C3:4 G2:4 C3:4 G2:2 C3:2'),
    'twinkle': (100, (4, 4),
        'C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2 '
        'G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 '
        'C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2',
        'C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2 '
        'C3:2 F3:2 C3:2 G2:2 C3:2 F3:2 C3:2 G2:2 '
        'C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2'),
    'happy-birthday': (100, (3, 4),
        'G4:0.75 G4:0.25 A4:1 G4:1 C5:1 B4:2 G4:0.75 G4:0.25 A4:1 G4:1 D5:1 C5:2 G4:0.75 G4:0.25 '
        'G5:1 E5:1 C5:1 B4:1 A4:1 F5:0.75 F5:0.25 E5:1 C5:1 D5:1 C5:3',
        'r:1 C3:3 G2:3 G2:3 C3:3 C3:3 F3:3 C3:2 G2:1 C3:3'),
    'amazing-grace': (90, (3, 4),
        # New Britain (1831), melody as in Colin Hume's ABC; chords G G7 C G-D7 G Em D D | G G7 C G-D7 Em D7 G
        'D4:1 G4:2 B4:0.5 G4:0.5 B4:2 A4:1 G4:2 E4:1 D4:2 D4:1 G4:2 B4:0.5 G4:0.5 B4:2 A4:1 D5:5 B4:1 '
        'D5:2 B4:0.5 G4:0.5 B4:2 A4:1 G4:2 E4:1 D4:2 D4:1 G4:2 B4:0.5 G4:0.5 B4:2 A4:1 G4:5',
        'r:1 G2:3 G2:3 C3:3 G2:2 D3:1 G2:3 E3:3 D3:3 D3:3 '
        'G2:3 G2:3 C3:3 G2:2 D3:1 E3:3 D3:3 G2:5'),
    'canon-in-d': (60, (4, 4),
        # Pachelbel's ground with the first two violin lines, then the first line in thirds
        'F#5:1 E5:1 D5:1 C#5:1 B4:1 A4:1 B4:1 C#5:1 '
        'D5:1 C#5:1 B4:1 A4:1 G4:1 F#4:1 G4:1 E4:1 '
        'F#5+D5:1 E5+C#5:1 D5+B4:1 C#5+A4:1 B4+G4:1 A4+F#4:1 B4+G4:1 C#5+A4:1 '
        'D5:1 C#5:1 B4:1 A4:1 G4:1 F#4:1 G4:1 E4:1 '
        'F#5+D5:1 E5+C#5:1 D5+B4:1 C#5+A4:1 B4+G4:1 A4+F#4:1 B4+G4:1 C#5+A4:1 '
        'D5+F#4:4',
        ' '.join(['D3:1 A2:1 B2:1 F#2:1 G2:1 D2:1 G2:1 A2:1'] * 5) + ' D2+D3:4'),

    # --- Batch 2 (2026-10-08). Melody plus one bass note per chord. Written from memory, checked by bar count only.
    'scarborough-fair': (96, (3, 4),
        # D dorian. Are you going | Parsley, sage | Remember me | She once was
        'D4:2 D4:1 A4:2 A4:1 E4:1.5 F4:0.5 E4:1 D4:3 '
        'r:1 A4:1 C5:1 D5:2 C5:1 A4:1 B4:1 G4:1 A4:3 '
        'r:1 D5:1 D5:1 D5:2 C5:1 A4:2 G4:1 F4:1 E4:2 '
        'r:1 D4:1 A4:1 G4:2 F4:1 E4:1 D4:1 C4:1 D4:3',
        'D3:3 D3:3 C3:3 D3:3 F3:3 C3:3 G2:3 A2:3 D3:3 F3:3 F3:3 C3:3 D3:3 C3:3 A2:3 D3:3'),
    'auld-lang-syne': (80, (4, 4),
        # The verse, twice (second verse). C major; pickup "Should".
        'r:3 G3:1 '
        'C4:1.5 C4:0.5 C4:1 E4:1 D4:1.5 C4:0.5 D4:1 E4:1 C4:1.5 C4:0.5 E4:1 G4:1 A4:3 A4:1 '
        'G4:1.5 E4:0.5 E4:1 C4:1 D4:1.5 C4:0.5 D4:1 E4:1 C4:1.5 A3:0.5 A3:1 G3:1 C4:3 G3:1 '
        'C4:1.5 C4:0.5 C4:1 E4:1 D4:1.5 C4:0.5 D4:1 E4:1 C4:1.5 C4:0.5 E4:1 G4:1 A4:3 A4:1 '
        'G4:1.5 E4:0.5 E4:1 C4:1 D4:1.5 C4:0.5 D4:1 E4:1 C4:1.5 A3:0.5 A3:1 G3:1 C4:4',
        'r:4 C3:4 G2:4 C3:4 F2:4 C3:4 G2:4 F2:4 C3:4 C3:4 G2:4 C3:4 F2:4 C3:4 G2:4 F2:4 C3:4'),
    'silent-night': (90, (3, 4),
        'G4:1.5 A4:0.5 G4:1 E4:3 G4:1.5 A4:0.5 G4:1 E4:3 '
        'D5:2 D5:1 B4:3 C5:2 C5:1 G4:3 '
        'A4:2 A4:1 C5:1.5 B4:0.5 A4:1 G4:1.5 A4:0.5 G4:1 E4:3 '
        'A4:2 A4:1 C5:1.5 B4:0.5 A4:1 G4:1.5 A4:0.5 G4:1 E4:3 '
        'D5:2 D5:1 F5:1.5 D5:0.5 B4:1 C5:3 E5:3 '
        'C5:1 G4:1 E4:1 G4:1.5 F4:0.5 D4:1 C4:6',
        'C3:3 C3:3 C3:3 C3:3 G2:3 G2:3 C3:3 C3:3 F2:3 F2:3 C3:3 C3:3 F2:3 F2:3 C3:3 C3:3 '
        'G2:3 G2:3 C3:3 C3:3 C3:3 G2:3 C3:6'),
    'jingle-bells': (120, (4, 4),
        # The chorus
        'E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:0.5 E4:4 '
        'F4:1 F4:1 F4:1.5 F4:0.5 F4:1 E4:1 E4:1 E4:0.5 E4:0.5 E4:1 D4:1 D4:1 E4:1 D4:2 G4:2 '
        'E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:0.5 E4:4 '
        'F4:1 F4:1 F4:1.5 F4:0.5 F4:1 E4:1 E4:1 E4:0.5 E4:0.5 G4:1 G4:1 F4:1 D4:1 C4:4',
        'C3:4 C3:4 C3:4 C3:4 F2:4 C3:4 G2:4 G2:4 C3:4 C3:4 C3:4 C3:4 F2:4 C3:4 G2:4 C3:4'),
    'saints': (110, (4, 4),
        'r:1 C4:1 E4:1 F4:1 G4:4 r:1 C4:1 E4:1 F4:1 G4:4 '
        'r:1 C4:1 E4:1 F4:1 G4:2 E4:2 C4:2 E4:2 D4:4 '
        'r:1 E4:1 E4:1 D4:1 C4:3 C4:1 E4:2 G4:2 G4:1 F4:3 '
        'r:1 E4:1 F4:1 G4:1 E4:2 C4:2 D4:4 C4:4',
        'C3:4 C3:4 C3:4 C3:4 C3:4 C3:4 C3:4 G2:4 C3:4 C3:4 F2:4 F2:4 C3:4 C3:4 G2:4 C3:4'),
    'oh-susanna': (100, (4, 4),
        # Pickup "I come from", verse, then the chorus
        'r:3 C4:0.5 D4:0.5 '
        'E4:1 G4:1 G4:1.5 A4:0.5 G4:1 E4:1 C4:1.5 D4:0.5 E4:1 E4:1 D4:1 C4:1 D4:3 C4:0.5 D4:0.5 '
        'E4:1 G4:1 G4:1.5 A4:0.5 G4:1 E4:1 C4:1.5 D4:0.5 E4:1 E4:1 D4:1 D4:1 C4:4 '
        'F4:2 A4:2 A4:2 G4:1 G4:1 E4:1 C4:1 D4:2 r:3 C4:0.5 D4:0.5 '
        'E4:1 G4:1 G4:1.5 A4:0.5 G4:1 E4:1 C4:1.5 D4:0.5 E4:1 E4:1 D4:1 D4:1 C4:4',
        'r:4 C3:4 C3:4 C3:4 G2:4 C3:4 C3:4 G2:4 C3:4 F2:4 C3:4 G2:4 G2:4 C3:4 C3:4 G2:4 C3:4'),
    'brahms-lullaby': (72, (3, 4),
        # Wiegenlied, Op. 49 No. 4 (1868); pickup of two eighths
        'E4:0.5 E4:0.5 '
        'G4:2 E4:0.5 E4:0.5 G4:2 E4:0.5 G4:0.5 C5:1 B4:1.5 A4:0.5 A4:1 G4:1 D4:0.5 E4:0.5 '
        'F4:1 D4:1 D4:0.5 E4:0.5 F4:2 D4:0.5 F4:0.5 B4:0.5 A4:0.5 G4:1 B4:1 C5:2 C4:0.5 C4:0.5 '
        'C5:2 A4:0.5 F4:0.5 G4:2 E4:0.5 C4:0.5 F4:1 G4:1 A4:1 G4:2 C4:0.5 C4:0.5 '
        'C5:2 A4:0.5 F4:0.5 G4:2 E4:0.5 C4:0.5 F4:1 E4:1 D4:1 C4:2',
        'r:1 C3:3 C3:3 C3:3 G2:3 G2:3 G2:3 G2:3 C3:3 F2:3 C3:3 F2:3 C3:3 F2:3 C3:3 G2:3 C3:2'),
    'frere-jacques': (100, (4, 4),
        # Twice through, the left hand moving the second time
        'C4:1 D4:1 E4:1 C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 F4:1 G4:2 E4:1 F4:1 G4:2 '
        'G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 C4:1 G3:1 C4:2 C4:1 G3:1 C4:2 '
        'C4:1 D4:1 E4:1 C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 F4:1 G4:2 E4:1 F4:1 G4:2 '
        'G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 C4:1 G3:1 C4:2 C4:1 G3:1 C4:2',
        'C3:4 C3:4 C3:4 C3:4 C3:4 C3:4 C3:4 C3:4 '
        'C3:2 G2:2 C3:2 G2:2 C3:2 G2:2 C3:2 G2:2 C3:2 G2:2 C3:2 G2:2 C3:2 G2:2 C3:4'),

    # --- Easy versions (2026-10-08): the main tune in the right hand, one bass note a bar in the left.
    'fur-elise-easy': (66, (3, 4),
        # The A section twice; sixteenths of the original written as eighths.
        'E5:0.5 D#5:0.5 '
        'E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 G#4:0.5 B4:0.5 C5:1.5 E4:0.5 E5:0.5 D#5:0.5 '
        'E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 C5:0.5 B4:0.5 A4:2 E5:0.5 D#5:0.5 '
        'E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 G#4:0.5 B4:0.5 C5:1.5 E4:0.5 E5:0.5 D#5:0.5 '
        'E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 C5:0.5 B4:0.5 A4:2',
        'r:1 '
        'r:3 A2:3 E2:3 A2:3 r:3 A2:3 E2:3 A2:3 '
        'r:3 A2:3 E2:3 A2:3 r:3 A2:3 E2:3 A2:2'),
    'minuet-g-easy': (88, (3, 4),
        # Petzold's A section with its two endings.
        'D5:1 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:1 G4:1 G4:1 E5:1 C5:0.5 D5:0.5 E5:0.5 F#5:0.5 G5:1 G4:1 G4:1 '
        'C5:1 D5:0.5 C5:0.5 B4:0.5 A4:0.5 B4:1 C5:0.5 B4:0.5 A4:0.5 G4:0.5 F#4:1 G4:0.5 A4:0.5 B4:0.5 G4:0.5 A4:3 '
        'D5:1 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:1 G4:1 G4:1 E5:1 C5:0.5 D5:0.5 E5:0.5 F#5:0.5 G5:1 G4:1 G4:1 '
        'C5:1 D5:0.5 C5:0.5 B4:0.5 A4:0.5 B4:1 C5:0.5 B4:0.5 A4:0.5 G4:0.5 A4:1 B4:0.5 A4:0.5 G4:0.5 F#4:0.5 G4:3',
        'G2:3 B2:3 C3:3 B2:3 A2:3 G2:3 D3:3 D2:3 G2:3 B2:3 C3:3 B2:3 A2:3 G2:3 D3:3 G2:3'),
    'greensleeves-easy': (96, (3, 4),
        # Verse and refrain, pickup "A-".
        'A4:1 '
        'C5:2 D5:1 E5:1.5 F5:0.5 E5:1 D5:2 B4:1 G4:1.5 A4:0.5 B4:1 C5:2 A4:1 A4:1.5 G#4:0.5 A4:1 B4:2 G#4:1 E4:2 A4:1 '
        'C5:2 D5:1 E5:1.5 F5:0.5 E5:1 D5:2 B4:1 G4:1.5 A4:0.5 B4:1 C5:1.5 B4:0.5 A4:1 G#4:1.5 F#4:0.5 G#4:1 A4:3 '
        'G5:3 G5:1.5 F#5:0.5 E5:1 D5:2 B4:1 G4:1.5 A4:0.5 B4:1 C5:2 A4:1 A4:1.5 G#4:0.5 A4:1 B4:2 G#4:1 E4:3 '
        'G5:3 G5:1.5 F#5:0.5 E5:1 D5:2 B4:1 G4:1.5 A4:0.5 B4:1 C5:1.5 B4:0.5 A4:1 G#4:1.5 F#4:0.5 G#4:1 A4:3',
        'r:1 '
        'A2:3 C3:3 G2:3 E2:3 A2:3 E2:3 E2:3 E2:3 A2:3 C3:3 G2:3 E2:3 A2:3 E2:3 A2:3 '
        'C3:3 C3:3 G2:3 E2:3 A2:3 E2:3 E2:3 E2:3 C3:3 C3:3 G2:3 E2:3 A2:3 E2:3 A2:3'),
}

if __name__ == '__main__':
    out = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'songs')
    only = set(sys.argv[1:])            # ids to write; all when none given
    for sid, (tempo, ts, rh, lh) in SONGS.items():
        if only and sid not in only:
            continue
        rb = sum(float(t.split(':')[1]) for t in rh.split())
        lb = sum(float(t.split(':')[1]) for t in lh.split())
        assert rb == lb, f'{sid}: right hand {rb} beats, left hand {lb}'
        write(os.path.join(out, sid + '.mid'), tempo, ts, rh, lh)
        print(sid, rb, 'beats')
