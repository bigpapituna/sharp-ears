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

    @Test fun theTwoThreeStarRulesStayInTheirPlaces() {
        // 2.14 merge: a lesson step is Stars.three (levels-ages), a song's Perform run the song rule (depth). Never or-ed.
        val allPlayedNoneInTime = recap(0, 20, 0)                      // 100% played, nothing in the good window
        assertEquals(3, Stars.perform(allPlayedNoneInTime))             // a lesson step: 98% or more is three
        assertEquals(2, Stars.of(allPlayedNoneInTime, 0))               // a song: nine notes in ten must be in time too
        val tightNinety = recap(45, 0, 5, bias = 20)                    // 90% played, all in time, timing within 80 ms
        assertEquals(3, Stars.perform(tightNinety))                     // a lesson step's third star (90% with tight timing)
        assertEquals(2, Stars.of(tightNinety, 0))                       // a song: 90% played is two (three needs 98%)
        // levels-ages' letGos still keeps a song recap at two at most.
        assertEquals(3, Stars.of(recap(42, 0, 0), 0))
        assertEquals(2, Stars.of(recap(42, 0, 0), 0, letGos = 1))
        // Each place says its own rule in the same frame.
        assertEquals("3 stars: steps played clean or at 98% or more (90% with the timing within 80 ms), and nothing let go.", Stars.LESSON_RULE)
        assertEquals("This lesson: 1 of 3 steps at 3 stars.", Stars.lessonLine(listOf(3, 2, 1)))
        assertEquals("This lesson: 0 of 1 step at 3 stars.", Stars.lessonLine(listOf(2)))
        assertEquals(listOf(Stars.LESSON_RULE, "This lesson: 2 of 2 steps at 3 stars."), Stars.lessonLines(listOf(3, 3)))
        assertEquals("3 stars: 98% or more, and 9 of 10 notes in time.", com.bigpapituna.sharpears.engine.SongStars.RULE)
        for (line in listOf(Stars.LESSON_RULE, com.bigpapituna.sharpears.engine.SongStars.RULE))
            org.junit.Assert.assertTrue(line, line.startsWith("3 stars: ") && line.endsWith(".") && line.none { it == '-' || it.code == 0x2013 || it.code == 0x2014 })
    }
}
