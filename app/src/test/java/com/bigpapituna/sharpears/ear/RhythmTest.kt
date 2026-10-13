package com.bigpapituna.sharpears.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.ear.Grade
import com.bigpapituna.sharpears.engine.ear.Meter
import com.bigpapituna.sharpears.engine.ear.RhythmTapGame
import com.bigpapituna.sharpears.engine.ear.Slot
import com.bigpapituna.sharpears.engine.ear.gradeFor
import com.bigpapituna.sharpears.engine.ear.gradeTaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmTest {
    private val expected = listOf(0L, 500L, 1000L, 1500L)

    @Test fun perfectTapsAreAllGood() {
        val r = gradeTaps(expected, expected)
        assertEquals(4, r.good)
        assertEquals(0, r.extraTaps.size)
        assertTrue(r.passed)
        assertEquals(0L, r.bias)
    }

    @Test fun earlyLateAndMissedTapsAreGradedByTheirError() {
        val r = gradeTaps(expected, listOf(10L, 600L, 850L))               // +10, +100, -150, nothing near 1500
        assertEquals(listOf(Grade.GOOD, Grade.OK, Grade.OK, Grade.MISSED), r.notes.map { it.grade })
        assertEquals(listOf(10L, 100L, -150L), r.notes.take(3).map { it.errorMs })
        assertNull(r.notes[3].tapMs)
        assertEquals(3, r.good + r.ok)
        assertEquals(1, r.missed)
        assertEquals(1 * 2 + 2, r.points)
        assertFalse(r.passed)                                               // 4 of 8 points
    }

    @Test fun windowsAreEightyAndOneSixtyMilliseconds() {
        assertEquals(Grade.GOOD, gradeFor(80)); assertEquals(Grade.GOOD, gradeFor(-80))
        assertEquals(Grade.OK, gradeFor(81)); assertEquals(Grade.OK, gradeFor(-160))
        assertEquals(Grade.OFF, gradeFor(161)); assertEquals(Grade.OFF, gradeFor(-400))
    }

    @Test fun aTapFarFromEveryNoteIsAnExtraNotAMatch() {
        val r = gradeTaps(listOf(0L, 1000L), listOf(200L, 1000L))            // 200 ms late: matched but off
        assertEquals(Grade.OFF, r.notes[0].grade)
        assertEquals(200L, r.notes[0].errorMs)
        val far = gradeTaps(listOf(0L, 1000L), listOf(600L, 1000L))          // 600 ms away: no note is near
        assertEquals(Grade.MISSED, far.notes[0].grade)
        assertEquals(listOf(600L), far.extraTaps)
    }

    @Test fun oneTapIsNeverUsedTwiceAndDoubleTapsAreExtras() {
        val close = gradeTaps(listOf(0L, 300L), listOf(150L))               // equally near both: the first note takes it
        assertEquals(1, close.notes.count { it.tapMs != null })
        assertEquals(1, close.missed)
        val dbl = gradeTaps(expected, listOf(0L, 30L, 500L, 1000L, 1500L))   // a double tap on the first note
        assertEquals(listOf(30L), dbl.extraTaps)
        assertEquals(4, dbl.good)
        assertTrue(dbl.passed)                                               // one stray tap is allowed
        assertFalse(gradeTaps(expected, listOf(0L, 30L, 500L, 540L, 1000L, 1500L)).passed)   // two are not
    }

    @Test fun biasShowsWhetherThePlayerRushesOrDrags() {
        assertEquals(50L, gradeTaps(expected, expected.map { it + 50 }).bias)
        assertEquals(-40L, gradeTaps(expected, expected.map { it - 40 }).bias)
        assertNull(gradeTaps(expected, emptyList()).bias)
        assertEquals(4, gradeTaps(expected, emptyList()).missed)
    }

    @Test fun everyLevelMakesValidPatterns() {
        for (level in 0..2) {
            val g = RhythmTapGame(level, seed = 7L + level, rounds = 300)
            val meters = mutableSetOf<Meter>()
            var prev: List<Slot>? = null
            while (!g.finished) {
                val p = g.pattern
                meters += p.meter
                assertEquals(p.totalTicks, p.slots.sumOf { it.ticks })
                assertFalse("starts on a note", p.slots.first().rest)
                assertTrue("at least three notes: ${p.words()}", p.notes.size >= 3)
                assertTrue(p.noteTimesMs.zipWithNext().all { (a, b) -> b > a })
                assertTrue(p.noteTimesMs.last() < p.totalMs)
                assertEquals(if (level == 0) 1 else 2, p.bars)
                assertTrue("not a repeat", prev == null || prev != p.slots)
                prev = p.slots
                g.skip()
            }
            when (level) {
                0 -> assertEquals(setOf(Meter.FOUR_FOUR), meters)
                1 -> assertEquals(setOf(Meter.FOUR_FOUR, Meter.THREE_FOUR), meters)
                else -> assertTrue(Meter.SIX_EIGHT in meters && Meter.THREE_FOUR in meters)
            }
        }
    }

    @Test fun higherLevelsUseDottedNotesRestsAndOffbeats() {
        fun seen(level: Int, ticks: Int, rest: Boolean): Boolean {
            val g = RhythmTapGame(level, seed = 3, rounds = 200)
            var found = false
            while (!g.finished) { if (g.pattern.slots.any { it.ticks == ticks && it.rest == rest }) found = true; g.skip() }
            return found
        }
        assertFalse(seen(0, 3, false))                                       // no dotted quarters at level 1
        assertTrue(seen(1, 3, false))
        assertTrue(seen(0, 2, true)); assertTrue(seen(1, 1, true))
        assertTrue(seen(2, 1, true))
    }

    @Test fun cuesAreTheBeatTheCountInAndTheNotes() {
        val g = RhythmTapGame(1, seed = 5)
        val p = g.pattern
        val countIn = g.countIn()
        assertEquals(p.meter.beats, countIn.size)
        assertTrue(countIn.first().accent && countIn.drop(1).none { it.accent })
        assertEquals(p.countInMs, Math.round(p.meter.beats * p.beatMs))
        val demo = g.demo()
        assertEquals(p.bars * p.meter.beats, demo.count { it.click })
        assertEquals(p.notes.size, demo.count { it.note != null })
        assertEquals(demo.sortedBy { it.atMs }, demo)
        assertEquals(p.bars * p.meter.beats, g.tapPulse().size)
    }

    @Test fun gameGradesAPerfectRoundAsClean() {
        val g = RhythmTapGame(0, seed = 1)
        val a = g.grade(g.pattern.noteTimesMs)
        assertEquals(ChordGame.Outcome.DONE, a.outcome)
        assertTrue(a.clean)
        assertEquals(1, g.score); assertEquals(1, g.streak)
        assertTrue(a.line!!.text.startsWith("Spot on"))
        assertTrue("says what it was", a.line!!.text.contains("It was:"))
        assertEquals(ChordGame.Outcome.IGNORED, g.grade(emptyList()).outcome)  // the round is over
        g.next()
        assertEquals(1, g.round)
    }

    @Test fun gameExplainsMissesLateTapsAndTapsOnRests() {
        val g = RhythmTapGame(0, seed = 1)
        val none = g.grade(emptyList())
        assertFalse(none.clean)
        assertTrue(none.line!!.text, none.line!!.text.contains("You missed note 1"))
        assertEquals(0, g.streak)
        g.next()
        val times = g.pattern.noteTimesMs
        val late = g.grade(times.mapIndexed { i, t -> if (i == 1) t + 200 else t })
        assertTrue(late.line!!.text, late.line!!.text.contains("Note 2 was 200 ms late"))
        assertTrue(late.line!!.spoken.contains("milliseconds"))
        // A tap on a rest, in a pattern that has one.
        val g2 = RhythmTapGame(0, seed = 2, rounds = 100)
        while (g2.pattern.slots.none { it.rest }) g2.skip()
        val rest = g2.pattern.slots.first { it.rest }
        val at = g2.pattern.timeOf(rest.startTick)
        val a = g2.grade(g2.pattern.noteTimesMs + at + (at + 20))
        assertTrue(a.line!!.text, a.line!!.text.contains("tapped on a rest"))
    }

    @Test fun outputLatencyIsSubtractedBeforeGrading() {
        val g = RhythmTapGame(0, seed = 4)
        val a = g.grade(g.pattern.noteTimesMs.map { it + 120 }, offsetMs = 120)
        assertTrue(a.clean)
        assertEquals(0L, g.result!!.bias)
    }

    @Test fun liveGradeUsesTheNearestNote() {
        val g = RhythmTapGame(0, seed = 4)
        val t = g.pattern.noteTimesMs[1]
        assertEquals(Grade.GOOD, g.liveGrade(t + 30))
        assertEquals(Grade.OK, g.liveGrade(t - 120))
    }

    @Test fun sameSeedSamePatterns() {
        val a = RhythmTapGame(2, seed = 99); val b = RhythmTapGame(2, seed = 99)
        repeat(10) { assertEquals(a.pattern.slots, b.pattern.slots); assertEquals(a.pattern.meter, b.pattern.meter); a.skip(); b.skip() }
    }
}
