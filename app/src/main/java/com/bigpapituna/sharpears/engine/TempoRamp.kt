package com.bigpapituna.sharpears.engine

import kotlin.math.roundToInt

/**
 * The speed ramp: start at [start] (50%) and rise by [step] (10 points) after every clean pass, up to 100%.
 * A pass is the loop (or the whole song, without a loop) played through. Clean: wait mode with no wrong key,
 * or Perform mode at [performPass]% or better. Listen mode never ramps.
 */
class TempoRamp(val start: Double = 0.5, val step: Double = 0.1, val performPass: Int = 90) {
    var speed = start
        private set
    val percent get() = (speed * 100).roundToInt()
    val atTop get() = speed >= 1.0 - 1e-9

    fun clean(mode: Mode, report: Report): Boolean = when (mode) {
        Mode.WAIT -> report.wrongKeys == 0
        Mode.PERFORM -> (report.recap?.accuracy ?: 0) >= performPass
        Mode.LISTEN -> false
    }

    /** A pass ended. Returns the new speed when it rose, or null. */
    fun passEnded(mode: Mode, report: Report): Double? {
        if (atTop || !clean(mode, report)) return null
        speed = (((speed + step) * 10).roundToInt() / 10.0).coerceAtMost(1.0)
        return speed
    }
}
