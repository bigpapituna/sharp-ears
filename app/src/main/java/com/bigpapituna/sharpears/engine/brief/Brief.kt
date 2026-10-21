package com.bigpapituna.sharpears.engine.brief

/**
 * What a writer produces from [Findings]: the parent brief (docs/PARENT-BRIEF-PLAN.md) in the fixed structure the
 * screens render. Every writer (the AI one, the template one) returns this; the screens never see prose from anywhere
 * else. Prose only; ids live in the [Findings] the brief was written from, which the screen keeps beside it.
 *
 * Writing rules every writer follows: warm, kind, optimistic, coach-like, factual; only the findings as facts; the
 * person compared with their own past only; nothing about other children; no advice outside the app; every "hard"
 * paired with why it is normal and a next step; numbers present but never the headline; sentence case; no em or en
 * dashes; the name used naturally, not in every sentence.
 */
data class Brief(
    val period: Period,
    /** One sentence a tired parent reads in three seconds. */
    val headline: String,
    /** Two to four short sentences: what happened. In importance order. */
    val facts: List<String>,
    /** Where it went well and where it was hard, each with why that is normal. Empty when nothing was hard. */
    val notes: List<String>,
    /** Two or three, each tied to a candidate from the findings (the screen draws the action button from it). */
    val recommendations: List<Recommendation>,
    /** One specific thing to praise at home. */
    val praise: String,
    /** The full report's extra sections (skills in words, habits), each a short paragraph. Empty for SESSION. */
    val sections: List<Section> = emptyList(),
    val writtenBy: Writer,
    /** Epoch ms when it was written; the screen shows "Refresh" against it. */
    val writtenAt: Long,
) {
    data class Recommendation(val text: String, val candidate: Findings.Candidate)
    data class Section(val title: String, val body: String)
    enum class Writer { AI, TEMPLATE }
}

/** The child's own card: first person from the pup, positives and one "next time let's try". Spoken aloud and shown. */
data class KidCard(val lines: List<String>, val nextTry: String?, val writtenBy: Brief.Writer, val writtenAt: Long)

/**
 * A writer turns findings into prose. [name] is the person's first name (inserted on the phone; it never reaches a
 * model). A writer returns null when it cannot produce the brief (offline, slow, over quota, a malformed reply), and
 * the caller falls back to the next writer; the template writer never returns null.
 */
interface BriefWriter {
    fun write(findings: Findings, name: String): Brief?
    fun kidCard(findings: Findings, name: String): KidCard?
    /** A parent's typed or dictated question answered from the same findings; null when this writer cannot. */
    fun answer(findings: Findings, name: String, question: String): String?
}
