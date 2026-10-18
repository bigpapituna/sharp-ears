package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.MicEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongKeysTest {
    @Test fun nearestExpectedNoteAndDistance() {
        assertEquals(60, WrongKeys.nearest(62, setOf(60, 67)))
        assertEquals(67, WrongKeys.nearest(65, setOf(60, 67)))
        assertEquals(2, WrongKeys.distance(62, setOf(60, 67)))
        assertEquals(12, WrongKeys.distance(81, listOf(69)))
        assertNull(WrongKeys.nearest(60, emptySet()))
        assertEquals("a tie goes to the lower note", 60, WrongKeys.nearest(62, setOf(64, 60)))
    }

    @Test fun neighboursWithinTwoSemitonesAreSlipsFurtherAreMisses() {
        assertTrue(WrongKeys.isSlip(1))
        assertTrue(WrongKeys.isSlip(2))
        assertFalse(WrongKeys.isSlip(3))
        assertFalse(WrongKeys.isSlip(12))
        assertFalse("the note itself is not a slip", WrongKeys.isSlip(0))
        assertTrue(MicEvent.Wrong(0, midi = 62, nearest = 60, distance = 2, riseDb = -30.0).slip)
        assertFalse(MicEvent.Wrong(0, midi = 67, nearest = 60, distance = 7, riseDb = -30.0).slip)
    }

    @Test fun aTouchOrMidiWrongKeyIsClassifiedTheSameWay() {
        val slip = WrongKey.of(63, listOf(64, 67), id = 1)
        assertEquals(64, slip.nearest); assertEquals(1, slip.distance); assertTrue(slip.slip)
        val far = WrongKey.of(55, listOf(64, 67), id = 2)
        assertEquals(64, far.nearest); assertEquals(9, far.distance); assertFalse(far.slip)
        val none = WrongKey.of(60, emptyList(), id = 3)
        assertNull(none.nearest); assertFalse(none.slip)
    }
}
