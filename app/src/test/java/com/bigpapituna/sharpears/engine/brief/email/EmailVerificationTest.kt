package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The address check: a code with the first email, 24 hours, three wrong tries; and the settings it lives in. */
class EmailVerificationTest {
    private var now = 1_792_000_000_000L
    private val v = EmailVerification(clock = { now }, digits = { "482913" })

    @Test fun startingMakesACodeAndPutsTheFirstEmailFirst() {
        val (p, r) = v.start(EmailPrefs(), "  Grown.Up+piano@Example.COM ")
        assertEquals(EmailVerification.Started.SENDING, r)
        assertEquals("Grown.Up+piano@example.com", p.address)
        assertEquals("482913", p.code); assertEquals(now + 24 * 3_600_000L, p.codeExpiresAt)
        assertTrue(p.pending)
        val plan = p.plan(WeeklySlot.DEFAULT)
        assertTrue(plan.firstPending); assertFalse(plan.verified); assertEquals(EmailFrequency.OFF, plan.frequency)
        assertFalse("once the first email went, it waits for the code", p.sent(EmailKind.FIRST, LocalDate.of(2026, 10, 21), now).plan(WeeklySlot.DEFAULT).firstPending)
        for (bad in listOf("", "mia", "mia@", "@example.com", "mia@example", "mia@@example.com", "mia @example.com", "mia@example.c", "a".repeat(250) + "@x.com"))
            assertEquals(bad, EmailVerification.Started.BAD_ADDRESS, v.start(p, bad).second)
    }

    @Test fun theRightCodeWithinADayVerifiesAndThreeWrongOnesThrowItAway() {
        val (p, _) = v.start(EmailPrefs(), "parent@example.com")
        val (once, r1) = v.verify(p, "111111")
        assertEquals(EmailVerification.Verified.WRONG, r1); assertEquals(2, v.triesLeft(once))
        val (ok, r2) = v.verify(once, " 482 913 ")
        assertEquals(EmailVerification.Verified.OK, r2)
        assertTrue(ok.verified); assertNull(ok.code)
        assertEquals(EmailVerification.Verified.ALREADY, v.verify(ok, "482913").second)
        val plan = ok.plan(WeeklySlot.DEFAULT)
        assertTrue(plan.verified); assertEquals("weekly until the grown-up chooses", EmailFrequency.WEEKLY, plan.frequency)
        assertEquals(EmailFrequency.BOTH, ok.choose(EmailFrequency.BOTH).plan(WeeklySlot.DEFAULT).frequency)

        var q = p
        repeat(2) { q = v.verify(q, "000000").first }
        val (locked, r3) = v.verify(q, "000000")
        assertEquals(EmailVerification.Verified.LOCKED, r3)
        assertNull("the code is gone", locked.code)
        assertEquals("even the right code is too late now", EmailVerification.Verified.NO_CODE, v.verify(locked, "482913").second)
        assertFalse(locked.plan(WeeklySlot.DEFAULT).firstPending)
    }

    @Test fun aCodeOlderThanADayHasExpired() {
        val (p, _) = v.start(EmailPrefs(), "parent@example.com")
        now += 24 * 3_600_000L + 1
        val (late, r) = v.verify(p, "482913")
        assertEquals(EmailVerification.Verified.EXPIRED, r)
        assertNull(late.code); assertFalse(late.verified)
    }

    @Test fun theSettingsTravelAsJsonAndNeverPrintTheAddressOrTheCode() {
        val p = EmailPrefs("parent@example.com", verified = true, code = "482913", codeExpiresAt = 5, wrongTries = 1, firstSent = true,
            frequency = EmailFrequency.DAILY, chosen = true, lastDaily = LocalDate.of(2026, 10, 21), lastWeekly = LocalDate.of(2026, 10, 18),
            sends = listOf(1L, 2L))
        assertEquals(p, EmailPrefs.decode(p.encode()))
        assertEquals(EmailPrefs(), EmailPrefs.decode(null)); assertEquals(EmailPrefs(), EmailPrefs.decode("not json"))
        assertFalse(p.toString().contains("parent") || p.toString().contains("482913"))
        val day = LocalDate.of(2026, 10, 22)
        val sent = p.sent(EmailKind.DAILY, day, now).sent(EmailKind.WEEKLY, LocalDate.of(2026, 10, 25), now + 1)
        assertEquals(day, sent.lastDaily); assertEquals(LocalDate.of(2026, 10, 25), sent.lastWeekly)
        assertEquals("sends older than two weeks are dropped", listOf(now, now + 1), sent.sends)
        assertEquals("an older day never moves the last one back", day, sent.sent(EmailKind.DAILY, LocalDate.of(2026, 10, 1), now + 2).lastDaily)
        val stopped = sent.cleared()
        assertNull(stopped.address); assertFalse(stopped.verified); assertEquals(sent.sends, stopped.sends)
    }
}
