package com.bigpapituna.sharpears.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.10.1 (security review 2026-10-22, finding 5): the recordings move from external app storage, which other apps can read
 * on Android 9 and 10, to files/recordings. The move keeps every file's name and time, never overwrites one already moved,
 * and can run again after a kill.
 */
class RecordingsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String, time: Long) = File(dir.apply { mkdirs() }, name).apply { writeText(text); setLastModified(time) }

    @Test fun everyFileMovesWithItsNameAndTimeAndTheOldFolderGoes() {
        val old = tmp.newFolder("external", "fixtures")
        file(old, "session-101500.wav", "riff", 1_700_000_000_000)
        file(old, "session-101500.notes", "# truth", 1_700_000_001_000)
        val to = File(tmp.root, "files/recordings/fixtures")
        assertEquals(2, Recordings.move(old, to))
        assertEquals(listOf("session-101500.notes", "session-101500.wav"), to.list()!!.sorted())
        assertEquals("riff", File(to, "session-101500.wav").readText())
        assertEquals(1_700_000_000_000, File(to, "session-101500.wav").lastModified())
        assertFalse("the emptied external folder is removed", old.exists())
    }

    @Test fun aFileAlreadyMovedStaysAndASecondRunMovesNothing() {
        val old = tmp.newFolder("external", "stalls")
        val to = File(tmp.root, "files/recordings/stalls")
        file(to, "stall-1.wav", "new", 2_000)
        file(old, "stall-1.wav", "old", 1_000)
        file(old, "stall-1.txt", "report", 1_000)
        assertEquals(1, Recordings.move(old, to))
        assertEquals("the copy already there is kept", "new", File(to, "stall-1.wav").readText())
        assertEquals("report", File(to, "stall-1.txt").readText())
        assertEquals(0, Recordings.move(old, to))
        assertEquals(0, Recordings.move(null, to))
        assertEquals(0, Recordings.move(File(tmp.root, "never-made"), to))
        assertFalse("no temporary file is left", to.list()!!.any { it.endsWith(".part") })
    }

    @Test fun aFolderInsideIsLeftAlone() {
        val old = tmp.newFolder("external", "fixtures")
        file(old, "a.wav", "a", 1_000)
        File(old, "inner").mkdirs()
        assertEquals(1, Recordings.move(old, File(tmp.root, "to")))
        assertTrue("not empty: kept", old.isDirectory)
    }

    @Test fun thePageSaysPlainlyWhatTheTestBuildKeeps() {
        assertEquals("This test build keeps the last four practice recordings on this phone, in the app's own storage, to tune the " +
            "listening. They never leave the phone unless you share them. Turn this off in Settings.", Recordings.KEEP_LINE)
        assertEquals("Keep the last four recordings", Recordings.KEEP_SWITCH)
        for (s in listOf(Recordings.KEEP_LINE, Recordings.KEEP_SWITCH)) assertFalse(s, s.contains(Char(0x2014)) || s.contains(Char(0x2013)) || s.contains(" - "))
    }
}
