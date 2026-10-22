package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// Country. Chords only (docs/CONTENT.md). Left out, sources disagreeing: Man! I Feel Like a Woman!, Whiskey Glasses,
// Beautiful Crazy (meter and bar grouping unsettled); Fast Car (Luke Combs), Last Night, Something in the Orange (key
// unsure).

internal val COUNTRY_SONGS: List<PopSong> = listOf(
    pop("ring-of-fire", "Ring of Fire", "Johnny Cash", 1963, listOf(COUNTRY, GREATEST_HITS), "G", 210, QUARTERS, 1, LIKELY,
        "Counted in quick twos with three chords: the C and D come and go fast, so keep counting.",
        "Intro, Verse, Chorus, Interlude x2, Chorus, Verse, Chorus x2, Outro x2",
        """
        Intro: G | G | C | G | G | G | G | G | G | D | G | G | G | G
        Verse: G | G | G | C | G | G | C | G | G | G | G | G | G | G | D | G | G | D | G | G | G | G | G | G | G | C | G | G | C | G | G | G | G | G | G | D | G | G | G | G
        Chorus: D | C | C | G | G | G | G | D | C | C | G | G | G | G | G | G | G | D | G | G | G | D | G | G | G | G
        Interlude: G | G | C | G | G | G | G | G | G | D | G | G | G | G
        Outro: G | D | G | G | G | D | G | G
        """, beatsPerBar = 2),
    pop("folsom-prison-blues", "Folsom Prison Blues", "Johnny Cash", 1955, listOf(COUNTRY, GREATEST_HITS), "F", 204, QUARTERS, 1, LIKELY,
        "Twenty four bars in F over a fast train beat: F, B flat, F, C7, F.",
        "Intro, Verse x2, Solo, Verse, Solo, Verse, Outro",
        """
        Intro: C7 | C7 | F | F
        Verse: (F) x8 | (Bb) x4 | (F) x4 | (C7) x4 | (F) x4
        Solo: (F) x8 | (Bb) x4 | (F) x4 | (C7) x4 | (F) x4
        Outro: C7 | C7 | F
        """),
    pop("youre-still-the-one", "You're Still the One", "Shania Twain", 1998, listOf(COUNTRY, BALLAD), "Eb", 67, BALLAD_ARPEGGIO, 2, SKETCH,
        "E flat with the bass stepping up to G under the same chord, then A flat and B flat.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Solo, Chorus, Outro",
        """
        Intro: (Eb Eb/G | Ab Bb) x2
        Verse: (Eb Eb/G | Ab Bb) x2
        Pre-chorus: Eb Eb/G | Ab Bb | Eb | Ab Bb | Eb | Ab Bb
        Chorus: Eb Ab | Fm Bb | Eb Ab | Bb Ab | Eb Ab | Fm Bb | Eb Ab | Bb
        Solo: Eb Ab | Fm Bb | Eb Ab | Bb
        Outro: (Eb Eb/G | Ab Bb) x2 | Eb
        """),
    pop("tennessee-whiskey", "Tennessee Whiskey", "Chris Stapleton", 2015, listOf(COUNTRY, SOUL), "A", 49, BALLAD_ARPEGGIO, 1, LIKELY,
        "Two chords, A and B minor, in a slow triplet four; the bass walks up between them.",
        "Intro, Verse, Chorus, Verse, Chorus, Solo, Chorus x2, Outro",
        """
        Intro: A | Bm | Bm | A
        Verse: (A | Bm | Bm | A) x2
        Chorus: (A | Bm | Bm | A) x2
        Solo: (A | Bm | Bm | A) x2
        Outro: A | Bm | Bm | A
        """),
    pop("wagon-wheel", "Wagon Wheel", "Darius Rucker", 2013, listOf(COUNTRY), "A", 148, QUARTERS, 1, LIKELY,
        "I, V, vi, IV in A, two bars each; the second line skips the minor chord.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Interlude x2, Verse, Chorus x2, Outro",
        """
        Intro: A | A | E | E | F#m | F#m | D | D | A | A | E | E | D | D | D | D
        Verse: (A | A | E | E | F#m | F#m | D | D | A | A | E | E | D | D | D | D) x2
        Chorus: (A | A | E | E | F#m | F#m | D | D | A | A | E | E | D | D | D | D) x2
        Interlude: A | A | E | E | F#m | F#m | D | D | A | A | E | E | D | D | D | D
        Outro: A | A | E | E | F#m | F#m | D | D | A | A | E | E | D | D | D | D | A
        """),
    pop("friends-in-low-places", "Friends in Low Places", "Garth Brooks", 1990, listOf(COUNTRY), "A", 108, QUARTERS, 3, SKETCH,
        "A with a passing B flat diminished 7 up to B minor 7, a borrowed D minor, and an A7 that leans toward D.",
        "Intro, Verse, Chorus, Solo, Verse, Chorus x2, Outro",
        """
        Intro: A | Bbdim7 | Bm7 | E
        Verse: A | Bbdim7 | Bm7 | Bm7 | E | E7 | A | A | A | Bbdim7 | Bm7 | Dm | E | E7 | A | A
        Chorus: A | A | Bm7 | E | A | A | A7 | Bm7 E | A | A
        Solo: A | A | A | A | Bm7 | E | A | A
        Outro: Bm7 | E | A
        """),
    pop("jolene", "Jolene", "Dolly Parton", 1973, listOf(COUNTRY, GREATEST_HITS), "C#m", 111, BROKEN, 1, LIKELY,
        "Minor and quick: four chords that never leave C sharp minor's own scale.",
        "Intro, Chorus, Verse x2, Chorus, Verse, Chorus, Outro",
        """
        Intro: C#m | C#m | C#m | C#m
        Chorus: C#m | E | B | C#m | B | B | C#m | C#m | C#m | E | B | C#m | B | G#m | C#m | C#m
        Verse: (C#m | E | B | C#m | B | G#m | C#m | C#m) x2
        Outro: C#m | C#m
        """),
)
