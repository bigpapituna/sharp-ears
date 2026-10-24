package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Settings > Briefs and email's logic: the address's steps, the resend wait, a child's row, the last-sent lines and every refusal in kind words. */
class EmailFlowTest {
    private val at = EmailSchedule.at(LocalDate.of(2026, 10, 20), LocalTime.of(20, 31), ZONE)      // Tuesday 8:31 pm

    @Test fun theFamilysAddressWalksThroughTheSteps() {
        assertEquals(EmailFlow.Stage.NO_ADDRESS, EmailFlow.stage(EmailPrefs()))
        val (started, _) = EmailVerification(clock = { at }, digits = { "123456" }).start(EmailPrefs(), "Parent@Example.com")
        assertEquals(EmailFlow.Stage.FIRST_WAITING, EmailFlow.stage(started))
        val sent = started.sent(EmailKind.FIRST, LocalDate.of(2026, 10, 20), at)
        assertEquals(EmailFlow.Stage.CODE, EmailFlow.stage(sent))
        assertNull("2.10.1: the code email covers no day, so the day's brief still goes once confirmed", sent.lastDaily)
        assertEquals(EmailFlow.Stage.CODE_GONE, EmailFlow.stage(sent.copy(code = null)))
        assertEquals(EmailFlow.Stage.VERIFIED, EmailFlow.stage(sent.copy(code = null, verified = true)))
    }

    @Test fun aNewCodeOpensTenMinutesAfterTheLastEmail() {
        val p = EmailPrefs("a@b.co", code = "1", firstSent = true, sends = listOf(at))
        assertEquals(10 * 60_000L, EmailFlow.resendIn(p, at))
        assertEquals(3 * 60_000L, EmailFlow.resendIn(p, at + 7 * 60_000))
        assertEquals(0L, EmailFlow.resendIn(p, at + 10 * 60_000))
        assertEquals(0L, EmailFlow.resendIn(EmailPrefs("a@b.co"), at))
        assertEquals("in 3 minutes", EmailFlow.waitWords(3 * 60_000L))
        assertEquals("in a minute", EmailFlow.waitWords(20_000L))
        assertEquals("in 8 minutes", EmailFlow.waitWords(7 * 60_000L + 1))
    }

    @Test fun theCodeSaysWhatHappenedKindly() {
        assertEquals("Not that one. 2 tries left.", EmailFlow.codeWords(EmailVerification.Verified.WRONG, 2))
        assertEquals("Not that one. One try left.", EmailFlow.codeWords(EmailVerification.Verified.WRONG, 1))
        assertTrue(EmailFlow.codeWords(EmailVerification.Verified.LOCKED, 0).contains("new code"))
        for (r in EmailVerification.Verified.entries) assertTrue(r.name, EmailWords.clean(EmailFlow.codeWords(r, 2)))
    }

    @Test fun theAddressFieldOnlyComplainsAboutAnAddressThatIsWrong() {
        assertNull(EmailFlow.addressProblem(""))
        assertNull(EmailFlow.addressProblem("mum@example.com"))
        assertNull(EmailFlow.addressProblem("  Mum@Example.COM "))
        assertEquals("That does not look like an email address.", EmailFlow.addressProblem("mum@"))
        assertEquals("That does not look like an email address.", EmailFlow.addressProblem("mum example.com"))
    }

    @Test fun everyRefusalAndFailureIsInKindWords() {
        assertEquals("Email is not set up on this build.", EmailFlow.refusalWords(EmailSender.Refusal.NO_KEY))
        assertTrue(EmailFlow.refusalWords(EmailSender.Refusal.METERED).startsWith("Waiting for Wi-Fi"))
        assertTrue(EmailFlow.refusalWords(EmailSender.Refusal.CAPPED).startsWith("That's enough for today"))
        for (r in EmailSender.Refusal.entries) EmailFlow.refusalWords(r).let { assertTrue(it, it.isNotBlank() && EmailWords.clean(it) && it.endsWith(".")) }
        for (k in EmailSendException.Kind.entries) EmailFlow.failureWords(k).let { assertTrue(it, it.isNotBlank() && EmailWords.clean(it) && it.endsWith(".")) }
        assertEquals("Sent. Check your inbox.", EmailFlow.outcomeWords(EmailSender.Outcome.Sent(EmailPrefs(), EmailFixtures.day())))
        assertEquals("the first email, the code alone, too", "Sent. Check your inbox.", EmailFlow.outcomeWords(EmailSender.Outcome.Sent(EmailPrefs(), null)))
        assertEquals(EmailFlow.refusalWords(EmailSender.Refusal.NO_KEY), EmailFlow.outcomeWords(EmailSender.Outcome.Refused(EmailSender.Refusal.NO_KEY)))
        assertTrue(EmailFlow.heldWords(EmailPrefs(held = "METERED"))!!.startsWith("Waiting"))
        assertNull(EmailFlow.heldWords(EmailPrefs(held = "nonsense"))); assertNull(EmailFlow.heldWords(EmailPrefs()))
    }

    @Test fun aChildsRowSaysWhenInTheChildsWords() {
        assertEquals("Each day Mia plays, 8:30 pm at the latest.", EmailFlow.dailyLine("Mia"))
        assertEquals("Friday at 7:30 pm. Tap to change.", EmailFlow.weeklyLine(WeeklySlot(DayOfWeek.FRIDAY, 19 * 60 + 30)))
        assertEquals("That's the one. Each child's emails can start now.", EmailFlow.codeWords(EmailVerification.Verified.OK, 3))
        for (line in listOf(EmailFlow.dailyLine("Mia"), EmailFlow.weeklyLine(WeeklySlot.DEFAULT), EmailFlow.ROWS_WAIT, EmailFlow.TEST_NOTE))
            assertTrue(line, EmailWords.clean(line) && line.endsWith(".") && line[0].isUpperCase())
    }

    @Test fun theLastSentLinesNameTheDayAndTime() {
        val p = EmailPrefs("a@b.co", verified = true, frequency = EmailFrequency.BOTH, chosen = true, lastDailyAt = at)
        assertEquals(listOf("Daily: sent Tuesday 8:31 pm", "Weekly: not sent yet"), EmailFlow.lastLines(p, ZONE))
        assertEquals(listOf("Daily: sent Tuesday 8:31 pm"), EmailFlow.lastLines(p.copy(frequency = EmailFrequency.DAILY), ZONE))
        assertEquals(emptyList<String>(), EmailFlow.lastLines(p.copy(frequency = EmailFrequency.OFF, lastDailyAt = 0), ZONE))
        assertFalse(EmailFlow.lastLines(p, ZONE).any { !EmailWords.clean(it) })
    }

    @Test fun theSettingsRoundTripAndForgiveOldFiles() {
        val p = EmailPrefs("a@b.co", verified = true, frequency = EmailFrequency.BOTH, chosen = true, lastDailyAt = at, lastWeeklyAt = at + 5, held = "METERED",
            lastDaily = LocalDate.of(2026, 10, 20), sends = listOf(at))
        assertEquals(p, EmailPrefs.decode(p.encode()))
        val old = EmailPrefs.decode("{\"address\":\"a@b.co\",\"verified\":true,\"frequency\":\"DAILY\"}")
        assertEquals(0L, old.lastDailyAt); assertNull(old.held); assertEquals(EmailFrequency.DAILY, old.frequency)
        assertEquals("sent clears held and records the time", p.copy(held = null, lastDailyAt = at + 9, lastDaily = LocalDate.of(2026, 10, 21), sends = listOf(at, at + 9)),
            p.sent(EmailKind.DAILY, LocalDate.of(2026, 10, 21), at + 9))
        assertEquals("a test only counts", p.copy(sends = listOf(at, at + 9)), p.counted(at + 9))
        assertFalse(p.toString().contains("a@b.co"))
    }
}
