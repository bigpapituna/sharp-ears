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

    /**
     * Audit: a backup opened and saved in a text editor gains a byte-order mark and was refused as "not a JSON file",
     * and MiniJson read the standard escapes \b and \f as the letters b and f.
     */
    @Test fun aBackupSavedByAnEditorStillImports() {
        assertEquals(files, ProgressBackup.decode("\uFEFF" + ProgressBackup.encode(files)))
        assertEquals("a\bb\u000cc\"d\\e/f", MiniJson.read("\"a\\bb\\fc\\\"d\\\\e\\/f\""))
        val names = mapOf("name" to "Zoë 🎹", "tab" to "a\tb", "ctrl" to "x\u0001y")
        assertEquals(names, MiniJson.read(MiniJson.write(names)))
    }

    /**
     * The parent brief: the event logs ride in the same file under "events", each profile's lines as written. Version 1
     * still: an older app reads the files and ignores the logs; a file from before the brief reads back with none.
     */
    @Test fun theEventLogsTravelBesideTheFilesAndOlderFilesHaveNone() {
        val events = mapOf("p1" to listOf("{\"t\":5,\"e\":\"start\"}"), "owner" to listOf("{\"t\":6,\"e\":\"end\",\"ms\":60000}", "{\"t\":7,\"e\":\"x\"}"))
        val text = ProgressBackup.encode(files, events)
        val back = ProgressBackup.decodeBackup(text)
        assertEquals(files, back.files)
        assertEquals(events, back.events)
        assertEquals("what an older app reads", files, ProgressBackup.decode(text))
        assertEquals(null, ProgressBackup.decodeBackup(ProgressBackup.encode(files)).events)
        assertEquals(emptyMap<String, List<String>>(), ProgressBackup.decodeBackup(ProgressBackup.encode(files, emptyMap())).events)
    }
}
