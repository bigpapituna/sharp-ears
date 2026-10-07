package com.bigpapituna.sharpears.engine

import kotlin.math.abs

enum class ChordType(val suffix: String, val label: String, val intervals: List<Int>) {
    MAJOR("", "major", listOf(0, 4, 7)),
    MINOR("m", "minor", listOf(0, 3, 7)),
    DIM("dim", "diminished", listOf(0, 3, 6)),
    AUG("aug", "augmented", listOf(0, 4, 8)),
    SUS2("sus2", "sus2", listOf(0, 2, 7)),
    SUS4("sus4", "sus4", listOf(0, 5, 7)),
    SIX("6", "6", listOf(0, 4, 7, 9)),
    MIN6("m6", "minor 6", listOf(0, 3, 7, 9)),
    DOM7("7", "dominant 7", listOf(0, 4, 7, 10)),
    MAJ7("maj7", "major 7", listOf(0, 4, 7, 11)),
    MIN7("m7", "minor 7", listOf(0, 3, 7, 10)),
    DIM7("dim7", "diminished 7", listOf(0, 3, 6, 9)),
    HALF_DIM("m7b5", "half-diminished", listOf(0, 3, 6, 10)),
    ADD9("add9", "add 9", listOf(0, 4, 7, 14)),
    NINE("9", "dominant 9", listOf(0, 4, 7, 10, 14)),
}

private val ROOT_NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
fun rootName(pc: Int) = ROOT_NAMES[((pc % 12) + 12) % 12]

data class Chord(val root: Int, val type: ChordType) {
    val name get() = rootName(root) + type.suffix
    val pitchClasses: Set<Int> get() = type.intervals.map { (root + it) % 12 }.toSet()
    // Ninths sit above the octave; "9th in the bass" is not offered as an inversion.
    val inversionCount get() = type.intervals.count { it < 12 }

    /** Notes from the root in the octave starting at [base], then the lowest [inversion] notes moved up an octave. */
    fun voicing(inversion: Int = 0, base: Int = 60): List<Int> {
        val notes = type.intervals.map { base + root % 12 + it }.toMutableList()
        repeat(inversion.coerceIn(0, inversionCount - 1)) { notes.sort(); notes[0] += 12 }
        return notes.sorted()
    }

    /** Right-hand fingering, lowest note first (1 = thumb). */
    fun fingering(inversion: Int = 0): List<Int> = when (type.intervals.size) {
        3 -> if (inversion == 1) listOf(1, 2, 5) else listOf(1, 3, 5)
        4 -> listOf(1, 2, 3, 5)
        else -> listOf(1, 2, 3, 4, 5)
    }
}

/** A chord by scale degree: semitones above the key and its quality, e.g. ii = (2, MINOR). */
data class Degree(val numeral: String, val semitones: Int, val type: ChordType)

data class Progression(val name: String, val degrees: List<Degree>)

private fun d(numeral: String, semis: Int, type: ChordType) = Degree(numeral, semis, type)

val PROGRESSIONS = listOf(
    Progression("I–V–vi–IV (pop)", listOf(d("I", 0, ChordType.MAJOR), d("V", 7, ChordType.MAJOR), d("vi", 9, ChordType.MINOR), d("IV", 5, ChordType.MAJOR))),
    Progression("ii–V–I (jazz)", listOf(d("ii7", 2, ChordType.MIN7), d("V7", 7, ChordType.DOM7), d("Imaj7", 0, ChordType.MAJ7), d("Imaj7", 0, ChordType.MAJ7))),
    Progression("I–vi–IV–V (50s)", listOf(d("I", 0, ChordType.MAJOR), d("vi", 9, ChordType.MINOR), d("IV", 5, ChordType.MAJOR), d("V", 7, ChordType.MAJOR))),
    Progression("12-bar blues", listOf(0, 0, 0, 0, 5, 5, 0, 0, 7, 5, 0, 7).map { s ->
        d(when (s) { 0 -> "I7"; 5 -> "IV7"; else -> "V7" }, s, ChordType.DOM7)
    }),
)

val PROGRESSION_KEYS = listOf(0, 7, 5, 2, 9)   // C, G, F, D, A

/**
 * Picks for each chord the inversion and octave closest to the previous one (smooth voice leading), so
 * the right hand moves as little as possible. The first chord is in root position from middle C.
 */
fun voiceLead(chords: List<Chord>): List<List<Int>> {
    val out = mutableListOf<List<Int>>()
    for (c in chords) {
        val prev = out.lastOrNull()
        if (prev == null) { out += rightHand(c, 0, 60); continue }
        val options = (0 until c.inversionCount).flatMap { inv -> listOf(48, 60).map { rightHand(c, inv, it) } }
            .filter { it.first() >= 52 && it.last() <= 81 }
        out += options.minBy { v -> distance(prev, v) }
    }
    return out
}

/**
 * Right-hand notes when the left hand plays the root: triads keep all three notes; four- and five-note chords
 * drop the root (3rd, 5th, 7th...), as pianists usually voice them. Fewer doubled notes also help the mic.
 */
fun rightHand(c: Chord, inversion: Int, base: Int): List<Int> =
    c.voicing(inversion, base).let { v -> if (c.type.intervals.size >= 4) v.filter { it % 12 != c.root % 12 } else v }

private fun distance(a: List<Int>, b: List<Int>): Int =
    if (a.size == b.size) a.zip(b).sumOf { (x, y) -> abs(x - y) } else abs(a.average() - b.average()).toInt() * a.size

/** A progression as a song: one chord per bar (4 beats), right hand voice-led chords, left hand the root. */
fun progressionSong(p: Progression, key: Int, bpm: Double = 72.0): Song {
    val chords = p.degrees.map { Chord((key + it.semitones) % 12, it.type) }
    val bar = (4 * 60_000 / bpm).toLong()
    val voicings = voiceLead(chords)
    val notes = chords.flatMapIndexed { i, c ->
        val t = i * bar
        voicings[i].map { Note(it, t, bar - 150, Hand.RIGHT, 75) } + Note(48 + c.root % 12, t, bar - 150, Hand.LEFT, 80)
    }.sortedWith(compareBy({ it.startMs }, { it.midi }))
    val sections = chords.mapIndexed { i, c -> Section("${p.degrees[i].numeral} ${c.name}", i * bar, (i + 1) * bar) }
    return Song("prog-${p.name}-$key", "${p.name} in ${rootName(key)}", bpm, 4 to 4, notes, groupOnsets(notes), sections, chords.size * bar)
}

/**
 * Chord practice: shows one chord at a time; the player strikes its notes in any octave and order
 * (pitch classes), over as many strikes as needed. A note outside the chord is a miss, not a reset.
 */
class ChordDrill(private val pool: List<Chord>, seed: Long = System.nanoTime()) {
    private val rnd = java.util.Random(seed)
    var current: Chord = pool[rnd.nextInt(pool.size)]
        private set
    val heard = mutableSetOf<Int>()
    var correct = 0; private set
    var streak = 0; private set
    private var missedThisChord = false

    enum class Result { PART, DONE, MISS }

    fun input(midi: Int): Result {
        val pc = midi % 12
        if (pc !in current.pitchClasses) { missedThisChord = true; streak = 0; return Result.MISS }
        heard += pc
        if (heard != current.pitchClasses) return Result.PART
        correct++
        if (!missedThisChord) streak++
        next()
        return Result.DONE
    }

    fun next() {
        heard.clear(); missedThisChord = false
        if (pool.size > 1) { var c: Chord; do { c = pool[rnd.nextInt(pool.size)] } while (c == current); current = c }
    }
}
