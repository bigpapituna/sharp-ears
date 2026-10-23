package com.bigpapituna.sharpears.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Beats of a song in song ms: one click per beat of [timeSignature] at [bpm] (quarter notes per minute, as in
 * MIDI), the first beat of each bar accented. Beat 0 is at song time 0; negative times are the count-in.
 *
 * Bounded whatever the song says (2.8.3): the tempo is held to SmfParser's 20..300 bpm and a time signature music
 * does not have (a damaged file's) counts as 4/4, so a beat is 25 ms to 12 s long; one call clicks at most [MAX_CLICKS].
 */
class Metronome(bpm: Double, timeSignature: Pair<Int, Int>) {
    private val real = timeSignature.first in 1..32 && timeSignature.second.let { it in 1..32 && (it and (it - 1)) == 0 }
    val beatsPerBar = if (real) timeSignature.first else 4
    val beatMs = 60_000.0 / (if (bpm.isNaN()) 120.0 else bpm.coerceIn(SmfParser.MIN_BPM.toDouble(), SmfParser.MAX_BPM.toDouble())) *
        4.0 / (if (real) timeSignature.second else 4)
    val barMs get() = beatMs * beatsPerBar

    /**
     * Clicks for song time moving from [fromMs] (included) to [toMs] (excluded): true for an accent. A frame spans a beat
     * or two; a span of more than [MAX_CLICKS] beats (a seek, a long stall) clicks nothing rather than a burst. A beat
     * counts when `from - 1e-9 <= beat < to - 1e-9`, the same test on both ends, so back-to-back frames click each beat
     * once (the start's tolerance was in beats and the end's in ms: a frame starting a hair after a beat clicked it again).
     */
    fun clicks(fromMs: Double, toMs: Double): List<Boolean> {
        if (!(toMs > fromMs) || !(abs(fromMs) < MAX_TIME_MS && abs(toMs) < MAX_TIME_MS) || (toMs - fromMs) / beatMs > MAX_CLICKS) return emptyList()
        val from = fromMs - 1e-9; val to = toMs - 1e-9
        var b = floor(from / beatMs).toLong() - 1
        repeat(3) { if (b * beatMs < from) b++ }
        val out = mutableListOf<Boolean>()
        while (out.size <= MAX_CLICKS && b * beatMs < to) { out += Math.floorMod(b, beatsPerBar.toLong()) == 0L; b++ }
        return out
    }

    /** The beat in the bar (0 = downbeat) at [ms]. */
    fun beatInBar(ms: Double) = Math.floorMod(floor(ms / beatMs).toLong(), beatsPerBar.toLong()).toInt()

    companion object {
        /** The most clicks one call makes: a frame's worth is one or two. */
        const val MAX_CLICKS = 64
        /** Song times past this (about 30 years) are no song's: they click nothing. */
        const val MAX_TIME_MS = 1e12
    }
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
