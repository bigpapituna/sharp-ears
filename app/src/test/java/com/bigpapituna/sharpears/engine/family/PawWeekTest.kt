package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PawWeekTest {
    private val wednesday = LocalDate.of(2026, 10, 14)

    @Test fun mondayToSundayWithTheDaysPractised() {
        val practice = mapOf(
            LocalDate.of(2026, 10, 12) to 300,     // Monday
            LocalDate.of(2026, 10, 14) to 5,       // today, a little
            LocalDate.of(2026, 10, 11) to 900,     // last Sunday: not this week
        )
        val week = PawWeek.of(practice, wednesday)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 12), week.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), week.last().date)
        assertEquals(listOf(true, false, true, false, false, false, false), week.map { it.practised })
        assertEquals(listOf(false, false, false, true, true, true, true), week.map { it.future })
        assertEquals("MTWTFSS", week.joinToString("") { it.initial })
        assertEquals(2, PawWeek.count(practice, wednesday))
    }

    @Test fun onSundayTheWholeWeekCounts() {
        val sunday = LocalDate.of(2026, 10, 18)
        val practice = (12..18).associate { LocalDate.of(2026, 10, it) to 60 }
        assertEquals(7, PawWeek.count(practice, sunday))
        assertEquals(0, PawWeek.of(practice, sunday).count { it.future })
    }

    @Test fun zeroSecondsIsABlankPaw() {
        assertEquals(0, PawWeek.count(mapOf(wednesday to 0), wednesday))
    }
}
