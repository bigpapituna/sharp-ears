package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The chain asks each writer in turn; the first that writes wins, and the result says who wrote it. */
class WriterChainTest {
    private val logs = ArrayList<String>()

    /** A stand-in for the template writer (brief2b): always writes, marked as written on the phone. */
    private class Phone : BriefWriter {
        var asked = 0
        override fun write(findings: Findings, name: String): Brief { asked++; return Brief(findings.period, "On the phone.", listOf("A fact."),
            emptyList(), emptyList(), "Praise.", writtenBy = Brief.Writer.TEMPLATE, writtenAt = 1) }
        override fun kidCard(findings: Findings, name: String) = KidCard(listOf("Woof!", "Well played!"), null, Brief.Writer.TEMPLATE, 1)
        override fun answer(findings: Findings, name: String, question: String) = "From the phone."
    }

    private class Silent : BriefWriter {
        override fun write(findings: Findings, name: String): Brief? = null
        override fun kidCard(findings: Findings, name: String): KidCard? = null
        override fun answer(findings: Findings, name: String, question: String): String? = null
    }

    private class Broken : BriefWriter {
        override fun write(findings: Findings, name: String): Brief = throw IllegalStateException("oops with a secret")
        override fun kidCard(findings: Findings, name: String): KidCard = throw IllegalStateException("oops")
        override fun answer(findings: Findings, name: String, question: String): String = throw IllegalStateException("oops")
    }

    private fun ai(t: AiTransport, store: MemoryBudgetStore = MemoryBudgetStore()) =
        AiWriter(t, "m", { AiFixtures.NOW }, AiLimits(), Budget(store, zone = ZoneId.of("America/Toronto")), "p2", AiFixtures.TITLES)

    @Test fun theFirstWriterThatWritesWinsAndSaysWhoItWas() {
        val f = AiFixtures.week()
        val withAi = WriterChain(listOf(ai(FakeTransport { AiFixtures.PERFECT_WEEK }), Phone()))
        assertEquals(Brief.Writer.AI, withAi.write(f, "Mia")!!.writtenBy)
        val offline = WriterChain(listOf(ai(FakeTransport { throw AiCallException(AiCallException.Kind.NETWORK, null, "offline") }), Phone()))
        assertEquals(Brief.Writer.TEMPLATE, offline.write(f, "Mia")!!.writtenBy)
        assertEquals(Brief.Writer.TEMPLATE, offline.kidCard(f, "Mia")!!.writtenBy)
        assertEquals(WriterChain.Answer("From the phone.", Brief.Writer.TEMPLATE), offline.ask(f, "Mia", "How?"))
        assertEquals("From the phone.", offline.answer(f, "Mia", "How?"))
        val answered = WriterChain(listOf(ai(FakeTransport { "Ta and ti-ti is still settling." }), Phone())).ask(f, "Mia", "How?")
        assertEquals(WriterChain.Answer("Ta and ti-ti is still settling.", Brief.Writer.AI), answered)
    }

    @Test fun aWriterThatReturnsNothingOrThrowsIsSkipped() {
        val f = AiFixtures.week()
        val chain = WriterChain(listOf(Silent(), Broken(), Phone()), log = { logs += it })
        assertEquals("On the phone.", chain.write(f, "Mia")!!.headline)
        assertEquals(listOf("Woof!", "Well played!"), chain.kidCard(f, "Mia")!!.lines)
        assertEquals("From the phone.", chain.answer(f, "Mia", "How?"))
        assertTrue(logs.all { it.startsWith("brief: Broken failed (IllegalStateException)") })
        assertTrue(logs.none { "secret" in it })
        assertNull(WriterChain(listOf(Silent())).write(f, "Mia"))
        assertNull(WriterChain(emptyList()).write(f, "Mia"))
    }

    @Test fun refreshWritesPastTheCacheAndFallsToTheNextWriterWhenItCannot() {
        val f = AiFixtures.week()
        val store = MemoryBudgetStore()
        val t = FakeTransport { AiFixtures.PERFECT_WEEK }
        val phone = Phone()
        val chain = WriterChain(listOf(ai(t, store), phone))
        chain.write(f, "Mia")
        chain.write(f, "Mia")
        assertEquals("the second read the cache", 1, t.requests.size)
        assertEquals(Brief.Writer.AI, chain.refresh(f, "Mia")!!.writtenBy)
        assertEquals(2, t.requests.size)
        t.reply = { throw AiCallException(AiCallException.Kind.NETWORK, null, "offline") }
        assertEquals("a failed refresh does not show the old words as new", Brief.Writer.TEMPLATE, chain.refresh(f, "Mia")!!.writtenBy)
        assertEquals(1, phone.asked)
    }
}
