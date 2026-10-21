package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.playground.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a lesson step and a song run write to the log, from the reports and tallies the player hands over. */
class RunEventsTest {
    private val play = Step(StepKind.PLAY, "Your turn.", Exercise(right = "C4:1 D4:1 E4:1", practice = Practice.RIGHT))
    private val perform = Step(StepKind.PERFORM, "In time.", Exercise(right = "C4:1 D4:1", practice = Practice.LEFT, pass = 60))
    private fun waitRun(groups: Int, wrong: Int) = Report(emptyList(), 0, wrong, null, groups)
    private fun tally(near: Int, far: Int, vararg hints: Int) = RunTally().apply {
        repeat(near) { wrong(61, 60) }; repeat(far) { wrong(66, 60) }
        hints.forEach { hint(it); waitEnded() }
    }

    @Test fun aStepPassedOnTheThirdTryAddsItsTriesUp() {
        val rec = StepRecorder("pond-3", 2, play, openedMs = 1_000)
        rec.run(waitRun(1, 4), tally(1, 3, 3))
        rec.run(waitRun(2, 2), tally(2, 0, 1, 2))
        rec.run(waitRun(3, 0), tally(0, 0))
        val e = rec.event(at = 9, nowMs = 61_000, passed = true)!!
        assertEquals(BriefEvent.LessonStep(9, "pond-3", 2, "PLAY", tries = 3, hintsUsed = listOf(3, 1, 2), durationMs = 60_000,
            hands = Hands.RIGHT, stars = 3, notes = 3, wrongKeys = 6, nearMisses = 3, farMisses = 3), e)
        assertNull("once only", rec.event(10, 62_000, passed = true))
    }

    @Test fun aPerformStepKeepsItsAccuracyAndTimingAndALetGoIsOneStar() {
        val rec = StepRecorder("hall-7", 3, perform.copy(game = Game.CONCERT), 0)
        rec.run(Report(emptyList(), 0, 0, Recap(5, 1, 4, -35, 3), letGoRounds = 0, rounds = 0), null, letGo = true)
        val e = rec.event(5, 30_000, passed = true)!!
        assertEquals(1, e.stars); assertEquals(60, e.accuracy); assertEquals(-35, e.timingMeanMs); assertEquals(6, e.notes)
        assertEquals("CONCERT", e.game); assertEquals(Hands.LEFT, e.hands); assertEquals(true, e.letGo)
    }

    @Test fun skippedStepsAndLessonsClosedMidStep() {
        val skipped = StepRecorder("pond-1", 1, play, 0).event(1, 5_000, passed = false, skipped = true)!!
        assertEquals(1, skipped.stars); assertEquals(true, skipped.skipped); assertEquals(0, skipped.tries)
        val left = StepRecorder("pond-1", 1, play, 0).apply { run(waitRun(1, 3), null); run(waitRun(0, 5), null) }.event(1, 5_000, passed = false)!!
        assertEquals(true, left.left); assertNull(left.stars); assertEquals(2, left.tries); assertEquals(8, left.wrongKeys)
        val read = StepRecorder("sit", 0, Step(StepKind.READ, "Sit."), 0).event(1, 20_000, passed = true)!!
        assertNull(read.stars); assertEquals("READ", read.kind); assertNull(read.hands)
    }

    @Test fun aTryLeftBeforeItsEndCountsWithItsWrongKeysAndHints() {
        val rec = StepRecorder("pond-2", 1, play, 0)
        rec.abandoned(tally(1, 2, 3, 3))
        val e = rec.event(1, 9_000, passed = false)!!
        assertEquals(true, e.left); assertEquals(1, e.tries); assertEquals(3, e.wrongKeys); assertEquals(listOf(3, 3), e.hintsUsed)
        assertNull(e.stars); assertEquals(1, e.nearMisses); assertEquals(2, e.farMisses)
        assertEquals("a copy is never a run under way", false, RunTally().apply { open = true }.copy().open)
    }

    @Test fun aSongRunWritesTheRunItsWrongKeysAndOctaveHints() {
        val t = tally(2, 1, 3).apply { octave() }
        val events = RunEvents.song(7, "ode", Mode.PERFORM, Practice.BOTH, Report(emptyList(), 0, 1, Recap(18, 2, 0, 42, 20)), t, 95_000,
            fromTop = true, duet = false)
        assertEquals(BriefEvent.SongRun(7, "ode", RunMode.PERFORM, Hands.BOTH, 100, 3, 42, 2, 1, 20, 3, listOf(3), 95_000), events[0])
        assertEquals(listOf(BriefEvent.WrongKey(7, 60, 61, true, 2, songId = "ode"), BriefEvent.WrongKey(7, 60, 66, false, 1, songId = "ode"),
            BriefEvent.OctaveHint(7, songId = "ode", count = 1)), events.drop(1))
        val wait = RunEvents.song(7, "ode", Mode.WAIT, Practice.RIGHT, waitRun(30, 4), RunTally(), 60_000, true, duet = true)
        assertEquals(BriefEvent.SongRun(7, "ode", RunMode.WAIT, Hands.RIGHT, null, 1, null, 0, 0, 30, 4, emptyList(), 60_000, duet = true), wait.single())
    }
}
