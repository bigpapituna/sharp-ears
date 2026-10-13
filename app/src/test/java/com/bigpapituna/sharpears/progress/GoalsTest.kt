package com.bigpapituna.sharpears.progress

import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.engine.progress.Streaks
import com.bigpapituna.sharpears.engine.progress.WeeklyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val d0 = LocalDate.of(2026, 9, 1)            // a Tuesday
    private fun day(n: Int) = d0.plusDays(n.toLong())
    private fun days(vararg n: Int) = n.map { day(it) }.toSet()
    private fun run(from: Int, to: Int) = (from..to).map { day(it) }.toSet()

    @Test fun dailyGoalMath() {
        assertEquals(listOf(5, 10, 15, 20, 30), DailyGoal.OPTIONS)
        assertFalse(DailyGoal.met(599, 10))
        assertTrue(DailyGoal.met(600, 10))
        assertEquals(0.5f, DailyGoal.fraction(300, 10), 0.001f)
        assertEquals(1f, DailyGoal.fraction(5000, 10), 0f)
        assertEquals(0, DailyGoal.minutes(0))
        assertEquals(1, DailyGoal.minutes(20))               // any practice shows as a minute
        assertEquals(3, DailyGoal.minutes(150))
        DailyGoal.OPTIONS.forEach { assertTrue(DailyGoal.note(it).isNotBlank()) }
    }

    @Test fun noPracticeNoStreak() {
        val r = Streaks.compute(emptySet(), day(10))
        assertEquals(0, r.streak); assertEquals(0, r.freezes); assertFalse(r.atRisk)
    }

    @Test fun plainStreakAndTodayStillOpen() {
        // Practised days 0..3; today is day 4 and still empty: the streak holds and is at risk.
        val r = Streaks.compute(run(0, 3), day(4))
        assertEquals(4, r.streak)
        assertTrue(r.atRisk)
        assertFalse(r.practicedToday)
        // Practising today extends it.
        val r2 = Streaks.compute(run(0, 4), day(4))
        assertEquals(5, r2.streak); assertFalse(r2.atRisk); assertTrue(r2.practicedToday)
    }

    @Test fun missedDayWithoutFreezeBreaksIt() {
        val r = Streaks.compute(days(0, 1, 2, 4), day(4))      // day 3 missed, no freeze held yet
        assertEquals(1, r.streak)
        assertEquals(3, r.longest)
        assertEquals(0, r.freezesUsed)
    }

    @Test fun sevenDaysEarnAFreeze() {
        assertEquals(0, Streaks.compute(run(0, 5), day(5)).freezes)
        assertEquals(1, Streaks.compute(run(0, 6), day(6)).freezes)
        assertEquals(1, Streaks.compute(run(0, 12), day(12)).freezes)
        assertEquals(2, Streaks.compute(run(0, 13), day(13)).freezes)
    }

    @Test fun freezesCapAtTwo() {
        val r = Streaks.compute(run(0, 27), day(27))           // four weeks: four earned, two kept
        assertEquals(2, r.freezes)
        assertEquals(28, r.streak)
    }

    @Test fun aMissedDaySpendsAFreezeInsteadOfBreaking() {
        // Seven days earn one; day 7 is missed and frozen; practice resumes on day 8.
        val r = Streaks.compute(run(0, 6) + days(8, 9), day(9))
        assertEquals(9, r.streak)                              // 7 + 2 practised days; the frozen day adds nothing
        assertEquals(0, r.freezes)
        assertEquals(1, r.freezesUsed)
        assertEquals(setOf(day(7)), r.frozenDays)
    }

    @Test fun twoMissedDaysNeedTwoFreezes() {
        val earned = run(0, 13)                                // two freezes
        val one = Streaks.compute(earned + days(16), day(16)) // days 14 and 15 missed
        assertEquals(15, one.streak)
        assertEquals(0, one.freezes); assertEquals(2, one.freezesUsed)
        // Three misses: the third has nothing to spend, so the streak resets.
        val three = Streaks.compute(earned + days(17), day(17))
        assertEquals(1, three.streak)
        assertEquals(14, three.longest)
    }

    @Test fun missingTodayNeverSpendsAFreeze() {
        val r = Streaks.compute(run(0, 6), day(7))             // today (day 7) is still open
        assertEquals(7, r.streak); assertEquals(1, r.freezes); assertEquals(0, r.freezesUsed)
        assertTrue(r.atRisk)
    }

    @Test fun freezeNotSpentWhenThereIsNoStreak() {
        // A new practice day after a long gap: the old streak is long gone and no freeze is wasted on empty days.
        val r = Streaks.compute(days(0, 20), day(20))
        assertEquals(1, r.streak)
        assertEquals(0, r.freezesUsed)
    }

    @Test fun freezeEarnedAgainAfterUsingOne() {
        // Week one earns a freeze, day 7 is frozen, then practice runs to a second multiple of seven.
        val r = Streaks.compute(run(0, 6) + run(8, 14), day(14))
        assertEquals(14, r.streak)                             // 14 practised days, one frozen
        assertEquals(1, r.freezes)                             // spent one, earned one at 14
        assertEquals(1, r.freezesUsed)
    }

    @Test fun daysAwayAndComeback() {
        assertEquals(0, Streaks.daysAway(day(0), day(1)))
        assertEquals(6, Streaks.daysAway(day(0), day(7)))
        assertFalse(Streaks.isComeback(day(0), day(7)))        // six days off is not yet a week
        assertTrue(Streaks.isComeback(day(0), day(8)))         // seven full days off is
        assertFalse(Streaks.isComeback(null, day(8)))
    }

    @Test fun weeklyGoal() {
        // 2026-09-01 is a Tuesday; the week starts Monday 2026-08-31.
        assertEquals(LocalDate.of(2026, 8, 31), WeeklyGoal.weekStart(d0))
        assertEquals(LocalDate.of(2026, 8, 31), WeeklyGoal.weekStart(LocalDate.of(2026, 9, 6)))   // Sunday
        val practiced = days(0, 1, 3, 4)                      // Tue, Wed, Fri, Sat
        assertEquals(4, WeeklyGoal.daysThisWeek(practiced, day(5)))
        assertTrue(WeeklyGoal.met(practiced, day(5), 4))
        assertFalse(WeeklyGoal.met(practiced, day(5), 5))
        assertEquals(0, WeeklyGoal.daysThisWeek(practiced, day(6)))   // Monday starts a new week
        // Two weeks: the first meets 3, the second only 1.
        val two = days(0, 1, 3, 8)
        assertEquals(1, WeeklyGoal.weeksMet(two, 3))
        assertEquals(2, WeeklyGoal.weeksMet(two, 1))
    }
}
