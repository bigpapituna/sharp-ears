package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// Hymns and spirituals, all public domain tunes, with the plain harmony most hymnals and songbooks give them (a
// traditional tune has no one recording, so the key is the usual songbook key). Silent Night, in the family file, is
// a hymn too. Chords only (docs/CONTENT.md).

internal val WORSHIP_SONGS: List<PopSong> = listOf(
    pop("amazing-grace", "Amazing Grace", "Traditional (New Britain)", 1835, listOf(WORSHIP), "G", 76, BALLAD_ARPEGGIO, 1, LIKELY,
        "A slow waltz on three chords: G, C, and D7 at the half way point and again before the last bar.",
        "Verse x3",
        """
        Verse: G | G | C | G | G | G | D | D7 | G | G | C | G | G | D7 | G | G
        """, beatsPerBar = 3),
    pop("when-the-saints", "When the Saints Go Marching In", "Traditional", 1896, listOf(WORSHIP, KIDS), "C", 112, QUARTERS, 1, LIKELY,
        "Six bars of C before the first change; C7 is a secondary dominant that leans into F.",
        "Verse x3",
        """
        Verse: C | C | C | C | C | C | G7 | G7 | C | C7 | F | F | C | G7 | C | C
        """),
    pop("whole-world-in-his-hands", "He's Got the Whole World in His Hands", "Traditional", 1927, listOf(WORSHIP, KIDS), "C", 100, QUARTERS, 1, LIKELY,
        "Two chords only: two bars of C, two of G7, and so on, landing on C at the end.",
        "Verse x3",
        """
        Verse: C | C | G7 | G7 | C | C | G7 | C
        """),
    pop("jesus-loves-me", "Jesus Loves Me", "William B. Bradbury", 1862, listOf(WORSHIP, KIDS), "C", 96, QUARTERS, 1, SKETCH,
        "The verse alone, on C, F and G: a gentle first hymn for both hands.",
        "Verse x2",
        """
        Verse: C | C | F | C | C | C | G | C
        """),
    pop("kumbaya", "Kumbaya", "Traditional", 1927, listOf(WORSHIP, KIDS), "C", 80, BALLAD_ARPEGGIO, 1, SKETCH,
        "C, F and G in a slow sway; the F comes and goes inside a bar, so let it ring into the C.",
        "Verse x3",
        """
        Verse: C | F C | C | G | C | F C | F C | G C
        """),
)
