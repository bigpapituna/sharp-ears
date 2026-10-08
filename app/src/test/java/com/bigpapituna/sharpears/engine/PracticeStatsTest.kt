package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PracticeStatsTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun days(vararg back: Pair<Long, Int>) = back.associate { (d, s) -> today.minusDays(d) to s }

    @Test fun weekAndStreak() {
        val s = days(0L to 600, 1L to 900, 2L to 600, 3L to 420, 5L to 0, 9L to 3000)
        assertEquals(42, PracticeStats.weekMinutes(s, today))
        assertEquals(4, PracticeStats.streak(s, today))
        assertEquals("This week: 42 min, 4 days in a row", PracticeStats.summary(s, today))
    }

    @Test fun streakStillCountsBeforeTodaysPractice() {
        val s = days(1L to 300, 2L to 300)
        assertEquals(2, PracticeStats.streak(s, today))
        assertEquals(0, PracticeStats.streak(days(2L to 300), today))
    }

    @Test fun omittedWhenZeroAndNoStreakForOneDay() {
        assertNull(PracticeStats.summary(days(8L to 3000), today))
        assertEquals("This week: 1 min", PracticeStats.summary(days(0L to 20), today))
    }

    @Test fun keepsSixtyDays() {
        val all = (0L..70L).map { today.minusDays(it) }
        val stale = PracticeStats.stale(all, today)
        assertEquals(11, stale.size)
        assertEquals(today.minusDays(60), stale.maxOrNull())
    }
}
