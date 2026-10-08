package com.bigpapituna.sharpears.engine

import kotlin.math.floor

/** A bar worth practising: [bar] counts from 1, [startMs]..[endMs] is its span in song time. */
data class TroubleSpot(val bar: Int, val startMs: Double, val endMs: Double, val score: Double)

/**
 * What a run leaves behind for the trouble-spot finder: how long wait mode held at each group (real ms, keyed by
 * the group's start), where wrong keys came (song ms), and which Perform groups were missed (their start).
 */
class TroubleLog {
    val waited = HashMap<Long, Double>()
    val wrongAt = mutableListOf<Double>()
    val missedAt = mutableListOf<Long>()

    fun reset() { waited.clear(); wrongAt.clear(); missedAt.clear() }
}

/**
 * The hardest bars of a run. Bars come from the song's first tempo and time signature, as the chord labels do
 * ([barMs]). Wait mode: seconds held past a normal reach for the keys, plus a wrong key costing as much as
 * [WRONG_COST_S] seconds of waiting. Perform: misses. Bars with nothing to fix are never listed.
 */
object TroubleSpots {
    /** A hold this long is just reading the next note; only the time beyond it counts. */
    const val FREE_WAIT_MS = 1200.0
    const val WRONG_COST_S = 1.5

    fun barIndex(ms: Double, barMs: Double) = floor(ms.coerceAtLeast(0.0) / barMs).toInt()

    fun spot(song: Song, bar: Int): TroubleSpot {
        val b = barMs(song)
        return TroubleSpot(bar, (bar - 1) * b, minOf(bar * b, song.durationMs.toDouble()), 0.0)
    }

    fun forWait(song: Song, log: TroubleLog, count: Int = 3): List<TroubleSpot> {
        val b = barMs(song)
        if (b <= 0) return emptyList()
        val score = HashMap<Int, Double>()
        log.waited.forEach { (start, ms) ->
            val extra = ms - FREE_WAIT_MS
            if (extra > 0) score.merge(barIndex(start.toDouble(), b), extra / 1000.0, Double::plus)
        }
        log.wrongAt.forEach { score.merge(barIndex(it, b), WRONG_COST_S, Double::plus) }
        return top(song, score, count)
    }

    fun forPerform(song: Song, log: TroubleLog, count: Int = 3): List<TroubleSpot> {
        val b = barMs(song)
        if (b <= 0) return emptyList()
        val score = HashMap<Int, Double>()
        log.missedAt.forEach { score.merge(barIndex(it.toDouble(), b), 1.0, Double::plus) }
        return top(song, score, count)
    }

    /** Worst first; ties go to the earlier bar. */
    private fun top(song: Song, score: Map<Int, Double>, count: Int) = score.entries
        .filter { it.value > 0 }
        .sortedWith(compareBy({ -it.value }, { it.key }))
        .take(count)
        .map { (i, s) -> spot(song, i + 1).copy(score = s) }

    /** "Hardest bars: 12, 13, 27" (worst first), or null when the run was clean. */
    fun line(spots: List<TroubleSpot>): String? = when (spots.size) {
        0 -> null
        1 -> "Hardest bar: ${spots[0].bar}"
        else -> "Hardest bars: " + spots.joinToString(", ") { "${it.bar}" }
    }

    /** Stored per song as "12,13,27". */
    fun encode(spots: List<TroubleSpot>) = spots.joinToString(",") { "${it.bar}" }
    fun decode(s: String?): List<Int> = s.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
}
