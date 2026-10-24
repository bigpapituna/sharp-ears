package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.RunMode
import com.bigpapituna.sharpears.engine.brief.Hands
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the email brief goes (EmailSchedule): the day's email 30 minutes after the last session, or at 20:30 if still
 * playing, once a day; the weekly at the grown-up's slot; nothing without playing; the frequency; the first email at once;
 * quiet hours from 21:30 to 07:00; the caps; and the two days a year the clocks change (Toronto).
 */
class EmailScheduleTest {
    private val zone: ZoneId = ZoneId.of("America/Toronto")
    private val wed = LocalDate.of(2026, 10, 21)
    private fun t(day: LocalDate, h: Int, m: Int = 0): Long = ZonedDateTime.of(day.atTime(h, m), zone).toInstant().toEpochMilli()
    private fun span(day: LocalDate, h: Int, m: Int, minutes: Int, played: Boolean = true) =
        PlaySpan(t(day, h, m), t(day, h, m) + minutes * 60_000L, played)
    private fun open(day: LocalDate, h: Int, m: Int = 0) = PlaySpan(t(day, h, m), null, true)

    private val sunday6pm = WeeklySlot(DayOfWeek.SUNDAY, 18 * 60)
    private fun plan(f: EmailFrequency = EmailFrequency.DAILY, lastDaily: LocalDate? = null, lastWeekly: LocalDate? = null,
                     sends: List<Long> = emptyList(), weekly: WeeklySlot = sunday6pm) =
        EmailPlan(f, weekly, verified = true, lastDaily = lastDaily, lastWeekly = lastWeekly, sends = sends)

    private fun decide(now: Long, p: EmailPlan, spans: List<PlaySpan>) = EmailSchedule.decide(now, zone, p, spans)
    private fun daily(day: LocalDate, now: Long) = EmailDue.Send(EmailKind.DAILY, day, Period.DAY, now)

    @Test fun aDaysEmailGoesHalfAnHourAfterTheLastSessionAndANewSessionPushesItBack() {
        val spans = listOf(span(wed, 16, 0, 15))                  // 16:00 to 16:15
        assertEquals(EmailDue.Wait(t(wed, 16, 45)), decide(t(wed, 16, 30), plan(), spans))
        assertEquals(daily(wed, t(wed, 16, 45)), decide(t(wed, 16, 45), plan(), spans))
        val again = spans + span(wed, 16, 40, 10)                  // back at the piano before the half hour was up
        assertEquals(EmailDue.Wait(t(wed, 17, 20)), decide(t(wed, 16, 45), plan(), again))
        assertEquals(daily(wed, t(wed, 17, 20)), decide(t(wed, 17, 20), plan(), again))
    }

    @Test fun stillPlayingAtHalfPastEightItGoesThenAndOnlyOnce() {
        val playing = listOf(span(wed, 18, 0, 20), open(wed, 19, 50))
        assertEquals(EmailDue.Wait(t(wed, 20, 30)), decide(t(wed, 20, 0), plan(), playing))
        assertEquals(daily(wed, t(wed, 20, 30)), decide(t(wed, 20, 30), plan(), playing))
        assertEquals("one a day: nothing more that day", EmailDue.Wait(t(LocalDate.of(2026, 10, 25), 18)),
            decide(t(wed, 21, 0), plan(EmailFrequency.BOTH, lastDaily = wed), playing + span(wed, 20, 40, 10)))
        // Finished at 20:10: the half hour would end at 20:40, but 20:30 is the latest.
        assertEquals(daily(wed, t(wed, 20, 30)), decide(t(wed, 20, 30), plan(), listOf(span(wed, 19, 40, 30))))
    }

    @Test fun aLateEveningWaitsForTheMorningAndADayHeldOvernightIsDroppedAtNoon() {
        val late = listOf(span(wed, 20, 45, 15))                   // started after 20:30, ended 21:00: due 21:30, quiet hours
        val thu = wed.plusDays(1)
        assertEquals(EmailDue.Wait(t(thu, 7)), decide(t(wed, 21, 30), plan(), late))
        assertEquals("nothing in the night", EmailDue.Wait(t(thu, 7)), decide(t(thu, 3), plan(), late))
        val morning = decide(t(thu, 7, 5), plan(), late) as EmailDue.Send
        assertEquals(EmailKind.DAILY, morning.kind); assertEquals(wed, morning.day)
        assertEquals("Wednesday's findings, to its last moment", EmailSchedule.endOf(wed, zone), morning.findingsAt)
        assertEquals("stale after noon", EmailDue.Wait(null), decide(t(thu, 12, 0), plan(), late))
    }

    @Test fun noPlayingNoEmailAndOpeningTheAppIsNotPlaying() {
        assertEquals(EmailDue.Wait(null), decide(t(wed, 19), plan(), emptyList()))
        assertEquals(EmailDue.Wait(null), decide(t(wed, 19), plan(), listOf(span(wed, 16, 0, 5, played = false))))
        assertEquals(EmailDue.Wait(null), decide(t(wed, 19), plan(lastDaily = wed), listOf(span(wed, 16, 0, 10))))
    }

    @Test fun theWeeklyGoesAtTheSlotSkipsAQuietWeekAndCatchesUpAMissedSlot() {
        val sun = LocalDate.of(2026, 10, 25)
        val week = listOf(span(LocalDate.of(2026, 10, 20), 17, 0, 20))
        val p = plan(EmailFrequency.WEEKLY)
        assertEquals(EmailDue.Wait(t(sun, 18)), decide(t(sun, 17), p, week))
        assertEquals(EmailDue.Send(EmailKind.WEEKLY, sun, Period.WEEK, t(sun, 18)), decide(t(sun, 18), p, week))
        assertEquals("the phone was off: Monday morning still", EmailDue.Send(EmailKind.WEEKLY, sun, Period.WEEK, t(sun, 18)),
            decide(t(sun.plusDays(1), 9), p, week))
        assertEquals("sent already", EmailDue.Wait(t(sun.plusWeeks(1), 18)), decide(t(sun, 18, 5), p.copy(lastWeekly = sun), week))
        assertEquals("a week with no playing", EmailDue.Wait(t(sun.plusWeeks(2), 18)), decide(t(sun.plusWeeks(1), 18), p, week))
        assertEquals("two days late is too late", EmailDue.Wait(t(sun.plusWeeks(1), 18)), decide(t(sun.plusDays(2), 18, 1), p, week))
    }

    @Test fun theFrequencyChoosesWhichGo() {
        val sun = LocalDate.of(2026, 10, 25)
        val spans = listOf(span(sun, 16, 0, 20))
        val now = t(sun, 18, 0)
        assertEquals(daily(sun, now), decide(now, plan(EmailFrequency.DAILY), spans))
        assertEquals(EmailKind.WEEKLY, (decide(now, plan(EmailFrequency.WEEKLY), spans) as EmailDue.Send).kind)
        val both = plan(EmailFrequency.BOTH)
        assertEquals("the day's was due first", EmailKind.DAILY, (decide(now, both, spans) as EmailDue.Send).kind)
        assertEquals(EmailKind.WEEKLY, (decide(now, both.copy(lastDaily = sun, sends = listOf(now)), spans) as EmailDue.Send).kind)
        assertEquals(EmailDue.Wait(null), decide(now, plan(EmailFrequency.OFF), spans))
        assertEquals("unverified: nothing", EmailDue.Wait(null), decide(now, plan().copy(verified = false), spans))
    }

    /** 2.10.1: the first email is the code alone (CodeEmail), so it goes at once and covers no day of playing. */
    @Test fun theFirstEmailGoesAtOnceEvenAtNight() {
        val first = EmailPlan(EmailFrequency.OFF, sunday6pm, verified = false, firstPending = true)
        assertEquals(EmailDue.Send(EmailKind.FIRST, wed, Period.DAY, t(wed, 22)), decide(t(wed, 22), first, listOf(span(wed, 17, 0, 10))))
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals("played days before: still now", EmailDue.Send(EmailKind.FIRST, wed, Period.DAY, t(wed, 9)),
            decide(t(wed, 9), first, listOf(span(monday, 17, 0, 10), span(wed, 8, 0, 3, played = false))))
        assertEquals("never played", EmailDue.Send(EmailKind.FIRST, wed, Period.DAY, t(wed, 9)), decide(t(wed, 9), first, emptyList()))
        assertEquals("the caps hold it too", EmailDue.Wait(t(wed.plusDays(1), 0)), decide(t(wed, 9), first.copy(sends = listOf(t(wed, 7), t(wed, 8))), emptyList()))
    }

    @Test fun twoADayAndTenAWeek() {
        val spans = listOf(span(wed, 16, 0, 10))
        val now = t(wed, 17)
        assertEquals(daily(wed, now), decide(now, plan(sends = listOf(t(wed, 8))), spans))
        assertEquals(EmailDue.Wait(t(wed.plusDays(1), 0)), decide(now, plan(sends = listOf(t(wed, 8), t(wed, 9))), spans))
        val monday = LocalDate.of(2026, 10, 19)
        val caps = EmailCaps()
        assertFalse(caps.allows(List(10) { t(monday, 8) + it }, now, zone))
        assertEquals(t(LocalDate.of(2026, 10, 26), 0), caps.nextFree(List(10) { t(monday, 8) + it }, now, zone))
        assertTrue(caps.allows(listOf(t(monday, 8), t(monday.minusDays(1), 8)), now, zone))
    }

    @Test fun theDaysTheClocksChangeKeepWallClockTimes() {
        // Back: Sunday 1 November 2026, 2:00 EDT becomes 1:00 EST.
        val sat = LocalDate.of(2026, 10, 31); val sun = sat.plusDays(1)
        val late = listOf(span(sat, 20, 45, 30))                   // ends 21:15, due 21:45: quiet, so 07:00 EST Sunday
        assertEquals(EmailDue.Wait(ZonedDateTime.of(sun.atTime(7, 0), zone).toInstant().toEpochMilli()), decide(t(sat, 22), plan(), late))
        assertEquals(t(sun, 7) - t(sat, 22), (9 + 1) * 3_600_000L)   // the night is an hour longer, and 07:00 is still 07:00
        val sunday = listOf(span(sun, 19, 0, 70))                  // still at it past 20:10: 20:30 local, EST
        assertEquals(daily(sun, t(sun, 20, 30)), decide(t(sun, 20, 30), plan(), sunday))
        assertEquals(EmailDue.Send(EmailKind.WEEKLY, sun, Period.WEEK, t(sun, 18)), decide(t(sun, 18), plan(EmailFrequency.WEEKLY), late + sunday))
        // Forward: Sunday 14 March 2027, 2:00 EST becomes 3:00 EDT.
        val fwd = LocalDate.of(2027, 3, 14)
        assertEquals("the quiet night is an hour shorter", (9 - 1) * 3_600_000L, t(fwd, 7) - t(fwd.minusDays(1), 22))
        assertEquals(EmailDue.Wait(t(fwd, 7)), decide(t(fwd, 1, 30), plan(), listOf(span(fwd, 0, 30, 30))))
        assertEquals(daily(fwd, t(fwd, 7)), decide(t(fwd, 7), plan(), listOf(span(fwd, 0, 30, 30))))
        // Half an hour after the last session is real time across the jump: 01:50 EST + 30 minutes is 03:20 EDT.
        val night = listOf(PlaySpan(t(fwd, 1, 20), t(fwd, 1, 50), true))
        assertEquals(EmailDue.Wait(t(fwd, 7)), decide(t(fwd, 3, 21), plan(), night))
        assertEquals(t(fwd, 3, 20), EmailSchedule.daily(fwd, night, zone).dueAt)
    }

    @Test fun theSpansComeFromTheLogAndSayWhetherAnythingWasPlayed() {
        val s = t(wed, 16)
        val events = listOf(
            BriefEvent.SessionStarted(s), BriefEvent.SessionEnded(s + 300_000, 300_000, 0),                       // looked around
            BriefEvent.SessionStarted(s + 3_600_000), BriefEvent.SongRun(s + 3_700_000, "ode", RunMode.LISTEN, Hands.BOTH, stars = 1),
            BriefEvent.SessionEnded(s + 4_000_000, 400_000, 0),                                                   // only listened
            BriefEvent.SessionStarted(s + 7_200_000), BriefEvent.SessionEnded(s + 7_500_000, 300_000, 120),       // practised
            BriefEvent.SessionStarted(s + 9_000_000), BriefEvent.LessonStep(s + 9_060_000, "pond-1", 1, "PLAY", tries = 1),
        )
        val spans = EmailSchedule.spans(events, now = s + 9_100_000)
        assertEquals(listOf(false, false, true, true), spans.map { it.played })
        assertEquals("the last one is still under way", null, spans.last().endMs)
        val closed = EmailSchedule.spans(events, now = s + 12_000_000)
        assertEquals("quiet for longer than a session gap: it ended at its last event", s + 9_060_000, closed.last().endMs)
        assertEquals("on screen right now says otherwise", null, EmailSchedule.spans(events, now = s + 12_000_000, openNow = true).last().endMs)
    }
}
