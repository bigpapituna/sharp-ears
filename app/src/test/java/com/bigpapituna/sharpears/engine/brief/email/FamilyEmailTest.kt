package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** One address for the family (2.8): the split into two records, the join, the first email's child and the move from 2.7. */
class FamilyEmailTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val everything = EmailPrefs("parent@example.com", verified = true, code = "482913", codeExpiresAt = 7, wrongTries = 1, firstSent = true,
        frequency = EmailFrequency.DAILY, chosen = true, lastDaily = day, lastWeekly = day.minusDays(3), sends = listOf(1L, 2L),
        lastDailyAt = 3, lastWeeklyAt = 4, held = "METERED")

    @Test fun theTwoPartsSplitEverythingAndJoinBack() {
        val family = FamilyEmail.addressPart(everything)
        val own = FamilyEmail.ownPart(everything)
        assertEquals(EmailPrefs("parent@example.com", verified = true, code = "482913", codeExpiresAt = 7, wrongTries = 1, firstSent = true, sends = listOf(1L, 2L)), family)
        assertNull("a child's part holds no address and no code", own.address); assertNull(own.code); assertFalse(own.verified)
        assertEquals(EmailFrequency.DAILY, own.frequency); assertEquals(day, own.lastDaily); assertEquals("METERED", own.held)
        assertEquals(everything, FamilyEmail.join(family, own, first = true))
        assertEquals("the child's sends count for the caps, not the family's", listOf(9L), FamilyEmail.join(family, own.copy(sends = listOf(9L)), first = true).sends)
    }

    @Test fun theCodeGoesInOneChildsEmailOnly() {
        val pending = EmailPrefs("parent@example.com", code = "482913", codeExpiresAt = 99)
        assertTrue(FamilyEmail.join(pending, EmailPrefs(), first = true).plan(WeeklySlot.DEFAULT).firstPending)
        assertFalse(FamilyEmail.join(pending, EmailPrefs(), first = false).plan(WeeklySlot.DEFAULT).firstPending)
        val gone = pending.copy(firstSent = true)
        assertFalse(FamilyEmail.join(gone, EmailPrefs(), first = true).plan(WeeklySlot.DEFAULT).firstPending)
        assertEquals("waiting for the code: no child's emails go yet", EmailFrequency.OFF, FamilyEmail.join(gone, EmailPrefs(), first = false).plan(WeeklySlot.DEFAULT).frequency)
        val confirmed = FamilyEmail.join(gone.copy(code = null, verified = true), EmailPrefs(frequency = EmailFrequency.BOTH), first = false)
        assertTrue(confirmed.plan(WeeklySlot.DEFAULT).verified); assertEquals(EmailFrequency.BOTH, confirmed.plan(WeeklySlot.DEFAULT).frequency)
    }

    @Test fun theFirstEmailIsAboutTheFirstChildWhoseEmailsAreOn() {
        val off = EmailPrefs(frequency = EmailFrequency.OFF)
        assertEquals("p2", FamilyEmail.lead(listOf("p1" to off, "p2" to EmailPrefs(frequency = EmailFrequency.DAILY), "p3" to EmailPrefs())))
        assertEquals("all off: the first child", "p1", FamilyEmail.lead(listOf("p1" to off, "p2" to off)))
        assertEquals("p1", FamilyEmail.lead(listOf("p1" to EmailPrefs(), "p2" to EmailPrefs())))
        assertNull(FamilyEmail.lead(emptyList()))
    }

    @Test fun twoSevenAddressesMoveToTheFamilyWithNothingConfirmedAgain() {
        val confirmedLater = EmailPrefs("second@example.com", verified = true, firstSent = true)
        val confirmed = EmailPrefs("first@example.com", verified = true, firstSent = true, frequency = EmailFrequency.BOTH, chosen = true, sends = listOf(4L))
        val m = FamilyEmail.migrate(listOf("p1" to null, "p2" to confirmed, "p3" to confirmedLater, "p4" to EmailPrefs(sends = listOf(8L))))
        assertEquals("first@example.com", m.family.address); assertTrue(m.family.verified); assertTrue(m.family.firstSent)
        assertEquals("a child with no record is switched off", EmailPrefs(frequency = EmailFrequency.OFF), m.children["p1"])
        assertEquals(FamilyEmail.ownPart(confirmed), m.children["p2"])
        assertEquals("another confirmed child keeps their emails, now to the family's address", EmailFrequency.WEEKLY, m.children["p3"]!!.frequency)
        assertEquals("a record without an address is switched off and keeps its sends", EmailPrefs(frequency = EmailFrequency.OFF, sends = listOf(8L)), m.children["p4"])
        assertTrue(m.children.values.all { it.address == null && it.code == null })

        // None confirmed: the first code waiting moves, so the code in the inbox still works.
        val waiting = EmailPrefs("mum@example.com", code = "482913", codeExpiresAt = 99, wrongTries = 1, firstSent = true)
        val w = FamilyEmail.migrate(listOf("p1" to EmailPrefs(), "p2" to waiting))
        assertEquals("mum@example.com", w.family.address); assertEquals("482913", w.family.code); assertEquals(1, w.family.wrongTries); assertFalse(w.family.verified)
        assertEquals(EmailFrequency.OFF, w.children["p1"]!!.frequency)

        // Nothing anywhere: no address, and no child is switched off.
        val none = FamilyEmail.migrate(listOf("p1" to null, "p2" to EmailPrefs(frequency = EmailFrequency.DAILY)))
        assertNull(none.family.address)
        assertEquals(mapOf("p2" to EmailPrefs(frequency = EmailFrequency.DAILY)), none.children)
    }

    @Test fun theSwitchesMakeTheFrequency() {
        assertEquals(EmailFrequency.BOTH, EmailFrequency.of(daily = true, weekly = true))
        assertEquals(EmailFrequency.DAILY, EmailFrequency.of(daily = true, weekly = false))
        assertEquals(EmailFrequency.WEEKLY, EmailFrequency.of(daily = false, weekly = true))
        assertEquals(EmailFrequency.OFF, EmailFrequency.of(daily = false, weekly = false))
        for (f in EmailFrequency.entries) assertEquals(f, EmailFrequency.of(f.daily, f.weekly))
    }
}
