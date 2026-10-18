package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.Candidate
import com.bigpapituna.sharpears.engine.Reject

/**
 * The octave hint (docs/KIDS-PLAN.md 3.7, from Roy's 2026-10-17 stuck moments: A5 played for A4, wait mode stuck):
 * when, for about [holdMs], the strongest thing the mic heard while waiting was an expected note an octave off, say so.
 *
 * Evidence comes from the matcher two ways: an expected note turned down because only its octave above rose
 * ([Reject.HIGHER_OCTAVE]) or because its octave below did ([Reject.LOWER_OCTAVE]), and a wrong key named exactly an
 * octave from an expected note. Everything else the mic turned down or named is a rival. The hint fires once per wait
 * when a piece of octave evidence is at least [holdMs] old and nothing stronger came since (so an octave overtone
 * under some other wrong key does not count), and the expected note was not heard in between.
 *
 * Not for lessons with any-of ranges ("any key above C4"): the octave is the point there ([appliesTo]).
 * Not thread-safe: the mic feeds it on the audio thread, and MicInput locks around every call.
 */
class OctaveHint(val holdMs: Long = 2_000, val windowMs: Long = 6_000) {
    /** [expected] was played [playedOctaveOffset] octaves off: +1 an octave up (try lower), -1 down (try higher). */
    data class Hint(val expected: Int, val playedOctaveOffset: Int) {
        /** The pup's line, shown and spoken. */
        val line: String get() = if (playedOctaveOffset > 0) UP else DOWN
    }

    private class Moment(val atMs: Long, val riseDb: Double, val expected: Int, val offset: Int)   // offset 0: a rival
    private val moments = ArrayDeque<Moment>()
    private var expected: Set<Int> = emptySet()
    private var fired = false

    /** A new wait on [expected] notes (or none): evidence starts over and the hint may fire again. */
    fun reset(expected: Set<Int>) { this.expected = expected; moments.clear(); fired = false }

    /** An expected note the matcher turned down at [nowMs]. */
    fun candidate(nowMs: Long, c: Candidate) {
        if (c.midi !in expected) return
        val off = when (c.reason) { Reject.HIGHER_OCTAVE -> 1; Reject.LOWER_OCTAVE -> -1; else -> 0 }
        add(Moment(nowMs, c.riseDb, c.midi, off))
    }

    /** A wrong key [midi] named at [nowMs]: evidence when an expected note is exactly an octave from it. */
    fun wrong(nowMs: Long, midi: Int, riseDb: Double) {
        val off = when { (midi - 12) in expected -> 1; (midi + 12) in expected -> -1; else -> 0 }
        add(Moment(nowMs, riseDb, if (off == 0) -1 else midi - 12 * off, off))
    }

    /** An expected note was heard: its octave evidence no longer counts. */
    fun right(midi: Int) { moments.removeAll { it.expected == midi && it.offset != 0 } }

    /** The hint, once per wait, when the evidence has held for [holdMs]; else null. */
    fun check(nowMs: Long): Hint? {
        if (fired) return null
        while (moments.isNotEmpty() && nowMs - moments.first().atMs > windowMs) moments.removeFirst()
        // Oldest first: an octave moment old enough, with nothing stronger since than evidence for the same thing.
        for ((i, m) in moments.withIndex()) {
            if (m.offset == 0 || nowMs - m.atMs < holdMs || m.expected !in expected) continue
            var best = m
            for (j in i + 1 until moments.size) if (moments[j].riseDb > best.riseDb) best = moments[j]
            if (best.offset == m.offset && best.expected == m.expected) {
                fired = true
                return Hint(m.expected, m.offset)
            }
        }
        return null
    }

    private fun add(m: Moment) { moments.addLast(m); while (moments.size > 2_000) moments.removeFirst() }

    companion object {
        const val UP = "That's the right note, but one octave up. Try lower."
        const val DOWN = "That's the right note, but one octave down. Try higher."
        /** Every line the hints say (the voice catalog records them). */
        val LINES = listOf(UP, DOWN, HintPlan.YOUR_TURN, NoiseWatch.LINE)

        /** False for a lesson song ("lesson-<id>") whose lesson uses any-of ranges ([anyOfLessons] holds the ids). */
        fun appliesTo(songId: String, anyOfLessons: Set<String>): Boolean =
            !(songId.startsWith("lesson-") && songId.removePrefix("lesson-") in anyOfLessons)
    }
}
