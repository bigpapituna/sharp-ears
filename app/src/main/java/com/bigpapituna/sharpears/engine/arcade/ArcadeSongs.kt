package com.bigpapituna.sharpears.engine.arcade

import com.bigpapituna.sharpears.engine.BUNS_X
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.MARY_X
import com.bigpapituna.sharpears.engine.ODE_LINE
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.parseTokens

/**
 * The songs the arcade's song games teach (Fireworks, Song painter): the library's own opening phrases (engine/Exercises.kt),
 * note for note, easiest first, one level each. [picture] is what Song painter paints for it.
 */
data class ArcadeSong(val id: String, val title: String, val picture: String, val exercise: Exercise) {
    /** The tune's keys in order (one per note; the songs are single notes). */
    val notes: List<Int> by lazy { parseTokens(exercise.right).filter { !it.rest }.map { it.keys.first() } }
    private val beats: List<Double> by lazy { parseTokens(exercise.right).filter { !it.rest }.map { it.beats } }

    /**
     * The tune's phrases: a note of two beats or more ends one (where a singer breathes). Song painter plays these.
     */
    fun phrases(): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        var cur = mutableListOf<Int>()
        notes.forEachIndexed { i, k ->
            cur += k
            if (beats[i] >= 2.0 || i == notes.lastIndex) { out += cur; cur = mutableListOf() }
        }
        return out
    }

    /**
     * Four-note bites (Fireworks): each phrase cut into bites of at most four, as even as they come (seven is four and
     * three, eleven is four, four and three).
     */
    fun bites(): List<List<Int>> = phrases().flatMap { p ->
        val n = (p.size + 3) / 4
        val base = p.size / n
        val extra = p.size % n
        var at = 0
        List(n) { i -> val len = base + if (i < extra) 1 else 0; p.subList(at, at + len).also { at += len } }
    }
}

object ArcadeSongs {
    val ALL: List<ArcadeSong> = listOf(
        ArcadeSong("buns", "Hot Cross Buns", "buns", BUNS_X),
        ArcadeSong("mary", "Mary Had a Little Lamb", "lamb", MARY_X),
        ArcadeSong("twinkle", "Twinkle, Twinkle", "star", TWINKLE),
        ArcadeSong("ode", "Ode to Joy", "sun", ODE_LINE),
    )

    /** The song of [level] (one level a song). */
    fun of(level: Int): ArcadeSong = ALL[level.coerceIn(0, ALL.size - 1)]
}
