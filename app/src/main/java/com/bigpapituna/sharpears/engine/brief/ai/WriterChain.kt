package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard

/**
 * The writers in order (docs/PARENT-BRIEF-PLAN.md, "three writers, chosen automatically"): each is asked in turn and the
 * first that writes wins. The integrator builds `WriterChain(listOf(aiWriter, templateWriter))`; the template writer
 * never returns null, so with it last the chain always writes. Who wrote it is on the result: [Brief.writtenBy] and
 * [KidCard.writtenBy] ("written by AI" or "written on the phone"), and [Answer.writtenBy] for [ask].
 *
 * A writer that throws is skipped like one that returned null (only the exception's type is logged), so a fault in one
 * writer can never cost the parent their brief.
 */
class WriterChain(private val writers: List<BriefWriter>, private val log: (String) -> Unit = {}) : BriefWriter {

    /** An answer and who wrote it. */
    data class Answer(val text: String, val writtenBy: Brief.Writer)

    override fun write(findings: Findings, name: String): Brief? = first { it.write(findings, name) }

    override fun kidCard(findings: Findings, name: String): KidCard? = first { it.kidCard(findings, name) }

    override fun answer(findings: Findings, name: String, question: String): String? = ask(findings, name, question)?.text

    /** [answer] with who wrote it: an [AiWriter] is AI, every other writer is the phone's own. */
    fun ask(findings: Findings, name: String, question: String): Answer? {
        for (w in writers) {
            val text = safely(w) { w.answer(findings, name, question) } ?: continue
            return Answer(text, if (w is AiWriter) Brief.Writer.AI else Brief.Writer.TEMPLATE)
        }
        return null
    }

    /**
     * The brief's Refresh: a writer that keeps a cache ([Refreshable], the AI writer) writes again past it, and when it
     * cannot, the next writer writes (the old cached words are not shown again as if they were new).
     */
    fun refresh(findings: Findings, name: String): Brief? =
        first { w -> if (w is Refreshable) w.refresh(findings, name) else w.write(findings, name) }

    private fun <T> first(block: (BriefWriter) -> T?): T? {
        for (w in writers) safely(w) { block(w) }?.let { return it }
        return null
    }

    private fun <T> safely(w: BriefWriter, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        log("brief: ${w.javaClass.simpleName} failed (${e.javaClass.simpleName}); trying the next writer")
        null
    }
}
