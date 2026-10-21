package com.bigpapituna.sharpears.ui.brief

import com.bigpapituna.sharpears.engine.plural
import com.bigpapituna.sharpears.engine.times
import com.bigpapituna.sharpears.engine.brief.ActionCopy
import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Direction
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.family.ProfileKind

/*
 * FAKE WRITER, FOR THE INTEGRATOR TO REPLACE.
 *
 * Step 3 (this branch) builds the screens against the BriefWriter interface only. The real writers live on other branches:
 * the AI writer (engine/brief/ai, brief2a) and the template writer (engine/brief/templates, brief2b), chained by the
 * integrator's WriterChain. This stand-in writes a plain, warm brief straight from the findings so every screen, every
 * state and the debug route (`--es screen brief`) can be seen. It is NOT a template bank: no variety, no copy tests.
 *
 * To switch to the real writers, change the one line in BriefWriters.kt (ui/brief/BriefEnv.kt); nothing else in ui/brief
 * refers to this class except the demo route and the tests.
 */
class FakeWriter(
    private val titles: BriefTitles,
    /** What the brief says wrote it: AI, or the phone's templates (the screens show the matching note). */
    private val by: Brief.Writer = Brief.Writer.AI,
    private val clock: () -> Long = System::currentTimeMillis,
) : BriefWriter {

    override fun write(findings: Findings, name: String): Brief {
        val f = findings
        val kid = f.profileKind == ProfileKind.KID
        val who = if (kid) name else "you"
        val has = if (kid) "$name played" else "You played"
        val fact = { k: FactKind -> f.facts.firstOrNull { it.kind == k } }
        val days = fact(FactKind.DAYS_PRACTISED)
        val mins = fact(FactKind.PRACTICE_MINUTES)
        val headline = when (f.period) {
            Period.WEEK -> if (days != null) {
                val up = f.trends.firstOrNull { it.kind == TrendKind.DAYS && it.direction == Direction.UP }
                "$has on ${days.value} day${if (days.value == 1L) "" else "s"} this week" + (if (up != null) ", up from about ${up.before.toInt()}, and it shows." else ".")
            } else "A calm week at the piano."
            Period.DAY -> "$has today and finished something new."
            Period.SESSION -> "A good short session: ${if (kid) name else "you"} finished a new lesson."
        }
        val facts = buildList {
            mins?.let { add("${plural(it.value.toInt(), "minute")} at the piano" + if (f.period == Period.WEEK) " this week." else ".") }
            days?.let { add("${plural(it.value.toInt(), "day")} out of ${it.of ?: 7}.") }
            fact(FactKind.NEW_LESSONS)?.let { add("New: " + it.lessons.take(2).joinToString(" and ") { l -> titles.lesson(l) } + ".") }
            fact(FactKind.GOAL_DAYS)?.let { add("The daily goal reached " + times(it.value.toInt()) + ".") }
            fact(FactKind.SONG_RUNS)?.let { add("${it.songs.firstOrNull()?.let(titles::song) ?: "A song"}, " + times(it.value.toInt()) + ".") }
            fact(FactKind.NEAR_MISSES)?.let { add("Most slips were the key next door, a good sign.") }
        }.take(4)
        val notes = f.flags.mapNotNull { fl ->
            when (fl.kind) {
                FlagKind.STUCK_LESSON -> "${fl.lessons.firstOrNull()?.let(titles::lesson) ?: "One lesson"} is taking a few tries. That is normal: new skills take days to settle, and the pup lets a step go on purpose so nobody gets stuck."
                FlagKind.NOISY_ROOM -> "The microphone heard the room a couple of times. It listens to everything, so a quieter corner helps."
                FlagKind.LOW_STARS -> "${fl.lessons.firstOrNull()?.let(titles::lesson) ?: "A lesson"} came with one star. First runs are often rough, and a second go usually earns more."
                else -> null
            }
        }.take(3)
        val recs = f.candidates.take(3).map { c ->
            val v = ActionCopy.of(c, titles, name)
            val text = when (c.action) {
                ActionId.SET_NEXT -> "Make ${c.params["lesson"]?.let(titles::lesson) ?: "the next lesson"} the first stone tomorrow, so it starts fresh."
                ActionId.PRACTISE_TOGETHER -> "Sit down together for a few minutes: you on the left hand, ${if (kid) name else "you"} on the right."
                ActionId.REPLAY_FOR_STARS -> "Play ${c.params["lesson"]?.let(titles::lesson) ?: "it"} once more for an easy extra star."
                ActionId.FRESH_START -> "A fresh start on ${c.params["page"]?.let(titles::page) ?: "the page"} can make it feel new again."
                ActionId.PACE_FOCUS -> "Stay with ${c.params["page"]?.let(titles::page) ?: "this page"} for now and let the rest wait."
                ActionId.PACE_UNLOCK -> "Open the next page early: it is going well."
                ActionId.SETTING -> v.body
                ActionId.TEACHER_ASSIGN -> "Add a new song to the shelf with a note from you. ${c.params["after"]?.let(titles::song) ?: "The last one"} is going really well."
            }
            Brief.Recommendation(text, c)
        }
        val praise = f.praise.firstOrNull()?.let { p ->
            when (p.kind) {
                PraiseKind.STUCK_WITH_IT -> "${if (kid) name else "You"} stuck with ${p.lessons.firstOrNull()?.let(titles::lesson) ?: "a hard lesson"} until it gave three stars. That is the habit that makes players."
                PraiseKind.CLEAN_SONG -> "${p.songs.firstOrNull()?.let(titles::song) ?: "A song"} came out clean. Say so out loud."
                PraiseKind.NEW_LESSONS -> "Finished ${p.lessons.firstOrNull()?.let(titles::lesson) ?: "a new lesson"} for the first time."
                else -> "Showing up on most days is what builds a player."
            }
        } ?: "Every minute at the piano counts."
        val sections = if (f.period == Period.SESSION) emptyList() else listOf(
            Brief.Section("How the skills moved", f.trends.joinToString(" ") { trendSentence(it) }.ifEmpty { "Not enough weeks yet to say." }),
            Brief.Section("Habits", "Most playing happens in the evening, in short sessions. That rhythm suits a young player."),
        )
        return Brief(f.period, headline, facts, notes, recs, praise, sections, by, clock())
    }

    private fun trendSentence(t: Trend) = when (t.kind) {
        TrendKind.DAYS -> "Days played are up from about ${t.before.toInt()} to ${t.now.toInt()}."
        TrendKind.STARS -> "Stars on lessons are climbing."
        else -> ""
    }

    override fun kidCard(findings: Findings, name: String): KidCard {
        val lines = buildList {
            add("Wow, ${name}! You played so well today.")
            findings.facts.firstOrNull { it.kind == FactKind.NEW_LESSONS }?.lessons?.firstOrNull()?.let { add("You finished ${titles.lesson(it)}!") }
            add("I loved listening to you.")
        }
        val next = findings.flags.firstOrNull { it.kind == FlagKind.STUCK_LESSON }?.lessons?.firstOrNull()?.let { "Next time, let's try ${titles.lesson(it)} again together!" }
        return KidCard(lines, next, by, clock())
    }

    override fun answer(findings: Findings, name: String, question: String): String {
        val q = question.lowercase()
        return when {
            "stuck" in q || "long" in q || "hard" in q -> "One lesson is taking a few tries, which is normal: new skills settle over days. The pup lets a step go after three tries so ${name} keeps moving."
            "practice" in q || "much" in q || "minutes" in q -> "${name} played ${plural(findings.stats.totals.practiceMinutes, "minute")} on ${plural(findings.stats.totals.daysPractised, "day")}. Short and often is better than long and rare."
            "well" in q || "good" in q -> "Most of ${name}'s slips were the key next door, which means the ear and the hands are close."
            else -> "From what the week shows, ${name} is on a good path. Keep sessions short and end on something that works."
        }
    }
}
