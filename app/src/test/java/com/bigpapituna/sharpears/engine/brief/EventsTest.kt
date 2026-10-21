package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The log's line format: every event comes back as it went, lines stay short, and reading forgives a newer app. */
class EventsTest {
    private val t = 1_792_000_000_000L

    /** One of every event, each with its fields away from their defaults. */
    private val every: List<BriefEvent> = listOf(
        BriefEvent.SessionStarted(t),
        BriefEvent.SessionEnded(t, 600_000, 540),
        BriefEvent.LessonStep(t, "pond-3", 2, "PLAY", tries = 3, letGo = true, hintsUsed = listOf(1, 3), durationMs = 61_000, game = "ECHO",
            hands = Hands.RIGHT, skipped = false, stars = 1, accuracy = 72, score = 55, timingMeanMs = -40, notes = 8, wrongKeys = 5,
            nearMisses = 3, farMisses = 2, letGoRounds = 1, rounds = 2),
        BriefEvent.LessonDone(t, "pond-3", 2, 300_000, first = true),
        BriefEvent.SongRun(t, "twinkle", RunMode.PERFORM, Hands.BOTH, 88, 2, 35, 3, 1, 42, 4, listOf(2), 95_000, fromTop = false, duet = true),
        BriefEvent.WrongKey(t, 60, 62, true, 4, lessonId = "pond-3"),
        BriefEvent.OctaveHint(t, songId = "twinkle", count = 2),
        BriefEvent.NoisyRoom(t, "stage:KEY_HUNT"),
        BriefEvent.ToyOpened(t, ToyKind.PUP_SAYS, 240_000),
        BriefEvent.EarRound(t, "chord:build", correct = true, level = 1),
        BriefEvent.QuestStar(t, "FIND_CS"),
        BriefEvent.StickerDrop(t, StickerRarity.SHINY, "pond-10", isNew = true, source = "egg"),
        BriefEvent.GoalReached(t, 5),
        BriefEvent.BedtimeShown(t),
        BriefEvent.SettingChanged(t, "reader", "true"),
        BriefEvent.ParentAction(t, "SET_NEXT", mapOf("lesson" to "pond-3")),
        BriefEvent.FinalePlayed(t, "garden"),
    )

    @Test fun everyEventComesBackAsItWent() {
        assertEquals(17, every.map { it.name }.toSet().size)
        for (e in every) assertEquals(e.encode(), e, BriefEvent.decode(e.encode()))
    }

    @Test fun aLineIsOneLineWithTimeAndNameFirstAndNoDefaults() {
        assertEquals("{\"t\":$t,\"e\":\"start\"}", BriefEvent.SessionStarted(t).encode())
        assertEquals("{\"t\":$t,\"e\":\"step\",\"lesson\":\"sit\",\"step\":0,\"kind\":\"READ\"}", BriefEvent.LessonStep(t, "sit", 0, "READ").encode())
        assertEquals("{\"t\":$t,\"e\":\"toy\",\"toy\":\"clapAlong\",\"ms\":5000}", BriefEvent.ToyOpened(t, ToyKind.CLAP_ALONG, 5000).encode())
        // A value with a line break (a parent's note) stays on its line.
        val note = BriefEvent.ParentAction(t, "TEACHER_ASSIGN", mapOf("note" to "Slowly,\nthen faster"))
        assertFalse(note.encode().contains('\n'))
        assertEquals(note, BriefEvent.decode(note.encode()))
        assertTrue("a full step stays short", every[2].encode().length < 300)
    }

    @Test fun unknownFieldsAreIgnored() {
        assertEquals(BriefEvent.LessonDone(t, "sit", 3), BriefEvent.decode("{\"t\":$t,\"e\":\"lesson\",\"lesson\":\"sit\",\"stars\":3,\"mood\":\"happy\",\"v\":[1,2]}"))
    }

    @Test fun aNewerAppsEventIsKeptAsUnknownAndWrittenBackUnchanged() {
        val line = "{\"t\":$t,\"e\":\"hologram\",\"size\":3,\"tags\":[\"a\"]}"
        val e = BriefEvent.decode(line)
        assertEquals(BriefEvent.Unknown(t, "hologram", mapOf("size" to 3L, "tags" to listOf("a"))), e)
        assertEquals(line, e!!.encode())
        // A known name with a value this version cannot read (a new hand) is unknown too, not a crash or a wrong event.
        val odd = BriefEvent.decode("{\"t\":$t,\"e\":\"song\",\"song\":\"x\",\"mode\":\"WAIT\",\"hands\":\"CROSSED\",\"stars\":2}")
        assertTrue(odd is BriefEvent.Unknown)
        assertTrue(BriefEvent.decode("{\"t\":$t,\"e\":\"lesson\",\"stars\":3}") is BriefEvent.Unknown)   // no lesson id
    }

    @Test fun linesThatAreNotEventsReadAsNull() {
        for (bad in listOf("", "not json", "{\"t\":1", "[1,2]", "{\"e\":\"start\"}", "{\"t\":\"soon\",\"e\":\"start\"}", "{\"t\":5}"))
            assertNull(bad, BriefEvent.decode(bad))
    }

    @Test fun theBodyNeverCarriesAProfile() {
        for (e in every) {
            val keys = (MiniJson.read(e.encode()) as Map<*, *>).keys
            assertTrue(e.name, keys.none { it.toString().contains("profile", true) || it == "name" })
        }
    }
}
