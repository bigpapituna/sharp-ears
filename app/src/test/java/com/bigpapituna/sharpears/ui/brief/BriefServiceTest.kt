package com.bigpapituna.sharpears.ui.brief

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Effects
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.IdTitles
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.Undo
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.ai.Refreshable
import com.bigpapituna.sharpears.engine.brief.ai.WriterChain
import com.bigpapituna.sharpears.engine.family.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * The writers behind the brief screens (2.4 integration): the AI only while it is on, the phone's words past the limits, and
 * the day and the week showing the phone's brief at once with the AI's swapped in when it comes.
 */
class BriefServiceTest {
    private val zone = ZoneId.of("America/Toronto")
    private val now = 1_792_000_000_000L
    private val mia = Profile.kid("Mia", 3, reader = false).copy(id = "p1")

    /** A writer that says who it is, counts its calls and can take its time. */
    private class Counting(val by: Brief.Writer, val delayMs: Long = 0) : BriefWriter, Refreshable {
        val writes = AtomicInteger(); val refreshes = AtomicInteger(); val cards = AtomicInteger()
        private val inner = FakeWriter(IdTitles, by)
        override fun write(findings: Findings, name: String): Brief { writes.incrementAndGet(); Thread.sleep(delayMs); return inner.write(findings, name) }
        override fun refresh(findings: Findings, name: String): Brief { refreshes.incrementAndGet(); Thread.sleep(delayMs); return inner.write(findings, name) }
        override fun kidCard(findings: Findings, name: String): KidCard { cards.incrementAndGet(); Thread.sleep(delayMs); return inner.kidCard(findings, name) }
        override fun answer(findings: Findings, name: String, question: String) = "${by.name} answer"
    }

    private fun week() = BriefSamples.kid(Period.WEEK, now, zone)

    @Test fun withTheAiOffEveryCallGoesToThePhoneAndTheChainIsNeverAsked() = runBlocking {
        val ai = Counting(Brief.Writer.AI); val phone = Counting(Brief.Writer.TEMPLATE)
        val s = BriefService(chain = { ai }, phone = { phone }, aiOn = { false })
        assertEquals(Brief.Writer.TEMPLATE, s.write(week(), "Mia")!!.writtenBy)
        assertEquals("TEMPLATE answer", s.answer(week(), "Mia", "How did it go?"))
        assertNotNull(s.kidCard(BriefSamples.kid(Period.SESSION, now, zone), "Mia"))
        assertFalse(s.aiMayWrite(week()))
        assertEquals(0, ai.writes.get() + ai.cards.get())
    }

    @Test fun withTheAiOnTheChainWritesAndTheDayAndWeekMayImprove() = runBlocking {
        val ai = Counting(Brief.Writer.AI); val phone = Counting(Brief.Writer.TEMPLATE)
        val s = BriefService(chain = { ai }, phone = { phone }, aiOn = { true })
        assertEquals(Brief.Writer.AI, s.write(week(), "Mia")!!.writtenBy)
        assertTrue(s.aiMayWrite(week()))
        assertFalse("nothing happened: nothing to ask the AI", s.aiMayWrite(BriefSamples.empty(Period.WEEK, now, zone)))
        assertEquals(Brief.Writer.TEMPLATE, s.phone(week(), "Mia")!!.writtenBy)
    }

    @Test fun theSessionNoteAndTheCardFallToThePhoneAfterTheirLimit() = runBlocking {
        val slow = Counting(Brief.Writer.AI, delayMs = 2_000); val phone = Counting(Brief.Writer.TEMPLATE)
        val s = BriefService(chain = { slow }, phone = { phone }, limits = BriefLimits(sessionMs = 150, kidCardMs = 150), aiOn = { true })
        val t0 = System.nanoTime()
        val note = s.write(BriefSamples.kid(Period.SESSION, now, zone), "Mia")!!
        val card = s.kidCard(BriefSamples.kid(Period.SESSION, now, zone), "Mia")!!
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(Brief.Writer.TEMPLATE, note.writtenBy)
        assertEquals(Brief.Writer.TEMPLATE, card.writtenBy)
        assertTrue("both within their limits, took $ms ms", ms < 1_500)
    }

    @Test fun refreshGoesPastTheAiCacheThroughTheChain() = runBlocking {
        val ai = Counting(Brief.Writer.AI); val phone = Counting(Brief.Writer.TEMPLATE)
        val chain = WriterChain(listOf(ai, phone))
        val s = BriefService(chain = { chain }, phone = { phone }, aiOn = { true })
        assertEquals(Brief.Writer.AI, s.refresh(week(), "Mia")!!.writtenBy)
        assertEquals(1, ai.refreshes.get())
        assertEquals(0, ai.writes.get())
    }

    @Test fun theDemoStandInWritesAloneWithoutAPhoneWriter() = runBlocking {
        val only = Counting(Brief.Writer.AI)
        val s = BriefService(chain = { only })
        assertEquals(Brief.Writer.AI, s.write(week(), "Mia")!!.writtenBy)
        assertFalse(s.aiMayWrite(week()))
    }

    // ---- the page: the phone's brief at once, the AI's when it comes ----

    private fun env(service: BriefService) = BriefEnv(
        titles = IdTitles,
        serviceFor = { service },
        loadFindings = { _, period, t -> BriefSamples.kid(period, t, zone) },
        effectsFor = { NoEffects },
        record = { _, _, _ -> },
        weekly = object : WeeklyAccess {
            override fun get(profile: Profile) = WeeklySlot.DEFAULT
            override fun set(profile: Profile, slot: WeeklySlot) {}
        },
        now = { now }, zone = zone,
    )

    private object NoEffects : Effects {
        override fun setNext(lessonId: String?, untilMs: Long): Undo? = null
        override fun replay(lessonId: String) {}
        override fun practiseTogether(lessonId: String?, songId: String?) {}
        override fun freshStart(pageId: String): Undo? = null
        override fun focus(pageId: String): Undo? = null
        override fun unlockNext(pageId: String): Undo? = null
        override fun changeSetting(setting: SettingId, value: String): Undo? = null
        override fun assignSong(songId: String, note: String, afterSongId: String?): Undo? = null
    }

    /** Polls [what] until [until] holds, for up to [ms]: the page's work runs on other threads. */
    private fun <T> waitFor(ms: Long, what: () -> T, until: (T) -> Boolean): T {
        val end = System.currentTimeMillis() + ms
        while (true) { val v = what(); if (until(v) || System.currentTimeMillis() > end) return v; Thread.sleep(10) }
    }

    @Test fun theWeekShowsThePhonesBriefFirstAndSwapsInTheAis() {
        val ai = Counting(Brief.Writer.AI, delayMs = 900); val phone = Counting(Brief.Writer.TEMPLATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = BriefController(env(BriefService(chain = { ai }, phone = { phone }, aiOn = { true })), scope)
            val page = c.page(mia, Period.WEEK)
            val first = waitFor(3_000, { page.brief }) { it != null }
            assertEquals("the phone's words first", Brief.Writer.TEMPLATE, first!!.writtenBy)
            assertTrue("while the AI writes", waitFor(500, { page.improving }) { it })
            val later = waitFor(5_000, { page.brief }) { it?.writtenBy == Brief.Writer.AI }
            assertEquals("then the AI's", Brief.Writer.AI, later!!.writtenBy)
            assertFalse(waitFor(500, { page.improving }) { !it })
            assertEquals(Phase.READY, page.phase)
        } finally { scope.cancel() }
    }

    @Test fun aQuickAiReplyAsFromTheCacheShowsWithoutThePhonesFirst() {
        val ai = Counting(Brief.Writer.AI); val phone = Counting(Brief.Writer.TEMPLATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = BriefController(env(BriefService(chain = { ai }, phone = { phone }, aiOn = { true })), scope)
            val page = c.page(mia, Period.DAY)
            val b = waitFor(3_000, { page.brief }) { it != null }
            assertEquals(Brief.Writer.AI, b!!.writtenBy)
            assertEquals("the phone was never asked", 0, phone.writes.get())
        } finally { scope.cancel() }
    }

    @Test fun theSessionNoteWaitsForTheChainAndNeverSwaps() {
        val ai = Counting(Brief.Writer.AI, delayMs = 300); val phone = Counting(Brief.Writer.TEMPLATE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = BriefController(env(BriefService(chain = { ai }, phone = { phone }, aiOn = { true })), scope)
            val page = c.page(mia, Period.SESSION)
            val b = waitFor(3_000, { page.brief }) { it != null }
            assertEquals(Brief.Writer.AI, b!!.writtenBy)
            assertEquals(0, phone.writes.get())
        } finally { scope.cancel() }
    }

    @Test fun anAiBriefSaysWhatTheAiWasGivenUnderIt() {
        assertTrue(footerLine(kid = true, name = "Mia", by = Brief.Writer.AI).contains("never gets a name"))
        assertTrue(footerLine(kid = true, name = "Mia", by = Brief.Writer.TEMPLATE).startsWith("Made on this phone"))
        assertFalse(footerLine(kid = false, name = "Roy", by = Brief.Writer.AI).contains("Roy"))
    }
}
