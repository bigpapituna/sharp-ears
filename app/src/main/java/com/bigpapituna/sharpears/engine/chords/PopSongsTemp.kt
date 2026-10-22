package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.*
import com.bigpapituna.sharpears.engine.chords.PopSong.Style.*

// PRIVATE, TEMPORARY, PERSONAL USE (docs/PLAN-2.8.md, section 4). Six Michael Jackson songs as chords only: key, tempo,
// section order and the chords of each bar, nothing else (the PopSong.kt contract). Every song here is privateOnly, so
// it appears only in a build with PRIVATE_BUILD=true in secrets.properties (Roy's PC). Every chart is a SKETCH: the
// keys, tempos and bar lengths are written from memory and not checked against anything.
//
// DELETE THIS FILE BEFORE ANY RELEASE, and remove TEMP_SONGS from POP_SONGS' source list in PopSongs.kt.
// PopSongsTest.privateSongsMustNotShipPublic fails in a public build until you do.

internal val TEMP_SONGS: List<PopSong> = listOf(
    pop("billie-jean", "Billie Jean", "Michael Jackson", 1983, listOf(POP, EIGHTIES_ROCK), "F#m", 117, QUARTERS, 2, SKETCH,
        "One minor seventh chord rides for bars on end with Bm7 as the only move, and the pre-chorus climbs down the scale; sketch, bar counts are a guess.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: (F#m7) x4
        Verse: (F#m7) x4 | (Bm7) x2 | (F#m7) x2
        Pre-chorus: Dmaj7 | C#m7 | Bm7 | Bm7
        Chorus: (F#m7) x2 | Bm7 | Bm7 | F#m7 | F#m7 | Bm7 | Bm7
        Bridge: Dmaj7 | C#m7 | Bm7 | Bm7 | Dmaj7 | C#m7 | Bm7 | Bm7
        Outro: (F#m7) x4
        """, privateOnly = true),
    pop("beat-it", "Beat It", "Michael Jackson", 1983, listOf(POP, EIGHTIES_ROCK, ROCK), "Em", 139, EIGHTHS_ROCK, 1, SKETCH,
        "Three chords, E minor, D and C, in a hard rock chug; sketch, the key and where the chorus lands are from memory.",
        "Intro, Verse, Chorus, Verse, Chorus, Solo, Chorus, Outro",
        """
        Intro: (Em) x4 | D | D | Em | Em
        Verse: Em | Em | D | D | Em | Em | D | D
        Chorus: C | D | Em | Em | C | D | Em | Em
        Solo: (Em) x2 | D | D | C | C | D | D
        Outro: Em | D | C | D | Em | Em
        """, privateOnly = true),
    pop("man-in-the-mirror", "Man in the Mirror", "Michael Jackson", 1987, listOf(POP, BALLAD), "G", 100, QUARTERS, 2, SKETCH,
        "A slow gospel feel on G, C, D and E minor with a minor seventh passing through; sketch, the closing key change is left out.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus x2, Outro",
        """
        Intro: G | G | C | D
        Verse: G | G | C | D | G | G | C | D
        Pre-chorus: Em | Em | C | D
        Chorus: G | Am7 | C | D | G | Am7 | C | D
        Bridge: Em | D | C | C | Em | D | C | D
        Outro: G | Am7 | C | D | G
        """, privateOnly = true),
    pop("human-nature", "Human Nature", "Michael Jackson", 1983, listOf(POP, BALLAD), "F#", 93, BROKEN, 3, SKETCH,
        "Soft major sevenths and minor sevenths in a gentle loop over a sus chord on the five; sketch, key and bar counts are a guess.",
        "Intro, Verse, Chorus, Verse, Chorus, Bridge, Chorus, Outro",
        """
        Intro: F#maj7 | Bmaj7 | G#m7 | C#sus4
        Verse: F#maj7 | Bmaj7 | G#m7 | C#sus4 | F#maj7 | Bmaj7 | G#m7 | C#sus4
        Chorus: Bmaj7 | A#m7 | G#m7 | C#sus4 | Bmaj7 | A#m7 | G#m7 | C#sus4
        Bridge: Bmaj7 | C#sus4 | F#maj7 | F#maj7
        Outro: F#maj7 | Bmaj7 | G#m7 | C#sus4
        """, privateOnly = true),
    pop("rock-with-you", "Rock With You", "Michael Jackson", 1979, listOf(POP, SOUL), "Bb", 114, QUARTERS, 3, SKETCH,
        "A smooth disco groove on major sevenths and minor sevenths, with the dominant seventh pulling back to the start; sketch, key and tempo are from memory.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: Bbmaj7 | Gm7 | Cm7 | F7
        Verse: Bbmaj7 | Gm7 | Cm7 | F7 | Bbmaj7 | Gm7 | Cm7 | F7
        Pre-chorus: Ebmaj7 | Dm7 | Cm7 | F7
        Chorus: Bbmaj7 | Gm7 | Ebmaj7 | F7 | Bbmaj7 | Gm7 | Ebmaj7 | F7
        Bridge: Ebmaj7 | Dm7 | Cm7 | F7
        Outro: Bbmaj7 | Gm7 | Cm7 | F7
        """, privateOnly = true),
    pop("the-way-you-make-me-feel", "The Way You Make Me Feel", "Michael Jackson", 1987, listOf(POP, EIGHTIES_ROCK), "Bm", 114, SHUFFLE, 1, SKETCH,
        "A minor groove that mostly stays on the one chord with G and A for lift; sketch, the key is from memory and the bar counts are a guess.",
        "Intro, Verse, Pre-chorus, Chorus, Verse, Pre-chorus, Chorus, Bridge, Chorus, Outro",
        """
        Intro: (Bm) x4
        Verse: (Bm) x4 | G | G | A | A
        Pre-chorus: G | A | Bm | Bm
        Chorus: G | A | Bm | Bm | G | A | Bm | Bm
        Bridge: G | A | Bm | Bm
        Outro: (Bm) x4
        """, privateOnly = true),
)
