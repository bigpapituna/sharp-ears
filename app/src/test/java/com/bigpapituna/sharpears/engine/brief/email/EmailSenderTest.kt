package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.IdTitles
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The sender: refuses before writing anything when an email must not go, writes with the chain, attaches the PDF, renders
 * (falling back to plain), sends, records; and never logs the address, the name or the subject.
 */
class EmailSenderTest {
    private val sent = ArrayList<EmailMessage>()
    private val logs = ArrayList<String>()
    private val day = LocalDate.of(2026, 10, 21)
    private val week = BriefSamples.kid(Period.WEEK, NOW, ZONE)
    private val templates = TemplateWriter({ id -> IdTitles.lesson(id) }, seed = 1, clock = { NOW })
    private val child = EmailSender.Child("p1", "Mia", 1, IdTitles)
    private val verified = EmailPrefs("parent@example.com", verified = true, frequency = EmailFrequency.BOTH, chosen = true)
    private val pdfBytes = "%PDF-1.7 fake".toByteArray()

    /** A writer that counts its calls (the AI writer in the chain costs a call). */
    private inner class Counting(private val inner: BriefWriter = templates) : BriefWriter {
        var calls = 0
        override fun write(findings: Findings, name: String): Brief? { calls++; return inner.write(findings, name) }
        override fun kidCard(findings: Findings, name: String): KidCard? = null
        override fun answer(findings: Findings, name: String, question: String): String? = null
    }

    private fun sender(hasKey: Boolean = true, allowed: Boolean = true, render: EmailRenderer = PlainTextEmail.renderer,
                       pdf: (Findings, Brief) -> ByteArray? = { _, _ -> pdfBytes }, fail: EmailSendException? = null) =
        EmailSender(transport = { m -> if (fail != null) throw fail; sent += m; "<id>" }, hasKey = { hasKey }, networkAllowed = { allowed },
            pdf = pdf, render = render, clock = { NOW }, zone = ZONE, log = { logs += it })

    private val weekly = EmailDue.Send(EmailKind.WEEKLY, day, Period.WEEK, NOW)

    @Test fun aWeeklyIsWrittenRenderedAttachedSentAndRecorded() {
        val writer = Counting()
        val out = sender().send(weekly, verified, week, writer, child) as EmailSender.Outcome.Sent
        val m = sent.single()
        val email = out.email!!
        assertEquals("parent@example.com", m.to)
        assertEquals(email.subject, m.subject)
        assertEquals(PlainTextEmail.html(email), m.html); assertEquals(PlainTextEmail.text(email), m.text)
        assertArrayEquals(pdfBytes, m.attachment); assertEquals("Mia-weekly-brief-2026-10-21.pdf", m.attachmentName)
        assertEquals(day, out.prefs.lastWeekly); assertEquals(listOf(NOW), out.prefs.sends)
        assertEquals(1, writer.calls)
        assertEquals("WEEK ${EmailBriefs.weekNumber(NOW, ZONE)}", email.postcard.stamp)
        assertTrue(email.footer.lines.any { it.startsWith("The full report is attached") })
        logs.forEach { assertFalse(it, it.contains("parent@") || it.contains("Mia") || it.contains(m.subject)) }
    }

    @Test fun anEmailThatMustNotGoIsRefusedBeforeAnythingIsWritten() {
        val writer = Counting()
        fun refused(s: EmailSender, due: EmailDue.Send, p: EmailPrefs) = (s.send(due, p, week, writer, child) as EmailSender.Outcome.Refused).why
        assertEquals(EmailSender.Refusal.NO_KEY, refused(sender(hasKey = false), weekly, verified))
        assertEquals(EmailSender.Refusal.METERED, refused(sender(allowed = false), weekly, verified))
        assertEquals(EmailSender.Refusal.UNVERIFIED, refused(sender(), weekly, verified.copy(verified = false)))
        assertEquals(EmailSender.Refusal.NO_ADDRESS, refused(sender(), weekly, EmailPrefs()))
        assertEquals(EmailSender.Refusal.OFF, refused(sender(), weekly, verified.copy(frequency = EmailFrequency.DAILY)))
        assertEquals(EmailSender.Refusal.OFF, refused(sender(), weekly.copy(kind = EmailKind.DAILY), verified.copy(frequency = EmailFrequency.WEEKLY)))
        assertEquals(EmailSender.Refusal.CAPPED, refused(sender(), weekly, verified.copy(sends = listOf(NOW - 60_000, NOW - 30_000))))
        assertEquals(EmailSender.Refusal.NO_CODE, refused(sender(), weekly.copy(kind = EmailKind.FIRST), EmailPrefs("parent@example.com")))
        assertEquals("nothing written for an email that cannot go", 0, writer.calls)
        assertTrue(sent.isEmpty())
    }

    /**
     * 2.10.1 (security review finding 6): the first email goes before the address is confirmed, so it is the code alone. Nothing
     * is written for it (the AI is not asked), no PDF is made, and the child's name is nowhere in it.
     */
    @Test fun theFirstEmailGoesUnverifiedWithTheCodeAloneAndMarksItselfSent() {
        val pending = EmailVerification(clock = { NOW }, digits = { "482913" }).start(EmailPrefs(), "parent@example.com").first
        val first = EmailDue.Send(EmailKind.FIRST, day, Period.DAY, NOW)
        val writer = Counting()
        var pdfs = 0
        val out = sender(pdf = { _, _ -> pdfs++; pdfBytes }).send(first, pending, BriefSamples.kid(Period.DAY, NOW, ZONE), writer, child) as EmailSender.Outcome.Sent
        val m = sent.single()
        assertEquals("Your Sharp Ears code is 482913", m.subject)
        assertEquals(EmailHtml.code(CodeEmail("482913")), m.html to m.text)
        assertTrue(m.text.contains("Your code: 482913") && m.text.contains("Your first brief comes as soon as you confirm."))
        assertNull("nothing attached", m.attachment); assertNull(m.attachmentName)
        assertEquals("nothing written", 0, writer.calls); assertEquals("no PDF", 0, pdfs)
        assertFalse("no name", m.subject.contains("Mia") || m.html.contains("Mia") || m.text.contains("Mia"))
        assertNull("no brief", out.email)
        assertTrue(out.prefs.firstSent)
        assertEquals("the code email covers no day", null, out.prefs.lastDaily)
        assertFalse(out.prefs.plan(com.bigpapituna.sharpears.engine.brief.WeeklySlot.DEFAULT).firstPending)
        assertTrue(logs.any { it.contains("FIRST") && it.contains("the code alone") })
        // The job's own door says the same.
        sent.clear()
        assertTrue(sender().sendCode(first, pending, "p1") is EmailSender.Outcome.Sent)
        assertEquals("Your Sharp Ears code is 482913", sent.single().subject)
    }

    @Test fun aFailingRendererOrNoPdfStillSendsAndAFailedSendRecordsNothing() {
        val broken = EmailRenderer { throw IllegalStateException("boom") }
        val out = sender(render = broken, pdf = { _, _ -> null }).send(weekly, verified, week, Counting(), child) as EmailSender.Outcome.Sent
        assertEquals(PlainTextEmail.html(out.email!!), sent.single().html)
        assertNull(sent.single().attachment); assertNull(sent.single().attachmentName)
        assertTrue("no PDF, so the footer does not promise one", out.email!!.footer.lines.none { it.startsWith("The full report is attached") })
        val blankText = sender(render = { e -> "<p>designed</p>" to "" }).send(weekly, verified, week, Counting(), child) as EmailSender.Outcome.Sent
        assertEquals(PlainTextEmail.text(blankText.email!!), sent.last().text)
        assertEquals("<p>designed</p>", sent.last().html)
        val failed = sender(fail = EmailSendException(EmailSendException.Kind.SERVER, 503, "HTTP 503")).send(weekly, verified, week, Counting(), child)
        assertEquals(EmailSender.Outcome.Failed(EmailSendException.Kind.SERVER, "HTTP 503"), failed)
        assertTrue(logs.any { it.contains("failed: SERVER") })
    }
}
