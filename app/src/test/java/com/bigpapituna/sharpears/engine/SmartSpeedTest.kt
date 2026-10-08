package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SmartSpeedTest {
    private val day = LocalDate.of(2026, 10, 7)

    @Test fun theTempoManagedIsSongTimeOverRealTime() {
        assertEquals(72, AutoSpeed.effectivePercent(songMs = 36_000.0, realMs = 50_000.0))     // 72% of the song's notes per minute
        assertEquals(100, AutoSpeed.effectivePercent(30_000.0, 29_000.0))                       // never above full speed
        assertEquals(25, AutoSpeed.effectivePercent(5_000.0, 60_000.0))                         // nor below a quarter
        assertNull(AutoSpeed.effectivePercent(2_000.0, 3_000.0))                                // too short to say
    }

    @Test fun anAccurateRunNudgesUpOnceADay() {
        val a = AutoSpeed.afterRun(null, effective = 72, accuracy = 90, today = day)
        assertEquals(AutoSpeed(77, day), a)
        val b = AutoSpeed.afterRun(a, effective = 77, accuracy = 95, today = day)              // same day: no second nudge
        assertEquals(AutoSpeed(77, day), b)
        val c = AutoSpeed.afterRun(b, effective = 77, accuracy = 85, today = day.plusDays(1))   // next day, 85% counts
        assertEquals(AutoSpeed(82, day.plusDays(1)), c)
    }

    @Test fun aSloppyOrSlowRunSetsTheTempoItManaged() {
        val a = AutoSpeed(80, day)
        assertEquals(AutoSpeed(60, day), AutoSpeed.afterRun(a, effective = 60, accuracy = 70, today = day.plusDays(2)))
        assertEquals(AutoSpeed(100, day.plusDays(1)), AutoSpeed.afterRun(a, effective = 98, accuracy = 100, today = day.plusDays(1)))
    }

    @Test fun storedAsPercentAndDay() {
        assertEquals(AutoSpeed(77, day), AutoSpeed.decode(AutoSpeed(77, day).encode()))
        assertEquals(AutoSpeed(70, null), AutoSpeed.decode(AutoSpeed(70).encode()))
        assertNull(AutoSpeed.decode(null))
        assertEquals(90, AutoSpeed.waitAccuracy(groups = 45, wrongKeys = 5))
    }

    @Test fun theTransportMeasuresRealAndSongTimeAfterTheLeadIn() {
        val notes = (0 until 8).map { Note(60, it * 500L, 400, Hand.RIGHT, 80) }
        val s = Song("s", "s", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 4000)
        val t = Transport(s).apply { mode = Mode.WAIT; play(leadInMs = 1000.0) }
        repeat(70) { t.tick(16.0) }                                                       // 1.12 s: lead-in, then held at 0
        assertEquals(112.0, t.runRealMs, 1.0)                                              // the lead-in does not count, the hold does
        t.noteInput(60)
        repeat(10) { t.tick(16.0) }
        assertEquals(160.0, t.runSongMs, 1.0)
        assertEquals(272.0, t.runRealMs, 1.0)
    }
}
