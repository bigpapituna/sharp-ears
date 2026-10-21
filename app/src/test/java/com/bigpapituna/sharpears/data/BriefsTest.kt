package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The one door into the logs: who an event goes to, the session clock, and a deleted profile's log. */
class BriefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private var practice = 0L
    private val lines = ArrayList<String>()

    @Before fun start() {
        Briefs.reset()
        Briefs.init(FolderContext(tmp.root))
        Briefs.logcat = { lines += it }
        Briefs.clock = { 1_000L }
        Briefs.practiceSeconds = { practice }
    }

    @After fun stop() = Briefs.reset()

    @Test fun eventsGoToWhoeverIsPlayingOrToTheProfileNamed() {
        Briefs.log { BriefEvent.QuestStar(it, "FIND_CS") }                       // the picker: no one yet
        Briefs.currentId = "p1"
        Briefs.log { BriefEvent.QuestStar(it, "COPY_PUP") }
        Briefs.logFor("p2", BriefEvent.FinalePlayed(5, "pond"))
        Briefs.setting("reader", true)
        assertEquals(listOf(BriefEvent.QuestStar(1_000, "COPY_PUP"), BriefEvent.SettingChanged(1_000, "reader", "true")), Briefs.read("p1"))
        assertEquals(listOf(BriefEvent.FinalePlayed(5, "pond")), Briefs.read("p2"))
        assertTrue(Briefs.read("owner").isEmpty())
        assertEquals("brief: p1 {\"t\":1000,\"e\":\"quest\",\"task\":\"COPY_PUP\"}", lines.first())
    }

    @Test fun theEmailJobIsToldWhenASessionEndsAndNeverBreaksIt() {
        val told = ArrayList<String>()
        Briefs.onSessionEnd = { told += it }
        Briefs.currentId = "p1"
        Briefs.sessionEnd()                                        // nothing open: nobody is told
        Briefs.sessionStart("p1")
        Briefs.sessionStart("p2")                                  // someone else takes over: p1's ends
        Briefs.sessionEnd()
        assertEquals(listOf("p1", "p2"), told)
        Briefs.onSessionEnd = { error("a broken hook") }
        Briefs.sessionStart("p1"); Briefs.sessionEnd()             // logged all the same
        assertTrue(lines.any { it.contains("session end hook failed") })
    }

    @Test fun aSessionCarriesItsPracticeAndEndsWhenSomeoneElseTakesOver() {
        Briefs.currentId = "p1"
        Briefs.sessionStart("p1")
        Briefs.sessionStart("p1")                                  // already open: nothing new
        practice += 150
        assertEquals(150, Briefs.sessionSoFar("p1")!!.practiceSec)
        assertNull(Briefs.sessionSoFar("p2"))
        // Stamped at the findings' "now" when asked: Insights leaves out anything later, and the practice with it.
        val asOf = Briefs.sessionSoFar("p1", at = 900)!!
        assertEquals(900L, asOf.at)
        assertEquals(150, asOf.practiceSec)
        Briefs.sessionStart("p2")                                  // a switch: p1's ends first
        practice += 30
        Briefs.sessionEnd(); Briefs.sessionEnd()
        val p1 = Briefs.read("p1")
        assertTrue(p1[0] is BriefEvent.SessionStarted)
        assertEquals(150, (p1[1] as BriefEvent.SessionEnded).practiceSec)
        assertEquals(2, p1.size)
        assertEquals(30, (Briefs.read("p2")[1] as BriefEvent.SessionEnded).practiceSec)
    }

    @Test fun aDeletedProfilesLogIsGoneAndItsSessionWithIt() {
        Briefs.currentId = "p1"
        Briefs.sessionStart("p1")
        Briefs.log { BriefEvent.BedtimeShown(it) }
        Briefs.flushAll()
        assertTrue(EventLog.fileFor(tmp.root, "p1").exists())
        Briefs.forget(FolderContext(tmp.root), "p1")
        assertFalse(EventLog.fileFor(tmp.root, "p1").exists())
        Briefs.sessionEnd()                                        // nothing left to end: no file comes back
        Briefs.flushAll()
        assertFalse(EventLog.fileFor(tmp.root, "p1").exists())
    }

    @Test fun aBackupRoundTripThroughTheOpenLogs() {
        Briefs.currentId = "owner"
        Briefs.log { BriefEvent.GoalReached(it, 10) }
        val logs = Briefs.exportLogs(FolderContext(tmp.root), listOf("owner", "p1"))
        assertEquals(mapOf("owner" to listOf("{\"t\":1000,\"e\":\"goal\",\"goal\":10}")), logs)
        Briefs.log { BriefEvent.GoalReached(it, 12) }               // after the backup: the import takes it away
        Briefs.restoreLogs(FolderContext(tmp.root), logs, listOf("owner"), allProfiles = true)
        assertEquals(listOf(BriefEvent.GoalReached(1_000, 10)), Briefs.read("owner"))
    }
}
