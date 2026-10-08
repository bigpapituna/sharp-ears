package com.bigpapituna.sharpears.engine

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Beats of a song in song ms: one click per beat of [timeSignature] at [bpm] (quarter notes per minute, as in
 * MIDI), the first beat of each bar accented. Beat 0 is at song time 0; negative times are the count-in.
 */
class Metronome(bpm: Double, timeSignature: Pair<Int, Int>) {
    val beatsPerBar = timeSignature.first.coerceAtLeast(1)
    val beatMs = 60_000.0 / bpm * 4.0 / timeSignature.second
    val barMs get() = beatMs * beatsPerBar

    /** Clicks for song time moving from [fromMs] (included) to [toMs] (excluded): true for an accent. */
    fun clicks(fromMs: Double, toMs: Double): List<Boolean> {
        if (toMs <= fromMs) return emptyList()
        val first = Math.ceil(fromMs / beatMs - 1e-9).toLong()
        val out = mutableListOf<Boolean>()
        var b = first
        while (b * beatMs < toMs - 1e-9) { out += Math.floorMod(b, beatsPerBar.toLong()) == 0L; b++ }
        return out
    }

    /** The beat in the bar (0 = downbeat) at [ms]. */
    fun beatInBar(ms: Double) = Math.floorMod(floor(ms / beatMs).toLong(), beatsPerBar.toLong()).toInt()
}

/**
 * A short synthesized click: a decaying sine burst, higher and louder for the accent. Two of these are the
 * metronome's only sounds.
 */
fun clickSamples(accent: Boolean, rate: Int = 48_000): FloatArray {
    val freq = if (accent) 1760.0 else 1175.0
    val gain = if (accent) 0.9 else 0.6
    val n = rate * 35 / 1000
    return FloatArray(n) { i ->
        val t = i.toDouble() / rate
        val attack = minOf(1.0, i / (rate * 0.0008))          // under a millisecond, so it does not pop
        (gain * attack * exp(-t / 0.007) * sin(2 * PI * freq * t)).toFloat()
    }
}
