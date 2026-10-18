package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Recap
import org.junit.Assert.assertEquals
import org.junit.Test

/** One to three stars from a recap's accuracy and timing, or a wait run's wrong keys. */
class StarsTest {
    private fun recap(good: Int, ok: Int, miss: Int, bias: Int = 0) = Recap(good, ok, miss, bias, longestStreak = good)

    @Test fun performThresholds() {
        assertEquals(1, Stars.perform(recap(0, 0, 10)))                 // reached the end: one star, never zero
        assertEquals(1, Stars.perform(recap(5, 0, 5)))                  // 50%
        assertEquals(2, Stars.perform(recap(6, 0, 4)))                  // 60%: heard it in time
        assertEquals(2, Stars.perform(recap(7, 1, 2, bias = 10)))       // 80%
        assertEquals(3, Stars.perform(recap(9, 1, 0, bias = 30)))       // all of it, tight
        assertEquals(2, Stars.perform(recap(9, 1, 0, bias = 120)))      // all in, but dragging: two
        assertEquals(2, Stars.perform(recap(9, 1, 0, bias = -80)))      // the band ends at 80 ms
        assertEquals(3, Stars.perform(recap(9, 1, 0, bias = -79)))
    }

    @Test fun ninetyPercentIsTheThirdStar() {
        assertEquals(90, recap(8, 1, 1).accuracy)
        assertEquals(3, Stars.perform(recap(8, 1, 1, bias = 10)))
        assertEquals(89, recap(80, 9, 11).accuracy)
        assertEquals(2, Stars.perform(recap(80, 9, 11, bias = 10)))
        assertEquals(59, recap(59, 0, 41).accuracy)
        assertEquals(1, Stars.perform(recap(59, 0, 41)))
    }

    @Test fun waitRunThresholds() {
        assertEquals(3, Stars.waitRun(0))
        assertEquals(2, Stars.waitRun(1)); assertEquals(2, Stars.waitRun(3))
        assertEquals(1, Stars.waitRun(4)); assertEquals(1, Stars.waitRun(40))
    }

    @Test fun ofPicksTheRightRule() {
        assertEquals(3, Stars.of(null, 0))
        assertEquals(1, Stars.of(recap(1, 0, 9), 0))                    // a Perform recap wins over wrong keys
        assertEquals("Two stars of three", Stars.spoken(2))
        assertEquals("One star of three", Stars.spoken(0))
    }
}
