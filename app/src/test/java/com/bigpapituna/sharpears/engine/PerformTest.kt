package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformTest {
    // Four right-hand notes a second apart, then a two-hand chord at 4000. Song ends at 5000.
    private val notes = listOf(
        Note(60, 0, 500, Hand.RIGHT, 90), Note(62, 1000, 500, Hand.RIGHT, 90),
        Note(64, 2000, 500, Hand.RIGHT, 90), Note(65, 3000, 500, Hand.RIGHT, 90),
        Note(48, 4000, 900, Hand.LEFT, 90), Note(67, 4000, 900, Hand.RIGHT, 90),
    )
    private val song = Song("p", "p", 60.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 5000)

    private fun Transport.runTo(ms: Double) { while (positionMs < ms - 1e-9 && playing) tick(minOf(16.0, ms - positionMs)) }

    @Test fun gradesByTimingAndCountsStreakBiasAndMisses() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        t.runTo(50.0);   assertEquals(InputResult.CORRECT, t.noteInput(60))           // +50 ms: good
        t.runTo(1220.0); assertEquals(InputResult.CORRECT, t.noteInput(62))           // +220 ms: ok
        t.runTo(1900.0); assertEquals(InputResult.WRONG, t.noteInput(70))             // not in any open group
        assertEquals(InputResult.CORRECT, t.noteInput(64))                            // -100 ms: good (early)
        // 65 at 3000 is never played: a miss once its window closes.
        t.runTo(4100.0); assertEquals(InputResult.CORRECT, t.noteInput(48))           // the chord counts on its first key
        assertEquals(InputResult.CORRECT, t.noteInput(67))                            // the other hand: part of the graded chord
        assertEquals(InputResult.WRONG, t.noteInput(50))                              // not in the chord
        t.runTo(5000.0)
        val r = t.score!!.recap()
        assertEquals(Recap(good = 3, ok = 1, miss = 1, biasMs = 68 /* (50 + 220 - 100 + 100) / 4 */,longestStreak = 3), r)
        assertEquals(80, r.accuracy)
    }

    // QA 2026-11-02 B-01: a perfect two-hand run (every chord's keys a few ms apart) has no wrong keys and no misses.
    @Test fun chordAndTwoHandKeysAfterTheFirstAreNeverWrong() {
        val chords = listOf(0L, 1000L, 2000L).flatMap { at ->
            listOf(Note(48, at, 500, Hand.LEFT, 80), Note(60, at, 500, Hand.RIGHT, 80), Note(64, at, 500, Hand.RIGHT, 80), Note(67, at, 500, Hand.RIGHT, 80))
        }
        val s = Song("c", "c", 60.0, 4 to 4, chords, groupOnsets(chords), emptyList(), 3000)
        val t = Transport(s).apply { mode = Mode.PERFORM; play() }
        for (at in listOf(0.0, 1000.0, 2000.0)) {
            t.runTo(at + 10.0)
            for ((i, k) in listOf(48, 60, 64, 67).withIndex()) {
                if (i > 0) t.runTo(at + 10.0 + i * 8)
                assertEquals("key $k of the chord at $at", InputResult.CORRECT, t.noteInput(k))
            }
        }
        t.runTo(3000.0)
        assertEquals(Recap(good = 3, ok = 0, miss = 0, biasMs = 10, longestStreak = 3), t.score!!.recap())
        assertTrue("no wrong keys: ${t.report()}", t.report().missed.isEmpty())
    }

    @Test fun neverWaitsAndDoesNotPlayRequiredNotes() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        val events = mutableListOf<NoteEvent>()
        while (t.playing) events += t.tick(16.0)
        assertEquals(5000.0, t.positionMs, 0.0)
        assertTrue("required notes are the player's: $events", events.none { it is NoteEvent.On })
        assertEquals(Recap(0, 0, 5, 0, 0), t.score!!.recap())
    }

    @Test fun micLatencyMovesTheStrikeBack() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        t.runTo(1250.0)
        // Heard 120 ms after it was played: counts as 1130, which is good, not 1250 (ok).
        t.noteInput(62, latencyMs = 120.0)
        t.runTo(5000.0)
        assertEquals(1, t.score!!.recap().good)
    }

    @Test fun listensOnlyInsideTheWindowAndASeekStartsANewRun() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        t.runTo(800.0)
        assertEquals(setOf(62), t.listenFor)           // 60's window closed at 300; 62 opens at 700
        t.seek(3500.0)
        assertEquals(3500.0, t.scoreFromMs, 0.0)
        t.runTo(5000.0)
        assertEquals(1, t.score!!.recap().total)        // only the chord at 4000 belongs to the new run
    }
}
