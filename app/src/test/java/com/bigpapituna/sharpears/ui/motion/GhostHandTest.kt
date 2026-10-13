package com.bigpapituna.sharpears.ui.motion

import com.bigpapituna.sharpears.engine.Hand
import org.junit.Assert.assertEquals
import org.junit.Test

class GhostHandTest {
    private fun n(start: Long, dur: Long = 500, finger: Int? = 1) = GhostNote(60, Hand.RIGHT, finger, start, dur)

    @Test fun thumbsSitOnTheOutsideOfEachHand() {
        assertEquals(-2f, fingerOffset(1, Hand.RIGHT))
        assertEquals(2f, fingerOffset(5, Hand.RIGHT))
        assertEquals(2f, fingerOffset(1, Hand.LEFT))
        assertEquals(0f, fingerOffset(3, Hand.LEFT), 0f)
    }

    @Test fun nextIndexSkipsNotesAlreadyLiftedButKeepsHeldOnes() {
        val notes = listOf(n(0), n(1000), n(2000), n(2000, finger = 3), n(4000))
        assertEquals(0, nextIndex(notes, -500.0))
        assertEquals(0, nextIndex(notes, 200.0))          // still held (hold is capped at HOLD_MS)
        assertEquals(1, nextIndex(notes, 600.0))          // the first one has lifted
        assertEquals(2, nextIndex(notes, 1999.0))         // a chord: its first note
        assertEquals(4, nextIndex(notes, 2500.0))
        assertEquals(-1, nextIndex(notes, 5000.0))
        assertEquals(-1, nextIndex(emptyList(), 0.0))
    }

    @Test fun dipGoesDownBeforeTheLineHoldsThenLifts() {
        val note = n(1000, dur = 1000)
        assertEquals(0f, dipFor(note, 1000 - LEAD_MS - 1))
        assertEquals(0.25f, dipFor(note, 1000 - LEAD_MS / 2), 1e-4f)
        assertEquals(1f, dipFor(note, 1000.0))
        assertEquals(1f, dipFor(note, 1000 + HOLD_MS - 1))
        assertEquals(0.5f, dipFor(note, 1000 + HOLD_MS + RELEASE_MS / 2), 1e-4f)
        assertEquals(0f, dipFor(note, 1000 + HOLD_MS + RELEASE_MS + 1))
    }
}
