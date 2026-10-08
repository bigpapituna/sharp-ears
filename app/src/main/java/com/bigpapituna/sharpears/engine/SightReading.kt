package com.bigpapituna.sharpears.engine

import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sight-reading drill: four bars nobody has seen before, from a key's scale and the tones of its I, IV, V, I
 * chords. Mostly stepwise with a few skips of a third. Level 1: C major, right hand, quarters and halves.
 * Level 2: C, G or F major, eighth notes as well, and a simple bass (the bar's root, one note a bar) in the left.
 */
data class SightPiece(val level: Int, val key: Int, val exercise: Exercise) {
    val keyName get() = rootName(key) + " major"
}

/** A key's major scale degree [d] (0 = tonic, may run past 7) above [base] (the tonic's MIDI note). */
private fun degree(base: Int, d: Int) = base + 12 * Math.floorDiv(d, 7) + MAJOR_STEPS[Math.floorMod(d, 7)]
private val MAJOR_STEPS = listOf(0, 2, 4, 5, 7, 9, 11)

/** The rhythms a bar can take, in beats. */
private val BARS_1 = listOf(listOf(1.0, 1.0, 1.0, 1.0), listOf(2.0, 1.0, 1.0), listOf(1.0, 1.0, 2.0), listOf(2.0, 2.0), listOf(1.0, 2.0, 1.0))
private val BARS_2 = BARS_1 + listOf(listOf(0.5, 0.5, 1.0, 1.0, 1.0), listOf(1.0, 0.5, 0.5, 2.0), listOf(1.0, 1.0, 0.5, 0.5, 1.0), listOf(0.5, 0.5, 0.5, 0.5, 2.0))

fun sightPiece(level: Int, seed: Long): SightPiece {
    val rnd = Random(seed)
    val key = if (level <= 1) 0 else listOf(0, 7, 5)[rnd.nextInt(3)]
    // The right hand sits around the tonic above middle C; G major starts on the G below, so it stays in reach.
    val base = 60 + key - if (key > 4) 12 else 0
    val chords = listOf(0, 3, 4, 0)                                         // I IV V I as scale degrees
    val right = mutableListOf<String>()
    val left = mutableListOf<String>()
    var d = listOf(0, 2, 4)[rnd.nextInt(3)]                                // start on a tone of I
    val bars = if (level <= 1) BARS_1 else BARS_2
    for (bar in 0 until 4) {
        val rhythm = if (bar == 3) listOf(listOf(2.0, 2.0), listOf(1.0, 1.0, 2.0), listOf(4.0))[rnd.nextInt(3)] else bars[rnd.nextInt(bars.size)]
        val chordTones = listOf(0, 2, 4).map { chords[bar] + it }
        rhythm.forEachIndexed { i, beats ->
            val last = bar == 3 && i == rhythm.lastIndex
            d = when {
                last -> listOf(0, 7).minBy { abs(it - d) }                    // end on the tonic, the nearer one
                bar > 0 && i == 0 -> chordTones.flatMap { listOf(it, it + 7, it - 7) }.filter { it in 0..9 }.minBy { abs(it - d) + rnd.nextDouble() }
                else -> {
                    val r = rnd.nextInt(20)
                    val step = when { r < 13 -> 1; r < 18 -> 2; else -> 0 }    // mostly steps, a few thirds, now and then a repeat
                    val up = if (d <= 0) true else if (d >= 9) false else rnd.nextBoolean()
                    (d + if (up) step else -step).coerceIn(0, 9)
                }
            }
            right += "${noteName(degree(base, d), key == 5)}:${fmt(beats)}"
        }
        // The bass: the chord's root between E2 and D#3, under the hand.
        if (level >= 2) left += "${noteName((36 + degree(base, chords[bar]) % 12).let { if (it < 40) it + 12 else it }, key == 5)}:4"
    }
    val ex = Exercise(right = right.joinToString(" "), left = left.joinToString(" "), bpm = if (level <= 1) 66.0 else 60.0,
        practice = if (level >= 2) Practice.BOTH else Practice.RIGHT, flats = key == 5)
    return SightPiece(level, key, ex)
}

private fun fmt(b: Double) = if (b == b.toInt().toDouble()) "${b.toInt()}" else "$b"

/** A sight-reading run: seconds to the end plus two seconds for each wrong note. Lower is better. */
data class SightScore(val seconds: Int, val wrong: Int) {
    val points get() = seconds + 2 * wrong
    fun encode() = "$seconds,$wrong"
    val label get() = "%d:%02d".format(seconds / 60, seconds % 60) + if (wrong == 0) ", no wrong notes" else ", $wrong wrong"

    companion object {
        fun of(realMs: Double, wrong: Int) = SightScore((realMs / 1000).roundToInt(), wrong)
        fun decode(s: String?): SightScore? = s?.split(',')?.takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { x -> b.toIntOrNull()?.let { SightScore(x, it) } } }
    }
}
