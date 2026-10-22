package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// Greatest hits and soul. Chords only (docs/CONTENT.md): key, tempo, section order and the chords of each bar. Bar
// lengths are the weakest part, so most charts are LIKELY. Notes name every chord outside the key and say why it is
// there (PopSongsTest). Left out, key or chords unsure: No Woman, No Cry (the live key), Dancing in the Dark,
// Let's Stay Together.

internal val HITS_SONGS: List<PopSong> = listOf(
    pop("let-it-be", "Let It Be", "The Beatles", 1970, listOf(GREATEST_HITS, BALLAD), "C", 73, QUARTERS, 4, LIKELY,
        "Two chords a bar over a steady piano; the B flat in the fill after the chorus is borrowed from C minor.",
        "Intro, Verse, Chorus, Verse, Chorus x2, Interlude, Solo, Chorus, Verse, Chorus x2, Outro",
        """
        Intro: C G | Am F | C G | F C
        Verse: (C G | Am F | C G | F C) x2
        Chorus: Am G | F C | C G | F C
        Interlude: F:1 C/E:1 Dm7:1 C:1 | Bb:1 F/A:1 G:1 F:1 | C | C
        Solo: (C G | Am F | C G | F C) x2
        Outro: F:1 C/E:1 Dm7:1 C:1 | Bb:1 F/A:1 G:1 F:1 | C | C
        """),
    pop("hey-jude", "Hey Jude", "The Beatles", 1968, listOf(GREATEST_HITS, BALLAD), "F", 74, QUARTERS, 4, LIKELY,
        "F7 is a secondary dominant into B flat, B flat 7 is a chromatic step in the bridge, and the long outro has an E flat borrowed from F minor.",
        "Verse x2, Bridge, Verse, Bridge, Verse, Outro x6",
        """
        Verse: F | C | C7 | F | Bb | F | C7 | F
        Bridge: F7 | Bb Bbmaj7 | Bb7 Gm | C7 | F | F7 | Bb Bbmaj7 | Bb7 Gm | C7 | F | F7 | C | C7
        Outro: F | Eb | Bb | F
        """),
    pop("imagine", "Imagine", "John Lennon", 1971, listOf(GREATEST_HITS, BALLAD), "C", 76, QUARTERS, 4, LIKELY,
        "Rock between C and C major 7; in the chorus E7 is a secondary dominant, the five of A minor, that moves to F instead.",
        "Intro, Verse, Pre-chorus, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus",
        """
        Intro: C Cmaj7 | F | C Cmaj7 | F
        Verse: (C Cmaj7 | F) x4
        Pre-chorus: F Am/E | Dm7 F/C | G | G7
        Chorus: (F G | C E7) x3 | F G | C
        """),
    pop("piano-man", "Piano Man", "Billy Joel", 1973, listOf(GREATEST_HITS, BALLAD), "C", 178, QUARTERS, 4, SKETCH,
        "A waltz whose bass walks down a step a bar under slash chords; D7 and D major over an F sharp bass are secondary dominants leading to G.",
        "Intro, Verse x2, Pre-chorus, Chorus, Interlude, Verse x2, Interlude, Verse x2, Pre-chorus, Chorus, Interlude, Verse x2, Pre-chorus, Chorus, Outro",
        """
        Intro: C | G/B | F/A | C/G | F | C/E | D7 | G | C | G/B | F/A | C/G | F | G | C | C
        Verse: C | G/B | F/A | C/G | F | C/E | D7 | G | C | G/B | F/A | C/G | F | G | C | C
        Pre-chorus: Am | Am/G | D/F# | Fmaj7 | G | G | G | G | Am | Am/G | D/F# | G | G/F | C/E | G | G
        Chorus: C | G/B | F/A | C/G | F | C/E | D7 | G | C | G/B | F/A | C/G | F | G | C | C
        Interlude: C | G/B | F/A | C/G | F | C/E | D7 | G | C | G/B | F/A | C/G | F | G | C | C
        Outro: C | G/B | F/A | C/G | F | G | C | C
        """, beatsPerBar = 3),
    pop("hotel-california", "Hotel California", "Eagles", 1976, listOf(GREATEST_HITS, ROCK), "Bm", 75, BROKEN, 2, LIKELY,
        "Eight bars that never change; the E major is borrowed from B Dorian and F sharp 7 is the minor key's own dominant.",
        "Intro x2, Verse, Chorus, Verse, Chorus, Verse, Solo x5",
        """
        Intro: Bm | F#7 | A | E | G | D | Em | F#7
        Verse: (Bm | F#7 | A | E | G | D | Em | F#7) x2
        Chorus: G | D | F#7 | Bm | G | D | Em | F#7
        Solo: Bm | F#7 | A | E | G | D | Em | F#7
        """),
    pop("three-little-birds", "Three Little Birds", "Bob Marley & The Wailers", 1977, listOf(GREATEST_HITS), "A", 74, QUARTERS, 1, LIKELY,
        "Only A, D and E, played on the offbeat for the reggae lilt.",
        "Intro, Chorus, Verse, Chorus, Verse, Chorus x2",
        """
        Intro: A | A | D | A
        Chorus: A | A | D | A | A | A | D | A
        Verse: A | E | A | D | A | E | D | A
        """),
    pop("stand-by-me", "Stand By Me", "Ben E. King", 1961, listOf(GREATEST_HITS, SOUL), "A", 119, BROKEN, 1, LIKELY,
        "The best known bass line in soul over I, vi, IV, V, with two bars on each of the first two chords.",
        "Intro, Verse x2, Chorus, Verse x2, Chorus, Interlude x2, Chorus x2",
        """
        Intro: A | A | F#m | F#m | D | E | A | A
        Verse: A | A | F#m | F#m | D | E | A | A
        Chorus: A | A | F#m | F#m | D | E | A | A
        Interlude: A | A | F#m | F#m | D | E | A | A
        """),
    pop("brown-eyed-girl", "Brown Eyed Girl", "Van Morrison", 1967, listOf(GREATEST_HITS, ROCK), "G", 150, EIGHTHS_ROCK, 2, LIKELY,
        "Play the guitar riff as chords: G and C a bar each, quick and bright, with a D7 stop before the chorus.",
        "Intro, Verse, Pre-chorus, Verse, Pre-chorus, Bridge, Chorus, Interlude, Verse, Pre-chorus, Bridge, Chorus x2, Outro",
        """
        Intro: G | C | G | D | G | C | G | D
        Verse: (G | C | G | D) x4
        Pre-chorus: C | D | G | Em | C | D | G | D
        Bridge: D7 | D7
        Chorus: G | C | G | D | G | C | G | D
        Interlude: G | C | G | D | G | C | G | D
        Outro: G | C | G | D | G
        """),
    pop("free-fallin", "Free Fallin'", "Tom Petty", 1989, listOf(GREATEST_HITS, ROCK), "F", 84, QUARTERS, 2, LIKELY,
        "A one bar loop on a held F: the B flat over F and the C sus4 are colours above the same bass, one beat each.",
        "Intro, Verse x2, Chorus, Verse x2, Chorus, Interlude, Bridge, Chorus x2, Outro",
        """
        Intro: (F Bb/F F Csus4) x2
        Verse: (F Bb/F F Csus4) x4
        Chorus: (F Bb/F F Csus4) x4
        Interlude: (F Bb/F F Csus4) x4
        Bridge: (F Bb/F F Csus4) x4
        Outro: F Bb/F F Csus4 | F
        """),
    pop("your-song", "Your Song", "Elton John", 1970, listOf(GREATEST_HITS, BALLAD), "Eb", 64, BALLAD_ARPEGGIO, 4, SKETCH,
        "Slash chords let the bass walk: C minor over B flat, then over A; G7 is a secondary dominant into C minor.",
        "Intro, Verse x2, Chorus, Verse x2, Chorus, Outro",
        """
        Intro: Eb Ab/Eb | Bb/Eb Ab/Eb
        Verse: Eb Abmaj7 | Bb/D Gm7 | Cm Cm/Bb | Cm/A Abmaj7 | Eb/Bb Bb | G7/B Cm | Eb Fm | Ab Bb
        Chorus: Bb/D Cm | Fm Ab | Bb/D Cm | Fm Ab | Cm | Cm/Bb | Cm/A | Fm6/Ab | Eb/G Ab6 | Fm7 Bb7 | Eb | Eb
        Outro: Cm/A | Fm6/Ab | Eb/G Ab6 | Fm7 Bb7 | Eb
        """),
    pop("crazy-little-thing-called-love", "Crazy Little Thing Called Love", "Queen", 1979, listOf(GREATEST_HITS, ROCK), "D", 154, SHUFFLE, 3, SKETCH,
        "A rockabilly shuffle: B flat and C are borrowed from D minor, F too, and the E major is a secondary dominant.",
        "Intro, Verse x2, Bridge, Verse, Solo, Verse x2, Outro",
        """
        Intro: D Dsus4 | D Dsus4
        Verse: D | D | G:1 C:1 G:2 | G | D | D | G:1 C:1 G:2 | G | D | D | Bb C | D
        Bridge: G | G | C | G | Bb | Bb | E | A | F | F
        Solo: Bb | Bb | D | D | Bb | Bb | E | A | F
        Outro: Bb C | D | Bb C | D
        """),
    pop("knockin-on-heavens-door", "Knockin' on Heaven's Door", "Bob Dylan", 1973, listOf(GREATEST_HITS, ROCK), "G", 70, HALVES, 1, LIKELY,
        "Two four bar phrases, one landing on A minor 7 and one on C: hear how different they feel.",
        "Intro, Verse, Chorus, Verse, Chorus, Outro x2",
        """
        Intro: G | D | Am7 | Am7 | G | D | C | C
        Verse: (G | D | Am7 | Am7 | G | D | C | C) x2
        Chorus: (G | D | Am7 | Am7 | G | D | C | C) x2
        Outro: G | D | Am7 | Am7 | G | D | C | C
        """),
    pop("take-me-home-country-roads", "Take Me Home, Country Roads", "John Denver", 1971, listOf(GREATEST_HITS, COUNTRY), "A", 82, QUARTERS, 2, LIKELY,
        "Verse and chorus are I, vi, V, IV in A; the bridge has a G borrowed from A minor.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus x2, Outro",
        """
        Intro: A | A | A | A
        Verse: A | F#m | E | D A | A | F#m | E | D A
        Chorus: A | E | F#m | D | A | E | D | A
        Bridge: F#m | E A | D | A E | F#m | G | D A | E E7
        Outro: E | A | E | A
        """),
    pop("wonderful-tonight", "Wonderful Tonight", "Eric Clapton", 1977, listOf(GREATEST_HITS, BALLAD), "G", 95, BALLAD_ARPEGGIO, 2, LIKELY,
        "D over F sharp makes the bass step down G, F sharp, E: keep the left thumb on the bass line.",
        "Intro, Verse, Pre-chorus, Interlude, Verse, Pre-chorus, Interlude, Bridge, Verse, Pre-chorus, Interlude",
        """
        Intro: G | D/F# | C | D | G | D/F# | C | D
        Verse: G | D/F# | C | D | G | D/F# | C | D
        Pre-chorus: C | D | G D/F# | Em | C | D | G | G
        Interlude: G | D/F# | C | D
        Bridge: C | D | G D/F# | Em | C | D | C | D | G | G
        """),
    pop("dreams", "Dreams", "Fleetwood Mac", 1977, listOf(GREATEST_HITS, ROCK), "Am", 120, HALVES, 1, SKETCH,
        "Two chords for nearly the whole song; A minor only shows up in the solo, so home is mostly implied.",
        "Intro, Verse, Pre-chorus, Chorus, Solo, Verse, Pre-chorus, Chorus x2, Outro",
        """
        Intro: Fmaj7 | G | Fmaj7 | G
        Verse: (Fmaj7 | G) x4
        Pre-chorus: (Fmaj7 | G) x4
        Chorus: (Fmaj7 | G) x4
        Solo: Fmaj7 | G | Fmaj7 | G | Am | G | Fmaj7 | G
        Outro: Fmaj7 | G | Fmaj7 | G
        """),
    pop("sweet-caroline", "Sweet Caroline", "Neil Diamond", 1969, listOf(GREATEST_HITS), "B", 127, QUARTERS, 2, SKETCH,
        "Mostly B, E and F sharp; the chorus walks down E, D sharp minor, C sharp minor back toward the verse.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Interlude, Chorus",
        """
        Intro: F#7 | F# | F#7 | F# | F#7 | B
        Verse: B | B | E | E | B | B | F# | F# | B | B | E | E | B | B | F# | F#7
        Pre-chorus: B | B | G#m | G#m | F# E | F# | F# E | F#
        Chorus: B | E | E | B | E | F# | F# E | F# | B | E | E | B | E | F# | E D#m | C#m
        Interlude: F#7 | F# | F#7 | F# | F#7 | B
        """),
    pop("hallelujah", "Hallelujah", "Jeff Buckley", 1994, listOf(GREATEST_HITS, BALLAD), "C", 68, BALLAD_ARPEGGIO, 2, LIKELY,
        "Counted in a slow two; the E7 near the end of each verse is a secondary dominant into A minor.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Interlude, Verse, Chorus, Interlude, Verse, Chorus, Interlude, Verse, Chorus, Outro",
        """
        Intro: C | Am | C | Am
        Verse: C | Am | C | Am | F | G | C | G | C | F | G | Am | F | G | E7 | Am
        Chorus: F | F | Am | Am | F | F | C G | C
        Interlude: C | Am | C | Am
        Outro: F | Am | F | C G | C
        """, beatsPerBar = 2),
    pop("lean-on-me", "Lean on Me", "Bill Withers", 1972, listOf(SOUL, GREATEST_HITS), "C", 76, QUARTERS, 2, LIKELY,
        "The chords climb and fall a step a beat, C, D minor, E minor, F, just like the left hand.",
        "Intro, Verse, Chorus, Verse, Bridge, Verse, Bridge, Chorus, Outro x2",
        """
        Intro: C | C Dm Em F | F | F Em Dm C | C | C Dm Em G | C | C
        Verse: C | C Dm Em F | F | F Em Dm C | C | C Dm Em Em | G | G | C | C Dm Em F | F | F Em Dm C | C | Dm:1 Em:1 G:2 | C | C
        Chorus: C | C Dm Em F | F | F Em Dm C | C | C Dm Em Em | G | G | C | C Dm Em F | F | F Em Dm C | C | Dm:1 Em:1 G:2 | C | C
        Bridge: C | G | C | C | C | G | C | C
        Outro: G | C | G | C
        """),
    pop("aint-no-sunshine", "Ain't No Sunshine", "Bill Withers", 1971, listOf(SOUL), "Am", 78, BROKEN, 2, LIKELY,
        "Minor sevenths in A minor; the middle holds one chord for a long stretch, so keep the time in your head.",
        "Intro, Verse x2, Bridge, Verse, Outro",
        """
        Intro: Am7 | Em7 G7
        Verse: Am7 | Em7 G7 | Am7 | Em7 G7 | Am7 | Em7 G7 | Em7 | Dm7 | Am7 | Em7 G7 | Am7 | Am7
        Bridge: (Am7) x8
        Outro: Am7 | Em7 G7 | Am7 | Am7
        """),
    pop("isnt-she-lovely", "Isn't She Lovely", "Stevie Wonder", 1976, listOf(SOUL, GREATEST_HITS), "E", 119, SHUFFLE, 2, LIKELY,
        "One swung loop, C sharp minor 7, F sharp 9, A over B, E; the F sharp 9 is a secondary dominant, the five of B.",
        "Intro, Verse, Solo, Verse, Solo, Verse, Solo x2",
        """
        Intro: C#m7 | F#9 | A/B | E
        Verse: (C#m7 | F#9 | A/B | E) x4
        Solo: (C#m7 | F#9 | A/B | E) x4
        """),
    pop("dock-of-the-bay", "(Sittin' On) The Dock of the Bay", "Otis Redding", 1968, listOf(SOUL, GREATEST_HITS), "G", 104, QUARTERS, 3, LIKELY,
        "Major chords on unexpected roots: B, A and E are secondary dominants, B flat and F are borrowed from G minor.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Verse, Chorus, Outro x2",
        """
        Intro: G | G | G | G
        Verse: G | G | B | B | C | C | B Bb | A
        Chorus: G | E | G | E | G | A | G | E
        Bridge: G D | C | G D | C | G D | C G | F | D
        Outro: G | G | E | E
        """),
    pop("my-girl", "My Girl", "The Temptations", 1964, listOf(SOUL, GREATEST_HITS), "C", 105, QUARTERS, 4, LIKELY,
        "A bass riff on C and F; the A major in the break is a secondary dominant that lifts the song up a whole step to D.",
        "Intro, Verse, Chorus, Verse, Chorus, Interlude, Verse#D, Chorus#D, Outro x2",
        """
        Intro: C | C | C | C
        Verse: C | F | C | F | C | F | C | F
        Chorus: C Dm7 | F G | C Dm7 | F G | Cmaj7 | Cmaj7 | Dm7 G | F G
        Interlude: C | F | C | F | Dm7 | G | Em | A
        Verse#D: D | G | D | G | D | G | D | G
        Chorus#D: D Em | G A | D Em | G A | Dmaj7 | Dmaj7 | Em A | G A
        Outro: Dmaj7 | Em A | Dmaj7 | Em A
        """),
)
