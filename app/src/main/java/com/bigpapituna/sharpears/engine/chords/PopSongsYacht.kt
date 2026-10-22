package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// Yacht rock: smooth sevenths, slash chords and borrowed colours. Chords only (docs/CONTENT.md). Charts that sources
// still argue over were left out (Sailing, What a Fool Believes, Summer Breeze, Lowdown, Lido Shuffle, Africa), and
// two whose key would not settle (Baby Come Back, Footloose).

internal val YACHT_SONGS: List<PopSong> = listOf(
    pop("do-it-again", "Do It Again", "Steely Dan", 1972, listOf(YACHT_ROCK), "Gm", 125, QUARTERS, 2, SKETCH,
        "A long vamp on G minor 7, then a chorus that climbs through C minor 7 and D minor 7 to E flat major 7.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Solo, Verse, Chorus, Outro",
        """
        Intro: (Gm7) x4
        Verse: (Gm7) x16
        Chorus: (Cm7 Dm7 | Ebmaj7 Dm7 | Gm7 | Gm7) x2
        Interlude: (Gm7) x4
        Solo: (Gm7) x20 | Cm7 Dm7 | Ebmaj7 Dm7 | Gm7 | Gm7 | Cm7 Dm7 | Ebmaj7 Dm7 | Gm7 | Gm7 Dm7
        Outro: (Gm7) x4
        """),
    pop("rich-girl", "Rich Girl", "Daryl Hall & John Oates", 1977, listOf(YACHT_ROCK), "F", 84, QUARTERS, 4, SKETCH,
        "The chorus bass walks down F, E, D, C, B flat, A under slash chords; B flat over C stands in for a C7.",
        "Chorus, Verse, Chorus, Verse, Chorus, Outro",
        """
        Chorus: F Am7 | Dm7 F/C | Bb F/A | Gm7 Bb/C | F Am7 | Dm7 F/C | Bb F/A | Gm7 F
        Verse: Gm7 Am7 | Bbmaj7 | Am7 Gm7 | Am7 Gm7 | F/C Gm7 | Am7 Dm7 | Gm7 Am7 | Bbmaj7 | Am7 Gm7 | Am7 Gm7 | Dm7 Gm7 | Dm7 Gm7 | Bb/C | Bb/C
        Outro: Dm7 C | Bb F/A | Gm7 Bb/C | F Am7 | Dm7 F/C | Bb F/A | Gm7 | Dm7 C | Bb F/A | Gm7 F
        """),
    pop("sara-smile", "Sara Smile", "Daryl Hall & John Oates", 1975, listOf(YACHT_ROCK, SOUL), "Dm", 75, BALLAD_ARPEGGIO, 4, SKETCH,
        "Soul ballad in D minor: G major comes from D Dorian, while E flat 7 and the B over C sharp are chromatic passing chords.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus x2, Interlude, Outro",
        """
        Intro: Dm7 | Am7 | Gm7 | Bb/C B/C# | Dm7 | Am7 | Gm7 | Bb/C
        Verse: Dm7 | C6 | Bbmaj7 | G A | Dm7 | C6 | Bbmaj7 | G A
        Pre-chorus: Bb | C | C#dim7 | Dm7 | Bb | C | Eb7 Dm7 | Gm7
        Chorus: Dm7 | Am7 | Gm7 | Bb/C B/C# | Dm7 | Am7 | Gm7 | Bb/C
        Interlude: Gm7 C Dm7 C/E | F:1 Bb:1 C#dim7:2
        Outro: Dm7 | Am7 | Gm7 | Bb/C B/C# | Dm7 | Am7 | Gm7 | Bb/C
        """),
    pop("maneater", "Maneater", "Daryl Hall & John Oates", 1982, listOf(YACHT_ROCK, EIGHTIES_ROCK), "Bm", 89, QUARTERS, 4, LIKELY,
        "The verse slides from D down through C to B major, chromatic chords before E minor 7; the chorus walks B minor, A, G, F sharp.",
        "Intro, Verse x2, Chorus, Verse, Chorus, Solo, Chorus x2, Outro",
        """
        Intro: (Bm | A | G | A) x4
        Verse: D | D | A/C# | A/C# | C | B | Em7 | Em7 | A | A#dim | Bm A | Bm
        Chorus: Bm | A | G | F# | Bm | A | Em | G A
        Solo: (Bm | A | G | A) x4
        Outro: Bm | A | G | F# | Bm | A | Em | G A
        """),
    pop("just-the-two-of-us", "Just the Two of Us", "Grover Washington Jr. featuring Bill Withers", 1980, listOf(YACHT_ROCK, SOUL), "Fm", 96, BROKEN, 3, LIKELY,
        "D flat major 7 to C7 to F minor 7, then E flat minor 7 and A flat 7, a secondary two five that leads back to D flat.",
        "Intro, Verse, Chorus, Verse, Chorus, Solo, Verse, Chorus, Outro x2",
        """
        Intro: (Dbmaj7 | C7 | Fm7 | Ebm7 Ab7) x2
        Verse: (Dbmaj7 | C7 | Fm7 | Ebm7 Ab7) x2
        Chorus: Dbmaj7 | C7 | Fm7 | Ebm7 Ab7 | Dbmaj7 | C7 | Fm7 | Fm7
        Solo: (Dbmaj7 | C7 | Fm7 | Ebm7 Ab7) x2
        Outro: Dbmaj7 | C7 | Fm7 | Ebm7 Ab7
        """),
    pop("ride-like-the-wind", "Ride Like the Wind", "Christopher Cross", 1980, listOf(YACHT_ROCK), "Cm", 124, EIGHTHS_ROCK, 4, LIKELY,
        "C minor with a modal, Dorian lift: D minor 7 over a C pedal and F over G both carry the raised sixth.",
        "Intro, Verse, Pre-chorus, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus x2, Chorus, Outro x2",
        """
        Intro: Cm7 Bb | Cm7 Bb | Bb/Ab Bb | Cm7 Bb | Cm7 Bb | Cm7 Bb | Bb/Ab Bb | Cm7 Bb
        Verse: (Cm7 | Bb) x4
        Pre-chorus: Abmaj7 | Gm7 | Abmaj7 | Gm7 | Cm7 | Bb | Cm7 | Bb
        Chorus: Cm7 | Dm7/C | Cm7 | Dm7/C | Cm7 | Dm7/C | Cm7 Dm7 | Ebmaj7 F/G
        Interlude: Cm7 | Bb | Bb/Ab | Bb
        Outro: Cm7 | Dm7/C | Cm7 | Dm7/C | Cm7 | Dm7/C | Cm7 Dm7 | Ebmaj7 F/G
        """),
    pop("you-make-my-dreams", "You Make My Dreams", "Daryl Hall & John Oates", 1980, listOf(YACHT_ROCK, EIGHTIES_ROCK), "F", 167, EIGHTHS_ROCK, 2, LIKELY,
        "Hold F under the keyboard riff, climb G minor, A minor, B flat; the E flat in the bridge is borrowed from F minor.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus, Outro x3",
        """
        Intro: F | F | F | F
        Verse: (F | F | F | F | F | F | Gm Am | Bb) x2
        Chorus: Gm | C | F | F | Gm Am | Bb | C | F
        Bridge: Eb | Bb | F | F | Eb | Bb | F | F | Eb | Dm | Gm Bb | C
        Outro: Gm Am | Bb | C | F
        """),
    pop("kiss-on-my-list", "Kiss on My List", "Daryl Hall & John Oates", 1980, listOf(YACHT_ROCK, EIGHTIES_ROCK), "C", 122, QUARTERS, 4, SKETCH,
        "Mixes C major and C minor: C minor 7, F minor 7, A flat major 7, B flat 6 and G minor 7 are all borrowed from C minor.",
        "Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus x3",
        """
        Verse: (Cm7 | Cm7 | Fm7 | Fm7 | Abmaj7 | Bb6 | Cadd9 | Cadd9) x2
        Pre-chorus: Fm7 | Gm7 | Fm7 | Gm7 | Fm7 | Gm7 | Fm7 Dm7 | G
        Chorus: C | C | Cm7 | Cm7 | F | F | Csus2 C | Csus2 C
        """),
)
