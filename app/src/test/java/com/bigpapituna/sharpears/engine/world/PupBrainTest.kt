package com.bigpapituna.sharpears.engine.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PupBrainTest {
    private val t0 = 1_000_000L

    @Test fun idleActsAfterTenSecondsThenSleepAfterAMinute() {
        val b = PupBrain(seed = 5L, now = t0)
        assertEquals(PupLook.Base, b.look(t0))
        assertEquals(PupLook.Base, b.look(t0 + 9_999))
        val first = b.look(t0 + 10_000)
        assertTrue(first is PupLook.Idle)
        assertEquals(first, b.look(t0 + 10_000 + PupBrain.ACT_MS - 1))
        assertEquals(PupLook.Base, b.look(t0 + 10_000 + PupBrain.ACT_MS))       // back to the screen's pose between acts
        val acts = (0 until PupBrain.ACTS).map { k -> (b.look(t0 + 10_000 + k * 15_000 + 100) as PupLook.Idle).act }
        assertEquals(4, acts.size)
        acts.zipWithNext().forEach { (a, c) -> assertNotEquals("never the same act twice in a row", a, c) }
        assertEquals(PupLook.Base, b.look(t0 + 59_999))
        assertEquals(PupLook.Asleep, b.look(t0 + 60_000))
        assertEquals(PupLook.Asleep, b.look(t0 + 3_600_000))
    }

    @Test fun theNextInputWakesHim() {
        val b = PupBrain(seed = 5L, now = t0)
        assertEquals(PupLook.Asleep, b.look(t0 + 70_000))
        b.input(t0 + 70_000)
        assertEquals(PupLook.Base, b.look(t0 + 70_001))
        assertTrue(b.look(t0 + 80_000) is PupLook.Idle)                           // the clock starts again from the input
    }

    @Test fun everyActTurnsUpAcrossQuietStretches() {
        val seen = HashSet<IdleAct>()
        for (i in 0 until 20) {
            val b = PupBrain(seed = 9L, now = t0 + i * 1_000L)
            seen += (b.look(t0 + i * 1_000L + 10_500) as PupLook.Idle).act
        }
        assertEquals(IdleAct.entries.toSet(), seen)
    }

    @Test fun aReactionShowsForItsTimeAndCountsAsInput() {
        val b = PupBrain(seed = 1L, now = t0)
        b.react(PupReaction.SURPRISED, t0 + 50_000)
        assertEquals(PupLook.Reacting(PupReaction.SURPRISED), b.look(t0 + 50_000 + 1_000))
        assertEquals(PupLook.Base, b.look(t0 + 50_000 + PupReaction.SURPRISED.ms))
        // The idle clock restarted at the reaction: no sleep at the old 60 s mark.
        assertEquals(PupLook.Base, b.look(t0 + 59_000))
        assertEquals(PupLook.Asleep, b.look(t0 + 110_000))
    }

    @Test fun aReactionWakesASleepingPup() {
        val b = PupBrain(seed = 1L, now = t0)
        assertEquals(PupLook.Asleep, b.look(t0 + 65_000))
        b.react(PupReaction.HEAD_TILT, t0 + 65_000)
        assertEquals(PupLook.Reacting(PupReaction.HEAD_TILT), b.look(t0 + 65_100))
        assertEquals(PupLook.Base, b.look(t0 + 65_000 + PupReaction.HEAD_TILT.ms))
    }

    @Test fun goodnightEndsInSleepUntilTheNextInput() {
        val b = PupBrain(seed = 1L, now = t0)
        b.react(PupReaction.GOODNIGHT, t0 + 1_000)
        assertEquals(PupLook.Reacting(PupReaction.GOODNIGHT), b.look(t0 + 2_000))
        assertEquals(PupLook.Asleep, b.look(t0 + 1_000 + PupReaction.GOODNIGHT.ms))
        assertEquals(PupLook.Asleep, b.look(t0 + 8_000))
        b.input(t0 + 9_000)
        assertEquals(PupLook.Base, b.look(t0 + 9_001))
    }

    @Test fun aMoodHoldsAndKeepsIdleAway() {
        val b = PupBrain(seed = 1L, now = t0)
        b.mood = PupMood.THINKING
        assertEquals(PupLook.Mood(PupMood.THINKING), b.look(t0 + 10_500))
        assertEquals(PupLook.Mood(PupMood.THINKING), b.look(t0 + 120_000))       // no sleep while he thinks
        b.react(PupReaction.CLAP, t0 + 120_000)
        assertEquals(PupLook.Reacting(PupReaction.CLAP), b.look(t0 + 120_100))     // a reaction still shows over a mood
        assertEquals(PupLook.Mood(PupMood.THINKING), b.look(t0 + 125_000))
        b.mood = PupMood.SLEEPING
        assertEquals(PupLook.Asleep, b.look(t0 + 125_000))
        b.mood = PupMood.NONE
        assertEquals(PupLook.Base, b.look(t0 + 125_000))
    }

    @Test fun aPupWithoutIdleKeepsHisPose() {
        val b = PupBrain(seed = 1L, now = t0, idle = false)
        assertEquals(PupLook.Base, b.look(t0 + 15_000))
        assertEquals(PupLook.Base, b.look(t0 + 600_000))
        assertNull(b.nextChange(t0 + 1))
        b.react(PupReaction.DANCE, t0 + 2)
        assertEquals(t0 + 2 + PupReaction.DANCE.ms, b.nextChange(t0 + 3))
    }

    @Test fun nextChangeLandsOnEveryBoundary() {
        val b = PupBrain(seed = 1L, now = t0)
        assertEquals(t0 + 10_000, b.nextChange(t0))
        assertEquals(t0 + 10_000 + PupBrain.ACT_MS, b.nextChange(t0 + 10_000))
        assertEquals(t0 + 25_000, b.nextChange(t0 + 10_000 + PupBrain.ACT_MS))
        assertEquals(t0 + 60_000, b.nextChange(t0 + 55_000 + PupBrain.ACT_MS))
        assertNull(b.nextChange(t0 + 60_000))
        // Walking the boundaries visits the same looks as polling every 100 ms.
        var now = t0
        val stepped = mutableListOf<PupLook>()
        while (true) { stepped += b.look(now); now = b.nextChange(now) ?: break }
        val polled = (0..700).map { b.look(t0 + it * 100L) }.fold(mutableListOf<PupLook>()) { l, x -> if (l.lastOrNull() != x) l += x; l }
        assertEquals(polled, stepped)
    }

    @Test fun bounceFollowsPitchAndLoudness() {
        assertTrue(Bounce.height(84, 12.0, big = false) > Bounce.height(48, 12.0, big = false))
        assertTrue(Bounce.height(60, 30.0, big = false) > Bounce.height(60, 6.0, big = false))
        assertTrue(Bounce.height(60, 12.0, big = true) > 2.5f * Bounce.height(60, 12.0, big = false))
        assertTrue(Bounce.height(127, 60.0, big = true) <= 0.55f)
        assertEquals(0f, Bounce.pitch(0), 0f); assertEquals(1f, Bounce.pitch(127), 0f)
        assertTrue(Bounce.squash(40, false) > Bounce.squash(90, false))
    }
}
