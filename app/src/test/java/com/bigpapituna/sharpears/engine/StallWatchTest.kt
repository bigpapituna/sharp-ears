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

    @Test fun crashLogsLoseThePlayersNames() {
        val log = "voice: Well done, Mia! Leo next.\nshare: Mia plays Twinkle.png\nMiami and Leopard stay\nvoice: Привет, Аня"
        assertEquals("voice: Well done, <name>! <name> next.\nshare: <name> plays Twinkle.png\nMiami and Leopard stay\nvoice: Привет, <name>",
            StallWatch.withoutNames(log, listOf("Mia", "leo", "Аня", "")))
    }

    @Test fun overBudgetDeletesWholeReportsOldestWrittenFirst() {
        fun f(name: String, bytes: Long, at: Long) = StallWatch.SavedFile(name, bytes, at)
        val files = listOf(
            f("stall-20261001-0900.wav", 100, 1), f("stall-20261001-0900.txt", 1, 1), f("stall-20261001-0900.sent", 1, 50),
            f("answer-20261020-1000.wav", 100, 20), f("answer-20261020-1000.txt", 1, 20),     // newer than the stall: kept
            f("crash-20261020-1100.txt", 1, 30),                                               // not sent yet: kept
            f("stall-20261020-1200.wav", 100, 40), f("stall-20261020-1200.txt", 1, 40),       // just saved
        )
        assertEquals(listOf("stall-20261001-0900.wav", "stall-20261001-0900.txt", "stall-20261001-0900.sent"),
            StallWatch.overBudget(files, budget = 250))
        assertEquals(emptyList<String>(), StallWatch.overBudget(files, budget = 400))
        assertEquals("the newest report stays even alone over budget", listOf("stall-20261020-1200.wav", "stall-20261020-1200.txt"),
            files.map { it.name } - StallWatch.overBudget(files, budget = 10).toSet())
    }
}
