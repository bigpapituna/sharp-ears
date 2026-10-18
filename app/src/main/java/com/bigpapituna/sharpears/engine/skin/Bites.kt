package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind

/**
 * Kid bites (docs/KIDS-PLAN.md section 3, item 2): the same lessons, cut small. A reading step shows its first scene
 * and offers "More?" for the rest; an exercise is cut at its phrase end (the first phrase marker, bar 4 or 8) and one
 * run through passes. Grown-ups never see any of this.
 *
 * Where the phrase ends is the playground team's (`Exercise.phraseEnd`); until it is merged [phraseEndBeats] answers
 * null and a kid plays the whole exercise. The cut itself works on the token notation, so it needs nothing else.
 */
object Bites {
    /** A Perform step passes for a kid at this many stars ("heard it in time"): the Playground concert's two stars. */
    const val KID_PASS_STARS = 2
    /** A tempo-ramp step is one Perform run for a kid, at this share of the target tempo. */
    const val KID_RAMP_SPEED = 0.75

    /**
     * Where a kid's bite of [ex] ends, in beats from its start; null plays the whole exercise.
     * INTEGRATION: return the playground team's `ex.phraseEnd` here (times [Exercise.beats] if it is counted in bars,
     * see [barsToBeats]). The one place to change.
     */
    fun phraseEndBeats(@Suppress("UNUSED_PARAMETER") ex: Exercise): Double? = null

    fun barsToBeats(bars: Int, ex: Exercise): Double = bars.toDouble() * ex.beats

    /** How many beats one hand's notation lasts. */
    fun beats(spec: String): Double = tokens(spec).sumOf { it.second }

    /**
     * One hand's notation up to [endBeats]: tokens starting before the cut are kept, a note that rings over it is
     * shortened to end there, and rests left dangling at the end are dropped.
     */
    fun cutLine(spec: String, endBeats: Double): String {
        val out = mutableListOf<Pair<String, Double>>()
        var beat = 0.0
        for ((names, len) in tokens(spec)) {
            if (beat >= endBeats - EPS) break
            out += names to minOf(len, endBeats - beat)
            beat += len
        }
        while (out.isNotEmpty() && out.last().first == "r") out.removeAt(out.lastIndex)
        return out.joinToString(" ") { (n, l) -> "$n:${fmt(l)}" }
    }

    /** [ex] cut at [endBeats] (both hands; the book fingering follows the right-hand notes kept). Null or past the end: unchanged. */
    fun cut(ex: Exercise, endBeats: Double?): Exercise {
        if (endBeats == null || endBeats <= 0) return ex
        if (endBeats >= maxOf(beats(ex.right), beats(ex.left)) - EPS) return ex
        val right = cutLine(ex.right, endBeats)
        val keptRightNotes = tokens(right).filter { it.first != "r" }.sumOf { it.first.split('+').size }
        val fingers = ex.fingers.split(" ").filter { it.isNotBlank() }.take(keptRightNotes).joinToString(" ")
        return ex.copy(right = right, left = cutLine(ex.left, endBeats), fingers = fingers)
    }

    /**
     * The step as a kid plays it: its exercise cut at [endBeats] (default: [phraseEndBeats]); a tempo ramp becomes one
     * Perform run at [KID_RAMP_SPEED]; reading steps and MIDI-only steps are unchanged.
     */
    fun kidStep(step: Step, endBeats: Double? = step.exercise?.let(::phraseEndBeats)): Step {
        val ex = step.exercise ?: return step
        if (step.kind == StepKind.READ || step.kind.needsMidi) return step
        val bite = cut(ex, endBeats)
        return if (step.kind == StepKind.RAMP) step.copy(kind = StepKind.PERFORM, exercise = bite.copy(bpm = bite.bpm * KID_RAMP_SPEED))
        else step.copy(exercise = bite)
    }

    /** Did a kid's run pass? Perform: [KID_PASS_STARS] stars; everything else as for grown-ups (a wait run passes at its end). */
    fun kidPassed(step: Step, report: Report): Boolean = when {
        step.kind.needsMidi -> step.passed(report)
        step.kind == StepKind.PERFORM || step.kind == StepKind.RAMP -> report.recap?.let { Stars.perform(it) >= KID_PASS_STARS } ?: false
        else -> step.passed(report)
    }

    /** How many of a reading step's [total] scenes show: one for a kid until they ask for more, all otherwise. */
    fun scenesShown(total: Int, kid: Boolean, more: Boolean): Int = if (kid && !more) minOf(1, total) else total

    private const val EPS = 1e-9

    private fun tokens(spec: String): List<Pair<String, Double>> =
        spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { tok -> tok.split(':').let { it[0] to it[1].toDouble() } }

    /** 2.0 -> "2", 0.5 -> "0.5". */
    private fun fmt(b: Double): String = if (b == Math.floor(b)) b.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, b).trimEnd('0').trimEnd('.')
}
