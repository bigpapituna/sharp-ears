package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.brief.ai.MemoryBudgetStore
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailKind
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Each child's email settings in the one shared file: a key per child, the flow through it, forgotten with the profile. */
class EmailSettingsTest {
    private val store = MemoryBudgetStore()
    private val verification = EmailVerification(clock = { 1_000L }, digits = { "123456" })

    @Test fun eachChildHasTheirOwnAddressInTheOneFile() {
        val mia = EmailSettings(store, "p1"); val leo = EmailSettings(store, "p2")
        assertEquals(EmailVerification.Started.SENDING, mia.start("mum@example.com", verification))
        assertEquals(EmailVerification.Started.SENDING, leo.start("dad@example.com", verification))
        assertEquals(EmailVerification.Started.BAD_ADDRESS, leo.start("not an address", verification))
        assertEquals("dad@example.com", leo.load().address)
        assertEquals(setOf("p1", "p2"), store.keys())
        assertEquals(EmailVerification.Verified.OK, mia.verify("123456", verification))
        assertTrue(mia.load().verified); assertFalse(leo.load().verified)
        mia.choose(EmailFrequency.DAILY)
        mia.sent(EmailKind.DAILY, LocalDate.of(2026, 10, 21), 2_000L)
        with(mia.load()) { assertEquals(EmailFrequency.DAILY, frequency); assertTrue(chosen); assertEquals(LocalDate.of(2026, 10, 21), lastDaily); assertEquals(listOf(2_000L), sends) }
        mia.stop()
        assertNull(mia.load().address); assertEquals(listOf(2_000L), mia.load().sends)
        leo.forget()
        assertEquals(setOf("p1"), store.keys())
        assertNull(EmailSettings(store, "p2").load().address)
        assertFalse("the store holds no address in plain sight of a log", mia.load().toString().contains("@"))
    }
}
