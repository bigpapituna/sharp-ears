package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.IdTitles
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The email job's decisions with a fake clock and a fake transport: who is sent what when, the retries and the wake, the
 * family's one alarm. (The Android half, reminder/EmailJob.kt, only loads the settings and the logs and sets the alarm.)
 */
class EmailRoundTest {
    private val wed = LocalDate.of(2026, 10, 21)
    private fun ms(day: LocalDate, h: Int, m: Int = 0) = EmailSchedule.at(day, LocalTime.of(h, m), ZONE)
    private val sundaySix = WeeklySlot(DayOfWeek.SUNDAY, 18 * 60)

    private val sentMessages = ArrayList<EmailMessage>()
    private var key = true
    private var allowed = true
    private var failWith: EmailSendException? = null
    private var clock = ms(wed, 18, 55)

    private fun sender() = EmailSender(
        transport = { m -> failWith?.let { throw it }; sentMessages += m; "<id>" }, hasKey = { key }, networkAllowed = { allowed },
        clock = { clock }, zone = ZONE,
    )
    private val writer = TemplateWriter({ id -> IdTitles.lesson(id) }, seed = 1, clock = { clock })
    private val child = EmailSender.Child("p1", "Mia", 1, IdTitles)
    private val findings = { p: Period -> BriefSamples.kid(p, clock, ZONE) }

    private fun verified(f: EmailFrequency = EmailFrequency.BOTH) = EmailPrefs("parent@example.com", verified = true, frequency = f, chosen = true)
    private fun session(h1: Int, m1: Int, h2: Int, m2: Int, day: LocalDate = wed) = PlaySpan(ms(day, h1, m1), ms(day, h2, m2), played = true)

    private fun run(prefs: EmailPrefs, spans: List<PlaySpan>, weekly: WeeklySlot = sundaySix, caps: EmailCaps = EmailCaps()) =
        EmailRound.run(clock, ZONE, prefs, weekly, spans, caps, send = { due, p -> sender().send(due, p, findings(due.period), writer, child) })

    @Test fun aDayPlayedAndFinishedIsEmailedOnceThirtyMinutesAfterTheLastSession() {
        val spans = listOf(session(18, 0, 18, 20))
        clock = ms(wed, 18, 49)
        val early = run(verified(), spans)
        assertTrue("29 minutes in: not yet", early.sent.isEmpty()); assertEquals(ms(wed, 18, 50), early.wakeAt); assertNull(early.last)
        clock = ms(wed, 18, 55)
        val r = run(verified(), spans)
        assertEquals(listOf(EmailKind.DAILY), r.sent)
        assertEquals(1, sentMessages.size)
        assertEquals(wed, r.prefs.lastDaily); assertEquals(clock, r.prefs.lastDailyAt); assertEquals(listOf(clock), r.prefs.sends)
        assertEquals("the weekly slot is the next wake", EmailSchedule.nextSlot(clock, ZONE, sundaySix), r.wakeAt)
        // Asked again, nothing more goes that day.
        val again = run(r.prefs, spans)
        assertTrue(again.sent.isEmpty()); assertEquals(1, sentMessages.size)
    }

    @Test fun aNewSessionRestartsTheHalfHourAndSomeoneWhoDidNotPlayGetsNothing() {
        clock = ms(wed, 19, 0)
        val spans = listOf(session(18, 0, 18, 20), session(18, 40, 18, 45))
        assertEquals("the second session ended at 18:45: due 19:15", ms(wed, 19, 15), run(verified(), spans).wakeAt)
        assertTrue(run(verified(), emptyList()).sent.isEmpty())
        assertTrue(run(verified(EmailFrequency.OFF), spans).sent.isEmpty())
        assertTrue("not verified yet", run(verified().copy(verified = false, code = "123456", firstSent = true), spans).sent.isEmpty())
    }

    @Test fun theFirstEmailGoesAtOnceWhateverTheHourAndWaitsForItsCode() {
        clock = ms(wed, 23, 10)
        val pending = EmailPrefs("parent@example.com", code = "482913", codeExpiresAt = clock + 1_000_000)
        val r = run(pending, emptyList())
        assertEquals(listOf(EmailKind.FIRST), r.sent)
        assertTrue(r.prefs.firstSent && r.prefs.pending)
        assertTrue("the code is in the email", sentMessages.single().subject.contains("482913"))
        assertNull("nothing else until it is verified", r.wakeAt)
        assertTrue(run(r.prefs, emptyList()).sent.isEmpty())
    }

    @Test fun refusalsSayWhyAndWhenToLookAgain() {
        val spans = listOf(session(18, 0, 18, 20))
        val due = verified()
        key = false
        run(due, spans).let { assertEquals(EmailSender.Refusal.NO_KEY, it.held); assertNull("no key: never again", it.wakeAt); assertTrue(it.sent.isEmpty()) }
        key = true; allowed = false
        run(due, spans).let { assertEquals(EmailSender.Refusal.METERED, it.held); assertEquals(clock + EmailRound.METERED_RETRY_MS, it.wakeAt) }
        allowed = true
        assertTrue("nothing was sent through all of it", sentMessages.isEmpty())
        failWith = EmailSendException(EmailSendException.Kind.NETWORK, null, "offline")
        run(due, spans).let { assertTrue(it.last is EmailSender.Outcome.Failed); assertEquals(clock + EmailRound.RETRY_MS, it.wakeAt); assertNull(it.held); assertEquals(due, it.prefs) }
        failWith = null
        assertEquals(listOf(EmailKind.DAILY), run(due, spans).sent)
    }

    @Test fun theCapsStopASecondDayAndHoldTheOthersUntilMidnight() {
        val spans = listOf(session(18, 0, 18, 20))
        val twoToday = verified().copy(sends = listOf(ms(wed, 8), ms(wed, 12)))
        val r = run(twoToday, spans)
        assertTrue(r.sent.isEmpty()); assertTrue(sentMessages.isEmpty())
        assertEquals(ms(wed.plusDays(1), 0), r.wakeAt)
    }

    @Test fun theQuietHoursHoldADueDayUntilSevenInTheMorning() {
        clock = ms(wed, 22, 0)
        val spans = listOf(session(20, 40, 21, 10))
        val r = run(verified(EmailFrequency.DAILY), spans)
        assertTrue(r.sent.isEmpty()); assertEquals(ms(wed.plusDays(1), 7), r.wakeAt)
        clock = ms(wed.plusDays(1), 7, 1)
        assertEquals("the morning sends the day before", listOf(EmailKind.DAILY), run(verified(EmailFrequency.DAILY), spans).sent)
        assertEquals(wed, run(verified(EmailFrequency.DAILY), spans).prefs.lastDaily)
    }

    @Test fun aDayAndAWeekDueTogetherBothGoInOneTurnButNeverAThird() {
        val sunday = LocalDate.of(2026, 10, 25)
        clock = ms(sunday, 18, 40)
        val spans = listOf(session(17, 0, 18, 5, sunday))
        val r = run(verified(), spans)
        assertEquals(listOf(EmailKind.DAILY, EmailKind.WEEKLY), r.sent.sorted()); assertEquals(2, sentMessages.size)
        assertEquals(2, r.prefs.sends.size)
        // A test email sent earlier counts: with one already today only one more goes.
        sentMessages.clear()
        val one = run(verified().copy(sends = listOf(ms(sunday, 9))), spans)
        assertEquals(1, one.sent.size)
    }

    @Test fun eachSendIsRecordedOnTheFreshestSettings() {
        val spans = listOf(session(18, 0, 18, 20))
        val grownUpChangedItMidSend = verified().copy(frequency = EmailFrequency.OFF)
        var recorded: Pair<EmailDue.Send, Long>? = null
        val r = EmailRound.run(clock, ZONE, verified(), sundaySix, spans, send = { due, p -> sender().send(due, p, findings(due.period), writer, child) },
            record = { due, at -> recorded = due to at; grownUpChangedItMidSend.sent(due.kind, due.day, at) })
        assertEquals(EmailFrequency.OFF, r.prefs.frequency)
        assertEquals(EmailKind.DAILY, recorded!!.first.kind); assertEquals(clock, recorded!!.second)
        assertNull("off: nothing else is due", r.wakeAt)
    }

    @Test fun wakeAsksWithoutSending() {
        val spans = listOf(session(18, 0, 18, 20))
        assertEquals(clock + EmailRound.SOON_MS, EmailRound.wake(clock, ZONE, verified(), sundaySix, spans))
        clock = ms(wed, 18, 30)
        assertEquals(ms(wed, 18, 50), EmailRound.wake(clock, ZONE, verified(), sundaySix, spans))
        assertEquals(EmailSchedule.nextSlot(clock, ZONE, sundaySix), EmailRound.wake(clock, ZONE, verified(EmailFrequency.WEEKLY), sundaySix, emptyList()))
        assertNull(EmailRound.wake(clock, ZONE, verified(EmailFrequency.OFF), sundaySix, spans))
        assertTrue(sentMessages.isEmpty())
    }

    @Test fun theFamilysOneAlarmIsTheEarliestWakeAndAlwaysTheCheckpoints() {
        val now = ms(wed, 12, 0)
        assertEquals("07:00 and 20:30 are looked at", ms(wed, 20, 30), EmailRound.nextCheckpoint(now, ZONE))
        assertEquals(ms(wed.plusDays(1), 7), EmailRound.nextCheckpoint(ms(wed, 21, 0), ZONE))
        assertEquals(ms(wed, 7), EmailRound.nextCheckpoint(ms(wed, 3, 0), ZONE))
        assertEquals(ms(wed, 20, 30), EmailRound.alarmAt(now, ZONE, listOf(null, null), anyActive = true))
        assertEquals(ms(wed, 15, 0), EmailRound.alarmAt(now, ZONE, listOf(ms(wed, 15, 0), ms(wed, 16, 0)), anyActive = true))
        assertNull("nobody needs one", EmailRound.alarmAt(now, ZONE, listOf(null), anyActive = false))
        assertEquals("never in the past", now + EmailRound.MIN_AHEAD_MS, EmailRound.alarmAt(now, ZONE, listOf(now - 5), anyActive = false))
        // Who needs checking at all.
        assertTrue(EmailRound.active(verified()))
        assertFalse(EmailRound.active(verified(EmailFrequency.OFF)))
        assertFalse(EmailRound.active(EmailPrefs()))
        assertTrue(EmailRound.active(EmailPrefs("a@b.co", code = "123456")))
        assertFalse("waiting for the code, first email gone", EmailRound.active(EmailPrefs("a@b.co", code = "123456", firstSent = true)))
    }

    @Test fun theClocksGoingBackDoNotSendTwiceOrMissTheCheckpoint() {
        val fall = LocalDate.of(2026, 11, 1)          // Toronto clocks go back at 02:00
        val spans = listOf(session(18, 0, 18, 20, fall))
        clock = ms(fall, 18, 55)
        assertEquals(1, run(verified(EmailFrequency.DAILY), spans).sent.size)
        assertNotNull(EmailRound.nextCheckpoint(ms(fall, 1, 30), ZONE))
        assertEquals(ms(fall, 7), EmailRound.nextCheckpoint(ms(fall, 1, 30), ZONE))
    }
}
