package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No dead ends (2.1.1): every round is shown first, three tries and it goes by; a step's third failed try lets it go. */
class RoundCoachTest {
    @Test fun aRoundIsShownFirstThenHeard() {
        val c = RoundCoach(firstDemoMs = 3_000, demoMs = 1_500)
        val d = c.begin(0, 0)
        assertTrue(d.first); assertFalse(d.again); assertEquals(RoundCoach.Phase.DEMO, c.phase)
        assertNull("deaf while it plays", c.miss(1_000))
        assertNull(c.tick(2_999))
        assertEquals(RoundCoach.Cue.Listen(0, first = true), c.tick(3_000))
        c.found()
        val next = c.begin(1, 5_000)
        assertFalse("only the step's first round says it", next.first)
        assertEquals(1_500, next.ms)
        assertEquals(RoundCoach.Cue.Listen(1, first = false), c.tick(6_500))
    }

    @Test fun threeTriesLetTheRoundGo() {
        val c = RoundCoach()
        c.begin(0, 0); c.tick(c.firstDemoMs)
        assertNull(c.miss(4_000)); assertNull(c.miss(5_000))
        assertEquals(RoundCoach.Cue.LetGo(0), c.miss(6_000))
        assertEquals(RoundCoach.Phase.DONE, c.phase)
    }

    @Test fun quietStretchesAreTriesAndShowTheRoundAgain() {
        val c = RoundCoach(quietMs = 7_000, firstDemoMs = 3_000, againDemoMs = 2_000)
        c.begin(0, 0); c.tick(3_000)
        val again = c.tick(10_000) as RoundCoach.Cue.Demo
        assertTrue(again.again); assertEquals(1, c.tries)
        assertEquals(RoundCoach.Cue.Listen(0, first = false), c.tick(12_000))
        // A miss restarts the quiet clock.
        assertNull(c.miss(15_000)); assertEquals(2, c.tries)
        assertNull(c.tick(21_999))
        assertEquals(RoundCoach.Cue.LetGo(0), c.tick(22_000))
    }

    @Test fun stageRoundsGoByWithOneStarAndTheRunEnds() {
        val ex = PLAYGROUND.lessons[1].steps.first { it.game == Game.KEY_HUNT }.exercise!!
        val r = StageRounds(ex.toSong("t", "t"), ex.practice, gapMs = 1_000)
        var now = 0L
        r.start(now)
        val cues = mutableListOf<RoundCoach.Cue>()
        fun run(ms: Long) { val end = now + ms; while (now < end) { now += 20; r.tick(20.0, now).second?.let { cues += it } } }
        run(100)
        assertTrue(cues.first() is RoundCoach.Cue.Demo)
        run(3_000)
        // Round 1: found. Round 2: three misses. Round 3: nothing at all.
        assertEquals(InputResult.GROUP_DONE, r.found(73, now))
        run(1_000 + r.coach.demoMs + 40)
        repeat(2) { assertNull(r.miss(now)) }
        assertEquals(RoundCoach.Cue.LetGo(1), r.miss(now))
        assertEquals(listOf(1), r.letGo)
        run(60_000)
        assertEquals(listOf(1, 2), r.letGo)
        assertTrue(r.ended)
        assertEquals(3, r.report().groups)
        assertEquals("a let go is no slip", 0, r.report().wrongKeys)
    }

    /**
     * QA 2.8.3: a key touched while the pup still showed the round was dropped, and the quiet stretches after it let the round
     * go (Leo's "Let's hear you play" put him in the garden). A touched answer counts once the demo has played its key; any
     * other touch in a demo is no try, so an early touch can only ever help.
     */
    @Test fun aTouchDuringTheDemoCountsOnceTheDemoHasPlayedTheKey() {
        val ex = PLAYGROUND.lessons[1].steps.first { it.game == Game.KEY_HUNT }.exercise!!
        val r = StageRounds(ex.toSong("t", "t"), ex.practice, gapMs = 1_000)
        var now = 0L
        r.start(now)
        fun run(ms: Long) { val end = now + ms; while (now < end) { now += 20; r.tick(20.0, now) } }
        run(100)
        assertEquals(RoundCoach.Phase.DEMO, r.coach.phase)
        val key = r.demoKeys().first()
        assertFalse("nothing shown yet", r.takesEarly(Answer.Found(key), shown = 0))
        assertEquals(InputResult.IGNORED, r.found(key, now))
        // Wrong keys while it shows, as many as a kid likes: no try, never a let go.
        repeat(10) { assertFalse(r.takesEarly(Answer.Miss(MissWhy.WHITE_KEY, 62), shown = 1)) }
        assertEquals(0, r.coach.tries)
        assertTrue(r.takesEarly(Answer.Found(key), shown = 1))
        assertEquals(InputResult.GROUP_DONE, r.found(key, now, early = true))
        assertEquals(1, r.done)
        assertEquals(RoundCoach.Phase.DONE, r.coach.phase)
        assertTrue(r.letGo.isEmpty())
        // Listening, the rule has nothing to do; the round's own tries apply.
        run(1_000 + r.coach.demoMs + 40)
        assertEquals(RoundCoach.Phase.LISTEN, r.coach.phase)
        assertFalse(r.earlyTouchMayCount(shown = 5))
    }

    @Test fun inAPhraseATouchedNoteWaitsForTheDemoToPlayIt() {
        val ex = com.bigpapituna.sharpears.engine.Exercise(right = "C4:1 D4:1 E4:1", bpm = 80.0, practice = com.bigpapituna.sharpears.engine.Practice.RIGHT)
        val r = StageRounds(ex.toSong("t", "t"), ex.practice, phrases = true, gapMs = 1_000)
        var now = 0L
        r.start(now)
        val cues = mutableListOf<RoundCoach.Cue>()
        fun run(ms: Long) { val end = now + ms; while (now < end) { now += 20; r.tick(20.0, now).second?.let { cues += it } } }
        run(40)
        assertEquals(listOf(60, 62, 64), r.demoKeys())
        assertTrue(r.takesEarly(Answer.Found(60), shown = 1))
        r.found(60, now, early = true)
        assertEquals("the phrase goes on, the demo too", RoundCoach.Phase.DEMO, r.coach.phase)
        assertFalse("D is not shown yet", r.takesEarly(Answer.Found(62), shown = 1))
        assertTrue(r.takesEarly(Answer.Found(62), shown = 2))
        r.found(62, now, early = true)
        // The demo ends: listening starts from E, and "Your turn!" comes as always.
        run(r.coach.firstDemoMs)
        assertTrue(cues.any { it is RoundCoach.Cue.Listen })
        assertEquals(64, r.expectedKey())
        assertEquals(InputResult.GROUP_DONE, r.found(64, now))
        assertEquals(1, r.done)
        assertTrue(r.letGo.isEmpty())
    }

    @Test fun aStepIsLetGoOnlyInThePlaygroundAfterThreeTries() {
        assertFalse(StepRetry.letGo("playground-7", 2))
        assertTrue(StepRetry.letGo("playground-7", 3))
        assertFalse("grown-up lessons keep Try again", StepRetry.letGo("basics-1", 5))
    }

    @Test fun theDrumAsksAgainThenLetsGo() {
        assertEquals(ClapTries.Next.DONE, ClapTries.after(0, heard = true))
        assertEquals(ClapTries.Next.AGAIN, ClapTries.after(0, heard = false))
        assertEquals(ClapTries.Next.AGAIN, ClapTries.after(1, heard = false))
        assertEquals(ClapTries.Next.LET_GO, ClapTries.after(2, heard = false))
    }
}
