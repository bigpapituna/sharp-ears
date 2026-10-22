package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// 80s rock and pop (and two early 90s staples that sit with them). Chords only (docs/CONTENT.md). Guitars tuned down
// a half step are written at the pitch you hear.

internal val EIGHTIES_SONGS: List<PopSong> = listOf(
    pop("dont-stop-believin", "Don't Stop Believin'", "Journey", 1981, listOf(EIGHTIES_ROCK, ROCK), "E", 119, EIGHTHS_ROCK, 4, LIKELY,
        "The piano loop is eight bars, E, B, C sharp minor, A, then G sharp minor in place of C sharp minor the second time.",
        "Intro, Verse x2, Interlude, Verse, Pre-chorus, Interlude, Verse x2, Pre-chorus, Solo, Chorus x4",
        """
        Intro: E | B | C#m | A | E | B | G#m | A
        Verse: E | B | C#m | A | E | B | G#m | A
        Interlude: E | B | C#m | A | E | B | G#m | A
        Pre-chorus: B7/A A | B7/A Amaj7 | E | E | B7/A A | B7/A Amaj7 | B Esus2 | B:1 A:3
        Solo: E | B | C#m | A | E | B | G#m | A
        Chorus: E | B | C#m | A | E | B | G#m | A
        """),
    pop("open-arms", "Open Arms", "Journey", 1981, listOf(EIGHTIES_ROCK, BALLAD), "D", 100, BALLAD_ARPEGGIO, 4, LIKELY,
        "A falling bass under slash chords, and the C9 closing each chorus line is borrowed, the flat seven.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Outro",
        """
        Intro: D | D | A/C# | G/B | Bm | F#m/A | G | G
        Verse: D | D | A/C# | G/B | Bm | F#m/A | G | G | D | D | A/C# | G/B | Bm | F#m/A | G | G | Em | Em | Bm | Bm:2 A/C#:1 | D | A | G | A | G | A
        Chorus: D | D | F#m | F#m | G | G | C9 | C9 | D | D | F#m | F#m | G | G | C9 | C9
        Interlude: D | D | A/C# | G/B | Bm | F#m/A | G | G
        Outro: D
        """, beatsPerBar = 3),
    pop("livin-on-a-prayer", "Livin' on a Prayer", "Bon Jovi", 1986, listOf(EIGHTIES_ROCK, ROCK), "Em", 123, EIGHTHS_ROCK, 3, LIKELY,
        "A riff on E minor and a climbing build; the last chorus makes a chromatic jump up a minor third to G minor.",
        "Intro, Verse x2, Pre-chorus, Chorus, Verse x2, Pre-chorus, Chorus, Solo, Pre-chorus, Chorus#up x2",
        """
        Intro: (Em) x8 | C | D | Em | Em | C | D | Em | Em
        Verse: Em | Em | Em | Em | C | D | Em | Em
        Pre-chorus: C D | D Em | C D | D Em | C D | D:2 Em:1 D:1 | C | D
        Chorus: (Em C | D | G C | D) x2
        Solo: (Em C | D | G C | D) x2
        Chorus#up: (Gm Eb | F | Bb Eb | F) x2
        """),
    pop("summer-of-69", "Summer of '69", "Bryan Adams", 1985, listOf(EIGHTIES_ROCK, ROCK), "D", 139, EIGHTHS_ROCK, 2, LIKELY,
        "D and A in the verse; the bridge has F, B flat and C borrowed from D minor, then drops straight back.",
        "Intro, Verse x2, Chorus, Interlude, Verse, Chorus, Bridge, Interlude, Verse, Chorus, Outro x2",
        """
        Intro: D | D | A | A | D | D | A | A
        Verse: D | D | A | A | D | D | A | A
        Chorus: Bm | A | D | G | Bm | A | D | G | Bm | A | D | D | A | A
        Interlude: D | D | A | A
        Bridge: F | Bb | C | Bb | F | Bb | C | C
        Outro: D | D | A | A
        """),
    pop("heaven", "Heaven", "Bryan Adams", 1984, listOf(EIGHTIES_ROCK, BALLAD), "C", 70, BALLAD_ARPEGGIO, 4, LIKELY,
        "A power ballad in C: the B flat sus2 is a borrowed flat seven on its way to G.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Solo, Chorus, Outro",
        """
        Intro: C | Am | G | F
        Verse: C Am | G | Dm Am | Bbsus2:2 Gsus4:1 G:1 | C Am | G | Dm Am | Bbsus2 F/A | Gsus4 G
        Chorus: F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G | F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G Gsus4
        Bridge: Dm:3 C/E:1 | F:3 G:1 | Am | G/B | Dm:3 C/E:1 | F G | C | Gsus4 G
        Solo: F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G | F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G
        Outro: F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G | F:1 G:1 Am:2 | C F | F:1 G:1 Am:2 | G Gsus4
        """),
    pop("with-or-without-you", "With or Without You", "U2", 1987, listOf(EIGHTIES_ROCK, ROCK), "D", 110, EIGHTHS_ROCK, 1, LIKELY,
        "I, V, vi, IV in D from the first bar to the last; the bass pulses eighths on the root.",
        "Intro x2, Verse x4, Chorus x2, Interlude, Bridge x2, Verse x2, Bridge x2, Chorus x4, Outro x4",
        """
        Intro: D | A | Bm | G
        Verse: D | A | Bm | G
        Chorus: D | A | Bm | G
        Interlude: D | A | Bm | G
        Bridge: D | A | Bm | G
        Outro: D | A | Bm | G
        """),
    pop("one", "One", "U2", 1991, listOf(ROCK, BALLAD), "Am", 91, QUARTERS, 2, LIKELY,
        "A minor with a D sus2 that never decides between major and minor, then a brighter chorus on C.",
        "Intro, Verse, Chorus, Verse, Chorus, Verse, Chorus, Bridge, Outro x2",
        """
        Intro: (Am | Dsus2 | Fmaj7 | G) x2
        Verse: (Am | Dsus2 | Fmaj7 | G) x2
        Chorus: (C | Am | Fmaj7 | C) x2
        Bridge: C | Am | C | Am | C | G | G | Fmaj7
        Outro: C | Am | Fmaj7 | C
        """),
    pop("girls-just-want-to-have-fun", "Girls Just Want to Have Fun", "Cyndi Lauper", 1983, listOf(EIGHTIES_ROCK, POP), "F#", 121, EIGHTHS_ROCK, 1, SKETCH,
        "F sharp major with D sharp minor as its partner; the bars before each landing split two chords.",
        "Intro, Verse x2, Chorus, Interlude, Verse, Chorus, Outro x2",
        """
        Intro: F# | F# | F# | F#
        Verse: F# | F# | D#m | D#m | B | B | D#m C# | B:3 C#:1 | D#m C# | F#
        Chorus: F# | F# | D#m | D#m | F# | F# | D#m C# | B:3 C#:1 | D#m C# | F#
        Interlude: F# | D#m | F# | D#m
        Outro: F# | D#m | B | C#
        """),
    pop("time-after-time", "Time After Time", "Cyndi Lauper", 1984, listOf(EIGHTIES_ROCK, POP, BALLAD), "C", 131, HALVES, 2, LIKELY,
        "Felt in a slow two: the verse rocks D minor to C, and the chorus resolves G, A minor, F, G to C.",
        "Intro, Verse, Pre-chorus, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus, Chorus, Pre-chorus, Chorus x2",
        """
        Intro: F | G | Em | F | F | G | Em | F
        Verse: (Dm C) x8
        Pre-chorus: F G | Em F | F G | Em | F G | Em F | F Gsus4 | Gsus4
        Chorus: (G | Am | F G | C) x4
        Interlude: F | G | Em | F | F | G | Em | F
        """),
    pop("la-isla-bonita", "La Isla Bonita", "Madonna", 1987, listOf(EIGHTIES_ROCK, POP), "C#m", 100, QUARTERS, 4, LIKELY,
        "Spanish flavour in C sharp minor: the bridge leans on F sharp minor with a D major borrowed there, and G sharp major is the dominant.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Interlude, Verse, Chorus x2, Outro",
        """
        Intro: C#m B | C#m | C#m B | C#m
        Verse: C#m B | C#m | A/C# C#m7 | E B | C#m B | C#m | A/C# C#m7 | E:2 B:1 G#:1
        Chorus: (C#m G#m7 | F#m E | B:3 F#m7:1 | F#m7 C#m) x2
        Bridge: F#m D | E Bsus2 | F#m D | E | F#m D | E Bsus2 | F#m D | E | E G#
        Interlude: C#m B | C#m | A/C# C#m7 | E B
        Outro: C#m B | C#m
        """),
    pop("everybody-wants-to-rule-the-world", "Everybody Wants to Rule the World", "Tears for Fears", 1985, listOf(EIGHTIES_ROCK, POP), "D", 112, SHUFFLE, 4, LIKELY,
        "A shuffle over a D pedal: A and G both sit on a D bass, and the C in the break is borrowed.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus, Interlude, Solo, Chorus x2, Outro",
        """
        Intro: A/D G/D | G/D | A/D G/D | G/D
        Verse: (A/D G/D | G/D) x4
        Chorus: Em7 | F#m | G | F#m | Em7 F#m | G A | A/D G/D | G/D
        Bridge: G | D A | G | D A | G | D A
        Interlude: G | D A | G | D A | Bm | C | Bm | C | G | A
        Solo: (A/D G/D | G/D) x2
        Outro: (A/D G/D | G/D) x2
        """),
    pop("mad-world", "Mad World", "Tears for Fears", 1982, listOf(EIGHTIES_ROCK), "F#m", 119, QUARTERS, 2, SKETCH,
        "F sharp minor with a modal B major, the Dorian raised sixth that keeps it from sounding heavy.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Interlude, Pre-chorus, Chorus x2",
        """
        Intro: F#m | A | E | B
        Verse: (F#m | A | E | B) x4
        Pre-chorus: (F#m | B) x4
        Chorus: (F#m7 | B) x4
        Interlude: (F#m | A | E | B) x2
        """),
    pop("take-on-me", "Take On Me", "a-ha", 1985, listOf(EIGHTIES_ROCK, POP), "A", 169, EIGHTHS_ROCK, 4, LIKELY,
        "Two five one in A with sevenths, a chorus over a falling bass, and a bridge with G and G major 7 borrowed from A minor.",
        "Intro, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus, Chorus, Bridge, Interlude, Verse, Pre-chorus, Chorus x2",
        """
        Intro: (Bm) x8 | Bm7 | E7 | A | Dmaj7 C#m7 | Bm7 | E7 | A | Dmaj7 C#m7 | Bm | E7 | Bm7 | E7
        Verse: (Bm7 | E7 | A | Dmaj7 C#m7) x2
        Pre-chorus: Bm7 | E7 | F#m | D
        Chorus: A | E/G# | F#m7 | D | A | E/G# | F#m7 | D | A | E/G# | F#m7 | D | A | E/G# | D | E
        Interlude: (Bm7 | E7 | A | Dmaj7 C#m7) x2
        Bridge: C#m | C#m | G | G | C#m | C#m | G | Gmaj7 | Bm | Bm | E | E
        """),
    pop("i-wanna-dance-with-somebody", "I Wanna Dance with Somebody (Who Loves Me)", "Whitney Houston", 1987, listOf(EIGHTIES_ROCK, POP), "F#", 119, EIGHTHS_ROCK, 4, LIKELY,
        "Bright F sharp major; the verse sits on the five chord, and the last choruses make a chromatic lift up a whole step to A flat.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus#up x2",
        """
        Intro: F# | F# | F# | F#
        Verse: C# | C# | D#m7 | D#m7:2 G#m7:1 F#/A#:1 | C# | C# | D#m7 | D#m7
        Pre-chorus: G#m7 | C# | F#/A# | B | G#m7 F#/A# | D#m7 | B
        Chorus: F# | F# | F# | F# | D#m | D#m | B | B:3 C#:1 | F# | F# | F# | F# | D#m | D#m | B | G#m7:1 A#m7:1 B:2 | F# | F#
        Bridge: D#m7 | D#m7 | C# | C#:2 G#m:1 A#m:1 | D#m7 | D#m7 | C# | C#:3 B:1
        Chorus#up: Ab | Ab | Ab | Ab | Fm | Fm | Dbadd9 | Dbadd9:3 Eb:1 | Ab | Ab | Ab | Ab | Fm | Fm | Dbadd9 | Bbm7:1 Cm7:1 Db:2
        """),
    pop("i-will-always-love-you", "I Will Always Love You", "Whitney Houston", 1992, listOf(BALLAD, SOUL, POP), "A", 67, BALLAD_ARPEGGIO, 3, SKETCH,
        "Slow and free; after the famous pause it makes a chromatic lift up a whole step, from A to B.",
        "Verse, Chorus, Verse, Chorus, Solo, Verse, Chorus#up, Outro",
        """
        Verse: A | A/G# F#m | D | E | A | A/G# F#m | D | E
        Chorus: A F#m | D E | A F#m | D E | A | A
        Solo: A | A/G# F#m | D | E | A | A/G# F#m | D | E
        Chorus#up: B G#m | E F# | B G#m | E F# | B | B
        Outro: E | F# | B
        """),
    pop("in-the-air-tonight", "In the Air Tonight", "Phil Collins", 1981, listOf(EIGHTIES_ROCK), "Dm", 95, WHOLE, 2, LIKELY,
        "Every chord sits over a D in the bass, so the left hand never moves; the drums do the rest.",
        "Intro, Chorus, Verse, Chorus, Interlude, Verse, Chorus, Outro x2",
        """
        Intro: (Dm | C/D | Bb/D | C/D) x2
        Chorus: (Dm | C/D | Bb/D | C/D) x2
        Verse: (Dm | Dm | C/D | C/D | Bb/D | Bb/D | Dm7 | Dm7) x2
        Interlude: Dm | C/D | Bb/D | C/D
        Outro: Dm | C/D | Bb/D | C/D
        """),
    pop("purple-rain", "Purple Rain", "Prince", 1984, listOf(EIGHTIES_ROCK, BALLAD), "Bb", 57, BALLAD_ARPEGGIO, 1, LIKELY,
        "Very slow: B flat, G minor 7, F and E flat, and each chorus starts on the four chord.",
        "Intro, Verse, Chorus, Verse, Chorus, Verse, Chorus, Solo, Outro",
        """
        Intro: Bb | Gm7 | F | Eb
        Verse: (Bb | Gm7 | F | Eb) x2
        Chorus: Eb | Eb | Eb | Bb | Gm7 | F | F | Bb
        Solo: (Bb | Gm7 | F | Eb) x4
        Outro: (Bb | Gm7 | F | Eb) x2 | Bb
        """),
    pop("sweet-child-o-mine", "Sweet Child o' Mine", "Guns N' Roses", 1987, listOf(EIGHTIES_ROCK, ROCK), "C#", 125, EIGHTHS_ROCK, 2, SKETCH,
        "Guitars tuned down a half step, so it sounds in C sharp; B and G sharp minor are borrowed from Mixolydian, with its flat seven.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Interlude, Chorus, Solo, Bridge, Outro x2",
        """
        Intro: C# | C# | B | B | F# | F# | C# | C#
        Verse: C# | C# | B | B | F# | F# | C# | C#
        Chorus: G# | B | C# | C# | G# | B | C# | C#
        Interlude: C# | C# | B | B | F# | F# | C# | C#
        Solo: C# | C# | C# | B | B | F# | F# | C#
        Bridge: D#m | D#m | B | B | A#m | A#m | G#m | G#m
        Outro: D#m | F# | G# | B C#
        """),
    pop("every-rose-has-its-thorn", "Every Rose Has Its Thorn", "Poison", 1988, listOf(EIGHTIES_ROCK, BALLAD), "F#", 70, HALVES, 2, LIKELY,
        "Tuned down a half step, so G shapes sound in F sharp; the add9 on B colours the whole song.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Bridge, Solo, Verse, Chorus, Outro",
        """
        Intro: F# | Badd9 | F# | Badd9
        Verse: F# | Badd9 | F# | Badd9 | F# | Badd9 | F# | Badd9 | C# | B
        Chorus: F# | Badd9 | F# | Badd9 | F# C# | B F# | F# | B
        Interlude: F# | Badd9 | F# | Badd9
        Bridge: D#m | C# | B | F# | D#m | C# | B | B
        Solo: F# | Badd9 | F# | Badd9 | D#m | C# | B | F# | D#m | C# | B | B | F# | Badd9 | F# | Badd9
        Outro: F#
        """),
    pop("here-i-go-again", "Here I Go Again", "Whitesnake", 1987, listOf(EIGHTIES_ROCK, ROCK), "G", 89, EIGHTHS_ROCK, 4, LIKELY,
        "Mostly G, D and C, with the bass walking down under the slash chords in each verse.",
        "Intro, Verse, Interlude, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Interlude, Solo, Pre-chorus, Chorus x2",
        """
        Intro: G D | C | G D | C
        Verse: G D | C | G D | C | G D/F# | C/E:2 C:1 Bm:1 | Am7 | D
        Interlude: Am7 | D | Em | Am C | G Em | Am:2 C:1 D:1
        Pre-chorus: Am7 | D | Am7 | D:2 C:1 D:1
        Chorus: G C | D:2 C:1 D:1 | G C | D:2 C:1 D:1 | G C | D:2 C:1 Bm:1 | Am | D | Am | D:2 C:1 D:1
        Solo: G D/G | C/G | G D/G | C/G | G D/F# | C/E:2 C:1 Bm:1 | Am7 | D
        """),
    pop("dont-you-forget-about-me", "Don't You (Forget About Me)", "Simple Minds", 1985, listOf(EIGHTIES_ROCK), "E", 112, EIGHTHS_ROCK, 2, LIKELY,
        "E Mixolydian: D is the flat seven, and E minor, C and G in the intro and bridge are borrowed from E minor.",
        "Intro, Verse, Chorus, Bridge, Interlude, Verse, Chorus, Outro x3",
        """
        Intro: D Em | Em | D Em | C D | D Em | Em | D Em | C D
        Verse: (E | D | A | D) x4
        Chorus: E | D | A | D | E | D | A | A
        Bridge: C | C | G | G | D | D | A | A
        Interlude: D Em | Em | D Em | C D
        Outro: E | D | A | D
        """),
)
