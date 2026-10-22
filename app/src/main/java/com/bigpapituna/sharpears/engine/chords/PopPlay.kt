package com.bigpapituna.sharpears.engine.chords

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.Section
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.groupOnsets
import com.bigpapituna.sharpears.engine.voiceLead

/**
 * A [PopSong] as something the app can play (popular-song chord mode): the chord chart as a [Song] of chord blocks the
 * player runs in Listen, Wait and Perform exactly as it runs a progression, and a generic drum-and-bass [BandBed] under
 * it. Chords, keys, tempos and section names only; nothing here knows a melody or a word (see PopSong).
 *
 * Self-contained on purpose: popc builds its own song helpers in PopSongs.kt against the same contract and the
 * integrator keeps one `toSong`. Everything of this file lives in [PopPlay] and the [BandBed] types.
 *
 * Layout of a bar of [PopSong.beatsPerBar] beats (4 unless the song says): each chord segment is played by the
 * pattern's grid. The right hand takes the voice-led chord (the nearest inversion to the one before, as the progressions
 * do), the left hand the root or the slash chord's bass. A segment that starts off the grid (a chord change on beat 3
 * of a "halves" bar) is struck where it starts.
 */
object PopPlay {
    /** The left hand's roots sit in C3..B3, like the progressions'. */
    const val LEFT_BASE = 48
    /** The band's bass sits an octave under that, C2..B2. */
    const val BASS_BASE = 36
    private const val RIGHT_VELOCITY = 75
    private const val ACCENT_VELOCITY = 82
    private const val LEFT_VELOCITY = 80
    private const val GAP_MS = 60L

    /** The pattern in one line a learner reads when picking it. */
    fun feel(p: PopSong.Pattern): String = when (p) {
        PopSong.Pattern.WHOLE -> "One chord held for as long as it lasts. The easiest way in."
        PopSong.Pattern.HALVES -> "A chord on the first and third beat, the bass with it."
        PopSong.Pattern.QUARTERS -> "A steady chord on every beat, the bass on one and three."
        PopSong.Pattern.BROKEN -> "Chord held up high while the left hand rocks root, fifth, third, fifth."
        PopSong.Pattern.BALLAD_ARPEGGIO -> "The chord rolled upwards and back, one note at a time, over a long bass."
        PopSong.Pattern.SHUFFLE -> "Long-short, long-short on every beat: a swung chord."
        PopSong.Pattern.EIGHTHS_ROCK -> "Chords in steady eighths with a bass on every beat. Drives like a rock band."
    }

    fun patternName(p: PopSong.Pattern): String = when (p) {
        PopSong.Pattern.WHOLE -> "Whole"
        PopSong.Pattern.HALVES -> "Halves"
        PopSong.Pattern.QUARTERS -> "Quarters"
        PopSong.Pattern.BROKEN -> "Broken"
        PopSong.Pattern.BALLAD_ARPEGGIO -> "Ballad"
        PopSong.Pattern.SHUFFLE -> "Shuffle"
        PopSong.Pattern.EIGHTHS_ROCK -> "Rock"
    }

    // --- keys and names ---------------------------------------------------------------------------------------

    /** The semitones up (0..11) from the song's own key to [key]. */
    fun shift(song: PopSong, key: Int): Int = Math.floorMod(key - song.key, 12)

    /** The same shift as the nearest step for a stepper: -6..+5, so "down 2" is not "up 10". */
    fun nearest(shift: Int): Int = Math.floorMod(shift + 6, 12) - 6

    /** The key reading in flats (F, B flat, E flat, A flat, D flat major, or the minors that share them). */
    fun flats(key: Int, minor: Boolean): Boolean = Math.floorMod(if (minor) key + 3 else key, 12) in setOf(5, 10, 3, 8, 1)

    fun moved(c: PopChord, by: Int): PopChord =
        if (by % 12 == 0) c else PopChord(Math.floorMod(c.root + by, 12), c.type, c.bass?.let { Math.floorMod(it + by, 12) })

    /** "Am7", "C/E", "Bb" (flats when the key reads in flats). */
    fun name(c: PopChord, flats: Boolean): String {
        val root = Chord(c.root, c.type).nameIn(flats)
        return if (c.slash) root + "/" + Chord(c.bass!!, ChordType.MAJOR).nameIn(flats) else root
    }

    /** The key as it is read: "C", "Eb", "F#m". */
    fun keyName(key: Int, minor: Boolean): String =
        Chord(key, if (minor) ChordType.MINOR else ChordType.MAJOR).nameIn(flats(key, minor))

    /** The dictionary chord of a pop chord (the slash bass is a left-hand matter, the chord is the root's). */
    fun chord(c: PopChord): Chord = Chord(c.root, c.type)

    // --- the chart ---------------------------------------------------------------------------------------------

    /** One chord for [beats] beats, starting [startBeat] beats into the song. */
    data class Slot(val chord: PopChord, val startBeat: Int, val beats: Int, val beatInBar: Int)

    /** One bar of the chart: its place in the song, in its section, and its chords. */
    data class BarView(val index: Int, val section: Int, val indexInSection: Int, val slots: List<Slot>)

    /** The song's bars in play order, chords moved up [by] semitones. */
    fun bars(song: PopSong, by: Int = 0): List<BarView> {
        var beat = 0
        var index = 0
        val out = mutableListOf<BarView>()
        song.sections.forEachIndexed { si, s ->
            s.bars.forEachIndexed { bi, bar ->
                var inBar = 0
                val slots = bar.chords.map { pb ->
                    Slot(moved(pb.chord, by), beat + inBar, pb.beats, inBar).also { inBar += pb.beats }
                }
                out += BarView(index++, si, bi, slots)
                beat += inBar
            }
        }
        return out
    }

    /** Total beats of the song. */
    fun beats(song: PopSong): Int = song.sections.sumOf { s -> s.bars.sumOf { b -> b.chords.sumOf { it.beats } } }

    /** The length of the song in ms at [bpm]. */
    fun durationMs(song: PopSong, bpm: Int = song.bpm): Long = Math.round(beats(song) * 60_000.0 / bpm)

    /** The index of the bar sounding at [ms] (clamped to the chart). */
    fun barAt(song: PopSong, ms: Double, bpm: Int = song.bpm): Int {
        val perBar = song.beatsPerBar * 60_000.0 / bpm
        val n = song.sections.sumOf { it.bars.size }
        return (ms / perBar).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
    }

    // --- the song ----------------------------------------------------------------------------------------------

    /** A strike: [at] beats into the bar, lasting [len] beats, the [step]th since its chord began. */
    internal data class Hit(val at: Double, val len: Double, val step: Int)

    private fun grid(step: Double, bpb: Int): List<Double> {
        val out = mutableListOf<Double>()
        var t = 0.0
        while (t < bpb - 1e-9) { out += t; t += step }
        return out
    }

    /** Where the right hand strikes in a bar of [bpb] beats. */
    internal fun rightGrid(p: PopSong.Pattern, bpb: Int): List<Double> = when (p) {
        PopSong.Pattern.WHOLE, PopSong.Pattern.BROKEN -> listOf(0.0)
        PopSong.Pattern.HALVES -> grid(2.0, bpb)
        PopSong.Pattern.QUARTERS -> grid(1.0, bpb)
        PopSong.Pattern.BALLAD_ARPEGGIO, PopSong.Pattern.EIGHTHS_ROCK -> grid(0.5, bpb)
        PopSong.Pattern.SHUFFLE -> (0 until bpb).flatMap { listOf(it.toDouble(), it + 2.0 / 3) }
    }

    /** Where the left hand strikes in a bar of [bpb] beats. */
    internal fun leftGrid(p: PopSong.Pattern, bpb: Int): List<Double> = when (p) {
        PopSong.Pattern.WHOLE, PopSong.Pattern.BALLAD_ARPEGGIO -> listOf(0.0)
        PopSong.Pattern.HALVES, PopSong.Pattern.QUARTERS, PopSong.Pattern.SHUFFLE -> grid(2.0, bpb)
        PopSong.Pattern.BROKEN, PopSong.Pattern.EIGHTHS_ROCK -> grid(1.0, bpb)
    }

    /**
     * The strikes of one chord that lasts [len] beats from [off] beats into its bar, on [grid]: every grid point inside
     * it, plus the start itself (a chord change is always struck). Each lasts until the next, or the chord's end.
     */
    internal fun hitsIn(grid: List<Double>, off: Int, len: Int): List<Hit> {
        val end = off + len
        val points = (grid.filter { it > off + 1e-9 && it < end - 1e-9 } + off.toDouble()).sorted()
        return points.mapIndexed { i, p -> Hit(p, (points.getOrNull(i + 1) ?: end.toDouble()) - p, i) }
    }

    private fun beatMs(bpm: Int) = 60_000.0 / bpm
    private fun at(beat: Double, bpm: Int) = Math.round(beat * beatMs(bpm))
    private fun length(len: Double, bpm: Int) = maxOf(80L, Math.round(len * beatMs(bpm)) - GAP_MS)

    /** The chord's note a third and a fifth above its root, as the left hand's broken pattern wants them. */
    private fun third(t: ChordType) = t.intervals.getOrElse(1) { 4 } % 12
    private fun fifth(t: ChordType) = t.intervals.getOrElse(2) { 7 } % 12

    /**
     * [song] in [key] (its own key by default) as a [Song]: [pattern] decides the rhythm. Sections are carried over, the
     * time signature is `beatsPerBar` over 4, and the black keys read as flats when the key does. The first beat of the
     * song is at 0 ms; the player's count-in runs before it.
     */
    fun toSong(song: PopSong, pattern: PopSong.Pattern = song.pattern, key: Int = song.key, bpm: Int = song.bpm): Song {
        val by = shift(song, key)
        val bpb = song.beatsPerBar
        val bars = bars(song, by)
        val slots = bars.flatMap { it.slots }
        val voicings = voiceLead(slots.map { chord(it.chord) })
        val rGrid = rightGrid(pattern, bpb)
        val lGrid = leftGrid(pattern, bpb)
        val fingerOf = HashMap<Note, Int>()
        val notes = mutableListOf<Note>()
        var cursor = 0
        for (bar in bars) {
            for (slot in bar.slots) {
                val c = chord(slot.chord)
                val v = voicings[cursor++]
                val base = slot.startBeat - slot.beatInBar            // the bar's first beat
                val bass = LEFT_BASE + Math.floorMod(slot.chord.bass ?: slot.chord.root, 12)
                // Right hand.
                val inversion = c.type.intervals.indexOfFirst { Math.floorMod(c.root + it, 12) == Math.floorMod(v.first(), 12) }
                val triad = c.type.intervals.size == 3 && v.size == 3 && inversion >= 0
                val arpeggio = pattern == PopSong.Pattern.BALLAD_ARPEGGIO
                val order = v.indices.toList() + (v.size - 2 downTo 1)
                hitsIn(rGrid, slot.beatInBar, slot.beats).forEach { h ->
                    val start = at(base + h.at, bpm)
                    val dur = length(h.len, bpm)
                    val vel = if (h.at == 0.0) ACCENT_VELOCITY else RIGHT_VELOCITY
                    if (arpeggio) {
                        val m = v[order[h.step % order.size]]
                        Note(m, start, dur, Hand.RIGHT, vel).also { notes += it }
                    } else v.forEach { m ->
                        val n = Note(m, start, dur, Hand.RIGHT, vel)
                        notes += n
                        if (triad) fingerOf[n] = c.fingering(inversion)[v.indexOf(m)]
                    }
                }
                // Left hand.
                hitsIn(lGrid, slot.beatInBar, slot.beats).forEach { h ->
                    val midi = if (pattern == PopSong.Pattern.BROKEN) {
                        // Root, fifth, third, fifth: the lowest, the highest, the middle, the highest.
                        fun above(semis: Int): Int = bass + Math.floorMod(slot.chord.root + semis - bass, 12).let { if (it == 0) 12 else it }
                        when (h.step % 4) { 0 -> bass; 1, 3 -> above(fifth(c.type)); else -> above(third(c.type)) }
                    } else bass
                    notes += Note(midi, at(base + h.at, bpm), length(h.len, bpm), Hand.LEFT, LEFT_VELOCITY)
                }
            }
        }
        val sorted = notes.sortedWith(compareBy({ it.startMs }, { it.midi }))
        // Sections at their first bar.
        var beat = 0
        val sections = song.sections.map { s ->
            val from = beat
            beat += s.bars.sumOf { b -> b.chords.sumOf { it.beats } }
            Section(s.name, at(from.toDouble(), bpm), at(beat.toDouble(), bpm))
        }
        val flatKey = flats(Math.floorMod(song.key + by, 12), song.minor)
        val base = Song("pop-${song.id}-${pattern.name.lowercase()}-${Math.floorMod(song.key + by, 12)}-$bpm", song.title, bpm.toDouble(), bpb to 4, sorted,
            groupOnsets(sorted), sections, durationMs(song, bpm), preferFlats = flatKey)
        // The dictionary's fingers for the triads it shows, the solver's for the rest, one per right-hand note in time order.
        val worked = Fingering.of(base)
        return base.copy(fingering = sorted.filter { it.hand == Hand.RIGHT }.map { fingerOf[it] ?: worked[it] ?: 1 })
    }

    // --- the band ----------------------------------------------------------------------------------------------

    /** The drum kit's three pieces: a kick, a snare and a closed hat. */
    enum class Kit { KICK, SNARE, HAT }

    internal data class Beat(val at: Double, val kit: Kit, val velocity: Int)

    /** A bar of the drum groove for [pattern] in [bpb] beats. */
    internal fun groove(pattern: PopSong.Pattern, bpb: Int): List<Beat> {
        val even = bpb % 2 == 0
        val mid = bpb / 2
        val light = pattern == PopSong.Pattern.WHOLE || pattern == PopSong.Pattern.BALLAD_ARPEGGIO
        val out = mutableListOf<Beat>()
        out += Beat(0.0, Kit.KICK, if (light) 70 else 100)
        if (even) out += Beat(mid.toDouble(), Kit.KICK, if (light) 55 else 90)
        // The backbeat: the snare on the even beats (2 and 4), a waltz's soft brush on 2 and 3.
        if (even) for (b in 1 until bpb step 2) out += Beat(b.toDouble(), Kit.SNARE, if (light) 45 else 100)
        else for (b in 1 until bpb) out += Beat(b.toDouble(), Kit.SNARE, 40)
        when (pattern) {
            PopSong.Pattern.WHOLE, PopSong.Pattern.BALLAD_ARPEGGIO, PopSong.Pattern.HALVES, PopSong.Pattern.BROKEN ->
                for (b in 0 until bpb) out += Beat(b.toDouble(), Kit.HAT, if (b % 2 == 0) 55 else 40)
            PopSong.Pattern.QUARTERS, PopSong.Pattern.EIGHTHS_ROCK -> {
                for (i in 0 until bpb * 2) out += Beat(i / 2.0, Kit.HAT, if (i % 2 == 0) 62 else 38)
                if (pattern == PopSong.Pattern.EIGHTHS_ROCK && even) out += Beat(mid + 0.5, Kit.KICK, 85)
            }
            PopSong.Pattern.SHUFFLE -> for (b in 0 until bpb) {
                out += Beat(b.toDouble(), Kit.HAT, 62)
                out += Beat(b + 2.0 / 3, Kit.HAT, 40)
            }
        }
        return out
    }

    /**
     * The band's bed under [song]: drums and a bass, generic and made from the chords and the pattern alone. The bass
     * plays root, fifth and so on in C2..B2 by the pattern; the player's band plays this when "Backing" is on (the app
     * also plays the left hand then, an octave above).
     */
    fun bandBed(song: PopSong, pattern: PopSong.Pattern = song.pattern, key: Int = song.key, bpm: Int = song.bpm): BandBed {
        val by = shift(song, key)
        val bpb = song.beatsPerBar
        val cues = mutableListOf<BandCue>()
        val grooveBar = groove(pattern, bpb)
        for (bar in bars(song, by)) {
            val base = bar.slots.first().startBeat - bar.slots.first().beatInBar
            val barLen = bar.slots.sumOf { it.beats }
            grooveBar.filter { it.at < barLen - 1e-9 }.forEach { b ->
                cues += BandCue.Drum(at(base + b.at, bpm), b.kit, b.velocity)
            }
            for (slot in bar.slots) {
                val rootPc = Math.floorMod(slot.chord.bass ?: slot.chord.root, 12)
                val chordType = slot.chord.type
                for (h in hitsIn(bassGrid(pattern, bpb), slot.beatInBar, slot.beats)) {
                    val semis = when (pattern) {
                        PopSong.Pattern.HALVES, PopSong.Pattern.QUARTERS -> if (h.step % 2 == 0) 0 else 7
                        PopSong.Pattern.SHUFFLE -> intArrayOf(0, third(chordType), 7, 9)[h.step % 4]
                        else -> 0
                    }
                    val midi = BASS_BASE + rootPc + semis
                    val start = at(base + h.at, bpm)
                    cues += BandCue.BassOn(start, midi, if (h.at == 0.0) 100 else 88)
                    cues += BandCue.BassOff(start + length(h.len, bpm), midi)
                }
            }
        }
        return BandBed(bpm, bpb, durationMs(song, bpm), cues.sortedWith(compareBy({ it.atMs }, { if (it is BandCue.BassOff) 0 else 1 })))
    }

    internal fun bassGrid(p: PopSong.Pattern, bpb: Int): List<Double> = when (p) {
        PopSong.Pattern.WHOLE, PopSong.Pattern.BALLAD_ARPEGGIO -> listOf(0.0)
        PopSong.Pattern.HALVES, PopSong.Pattern.BROKEN -> grid(2.0, bpb)
        PopSong.Pattern.QUARTERS, PopSong.Pattern.SHUFFLE -> grid(1.0, bpb)
        PopSong.Pattern.EIGHTHS_ROCK -> grid(0.5, bpb)
    }
}

/** One thing the band does at a moment: a drum hit, or a bass note starting or ending. */
sealed interface BandCue {
    val atMs: Long
    data class Drum(override val atMs: Long, val kit: PopPlay.Kit, val velocity: Int) : BandCue
    data class BassOn(override val atMs: Long, val midi: Int, val velocity: Int) : BandCue
    data class BassOff(override val atMs: Long, val midi: Int) : BandCue
}

/**
 * The drums and bass that play under a pop song, in song time (0 = the song's first beat). The player asks for the cues
 * the playhead passed since the last frame ([cuesIn]) and plays them, as it asks the metronome for its clicks.
 */
class BandBed(val bpm: Int, val beatsPerBar: Int, val durationMs: Long, val cues: List<BandCue>) {
    private val starts = cues.map { it.atMs }

    /** The cues with `fromMs <= atMs < toMs`, in order. */
    fun cuesIn(fromMs: Double, toMs: Double): List<BandCue> {
        if (toMs <= fromMs || cues.isEmpty()) return emptyList()
        var lo = 0
        var hi = starts.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (starts[mid] < fromMs) lo = mid + 1 else hi = mid }
        val out = mutableListOf<BandCue>()
        var i = lo
        while (i < cues.size && cues[i].atMs < toMs) out += cues[i++]
        return out
    }

    val drums: List<BandCue.Drum> get() = cues.filterIsInstance<BandCue.Drum>()
    val bass: List<BandCue.BassOn> get() = cues.filterIsInstance<BandCue.BassOn>()
}
