package com.bigpapituna.sharpears.engine.playground

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit 2026-10-20: time at a Playground stage counted as no practice at all, so the fair's games and most of the night
 * sky earned no paw and never reached the daily goal (its cheer, bedtime). Every exercise step is timed by exactly one
 * clock now: the embedded player's own, or its stage's.
 */
class StageTimeTest {
    @Test fun everyExerciseStepIsTimedByExactlyOneClock() {
        for (lesson in PLAYGROUND_LEVELS.flatMap { it.lessons }) for (step in lesson.steps) {
            if (step.exercise == null) continue
            val player = step.game == null || step.game in StageTime.RUN_THE_PLAYER
            assertTrue("${lesson.id}: \"${step.text}\" (${step.game})", player != StageTime.counts(step.game))
        }
    }

    @Test fun aKidWhoPlaysOnlyTheFairsGamesStillPractises() {
        val fair = PLAYGROUND_LEVELS.first { it.id == FAIR_ID }.lessons
        assertTrue(fair.isNotEmpty())
        for (l in fair) {
            val timed = l.steps.filter { it.exercise != null }
            assertTrue(l.id, timed.isNotEmpty() && timed.all { StageTime.counts(it.game) })
        }
    }

    @Test fun theStagesThatRunThePlayerAreLeftToIt() {
        assertFalse(StageTime.counts(null))
        assertFalse(StageTime.counts(Game.CONCERT))
        assertFalse(StageTime.counts(Game.SET_LIST))
        Game.entries.filter { it !in StageTime.RUN_THE_PLAYER }.forEach { assertTrue("$it", StageTime.counts(it)) }
    }
}
