package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoRampTest {
    private fun wait(wrong: Int) = Report(emptyList(), if (wrong > 0) 1 else 0, wrong, null)
    private fun perform(acc: Int) = Report(emptyList(), 0, 0, Recap(acc, 0, 100 - acc, 0, acc))

    @Test fun risesTenPointsPerCleanPassUpToFull() {
        val r = TempoRamp()
        assertEquals(50, r.percent)
        val seen = mutableListOf<Int>()
        repeat(7) { r.passEnded(Mode.WAIT, wait(0))?.let { seen += (it * 100).toInt() } }
        assertEquals(listOf(60, 70, 80, 90, 100), seen)
        assertEquals(100, r.percent)
        assertNull("stays at 100", r.passEnded(Mode.WAIT, wait(0)))
    }

    @Test fun aWrongNoteOrALowScoreHoldsTheSpeed() {
        val r = TempoRamp()
        assertNull(r.passEnded(Mode.WAIT, wait(1)))
        assertNull(r.passEnded(Mode.PERFORM, perform(89)))
        assertNull(r.passEnded(Mode.LISTEN, wait(0)))
        assertEquals(0.6, r.passEnded(Mode.PERFORM, perform(90))!!, 1e-9)
    }

    @Test fun transportReportsEachLoopPass() {
        val notes = parseLine("C4:1 D4:1 E4:1 F4:1", Hand.RIGHT, 500.0)
        val t = Transport(Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 2500)).apply { mode = Mode.WAIT }
        t.loop = 0.0..1000.0                       // C and D
        t.play()
        t.tick(5.0); t.noteInput(61); t.noteInput(60)  // a wrong key, then C
        t.tick(600.0); t.noteInput(62)
        t.tick(600.0)                              // reaches B: wraps
        assertEquals(1, t.passes)
        assertEquals(1, t.lastPass!!.wrongKeys)
        t.tick(5.0); t.noteInput(60); t.tick(600.0); t.noteInput(62); t.tick(600.0)
        assertEquals(2, t.passes)
        assertEquals("a clean second pass", 0, t.lastPass!!.wrongKeys)
    }

    @Test fun performLoopPassCountsOnlyTheLoopsGroups() {
        val notes = parseLine("C4:1 D4:1 E4:1 F4:1", Hand.RIGHT, 500.0)
        val t = Transport(Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 2500)).apply { mode = Mode.PERFORM }
        t.loop = 0.0..1000.0
        t.play()
        t.tick(1.0); t.noteInput(60)
        t.tick(500.0); t.noteInput(62)
        t.tick(600.0)
        val r = t.lastPass!!.recap!!
        assertEquals(2, r.good); assertEquals(0, r.miss); assertEquals(100, r.accuracy)
    }
}
