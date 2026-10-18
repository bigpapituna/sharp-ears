package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.OnsetGroup

/** What a key hunt round asks for (lessons 2 and 3): a dog house (two black keys), a fence (three), or a C. */
enum class Hunt(val pitchClasses: Set<Int>) {
    DOG_HOUSE(setOf(1, 3)), FENCE(setOf(6, 8, 10)), C(setOf(0));

    companion object {
        /** The hunt a set of keys stands for (any octave), or null when it is none of them. */
        fun of(keys: Collection<Int>): Hunt? = keys.map { it % 12 }.toSet().let { pcs -> entries.firstOrNull { it.pitchClasses == pcs } }
    }
}

/**
 * Every place a [hunt] can be found on a keyboard from [low] to [high]: each two-black group, three-black group or C
 * whole on it, low to high, as its keys.
 */
fun huntPlaces(hunt: Hunt, low: Int, high: Int): List<List<Int>> =
    (low / 12 - 1..high / 12 + 1).map { octave -> hunt.pitchClasses.sorted().map { 12 * (octave + 1) + it } }
        .filter { keys -> keys.all { it in low..high } }

/** The place of [hunt] that [key] belongs to, if it is one of them. */
fun huntPlaceOf(hunt: Hunt, key: Int): List<Int>? = if (key % 12 !in hunt.pitchClasses) null
    else hunt.pitchClasses.sorted().map { key - key % 12 + it }

/** Whether an any-of round of the high-and-low game wants a high key (it lies right of middle C) rather than a low one. */
fun wantsHigh(group: OnsetGroup): Boolean = group.notes.first().midi > 60

/**
 * Where the pup flies in the high-and-low game (0 = on the grass, 1 = the top of the sky): any key left of middle C
 * keeps him low (the lowest on the grass), middle C hovers just above it, any key right of it lifts him well up, and
 * higher keys higher still. So a child sees the side they played straight away, whichever key it was.
 */
fun flightHeight(midi: Double): Float = when {
    midi < 59.5 -> ((midi - 21.0) / (59.0 - 21.0) * 0.18).toFloat().coerceIn(0f, 0.18f)
    midi < 60.5 -> 0.28f
    else -> (0.45 + (midi - 61.0) / (96.0 - 61.0) * 0.55).toFloat().coerceIn(0.45f, 1f)
}
