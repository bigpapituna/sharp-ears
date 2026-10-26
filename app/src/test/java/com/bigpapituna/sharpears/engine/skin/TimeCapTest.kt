package com.bigpapituna.sharpears.engine.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14, a kid's daily time (Priya's P6): the cap, how what runs ends, the pup's line, and which doors stay open. */
class TimeCapTest {
    @Test fun theChoicesAndTheirLabels() {
        assertEquals(listOf(0, 10, 15, 20, 30, 45), TimeCap.CHOICES)
        assertEquals("Off", TimeCap.label(0))
        assertEquals("10 min", TimeCap.label(10))
        assertEquals("a stored cap that is not a choice reads as off", 0, TimeCap.of(12))
        assertEquals(30, TimeCap.of(30))
    }

    @Test fun theCapCountsTimeOnScreen() {
        assertFalse("off never", TimeCap.reached(0, 99 * 60_000L))
        assertFalse(TimeCap.reached(10, 9 * 60_000L + 59_999))
        assertTrue(TimeCap.reached(10, 10 * 60_000L))
        assertNull(TimeCap.minutesLeft(0, 0))
        assertEquals(10, TimeCap.minutesLeft(10, 0))
        assertEquals("any time left reads as a minute", 1, TimeCap.minutesLeft(10, 9 * 60_000L + 30_000))
        assertEquals(0, TimeCap.minutesLeft(10, 11 * 60_000L))
    }

    @Test fun whatRunsEndsAtItsNextStepOrAfterTheGrace() {
        assertTrue("a step ended", TimeCap.endNow(boundary = true, reachedForMs = 0))
        assertFalse("mid-step, just reached", TimeCap.endNow(boundary = false, reachedForMs = 30_000))
        assertTrue("stuck on a step for two minutes past it", TimeCap.endNow(boundary = false, reachedForMs = TimeCap.GRACE_MS))
    }

    @Test fun thePupsLine() {
        assertEquals("That's all for today. See you tomorrow!", TimeCap.line(null))
        assertEquals("That's all for today, Mia. See you tomorrow!", TimeCap.line("Mia"))
    }

    /** Lessons, Songs, the Jam and the games say "Tomorrow" once the time is up; the stickers never close. */
    @Test fun theDoors() {
        val free = KidDay()
        for (d in KidDoor.entries) assertTrue(d.name, KidDoors.open(d, free))
        val capped = KidDay(capReached = true)
        for (d in KidDoor.entries) assertEquals(d.name, d == KidDoor.STICKERS, KidDoors.open(d, capped))
        assertTrue(capped.allAsleep)
        // After the goal, wind down: lessons close, songs and toys stay.
        val windDown = KidDay(lessonsAsleep = true)
        assertFalse(KidDoors.open(KidDoor.LESSON, windDown))
        for (d in listOf(KidDoor.SONGS, KidDoor.TOYS, KidDoor.GAMES, KidDoor.STICKERS)) assertTrue(d.name, KidDoors.open(d, windDown))
        assertFalse(windDown.allAsleep)
        // ... with "Close Songs and the Jam too".
        val all = KidDay(lessonsAsleep = true, songsAsleep = true)
        for (d in KidDoor.entries) assertEquals(d.name, d == KidDoor.STICKERS, KidDoors.open(d, all))
        assertTrue(all.allAsleep)
        assertEquals("Tomorrow", KidDoors.TOMORROW)
    }
}
