package com.bigpapituna.sharpears.engine

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * "Auto" speed: a song plays at the tempo the player managed last time, nudged up [NUDGE] points on each day it
 * is practised with [GOOD_ACCURACY]% or better. Stored per song as the percent and the day of the last nudge.
 */
data class AutoSpeed(val percent: Int, val nudgedOn: LocalDate? = null) {
    val speed get() = percent / 100.0

    fun encode() = "$percent,${nudgedOn ?: ""}"

    companion object {
        const val NUDGE = 5
        const val GOOD_ACCURACY = 85
        const val MIN = 25
        const val MAX = 100
        /** A song with no run yet starts here. */
        const val FIRST = 70

        fun decode(s: String?): AutoSpeed? {
            val parts = s?.split(',') ?: return null
            val p = parts.getOrNull(0)?.toIntOrNull() ?: return null
            return AutoSpeed(p, parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        }

        /**
         * The tempo a run achieved, in percent of the song's: notes per minute played against the notes per minute
         * the song has over the same stretch. Both count the same notes, so it is song time over real time.
         * Holds in wait mode lower it; a run shorter than [minRealMs] says nothing (null).
         */
        fun effectivePercent(songMs: Double, realMs: Double, minRealMs: Double = 4000.0): Int? {
            if (realMs < minRealMs || songMs <= 0) return null
            return (songMs / realMs * 100).roundToInt().coerceIn(MIN, MAX)
        }

        /** After a Wait or Perform run: the tempo it managed, plus today's nudge when it was accurate enough. */
        fun afterRun(prev: AutoSpeed?, effective: Int, accuracy: Int, today: LocalDate): AutoSpeed {
            val nudge = accuracy >= GOOD_ACCURACY && prev?.nudgedOn != today
            val p = (effective + if (nudge) NUDGE else 0).coerceIn(MIN, MAX)
            return AutoSpeed(p, if (nudge) today else prev?.nudgedOn)
        }

        /** Wait-mode accuracy: groups played against groups plus wrong keys. */
        fun waitAccuracy(groups: Int, wrongKeys: Int): Int = if (groups + wrongKeys == 0) 0 else (groups * 100.0 / (groups + wrongKeys)).roundToInt()
    }
}
