package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern
import com.bigpapituna.sharpears.engine.chords.PopSong.Style

/**
 * Popular songs as chords only (docs/CONTENT.md, "Popular songs by chords"). The charts live in the PopSongs*.kt files
 * next to this one, one file per style batch, written in the small chart language parsed below; this file joins them
 * and holds the discovery helpers the chord-mode screens use.
 *
 * Nothing here is a melody, a lyric or a recording: a key, a tempo, a section order and the chords of each bar.
 */
val POP_SONGS: List<PopSong> by lazy { HITS_SONGS + YACHT_SONGS + EIGHTIES_SONGS }

object PopSongs {
    /** Songs whose every chord is already mastered, and songs one new chord away (with that chord). */
    data class Playable(val ready: List<PopSong>, val oneAway: List<Pair<PopSong, Chord>>)

    /** The chords a learner has to know for [song]: root and quality (a slash chord's bass is the left hand's job). */
    fun chordsOf(song: PopSong): Set<Chord> = song.chords.map { Chord(Math.floorMod(it.root, 12), it.type) }.toSet()

    fun playableWith(mastered: Set<Chord>, songs: List<PopSong> = POP_SONGS): Playable {
        val known = mastered.map { Chord(Math.floorMod(it.root, 12), it.type) }.toSet()
        val ready = mutableListOf<PopSong>()
        val oneAway = mutableListOf<Pair<PopSong, Chord>>()
        for (s in songs) {
            val missing = chordsOf(s) - known
            when (missing.size) { 0 -> ready += s; 1 -> oneAway += s to missing.single() }
        }
        return Playable(ready, oneAway)
    }

    fun byStyle(style: Style, songs: List<PopSong> = POP_SONGS): List<PopSong> = songs.filter { style in it.styles }

    fun byStyle(songs: List<PopSong> = POP_SONGS): Map<Style, List<PopSong>> =
        Style.entries.associateWith { st -> songs.filter { st in it.styles } }.filterValues { it.isNotEmpty() }

    /**
     * The "four chords" songs: a major key, and every chord is its I, IV, V or vi, in any voicing (the slash bass is
     * ignored): a triad, a sus or add9 colour, a 6, Imaj7 or IVmaj7, V7, vi7. A minor-key song is never one of them.
     */
    fun isFourChord(song: PopSong): Boolean {
        if (song.minor) return false
        return song.chords.all { c ->
            when (Math.floorMod(c.root - song.key, 12)) {
                0, 5 -> c.type in setOf(ChordType.MAJOR, ChordType.SUS2, ChordType.SUS4, ChordType.ADD9, ChordType.SIX, ChordType.MAJ7)
                7 -> c.type in setOf(ChordType.MAJOR, ChordType.SUS2, ChordType.SUS4, ChordType.ADD9, ChordType.SIX, ChordType.DOM7)
                9 -> c.type in setOf(ChordType.MINOR, ChordType.MIN7)
                else -> false
            }
        }
    }

    fun fourChordSongs(songs: List<PopSong> = POP_SONGS): List<PopSong> = songs.filter(::isFourChord)

    /** Easiest first: difficulty, then the four-chord songs, then the newest. */
    fun sortedForBeginners(songs: List<PopSong> = POP_SONGS): List<PopSong> =
        songs.sortedWith(compareBy<PopSong> { it.difficulty }.thenBy { !isFourChord(it) }.thenByDescending { it.year })

    /** The same song [semitones] higher (or lower): the key, every root and every bass move together. */
    fun transposed(song: PopSong, semitones: Int): PopSong {
        if (Math.floorMod(semitones, 12) == 0) return song
        fun move(c: PopChord) = c.copy(root = Math.floorMod(c.root + semitones, 12), bass = c.bass?.let { Math.floorMod(it + semitones, 12) })
        return song.copy(
            key = Math.floorMod(song.key + semitones, 12),
            sections = song.sections.map { s -> s.copy(bars = s.bars.map { b -> PopBar(b.chords.map { it.copy(chord = move(it.chord)) }) }) },
        )
    }

    /**
     * How hard the chart is, by rule (the content's difficulty must match it; PopSongsTest): one point to start, one
     * for five or more different chords and another for eight or more, one for three or more colour chords (anything
     * past a plain major or minor triad, slash chords included), one for chord changes coming faster than about one a
     * second (0.9 a second or more). Capped at 4.
     */
    fun difficultyByRule(song: PopSong): Int {
        val distinct = song.chords
        val colour = distinct.count { it.slash || it.type !in setOf(ChordType.MAJOR, ChordType.MINOR) }
        val bars = song.sections.flatMap { it.bars }
        var changes = 0
        var last: PopChord? = null
        for (b in bars) for (beat in b.chords) { if (beat.chord != last) changes++; last = beat.chord }
        val seconds = bars.size * song.beatsPerBar * 60.0 / song.bpm
        val perSecond = if (seconds > 0) changes / seconds else 0.0
        var d = 1
        if (distinct.size >= 5) d++
        if (distinct.size >= 8) d++
        if (colour >= 3) d++
        if (perSecond >= 0.9) d++
        return d.coerceIn(1, 4)
    }
}

// ---- The chart language (PopSongs*.kt) ----
//
// A chart is one line per section: "Verse: C G | Am F | C G | F C". Bars are split by "|"; chords in a bar share it
// evenly unless one says its beats ("C:3 G:1"). "(C G | Am F) x2" writes the bars out twice. Chords are a root
// (C, F#, Bb), a ChordType suffix (m, 7, maj7, m7, sus4, add9, dim, ...) and an optional slash bass (G/B). The order
// names sections as they occur, "x2" repeating one; a name may carry "#tag" to tell two versions apart (a chorus
// after a key change), and the tag is dropped from what the learner sees. Compound time is counted in its
// dotted-quarter pulse: 12/8 as four beats, 6/8 as two.

private val ROOTS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
private val SUFFIXES = ChordType.entries.associateBy { it.suffix }

private fun pitch(s: String): Int {
    val base = ROOTS[s.first()] ?: error("bad note $s")
    val acc = s.drop(1).sumOf { when (it) { '#' -> 1; 'b' -> -1; else -> error("bad note $s") }.toInt() }
    return Math.floorMod(base + acc, 12)
}

internal fun parseChord(token: String): PopChord {
    val (main, bass) = token.split('/').let { it[0] to it.getOrNull(1) }
    val rootLen = if (main.length > 1 && (main[1] == '#' || main[1] == 'b')) 2 else 1
    val type = SUFFIXES[main.drop(rootLen)] ?: error("bad chord $token")
    return PopChord(pitch(main.take(rootLen)), type, bass?.let(::pitch))
}

private val REPEAT = Regex("""\(([^()]*)\)\s*x(\d+)""")

internal fun parseBars(text: String, beatsPerBar: Int): List<PopBar> {
    val expanded = REPEAT.replace(text) { m -> List(m.groupValues[2].toInt()) { m.groupValues[1].trim() }.joinToString(" | ") }
    return expanded.split('|').map { it.trim() }.filter { it.isNotEmpty() }.map { bar ->
        val tokens = bar.split(Regex("\\s+"))
        val explicit = tokens.any { ':' in it }
        require(explicit || beatsPerBar % tokens.size == 0) { "bar \"$bar\" does not split $beatsPerBar beats evenly" }
        val beats = tokens.map { t ->
            val (c, b) = t.split(':').let { it[0] to it.getOrNull(1)?.let { n -> n.toIntOrNull() ?: error("bad beats in $t") } }
            PopBeat(parseChord(c), b ?: (beatsPerBar / tokens.size))
        }
        require(beats.sumOf { it.beats } == beatsPerBar) { "bar \"$bar\" has ${beats.sumOf { it.beats }} beats, not $beatsPerBar" }
        PopBar(beats)
    }
}

/** One song from its chart. [key] is "C", "F#m", "Bb"; [order] "Intro, Verse x2, Chorus"; [chart] "Name: bars" lines. */
internal fun pop(
    id: String, title: String, artist: String, year: Int, styles: List<Style>, key: String, bpm: Int, pattern: Pattern,
    difficulty: Int, confidence: PopSong.Confidence, note: String, order: String, chart: String, beatsPerBar: Int = 4,
): PopSong {
    try {
        val minor = key.endsWith("m")
        val lines = chart.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val defs = lines.associate { line ->
            val name = line.substringBefore(':').trim()
            name to parseBars(line.substringAfter(':'), beatsPerBar)
        }
        require(defs.size == lines.size) { "a section is charted twice" }
        val sections = order.split(',').map { it.trim() }.flatMap { item ->
            val m = Regex("""^(.*?)\s+x(\d+)$""").find(item)
            val name = m?.groupValues?.get(1) ?: item
            val times = m?.groupValues?.get(2)?.toInt() ?: 1
            val bars = defs[name] ?: error("no chart line for section $name")
            List(times) { PopSection(name.substringBefore('#').trim(), bars) }
        }
        val unused = defs.keys - order.split(',').map { it.trim().replace(Regex("""\s+x\d+$"""), "") }.toSet()
        require(unused.isEmpty()) { "charted but not in the order: $unused" }
        return PopSong(id, title, artist, year, styles, pitch(key.removeSuffix("m")), minor, bpm, beatsPerBar, sections,
            pattern, difficulty, confidence, note)
    } catch (e: Exception) {
        throw IllegalArgumentException("pop song $id: ${e.message}", e)
    }
}
