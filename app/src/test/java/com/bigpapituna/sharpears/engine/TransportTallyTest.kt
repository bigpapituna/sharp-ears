package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.brief.HandNotes
import com.bigpapituna.sharpears.engine.brief.KeyMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport feeds the brief's tally the notes itself (the email brief's extra data): each wait group as it comes due and
 * its keys as they are heard (hesitation, each hand's clean notes), notes let go or left out, and each Perform group played
 * or missed (the hands, the skipped notes, the tempo's spread).
 */
class TransportTallyTest {
    // C4 at 0, E4 at 500, then a chord at 1000: C3 (left) with G4 (right). The song ends at 2000.
    private val notes = listOf(
        Note(60, 0, 500, Hand.RIGHT, 90),
        Note(64, 500, 500, Hand.RIGHT, 90),
        Note(48, 1000, 1000, Hand.LEFT, 70),
        Note(67, 1000, 1000, Hand.RIGHT, 90),
    )
    private val song = Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 2000)

    private fun Transport.run(ms: Double, frame: Double = 10.0) {
        var left = ms
        while (left > 1e-9) { val dt = minOf(frame, left); tick(dt); left -= dt }
    }

    @Test fun aWaitModeRunTimesEachKeyFromItsGroupComingDue() {
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        t.run(20.0)                                  // C4 is due at once
        assertTrue(t.waiting)
        t.run(700.0)                                 // ...and found 700 ms later
        t.noteInput(60)
        t.run(500.0)                                 // E4 due at 500: a wrong key first, then E4 after 1200 ms in all
        t.run(600.0)
        t.tally.wrong(65, 64)                        // the player charges a slip to E4 (PlayerScreen.showWrongKey)
        t.run(600.0)
        t.noteInput(64)
        t.run(500.0)                                 // the chord comes due at 1000
        t.run(600.0)                                 // G4 heard by the mic 600 ms on, struck 100 ms before that
        t.noteInput(67, latencyMs = 100.0)
        t.run(1500.0)                                // the left hand is slow
        t.noteInput(48)
        val d = t.tally.detail()
        assertEquals(4, t.tally.notesDue)
        assertEquals(0, d.skippedNotes)
        assertEquals(HandNotes(due = 1, clean = 1, hesMs = 2100, hesN = 1), d.left)
        val right = d.right
        assertEquals(3, right.due); assertEquals(2, right.clean)
        assertEquals(KeyMs(48, 2100), d.slowKeys.first())
        assertEquals(listOf(48, 64, 60, 67), d.slowKeys.map { it.midi })
        assertEquals(500.0, d.slowKeys.first { it.midi == 67 }.ms.toDouble(), 20.0)  // 600 ms after due, less the mic's 100 ms
    }

    @Test fun aKeyStruckJustBeforeItsGroupWaitedForNothing() {
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        t.run(20.0); t.noteInput(60)
        t.run(400.0)                                 // 80 ms before E4
        t.noteInput(64)
        assertEquals(0, t.tally.detail().slowKeys.first { it.midi == 64 }.ms)
    }

    @Test fun aSeekAbandonsTheGroupBeingTimed() {
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        t.run(20.0)
        t.seek(900.0)                                // C4 was due and never played: not a skipped note, just abandoned
        t.play()
        t.run(200.0)
        t.noteInput(67); t.noteInput(48)
        assertEquals(2, t.tally.notesDue)
        assertEquals(0, t.tally.skipped)
    }

    @Test fun aPerformRunCountsGroupsPlayedAndMissedAndTheSpreadOfTheirOffsets() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        t.run(40.0); t.noteInput(60)                 // C4 40 ms late
        t.run(420.0); t.noteInput(64)                // E4 40 ms early
        t.run(1700.0)                                // the chord goes by: a miss for both hands
        assertTrue(!t.playing)
        val d = t.tally.detail()
        assertEquals(2, d.skippedNotes)
        assertEquals(HandNotes(due = 3, clean = 2), d.right)
        assertEquals(HandNotes(due = 1, clean = 0), d.left)
        assertEquals("two graded notes are too few for a spread", null, d.spreadMs)
        val longer = Transport(Song("u", "u", 120.0, 4 to 4, (0 until 8).map { Note(60 + it % 3, it * 500L, 400, Hand.RIGHT, 80) },
            groupOnsets((0 until 8).map { Note(60 + it % 3, it * 500L, 400, Hand.RIGHT, 80) }), emptyList(), 4000)).apply { mode = Mode.PERFORM; play() }
        var at = 0.0
        for (i in 0 until 8) {
            val target = i * 500.0 + (if (i % 2 == 0) 30.0 else -30.0)
            longer.run(target - at, frame = 5.0); at = target
            longer.noteInput(60 + i % 3)
        }
        assertNotNull(longer.tally.spreadMs)
        assertEquals(30.0, longer.tally.spreadMs!!.toDouble(), 3.0)
    }
}
