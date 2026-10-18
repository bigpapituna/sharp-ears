package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No dead ends (2.1.1): every one of the twelve Playground lessons reaches its end with no right input at all, from a
 * kid who plays nothing and from one who plays only keys that are not it. Each step runs the way the app runs it: the
 * stages through StageRounds and the judges (as ui/playground's rememberStage), the drum through ClapTries, the embedded
 * player's wait mode through the same RoundCoach (PlayerScreen's letGo), Perform steps and the concert through
 * LessonScreen's StepRetry; the kid's bites as LessonScreen cuts them.
 */
class PlaygroundFlowTest {
    private enum class Kid { SILENT, WRONG }

    /** A walk through one lesson: the tries each step took and the simulated minutes. */
    private class Walk(val steps: MutableList<String> = mutableListOf(), var ms: Long = 0)

    @Test fun everyLessonEndsWithNoRightInput() {
        for (kid in Kid.entries) for (lesson in PLAYGROUND.lessons) {
            val w = walk(lesson, kid)
            assertEquals("${lesson.id} ($kid): every step done: ${w.steps}", lesson.steps.size, w.steps.size)
            println("%-14s %-6s %5.1f min  %s".format(lesson.id, kid, w.ms / 60_000.0, w.steps.joinToString(" | ")))
            // Nothing played at all: about half a minute a round at most, so a lesson never runs on for an hour.
            assertTrue("${lesson.id} ($kid) took ${w.ms / 60_000} min", w.ms < 25 * 60_000)
        }
    }

    private fun walk(lesson: CourseLesson, kid: Kid): Walk {
        val w = Walk()
        for (original in lesson.steps) {
            val step = Bites.kidStep(original, original.exercise?.let(Bites::phraseEndBeats))
            var fails = 0
            while (true) {
                val (report, note) = run(step, kid, w)
                val judged = report == null || Bites.kidPassed(step, report)
                val letGo = !judged && step.kind != StepKind.LISTEN && StepRetry.letGo(lesson.id, fails + 1)
                if (judged || letGo) { w.steps += "${step.kind.name.lowercase()}${step.game?.let { " ${it.name.lowercase()}" } ?: ""}: $note" +
                    if (letGo) ", let go after ${fails + 1} tries" else ""; break }
                fails++
                assertTrue("${lesson.id}: a step tried $fails times", fails < 10)
            }
        }
        return w
    }

    /** One try of a step: its report (null for a reading step) and a note for the log. */
    private fun run(step: Step, kid: Kid, w: Walk): Pair<Report?, String> {
        val ex = step.exercise ?: return null to "read"
        return when {
            step.game == Game.CLAP -> clap(kid, w)
            step.game == Game.HIGH_LOW || step.game == Game.KEY_HUNT || step.game == Game.SOFT_LOUD -> stage(step, kid, w)
            step.kind == StepKind.PERFORM || step.game == Game.CONCERT -> perform(step, kid, w)
            step.kind == StepKind.LISTEN -> listen(step, w)
            else -> waitMode(step, kid, w)
        }
    }

    /** A stage: rounds shown, heard, let go (ui/playground rememberStage). */
    private fun stage(step: Step, kid: Kid, w: Walk): Pair<Report?, String> {
        val ex = step.exercise!!
        val r = StageRounds(ex.toSong("t", "t"), ex.practice)
        val game = if (step.game == Game.SOFT_LOUD) SoftLoudGame() else null
        var now = 0L
        var lastTry = 0L
        r.start(now)
        var finds = 0
        while (!r.ended) {
            now += 20
            assertTrue("a stage that never ends: ${step.text}", now < 30 * 60_000)
            val cue = r.tick(20.0, now).second
            if (cue is RoundCoach.Cue.LetGo && game != null && !game.done) game.skip()
            if (kid != Kid.WRONG || r.coach.phase != RoundCoach.Phase.LISTEN || now - lastTry < 1_500) continue
            lastTry = now
            // A key that is not it, the way each stage hears one.
            val wrong: Answer = when (step.game) {
                Game.HIGH_LOW -> {
                    val wantHigh = wantsHigh(r.current!!)
                    HighLowJudge.judge(StageSound(now, HeardBy.MATCHER, if (wantHigh) 43 else 79, final = true), wantHigh)
                }
                Game.KEY_HUNT -> {
                    val hunt = Hunt.of(r.current!!.notes.map { it.midi })!!
                    HuntJudge.judge(StageSound(now, HeardBy.NAMED, if (hunt == Hunt.C) 62 else 64, final = true), hunt)
                }
                else -> {
                    // Soft and loud: the other loudness each time (the first note sets the level, so anything counts there).
                    val g = game!!
                    val rise = if (g.round < 0) -30.0 else if (g.target == Loudness.LOUD) (g.anchorDb ?: -30.0) - 8 else (g.anchorDb ?: -30.0) + 8
                    val v = g.heard(rise)
                    if (v.advance) Answer.Found(60) else Answer.Miss(MissWhy.TOO_LOUD, 60)
                }
            }
            when (val o = r.answer(wrong, now)) {
                is StageRounds.Outcome.Found -> finds++
                is StageRounds.Outcome.Missed -> if (o.cue is RoundCoach.Cue.LetGo && game != null && !game.done) game.skip()
                StageRounds.Outcome.Ignored -> {}
            }
        }
        w.ms += now
        if (game != null) assertTrue("the nap ends", game.done)
        return r.report() to "${r.rounds} rounds, ${r.letGo.size} let go, $finds taken by the game"
    }

    /** The drum: silent tries ask again, the third lets go (ui/playground ClapStage). Claps are never right here. */
    private fun clap(kid: Kid, w: Walk): Pair<Report?, String> {
        var attempt = 0
        while (true) {
            w.ms += 16_000                                      // the count-in and eight beats at 60 to 80 bpm, and the pause
            // A kid who plays keys instead of clapping: the clap counter hears no claps (it ignores piano).
            when (ClapTries.after(attempt, heard = false)) {
                ClapTries.Next.DONE -> error("no claps were heard")
                ClapTries.Next.AGAIN -> attempt++
                ClapTries.Next.LET_GO -> return Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0) to "${attempt + 1} silent tries ($kid), let go"
            }
        }
    }

    /** Perform (the drum's C on every boom, the concert): the song runs out; nothing right is under the pass mark. */
    private fun perform(step: Step, kid: Kid, w: Walk): Pair<Report?, String> {
        val ex = if (step.game == Game.CONCERT) CONCERT_SONGS.first().exercise else step.exercise!!
        val t = Transport(ex.toSong("t", "t")).apply { mode = Mode.PERFORM; practice = ex.practice; play(leadInMs = 3_000.0) }
        var ms = 0L
        while (t.playing) {
            t.tick(20.0); ms += 20
            if (kid == Kid.WRONG && ms % 1_000 == 0L) t.noteInput(23)
        }
        w.ms += ms
        val r = t.report()
        return r to "perform ${r.recap?.accuracy ?: 0}%"
    }

    private fun listen(step: Step, w: Walk): Pair<Report?, String> {
        val t = Transport(step.exercise!!.toSong("t", "t")).apply { mode = Mode.LISTEN; play() }
        while (t.playing) { t.tick(20.0); w.ms += 20 }
        return t.report() to "listened"
    }

    /** The embedded player's wait mode in a Playground lesson: a group goes by after three tries (PlayerScreen letGo). */
    private fun waitMode(step: Step, kid: Kid, w: Walk): Pair<Report?, String> {
        val ex = step.exercise!!
        val t = Transport(ex.toSong("t", "t")).apply { mode = Mode.WAIT; practice = ex.practice; play(leadInMs = 2_000.0) }
        val coach = RoundCoach(firstDemoMs = 0, demoMs = 0, againDemoMs = 0, quietMs = 9_000)
        var now = 0L
        var waiting = false
        var lastTry = 0L
        var letGo = 0
        fun letGoGroup() { letGo++; for (k in t.expected.map { it.midi }) if (t.noteInput(k) == InputResult.GROUP_DONE) break }
        while (t.playing) {
            now += 20
            assertTrue("a wait that never ends: ${step.text}", now < 30 * 60_000)
            t.tick(20.0)
            if (t.waiting != waiting) { waiting = t.waiting; if (waiting) coach.begin(0, now) else coach.found() }
            if (!t.waiting) continue
            if (coach.tick(now) is RoundCoach.Cue.LetGo) { letGoGroup(); continue }
            if (kid == Kid.WRONG && now - lastTry >= 1_500) {
                lastTry = now
                val wrongKey = 23                               // B0: no Playground note is there
                if (t.noteInput(wrongKey) == InputResult.WRONG && coach.miss(now) is RoundCoach.Cue.LetGo) letGoGroup()
            }
        }
        w.ms += now
        return t.report() to "wait, $letGo groups let go"
    }
}
