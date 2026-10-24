package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.PawWeek
import com.bigpapituna.sharpears.engine.progress.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.9, the rest day: one missed day between two practice days keeps the streak, automatically, at most once in any seven
 * days; a second gap inside the window, or a gap of two days, is a plain missed day. The strips draw it as a rest-day paw
 * and the line says it plainly.
 */
class RestDayTest {
    private val d0 = LocalDate.of(2026, 10, 1)           // a Thursday
    private fun day(n: Int) = d0.plusDays(n.toLong())
    private fun days(vararg n: Int) = n.map { day(it) }.toSet()

    @Test fun oneMissedDayIsKeptAsARestDay() {
        val practised = days(0, 1, 2, 4, 5, 6)                // day 3 missed
        assertEquals(setOf(day(3)), PracticeStats.restDays(practised, day(6)))
        assertEquals(StreakRun(6, 1), PracticeStats.streakRun(practised, day(6)))
        assertEquals("6 days, with one rest day", PracticeStats.streakWords(6, 1))
        assertEquals("6 days in a row", PracticeStats.streakWords(6, 0))
        assertEquals("9 days, with two rest days", PracticeStats.streakWords(9, 2))
    }

    @Test fun atMostOneRestDayInAnySevenDays() {
        // Day 2 is the rest day; day 5 is a second single gap three days later: not kept.
        val practised = days(0, 1, 3, 4, 6)
        assertEquals(setOf(day(2)), PracticeStats.restDays(practised, day(6)))
        assertEquals(StreakRun(1, 0), PracticeStats.streakRun(practised, day(6)))
        // Six days after the first is still inside the window; seven days after is a new window.
        assertEquals(setOf(day(2)), PracticeStats.restDays(days(0, 1, 3, 4, 5, 6, 7, 9), day(9)))
        val twoWeeks = days(0, 1, 3, 4, 5, 6, 7, 8, 10, 11)
        assertEquals(setOf(day(2), day(9)), PracticeStats.restDays(twoWeeks, day(11)))
        assertEquals(StreakRun(10, 2), PracticeStats.streakRun(twoWeeks, day(11)))
    }

    @Test fun twoGapsInTheWindowSpendAFreezeWhenOneIsHeldOtherwiseBreak() {
        // A full week earns a freeze: day 7 is the rest day, day 9 spends the freeze.
        val withFreeze = (0..6).map { day(it) }.toSet() + days(8, 10)
        val r = Streaks.compute(withFreeze, day(10))
        assertEquals(9, r.streak); assertEquals(setOf(day(7)), r.restDays); assertEquals(setOf(day(9)), r.frozenDays)
        // No freeze held: the second gap breaks the run.
        assertEquals(1, Streaks.compute(days(0, 1, 3, 5), day(5)).streak)
    }

    @Test fun aGapOfTwoDaysIsNotARestDay() {
        val practised = days(0, 1, 2, 5)                      // days 3 and 4 missed
        assertTrue(PracticeStats.restDays(practised, day(5)).isEmpty())
        assertEquals(StreakRun(1, 0), PracticeStats.streakRun(practised, day(5)))
        assertEquals(1, Streaks.compute(practised, day(5)).streak)
    }

    @Test fun yesterdayIsARestDayWhileTodayIsOpenAndStopsBeingOneWhenTodayEndsEmpty() {
        val practised = days(0, 1, 2)
        // Today is day 4, nothing yet: day 3 holds the run for now.
        assertEquals(setOf(day(3)), PracticeStats.restDays(practised, day(4)))
        assertEquals(StreakRun(3, 1), PracticeStats.streakRun(practised, day(4)))
        val now = Streaks.compute(practised, day(4))
        assertEquals(3, now.streak); assertTrue(now.atRisk); assertEquals(1, now.restInStreak)
        // Day 4 ended with no practice: on day 5 it is a gap of two days.
        assertTrue(PracticeStats.restDays(practised, day(5)).isEmpty())
        assertEquals(0, PracticeStats.streakRun(practised, day(5)).days)
    }

    @Test fun noRestDayBeforeTheFirstPracticeOrAfterAStreakEnded() {
        assertTrue(PracticeStats.restDays(emptySet(), day(3)).isEmpty())
        assertTrue(PracticeStats.restDays(days(2), day(2)).isEmpty())
        assertEquals(StreakRun(0, 0), PracticeStats.streakRun(emptySet(), day(3)))
    }

    @Test fun thePawStripShowsTheRestDay() {
        val monday = LocalDate.of(2026, 10, 19)
        val practice = listOf(0, 1, 3, 4).associate { monday.plusDays(it.toLong()) to 300 }    // Wednesday missed
        val week = PawWeek.of(practice, monday.plusDays(4))
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.rest })
        assertFalse(week[2].practised)
        assertEquals(4, PawWeek.count(practice, monday.plusDays(4)))     // a rest day is not a day played
    }

    @Test fun theWeekLineSaysTheRestDay() {
        val today = day(6)
        val log = days(0, 1, 2, 4, 5, 6).associateWith { 600 }
        val run = PracticeStats.streakRun(log.keys, today)
        assertEquals("This week: 60 min, 6 days, with one rest day", PracticeStats.summary(log, today, run.days, run.rest))
    }
}
