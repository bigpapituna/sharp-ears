package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundClockTest {
    @Test fun timeOffScreenDoesNotCount() {
        var t = 1_000L
        val c = ForegroundClock { t }
        val opened = c.nowMs()
        t += 60_000                       // a minute of chords
        c.stop(); t += 8 * 3_600_000      // the phone locked overnight
        assertEquals("stands still while stopped", 60_000L, c.nowMs() - opened)
        c.start(); c.start()              // a second start changes nothing
        t += 30_000
        assertEquals(90_000L, c.nowMs() - opened)
        c.stop(); t += 1_000; c.stop(); t += 1_000; c.start()   // a second stop does not reset the pause
        assertEquals(90_000L, c.nowMs() - opened)
    }
}
