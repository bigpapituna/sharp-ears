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
        // Only today so far: the week says what the goal line over it says (16 min, not 17).
        assertEquals("This week: 16 min", PracticeStats.summary(days(0L to 1000), today))
        assertEquals("Goal met, 16 min today", com.bigpapituna.sharpears.engine.progress.DailyGoal.line(1000, 10))
    }

    @Test fun streakStillCountsBeforeTodaysPractice() {
        val s = days(1L to 300, 2L to 300)
        assertEquals(2, PracticeStats.streak(s, today))
        // 2.9: practised two days ago, yesterday missed, today still open: yesterday reads as the rest day for now.
        assertEquals(1, PracticeStats.streak(days(2L to 300), today))
        assertEquals(0, PracticeStats.streak(days(3L to 300), today))      // two days missed: no rest day
    }

    @Test fun omittedWhenZeroAndNoStreakForOneDay() {
        assertNull(PracticeStats.summary(days(8L to 3000), today))
        assertEquals("This week: 1 min", PracticeStats.summary(days(0L to 20), today))
    }

    /**
     * Audit: the home's week line and "Done for today" counted the player's 60-day log with no freezes, while the
     * greeting and You count every practice with the freezes earned: with Oct 8 missed after a full week, the home said
     * "2 days in a row" next to a 9-day streak. Both now show the profile's streak.
     */
    @Test fun theWeekLineShowsTheProfilesStreak() {
        val today = LocalDate.of(2026, 10, 10)
        val days = (1..10).filter { it != 8 }.map { LocalDate.of(2026, 10, it) }
        val profile = com.bigpapituna.sharpears.engine.progress.Streaks.compute(days.toSet(), today).streak
        assertEquals(9, profile)
        assertEquals("This week: 30 min, 9 days in a row", PracticeStats.summary(days.associateWith { 300 }, today, profile))
    }

    @Test fun keepsSixtyDays() {
        val all = (0L..70L).map { today.minusDays(it) }
        val stale = PracticeStats.stale(all, today)
        assertEquals(11, stale.size)
        assertEquals(today.minusDays(60), stale.maxOrNull())
    }

    /** 2.7: You's goal card counts its week from Monday, beside the week's dots; the home's seven-day line is gone. */
    @Test fun theGoalCardCountsFromMonday() {
        val monday = LocalDate.of(2026, 10, 19)
        val wednesday = monday.plusDays(2)
        val log = mapOf(monday.minusDays(1) to 3600, monday to 600, monday.plusDays(1) to 59, wednesday to 1200, wednesday.plusDays(1) to 900)
        assertEquals(30, PracticeStats.minutesSince(log, monday, wednesday))        // 1859 s, counted down; Sunday and Thursday stay out
        assertEquals(1, PracticeStats.minutesSince(mapOf(monday to 20), monday, wednesday))   // any practice is a minute
        assertEquals(0, PracticeStats.minutesSince(emptyMap(), monday, wednesday))
    }
}
