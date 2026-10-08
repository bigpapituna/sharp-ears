package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySessionTest {
    private val chords = (1..9).map { "chord:C$it" }

    @Test fun chordsThenLessonThenTheWorstBar() {
        val plan = planSession(chords, "rests" to "Rests", SessionSong("minuet-g", "Minuet in G", 2.0, troubleBar = 12))
        assertEquals(3, plan.parts.size)
        val c = plan.parts[0] as SessionPart.Chords
        assertEquals(MAX_CHORDS, c.itemIds.size)                                  // at most six chords
        assertEquals(chords.take(6), c.itemIds)                                   // oldest due first, as queued
        assertEquals("Next: the lesson Rests", plan.parts[1].next)
        assertEquals(SessionPart.Loop("minuet-g", "Minuet in G", 12), plan.parts[2])
        assertEquals(4.5 + 5.0 + 2.0, plan.minutes, 1e-9)
        assertEquals("about 10 min", plan.aboutLabel)
    }

    @Test fun withoutATroubleSpotTheSongFillsTheRest() {
        val plan = planSession(chords.take(2), null, SessionSong("twinkle", "Twinkle", 20.0, troubleBar = null))
        val song = plan.parts.last() as SessionPart.Song
        assertEquals(15.0 - 1.5, song.fill, 1e-9)                                 // up to the 15 minute target
        assertEquals("about 15 min", plan.aboutLabel)
        val short = planSession(emptyList(), "rests" to "Rests", SessionSong("t", "T", 0.5, null))
        assertEquals(1.0, short.parts.last().minutes, 1e-9)                      // never longer than the song
    }

    @Test fun nothingDueStillPlansTheSong() {
        val plan = planSession(emptyList(), null, SessionSong("t", "Twinkle", 0.6, null))
        assertEquals(listOf("Next: Twinkle"), plan.parts.map { it.next })
        assertEquals("about 5 min", plan.aboutLabel)
        assertTrue(planSession(emptyList(), null, null).isEmpty)
    }

    @Test fun summaryNamesWhatWasDone() {
        val parts = planSession(chords.take(1), "rests" to "Rests", SessionSong("m", "Minuet in G", 2.0, 12)).parts
        assertEquals("Done today: 1 chord, the lesson Rests and bar 12 of Minuet in G. 14 min.", sessionSummary(parts, 14 * 60L + 5))
        assertEquals("Done today: 1 chord. 1 min.", sessionSummary(parts.take(1), 10))
    }
}
