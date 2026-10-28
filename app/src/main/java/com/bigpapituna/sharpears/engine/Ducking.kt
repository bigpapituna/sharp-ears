package com.bigpapituna.sharpears.engine

import kotlin.math.pow

/**
 * 2.17: while a spoken line of the build's extra plays, the app's own piano notes are a little quieter (Roy: the notes
 * "overtake" the voice), and back to full after. Subtle on purpose: [DB] down, never silent. Pure (DuckingTest).
 */
object Ducking {
    /** How far the notes go down while a line plays. */
    const val DB = -4.0

    /** The notes' volume factor: [DB] as a gain while ducked, 1 otherwise. */
    fun gain(ducked: Boolean): Float = if (ducked) 10.0.pow(DB / 20.0).toFloat() else 1f

    /** A note's SoundPool volume: its struck volume times the gain, kept in 0..1. */
    fun volume(struck: Float, ducked: Boolean): Float = (struck * gain(ducked)).coerceIn(0f, 1f)
}
