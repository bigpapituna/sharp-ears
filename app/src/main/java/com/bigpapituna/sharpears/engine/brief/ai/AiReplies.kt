package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.ProfileKind

/** A reply after the checks: the [value] (null when it cannot be used) and why each dropped element went. */
data class Checked<T>(val value: T?, val dropped: List<String>)

/**
 * The guardrails on the way back (docs/PARENT-BRIEF-PLAN.md): a reply is parsed with MiniJson, every piece of prose is
 * tidied (dashes, markdown, the {name} placeholder) and checked, and a piece that breaks a rule is dropped:
 *
 * - it names a lesson, song or page title that is not in the findings ([TitleSet.forbidden]);
 * - it compares with other children or learners, or says what is usual for an age;
 * - it gives advice outside the app (teachers, books, benches, other apps, health);
 * - it is over its length limit, empty, or holds a placeholder other than {name} or a link;
 * - a recommendation's candidateIndex is not one of the findings' candidates;
 * - the headline holds a digit (numbers belong in the facts);
 * - a fact holds a number the findings do not (2.8.4, QA D4: "all 57 notes" after a 42-note run): every whole number in
 *   it must be one of the findings' own, or a day count up to seven ([SMALL]). The phone's fact at the same place
 *   ([standIns], the template writer's) takes its place when there is one;
 * - the pup's lines say anything hard, hold a symbol or a number over ten, or mention letters to a child who does not
 *   read yet.
 *
 * The brief is null when the headline, every fact or the praise is gone (the template writer then writes it whole);
 * the pup's card is null with fewer than two lines; an answer is all or nothing. The name goes in last, on the phone.
 */
internal class AiReplies(
    private val findings: Findings,
    private val titles: TitleSet,
    private val name: String,
    private val writtenAt: Long,
    /** The template writer's facts for the same findings, with the name in (asked only when a fact is dropped for a number). */
    private val standIns: () -> List<String> = { emptyList() },
) {
    private val kid = findings.profileKind == ProfileKind.KID

    fun brief(text: String): Checked<Brief> {
        val root = json(text) ?: return Checked(null, listOf("the reply is not a JSON object"))
        val dropped = ArrayList<String>()
        val headline = (root["headline"] as? String)?.let(::clean)
        (if (headline == null) "missing" else problem(headline, Prompts.HEADLINE_MAX, headline = true))?.let {
            return Checked(null, listOf("headline: $it"))
        }
        val facts = numberedFacts(texts(root["facts"], "fact", Prompts.FACT_MAX, Prompts.FACTS_MAX, dropped), dropped)
        if (facts.isEmpty()) return Checked(null, dropped + "facts: none left")
        val notes = texts(root["notes"], "note", Prompts.NOTE_MAX, Prompts.NOTES_MAX, dropped)
        val recs = recommendations(root["recommendations"], dropped)
        val praise = (root["praise"] as? String)?.let(::clean)
        (if (praise == null) "missing" else problem(praise, Prompts.PRAISE_MAX))?.let { return Checked(null, dropped + "praise: $it") }
        val sections = sections(root["sections"], dropped)
        return Checked(Brief(
            period = findings.period, headline = fill(headline!!), facts = facts.map { it.fold({ s -> s }, ::fill) }, notes = notes.map(::fill),
            recommendations = recs.map { (t, c) -> Brief.Recommendation(fill(t), c) }, praise = fill(praise!!),
            sections = sections.map { (t, b) -> Brief.Section(fill(t), fill(b)) }, writtenBy = Brief.Writer.AI, writtenAt = writtenAt,
        ), dropped)
    }

    fun kidCard(text: String): Checked<KidCard> {
        val root = json(text) ?: return Checked(null, listOf("the reply is not a JSON object"))
        val dropped = ArrayList<String>()
        val lines = ArrayList<String>()
        (root["lines"] as? List<*>).orEmpty().forEachIndexed { i, v ->
            val s = (v as? String)?.let(::clean)
            val p = if (s == null) "not text" else kidProblem(s)
            when {
                p != null -> dropped += "line ${i + 1}: $p"
                lines.size >= Prompts.KID_LINES_MAX -> dropped += "line ${i + 1}: over ${Prompts.KID_LINES_MAX}"
                else -> lines += s!!
            }
        }
        if (lines.size < 2) return Checked(null, dropped + "lines: fewer than two left")
        val next = (root["nextTry"] as? String)?.let(::clean)
        val nextProblem = when {
            next == null -> "missing"
            !next.startsWith("next time", ignoreCase = true) -> "does not start with next time"
            else -> kidProblem(next)
        }
        if (nextProblem != null) dropped += "nextTry: $nextProblem"
        return Checked(KidCard(lines.map(::fill), next?.takeIf { nextProblem == null }?.let(::fill), Brief.Writer.AI, writtenAt), dropped)
    }

    fun answer(text: String): Checked<String> {
        // Plain text was asked for; a model that wrapped it in JSON anyway gets its one string back out.
        val raw = json(text)?.values?.filterIsInstance<String>()?.singleOrNull()?.takeIf { text.trimStart().startsWith("{") } ?: text
        val s = clean(raw.lines().joinToString(" ") { stripBullet(it) }.replace(Regex("^(?i)answer:\\s*"), ""))
        val p = problem(s, Prompts.ANSWER_MAX)
        return if (p != null) Checked(null, listOf("answer: $p")) else Checked(fill(s), emptyList())
    }

    // ---- pieces ---------------------------------------------------------------------------------------------------

    private fun texts(v: Any?, what: String, max: Int, count: Int, dropped: MutableList<String>): List<String> {
        val out = ArrayList<String>()
        (v as? List<*>).orEmpty().forEachIndexed { i, x ->
            val s = (x as? String)?.let(::clean)
            val p = if (s == null) "not text" else problem(s, max)
            when {
                p != null -> dropped += "$what ${i + 1}: $p"
                out.size >= count -> dropped += "$what ${i + 1}: over $count"
                else -> out += s!!
            }
        }
        return out
    }

    /**
     * [facts] with any fact whose numbers are not the findings' replaced by the phone's fact at its place (the phone's words
     * come with the name in; the AI's are filled later), or dropped when the phone has none to spare.
     */
    private fun numberedFacts(facts: List<String>, dropped: MutableList<String>): List<FactText> {
        val out = ArrayList<FactText>()
        val phone by lazy { standIns() }
        facts.forEachIndexed { i, s ->
            val wrong = strangeNumbers(s)
            if (wrong.isEmpty()) { out += FactText.Ai(s); return@forEachIndexed }
            val stand = (listOfNotNull(phone.getOrNull(i)) + phone).firstOrNull { p -> out.none { it.text == p } }
            dropped += "fact ${i + 1}: ${if (wrong.size == 1) "a number" else "numbers"} not in the findings (${wrong.joinToString(", ")})" +
                (if (stand != null) ", the phone's fact stands in" else "")
            if (stand != null) out += FactText.Phone(stand)
        }
        return out
    }

    /** The whole numbers in [s] (titles and {name} aside) that are neither in the findings nor a day count. */
    internal fun strangeNumbers(s: String): List<Long> =
        numbersIn(withoutTitles(s).replace(Prompts.NAME, " ")).filter { it !in SMALL && it !in known }.distinct()

    /** Every whole number the findings hold, as the model saw them (counts, minutes, percentages, days, ids). */
    private val known: Set<Long> by lazy { numbersIn(findings.encode()).toSet() }

    /** A fact's words: the AI's ({name} filled in later) or the phone's (written whole, name and all). */
    private sealed interface FactText {
        val text: String
        data class Ai(override val text: String) : FactText
        data class Phone(override val text: String) : FactText
        fun fold(phone: (String) -> String, ai: (String) -> String) = when (this) { is Phone -> phone(text); is Ai -> ai(text) }
    }

    private fun recommendations(v: Any?, dropped: MutableList<String>): List<Pair<String, Candidate>> {
        val out = ArrayList<Pair<String, Candidate>>()
        val used = HashSet<Int>()
        (v as? List<*>).orEmpty().forEachIndexed { i, x ->
            val m = x as? Map<*, *>
            val s = (m?.get("text") as? String)?.let(::clean)
            val idx = index(m?.get("candidateIndex"))
            val p = when {
                m == null || s == null -> "not a recommendation"
                idx == null || idx !in findings.candidates.indices -> "candidateIndex ${m["candidateIndex"]} is not a candidate"
                idx in used -> "candidate $idx twice"
                out.size >= Prompts.RECS_MAX -> "over ${Prompts.RECS_MAX}"
                else -> problem(s, Prompts.REC_MAX)
            }
            if (p != null) dropped += "recommendation ${i + 1}: $p"
            else { used += idx!!; out += s!! to findings.candidates[idx] }
        }
        return out
    }

    private fun sections(v: Any?, dropped: MutableList<String>): List<Pair<String, String>> {
        val list = (v as? List<*>).orEmpty()
        if (findings.period == Period.SESSION) { if (list.isNotEmpty()) dropped += "sections: none in a session note"; return emptyList() }
        val out = ArrayList<Pair<String, String>>()
        list.forEachIndexed { i, x ->
            val m = x as? Map<*, *>
            val t = (m?.get("title") as? String)?.let(::clean)
            val b = (m?.get("body") as? String)?.let(::clean)
            val p = when {
                t == null || b == null -> "not a section"
                out.size >= Prompts.SECTIONS_MAX -> "over ${Prompts.SECTIONS_MAX}"
                else -> problem(t, Prompts.SECTION_TITLE_MAX) ?: problem(b, Prompts.SECTION_BODY_MAX)
            }
            if (p != null) dropped += "section ${i + 1}: $p" else out += t!! to b!!
        }
        return out
    }

    private fun index(v: Any?): Int? = when (v) {
        is Long -> v.toInt().takeIf { it.toLong() == v }
        is Double -> v.toInt().takeIf { it.toDouble() == v }
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    // ---- the rules ------------------------------------------------------------------------------------------------

    /**
     * Why [s] may not be shown to a grown-up, or null when it may. The word rules look at [s] with the allowed titles
     * blanked: a title may hold a digit ("Gymnopedie No. 1"), a bracket or a word the rules watch for.
     */
    private fun problem(s: String, max: Int, headline: Boolean = false): String? {
        val bare = withoutTitles(s)
        return when {
            s.isBlank() -> "empty"
            s.length > max -> "longer than $max characters"
            PLACEHOLDER.containsMatchIn(bare.replace(Prompts.NAME, "")) -> "a placeholder other than ${Prompts.NAME}"
            LINK.containsMatchIn(bare) -> "a link"
            headline && bare.any(Char::isDigit) -> "a number in the headline"
            titles.forbiddenPatterns.any { it.containsMatchIn(bare) } -> "names a title not in the findings"
            COMPARE.containsMatchIn(bare) -> "compares with others"
            OFF_APP.containsMatchIn(bare.replace(TEACHER_MODE, "")) -> "advice outside the app"
            else -> null
        }
    }

    /** Why [s] may not be one of the pup's lines, or null when it may. */
    private fun kidProblem(s: String): String? {
        problem(s, Prompts.KID_LINE_MAX)?.let { return it }
        val bare = withoutTitles(s).replace(Prompts.NAME, " ")
        return when {
            NEGATIVE.containsMatchIn(bare) -> "not a positive"
            Regex("\\d+").findAll(bare).any { (it.value.toLongOrNull() ?: Long.MAX_VALUE) > 10 } -> "a number over ten"
            SYMBOL.containsMatchIn(bare) -> "a symbol"
            !findings.reader && LETTERS.containsMatchIn(bare) -> "letters for a child who does not read yet"
            else -> null
        }
    }

    /** [s] with every allowed title blanked, longest first, so a title holding another ("Left paw walks") is not caught. */
    private fun withoutTitles(s: String): String = titles.allowedPatterns.fold(s) { t, r -> r.replace(t, " ") }

    /** The person's name goes in on the phone; the model only ever saw {name}. */
    private fun fill(s: String): String = s.replace(Prompts.NAME, name.trim().ifEmpty { if (kid) "your child" else "you" })

    companion object {
        private val PLACEHOLDER = Regex("[{}<>\\[\\]]")
        private val LINK = Regex("(?i)https?://|www\\.")
        private val TEACHER_MODE = Regex("(?i)teacher mode")
        private fun words(vararg w: String) = Regex("(?i)(?<![\\p{L}\\p{N}])(" + w.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])")

        private val COMPARE = words(
            "other children", "other kids", "other learners", "other players", "other students", "other pianists",
            "most children", "most kids", "most learners", "most people", "kids his age", "kids her age", "kids their age",
            "children his age", "children her age", "children their age", "for his age", "for her age", "for their age",
            "for your age", "at this age", "at that age", "average child", "average kid", "classmates", "classmate",
            "siblings", "sibling", "brother", "brothers", "sister", "sisters",
        )
        private val OFF_APP = words(
            "teacher", "teachers", "tutor", "tutors", "book", "books", "bench", "benches", "stool", "youtube", "website",
            "websites", "doctor", "therapist", "paediatrician", "pediatrician", "another app", "other apps", "a class", "classes",
        )
        private val NEGATIVE = words(
            "hard", "harder", "hardest", "tricky", "trickier", "difficult", "wrong", "mistake", "mistakes", "miss", "missed",
            "misses", "missing", "stuck", "struggle", "struggled", "struggling", "fail", "failed", "bad", "sad", "sorry", "oops",
            "worse", "worst", "behind", "problem", "problems", "trouble",
        )
        private val SYMBOL = Regex("[^\\p{L}\\p{N}\\s.,!?'\u2019:;-]")
        private val LETTERS = words("letter", "letters")

        /** Numbers a fact may hold without the findings: a count of days in a week (none is a number the model makes up). */
        val SMALL: Set<Long> = (0L..7L).toSet()
        private val THOUSANDS = Regex("([0-9]),([0-9]{3})(?![0-9])")
        private val DIGITS = Regex("[0-9]+")

        /** The whole numbers in [s]: each run of digits ("1,200" read as 1200; "1.5" as 1 and 5). */
        fun numbersIn(s: String): List<Long> {
            var t = s
            while (THOUSANDS.containsMatchIn(t)) t = THOUSANDS.replace(t, "$1$2")
            return DIGITS.findAll(t).mapNotNull { it.value.trimStart('0').ifEmpty { "0" }.toLongOrNull() }.toList()
        }

        private val DASHED_RANGE = Regex("(\\d)\\s*[\u2013\u2014]\\s*(\\d)")
        private val DASH = Regex("\\s*[\u2013\u2014]+\\s*")
        private val NAME_VARIANTS = Regex("(?i)\\{\\s*name\\s*\\}")
        private val MARKDOWN = Regex("\\*\\*|__|`")

        /** One line of prose, tidied: {name} spelled one way, no markdown or bullet, no em or en dash, spaces collapsed. */
        fun clean(raw: String): String {
            var s = raw.replace(NAME_VARIANTS, Prompts.NAME).replace(MARKDOWN, "")
            s = s.lines().joinToString(" ") { stripBullet(it) }
            s = DASHED_RANGE.replace(s, "$1 to $2")
            s = DASH.replace(s, ", ")
            s = s.replace(Regex("\\s+"), " ").replace(Regex(",\\s*,"), ",").replace(Regex("\\s+([,.!?;:])"), "$1").trim()
            s = s.removePrefix(", ").trim()
            if (s.length >= 2 && s.first() in "\"\u201c" && s.last() in "\"\u201d") s = s.substring(1, s.length - 1).trim()
            return s
        }

        private fun stripBullet(line: String) = line.trim().replace(Regex("^(#+|[-*\u2022]|\\d+[.)])\\s+"), "")

        /** The outermost JSON object in [text] (a model may wrap it in a code fence), or null. */
        fun json(text: String): Map<*, *>? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { MiniJson.read(text.substring(start, end + 1)) as? Map<*, *> }.getOrNull()
        }
    }
}
