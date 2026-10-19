package com.bigpapituna.sharpears.engine.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.3, the pick game's rounds: asked first, a right tap ends the round, three taps or quiet stretches let it go. */
class PickRoundsTest {
    private val rounds = listOf(
        PickRound("Which one sits tall?", listOf(Pick.SIT_SLOUCH, Pick.SIT_TALL), Pick.SIT_TALL),
        PickRound("Listen. Short or long?", listOf(Pick.SHORT, Pick.LONG), Pick.LONG, PickSound.LONG),
    )

    /** Ticks the clock until the round listens; returns the time. */
    private fun untilListening(g: PickRounds, from: Long): Long {
        var t = from
        while (g.coach.phase != RoundCoach.Phase.LISTEN) { t += 50; g.tick(t); assertTrue("never listened", t < from + 60_000) }
        return t
    }

    @Test fun aRightTapEndsTheRoundAndTheNextIsAskedAfterAPause() {
        val g = PickRounds(rounds)
        g.start(0)
        val cue = g.tick(0)
        assertTrue(cue is RoundCoach.Cue.Demo && cue.first)
        // A picture round takes a tap while the question is still being asked.
        val t = 10L
        assertEquals(PickRounds.Tap.Right("Yes! Tall, with room to move."), g.tap(Pick.SIT_TALL, t))
        assertEquals(1, g.done)
        assertEquals(null, g.tick(t + 100))
        assertTrue(g.tick(t + g.gapMs) is RoundCoach.Cue.Demo)
        assertEquals("a listening round waits for its sound", PickRounds.Tap.Ignored, g.tap(Pick.LONG, t + g.gapMs + 10))
        val u = untilListening(g, t + g.gapMs)
        val notIt = g.tap(Pick.SHORT, u)
        assertTrue(notIt is PickRounds.Tap.NotIt && "Listen again" in notIt.line)
        assertTrue(g.tap(Pick.LONG, u + 100) is PickRounds.Tap.Right)
        assertTrue(g.ended)
        assertEquals(1, g.report().wrongKeys)
        assertEquals(2, g.report().groups)
    }

    @Test fun threeTapsThatAreNotItLetTheRoundGo() {
        val g = PickRounds(rounds.take(1))
        g.start(0); g.tick(0)
        val t = 0L
        val a = g.tap(Pick.SIT_SLOUCH, t); val b = g.tap(Pick.SIT_SLOUCH, t + 1_000); val c = g.tap(Pick.SIT_SLOUCH, t + 2_000)
        assertTrue(a is PickRounds.Tap.NotIt && a.cue == null && b is PickRounds.Tap.NotIt && b.cue == null)
        assertTrue(c is PickRounds.Tap.NotIt && c.cue is RoundCoach.Cue.LetGo)
        assertEquals("That one slouches. Look for the one sitting tall.", (a as PickRounds.Tap.NotIt).line)
        assertTrue(g.ended)
        assertEquals(listOf(0), g.letGo)
    }

    @Test fun aSilentKidIsShownAgainThenLetGo() {
        val g = PickRounds(rounds.take(1))
        g.start(0)
        var t = 0L
        val cues = mutableListOf<RoundCoach.Cue>()
        while (!g.ended && t < 120_000) { t += 50; g.tick(t)?.let { cues += it } }
        assertTrue(g.ended)
        assertEquals("asked again twice, then let go", 2, cues.count { it is RoundCoach.Cue.Demo && it.again })
        assertTrue(cues.last() is RoundCoach.Cue.LetGo)
        assertTrue("about half a minute", t in 20_000..40_000)
    }
}
