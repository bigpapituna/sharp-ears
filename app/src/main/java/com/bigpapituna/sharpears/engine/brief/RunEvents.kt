package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.kid.LessonStars
import com.bigpapituna.sharpears.engine.skin.Stars

/**
 * One lesson step as the brief logs it (ui/LessonScreen.kt keeps one per step): each try's report and tally go in
 * ([run]), and when the step is left the one [BriefEvent.LessonStep] comes out ([event]), once. Pure, tested on the JVM.
 * [openedMs] and the `nowMs` given to [event] are ForegroundClock times (time off screen is not the step's).
 */
class StepRecorder(val lessonId: String, val stepIndex: Int, val step: Step, private val openedMs: Long) {
    private var tally = RunTally()
    private var tries = 0
    private var reportWrong = 0
    private var last: Report? = null
    private var letGo = false
    /** The step's event was made: [event] gives nothing more. */
    var logged = false; private set

    /** A try ended with [report] (and the player's [tally], null from a stage); [letGo] when the Playground let it go. */
    fun run(report: Report, tally: RunTally?, letGo: Boolean = false) {
        tries++
        last = report
        reportWrong += report.wrongKeys
        tally?.let { this.tally = this.tally.plus(it) }
        if (letGo) this.letGo = true
    }

    /**
     * A try left before it ended (Back, the lesson closed, the app going away from a step): its wrong keys and hints
     * count, and it counts as a try, so a child who gives up mid-run is seen struggling.
     */
    fun abandoned(tally: RunTally) {
        tries++
        this.tally = this.tally.plus(tally)
    }

    /** Tries so far. */
    val runs get() = tries

    /**
     * The step's event at [at] (wall clock) and [nowMs] (ForegroundClock), or null once made. [passed]: left forward after
     * passing or being let go; [skipped]: Next without passing; neither: the lesson closed during it ([BriefEvent.LessonStep.left]).
     */
    fun event(at: Long, nowMs: Long, passed: Boolean, skipped: Boolean = false): BriefEvent.LessonStep? {
        if (logged) return null
        logged = true
        val graded = step.exercise != null && step.kind != StepKind.LISTEN
        val r = last
        val stars = when {
            !graded -> null
            skipped -> LessonStars.ofSkip(step)
            !passed -> null
            letGo -> LessonStars.LET_GO
            r != null -> LessonStars.ofRun(r)
            else -> null
        }
        return BriefEvent.LessonStep(
            at, lessonId, stepIndex, step.kind.name,
            tries = tries, letGo = letGo, hintsUsed = tally.hintsUsed, durationMs = (nowMs - openedMs).coerceAtLeast(0),
            game = step.game?.name, hands = step.exercise?.practice?.let(::hands), skipped = skipped,
            stars = stars, accuracy = r?.recap?.accuracy?.takeIf { passed }, score = r?.extraScore?.takeIf { passed },
            timingMeanMs = r?.recap?.takeIf { passed && it.total > 0 }?.biasMs,
            notes = r?.let(::notes) ?: 0, wrongKeys = maxOf(reportWrong, tally.wrongKeys), nearMisses = tally.near, farMisses = tally.far,
            letGoRounds = r?.letGoRounds ?: 0, rounds = r?.rounds ?: 0, left = !passed && !skipped,
        )
    }

    /** The step's event with its wrong-key pairs and octave hints, in one list to log (empty once made). */
    fun events(at: Long, nowMs: Long, passed: Boolean, skipped: Boolean = false): List<BriefEvent> =
        event(at, nowMs, passed, skipped)?.let { listOf(it) + wrongKeyEvents(at) + listOfNotNull(octaveEvent(at)) }.orEmpty()

    /** The octave hints of every try, as one event (or null). */
    fun octaveEvent(at: Long): BriefEvent.OctaveHint? = tally.octaveEvent(at, lessonId = lessonId)

    /** The step's most frequent wrong-key pairs. */
    fun wrongKeyEvents(at: Long): List<BriefEvent.WrongKey> = tally.wrongKeyEvents(at, lessonId = lessonId)
}

/** The events a finished song run logs: its SongRun, its wrong-key pairs and its octave hints. */
object RunEvents {
    fun song(at: Long, songId: String, mode: Mode, practice: Practice, report: Report, tally: RunTally, durationMs: Long,
             fromTop: Boolean, duet: Boolean): List<BriefEvent> {
        val recap = report.recap
        val run = BriefEvent.SongRun(
            at, songId, RunMode.valueOf(mode.name), hands(practice), recap?.accuracy, Stars.of(recap, report.wrongKeys),
            recap?.takeIf { it.total > 0 }?.biasMs, tally.near, tally.far, notes(report), maxOf(report.wrongKeys, tally.wrongKeys),
            tally.hintsUsed, durationMs, fromTop, duet,
        )
        return listOf(run) + tally.wrongKeyEvents(at, songId = songId) + listOfNotNull(tally.octaveEvent(at, songId = songId))
    }
}

private fun hands(p: Practice) = Hands.valueOf(p.name)

/** Groups played: a Perform run's graded notes, or the groups a waiting run passed. */
private fun notes(r: Report) = r.recap?.let { it.good + it.ok } ?: r.groups
