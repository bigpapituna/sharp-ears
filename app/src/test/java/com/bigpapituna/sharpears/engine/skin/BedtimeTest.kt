package com.bigpapituna.sharpears.engine.skin

import org.junit.Assert.assertEquals
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
    }

    /** 2.14 (Priya's P6): Songs and the Jam stay open after the goal, unless "Close Songs and the Jam too" is on as well. */
    @Test fun songsCloseAfterTheGoalOnlyWithTheirOwnSwitch() {
        assertTrue("wind down off", Bedtime.songsOpen(kid = true, practisedSec = 600, goalMin = 5, bedtime = false, closeSongs = true))
        assertTrue("wind down on, songs stay", Bedtime.songsOpen(kid = true, practisedSec = 600, goalMin = 5, bedtime = true, closeSongs = false))
        assertFalse("both on, goal reached", Bedtime.songsOpen(kid = true, practisedSec = 600, goalMin = 5, bedtime = true, closeSongs = true))
        assertTrue("before the goal", Bedtime.songsOpen(kid = true, practisedSec = 200, goalMin = 5, bedtime = true, closeSongs = true))
        assertTrue("grown-ups", Bedtime.songsOpen(kid = false, practisedSec = 99999, goalMin = 5, bedtime = true, closeSongs = true))
        assertEquals("After the goal: wind down", Bedtime.LABEL)
        assertEquals("Lessons close for the day; Songs and the Jam stay open.", Bedtime.LINE)
        assertEquals("Close Songs and the Jam too", Bedtime.SONGS_SWITCH)
    }

    /** Audit 2026-10-20: one "one more lesson" from a grown-up opened every lesson for the rest of the day. */
    @Test fun aGrownUpsOneMoreIsOneLesson() {
        val today = LocalDate.of(2026, 10, 20)
        assertTrue(Bedtime.oneMoreSpent(kid = true, practisedSec = 600, goalMin = 5, grownUpSaidOn = today, today = today, bedtime = true))
        assertFalse("bedtime off: nothing to spend", Bedtime.oneMoreSpent(true, 600, 5, today, today, bedtime = false))
        assertFalse("given another day", Bedtime.oneMoreSpent(true, 600, 5, today.minusDays(1), today, bedtime = true))
        assertFalse("before the goal lessons are open anyway", Bedtime.oneMoreSpent(true, 100, 5, today, today, bedtime = true))
        assertFalse("grown-ups have no bedtime", Bedtime.oneMoreSpent(false, 600, 5, today, today, bedtime = true))
        // Spent (cleared), the lessons sleep again until a grown-up says one more.
        assertTrue(Bedtime.lessonOpen(true, 600, 5, grownUpSaidOn = today, today = today, bedtime = true))
        assertFalse(Bedtime.lessonOpen(true, 600, 5, grownUpSaidOn = null, today = today, bedtime = true))
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
