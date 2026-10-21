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

    @Test fun reportsCarryTheProfileKindNeverTheName() {
        val w = StallWatch()
        w.profile = StallWatch.profileKind(kid = true)
        w.start(0, listOf("E4"), "song=lesson-together-3")
        val r = w.end(6_000, "left")!!.report
        assertEquals("# sharp ears stuck moment", r.lines()[0])
        assertEquals("profile=kid", r.lines()[1])
        assertEquals("adult", StallWatch.profileKind(kid = false))
    }

    @Test fun anyReportGetsTheProfileTagOnce() {
        val flag = "title=Flag: Twinkle at 0:07: It missed a note I played\n# sharp ears flag (\"that wasn't right\")\nsong=twinkle\n"
        val tagged = StallWatch.tagProfile(flag, "adult")
        assertEquals(listOf("title=Flag: Twinkle at 0:07: It missed a note I played", "# sharp ears flag (\"that wasn't right\")",
            "profile=adult", "song=twinkle", ""), tagged.lines())
        assertEquals("already tagged", tagged, StallWatch.tagProfile(tagged, "kid"))
        assertEquals("profile=kid\nno header\n", StallWatch.tagProfile("no header\n", "kid"))
    }

    @Test fun waitedMsCountsOnlyDuringAWait() {
        val w = StallWatch()
        assertEquals(0L, w.waitedMs(5_000))
        w.start(1_000, listOf("C4"), "")
        assertEquals(4_000L, w.waitedMs(5_000))
        w.end(5_000, "played")
        assertEquals(0L, w.waitedMs(6_000))
    }

    @Test fun heardOutsideAWaitIsIgnored() {
        val w = StallWatch()
        w.heard(0, "C3")
        w.start(100, listOf("D4"), "")
        assertTrue("nothing" in w.end(6_000, "played")!!.report)
    }

    /**
     * Audit: the stalls folder dropped files by name past its 250 MB, so every answer-key clip and crash report (even
     * today's) went before months-old stuck moments, and a report's ".sent" marker went before the report, which was
     * then sent again.
     */
    @Test fun theStallsFolderDropsTheOldestFirstWhateverItsKind() {
        val names = listOf("answer-20261020-101500-twinkle.txt", "stall-20261001-090000-twinkle.sent", "crash-20261019-080000.txt",
            "stall-20261001-090000-twinkle.wav", "flag-20261018-120000-ode.wav", "stall-20261001-090000-twinkle.txt", "summary-20261002-070000.txt")
        assertEquals(listOf("stall-20261001-090000-twinkle.txt", "stall-20261001-090000-twinkle.wav", "stall-20261001-090000-twinkle.sent",
            "summary-20261002-070000.txt", "flag-20261018-120000-ode.wav", "crash-20261019-080000.txt", "answer-20261020-101500-twinkle.txt"),
            names.sortedWith(StallWatch.OLDEST_FIRST))
    }

    /**
     * Audit: a crash report carried the app's last 200 log lines, where the voice logs every line it says ("voice:
     * Lesson done, Mia!") and the finale and photo booth log file names with the kid's name; it went to GitHub as is.
     */
    @Test fun crashReportsNameNoOne() {
        val log = "voice: Lesson done, Mia! Five stars, mia.\nfinale: garden for Zoë Rose\nsaved Zoë Rose finished the garden.png\n" +
            "voice: Amazing, Al! All done. Alicia is fine.\nroster: Mia,Zoë Rose"
        val clean = StallWatch.scrubNames(log, listOf("Mia", "Zoë Rose", "Al", ""))
        for (name in listOf("Mia", "mia", "Zoë")) assertTrue("$name in $clean", name !in clean)
        assertTrue(clean, "Amazing, <name>! All done. Alicia is fine." in clean)    // whole words only
        assertEquals("voice: <name> and <name>.", StallWatch.scrubNames("voice: Mia and Zoë Rose.", listOf("Mia", "Zoë Rose")))
    }
}
