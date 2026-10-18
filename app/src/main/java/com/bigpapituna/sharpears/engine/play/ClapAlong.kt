package com.bigpapituna.sharpears.engine.play

import kotlin.math.abs
import kotlin.math.roundToLong

/** The three speeds of Clap along. [word] is what the button and the pup call it. */
enum class ClapSpeed(val bpm: Int, val word: String) {
    SLOW(60, "Slow"), WALK(90, "Walking"), RUN(120, "Running");

    val beatMs: Long get() = (60_000.0 / bpm).roundToLong()
}

/**
 * Clap along: the pup drums a steady beat, the player taps the drum or claps with it. No score and no end: claps
 * (taps) are judged against the nearest beat, the beats in time light up, and the pup cheers a run of them.
 *
 * Times are milliseconds on one clock ([startMs] is beat 0). A clap within [toleranceMs] of a beat is in time; a
 * second clap on a beat that already has one is ignored (a double clap is not two beats).
 */
class ClapAlong(val speed: ClapSpeed, val startMs: Long) {
    /** In time: a quarter of the beat each way, at most 150 ms (kids are not metronomes, and the mic adds wobble). */
    val toleranceMs: Long = minOf(speed.beatMs / 4, MAX_TOLERANCE_MS)

    enum class Verdict { IN_TIME, EARLY, LATE, EXTRA, BEFORE_START }

    data class Clap(val verdict: Verdict, val beat: Int, val offMs: Long)

    private val hitBeats = HashSet<Int>()
    /** Beats in a row that had a clap in time, up to the latest beat judged. */
    var streak = 0; private set
    var bestStreak = 0; private set
    var inTime = 0; private set
    private var lastCountedBeat = -1

    fun beatAt(i: Int): Long = startMs + i * speed.beatMs

    /** The beat sounding at or just before [t] (-1 before the first). */
    fun beatIndex(t: Long): Int = if (t < startMs) -1 else ((t - startMs) / speed.beatMs).toInt()

    /** Judges a clap or tap at [t]. */
    fun clap(t: Long): Clap {
        if (t < startMs - toleranceMs) return Clap(Verdict.BEFORE_START, -1, 0)
        val nearest = ((t - startMs).toDouble() / speed.beatMs).let { Math.round(it).toInt() }.coerceAtLeast(0)
        val off = t - beatAt(nearest)
        val verdict = when {
            abs(off) > toleranceMs -> if (off < 0) Verdict.EARLY else Verdict.LATE
            nearest in hitBeats -> Verdict.EXTRA
            else -> Verdict.IN_TIME
        }
        if (verdict == Verdict.IN_TIME) {
            hitBeats += nearest
            inTime++
            streak = if (nearest == lastCountedBeat + 1 || streak == 0) streak + 1 else 1
            lastCountedBeat = nearest
            bestStreak = maxOf(bestStreak, streak)
        }
        return Clap(verdict, nearest, off)
    }

    /**
     * Called as time passes: a beat whose window closed with no clap breaks the run (the pup does not mind; the
     * dots just start again). Returns true when the run was broken now.
     */
    fun tick(now: Long): Boolean {
        val closed = ((now - startMs - toleranceMs).toDouble() / speed.beatMs).let { Math.floor(it).toInt() }
        if (streak > 0 && closed > lastCountedBeat) { streak = 0; return true }
        return false
    }

    /** Whether beat [i] had a clap in time (for the row of dots). */
    fun hit(i: Int): Boolean = i in hitBeats

    /** What the pup says after a clap in time, now and then; null when he just keeps drumming. */
    fun cheer(): String? = when (streak) {
        4 -> "You're right with me!"
        8 -> "Eight in a row. What a drummer!"
        16 -> "Sixteen! We're a band."
        else -> null
    }

    companion object {
        const val MAX_TOLERANCE_MS = 150L
    }
}
