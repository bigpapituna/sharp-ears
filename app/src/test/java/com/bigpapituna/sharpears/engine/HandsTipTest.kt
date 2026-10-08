package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandsTipTest {
    @Test fun rightHandFirstThenLeftThenTogether() {
        assertEquals("right", handsTip(true, emptySet(), playedThrough = false))
        assertEquals("right", handsTip(true, setOf(Practice.LEFT), false))
        assertEquals("left", handsTip(true, setOf(Practice.RIGHT), true))
        assertEquals("together", handsTip(true, setOf(Practice.RIGHT, Practice.LEFT), true))
    }

    @Test fun noTipOnceBothHandsPlayedOrForOneHandSongs() {
        assertNull(handsTip(true, setOf(Practice.BOTH), true))
        assertNull(handsTip(false, emptySet(), false))
        assertNull(handsTip(true, emptySet(), playedThrough = true))      // played through before hands were tracked
    }
}
