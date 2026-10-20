package com.bigpapituna.sharpears.engine.skin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The kid's session goodbye: once a day, at the goal, lessons close and songs stay open. */
class BedtimeTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun goalReached() {
        assertFalse(Bedtime.goalReached(299, 5))
        assertTrue(Bedtime.goalReached(300, 5))
        assertTrue(Bedtime.goalReached(9999, 8))
        assertFalse(Bedtime.goalReached(9999, 0))               // no goal, no bedtime
    }

    @Test fun goodnightOncePerDayForKidsOnly() {
        assertTrue(Bedtime.sayGoodnight(kid = true, practisedSec = 300, goalMin = 5, shownOn = null, today = today, bedtime = true))
        assertFalse(Bedtime.sayGoodnight(kid = true, practisedSec = 299, goalMin = 5, shownOn = null, today = today))
        assertFalse(Bedtime.sayGoodnight(kid = true, practisedSec = 600, goalMin = 5, shownOn = today, today = today))
        assertTrue(Bedtime.sayGoodnight(kid = true, practisedSec = 600, goalMin = 5, shownOn = today.minusDays(1), today = today, bedtime = true))
        assertFalse(Bedtime.sayGoodnight(kid = false, practisedSec = 6000, goalMin = 10, shownOn = null, today = today))
        // An eight minute goal (a kid who reads).
        assertFalse(Bedtime.sayGoodnight(kid = true, practisedSec = 300, goalMin = 8, shownOn = null, today = today))
        assertTrue(Bedtime.sayGoodnight(kid = true, practisedSec = 480, goalMin = 8, shownOn = null, today = today, bedtime = true))
    }

    @Test fun lessonsCloseAfterTheGoalUnlessAGrownUpSaysOneMore() {
        // With bedtime switched on by a grown-up.
        assertTrue(Bedtime.lessonOpen(kid = true, practisedSec = 120, goalMin = 5, today = today, bedtime = true))
        assertFalse(Bedtime.lessonOpen(kid = true, practisedSec = 300, goalMin = 5, today = today, bedtime = true))
        assertTrue(Bedtime.lessonOpen(kid = true, practisedSec = 300, goalMin = 5, grownUpSaidOn = today, today = today, bedtime = true))
        assertFalse(Bedtime.lessonOpen(kid = true, practisedSec = 300, goalMin = 5, grownUpSaidOn = today.minusDays(1), today = today, bedtime = true))
        assertTrue(Bedtime.lessonOpen(kid = false, practisedSec = 99999, goalMin = 10, today = today))
        assertTrue(Bedtime.SONGS_OPEN)
    }

    @Test fun bedtimeIsOffByDefaultSoKidsPlayOn() {
        val today = LocalDate.of(2026, 10, 20)
        // The goal reached, bedtime off: no goodnight, lessons stay open, the cheer is said once a day.
        assertFalse(Bedtime.sayGoodnight(kid = true, practisedSec = 6000, goalMin = 5, shownOn = null, today = today))
        assertTrue(Bedtime.lessonOpen(kid = true, practisedSec = 6000, goalMin = 5, today = today))
        assertTrue(Bedtime.sayWellDone(kid = true, practisedSec = 300, goalMin = 5, shownOn = null, today = today))
        assertFalse(Bedtime.sayWellDone(kid = true, practisedSec = 300, goalMin = 5, shownOn = today, today = today))
        assertFalse(Bedtime.sayWellDone(kid = false, practisedSec = 3000, goalMin = 5, shownOn = null, today = today))
        // Bedtime on: the old rules.
        assertFalse(Bedtime.lessonOpen(kid = true, practisedSec = 6000, goalMin = 5, today = today, bedtime = true))
        assertTrue(Bedtime.lessonOpen(kid = true, practisedSec = 6000, goalMin = 5, grownUpSaidOn = today, today = today, bedtime = true))
    }
}
