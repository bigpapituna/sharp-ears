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


def track(evts, chan, name, tempo=None, ts=None, marks=()):
    msgs = []
    if tempo:
        msgs.append((0, b'\xff\x51\x03' + int(60e6 / tempo).to_bytes(3, 'big')))
    if ts:
        msgs.append((0, b'\xff\x58\x04' + bytes([ts[0], {2: 1, 4: 2, 8: 3}[ts[1]], 24, 8])))
    msgs.append((0, b'\xff\x03' + vlq(len(name)) + name.encode()))
    for tick, label in marks:                      # section markers (SmfParser turns them into Song.sections)
        msgs.append((tick, b'\xff\x06' + vlq(len(label)) + label.encode('latin-1')))
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


def write(path, tempo, ts, rh, lh, marks=()):
    data = b'MThd' + struct.pack('>IHHH', 6, 1, 2, PPQ)
    data += track(events(rh), 0, 'right', tempo, ts, marks) + track(events(lh), 1, 'left')
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

# --- Batch 4 (2026-10-13), Sharp Ears 2.0 content: public-domain tunes for the Repertoire levels of the course.
# Every tune is written from memory in the token notation above. Where a piece is only an opening or a skeleton the
# title says so, and docs/CONTENT.md names what was simplified. Left hands are built from chord symbols by lh().
# Sections: the index format has no sections field, so each song's section list is written into the right-hand track
# as MIDI marker events (SmfParser reads them into Song.sections) and also listed in index.json under "sections"
# (name + bar number; ignored by the loader, there for people and for the tests).

SHARP = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
PC = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def tok(m):
    return f'{SHARP[m % 12]}{m // 12 - 1}'


def g(x):
    return f'{x:g}'


def chord_notes(sym):
    """Root in G2..F#3, then the third and the fifth above it (midi numbers)."""
    pc = PC[sym[0]]
    i = 1
    while i < len(sym) and sym[i] in '#b':
        pc += 1 if sym[i] == '#' else -1
        i += 1
    minor = sym[i:i + 1] == 'm'
    r = 36 + pc % 12
    if r < 43:
        r += 12
    return r, r + (3 if minor else 4), r + 7


def bar_tokens(sym, pattern, beats):
    """One bar of left hand for chord symbol sym ('C', 'Am', 'C/G' = two chords, half a bar each)."""
    if '/' in sym:
        a, b = sym.split('/')
        p = pattern if pattern == 'arp8' else 'root'
        return bar_tokens(a, p, beats / 2) + bar_tokens(b, p, beats / 2)
    r, t, f = chord_notes(sym)
    tf = f'{tok(t)}+{tok(f)}'
    if pattern == 'root':
        return [f'{tok(r)}:{g(beats)}']
    if pattern == 'half':
        return [f'{tok(r)}:{g(beats / 2)}', f'{tok(f)}:{g(beats / 2)}']
    if pattern == 'pah':                      # root, then the chord held
        return [f'{tok(r)}:1', f'{tf}:{g(beats - 1)}']
    if pattern == 'waltz':                    # oom pah pah
        return [f'{tok(r)}:1'] + [f'{tf}:1'] * int(beats - 1)
    if pattern == 'oompah4':                  # bass, chord, fifth below, chord
        return [f'{tok(r)}:1', f'{tf}:1', f'{tok(r - 5)}:1', f'{tf}:1']
    if pattern == 'lilt':                     # 6/8: bass, chord
        return [f'{tok(r)}:1.5', f'{tf}:1.5']
    if pattern == 'arp8':                     # eighths: root, fifth, third, fifth
        cyc = [r, f, t, f]
        return [f'{tok(cyc[k % 4])}:0.5' for k in range(int(beats * 2))]
    raise ValueError(pattern)


def lh(beats, *groups):
    """Left hand from (pattern, [chord per bar]) groups, played one after the other."""
    return ' '.join(' '.join(bar_tokens(c, pat, beats)) for pat, chords in groups for c in chords)


def bars(*b):
    return ' '.join(b)


SECTIONS = {}      # id -> [(bar, name)]; bar 1 is the first full bar (a pickup before it belongs to bar 1)
PICKUPS = {}       # id -> beats before bar 1
META = {}          # id -> dict(title, composer, difficulty, source)

TRAD = 'Arrangement written for Sharp Ears (melody and bass); the tune is in the public domain'


def add(sid, title, composer, difficulty, tempo, ts, rh, lhs, sections, pickup=0, source=None):
    SONGS[sid] = (tempo, ts, rh, lhs)
    SECTIONS[sid] = sections
    PICKUPS[sid] = pickup
    META[sid] = dict(title=title, composer=composer, difficulty=difficulty, source=source or TRAD)


def classical(name, died):
    return f'Simplified arrangement written for Sharp Ears; the music is in the public domain ({name} died in {died})'


C8 = ['C', 'C', 'G', 'C', 'C', 'C', 'G', 'C']

add('mary-had-a-little-lamb', 'Mary Had a Little Lamb', 'Traditional (American)', 'easy', 100, (4, 4),
    ' '.join([bars('E4:1 D4:1 C4:1 D4:1', 'E4:1 E4:1 E4:2', 'D4:1 D4:1 D4:2', 'E4:1 G4:1 G4:2',
                   'E4:1 D4:1 C4:1 D4:1', 'E4:1 E4:1 E4:1 E4:1', 'D4:1 D4:1 E4:1 D4:1', 'C4:4')] * 2),
    lh(4, ('root', C8), ('half', C8)),
    [(1, 'First verse'), (9, 'Second verse')])

HCB = bars('E4:1 D4:1 C4:2', 'E4:1 D4:1 C4:2', 'C4:0.5 C4:0.5 C4:0.5 C4:0.5 D4:0.5 D4:0.5 D4:0.5 D4:0.5', 'E4:1 D4:1 C4:2')
add('hot-cross-buns', 'Hot Cross Buns', 'Traditional (English)', 'easy', 96, (4, 4),
    HCB + ' ' + HCB,
    lh(4, ('root', ['C', 'C', 'C/G', 'C']), ('oompah4', ['C', 'C', 'C/G', 'C'])),
    [(1, 'First time'), (5, 'Second time')])

ROW = bars('C4:1.5 C4:1.5', 'C4:1 D4:0.5 E4:1.5', 'E4:1 D4:0.5 E4:1 F4:0.5', 'G4:3',
           'C5:0.5 C5:0.5 C5:0.5 G4:0.5 G4:0.5 G4:0.5', 'E4:0.5 E4:0.5 E4:0.5 C4:0.5 C4:0.5 C4:0.5',
           'G4:1 F4:0.5 E4:1 D4:0.5', 'C4:3')
ROWC = ['C', 'C', 'C', 'C', 'C', 'C', 'G', 'C']
# 6/8 is counted here in quarter notes: one bar is three beats, and the metronome clicks every eighth.
add('row-row-row-your-boat', 'Row, Row, Row Your Boat', 'Traditional', 'easy', 90, (6, 8),
    ROW + ' ' + ROW, lh(3, ('root', ROWC), ('lilt', ROWC)),
    [(1, 'Row the boat'), (5, 'Merrily'), (9, 'Again'), (13, 'Merrily again')])

LON = bars('G4:0.75 A4:0.25 G4:0.5 F4:0.5', 'E4:0.5 F4:0.5 G4:1', 'D4:0.5 E4:0.5 F4:1', 'E4:0.5 F4:0.5 G4:1',
           'G4:0.75 A4:0.25 G4:0.5 F4:0.5', 'E4:0.5 F4:0.5 G4:1', 'D4:1 G4:1', 'E4:1 C4:1')
add('london-bridge', 'London Bridge', 'Traditional (English)', 'easy', 108, (2, 4),
    LON + ' ' + LON, lh(2, ('root', C8), ('pah', C8[:7]), ('root', ['C'])),
    [(1, 'First time'), (9, 'Second time')])

YA = bars('C4:1 C4:1 D4:1 E4:1', 'C4:1 E4:1 D4:1 G3:1', 'C4:1 C4:1 D4:1 E4:1', 'C4:1 B3:3')
YC = bars('C4:1 C4:1 D4:1 E4:1', 'F4:1 E4:1 D4:1 C4:1', 'B3:1 G3:1 A3:1 B3:1', 'C4:1 C4:3')
add('yankee-doodle', 'Yankee Doodle', 'Traditional (American)', 'easy', 116, (4, 4),
    ' '.join([YA, YA, YC, YC]),
    lh(4, ('root', ['C', 'C', 'C', 'C/G']), ('half', ['C', 'C', 'C', 'C/G']),
       ('oompah4', ['C', 'F/G', 'G', 'C']), ('oompah4', ['C', 'F/G', 'G', 'C'])),
    [(1, 'Verse'), (5, 'Verse again'), (9, 'Chorus'), (13, 'Chorus again')])

OM1 = 'C4:1 C4:1 C4:1 G3:1'
OM2 = 'A3:1 A3:1 G3:2'
OM3 = 'E4:1 E4:1 D4:1 D4:1'
OMC = ['C', 'F/C', 'C/G', 'C']
add('old-macdonald', 'Old MacDonald Had a Farm', 'Traditional', 'easy', 108, (4, 4),
    bars(OM1, OM2, OM3, 'C4:3 G3:1', OM1, OM2, OM3, 'C4:4', OM1, OM2, OM3, 'C4:4'),
    lh(4, ('root', OMC), ('half', OMC), ('oompah4', OMC)),
    [(1, 'Old MacDonald'), (5, 'And on his farm'), (9, 'Last time')])

HA = bars('C4:0.5 C4:0.5 E4:0.5 E4:0.5', 'G4:0.5 G4:0.5 E4:1', 'F4:0.5 F4:0.5 D4:0.5 D4:0.5', 'B3:0.5 B3:0.5 G3:1')
HC = ['C', 'C', 'G', 'G']
add('haydn-surprise', 'Surprise Symphony (theme)', 'Joseph Haydn', 'easy', 84, (2, 4),
    ' '.join([HA, HA, HA, 'C4+E4+G4:2']),
    lh(2, ('pah', HC), ('arp8', HC), ('pah', HC), ('root', ['C'])),
    [(1, 'Theme'), (5, 'Second time'), (9, 'The surprise'), (13, 'Ending')],
    source=classical('Haydn', 1809))

AU = bars('C4:1 C4:1 C4:1 D4:1', 'E4:2 D4:2', 'C4:1 E4:1 D4:1 D4:1', 'C4:4')
AUC = ['C', 'C/G', 'C/G', 'C']
add('au-clair-de-la-lune', 'Au clair de la lune', 'Traditional (French)', 'easy', 84, (4, 4),
    AU + ' ' + AU, lh(4, ('root', AUC), ('oompah4', AUC)),
    [(1, 'First time'), (5, 'Second time')])

LRA = bars('G4:1 E4:1 E4:2', 'F4:1 D4:1 D4:2', 'C4:1 D4:1 E4:1 F4:1', 'G4:1 G4:1 G4:2')
LRA2 = bars('G4:1 E4:1 E4:2', 'F4:1 D4:1 D4:2', 'C4:1 E4:1 G4:1 G4:1', 'C4:4')
LRB = bars('D4:1 D4:1 D4:1 D4:1', 'D4:1 E4:1 F4:2', 'E4:1 E4:1 E4:1 E4:1', 'E4:1 F4:1 G4:2')
add('lightly-row', 'Lightly Row', 'Traditional (German)', 'medium', 100, (4, 4),
    ' '.join([LRA, LRA2, LRB, LRA2]),
    lh(4, ('half', ['C', 'G', 'C', 'G', 'C', 'G', 'C', 'C']), ('half', ['G', 'G', 'C', 'G']),
       ('arp8', ['C', 'G', 'C', 'C'])),
    [(1, 'First phrase'), (5, 'Second phrase'), (9, 'Middle'), (13, 'Last phrase')])

WW = bars('G4:1 G4:0.5 A4:0.5 G4:0.5 F#4:0.5', 'E4:1 E4:1 E4:1', 'A4:1 A4:0.5 B4:0.5 A4:0.5 G4:0.5', 'F#4:1 D4:1 D4:1',
          'B4:1 B4:0.5 C5:0.5 B4:0.5 A4:0.5', 'G4:1 E4:1 D4:0.5 D4:0.5', 'E4:1 A4:1 F#4:1')
WWC = ['G', 'C', 'D', 'D', 'G', 'C', 'D', 'G']
add('we-wish-you-a-merry-christmas', 'We Wish You a Merry Christmas', 'Traditional (English)', 'medium', 112, (3, 4),
    'D4:1 ' + WW + ' G4:2 D4:1 ' + WW + ' G4:3',
    'r:1 ' + lh(3, ('pah', WWC), ('waltz', WWC[:7]), ('root', ['G'])),
    [(1, 'First time'), (9, 'Second time')], pickup=1)

EK1 = bars('G4:1 r:0.5 D4:0.5 G4:1 r:0.5 D4:0.5', 'G4:0.5 D4:0.5 G4:0.5 B4:0.5 D5:2',
           'C5:1 r:0.5 A4:0.5 C5:1 r:0.5 A4:0.5', 'C5:0.5 A4:0.5 F#4:0.5 A4:0.5 D5:2')
EK2 = bars('G4:1 r:0.5 D4:0.5 G4:1 r:0.5 D4:0.5', 'G4:0.5 D4:0.5 G4:0.5 B4:0.5 D5:2',
           'D5:1 B4:1 G4:1 D4:1', 'G4:4')
add('eine-kleine-nachtmusik', 'Eine kleine Nachtmusik (opening)', 'Wolfgang Amadeus Mozart', 'medium', 112, (4, 4),
    EK1 + ' ' + EK2,
    lh(4, ('half', ['G', 'G', 'D', 'D', 'G', 'G', 'G', 'G'])),
    [(1, 'Opening'), (5, 'Answer')], source=classical('Mozart', 1791))

GY_A = 'G2:1 B3+D4+F#4:2'
GY_B = 'D3:1 A3+C#4+F#4:2'
add('gymnopedie-1', 'Gymnopedie No. 1 (opening)', 'Erik Satie', 'medium', 66, (3, 4),
    'r:12 F#5:3 A5:3 G5:3 F#5:3 C#5:3 B4:3 C#5:3 D5:3',
    ' '.join([GY_A, GY_B] * 6),
    [(1, 'Introduction'), (5, 'Melody')], source=classical('Satie', 1925))

MK = bars('B3:0.5 C#4:0.5 D4:0.5 E4:0.5 F#4:0.5 D4:0.5 F#4:1', 'F4:0.5 C#4:0.5 F4:1 r:2',
          'B3:0.5 C#4:0.5 D4:0.5 E4:0.5 F#4:0.5 D4:0.5 F#4:1', 'B4:1 A4:0.5 F#4:0.5 D4:0.5 F#4:0.5 A4:1')
MK_UP = bars('B4:0.5 C#5:0.5 D5:0.5 E5:0.5 F#5:0.5 D5:0.5 F#5:1', 'F5:0.5 C#5:0.5 F5:1 r:2',
             'B4:0.5 C#5:0.5 D5:0.5 E5:0.5 F#5:0.5 D5:0.5 F#5:1', 'B5:1 A5:0.5 F#5:0.5 D5:0.5 F#5:0.5 B4:1')
MK_B = 'B2:1 F#3:1 B2:1 F#3:1'
MK_V = 'F#2:1 C#3:1 F#2:1 C#3:1'
add('hall-of-the-mountain-king', 'In the Hall of the Mountain King (theme)', 'Edvard Grieg', 'hard', 104, (4, 4),
    MK + ' ' + MK_UP, ' '.join([MK_B, MK_V, MK_B, MK_B] * 2),
    [(1, 'Creeping in'), (5, 'Higher')], source=classical('Grieg', 1907))

RT = bars('B4:0.25 A4:0.25 G#4:0.25 A4:0.25 C5:1', 'D5:0.25 C5:0.25 B4:0.25 C5:0.25 E5:1',
          'F5:0.25 E5:0.25 D#5:0.25 E5:0.25 B5:0.25 A5:0.25 G#5:0.25 A5:0.25', 'B5:0.25 A5:0.25 G#5:0.25 A5:0.25 C6:1')
RT_L = 'A2:0.5 C3+E3:0.5 A2:0.5 C3+E3:0.5'
RT_E = 'E2:0.5 G#2+B2:0.5 E2:0.5 G#2+B2:0.5'
add('rondo-alla-turca', 'Rondo alla Turca (theme)', 'Wolfgang Amadeus Mozart', 'hard', 100, (2, 4),
    RT + ' ' + RT + ' A5:2',
    ' '.join([RT_L, RT_L, RT_E, RT_L] * 2) + ' A2+A3:2',
    [(1, 'Theme'), (5, 'Theme again'), (9, 'Last note')], source=classical('Mozart', 1791))

add('bach-toccata-opening', 'Toccata in D minor (opening)', 'Johann Sebastian Bach', 'medium', 60, (4, 4),
    bars('A5:0.25 G5:0.25 A5:3.5', 'G5:0.25 F5:0.25 E5:0.25 D5:0.25 C#5:0.25 D5:2.75',
         'A4:0.25 G4:0.25 A4:3.5', 'G4:0.25 F4:0.25 E4:0.25 D4:0.25 C#4:0.25 D4:2.75',
         'C#4+E4+G4+Bb4:4', 'D4+F4+A4+D5:4'),
    'r:16 D2+A2:4 D2+A2+D3:4',
    [(1, 'First statement'), (3, 'Echo, an octave lower'), (5, 'Chords')], source=classical('Bach', 1750))

NEW_IDS = list(META)


def bar_beats(ts):
    return ts[0] * 4 / ts[1]


def markers(sid, ts):
    pick, bb = PICKUPS[sid], bar_beats(ts)
    return [(round((0 if (bar == 1 and pick) else pick + (bar - 1) * bb) * PPQ), name) for bar, name in SECTIONS[sid]]


def check_bars(sid, ts, spec, hand):
    """New songs are whole bars (plus the pickup) and no note crosses a bar line (the pickup may be a bar's end)."""
    bb, pick = bar_beats(ts), PICKUPS[sid]
    t = 0.0
    for tk in spec.split():
        n = float(tk.split(':')[1])
        if t >= pick - 1e-9 and not tk.startswith('r:'):     # rests may span bars
            edge = pick + (int((t - pick + 1e-9) // bb) + 1) * bb
            assert t + n <= edge + 1e-9, f'{sid} {hand}: {tk} crosses a bar line at beat {t}'
        t += n
    assert abs((t - pick) / bb - round((t - pick) / bb)) < 1e-9, f'{sid} {hand}: {t - pick} beats after the pickup is not whole bars'


def update_index(path):
    """Add (or refresh) the batch-4 entries in index.json, just before the imported-song entries."""
    import json
    data = json.load(open(path, encoding='utf-8'))
    have = {s['id'] for s in data['songs']}
    at = next(i for i, s in enumerate(data['songs']) if s['file'].startswith('user/'))
    for sid in NEW_IDS:
        m = META[sid]
        entry = dict(id=sid, file=sid + '.mid', title=m['title'], composer=m['composer'], difficulty=m['difficulty'],
                     hands='auto', license='Public Domain', attribution=m['source'], videos=[],
                     sections=[dict(name=n, bar=b) for b, n in SECTIONS[sid]])
        if sid in have:
            data['songs'] = [entry if s['id'] == sid else s for s in data['songs']]
        else:
            data['songs'].insert(at, entry)
            at += 1
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')



if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'songs')
    only = {a for a in sys.argv[1:] if not a.startswith('--')}     # ids to write; all when none given
    for sid, (tempo, ts, rh, lhs) in SONGS.items():
        if only and sid not in only:
            continue
        rb = sum(float(t.split(':')[1]) for t in rh.split())
        lb = sum(float(t.split(':')[1]) for t in lhs.split())
        assert rb == lb, f'{sid}: right hand {rb} beats, left hand {lb}'
        marks = ()
        if sid in META:
            check_bars(sid, ts, rh, 'right')
            check_bars(sid, ts, lhs, 'left')
            marks = markers(sid, ts)
        write(os.path.join(root, sid + '.mid'), tempo, ts, rh, lhs, marks)
        print(sid, rb, 'beats')
    if '--index' in sys.argv:        # add the batch-4 entries to index.json
        update_index(os.path.join(root, 'index.json'))
