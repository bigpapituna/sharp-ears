package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 2.3 part B, the night sky's play-it-back games after a tap game: a note higher or lower than the pup's (up or down),
 * and a tune played the usual way, then fast and slow (fast or slow). Pure rules; the stages in ui/playground feed them
 * what the microphone heard, TreeSkyFlowTest and TreeSkyHearingTest drive them on the JVM.
 */

/** Which way a note should go from the pup's. */
enum class Way { HIGHER, LOWER }

/** A round of "higher or lower": the pup plays [ref]; any key the [way] of it counts. */
data class PitchRound(val ref: Int, val way: Way) {
    /** The key the pup plays when he shows the round: a few keys the right way, on a white key. */
    val demo: Int get() = (if (way == Way.HIGHER) ref + 4 else ref - 4).let { if (isBlack(it)) it + (if (way == Way.HIGHER) 1 else -1) else it }

    /**
     * The keys the stage asks the note matcher about (at most 12, each its own group): the pup's own note, and white
     * keys a few steps either side of it. A key outside them is named by the wrong-key search and judged by its height.
     */
    val keys: Set<Int> get() {
        val whites = (ref - 12..ref + 12).filter { !isBlack(it) && it != ref }.sortedBy { abs(it - ref) }
        return (listOf(ref) + whites.take(11)).toSet()
    }

    /** The round as exercise notation: every key the right way, up to an octave and a half, the demonstration key first. */
    val token: String get() {
        val (lo, hi) = if (way == Way.HIGHER) ref + 1 to ref + 18 else ref - 18 to ref - 1
        return "${noteName(demo)}|${noteName(lo)}..${noteName(hi)}:2"
    }
}

/** The exercise for [rounds] of higher or lower: one any-of group a round (the plain player can run it too). */
fun higherLowerExercise(rounds: List<PitchRound>): Exercise =
    Exercise(right = rounds.joinToString(" ") { it.token }, bpm = 60.0, practice = Practice.RIGHT)

/**
 * Higher or lower: a key the matcher confirmed or named counts as itself, the pitch tracker's guess only when it is at
 * least [clear] semitones from the pup's note. The right way finds the round; the pup's own key is [MissWhy.MIDDLE]
 * ("that's my note"), the other way [MissWhy.OTHER_SIDE].
 */
object HigherLowerJudge {
    const val CLEAR = 1.5

    fun judge(sound: StageSound, round: PitchRound, clear: Double = CLEAR): Answer {
        val key = when (sound.by) {
            HeardBy.MATCHER, HeardBy.NAMED -> sound.midi ?: return Answer.Heard
            HeardBy.PITCH -> sound.guess?.takeIf { abs(it - round.ref) >= clear }?.roundToInt() ?: return Answer.Heard
            HeardBy.ONSET -> return Answer.Heard
        }
        return when {
            key == round.ref -> Answer.Miss(MissWhy.MIDDLE, key)
            (key > round.ref) == (round.way == Way.HIGHER) -> Answer.Found(key)
            else -> Answer.Miss(MissWhy.OTHER_SIDE, key)
        }
    }
}

/** How quickly a tune went. */
enum class Pace { FAST, USUAL, SLOW }

/** What the fast and slow game made of a phrase: its notes' [gapMs] (median time between them), what it heard and wanted. */
data class TempoVerdict(val heard: Pace, val target: Pace?, val match: Boolean, val gapMs: Double)

/**
 * Fast or slow for real (night 8): a little tune played the usual way (it sets the pace), then fast, then slow. Each
 * note's time is kept ([note]); when a phrase is done its median gap between notes is graded against the first's:
 * [FAST_BY] or quicker is fast, [SLOW_BY] or slower is slow. Without a first phrase (it was let go) fixed lines stand
 * in. A toy: it never holds the lesson up, it only shows how it went.
 */
class TempoTune(val rounds: List<Pace?> = ROUNDS) {
    var anchorMs: Double? = null; private set
    private val times = ArrayList<Long>()

    fun note(atMs: Long) { times += atMs }

    val notes get() = times.size

    fun grade(gapMs: Double): Pace {
        val a = anchorMs
        return when {
            a != null && gapMs <= a * FAST_BY -> Pace.FAST
            a != null && gapMs >= a * SLOW_BY -> Pace.SLOW
            a != null -> Pace.USUAL
            gapMs <= FAST_MS -> Pace.FAST
            gapMs >= SLOW_MS -> Pace.SLOW
            else -> Pace.USUAL
        }
    }

    /** Phrase [round] is done: its verdict (the first only sets the pace); null with fewer than two notes heard. */
    fun phraseDone(round: Int): TempoVerdict? {
        if (times.size < 2) { times.clear(); return null }
        val gaps = times.zipWithNext { a, b -> (b - a).toDouble() }.sorted()
        times.clear()
        val gap = gaps[gaps.size / 2]
        val target = rounds.getOrNull(round)
        if (target == null) {
            if (round == 0) anchorMs = gap
            return TempoVerdict(Pace.USUAL, null, match = true, gapMs = gap)
        }
        val heard = grade(gap)
        return TempoVerdict(heard, target, heard == target, gap)
    }

    fun skipped() = times.clear()

    companion object {
        val ROUNDS: List<Pace?> = listOf(null, Pace.FAST, Pace.SLOW)
        const val FAST_BY = 0.8
        const val SLOW_BY = 1.3
        const val FAST_MS = 380.0
        const val SLOW_MS = 900.0
    }
}
