package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.ChordType

/**
 * A popular song as its chords only (Roy, 2026-10-22: "give these specific chords for these popular songs so they can
 * learn them"). The chord progression, key, tempo and section shape of a song are facts and not protected; the melody,
 * lyrics and recordings are, so nothing here may carry a melody, a lyric or a MIDI of the song, in any build. The player
 * shows chord blocks, the band plays a drum and bass bed, and the learner plays (and sings, on their own) the chords.
 *
 * Contract for the content (engine/chords/PopSongs.kt), the player's chord mode (ui/chords) and the discovery screens.
 */
data class PopSong(
    val id: String,                      // stable, kebab-case, e.g. "lets-stay-together"
    val title: String,
    val artist: String,
    val year: Int,
    val styles: List<Style>,
    val key: Int,                        // 0..11, the song's own key (0 = C)
    val minor: Boolean,
    val bpm: Int,
    val beatsPerBar: Int = 4,
    val sections: List<PopSection>,      // in play order; a section may repeat by name
    val pattern: Pattern,                // the accompaniment the band suggests
    val difficulty: Int,                 // 1 easy (three or four chords, slow) .. 4 (sevenths, quick changes, slash chords)
    val confidence: Confidence,          // how sure the content is (docs/CONTENT.md style)
    val note: String = "",               // one line a learner reads: the chords' trick, the feel
    /** Never in a public build: shown only when BuildConfig.PRIVATE_BUILD is true; the file that holds it is deleted before release. */
    val privateOnly: Boolean = false,
) {
    enum class Style { GREATEST_HITS, YACHT_ROCK, EIGHTIES_ROCK, POP, ROCK, COUNTRY, SOUL, BALLAD, KIDS, HOLIDAY, WORSHIP }
    enum class Pattern { WHOLE, HALVES, QUARTERS, BROKEN, BALLAD_ARPEGGIO, SHUFFLE, EIGHTHS_ROCK }
    enum class Confidence { CHECKED, LIKELY, SKETCH }

    /** Every distinct chord in the song, in order of first use. */
    val chords: List<PopChord> get() = sections.flatMap { s -> s.bars.flatMap { it.chords.map { c -> c.chord } } }.distinct()
}

/** A named section ("Verse", "Chorus", "Bridge", "Intro", "Outro"), with its bars in order. */
data class PopSection(val name: String, val bars: List<PopBar>)

/** One bar: chords with the beats each takes; the beats sum to the song's beatsPerBar. */
data class PopBar(val chords: List<PopBeat>) {
    constructor(vararg chords: PopChord) : this(chords.map { PopBeat(it, 4 / chords.size) })
}

data class PopBeat(val chord: PopChord, val beats: Int)

/** A chord as the learner reads it: root 0..11, quality, optional bass note for slash chords (root 0..11). */
data class PopChord(val root: Int, val type: ChordType, val bass: Int? = null) {
    val slash get() = bass != null && bass != root
}
