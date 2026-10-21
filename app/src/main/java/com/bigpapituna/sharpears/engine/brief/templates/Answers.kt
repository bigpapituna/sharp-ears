package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Period

/*
 * "Ask the brief" without a model: a small set of recognisable question intents, each answered from the findings in a
 * sentence or three. Anything else gets the polite limit and the headline. Never null, never a guess.
 */
internal enum class Intent { HOW_LONG, WHAT_DID, WHAT_HARD, WHAT_NEXT, WHAT_PRAISE, READY_LETTERS, READY_ROAD, OTHER }

private fun has(q: String, vararg words: String) = words.any { q.contains(it) }

internal fun intentOf(question: String): Intent {
    val q = question.lowercase()
    return when {
        has(q, "praise", "proud", "compliment", "celebrate", "well done") -> Intent.WHAT_PRAISE
        has(q, "road", "next level", "move up", "move on to", "big kid") -> Intent.READY_ROAD
        has(q, "letter", "reader", "read yet", "read letters", "reading") || (has(q, "ready") && has(q, "read")) -> Intent.READY_LETTERS
        has(q, "hard", "difficult", "struggl", "stuck", "trouble", "tricky", "problem", "worry", "worried") -> Intent.WHAT_HARD
        has(q, "next", "should", "recommend", "suggest", "what now", "what can", "do now", "help") -> Intent.WHAT_NEXT
        has(q, "how long", "how much", "how many", "how often", "minutes", "time spent", "long did", "hours") -> Intent.HOW_LONG
        has(q, "what did", "what has", "what have", "what was", "played", "worked on", "learn", "lesson", "song", "practis", "practic", "do this") -> Intent.WHAT_DID
        has(q, "well", "good", "best", "win") -> Intent.WHAT_PRAISE
        else -> Intent.OTHER
    }
}

private val PRAISE_NONE = listOf(
    l("Nothing stood out as a special moment {span}, and turning up is the real work. A word of thanks for that goes a long way."),
    l("No big moment showed up {span}. Showing up still counts, and it is worth saying so."),
    l("It was a quiet stretch {span}, with no single moment to point at. Praise the effort: sitting down at the piano is the hard part."),
    l("There was no single highlight {span}. The best thing to praise is the habit of coming back."),
)

internal fun praiseNone(c: Ctx): String = c.phrase("praise none", PRAISE_NONE, c.common())!!

private val NOT_SEEN = listOf(
    l("The app did not see any playing {span}."),
    l("Nothing was played {span}, as far as the app can tell."),
)

private fun sentences(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ")

private val LETTERS_NOT_YET = listOf(
    l("Not yet, going by what the app saw {span}. It looks for about two weeks of letter lessons played well, and that has not shown up."),
    l("Nothing {span} says so yet. The app suggests letters after about two weeks of letter lessons going well."),
)
private val ROAD_NOT_YET = listOf(
    l("Not yet, going by what the app saw {span}. The road opens after the fair, the last page of the playground, is finished."),
    l("The road comes after the fair, and the app has not seen the fair finished {span}."),
)

internal fun answer(c: Ctx, question: String, headline: () -> String): String {
    val f = c.f
    val span = c.common()
    val intent = intentOf(question)
    val text: String? = when (intent) {
        Intent.HOW_LONG -> {
            val parts = listOf(FactKind.PRACTICE_MINUTES, FactKind.DAYS_PRACTISED, FactKind.SESSIONS)
                .mapNotNull { k -> f.facts.firstOrNull { it.kind == k }?.let { factLine(c, it, 2) } }
            if (parts.isEmpty()) c.phrase("answer none", NOT_SEEN, span) else sentences(*parts.toTypedArray())
        }
        Intent.WHAT_DID -> {
            val kinds = listOf(FactKind.NEW_LESSONS, FactKind.LESSONS_REPLAYED, FactKind.SONG_RUNS)
            val parts = kinds.mapNotNull { k -> f.facts.firstOrNull { it.kind == k }?.let { factLine(c, it, 2) } }
            when {
                parts.isNotEmpty() -> sentences(*parts.toTypedArray())
                f.facts.isNotEmpty() -> sentences(*f.facts.mapNotNull { factLine(c, it, 2) }.take(2).toTypedArray()).ifBlank { null }
                else -> c.phrase("answer none", NOT_SEEN, span)
            }
        }
        Intent.WHAT_HARD -> {
            val hard = f.flags.filter { it.kind !in FlagBank.GOOD }.take(2)
            if (hard.isEmpty()) sentences("Nothing stood out as hard ${c.span}.", f.trends.firstOrNull { it.good == false }?.let { trendLine(c, it, 2) })
            else sentences(*hard.mapIndexed { i, fl -> flagNote(c, fl, 2 + i) }.toTypedArray())
        }
        Intent.WHAT_NEXT -> {
            val recs = f.candidates.take(2).map { recText(c, it, 2) }.distinct()
            if (recs.isEmpty()) "From what the app saw ${c.span}, nothing needs changing. The next lesson on the path is the natural place to carry on."
            else sentences(*recs.toTypedArray())
        }
        Intent.WHAT_PRAISE -> f.praise.firstNotNullOfOrNull { praiseLine(c, it, 2, allowName = false) } ?: praiseNone(c)
        Intent.READY_LETTERS -> {
            val flag = f.flags.firstOrNull { it.kind == FlagKind.READY_FOR_LETTERS }
            when {
                flag != null -> sentences(flagObservation(c, flag, 2), flagWhy(c, flag, 2), "Reader mode is the setting to switch.")
                c.kid && f.reader -> "Letters are already on the keys."
                else -> c.phrase("answer letters", LETTERS_NOT_YET, span)
            }
        }
        Intent.READY_ROAD -> {
            val flag = f.flags.firstOrNull { it.kind == FlagKind.READY_FOR_ROAD }
            if (flag != null) sentences(flagObservation(c, flag, 2), flagWhy(c, flag, 2)) else c.phrase("answer road", ROAD_NOT_YET, span)
        }
        Intent.OTHER -> null
    }
    val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }
    return cleaned ?: "I can only answer about what the app saw ${c.span}. ${headline()}"
}

/** Used by the catalog: the questions the answers recognise, one example each. */
internal val EXAMPLE_QUESTIONS = mapOf(
    Intent.HOW_LONG to "How long did they play?",
    Intent.WHAT_DID to "What did they do?",
    Intent.WHAT_HARD to "What was hard?",
    Intent.WHAT_NEXT to "What should we do next?",
    Intent.WHAT_PRAISE to "What should I praise?",
    Intent.READY_LETTERS to "Are they ready for letters?",
    Intent.READY_ROAD to "Are they ready for the road?",
)

@Suppress("unused") private val KEEP = Period.WEEK
