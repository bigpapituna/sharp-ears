package com.bigpapituna.sharpears.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The MIDI timing offset (2.14, Settings > Microphone > MIDI keyboard): how late (positive) or early a MIDI keyboard's notes
 * reach the app, from −100 to +100 ms, 0 by default, kept per phone (the "mic" prefs file, like the rest of the phone's
 * input setup). Perform's scoring alone moves each MIDI note back by it ([latencyMs], Transport.noteInput's latency); Wait
 * mode, the lights, the recording and every other screen take the notes as they come.
 *
 * The calibration ([calibrate]) plays [CLICKS] clicks [CLICK_MS] apart after a one-bar count-in and listens for a key on
 * each: the offset is the median of tap minus click over the taps that found a click, so one stray tap does not move it.
 */
object MidiOffset {
    const val MIN = -100
    const val MAX = 100
    const val DEFAULT = 0
    /** The slider's step. */
    const val STEP = 5

    /** The calibration: 8 clicks at 80 bpm after a count-in of 4, about ten seconds in all. */
    const val CLICKS = 8
    const val COUNT_IN = 4
    const val CLICK_MS = 750L
    /** Taps a calibration needs to say anything. */
    const val MIN_TAPS = 5
    /** A tap further than this from its click belongs to no click (a stray key, a tap a beat off). */
    const val MATCH_MS = 250L
    /** Taps further than this from the median are left out before the median is taken again. */
    const val OUTLIER_MS = 80

    fun clamp(ms: Int) = ms.coerceIn(MIN, MAX)

    /**
     * What Perform's scoring is told about a MIDI note's delay: the offset itself (Transport moves the note back by it, so
     * +40 grades a key that reached the app 40 ms after it was played as played on time).
     */
    fun latencyMs(offsetMs: Int): Double = clamp(offsetMs).toDouble()

    /** The click times of a calibration that starts at [startMs]: the count-in's first, then [CLICKS] counted ones. */
    fun clickTimes(startMs: Long): List<Long> = (COUNT_IN until COUNT_IN + CLICKS).map { startMs + it * CLICK_MS }

    /**
     * The offset from a calibration: each tap in [tapsMs] goes to the nearest of [clicksMs] within [MATCH_MS] (one tap per
     * click, the nearest); with fewer than [MIN_TAPS] matched, null (try again). Otherwise the median of tap minus click,
     * again over the taps within [OUTLIER_MS] of that median, rounded to [STEP] and held to [MIN]..[MAX].
     */
    fun calibrate(clicksMs: List<Long>, tapsMs: List<Long>): Int? {
        val diffs = clicksMs.mapNotNull { c -> tapsMs.minByOrNull { abs(it - c) }?.takeIf { abs(it - c) <= MATCH_MS }?.let { (it - c).toInt() } }
        if (diffs.size < MIN_TAPS) return null
        val first = median(diffs)
        val kept = diffs.filter { abs(it - first) <= OUTLIER_MS }.ifEmpty { diffs }
        return clamp(((median(kept) / STEP.toDouble()).roundToInt() * STEP))
    }

    private fun median(xs: List<Int>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    /** "0 ms", "40 ms late", "25 ms early": how the setting reads (the keyboard's notes arrive late or early). */
    fun label(offsetMs: Int): String = when {
        offsetMs == 0 -> "0 ms"
        offsetMs > 0 -> "$offsetMs ms late"
        else -> "${-offsetMs} ms early"
    }
}
