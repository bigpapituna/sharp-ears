package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StallWatchTest {
    @Test fun shortWaitsAreNotStalls() {
        val w = StallWatch()
        w.start(0, listOf("C3", "C6"), "song=alicia")
        assertNull(w.end(4_999, "played"))
    }

    @Test fun aLongWaitIsReportedWithWhatWasHeard() {
        val w = StallWatch()
        w.start(1_000, listOf("C3", "C6"), "song=alicia position_ms=2182")
        w.heard(1_950, "C3 rise -24.8 dB")
        val s = w.end(29_000, "left")!!
        assertEquals(28_000, s.waitedMs)
        assertTrue(s.report, "expected=C3,C6" in s.report && "outcome=left" in s.report && "+0.95 s  C3 rise -24.8 dB" in s.report)
        assertNull("reported once", w.end(30_000, "left"))
    }

    @Test fun heardOutsideAWaitIsIgnored() {
        val w = StallWatch()
        w.heard(0, "C3")
        w.start(100, listOf("D4"), "")
        assertTrue("nothing" in w.end(6_000, "played")!!.report)
    }
}
