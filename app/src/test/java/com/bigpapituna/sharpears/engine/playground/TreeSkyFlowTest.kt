package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No dead ends on the 2.3 pages: every treehouse and night sky lesson reaches its end with no right input at all, from a
 * kid who plays nothing and from one who plays (or taps) only what is not it. Each step runs the way the app runs it:
 * the copy games, higher or lower, loud and quiet, fast and slow and the happy chord through StageRounds and their
 * judges (ui/playground rememberStage), the tap games through TapQuiz, the beat games and the drum through ClapTries,
 * the jam through StarJam, the plain player's wait mode through the same RoundCoach (PlayerScreen's letGo), Perform steps
 * and the concerts through LessonScreen's StepRetry, as they run once the pages are on PLAYGROUND_PAGES (INTEGRATION-B).
 */
class TreeSkyFlowTest {
    private enum class Kid { SILENT, WRONG }

    private class Walk(val steps: MutableList<String> = mutableListOf(), var ms: Long = 0)

    @Test fun everyLessonEndsWithNoRightInput() {
        val lessons = TREEHOUSE.lessons + NIGHT_SKY.lessons
        assertEquals(24, lessons.size)
        for (kid in Kid.entries) for (lesson in lessons) {
            val w = walk(lesson, kid)
            assertEquals("${lesson.id} ($kid): every step done: ${w.steps}", lesson.steps.size, w.steps.size)
            println("%-14s %-6s %5.1f min  %s".format(lesson.id, kid, w.ms / 60_000.0, w.steps.joinToString(" | ")))
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
                // StepRetry.letGo once the pages are on PLAYGROUND_PAGES: three tries and the step goes on.
                val letGo = !judged && step.kind != StepKind.LISTEN && fails + 1 >= PLAYGROUND_TRIES
                if (judged || letGo) { w.steps += "${step.kind.name.lowercase()}${StageGames.of(step)?.let { " ${it::class.simpleName}" } ?: ""}: $note" +
                    if (letGo) ", let go after ${fails + 1} tries" else ""; break }
                fails++
                assertTrue("${lesson.id}: a step tried $fails times", fails < 10)
            }
        }
        return w
    }

    private fun run(step: Step, kid: Kid, w: Walk): Pair<Report?, String> {
        val ex = step.exercise ?: return null to "read"
        return when (val game = StageGames.of(step)) {
            is StageGame.Copy -> rounds(step, kid, w, phrases = true) { s, r -> NoteJudge.judge(s, r.expectedKey()) }
            is StageGame.HigherLower -> rounds(step, kid, w, phrases = false, wrongKey = { r -> game.rounds[r.done].ref }) { s, r -> HigherLowerJudge.judge(s, game.rounds[r.done]) }
            is StageGame.LoudQuiet -> {
                val tune = TuneLoudness(game.rounds)
                rounds(step, kid, w, phrases = true, onLetGo = tune::skipped) { s, r -> NoteJudge.judge(s, r.expectedKey()) }
            }
            is StageGame.Tempo -> {
                val tune = TempoTune(game.rounds)
                rounds(step, kid, w, phrases = true, onLetGo = tune::skipped) { s, r -> NoteJudge.judge(s, r.expectedKey()) }
            }
            StageGame.SkyChord -> {
                val chord = ChordJudge(StageKeys.C_CHORD)
                rounds(step, kid, w, phrases = false, wrongKey = { 62 }, onLetGo = chord::reset) { s, _ -> chord.judge(s) }
            }
            is StageGame.Tap -> tap(game, kid, w)
            is StageGame.Beat -> beat(ex, kid, w)
            is StageGame.Jam -> jam(game, ex, kid, w)
            StageGame.NightClap -> clap(kid, w)
            is StageGame.Concert -> { w.ms += (2 * ex.beats * 60_000 / ex.bpm).toLong(); perform(ex, kid, w) }
            null -> when (step.kind) {
                StepKind.PERFORM -> perform(ex, kid, w)
                StepKind.LISTEN -> listen(ex, w)
                else -> waitMode(ex, kid, w)
            }
        }
    }

    /** A stage of rounds (ui/playground rememberStage): shown, heard, let go after three tries. The wrong kid plays [wrongKey]. */
    private fun rounds(step: Step, kid: Kid, w: Walk, phrases: Boolean, wrongKey: (StageRounds) -> Int = { 66 }, onLetGo: () -> Unit = {},
                       judge: (StageSound, StageRounds) -> Answer): Pair<Report?, String> {
        val ex = step.exercise!!
        val r = StageRounds(ex.toSong("t", "t"), ex.practice, phrases = phrases)
        var now = 0L
        var lastTry = 0L
        r.start(now)
        while (!r.ended) {
            now += 20
            assertTrue("a stage that never ends: ${step.text}", now < 30 * 60_000)
            if (r.tick(20.0, now).second is RoundCoach.Cue.LetGo) onLetGo()
            if (kid != Kid.WRONG || r.coach.phase != RoundCoach.Phase.LISTEN || now - lastTry < 1_500) continue
            lastTry = now
            // F sharp 4 is in none of the stages' positions; higher or lower hears the pup's own note; the chord a D.
            val o = r.answer(judge(StageSound(now, HeardBy.NAMED, wrongKey(r), riseDb = -30.0, final = true), r), now)
            if (o is StageRounds.Outcome.Missed && o.cue is RoundCoach.Cue.LetGo) onLetGo()
            assertTrue("nothing right is found", o !is StageRounds.Outcome.Found)
        }
        w.ms += now
        assertEquals("every round let go", r.rounds, r.letGo.size)
        return r.report() to "${r.rounds} rounds, all let go"
    }

    /** A tap game: the silent kid never taps; the other taps the other answer and plays a key that is not it. */
    private fun tap(game: StageGame.Tap, kid: Kid, w: Walk): Pair<Report?, String> {
        val q = TapQuiz(game.example, game.rounds)
        var now = 0L
        var lastTry = 0L
        q.start(now)
        while (!q.done) {
            now += 20
            assertTrue("a tap game that never ends", now < 30 * 60_000)
            q.tick(now)
            if (kid != Kid.WRONG || now - lastTry < 1_500) continue
            lastTry = now
            when (q.phase) {
                TapQuiz.Phase.ASK -> q.tap(game.kind.choices.first { it != q.round!!.answer }, now)
                TapQuiz.Phase.PLAY -> q.key(q.round!!.play!! + 1, now)
                else -> {}
            }
        }
        w.ms += now
        assertEquals(0, q.right)
        assertEquals(game.rounds.indices.toList(), q.letGo)
        return q.report() to "${game.rounds.size} taps, all shown by the pup"
    }

    /** A key on every beat: tries like the drum's (nothing heard asks again, three let go); keys that are not C light no star. */
    private fun beat(ex: Exercise, kid: Kid, w: Walk): Pair<Report?, String> {
        val b = BeatStars(parseTokens(ex.right).filter { !it.rest }.map { it.startBeat }, ex.bpm)
        var attempt = 0
        w.ms += (4 * b.beatMs).toLong()                          // the pup's bar first
        while (true) {
            b.reset()
            w.ms += b.lengthMs.toLong() + 2_000
            val heard = kid == Kid.WRONG                         // the wrong kid plays D on every beat: heard, no star
            when (ClapTries.after(attempt, heard)) {
                ClapTries.Next.DONE -> return b.report() to "beat, ${b.lit.size} stars"
                ClapTries.Next.AGAIN -> attempt++
                ClapTries.Next.LET_GO -> return Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0) to "beat, ${attempt + 1} silent tries, let go"
            }
        }
    }

    /** The jam: the silent kid's quiet stretches end it early; white keys draw no stars but keep it going to the band's end. */
    private fun jam(game: StageGame.Jam, ex: Exercise, kid: Kid, w: Walk): Pair<Report?, String> {
        val j = StarJam(game.bars, ex.bpm)
        j.start()
        var ms = 0.0
        var lastKey = 0.0
        while (true) {
            ms += 20
            if (kid == Kid.WRONG && ms - lastKey >= 1_500) { lastKey = ms; j.heard(64, ms) }
            when (j.tick(ms)) {
                StarJam.Tick.End, StarJam.Tick.LetGo -> break
                else -> {}
            }
        }
        w.ms += ms.toLong()
        assertTrue(j.stars.isEmpty())
        return j.report() to "jam, ${j.whites} white keys, ${j.tries} quiet tries"
    }

    private fun clap(kid: Kid, w: Walk): Pair<Report?, String> {
        var attempt = 0
        while (true) {
            w.ms += 16_000
            when (ClapTries.after(attempt, heard = false)) {
                ClapTries.Next.DONE -> error("no claps were heard")
                ClapTries.Next.AGAIN -> attempt++
                ClapTries.Next.LET_GO -> return Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0) to "${attempt + 1} silent tries ($kid), let go"
            }
        }
    }

    private fun perform(ex: Exercise, kid: Kid, w: Walk): Pair<Report?, String> {
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

    private fun listen(ex: Exercise, w: Walk): Pair<Report?, String> {
        val t = Transport(ex.toSong("t", "t")).apply { mode = Mode.LISTEN; play() }
        while (t.playing) { t.tick(20.0); w.ms += 20 }
        return t.report() to "listened"
    }

    private fun waitMode(ex: Exercise, kid: Kid, w: Walk): Pair<Report?, String> {
        val t = Transport(ex.toSong("t", "t")).apply { mode = Mode.WAIT; practice = ex.practice; play(leadInMs = 2_000.0) }
        val coach = RoundCoach(firstDemoMs = 0, demoMs = 0, againDemoMs = 0, quietMs = 9_000)
        var now = 0L
        var waiting = false
        var lastTry = 0L
        var letGo = 0
        fun letGoGroup() { letGo++; for (k in t.expected.map { it.midi }) if (t.noteInput(k) == InputResult.GROUP_DONE) break }
        while (t.playing) {
            now += 20
            assertTrue("a wait that never ends", now < 30 * 60_000)
            t.tick(20.0)
            if (t.waiting != waiting) { waiting = t.waiting; if (waiting) coach.begin(0, now) else coach.found() }
            if (!t.waiting) continue
            if (coach.tick(now) is RoundCoach.Cue.LetGo) { letGoGroup(); continue }
            if (kid == Kid.WRONG && now - lastTry >= 1_500) {
                lastTry = now
                if (t.noteInput(23) == InputResult.WRONG && coach.miss(now) is RoundCoach.Cue.LetGo) letGoGroup()
            }
        }
        w.ms += now
        return t.report() to "wait, $letGo groups let go"
    }
}
