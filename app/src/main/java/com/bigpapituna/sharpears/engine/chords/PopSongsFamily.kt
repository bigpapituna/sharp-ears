package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// Kids and holiday songs for the family. The traditional tunes are public domain and carry their standard chords
// (one verse, the usual harmony); the newer ones (Last Christmas, Feliz Navidad) are chords only, like everything else
// here (docs/CONTENT.md).

internal val FAMILY_SONGS: List<PopSong> = listOf(
    pop("twinkle-twinkle", "Twinkle, Twinkle, Little Star", "Traditional", 1806, listOf(KIDS), "C", 100, QUARTERS, 1, CHECKED,
        "C, F and G, often two chords a bar: the easiest song to start with.",
        "Verse, Bridge, Verse",
        """
        Verse: C | F C | F C | G C
        Bridge: C F | C G | C F | C G
        """),
    pop("mary-had-a-little-lamb", "Mary Had a Little Lamb", "Traditional", 1830, listOf(KIDS), "C", 100, QUARTERS, 1, CHECKED,
        "Only C and G: the G arrives in bar three and again in bar seven.",
        "Verse x2",
        """
        Verse: C | C | G | C | C | C | G | C
        """),
    pop("row-row-row-your-boat", "Row, Row, Row Your Boat", "Traditional", 1852, listOf(KIDS), "C", 60, WHOLE, 1, CHECKED,
        "One chord until the last line, where G turns it home; counted in a lilting two.",
        "Verse x2",
        """
        Verse: C | C | C | C | C | C | G | C
        """, beatsPerBar = 2),
    pop("old-macdonald", "Old MacDonald Had a Farm", "Traditional", 1917, listOf(KIDS), "G", 110, QUARTERS, 1, CHECKED,
        "G for most of it, with a quick C and D on the way to each landing.",
        "Verse x3",
        """
        Verse: G | C G | G D | G | G | C G | G D | G | G | G | G | G | G | C G | G D | G
        """),
    pop("if-youre-happy", "If You're Happy and You Know It", "Traditional", 1916, listOf(KIDS), "C", 108, QUARTERS, 1, LIKELY,
        "C, then G, then F for the third line, and back home to C.",
        "Verse x3",
        """
        Verse: C | C | C | G | G | G | G | C | F | F | C | C | G | G | C | C
        """),
    pop("wheels-on-the-bus", "The Wheels on the Bus", "Traditional", 1939, listOf(KIDS), "C", 112, QUARTERS, 1, LIKELY,
        "C with G on the third bar and the seventh, like most nursery tunes.",
        "Verse x4",
        """
        Verse: C | C | G | C | C | C | G | C
        """),
    pop("happy-birthday", "Happy Birthday to You", "Mildred J. Hill and Patty Hill", 1893, listOf(KIDS), "C", 100, QUARTERS, 1, CHECKED,
        "Waltz time in C; F only shows up once, in the third line.",
        "Verse",
        """
        Verse: C | G | G | C | C | F | C:2 G:1 | C
        """, beatsPerBar = 3),
    pop("jingle-bells", "Jingle Bells", "James Lord Pierpont", 1857, listOf(HOLIDAY, KIDS), "G", 120, QUARTERS, 2, LIKELY,
        "Bright G major; the A7 before D7 in the chorus is a secondary dominant, the five of D.",
        "Verse, Chorus, Verse, Chorus",
        """
        Verse: G | G | G | C | C | D | D7 | G | G | G | G | C | C | D | D7 | G
        Chorus: G | G | G | G | C | G | A7 | D7 | G | G | G | G | C | G | D7 | G
        """),
    pop("silent-night", "Silent Night", "Franz Xaver Gruber", 1818, listOf(HOLIDAY, WORSHIP), "C", 90, BALLAD_ARPEGGIO, 1, CHECKED,
        "A slow waltz with three chords; the F lines sit in the middle like a hymn.",
        "Verse x3",
        """
        Verse: C | C | C | C | G7 | G7 | C | C | F | F | C | C | F | F | C | C | G7 | G7 | C | C | C | G7 | C | C
        """, beatsPerBar = 3),
    pop("we-wish-you-a-merry-christmas", "We Wish You a Merry Christmas", "Traditional", 1935, listOf(HOLIDAY, KIDS), "G", 112, QUARTERS, 2, SKETCH,
        "A7 and B7 are secondary dominants, each one pushing to the chord after it.",
        "Verse x2",
        """
        Verse: G | C | A7 | D | B7 | Em | C:2 D:1 | G
        """, beatsPerBar = 3),
    pop("last-christmas", "Last Christmas", "Wham!", 1984, listOf(HOLIDAY, POP, EIGHTIES_ROCK), "D", 107, QUARTERS, 1, LIKELY,
        "Two bars on each of D, B minor, E minor and A, all the way through.",
        "Intro, Chorus, Interlude, Verse, Chorus, Interlude, Verse, Chorus, Outro",
        """
        Intro: D | D | Bm | Bm | Em | Em | A | A
        Chorus: (D | D | Bm | Bm | Em | Em | A | A) x2
        Interlude: D | D | Bm | Bm | Em | Em | A | A
        Verse: (D | D | Bm | Bm | Em | Em | A | A) x2
        Outro: D | D | Bm | Bm | Em | Em | A | A | D
        """),
    pop("feliz-navidad", "Feliz Navidad", "José Feliciano", 1970, listOf(HOLIDAY), "D", 148, QUARTERS, 2, SKETCH,
        "Four, five, one over and over in D, with one B minor for colour.",
        "Chorus x2, Verse x2, Chorus x2, Verse x2, Chorus x2, Verse x2",
        """
        Chorus: G A | D | G A | D | G A | D Bm | G A | D
        Verse: G A | D | G A | D | G A | D Bm | G A | D
        """),
)
