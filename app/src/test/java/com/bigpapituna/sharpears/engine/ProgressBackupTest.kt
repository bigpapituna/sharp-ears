package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProgressBackupTest {
    private val files: Map<String, Map<String, Any?>> = mapOf(
        "progress" to mapOf("best:twinkle" to 92, "pos:fur-elise" to 41_250L, "voice" to true, "lastSong" to "minuet-g",
            "anchor:twinkle:OBdYoGZ1Xio" to 7.50422f, "daily:2026-10-08" to "Done today: 6 chords. 12 min.", "auto:twinkle" to "61,2026-10-08"),
        "mastery" to mapOf("chord:Am7" to "3,1791504000000", "prog:0:7" to "1,1791000000000"),
        "mic" to mapOf("sensitivityDb" to -3.5f, "wrongOnsetDb" to 12f, "device" to 7, "tags" to setOf("b", "a")),
    )

    @Test fun everyTypeComesBackAsItWent() {
        val back = ProgressBackup.decode(ProgressBackup.encode(files))
        assertEquals(files, back)
        assertTrue(back.getValue("progress")["best:twinkle"] is Int)
        assertTrue(back.getValue("progress")["pos:fur-elise"] is Long)
        assertTrue(back.getValue("mic")["sensitivityDb"] is Float)
    }

    @Test fun theFileSaysWhatItIs() {
        val json = ProgressBackup.encode(mapOf("progress" to mapOf("voice" to false)))
        assertEquals("{\"app\":\"sharp-ears\",\"version\":1,\"files\":{\"progress\":{\"voice\":{\"bool\":false}}}}", json)
    }

    @Test fun otherFilesAreRefusedWithAReason() {
        fun refused(text: String, why: String) {
            try { ProgressBackup.decode(text); fail("accepted: $text") } catch (e: ProgressBackup.BadBackup) { assertEquals(why, e.message) }
        }
        refused("MThd not json", "This is not a JSON file.")
        refused("{\"songs\": []}", "This file was not exported by Sharp Ears.")
        refused("{\"app\": \"sharp-ears\", \"version\": 9, \"files\": {}}", "This progress file is from a newer version of the app.")
    }
}
