package com.bigpapituna.sharpears.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class CountUpTest {
    @Test fun timeCountsInSeconds() {
        assertEquals("0:00", countUp("3:12", 0f))
        assertEquals("1:36", countUp("3:12", 0.5f))
        assertEquals("3:12", countUp("3:12", 1f))
    }

    @Test fun firstNumberCountsAndTheRestStays() {
        assertEquals("0%", countUp("95%", 0f))
        assertEquals("47%", countUp("95%", 0.5f))
        assertEquals("+60 XP", countUp("+120 XP", 0.5f))
        assertEquals("4/10", countUp("8/10", 0.5f))
        assertEquals("12 min", countUp("12 min", 1f))
    }

    @Test fun decimalsAndPaddingAreKept() {
        assertEquals("0.7 s", countUp("1.5 s", 0.5f))
        assertEquals("03", countUp("07", 0.5f))
    }

    @Test fun textWithoutNumbersIsUnchanged() {
        assertEquals("Clean", countUp("Clean", 0.3f))
    }

    @Test fun easeOutEndsAtOne() {
        assertEquals(0f, easeOutCubic(0f), 1e-6f)
        assertEquals(1f, easeOutCubic(1f), 1e-6f)
        assertEquals(0.875f, easeOutCubic(0.5f), 1e-6f)
    }
}
