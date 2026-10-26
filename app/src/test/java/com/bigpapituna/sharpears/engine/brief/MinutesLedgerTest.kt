package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.PracticeStats
import com.bigpapituna.sharpears.engine.family.FamilyToday
import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 2.14, the one minutes ledger (Priya's P11): every view of a child's minutes reads the same sessions, rounds the same way,
 * and a session the app never ended still counts once the next start closes it from its checkpoint.
 */
class MinutesLedgerTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    @Test fun oneRoundingCountedDownAndAnyTimeIsAtLeastOneMinute() {
        assertEquals(0, MinutesLedger.minutes(0))
        assertEquals(1, MinutesLedger.minutes(20))
        assertEquals(1, MinutesLedger.minutes(119))
        assertEquals(2, MinutesLedger.minutes(150))          // 2.5 minutes read 2, as the goal line and PracticeStats count
        assertEquals(16, MinutesLedger.minutes(16 * 60 + 36))
        assertEquals(0, MinutesLedger.minutesOfMs(0))
        assertEquals(1, MinutesLedger.minutesOfMs(400))
        assertEquals(22, MinutesLedger.minutesOfMs(22 * 60_000L + 59_000))
        // PracticeStats' week is the same rule.
        assertEquals(MinutesLedger.minutes(16 * 60 + 36), PracticeStats.weekMinutes(mapOf(TODAY to 16 * 60 + 36), TODAY))
    }

    @Test fun daysAreTheSessionsThatStartedOnThem() {
        val events = history {
            session(d(20), 17, 12, practiceMin = 8)
            session(d(21), 9, 5, practiceMin = 3)
            session(d(21), 18, 20, practiceMin = 14)
        }
        val days = MinutesLedger.byDay(events, NOW, ZONE)
        assertEquals(MinutesLedger.Day(d(20), 12 * 60_000L, 8 * 60), days[d(20)])
        val today = days.getValue(TODAY)
        assertEquals(25, today.minutes); assertEquals(17, today.practiceMinutes)
        assertEquals(mapOf(d(20) to 480, d(21) to 1020), MinutesLedger.practiceByDay(events, NOW, ZONE))
        assertEquals(MinutesLedger.Day(d(19)), MinutesLedger.day(events, d(19), NOW, ZONE))
    }

    /** The app ends and starts its session at midnight; the two halves stay on their own days even three minutes apart. */
    @Test fun midnightSplitsADayFromTheNext() {
        val start = at(d(20), 23, 50)
        val midnight = at(d(21), 0, 0)
        val events = listOf(
            BriefEvent.SessionStarted(start), BriefEvent.SessionEnded(midnight, 10 * 60_000L, 9 * 60),
            BriefEvent.SessionStarted(midnight), BriefEvent.SessionEnded(midnight + 6 * 60_000L, 6 * 60_000L, 5 * 60),
        )
        val days = MinutesLedger.byDay(events, NOW, ZONE)
        assertEquals(9, days.getValue(d(20)).practiceMinutes)
        assertEquals(5, days.getValue(d(21)).practiceMinutes)
        // The brief's days say the same.
        val strip = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE).stats.days.associate { it.date to it.practiceMinutes }
        assertEquals(9, strip["2026-10-20"]); assertEquals(5, strip["2026-10-21"])
    }

    /**
     * A session the app never ended (force-stopped mid-step): data/Briefs.kt writes its end on the next start, stamped at
     * its last checkpoint, with the practice the stores counted. Every view counts it, and they all agree.
     */
    @Test fun aKilledSessionCountsItsCheckpointedMinutesAndEveryViewAgrees() {
        val killedAt = at(TODAY, 17, 0)
        val events = history {
            session(d(18), 18, 15, practiceMin = 11) { step("playground-1") }
            session(d(20), 18, 9, practiceMin = 6) { done("playground-2", stars = 2) }
        } + listOf(
            BriefEvent.SessionStarted(killedAt),
            BriefEvent.LessonDone(killedAt + 60_000, "playground-3", 3, 120_000, true),
            // Recovered on the next start: the last checkpoint said 7 min on screen; the stores counted 5 min 40 s.
            BriefEvent.SessionEnded(killedAt + 7 * 60_000L + 1, 7 * 60_000L, 340),
        ) + history { session(TODAY, 19, 4, practiceMin = 3) }
        val day = Insights.compute(events, mia, Period.DAY, NOW, zone = ZONE)
        val week = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE)
        val ledger = MinutesLedger.day(events, TODAY, NOW, ZONE)
        assertEquals("killed 5 min 40 s plus 3 min, counted down", 8, ledger.practiceMinutes)
        assertEquals(11, ledger.minutes)
        // Today: the brief's day, its fact, the strip's last day, the family card.
        assertEquals(8, day.stats.totals.practiceMinutes)
        assertEquals(11, day.stats.totals.minutes)
        assertEquals(Fact(FactKind.PRACTICE_MINUTES, 8, 11), day.facts.first { it.kind == FactKind.PRACTICE_MINUTES })
        assertEquals(8, week.stats.days.last().practiceMinutes)
        assertEquals(11, week.stats.days.last().minutes)
        val card = FamilyToday.of(mia.id, events, TODAY, NOW, ZONE)
        assertEquals(8, card.practiceMinutes); assertEquals(11, card.minutes); assertEquals(1, card.lessons); assertEquals(3, card.stars)
        // The week: the brief's total, the email's "In one minute" (the same Totals), and PracticeStats over the ledger's days.
        val perDay = MinutesLedger.practiceByDay(events, NOW, ZONE)
        assertEquals(PracticeStats.weekMinutes(perDay, TODAY), week.stats.totals.practiceMinutes)
        assertEquals(MinutesLedger.minutes((11 + 6) * 60L + 340 + 180), week.stats.totals.practiceMinutes)
        // The session note for the killed session alone.
        val note = Insights.compute(events.filter { it.at <= killedAt + 7 * 60_000L + 1 }, mia, Period.SESSION, killedAt + 7 * 60_000L + 1, zone = ZONE)
        assertEquals(5, note.stats.totals.practiceMinutes)
        assertEquals(7, note.stats.totals.minutes)
    }

    /** Without its recovered end, an unfinished session still shows its time on screen up to its last event (as before). */
    @Test fun anUnendedSessionIsNotLostEitherWay() {
        val start = at(TODAY, 17, 0)
        val events = listOf(BriefEvent.SessionStarted(start), BriefEvent.LessonDone(start + 4 * 60_000L, "playground-1", 3, 0, true))
        assertEquals(4, MinutesLedger.day(events, TODAY, NOW, ZONE).minutes)
    }
}
