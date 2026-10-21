package com.bigpapituna.sharpears.engine

import kotlin.math.abs
import kotlin.math.roundToInt

const val GOOD_MS = 150.0
const val OK_MS = 300.0

enum class Grade { GOOD, OK, MISS }

/** What the recap shows after a Perform run. */
data class Recap(val good: Int, val ok: Int, val miss: Int, val biasMs: Int, val longestStreak: Int) {
    val total get() = good + ok + miss
    /** Good plus ok over all groups, 0..100. */
    val accuracy get() = if (total == 0) 0 else ((good + ok) * 100.0 / total).roundToInt()
}

/**
 * Perform-mode scoring (PLAN 5.5): the song never waits. Each group with required notes is graded by the
 * first matching key: within ±150 ms of its time is good, within ±300 ms ok; a group nobody played in time is
 * a miss. Wrong keys do not cost anything by themselves; only misses break the streak.
 */
class PerformScore(groups: List<OnsetGroup>, required: (Note) -> Boolean, fromMs: Double = 0.0,
                   /** Called with a group's start time and the key's offset from it (ms, negative early) when it is played in time. */
                   private val onHit: (Long, Double) -> Unit = { _, _ -> },
                   /** Called with a group's start time when it is graded a miss. */
                   private val onMiss: (Long) -> Unit = {}) {
    private class Target(val startMs: Long, val keys: Set<Int>) { var grade: Grade? = null }
    private val targets = groups.filter { it.startMs >= fromMs }
        .map { g -> Target(g.startMs, g.notes.filter(required).map { it.midi }.toSet()) }
        .filter { it.keys.isNotEmpty() }
    private var good = 0; private var ok = 0; private var miss = 0
    private var streak = 0; private var longest = 0
    private var offsetSum = 0.0; private var offsets = 0
    private var firstOpen = 0       // targets before this one are graded

    val streakNow get() = streak

    /** Keys to listen for at [atMs]: required notes of ungraded groups inside the ok window. */
    fun listening(atMs: Double): Set<Int> = buildSet {
        for (i in firstOpen until targets.size) {
            val t = targets[i]
            if (t.startMs - atMs > OK_MS) break
            if (t.grade == null && abs(t.startMs - atMs) <= OK_MS) addAll(t.keys)
        }
    }

    /** A key struck at song time [atMs]. Returns the grade it earned, or null when it matched no open group. */
    fun input(midi: Int, atMs: Double): Grade? {
        var best: Target? = null
        for (i in firstOpen until targets.size) {
            val t = targets[i]
            if (t.startMs - atMs > OK_MS) break
            if (t.grade == null && midi in t.keys && abs(t.startMs - atMs) <= OK_MS &&
                (best == null || abs(t.startMs - atMs) < abs(best.startMs - atMs))) best = t
        }
        val t = best ?: return null
        val off = atMs - t.startMs
        val g = if (abs(off) <= GOOD_MS) Grade.GOOD else Grade.OK
        t.grade = g
        if (g == Grade.GOOD) good++ else ok++
        offsetSum += off; offsets++
        streak++; longest = maxOf(longest, streak)
        onHit(t.startMs, off)
        return g
    }

    /** Song time moved to [nowMs]: groups whose ok window has closed without a key are misses. Returns how many. */
    fun advance(nowMs: Double): Int {
        var missed = 0
        while (firstOpen < targets.size && targets[firstOpen].startMs + OK_MS < nowMs) {
            val t = targets[firstOpen++]
            if (t.grade == null) { t.grade = Grade.MISS; miss++; missed++; streak = 0; onMiss(t.startMs) }
        }
        return missed
    }

    fun recap() = Recap(good, ok, miss, if (offsets == 0) 0 else (offsetSum / offsets).roundToInt(), longest)
}
