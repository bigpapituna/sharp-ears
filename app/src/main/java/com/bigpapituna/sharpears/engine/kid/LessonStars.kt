package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.skin.Stars
import kotlin.math.floor

/**
 * Stars on every stone (2.2): a lesson earns one to three stars from its steps' runs, by the skin team's recap rules
 * ([Stars]), and its stone keeps the best it ever got, so a replay can only add. Pure; data/KidStore.kt keeps them.
 */
object LessonStars {
    /** A step the Playground let go after its third try (no dead ends): it counts, as one star. */
    const val LET_GO = 1

    /**
     * A graded step skipped with Next (never played): one star, as a step let go. Before, a skipped step simply did not
     * count, so a kid who tapped Next through every game (a concert too) got three stars for "nothing to grade".
     */
    const val SKIPPED = 1

    /** What skipping [step] adds to the lesson's steps: [SKIPPED] for a graded step, null for a picture or a listen. */
    fun ofSkip(step: com.bigpapituna.sharpears.engine.Step): Int? =
        SKIPPED.takeIf { graded(step) }

    /** A step that is judged (it has an exercise and is not a listen): the steps a skip costs a star and a replay goes to. */
    fun graded(step: com.bigpapituna.sharpears.engine.Step): Boolean =
        step.exercise != null && step.kind != com.bigpapituna.sharpears.engine.StepKind.LISTEN

    /**
     * 2.14, "Try for 3 stars" and a stone's Replay: the index of the lesson's first graded step, so a replay skips the reading
     * pages and the listens before it (Theo's replay started again at the reading page, every time). 0 when none is graded.
     */
    fun firstGraded(steps: List<com.bigpapituna.sharpears.engine.Step>): Int = steps.indexOfFirst(::graded).coerceAtLeast(0)

    /** The finish screen offers "Try for 3 stars" when the lesson earned fewer and has a graded step to go back to. */
    fun tryForThree(stars: Int, steps: List<com.bigpapituna.sharpears.engine.Step>): Boolean = stars in 1 until Stars.MAX && steps.any(::graded)

    /** A walked stone under three stars wears a Replay chip (2.14); a stone passed by placement has no stars to chase. */
    fun replayChip(stone: Stone): Boolean = stone.state == StoneState.DONE && !stone.skipped && stone.stars in 1 until Stars.MAX

    /**
     * How a lesson's finish sounds (2.14): a lesson with a graded step skipped with Next is [Finish.NEUTRAL] ("On to the next.",
     * the pup's wag, no "You did it!" or the celebrate clip); every other finish celebrates.
     */
    fun finish(skippedGraded: Int): Finish = if (skippedGraded > 0) Finish.NEUTRAL else Finish.CELEBRATE

    enum class Finish { CELEBRATE, NEUTRAL }

    const val TRY_FOR_THREE = "Try for 3 stars"
    const val REPLAY = "Replay"
    /** The sheet before Next passes a graded step by. */
    const val SKIP_TITLE = "Skip this one?"
    const val SKIP_LINE = "It stays at one star."
    const val SKIP_YES = "Skip it"
    const val SKIP_NO = "Keep playing"
    /** The neutral finish's title (and what the voice says). */
    const val NEUTRAL_TITLE = "On to the next."
    const val NEUTRAL_LINE = "Come back for more stars any time."

    /**
     * Stars for one exercise step's passing run: a Perform run by its accuracy and timing ([Stars.perform]); a run
     * with its own score (the drum's beats with a clap, a pedal or loudness step) by that score on the same lines as
     * Perform accuracy; a run where the notes wait by its wrong keys ([Stars.waitRun]).
     */
    fun ofRun(report: Report, letGos: Int = 0): Int = minOf(byScore(report, letGos), letGoCap(report))

    /**
     * A stage that let rounds go (three tries, nothing heard or found) cannot earn more than it played: one star when half
     * its rounds or more were let go, two when some were. Before, a kid who played nothing (or whose microphone heard
     * nothing) was given three stars, since a let-go round adds no wrong key.
     */
    private fun letGoCap(report: Report): Int = when {
        report.letGoRounds <= 0 || report.rounds <= 0 -> Stars.MAX
        report.letGoRounds * 2 >= report.rounds -> LET_GO
        else -> 2
    }

    private fun byScore(report: Report, letGos: Int): Int = when {
        report.recap != null -> Stars.perform(report.recap, letGos)
        report.extraScore != null -> when {
            report.extraScore >= Stars.ALL_FROM && letGos <= 0 -> 3
            report.extraScore >= Stars.IN_TIME_FROM -> 2
            else -> 1
        }
        else -> Stars.waitRun(report.wrongKeys, letGos)
    }

    /**
     * A lesson's stars from its graded steps' stars ([steps], one each, in any order): their mean, a half rounded up,
     * one to three. A lesson with nothing to grade (only pictures and listening) gets three: it was finished.
     */
    fun ofLesson(steps: List<Int>): Int {
        if (steps.isEmpty()) return Stars.MAX
        val mean = steps.sumOf { it.coerceIn(1, Stars.MAX) }.toDouble() / steps.size
        return floor(mean + 0.5).toInt().coerceIn(1, Stars.MAX)
    }

    /** The best of what a stone had ([stored], 0 or null for none yet) and a new run's [stars]. */
    fun best(stored: Int?, stars: Int): Int = maxOf(stored ?: 0, stars.coerceIn(1, Stars.MAX)).coerceAtMost(Stars.MAX)

    /** A page's stars for its certificate: the mean of its starred stones, a half rounded up; one when none has any. */
    fun ofPage(lessonIds: List<String>, stars: (String) -> Int): Int =
        lessonIds.map(stars).filter { it > 0 }.let { if (it.isEmpty()) 1 else ofLesson(it) }
}
