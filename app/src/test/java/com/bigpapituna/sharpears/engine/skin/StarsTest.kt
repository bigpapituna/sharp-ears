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
        // 2.14: all of it in time is three whatever the drift (two before 2.14).
        assertEquals(3, Stars.perform(recap(9, 1, 0, bias = 120)))
        assertEquals(3, Stars.perform(recap(9, 1, 0, bias = -80)))
        // Between 90 and 97 percent the timing still decides: the band ends at 80 ms.
        assertEquals(95, recap(18, 1, 1).accuracy)
        assertEquals(2, Stars.perform(recap(18, 1, 1, bias = -80)))
        assertEquals(3, Stars.perform(recap(18, 1, 1, bias = -79)))
    }

    /** 2.14, the 3-star rule in one place: a clean run, or 98 percent or more, with nothing let go. */
    @Test fun theThreeStarRule() {
        // Sam's Twinkle: 40 good, 1 ok, 1 missed (98 percent), 126 ms early. Two stars in 2.13, three now.
        val sam = recap(40, 1, 1, bias = -126)
        assertEquals(98, sam.accuracy)
        assertEquals(3, Stars.perform(sam)); assertEquals(3, Stars.of(sam, wrongKeys = 0))
        // 97 percent with loose timing stays at two.
        val near = Recap(96, 1, 3, -126, 40)
        assertEquals(97, near.accuracy); assertEquals(2, Stars.perform(near))
        // Anything let go keeps the third star away, in Perform and in Wait.
        assertEquals(2, Stars.perform(recap(10, 0, 0), letGos = 1))
        assertEquals(2, Stars.waitRun(0, letGos = 1))
        assertEquals(2, Stars.of(null, 0, letGos = 2))
        // A clean wait run is three; one slip is two.
        assertEquals(3, Stars.waitRun(0)); assertEquals(2, Stars.waitRun(1))
        // One function decides it, and every caller agrees with it.
        for (acc in listOf(recap(49, 0, 1, bias = 200), recap(9, 0, 1, bias = 10), recap(97, 0, 3, bias = 10)))
            assertEquals(Stars.three(acc, 0), Stars.perform(acc) == 3)
        for (w in 0..5) assertEquals(Stars.three(null, w), Stars.waitRun(w) == 3)
        assertEquals(false, Stars.THREE_LINE.contains("%"))
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
