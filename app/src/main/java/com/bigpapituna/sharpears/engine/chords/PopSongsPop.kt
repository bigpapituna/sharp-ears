package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// 90s to today: pop and top 30 staples. Chords only (docs/CONTENT.md). Capo shapes on guitar are written at the pitch
// you hear. Left out, sources disagreeing or no real chord chart: Levitating, bad guy, Beautiful Things, Firework,
// Treat You Better, Stay, Rolling in the Deep, As It Was, Believer, Diamonds, Die with a Smile, Espresso, Good as Hell,
// Lose Control.

internal val POP_HITS: List<PopSong> = listOf(
    pop("wonderwall", "Wonderwall", "Oasis", 1995, listOf(POP, ROCK), "F#m", 87, EIGHTHS_ROCK, 3, LIKELY,
        "Guitar capo shapes become F sharp minor 7, A, E sus4 and B sus4 on piano; keep the top notes ringing.",
        "Intro, Verse#first, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus x2, Outro x2",
        """
        Intro: (F#m7 A | Esus4 Bsus4) x4
        Verse#first: (F#m7 A | Esus4 Bsus4) x3 | Dadd9 Esus4 | Bsus4
        Verse: (F#m7 A | Esus4 Bsus4) x4
        Pre-chorus: Dadd9 Esus4 | F#m7 | Dadd9 Esus4 | F#m7 | Dadd9 Esus4 | A E/G# | F#m7 | A | Bsus4 | Bsus4
        Chorus: (Dadd9 F#m7 | A F#m7) x4
        Outro: (Dadd9 F#m7 | A F#m7) x4
        """),
    pop("dont-look-back-in-anger", "Don't Look Back in Anger", "Oasis", 1996, listOf(POP, ROCK), "C", 82, QUARTERS, 3, LIKELY,
        "Piano chords from the first bar; E7 is a secondary dominant into A minor, and F minor 6 is borrowed from C minor.",
        "Intro, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus, Chorus, Solo, Chorus, Outro",
        """
        Intro: C | F | C | F
        Verse: (C G | Am E7 | F G | C:2 Am:1 G:1) x2
        Pre-chorus: F Fm6 | C | F Fm6 | C | F Fm6 | C | G | E7/G# | Am G | F | G | G
        Chorus: C G | Am E7 | F G | C:2 Am:1 G:1 | C G | Am E7 | F | G
        Interlude: C G | Am E7 | F G | C:2 Am:1 G:1
        Solo: F Fm6 | C | F Fm6 | C | F Fm6 | C | G | E7/G# | Am G | F | G | G
        Outro: C G | Am E7 | F G | C:2 Am:1 G:1 | C G | Am | F | Fm6 | C G | Am E7 | F | Fm6 | C
        """),
    pop("viva-la-vida", "Viva la Vida", "Coldplay", 2008, listOf(POP, ROCK), "Ab", 138, QUARTERS, 2, LIKELY,
        "Four chords, IV, V, I, vi in A flat, under strings that strike every beat.",
        "Intro, Verse#short, Interlude, Verse, Chorus, Interlude, Verse, Chorus, Bridge, Chorus, Outro",
        """
        Intro: (Db | Eb | Ab | Fm) x2
        Verse#short: (Db | Eb | Ab | Fm) x2
        Interlude: (Db | Eb | Ab | Fm) x2
        Verse: (Db | Eb | Ab | Fm) x4
        Chorus: (Db | Eb | Ab | Fm) x3 | Db | Eb | Ab/C | Fm
        Bridge: Db | Fm | Db | Fm | Db | Fm | Eb | Eb | Db | Eb | Ab | Fm | Db | Eb | Ab | Fm
        Outro: (Db | Eb | Ab | Fm) x2
        """),
    pop("the-scientist", "The Scientist", "Coldplay", 2002, listOf(POP, BALLAD), "F", 73, BALLAD_ARPEGGIO, 4, SKETCH,
        "A piano ballad in F: D minor 7, B flat, F, with a sus2 that rings over the bar.",
        "Intro, Verse, Chorus, Interlude, Verse, Chorus, Outro",
        """
        Intro: (Dm7 | Bb | F | Fsus2) x2
        Verse: (Dm7 | Bb | F | Fsus2) x4
        Chorus: Bb | Bb | F | Fsus2 | Bb | Bb | F | F F6 | C | C7 | F
        Interlude: F | Bb | F | F Fmaj7 | Dm7 | Bb | F | Fsus2
        Outro: (Dm7 | Bb | F | Fsus2) x4
        """),
    pop("fix-you", "Fix You", "Coldplay", 2005, listOf(POP, BALLAD), "Eb", 69, BALLAD_ARPEGGIO, 4, LIKELY,
        "Organ chords in E flat with G minor stepping the bass down; the build doubles the chord rate.",
        "Intro, Verse x2, Chorus, Interlude, Verse, Chorus, Solo, Bridge, Chorus",
        """
        Intro: (Eb Gm | Cm7 Gm/Bb) x2
        Verse: (Eb Gm | Cm7 Gm/Bb) x4
        Chorus: (Ab | Eb/G | Bbsus4 | Bb) x3
        Interlude: (Eb Gm | Cm7 Bb) x2
        Solo: (Eb Ab | Eb Bb | Cm7 Ab | Eb Bb) x2
        Bridge: Eb Ab | Eb Bb | Cm7 Ab | Eb Bb | Eb Ab | Eb Gm | Cm7 Ab | Eb Bb
        """),
    pop("clocks", "Clocks", "Coldplay", 2002, listOf(POP, ROCK), "Eb", 131, BROKEN, 2, LIKELY,
        "A modal E flat: B flat minor brings the Mixolydian flat seven, and the bridge has G flat major 7, G flat and D flat borrowed from E flat minor.",
        "Intro, Verse, Chorus, Verse, Chorus#long, Bridge, Interlude, Outro",
        """
        Intro: (Eb | Bbm | Bbm | Fm) x4
        Verse: (Eb | Bbm | Bbm | Fm) x4
        Chorus: (Eb | Bbm | Bbm | Fm) x4
        Chorus#long: (Eb | Bbm | Bbm | Fm) x8
        Bridge: Gbmaj7 | Gbmaj7 | Db | Ab | Gbmaj7 | Gbmaj7 | Db | Ab | Gbmaj7 | Gbmaj7 | Db | Ab | Gb | Gbmaj7 | Gb | Gbmaj7
        Interlude: (Eb | Bbm | Bbm | Fm) x6
        Outro: (Eb | Bbm | Bbm | Fm) x7
        """),
    pop("someone-like-you", "Someone Like You", "Adele", 2011, listOf(POP, BALLAD), "A", 135, BALLAD_ARPEGGIO, 2, LIKELY,
        "Broken chords in A; C sharp minor over G sharp makes the bass fall A, G sharp, F sharp.",
        "Intro, Verse, Pre-chorus, Chorus#long, Verse#two, Pre-chorus, Chorus, Bridge, Chorus, Chorus#long, Outro",
        """
        Intro: A | C#m/G# | F#m | D
        Verse: (A | C#m/G# | F#m | D) x3
        Verse#two: (A | C#m/G# | F#m | D) x2
        Pre-chorus: E | F#m7 | D | D | E | F#m7 | D | D
        Chorus: (A | E | F#m | D) x4
        Chorus#long: (A | E | F#m | D) x5
        Bridge: E | E | F#m | F#m | D | D | Bm A | D E
        Outro: E | A
        """),
    pop("hello", "Hello", "Adele", 2015, listOf(POP, BALLAD), "Fm", 79, HALVES, 2, LIKELY,
        "F minor with its relative major's chords; the chorus reorders the same four and the build adds C minor.",
        "Intro, Verse x2, Pre-chorus, Chorus#first, Verse, Pre-chorus, Chorus, Bridge, Chorus",
        """
        Intro: Fm Ab | Eb Db
        Verse: (Fm Ab | Eb Db) x4
        Pre-chorus: Fm Eb | Cm Db | Fm Eb | Db
        Chorus#first: (Fm Db | Ab Eb) x7 | Fm Ab | Eb Db
        Chorus: (Fm Db | Ab Eb) x8
        Bridge: Eb Ab | Fm Db | Eb Ab | Fm Db | Eb Ab | Fm Db | Eb Ab
        """),
    pop("all-of-me", "All of Me", "John Legend", 2013, listOf(POP, BALLAD, SOUL), "Ab", 63, BALLAD_ARPEGGIO, 2, LIKELY,
        "A piano ballad in A flat: the verse is vi, IV, I, V and the chorus adds B flat minor over D flat.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Pre-chorus, Chorus, Outro",
        """
        Intro: (Fm Db | Ab Eb) x2
        Verse: (Fm Db | Ab Eb) x4
        Pre-chorus: Bbm Ab | Eb | Bbm Ab | Eb
        Chorus: Ab | Fm | Bbm/Db | Ebsus4 Eb | Ab | Fm | Bbm/Db | Ebsus4 Eb | (Fm Db | Ab Eb) x2
        Outro: (Fm Db | Ab Eb) x2
        """),
    pop("perfect", "Perfect", "Ed Sheeran", 2017, listOf(POP, BALLAD), "Ab", 63, BALLAD_ARPEGGIO, 3, LIKELY,
        "Counted in a slow four with a triplet sway: A flat, F minor, D flat, E flat.",
        "Intro, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus, Chorus, Interlude#B, Chorus, Outro",
        """
        Intro: Ab
        Verse: Ab | Fm | Db | Eb | Ab | Fm | Db | Eb
        Pre-chorus: Ab | Fm | Db Ab | Eb | Ab | Fm | Db | Eb
        Chorus: (Fm Db | Ab Eb) x4
        Interlude: Ab Eb/G Fm Eb | Db Eb
        Interlude#B: Ab | Fm | Db | Eb
        Outro: Ab/C Db | Ebsus4 Eb | Ab Eb/G Fm Eb | Db Eb | Ab
        """),
    pop("shape-of-you", "Shape of You", "Ed Sheeran", 2017, listOf(POP), "C#m", 96, QUARTERS, 1, LIKELY,
        "One two bar loop, C sharp minor, F sharp minor, A, B, for the whole song: lock it in and groove.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: (C#m F#m | A B) x2
        Verse: (C#m F#m | A B) x4
        Pre-chorus: (C#m F#m | A B) x4
        Chorus: (C#m F#m | A B) x8
        Bridge: (C#m F#m | A B) x4
        Outro: (C#m F#m | A B) x2
        """),
    pop("thinking-out-loud", "Thinking Out Loud", "Ed Sheeran", 2014, listOf(POP, BALLAD, SOUL), "D", 79, SHUFFLE, 2, LIKELY,
        "D, D over F sharp, G, A: the bass climbs a step at a time while the right hand barely moves.",
        "Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Solo, Chorus, Outro",
        """
        Verse: (D D/F# | G A) x8
        Pre-chorus: Em | A D | Em | A | Em | A Bm | Em | A
        Chorus: (G A | D D/F#) x3 | G A | Bm A | G D/F# | Em A | D
        Solo: (D D/F# | G A) x4
        Outro: (Bm A | G D/F# | Em A | D) x3
        """),
    pop("just-the-way-you-are", "Just the Way You Are", "Bruno Mars", 2010, listOf(POP), "F", 109, EIGHTHS_ROCK, 1, LIKELY,
        "Three chords, two bars each, the whole way through: F, D minor, B flat.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus",
        """
        Intro: F | F | Dm | Dm | Bb | Bb | F | F
        Verse: (F | F | Dm | Dm | Bb | Bb | F | F) x2
        Pre-chorus: F | F | Dm | Dm | Bb | Bb | F | F
        Chorus: (F | F | Dm | Dm | Bb | Bb | F | F) x2
        Bridge: F | F | Dm | Dm | Bb | Bb | F | F
        """),
    pop("count-on-me", "Count on Me", "Bruno Mars", 2010, listOf(POP), "C", 89, QUARTERS, 2, LIKELY,
        "Ukulele friendly C major: C, E minor, A minor, G, F, with a D minor build.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus",
        """
        Intro: C | C
        Verse: (C | Em | Am G | F) x2
        Pre-chorus: Dm | Em | F | G
        Chorus: (C | Em | Am G | F) x2 | C | Em | Am G | F G
        Bridge: Dm | Em | Am | G | Dm | Em | F | G
        """),
    pop("someone-you-loved", "Someone You Loved", "Lewis Capaldi", 2018, listOf(POP, BALLAD), "Db", 110, QUARTERS, 2, LIKELY,
        "I, V, vi, IV in D flat on piano; the bridge turns to E flat minor for contrast.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus x2, Outro",
        """
        Intro: Db | Ab | Bbm | Gb
        Verse: (Db | Ab | Bbm | Gb) x4
        Chorus: (Db | Ab | Bbm | Gb) x2
        Bridge: Ebm | Bbm | Ab | Bbm | Ebm | Bbm | Ab | Ab
        Outro: Db | Ab | Bbm | Gb | Db
        """),
    pop("im-yours", "I'm Yours", "Jason Mraz", 2008, listOf(POP), "B", 76, SHUFFLE, 2, LIKELY,
        "Laid back I, V, vi, IV in B, counted in half time; C sharp 7 is a secondary dominant pointing at F sharp.",
        "Intro, Verse, Chorus, Verse#two, Chorus, Bridge, Verse, Chorus, Outro",
        """
        Intro: B | F# | G#m | E
        Verse: B | F# | G#m | E | B | F# | G#m | E
        Verse#two: B | F# | G#m | E | B | F# | G#m | E C#7
        Chorus: B | F# | G#m | E | B | F# | G#m | E
        Bridge: (B F#/A# | G#m F# | E | C#7) x2
        Outro: B | F# | G#m | E | B | F# | G#m | E | B
        """),
    pop("riptide", "Riptide", "Vance Joy", 2013, listOf(POP), "Db", 102, QUARTERS, 1, LIKELY,
        "A ukulele favourite: B flat minor and A flat share a bar, then D flat takes a whole one.",
        "Intro, Verse, Chorus, Verse, Chorus, Interlude, Bridge, Chorus x3",
        """
        Intro: (Bbm Ab | Db) x2
        Verse: (Bbm Ab | Db) x6
        Chorus: (Bbm Ab | Db) x4
        Interlude: Db | Db | Db | Db
        Bridge: Bbm | Ab | Db | Gbmaj7 | Bbm | Ab | Db | Gbmaj7 | Bbm Ab | Db | Bbm Ab | Db
        """),
    pop("let-her-go", "Let Her Go", "Passenger", 2012, listOf(POP, BALLAD), "G", 75, BALLAD_ARPEGGIO, 2, LIKELY,
        "Plain G major on piano: C, G, D and E minor, with a B minor in the verse.",
        "Intro, Chorus, Interlude, Verse, Chorus, Verse, Chorus, Bridge, Interlude, Chorus x2",
        """
        Intro: (Cmaj7 D6 | Em D6) x2
        Chorus: C G | D Em | C G | D | C G | D Em | C G | D
        Interlude: Em C | D Bm | Em C | D
        Verse: (Em C | D Bm | Em C | D) x2
        Bridge: Em | C D | Em | C D | Em C | D Bm
        """),
    pop("demons", "Demons", "Imagine Dragons", 2012, listOf(POP, ROCK), "Eb", 90, QUARTERS, 1, LIKELY,
        "I, V, vi, IV in E flat, every section, one chord a bar.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus",
        """
        Intro: Eb | Bb | Cm | Ab
        Verse: (Eb | Bb | Cm | Ab) x2
        Pre-chorus: (Eb | Bb | Cm | Ab) x2
        Chorus: (Eb | Bb | Cm | Ab) x2
        Bridge: (Eb | Bb | Cm | Ab) x2
        """),
    pop("memories", "Memories", "Maroon 5", 2019, listOf(POP), "B", 91, QUARTERS, 2, LIKELY,
        "Pachelbel's canon in B: eight chords stepping down, two to a bar.",
        "Intro, Chorus, Verse, Pre-chorus, Chorus#mid, Verse, Pre-chorus, Chorus#long, Outro",
        """
        Intro: B F# | G#m D#m | E B | E F#
        Chorus: (B F# | G#m D#m | E B | E F#) x2
        Verse: (B F# | G#m D#m | E B | E F#) x2
        Pre-chorus: B F# | G#m D#m | E B | E F#
        Chorus#mid: (B F# | G#m D#m | E B | E F#) x3
        Chorus#long: (B F# | G#m D#m | E B | E F#) x4
        Outro: B F# | G#m D#m | E B | E F#
        """),
    pop("she-will-be-loved", "She Will Be Loved", "Maroon 5", 2002, listOf(POP), "Cm", 102, QUARTERS, 2, LIKELY,
        "C minor 7 and B flat in the verse, then the chorus brightens to E flat, the relative major.",
        "Intro, Verse, Chorus, Verse, Chorus#long, Bridge, Interlude, Chorus#long, Outro",
        """
        Intro: Cm7 | Bb | Cm7 | Bb
        Verse: (Cm7 | Bb) x8
        Chorus: Eb | Bb | Cm | Bb | Eb | Bb | Cm | Ab | Eb | Bb | Cm | Ab
        Chorus#long: Eb | Bb | Cm | Bb | (Eb | Bb | Cm | Ab) x3
        Bridge: (Cm | Bb) x4 | Ab
        Interlude: Cm7 | Bb | Cm7 | Bb
        Outro: Eb | Bb | Cm | Bb | Eb | Bb | Cm | Ab
        """),
    pop("shake-it-off", "Shake It Off", "Taylor Swift", 2014, listOf(POP), "G", 160, EIGHTHS_ROCK, 1, LIKELY,
        "One four bar loop: A minor, C, then G for two bars, fast and on the beat.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Am | C | G | G
        Verse: (Am | C | G | G) x2
        Pre-chorus: (Am | C | G | G) x2
        Chorus: (Am | C | G | G) x4
        Bridge: (Am | C | G | G) x4
        Outro: (Am | C | G | G) x2
        """),
    pop("love-story", "Love Story", "Taylor Swift", 2008, listOf(POP, COUNTRY), "D", 119, QUARTERS, 2, LIKELY,
        "I, V, vi, IV in D, then the last choruses make a chromatic lift up a whole step to E.",
        "Intro, Verse, Pre-chorus, Chorus, Verse#two, Pre-chorus, Chorus x2, Interlude, Bridge, Chorus, Chorus#up x2, Outro",
        """
        Intro: D | D | A | A | Bm | Bm | G | G
        Verse: D | D | G | G | Bm | Bm | G | G | D | D | G | G | Bm | Bm | A | A
        Verse#two: D | D | G | G | Bm | Bm | A | A
        Pre-chorus: G | A | Bm | D | G | G | A | Bm
        Chorus: D | D | A | A | Bm | Bm | G | A
        Interlude: D | D | A | A | Bm | Bm | G | G
        Bridge: Bm | G | D | A | Bm | G | D | A
        Chorus#up: E | E | B | B | C#m | C#m | A | B
        Outro: B | C#m | A | E
        """),
    pop("you-belong-with-me", "You Belong with Me", "Taylor Swift", 2008, listOf(POP, COUNTRY), "F#", 130, EIGHTHS_ROCK, 1, LIKELY,
        "Banjo bright I, V, vi, IV in F sharp; the pre chorus starts on the minor chord instead.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: F# | C# | G#m | B
        Verse: (F# | C# | G#m | B) x2
        Pre-chorus: (G#m | B | F# | C#) x2
        Chorus: (F# | C# | G#m | B) x2
        Bridge: (G#m | B | F# | C#) x2
        Outro: F# | C# | G#m | B | F#
        """),
    pop("watermelon-sugar", "Watermelon Sugar", "Harry Styles", 2019, listOf(POP), "Dm", 95, QUARTERS, 1, LIKELY,
        "D minor with a modal twist: D minor 7, A minor 7, C, and a G major whose B natural is the Dorian brightness.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Dm7 | Am7 | C | G
        Verse: (Dm7 | Am7 | C | G) x2
        Chorus: (Dm7 | Am7 | C | G) x2
        Bridge: (Dm7 | Am7 | C | G) x2
        Outro: Dm7 | Am7 | C | G
        """),
    pop("drivers-license", "drivers license", "Olivia Rodrigo", 2021, listOf(POP, BALLAD), "Bb", 72, BALLAD_ARPEGGIO, 4, SKETCH,
        "Slow piano in B flat; the bridge loops G minor 7, E flat major 7, B flat, F and builds.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Bb/F | Gm7 | Eb | Eb
        Verse: Bb | Bb | Gm7 | Gm7 | Eb | Eb | Bb | Bb
        Pre-chorus: Ebmaj7 | Ebmaj7 | Bb | Bb | Ebmaj7 | Ebmaj7 | Bb | Bb
        Chorus: Gm7 | F | Bb | Dm Eb | Cm | F | Bb | Bb
        Bridge: (Gm7 | Ebmaj7 | Bb | F) x4
        Outro: Cm | F | Bb | Bb
        """),
    pop("blinding-lights", "Blinding Lights", "The Weeknd", 2019, listOf(POP), "Fm", 171, EIGHTHS_ROCK, 1, LIKELY,
        "F minor at speed with a modal B flat major, the Dorian sixth: F minor, C minor, E flat, B flat, two bars each.",
        "Intro, Verse, Pre-chorus, Chorus, Interlude, Verse, Pre-chorus, Chorus, Bridge, Chorus, Interlude, Outro",
        """
        Intro: Fm | Fm | Fm | Fm | Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb
        Verse: (Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb) x2
        Pre-chorus: Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb
        Chorus: (Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb) x2
        Interlude: Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb
        Bridge: Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb
        Outro: Fm | Fm | Cm | Cm | Eb | Eb | Bb | Bb
        """),
    pop("birds-of-a-feather", "Birds of a Feather", "Billie Eilish", 2024, listOf(POP), "D", 105, QUARTERS, 3, LIKELY,
        "I, vi, ii, V in D, one chord a bar: the oldest turnaround in pop.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Interlude, Outro",
        """
        Intro: Dmaj7 | Bm | Em7 | A
        Verse: (Dmaj7 | Bm | Em7 | A) x2
        Pre-chorus: (D | Bm7 | Em7 | A) x2
        Chorus: (D | Bm7 | Em7 | A) x2
        Interlude: Dmaj7 | Bm | Em7 | A
        Outro: Dmaj7 | Bm | Em7 | A
        """),
    pop("shallow", "Shallow", "Lady Gaga and Bradley Cooper", 2018, listOf(POP, BALLAD), "G", 96, QUARTERS, 3, SKETCH,
        "G major from E minor 7; the climb in the bridge has an A major borrowed from E Dorian.",
        "Intro, Verse, Pre-chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Em7 | D/F# G | C | G D
        Verse: (Em7 | D/F# G | C | G D) x2
        Pre-chorus: Em7 | D/F# | G | C G
        Chorus: (Am | D/F# | G D/F# | Em) x2
        Bridge: Bm | D | A | Em
        Outro: Em7 | D/F# G | C | G D
        """),
    pop("roar", "Roar", "Katy Perry", 2013, listOf(POP), "Bb", 90, EIGHTHS_ROCK, 2, LIKELY,
        "I, ii, vi, IV in B flat in every section; only the bridge swaps the last chord for F.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Bb | Cm | Gm | Eb
        Verse: (Bb | Cm | Gm | Eb) x2
        Pre-chorus: (Bb | Cm | Gm | Eb) x2
        Chorus: (Bb | Cm | Gm | Eb) x2
        Bridge: (Bb | Cm | Gm | F) x2
        Outro: Bb | Cm | Gm | Eb | Bb
        """),
    pop("love-yourself", "Love Yourself", "Justin Bieber", 2015, listOf(POP), "E", 100, BROKEN, 2, SKETCH,
        "Fingerpicked E major: B over D sharp walks the bass down a step under the verse.",
        "Verse x2, Pre-chorus, Chorus, Verse x2, Pre-chorus, Chorus, Interlude, Verse, Chorus x2",
        """
        Verse: E B/D# | C#m | F#m7 E | B/D# | E B/D# | C#m | F#m7 E | B/D#
        Pre-chorus: C#m | A E | C#m | A E | C#m | A:2 E:1 B:1 | C#m | A B
        Chorus: E B | C#m A | E B | E | E B | C#m A | E B | E
        Interlude: E B | C#m A | E B | E
        """),
    pop("flowers", "Flowers", "Miley Cyrus", 2023, listOf(POP), "Am", 118, EIGHTHS_ROCK, 2, LIKELY,
        "An A minor loop of i, iv, VII, III, and E7 at the end of each chorus pulls back to A minor.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Am | Dm | G | C
        Verse: (Am | Dm | G | C) x2
        Pre-chorus: Am | Dm | G | C
        Chorus: (Am | Dm | G | C) x3 | F | E7
        Bridge: (Am | Dm | G | C) x2
        Outro: Am | Dm | G | C | Am
        """),
)
