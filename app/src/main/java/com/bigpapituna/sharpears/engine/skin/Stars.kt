package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Recap
import kotlin.math.abs

/**
 * One to three stars on every recap (docs/KIDS-PLAN.md section 3: "heard it, heard it in time, heard it twice"),
 * shown to everyone; grown-ups also see the accuracy number, kids never see a number.
 *
 * A recap holds one run, so "twice" cannot be counted from it; the third star is earned by playing it cleanly
 * instead. Derived from the recap's accuracy and timing fields (decided 2026-10-18):
 * - Perform (the notes keep moving): ★ for reaching the end (you played it); ★★ at [IN_TIME_FROM] percent or more of
 *   the notes in time, the same line as RunWords.CELEBRATE_FROM; ★★★ at [ALL_FROM] percent or more with the
 *   average timing within [TIGHT_MS] ms (RunWords' "a hair early/late" band or better).
 * - Wait (the notes wait, no timing): ★ for reaching the end; ★★ with at most [FEW_WRONG] wrong keys; ★★★ with none.
 * Never zero: a recap only appears once a run reached its end, and getting there earns the first star.
 */
object Stars {
    const val MAX = 3
    const val IN_TIME_FROM = 60
    const val ALL_FROM = 90
    const val TIGHT_MS = 80
    const val FEW_WRONG = 3

    fun perform(r: Recap): Int = when {
        r.accuracy >= ALL_FROM && abs(r.biasMs) < TIGHT_MS -> 3
        r.accuracy >= IN_TIME_FROM -> 2
        else -> 1
    }

    fun waitRun(wrongKeys: Int): Int = when {
        wrongKeys <= 0 -> 3
        wrongKeys <= FEW_WRONG -> 2
        else -> 1
    }

    /**
     * Stars for a song run's recap: a Perform [recap] by the song rule (2.14, engine/PerformScore.kt songStars: no misses and
     * nine notes in ten in time for three), else a wait run with [wrongKeys]. A lesson step keeps [perform].
     */
    fun of(recap: Recap?, wrongKeys: Int): Int = recap?.let { com.bigpapituna.sharpears.engine.songStars(it) } ?: waitRun(wrongKeys)

    /** What TalkBack says for the row. */
    fun spoken(stars: Int): String = when (stars.coerceIn(1, MAX)) {
        1 -> "One star of three"
        2 -> "Two stars of three"
        else -> "Three stars of three"
    }
}
