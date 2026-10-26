package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.skin.Stars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14 depth: Perform timing per note and per bar, the plain line, the song's 3-star rule and the live tick. */
class PerformTimingTest {
    // 4/4 at 120 bpm: a beat is 500 ms, a bar 2000 ms.
    private val barMs = 2000.0

    /** Eight bars of four quarter notes, one note per beat, with [off] giving each note's offset (null: missed). */
    private fun eightBars(off: (bar: Int, beat: Int) -> Double?): List<NoteTiming> =
        (1..8).flatMap { bar -> (0 until 4).map { beat -> NoteTiming(((bar - 1) * 2000 + beat * 500).toLong(), listOf(60), off(bar, beat)) } }

    @Test fun rushedFirstBeatsAreNamedByBeatAndBar() {
        // Sam's walk: the first beat of bars 3 and 7 rushed by 120 ms, the rest on the beat.
        val r = PerformTiming.of(eightBars { bar, beat -> if (beat == 0 && (bar == 3 || bar == 7)) -120.0 else 0.0 }, barMs, 4)
        assertEquals("You rush the first beat of bars 3 and 7.", r.line)
        assertEquals(8, r.bars.size)
        assertEquals(BarVerdict.RUSHED, r.bars[2].verdict)
        assertEquals(BarVerdict.RUSHED, r.bars[6].verdict)
        assertEquals(listOf(1, 2, 4, 5, 6, 8), r.bars.filter { it.verdict == BarVerdict.CLEAN }.map { it.bar })
        val first = r.bars[2].notes[0]
        assertEquals(-120, first.offsetMs)
        assertEquals(0, first.onBeat)
        assertEquals("Beat 1", PerformTiming.beatLabel(first.beat))
        assertEquals("120 ms early", PerformTiming.offsetWords(first.offsetMs))
    }

    @Test fun draggedBeatsAndDraggedBars() {
        val third = PerformTiming.of(eightBars { bar, beat -> if (beat == 2 && bar in setOf(2, 5, 6)) 100.0 else 0.0 }, barMs, 4)
        assertEquals("You drag the third beat of bars 2, 5 and 6.", third.line)
        // Late notes spread over different beats: no beat to name, the bars are.
        val bars = PerformTiming.of(eightBars { bar, beat -> if ((bar == 4 && beat == 1) || (bar == 6 && beat == 3)) 90.0 else 0.0 }, barMs, 4)
        assertEquals("You drag bars 4 and 6.", bars.line)
        assertEquals(BarVerdict.DRAGGED, bars.bars[3].verdict)
    }

    @Test fun messyBarsAndSteadyRuns() {
        val messy = PerformTiming.of(eightBars { bar, beat -> if (bar == 5 && beat == 2) null else 0.0 }, barMs, 4)
        assertEquals(BarVerdict.MESSY, messy.bars[4].verdict)
        assertEquals("Bar 5 was uneven. Try it on a loop.", messy.line)
        val mixed = PerformTiming.verdict(listOf(TimedNote(1, 0.0, listOf(60), -90), TimedNote(1, 1.0, listOf(60), 90)))
        assertEquals("early and late in one bar", BarVerdict.MESSY, mixed)
        val steady = PerformTiming.of(eightBars { _, beat -> listOf(-20.0, 15.0, 0.0, 30.0)[beat] }, barMs, 4)
        assertEquals(PerformTiming.STEADY, steady.line)
        assertTrue(steady.bars.all { it.verdict == BarVerdict.CLEAN })
        // One early note in a bar of eight is no rush (under a quarter of the bar).
        assertEquals(BarVerdict.CLEAN, PerformTiming.verdict((0 until 8).map { TimedNote(1, it * 0.5, listOf(60), if (it == 3) -100 else 0) }))
    }

    @Test fun aLeanAcrossTheRunAndTheMidiOffsetHint() {
        val lean = PerformTiming.of(eightBars { _, _ -> -40.0 }, barMs, 4)
        assertEquals("You play a little ahead of the beat all the way through.", lean.line)
        assertEquals(-40, lean.biasMs)
        // Every note 100 ms late on a MIDI keyboard points at the offset; by touch it is just dragging.
        val late = eightBars { _, _ -> 100.0 }
        assertEquals("Nearly every note came late. If it felt in time, set the MIDI timing offset in Settings.", PerformTiming.of(late, barMs, 4, midi = true).line)
        assertFalse(PerformTiming.of(late, barMs, 4).line.contains("MIDI"))
    }

    @Test fun realMillisecondsAtAPracticeSpeed() {
        // At half speed 60 song ms of lead are 120 ms the player felt: a rush.
        val r = PerformTiming.of(eightBars { bar, beat -> if (beat == 0 && bar in setOf(1, 2)) -60.0 else 0.0 }, barMs, 4, speed = 0.5)
        assertEquals(-120, r.bars[0].notes[0].offsetMs)
        assertEquals("You rush the first beat of bars 1 and 2.", r.line)
    }

    @Test fun barsWithNothingToPlayStayInTheStripWithNoVerdict() {
        val notes = listOf(NoteTiming(0, listOf(60), 0.0), NoteTiming(6000, listOf(62), 0.0))
        val r = PerformTiming.of(notes, barMs, 4)
        assertEquals(listOf(1, 2, 3, 4), r.bars.map { it.bar })
        assertNull(r.bars[1].verdict)
        assertTrue(PerformTiming.of(emptyList(), barMs, 4).isEmpty)
    }

    @Test fun wordsFollowTheCopyRules() {
        assertEquals("bars 2, 5, 9, 12 and 3 more", PerformTiming.barList(listOf(2, 5, 9, 12, 14, 15, 16)))
        assertEquals("Beat 2 and", PerformTiming.beatLabel(1.5))
        assertEquals("Beat 4", PerformTiming.beatLabel(3.0))
        assertEquals("on the beat", PerformTiming.offsetWords(6))
        assertEquals("missed", PerformTiming.offsetWords(null))
        val lines = listOf(PerformTiming.STEADY, SongStars.RULE, SongStars.RULE_TWO) + PerformTiming.of(eightBars { b, _ -> if (b == 2) null else -100.0 }, barMs, 4, midi = true).line
        lines.forEach { assertFalse(it, it.contains('—') || it.contains('–') || it.contains('·')) }
    }

    @Test fun scoreKeepsEveryNotesOffsetAndMisses() {
        val notes = listOf(Note(60, 0, 400, Hand.RIGHT, 80), Note(62, 1000, 400, Hand.RIGHT, 80), Note(64, 2000, 400, Hand.RIGHT, 80))
        val s = PerformScore(groupOnsets(notes), { true })
        assertEquals(Grade.GOOD, s.input(60, -40.0))
        assertEquals(Grade.OK, s.input(64, 2200.0))
        s.advance(5000.0)
        assertEquals(listOf(NoteTiming(0, listOf(60), -40.0), NoteTiming(1000, listOf(62), null), NoteTiming(2000, listOf(64), 200.0)), s.timings())
        assertEquals(1, s.goodCount)
    }

    @Test fun theLiveTickSitsInsideTheGoodWindow() {
        assertEquals(0f, PerformTiming.tick(0.0), 0f)
        assertEquals(-0.5f, PerformTiming.tick(-75.0), 1e-6f)
        assertEquals(1f, PerformTiming.tick(260.0), 0f)
    }

    // ---- The song's 3-star rule ----

    private fun recap(good: Int, ok: Int, miss: Int, bias: Int = 0) = Recap(good, ok, miss, bias, good)

    @Test fun samsRunsUnderTheSongRule() {
        // Sam's walk: 40 good, 1 OK, 1 missed, 98% accuracy, bias -126. The miss keeps it at two, and the recap now says so.
        val sam = recap(40, 1, 1, bias = -126)
        assertEquals(98, sam.accuracy)
        assertEquals(2, songStars(sam))
        assertEquals("This run: 1 miss, 40 of 42 notes in time.", SongStars.runLine(sam))
        // 98% in time, nothing missed, every note a little early: three stars now (the old average-timing rule gave two).
        val early = recap(49, 1, 0, bias = -126)
        assertEquals(98, early.inTime)
        assertEquals(2, Stars.perform(early))
        assertEquals(3, songStars(early))
        assertEquals(3, Stars.of(early, 0))                       // the song recap's stars are the song rule
        assertEquals("This run: no misses, 49 of 50 notes in time.", SongStars.runLine(early))
    }

    @Test fun theSongRuleAtItsEdges() {
        assertEquals(3, songStars(recap(9, 1, 0)))                // 9 of 10 in time, no misses
        assertEquals(2, songStars(recap(8, 2, 0)))                // 8 of 10: no misses, too few in time
        assertEquals(2, songStars(recap(10, 0, 2)))               // two misses
        assertEquals(2, songStars(recap(30, 2, 3)))               // three misses but 30 of 35 (86%) in time
        assertEquals(1, songStars(recap(20, 5, 5)))               // five misses, 67% in time
        assertEquals(1, songStars(recap(0, 0, 0)))
        assertEquals(listOf(SongStars.RULE, SongStars.RULE_TWO, "This run: 5 misses, 20 of 30 notes in time."), SongStars.ruleLines(recap(20, 5, 5)))
        assertEquals(2, SongStars.ruleLines(recap(9, 1, 0)).size)
        // A lesson step keeps its own rule.
        assertEquals(3, Stars.perform(recap(9, 1, 0, bias = 30)))
    }
}
