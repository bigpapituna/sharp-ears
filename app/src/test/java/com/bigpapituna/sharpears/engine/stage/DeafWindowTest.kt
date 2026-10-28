package com.bigpapituna.sharpears.engine.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeafWindowTest {
    private var now = 1_000L
    private val w = DeafWindow { now }

    @Test fun timedWindowsKeepTheLonger() {
        w.deafFor(500); w.deafFor(200)
        now += 499; assertTrue(w.deaf())
        now += 2; assertFalse(w.deaf())
        assertTrue("decides late", w.deafWithin(400))
        now += 400; assertFalse(w.deafWithin(400))
    }

    @Test fun heldWindowsEndOnCloseWithTheirTail() {
        w.open("a", 60_000); w.open("b", 60_000)
        now += 20_000
        assertTrue(w.close("a", 300))
        assertTrue("b still holds", w.deaf())
        assertTrue(w.close("b", 0))
        assertTrue("a's tail", w.deaf())
        now += 301; assertFalse(w.deaf())
        assertFalse("closed twice", w.close("a", 300))
        assertFalse(w.deaf())
    }

    @Test fun aLostCloseEndsAtTheLimitAndCloseAllEndsEverything() {
        w.open("lost", 1_000)
        assertEquals(Long.MAX_VALUE, w.hearsAgainAt())
        now += 1_001; assertFalse(w.deaf()); assertFalse(w.holds("lost"))
        w.open("x", 60_000); w.open("y", 60_000)
        w.closeAll()
        assertFalse(w.deaf())
        w.open("z", 60_000); w.closeAll(tailMs = 300)
        assertTrue(w.deaf()); now += 301; assertFalse(w.deaf())
    }
}
