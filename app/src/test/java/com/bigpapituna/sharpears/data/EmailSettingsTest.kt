package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.ai.MemoryBudgetStore
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailKind
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The email settings in the one shared file (2.8): one address for the family under its own key, entered and confirmed
 * once; each child's key keeps only their switches, the last sent and the sends; 2.7's per-child addresses move over once.
 */
class EmailSettingsTest {
    private val store = MemoryBudgetStore()
    private val verification = EmailVerification(clock = { 1_000L }, digits = { "123456" })
    private val day = LocalDate.of(2026, 10, 21)

    @Test fun oneAddressForTheFamilyConfirmedOnceAndEachChildKeepsTheirSwitches() {
        val mia = EmailSettings(store, "p1"); val leo = EmailSettings(store, "p2")
        assertEquals(EmailVerification.Started.BAD_ADDRESS, mia.family.start("not an address", verification))
        assertEquals(EmailVerification.Started.SENDING, mia.family.start("mum@example.com", verification))
        assertEquals("both children see the family's address", "mum@example.com", leo.load().address)
        assertEquals(setOf(EmailSettings.FAMILY), store.keys())

        // The first email goes for one child only: the code is the family's, so the others count it as gone.
        assertTrue(mia.load(first = true).plan(WeeklySlot.DEFAULT).firstPending)
        assertFalse(leo.load(first = false).plan(WeeklySlot.DEFAULT).firstPending)
        mia.sent(EmailKind.FIRST, day, 2_000L, first = true)
        assertTrue(mia.family.load().firstSent)
        assertEquals(listOf(2_000L), mia.family.load().sends)
        assertEquals("the child's caps count it too", listOf(2_000L), mia.own().sends)
        assertFalse(mia.load(first = true).plan(WeeklySlot.DEFAULT).firstPending)

        assertEquals(EmailVerification.Verified.OK, leo.family.verify("123456", verification))
        assertTrue("confirmed once, for both", mia.load().verified && leo.load().verified)

        // The switches are each child's own.
        mia.setDaily(true)
        leo.setWeekly(false)
        assertEquals(EmailFrequency.BOTH, mia.load().frequency)
        assertEquals(EmailFrequency.OFF, leo.load().frequency)
        leo.setDaily(true)
        assertEquals(EmailFrequency.DAILY, leo.load().frequency)
        mia.sent(EmailKind.DAILY, day, 3_000L)
        with(mia.load()) { assertEquals(day, lastDaily); assertEquals(listOf(2_000L, 3_000L), sends); assertTrue(chosen) }
        assertNull(leo.load().lastDaily)

        // A child's record never holds the address, whatever is saved through it.
        mia.save(mia.load().copy(address = "someone@else.com", verified = false))
        assertNull(EmailPrefs.decode(store.get("p1")).address)
        assertEquals("mum@example.com", mia.load().address)
        assertFalse("the store holds no address in plain sight of a log", mia.load().toString().contains("@"))

        // Stop: the address and the code go; the switches and the sends stay.
        mia.family.stop()
        assertNull(leo.load().address); assertFalse(leo.load().verified)
        assertEquals(EmailFrequency.DAILY, leo.load().frequency)
        assertEquals(listOf(2_000L, 3_000L), mia.own().sends)

        // A deleted child takes only their own key.
        leo.forget()
        assertEquals(setOf(EmailSettings.FAMILY, "p1"), store.keys())
    }

    @Test fun theFirstRunMovesTheFirstConfirmedChildsAddressToTheFamilyOnce() {
        // 2.7: Mia with a code waiting at one address, Leo confirmed at another (daily), Noor never set up.
        store.put("p1", EmailPrefs("aunt@example.com", code = "999999", codeExpiresAt = 9_000, firstSent = true).encode())
        store.put("p2", EmailPrefs("dad@example.com", verified = true, firstSent = true, frequency = EmailFrequency.DAILY, chosen = true,
            lastDaily = day, sends = listOf(5L)).encode())
        assertTrue(EmailSettings.migrate(store, listOf("p1", "p2", "p3")))

        val mia = EmailSettings(store, "p1"); val leo = EmailSettings(store, "p2"); val noor = EmailSettings(store, "p3")
        with(leo.load()) {
            assertEquals("the first confirmed address is the family's, confirmed", "dad@example.com", address)
            assertTrue(verified); assertNull(code)
            assertEquals(EmailFrequency.DAILY, frequency); assertEquals(day, lastDaily); assertEquals(listOf(5L), sends)
        }
        assertEquals("Mia had an address: her switches stay (weekly, never chosen)", EmailFrequency.WEEKLY, mia.load().frequency)
        assertEquals("Noor had none: she gets no emails she did not get before", EmailFrequency.OFF, noor.load().frequency)
        for (id in listOf("p1", "p2", "p3")) assertNull("no child's record keeps an address", EmailPrefs.decode(store.get(id)).address)

        // Once only: a later run changes nothing, even with an old record put back.
        store.put("p1", EmailPrefs("aunt@example.com", verified = true).encode())
        assertFalse(EmailSettings.migrate(store, listOf("p1", "p2", "p3")))
        assertEquals("dad@example.com", mia.load().address)
    }

    @Test fun aPhoneThatNeverHadAnAddressStartsWithEveryChildWeekly() {
        assertTrue(EmailSettings.migrate(store, listOf("p1", "p2")))
        assertNull(EmailSettings(store, "p1").load().address)
        assertEquals("nothing to move: nobody switched off", setOf(EmailSettings.FAMILY), store.keys())
        assertEquals(EmailFrequency.WEEKLY, EmailSettings(store, "p2").load().frequency)
        assertFalse(EmailSettings.migrate(store, listOf("p1", "p2")))
    }
}
