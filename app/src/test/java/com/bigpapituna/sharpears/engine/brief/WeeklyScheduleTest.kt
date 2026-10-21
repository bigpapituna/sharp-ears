package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyScheduleTest {
    private val toronto = ZoneId.of("America/Toronto")
    private fun at(z: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0, s: Int = 0): Instant = LocalDateTime.of(y, m, d, h, min, s).atZone(z).toInstant()
    private fun local(i: Instant, z: ZoneId) = i.atZone(z).toLocalDateTime()
    private val sunday6pm = WeeklySlot()

    @Test fun theDefaultIsSundayAtSixInTheEveningAndOn() {
        assertEquals(DayOfWeek.SUNDAY, WeeklySlot.DEFAULT.day); assertEquals(18 * 60, WeeklySlot.DEFAULT.minutes); assertTrue(WeeklySlot.DEFAULT.on)
    }

    @Test fun aSlotSurvivesBeingKept() {
        for (s in listOf(WeeklySlot(), WeeklySlot(DayOfWeek.FRIDAY, 7 * 60 + 30, false), WeeklySlot(DayOfWeek.MONDAY, 0))) assertEquals(s, WeeklySlot.decode(s.encode()))
        assertNull(WeeklySlot.decode(null)); assertNull(WeeklySlot.decode("")); assertNull(WeeklySlot.decode("FUNDAY@5")); assertNull(WeeklySlot.decode("SUNDAY@9999")); assertNull(WeeklySlot.decode("SUNDAY"))
    }

    @Test fun nextIsThisComingSundayFromMidweek() {
        // Wednesday 21 October 2026, 8 pm.
        val next = WeeklyPlan.next(at(toronto, 2026, 10, 21, 20), toronto, sunday6pm)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), local(next, toronto))
    }

    @Test fun onTheDayBeforeTheTimeItIsToday_AfterTheTimeItIsNextWeek() {
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), local(WeeklyPlan.next(at(toronto, 2026, 10, 25, 9), toronto, sunday6pm), toronto))
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), local(WeeklyPlan.next(at(toronto, 2026, 10, 25, 18, 1), toronto, sunday6pm), toronto))
    }

    @Test fun anAlarmThatFiresRightOnTimeSchedulesNextWeekNotItself() {
        val fired = at(toronto, 2026, 10, 25, 18)
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), local(WeeklyPlan.next(fired, toronto, sunday6pm), toronto))     // 6 pm EST after the clocks went back
        // Twenty seconds early (inside the slack) is no different: it is not set again for the same minute.
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), local(WeeklyPlan.next(fired.minusSeconds(20), toronto, sunday6pm), toronto))
    }

    @Test fun sixPmStaysSixPmAcrossBothDaylightSavingChanges() {
        // Clocks go back on Sunday 1 November 2026: the Sunday before is 18:00 EDT (UTC-4), the Sunday itself 18:00 EST (UTC-5).
        val beforeFall = WeeklyPlan.next(at(toronto, 2026, 10, 31, 12), toronto, sunday6pm)
        assertEquals(LocalDateTime.of(2026, 11, 1, 18, 0), local(beforeFall, toronto))
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), beforeFall)
        // Clocks go forward on Sunday 8 March 2026.
        val beforeSpring = WeeklyPlan.next(at(toronto, 2026, 3, 7, 12), toronto, sunday6pm)
        assertEquals(LocalDateTime.of(2026, 3, 8, 18, 0), local(beforeSpring, toronto))
        assertEquals(Instant.parse("2026-03-08T22:00:00Z"), beforeSpring)
        // A week is not always 168 hours: from the fall Sunday to the next is 169.
        val beforeFallEdt = WeeklyPlan.next(at(toronto, 2026, 10, 24, 12), toronto, sunday6pm)
        val after = WeeklyPlan.next(beforeFallEdt, toronto, sunday6pm)
        assertEquals(169L, java.time.Duration.between(beforeFallEdt, after).toHours())
        assertEquals(Instant.parse("2026-11-01T23:00:00Z"), after)
    }

    @Test fun aTimeThatDoesNotExistMovesToJustAfterTheGapAndOneThatHappensTwiceUsesTheFirst() {
        val twoThirty = WeeklySlot(DayOfWeek.SUNDAY, 2 * 60 + 30)
        // 8 March 2026 02:30 does not exist in Toronto (02:00 jumps to 03:00): 03:30 EDT.
        val gap = WeeklyPlan.next(at(toronto, 2026, 3, 7, 12), toronto, twoThirty)
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), local(gap, toronto))
        // 1 November 2026 01:30 happens twice: the first one (EDT, UTC-4).
        val oneThirty = WeeklySlot(DayOfWeek.SUNDAY, 90)
        val overlap = WeeklyPlan.next(at(toronto, 2026, 10, 31, 12), toronto, oneThirty)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlap)
    }

    @Test fun theSameSlotFollowsThePhonesZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = Instant.parse("2026-10-21T00:00:00Z")           // Wednesday 9 am in Tokyo, Tuesday 8 pm in Toronto
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), local(WeeklyPlan.next(now, tokyo, sunday6pm), tokyo))
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 0), local(WeeklyPlan.next(now, toronto, sunday6pm), toronto))
        assertTrue(WeeklyPlan.next(now, tokyo, sunday6pm) != WeeklyPlan.next(now, toronto, sunday6pm))
    }

    @Test fun everyDayAndTimeGivesAFutureMomentOnThatDayAndTimeWithinAWeek() {
        val now = at(toronto, 2026, 11, 1, 1, 15)     // the night the clocks go back
        for (d in DayOfWeek.entries) for (m in listOf(0, 30, 90, 150, 18 * 60, 23 * 60 + 30)) {
            val n = WeeklyPlan.next(now, toronto, WeeklySlot(d, m))
            assertTrue(n.isAfter(now.plusSeconds(30)))
            assertTrue("within a week and an hour: $d $m", java.time.Duration.between(now, n).toHours() <= 24 * 7 + 1)
            val l = local(n, toronto)
            assertEquals(d, l.dayOfWeek)
            if (m != 150 && m != 90) assertEquals(m, l.hour * 60 + l.minute)
        }
    }

    @Test fun labelsReadLikeSpeech() {
        assertEquals("Sunday at 6 pm", WeeklyPlan.label(sunday6pm))
        assertEquals("Friday at 7:30 am", WeeklyPlan.label(WeeklySlot(DayOfWeek.FRIDAY, 7 * 60 + 30)))
        assertEquals("Monday at noon", WeeklyPlan.label(WeeklySlot(DayOfWeek.MONDAY, 720)))
        assertEquals("Monday at 12:30 pm", WeeklyPlan.label(WeeklySlot(DayOfWeek.MONDAY, 750)))
        assertEquals("Tuesday at 9 pm", WeeklyPlan.label(WeeklySlot(DayOfWeek.TUESDAY, 21 * 60)))
        assertEquals("midnight", WeeklyPlan.timeLabel(0))
    }

    @Test fun theTimeStepperMovesByHalfHoursAndStopsAtTheEnds() {
        assertEquals(18 * 60 + 30, WeeklyPlan.step(18 * 60, 1))
        assertEquals(17 * 60 + 30, WeeklyPlan.step(18 * 60, -1))
        assertEquals(WeeklyPlan.TIMES.first(), WeeklyPlan.step(WeeklyPlan.TIMES.first(), -1))
        assertEquals(WeeklyPlan.TIMES.last(), WeeklyPlan.step(WeeklyPlan.TIMES.last(), 1))
        assertEquals(18 * 60 + 30, WeeklyPlan.step(18 * 60 + 10, 0))       // an odd time snaps onto the grid
    }

    @Test fun eachPersonHasTheirOwnAlarm() {
        val codes = listOf("owner", "p1", "p2", "p3", "p4", "p5", "p6", "p7").map(WeeklyPlan::requestCode)
        assertEquals(codes.size, codes.distinct().size)
        assertTrue(codes.all { it >= 1_000 })
    }

    @Test fun theNotificationSaysNothingAboutScoresAndStaysQuietOnAnEmptyWeek() {
        val now = 1_800_000_000_000L
        assertEquals("Mia's weekly brief is ready", BriefNotice.title("Mia", ProfileKind.KID))
        assertEquals("Your week at the piano", BriefNotice.title("Roy", ProfileKind.ADULT))
        for (k in ProfileKind.entries) assertFalse(BriefNotice.text(k).any { it.isDigit() || it in "—–" })
        assertTrue(BriefNotice.shouldSend(BriefSamples.kid(Period.WEEK, now)))
        assertFalse(BriefNotice.shouldSend(BriefSamples.empty(Period.WEEK, now)))
    }
}
