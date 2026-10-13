package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName

/*
 * Scales and arpeggios in every key, with the conventional fingering, and a renderer that turns one into an
 * Exercise (token notation) the player can run in wait mode or Perform mode.
 *
 * Fingering numbers: 1 = thumb .. 5 = little finger, one per note, ascending; the way down is the same numbers
 * read backwards. Scales of seven notes in the 24 standard keys use the usual tables (thumb on white keys, the
 * thumb crossing every 3 or 4 notes). Pentatonics, blues and the chromatic scale are fingered by the player's
 * hand-position search (Fingering.solve), the chromatic scale by the usual 1 and 3 rule.
 * Arpeggio fingerings are derived from the same rule (thumb on a white chord tone, 1 2 3 5 or 1 2 4 cycles).
 */

private val MAJOR_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
private val MINOR_NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")
private val MAJOR_FLAT_KEYS = setOf(5, 10, 3, 8, 1)          // F, Bb, Eb, Ab, Db major
private val MINOR_FLAT_KEYS = setOf(2, 7, 0, 5, 10, 3)       // D, G, C, F, Bb, Eb minor

/**
 * A scale's interval pattern from the root (semitones, one octave without the top note). [descent] is a different
 * pattern for the way down (melodic minor comes down as natural minor).
 */
enum class ScaleType(val label: String, val steps: List<Int>, val minorFamily: Boolean, val descent: List<Int>? = null) {
    MAJOR("Major", listOf(0, 2, 4, 5, 7, 9, 11), false),
    NATURAL_MINOR("Natural minor", listOf(0, 2, 3, 5, 7, 8, 10), true),
    HARMONIC_MINOR("Harmonic minor", listOf(0, 2, 3, 5, 7, 8, 11), true),
    MELODIC_MINOR("Melodic minor", listOf(0, 2, 3, 5, 7, 9, 11), true, descent = listOf(0, 2, 3, 5, 7, 8, 10)),
    MAJOR_PENTATONIC("Major pentatonic", listOf(0, 2, 4, 7, 9), false),
    MINOR_PENTATONIC("Minor pentatonic", listOf(0, 3, 5, 7, 10), true),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10), true),
    CHROMATIC("Chromatic", (0..11).toList(), false),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10), true),
    MIXOLYDIAN("Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10), false);

    /** Seven-note scales that follow the major (or natural minor) fingering table of their root. */
    val tabled get() = steps.size == 7
    /** Which table: the major one (major, mixolydian) or the minor one (the minors, dorian). */
    val minorTable get() = minorFamily
}

/** The scales the practice screen offers first. */
val CORE_SCALES = listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.HARMONIC_MINOR, ScaleType.MELODIC_MINOR)

/** Name of a root pitch class (0 = C) as the key is usually written: Db and F# major, C# and Eb minor. */
fun scaleRootName(pc: Int, minorFamily: Boolean): String = (if (minorFamily) MINOR_NAMES else MAJOR_NAMES)[((pc % 12) + 12) % 12]
fun scaleRootName(pc: Int, type: ScaleType): String = scaleRootName(pc, type.minorFamily)

/** Whether black keys of this scale are best named as flats (B flat, not A sharp). */
fun flatsFor(root: Int, type: ScaleType): Boolean = when (type) {
    ScaleType.DORIAN -> (root - 2 + 12) % 12 in MAJOR_FLAT_KEYS
    ScaleType.MIXOLYDIAN -> (root + 5) % 12 in MAJOR_FLAT_KEYS
    else -> if (type.minorFamily) root in MINOR_FLAT_KEYS else root in MAJOR_FLAT_KEYS
}

/**
 * Where a scale starts for [hand]: the root nearest middle C, but keys from G up start an octave lower so the
 * hands stay around the middle of the piano. The left hand is an octave below the right.
 */
fun handRoot(pc: Int, hand: Hand): Int {
    val base = if (hand == Hand.RIGHT) 60 else 48
    return base + if (pc >= 7) pc - 12 else pc
}

private fun fingers(s: String) = s.split(" ").map { it.toInt() }

// One octave up, 8 numbers, keyed by root pitch class (0 = C).
private val C_RH = fingers("1 2 3 1 2 3 4 5")
private val C_LH = fingers("5 4 3 2 1 3 2 1")
private val FLAT_LH = fingers("3 2 1 4 3 2 1 3")
private val MAJOR_RH: Map<Int, List<Int>> = mapOf(
    0 to C_RH, 1 to fingers("2 3 1 2 3 4 1 2"), 2 to C_RH, 3 to fingers("3 1 2 3 4 1 2 3"), 4 to C_RH,
    5 to fingers("1 2 3 4 1 2 3 4"), 6 to fingers("2 3 4 1 2 3 1 2"), 7 to C_RH, 8 to fingers("3 4 1 2 3 1 2 3"),
    9 to C_RH, 10 to fingers("2 1 2 3 1 2 3 4"), 11 to C_RH)
private val MAJOR_LH: Map<Int, List<Int>> = mapOf(
    0 to C_LH, 1 to FLAT_LH, 2 to C_LH, 3 to FLAT_LH, 4 to C_LH, 5 to C_LH, 6 to fingers("4 3 2 1 3 2 1 4"),
    7 to C_LH, 8 to FLAT_LH, 9 to C_LH, 10 to FLAT_LH, 11 to fingers("4 3 2 1 4 3 2 1"))
private val SHARP_MINOR_RH = fingers("3 4 1 2 3 1 2 3")
private val MINOR_RH: Map<Int, List<Int>> = mapOf(
    0 to C_RH, 1 to SHARP_MINOR_RH, 2 to C_RH, 3 to fingers("3 1 2 3 4 1 2 3"), 4 to C_RH,
    5 to fingers("1 2 3 4 1 2 3 4"), 6 to SHARP_MINOR_RH, 7 to C_RH, 8 to SHARP_MINOR_RH,
    9 to C_RH, 10 to fingers("2 1 2 3 1 2 3 4"), 11 to C_RH)
private val MINOR_LH: Map<Int, List<Int>> = mapOf(
    0 to C_LH, 1 to FLAT_LH, 2 to C_LH, 3 to fingers("2 1 4 3 2 1 3 2"), 4 to C_LH, 5 to C_LH,
    6 to fingers("4 3 2 1 3 2 1 4"), 7 to C_LH, 8 to FLAT_LH, 9 to C_LH, 10 to fingers("2 1 3 2 1 4 3 2"),
    11 to fingers("4 3 2 1 4 3 2 1"))

/** The one-octave table (8 numbers) for a 7-note scale, the root's pitch class and the hand. */
fun scaleTable(root: Int, minor: Boolean, hand: Hand): List<Int> =
    (if (hand == Hand.RIGHT) (if (minor) MINOR_RH else MAJOR_RH) else (if (minor) MINOR_LH else MAJOR_LH)).getValue(root)

/** [octaves] octaves up from the one-octave pattern [p]. Thumb-first right-hand patterns restart on the thumb. */
private fun extend(p: List<Int>, hand: Hand, octaves: Int): List<Int> {
    if (octaves == 1) return p
    return if (hand == Hand.RIGHT && p[0] == 1) p.take(7).repeat(octaves - 1) + p
    else p + p.drop(1).repeat(octaves - 1)
}

private fun List<Int>.repeat(n: Int): List<Int> = (0 until n).flatMap { this }

class Scale(val root: Int, val type: ScaleType, val octaves: Int = 1) {
    init { require(root in 0..11 && octaves in 1..4) { "root 0..11, octaves 1..4" } }

    val flats = flatsFor(root, type)
    val rootName get() = scaleRootName(root, type)
    val name get() = "$rootName ${type.label.lowercase()}"

    private fun build(steps: List<Int>, hand: Hand): List<Int> {
        val s = handRoot(root, hand)
        return (0 until octaves).flatMap { o -> steps.map { s + 12 * o + it } } + (s + 12 * octaves)
    }

    /** Notes going up, the top root included. */
    fun ascending(hand: Hand): List<Int> = build(type.steps, hand)

    /** Notes coming down from the top root to the root. */
    fun descending(hand: Hand): List<Int> = build(type.descent ?: type.steps, hand).reversed()

    /** Up then down, the top note once. */
    fun upAndDown(hand: Hand): List<Int> = ascending(hand) + descending(hand).drop(1)

    /** True when [fingering] comes from the standard tables (otherwise a hand-position search). */
    val standardFingering get() = type.tabled

    /** One finger per note going up. */
    fun fingering(hand: Hand): List<Int> =
        if (type.tabled) extend(scaleTable(root, type.minorTable, hand), hand, octaves)
        else searched(ascending(hand), hand)

    /** One finger per note of [upAndDown]. */
    fun fingeringUpAndDown(hand: Hand): List<Int> =
        if (type.tabled) fingering(hand).let { it + it.reversed().drop(1) }
        else searched(upAndDown(hand), hand)

    private fun searched(notes: List<Int>, hand: Hand): List<Int> {
        if (type == ScaleType.CHROMATIC) return chromaticFingers(notes)
        val mirror = hand == Hand.LEFT
        val p = notes.map { listOf(if (mirror) -it else it) }
        return Fingering.solve(p, notes.map { listOf(isBlack(it)) }).map { it[0] }
    }

    /**
     * This scale as an exercise: up and down (or only up), [beatsPerNote] beats per note, the last note held two
     * beats. [practice] picks the hand; with both, the left hand plays an octave below the right.
     */
    fun toExercise(practice: Practice, bpm: Double, beatsPerNote: Double = 1.0, upAndDown: Boolean = true): Exercise {
        fun line(hand: Hand) = noteLine(if (upAndDown) upAndDown(hand) else ascending(hand), beatsPerNote, flats)
        val right = if (practice != Practice.LEFT) line(Hand.RIGHT) else ""
        val left = if (practice != Practice.RIGHT) line(Hand.LEFT) else ""
        val fingers = if (practice == Practice.LEFT) "" else
            (if (upAndDown) fingeringUpAndDown(Hand.RIGHT) else fingering(Hand.RIGHT)).joinToString(" ")
        return Exercise(right = right, left = left, bpm = bpm, practice = practice, flats = flats, fingers = fingers)
    }

    fun title(practice: Practice) = "$name, ${handWords(practice)}, ${octaveWords(octaves)}"
}

internal fun handWords(p: Practice) = when (p) { Practice.RIGHT -> "right hand"; Practice.LEFT -> "left hand"; Practice.BOTH -> "both hands" }
internal fun octaveWords(n: Int) = if (n == 1) "1 octave" else "$n octaves"

/** Token notation for a run of notes: "C4:1 D4:1 ... C4:2". */
internal fun noteLine(notes: List<Int>, beatsPerNote: Double, flats: Boolean): String =
    notes.mapIndexed { i, m -> "${noteName(m, flats)}:${beat(if (i == notes.lastIndex) maxOf(2.0, beatsPerNote) else beatsPerNote)}" }.joinToString(" ")

/** 1.0 as "1", 0.5 as "0.5": the notation reads any decimal, this just keeps it short. */
internal fun beat(x: Double): String = if (x == Math.rint(x)) x.toInt().toString() else x.toString()

/** 1 on white keys, 3 on black keys, 2 on the white key that follows a white key (F after E, C after B). */
private fun chromaticFingers(notes: List<Int>): List<Int> {
    val out = notes.mapIndexed { i, m ->
        when {
            isBlack(m) -> 3
            i > 0 && !isBlack(notes[i - 1]) -> 2
            else -> 1
        }
    }.toMutableList()
    // Finish on the thumb, like the other scales.
    if (out.isNotEmpty() && !isBlack(notes.last())) out[out.lastIndex] = 1
    return out
}

// ---------------------------------------------------------------------------------------------------------
// Arpeggios

enum class ArpeggioType(val label: String, val intervals: List<Int>, val minorFamily: Boolean) {
    MAJOR("Major", listOf(0, 4, 7), false),
    MINOR("Minor", listOf(0, 3, 7), true),
    DOMINANT_7("Dominant 7th", listOf(0, 4, 7, 10), false),
    DIMINISHED_7("Diminished 7th", listOf(0, 3, 6, 9), true),
}

private val DOM7_FLAT_ROOTS = setOf(0, 5, 10, 3, 8, 1, 6)

class Arpeggio(val root: Int, val type: ArpeggioType, val octaves: Int = 1) {
    init { require(root in 0..11 && octaves in 1..4) { "root 0..11, octaves 1..4" } }

    val flats = when (type) {
        ArpeggioType.MAJOR -> root in MAJOR_FLAT_KEYS
        ArpeggioType.MINOR -> root in MINOR_FLAT_KEYS
        ArpeggioType.DOMINANT_7 -> root in DOM7_FLAT_ROOTS
        ArpeggioType.DIMINISHED_7 -> root != 1 && root != 6
    }
    val rootName get() = scaleRootName(root, type.minorFamily)
    val name get() = "$rootName ${type.label.lowercase()} arpeggio"

    private val k = type.intervals.size

    fun ascending(hand: Hand): List<Int> {
        val s = handRoot(root, hand)
        return (0 until octaves).flatMap { o -> type.intervals.map { s + 12 * o + it } } + (s + 12 * octaves)
    }

    fun descending(hand: Hand): List<Int> = ascending(hand).reversed()
    fun upAndDown(hand: Hand): List<Int> = ascending(hand) + descending(hand).drop(1)

    private fun white(toneIndex: Int) = !isBlack((root + type.intervals[toneIndex % k]) % 12)

    /** One finger per note going up. */
    fun fingering(hand: Hand): List<Int> {
        val n = k * octaves + 1
        return List(n) { i -> if (hand == Hand.RIGHT) right(i, n) else left(i) }
    }

    fun fingeringUpAndDown(hand: Hand): List<Int> = fingering(hand).let { it + it.reversed().drop(1) }

    private fun cycleAt(cycle: List<Int>, i: Int) = cycle[Math.floorMod(i, cycle.size)]

    private fun right(i: Int, n: Int): Int {
        if (k == 3) {
            val t = (0..2).firstOrNull { white(it) } ?: 2          // all three tones black (F# major, E flat minor): thumb on the fifth
            return when (t) {
                0 -> if (i == n - 1) 5 else cycleAt(listOf(1, 2, 3), i)
                1 -> if (i == 0) 3 else cycleAt(listOf(1, 2, 4), i - 1)
                else -> if (i == 0) 2 else if (i == 1) 4 else cycleAt(listOf(1, 2, 4), i - 2)
            }
        }
        val t = (0..3).firstOrNull { white(it) } ?: 0
        return if (t == 0) cycleAt(listOf(1, 2, 3, 5), i) else cycleAt(listOf(1, 2, 3, 4), i - t)
    }

    private fun left(i: Int): Int {
        if (k == 3) {
            if (white(0)) return if (i == 0) 5 else cycleAt(listOf(3, 2, 1), i - 1)
            val t = (1..2).firstOrNull { white(it) } ?: 1
            return cycleAt(listOf(1, 3, 2), i - t)
        }
        if (white(0)) return if (i == 0) 5 else cycleAt(listOf(1, 4, 3, 2), i)
        val t = (1..3).firstOrNull { white(it) } ?: 1
        return cycleAt(listOf(1, 4, 3, 2), i - t)
    }

    fun toExercise(practice: Practice, bpm: Double, beatsPerNote: Double = 1.0, upAndDown: Boolean = true): Exercise {
        fun line(hand: Hand) = noteLine(if (upAndDown) upAndDown(hand) else ascending(hand), beatsPerNote, flats)
        val right = if (practice != Practice.LEFT) line(Hand.RIGHT) else ""
        val left = if (practice != Practice.RIGHT) line(Hand.LEFT) else ""
        val fingers = if (practice == Practice.LEFT) "" else
            (if (upAndDown) fingeringUpAndDown(Hand.RIGHT) else fingering(Hand.RIGHT)).joinToString(" ")
        return Exercise(right = right, left = left, bpm = bpm, practice = practice, flats = flats, fingers = fingers)
    }

    fun title(practice: Practice) = "$name, ${handWords(practice)}, ${octaveWords(octaves)}"
}
