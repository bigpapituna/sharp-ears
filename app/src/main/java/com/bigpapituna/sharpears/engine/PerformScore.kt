package com.bigpapituna.sharpears.engine

import kotlin.math.abs
import kotlin.math.roundToInt

const val GOOD_MS = 150.0
const val OK_MS = 300.0

enum class Grade { GOOD, OK, MISS }

/**
 * One graded note of a Perform run (2.14): its group's song time, the keys it wanted, and how far from it the key came, in
 * song ms (negative early); null when it was missed. [PerformTiming] turns these into bars and the timing panel.
 */
data class NoteTiming(val startMs: Long, val keys: List<Int>, val offsetMs: Double?) {
    val missed get() = offsetMs == null
    val good get() = offsetMs != null && abs(offsetMs) <= GOOD_MS
}

/**
 * What the recap shows after a Perform run. The notes one by one are [PerformScore.timings] (2.14), kept out of here: a
 * recap is logged, compared and built on every key.
 */
data class Recap(val good: Int, val ok: Int, val miss: Int, val biasMs: Int, val longestStreak: Int) {
    val total get() = good + ok + miss
    /** Good plus ok over all groups, 0..100. */
    val accuracy get() = if (total == 0) 0 else ((good + ok) * 100.0 / total).roundToInt()
    /** Notes in the good window over all notes, 0..100: the star rule's "in time". */
    val inTime get() = if (total == 0) 0 else (good * 100.0 / total).roundToInt()
}

/**
 * The stars of a song's Perform run (2.14, Sam: "98% gives 2 stars" with no way to see why), the one rule for a song or a
 * song by chords played in Perform; shown on its recap with [SongStars.ruleLine]. Three: no misses and at least nine notes
 * in ten in time (the good window, ±150 ms). Two: two misses at most, or at least three notes in four in time. One: the run
 * reached its end. The old rule's average-timing test is gone: a run of good notes all a little early (Sam's −126 ms) no
 * longer loses its third star to the average. A lesson step's stars stay engine/skin/Stars.perform (branch levels-ages).
 */
fun songStars(r: Recap): Int = when {
    r.total == 0 -> 1
    r.miss == 0 && r.good * 100 >= SongStars.THREE_IN_TIME * r.total -> 3
    r.miss <= SongStars.TWO_MISSES || r.good * 100 >= SongStars.TWO_IN_TIME * r.total -> 2
    else -> 1
}

/** The song rule's numbers and its words for the recap. */
object SongStars {
    /** Percent of the notes in time for three stars (with no misses), and for two. */
    const val THREE_IN_TIME = 90
    const val TWO_IN_TIME = 75
    /** Misses two stars allow. */
    const val TWO_MISSES = 2

    /** The rule, always the three-star one: "3 stars: no misses, 9 of 10 notes in time." */
    const val RULE = "3 stars: no misses, 9 of 10 notes in time."

    /** What two stars need, for a run below them: "2 stars: 2 misses at most, or 3 of 4 notes in time." */
    const val RULE_TWO = "2 stars: 2 misses at most, or 3 of 4 notes in time."

    /** The run against the rule: "This run: no misses, 41 of 42 notes in time." */
    fun runLine(r: Recap): String =
        "This run: ${if (r.miss == 0) "no misses" else plural(r.miss, "miss", "misses")}, ${r.good} of ${r.total} notes in time."

    /** The lines under the stars: the three-star rule, the two-star one too for a one-star run, then this run. */
    fun ruleLines(r: Recap): List<String> = listOfNotNull(RULE, RULE_TWO.takeIf { songStars(r) == 1 }, runLine(r))
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
    private class Target(val startMs: Long, val keys: Set<Int>) { var grade: Grade? = null; var offset: Double? = null }
    private val targets = groups.filter { it.startMs >= fromMs }
        .map { g -> Target(g.startMs, g.notes.filter(required).map { it.midi }.toSet()) }
        .filter { it.keys.isNotEmpty() }
    private var good = 0; private var ok = 0; private var miss = 0
    private var streak = 0; private var longest = 0
    private var offsetSum = 0.0; private var offsets = 0
    private var firstOpen = 0       // targets before this one are graded

    val streakNow get() = streak
    /** Notes graded good so far (the lights' "Perfect"), without building a recap. */
    val goodCount get() = good
    /** The last hit's offset from its note (song ms, negative early): the live tick under the hit line. */
    var lastOffset = 0.0
        private set

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
        t.offset = off
        lastOffset = off
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

    /** The graded notes so far, in song order (a note still open is not in it). */
    fun timings(): List<NoteTiming> = targets.filter { it.grade != null }.map { NoteTiming(it.startMs, it.keys.sorted(), it.offset) }

    fun recap() = Recap(good, ok, miss, if (offsets == 0) 0 else (offsetSum / offsets).roundToInt(), longest)
}
