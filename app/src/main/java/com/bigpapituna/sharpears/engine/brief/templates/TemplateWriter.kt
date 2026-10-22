package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES

/**
 * The built-in writer: prose from a line bank, on the phone, with no network, instantly. It never returns null.
 *
 * [titles] turns a lesson, song or page id into its title (blank for unknown; a line that needs a title then reads
 * without it). [CourseTitles.lookup] builds the usual one. [seed] makes a family's briefs read differently from another
 * family's (any stable number, such as a hash of the profile id); the same findings, week and seed always give the same
 * words, so Refresh does not reshuffle, and the next week picks other phrasings. [clock] stamps `writtenAt`.
 */
class TemplateWriter(
    private val titles: (String) -> String,
    private val seed: Long = 0L,
    private val clock: () -> Long = System::currentTimeMillis,
) : BriefWriter {

    override fun write(findings: Findings, name: String): Brief = runCatching { compose(findings, name) }.getOrElse { fallback(findings) }

    override fun kidCard(findings: Findings, name: String): KidCard =
        runCatching { kidCard(Ctx(findings, name, titles, seed), clock()) }.getOrElse {
            KidCard(listOf("You are doing so well!", "I love hearing you play!"), "Next time, let's play together again!", Brief.Writer.TEMPLATE, clock())
        }

    override fun answer(findings: Findings, name: String, question: String): String = runCatching {
        val c = Ctx(findings, name, titles, seed)
        answer(c, question) { headline(c) }
    }.getOrElse { "I can only answer about what the app saw this week." }

    private fun compose(findings: Findings, name: String): Brief {
        val c = Ctx(findings, name, titles, seed)
        val f = findings

        val facts = ArrayList<String>()
        val shown = HashSet<com.bigpapituna.sharpears.engine.brief.FactKind>()
        val usedTrends = HashSet<com.bigpapituna.sharpears.engine.brief.TrendKind>()
        val better = f.trends.filter { it.direction != Direction.STEADY && it.good == true }
        for (fact in f.facts) {
            if (facts.size >= 3) break
            // Visits with no practice minutes (the app opened, a look around, the toys) are not "the piano came out" times.
            if (fact.kind == com.bigpapituna.sharpears.engine.brief.FactKind.SESSIONS && f.stats.totals.practiceMinutes == 0) continue
            // The first trend that says more about the same number ("six days, up from two") stands in for its plain fact.
            val matching = if (usedTrends.isNotEmpty()) null else TREND_OF[fact.kind]?.let { k -> better.firstOrNull { it.kind == k } }
            val viaTrend = matching?.let { trendLine(c, it) }
            if (viaTrend != null) { facts += viaTrend; shown += fact.kind; usedTrends += matching.kind }
            else factLine(c, fact)?.let { facts += it; shown += fact.kind }
        }
        if (usedTrends.isEmpty()) better.firstOrNull()?.let { t -> trendLine(c, t)?.let { facts += it; usedTrends += t.kind } }
        if (facts.isEmpty()) facts += (if (f.isEmpty) c.phrase("facts quiet", SectionBank.NOTHING, c.common()) else c.phrase("facts light", SectionBank.NO_PRACTICE_FACTS, c.common()))!!

        val hard = f.flags.filter { it.kind !in FlagBank.GOOD }
        val good = f.flags.filter { it.kind in FlagBank.GOOD }
        val seen = HashMap<com.bigpapituna.sharpears.engine.brief.FlagKind, Int>()
        val notes = (hard + good).take(4).map { flag ->
            val n = seen.merge(flag.kind, 1, Int::plus)!! - 1
            flagNote(c, flag, n)
        }

        val recommendations = ArrayList<Brief.Recommendation>()
        for (cand in f.candidates) {
            if (recommendations.size >= 3) break
            val text = recText(c, cand)
            if (recommendations.none { it.text == text }) recommendations += Brief.Recommendation(text, cand)
        }

        val praise = f.praise.firstNotNullOfOrNull { praiseLine(c, it) ?: genericPraise(c, it) } ?: praiseNone(c)

        return Brief(
            period = f.period, headline = headline(c), facts = facts, notes = notes, recommendations = recommendations,
            praise = praise, sections = sections(c, shown, usedTrends), writtenBy = Brief.Writer.TEMPLATE, writtenAt = clock(),
        )
    }

    /** If anything above ever throws on odd input, a plain brief still comes back. */
    private fun fallback(f: Findings): Brief = Brief(
        period = f.period, headline = "A look at the piano ${if (f.period == Period.WEEK) "this week" else if (f.period == Period.DAY) "today" else "this session"}.",
        facts = listOf("The details are in the numbers below."), notes = emptyList(), recommendations = emptyList(),
        praise = "Showing up counts, and it is worth saying so.", sections = emptyList(), writtenBy = Brief.Writer.TEMPLATE, writtenAt = clock(),
    )
}

/** Title lookups for [TemplateWriter] built from the course and the playground; songs come from the library. */
object CourseTitles {
    private val lessons: Map<String, String> by lazy {
        COURSE.flatMap { it.lessons }.associate { it.id to it.title } + (com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID to com.bigpapituna.sharpears.engine.family.Placement.CHECK_TITLE)
    }
    private val pages: Map<String, String> by lazy { PLAYGROUND_PAGES.associate { it.id to it.title } }

    /**
     * [songTitle] answers for a library song id (null or blank for an unknown one). Lessons (and "Let's hear you play", the
     * placement check), pages and pop songs' runs ("pop-<id>", songs by chords) are looked up here.
     */
    fun lookup(songTitle: (String) -> String?): (String) -> String = { id ->
        lessons[id] ?: pages[id] ?: songTitle(id)?.takeIf { it.isNotBlank() } ?: com.bigpapituna.sharpears.engine.chords.PopPlay.titleOfRun(id).orEmpty()
    }
}
