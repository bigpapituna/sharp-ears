package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind

/*
 * Headlines: one sentence composed from the top fact and the top praise or flag. The pieces are predicates with no
 * subject ("played on five days this week"), so the same piece reads for "Mia" and for "You". Several shapes join them
 * and the seeded pick rotates through the shapes, so next week's headline is built differently.
 */
internal const val HEADLINE_MAX = 110

/** The predicate for a fact, or null when the kind does not make a good headline. Lower case, no full stop. */
internal fun factClause(c: Ctx, f: Fact): String? {
    val vars = factVars(c, f)
    val bank: List<L> = when {
        f.kind == FactKind.PRACTICE_MINUTES && f.value == 0L -> listOf(l("opened the app {span} without practice counting yet"), l("had a look around {span}"))
        f.kind == FactKind.PRACTICE_MINUTES -> listOf(l("put in {min} {span}"), l("spent {min} at the piano {span}"))
        f.kind == FactKind.DAYS_PRACTISED && f.value >= 7 -> listOf(l("played every day this week"), l("practised on all seven days"))
        f.kind == FactKind.DAYS_PRACTISED && f.value == 0L -> listOf(l("took the week off from the piano"), l("kept the piano closed this week"))
        f.kind == FactKind.DAYS_PRACTISED -> listOf(l("played on {days} this week"), l("got to the piano on {days} this week"))
        f.kind == FactKind.SESSIONS -> listOf(l("sat down at the piano {times} {span}"), l("came back to the keys {times} {span}"))
        f.kind == FactKind.GOAL_DAYS && c.period == Period.WEEK -> listOf(l("reached the daily goal on {days}"), l("met the daily goal {times}"))
        f.kind == FactKind.GOAL_DAYS -> listOf(l("reached the daily goal {span}"), l("finished the day's goal {span}"))
        f.kind == FactKind.MOSTLY_MORNING -> listOf(l("played mostly in the morning"))
        f.kind == FactKind.MOSTLY_AFTERNOON -> listOf(l("played mostly in the afternoon"))
        f.kind == FactKind.MOSTLY_EVENING -> listOf(l("played mostly in the evening"))
        f.kind == FactKind.MOSTLY_NIGHT -> listOf(l("played mostly late at night"))
        f.kind == FactKind.NEW_LESSONS && f.value == 1L && f.lessons.size == 1 && c.quoted(f.lessons[0]) != null ->
            listOf(l("finished {first} for the first time"), l("got through {first} for the first time"))
        f.kind == FactKind.NEW_LESSONS -> listOf(l("finished {lessons} for the first time"), l("got through {lessons} for the first time"))
        f.kind == FactKind.LESSONS_REPLAYED -> listOf(l("went back to {titles}"), l("replayed lessons from before"))
        f.kind == FactKind.SONG_RUNS -> listOf(l("played {first} through to the end"), l("played songs through to the end {times}"))
        f.kind == FactKind.TIMING -> listOf(l("kept within {ms} of the beat on average"))
        f.kind == FactKind.HANDS -> listOf(l("played with both hands"))
        f.kind == FactKind.EAR_ROUNDS -> listOf(l("got {correct} of {total} ear rounds right"))
        f.kind == FactKind.CLAP -> listOf(l("clapped along on {pct} of the beats"))
        f.kind == FactKind.TOY_MINUTES -> listOf(l("spent {toymin} in the toys {span}"))
        f.kind == FactKind.STICKERS -> listOf(l("collected {stickers} {span}"))
        f.kind == FactKind.QUEST_STARS -> listOf(l("earned {stars} on Pup's quest"))
        f.kind == FactKind.NEAR_MISSES || f.kind == FactKind.HINTS -> return null
        else -> return null
    }
    return clause(c, "headline fact ${f.kind}", bank, vars)
}

/** The predicate for a praise. */
internal fun praiseClause(c: Ctx, p: Praise): String {
    val v = praiseVars(c, p)
    val bank: List<L> = when (p.kind) {
        PraiseKind.FINALE -> listOf(l("played the finale of {page}"), l("finished with the finale of {page}"), l("played a whole finale"))
        PraiseKind.FIRST_CHORD -> listOf(l("played a first chord"), l("played a first chord in {title}"))
        PraiseKind.STUCK_WITH_IT -> listOf(l("stuck with {titles} until it worked"), l("kept at {titles} until it worked"), l("stuck with a tricky lesson until it worked"))
        PraiseKind.SHINY_STICKER -> listOf(l("found a shiny sticker"), l("earned a shiny sticker"))
        PraiseKind.RARE_STICKER -> listOf(l("found a rare sticker"), l("earned a rare sticker"))
        PraiseKind.GOAL_STREAK -> listOf(l("hit the daily goal {days} in a row"), l("kept a goal streak going"))
        PraiseKind.THREE_STARS -> listOf(l("earned three stars on {titles}"), l("earned three stars"))
        PraiseKind.TIMING_IMPROVED -> listOf(l("tightened up the timing"), l("got closer to the beat"))
        PraiseKind.ACCURACY_IMPROVED -> listOf(l("played more accurately"), l("landed more notes on time"))
        PraiseKind.MISSES_CLOSER -> listOf(l("got closer to the right keys"), l("found the neighbourhood of the right key"))
        PraiseKind.EAR_IMPROVED -> listOf(l("sharpened up the ear games"), l("heard more right in the ear games"))
        PraiseKind.LEFT_HAND_IMPROVED -> listOf(l("strengthened the left hand"), l("landed more left-hand notes"))
        PraiseKind.CONSISTENT -> listOf(l("kept showing up"), l("built a steady routine"))
        PraiseKind.CLEAN_SONG -> listOf(l("played {songs} at three stars"), l("played a song at three stars"))
        PraiseKind.NEW_LESSONS -> listOf(l("finished {titles}"), l("worked through new lessons"))
        PraiseKind.QUEST_DAY -> listOf(l("completed Pup's whole quest"), l("earned all of Pup's quest stars"))
    }
    return clause(c, "headline praise ${p.kind}", bank, v) ?: "did well"
}

/** The predicate for a flag: [Flag] in the voice of "what came up". */
internal fun flagClause(c: Ctx, f: Flag): String {
    val v = flagVars(c, f) + mapOf("is" to if (c.kid) "is" else "are", "has" to if (c.kid) "has" else "have", "looks" to if (c.kid) "looks" else "look")
    val bank: List<L> = when (f.kind) {
        FlagKind.STUCK_LESSON -> listOf(l("met a tricky step in {title}"), l("hit a tricky step in a lesson"))
        FlagKind.STUCK_SONG -> listOf(l("{is} still finding {song}"), l("{is} still working on a song"))
        FlagKind.NOISY_ROOM -> listOf(l("had a noisy room in the way"))
        FlagKind.HINT_DEPENDENCE -> listOf(l("leaned on hints"))
        FlagKind.PAGE_STRUGGLE -> listOf(l("hit a tricky patch on {page}"), l("hit a tricky patch"))
        FlagKind.READY_FOR_LETTERS -> listOf(l("{looks} ready for letters"))
        FlagKind.READY_FOR_ROAD -> listOf(l("finished the playground"))
        FlagKind.CRUISING -> listOf(l("{is} cruising through the lessons"))
        FlagKind.REPLAYING_NOT_MOVING_ON -> listOf(l("went back to old favourites"))
        FlagKind.LOW_STARS -> listOf(l("finished some lessons at one star"))
        FlagKind.LEFT_HAND_BEHIND -> listOf(l("{is} still bringing the left hand along"))
        FlagKind.FAR_MISSES -> listOf(l("{is} still finding the keys"))
        FlagKind.LONG_DAYS -> listOf(l("played long days"))
        FlagKind.MASTERED_SONG -> listOf(l("{has} {song} down"), l("{has} a song down"))
        FlagKind.SCATTERED -> listOf(l("sampled lots of lessons"))
        FlagKind.TOY_HEAVY -> listOf(l("spent most of the time in the toys"))
        FlagKind.PRACTICE_DIPPED -> listOf(l("had a lighter week"))
    }
    return clause(c, "headline flag ${f.kind}", bank, v) ?: "had a few things come up"
}

private val HARD_TAILS = listOf("with one spot worth a look", "and there is one spot worth a closer look", "though one spot needs a look")
private val GOOD_TAILS = listOf("with good news about what comes next", "and the next step looks ready")

private val QUIET = listOf(
    l("A quiet {spanNoun}: the piano stayed closed."),
    l("The piano stayed quiet {span}."),
    l("Nothing was played {span}, so there is little to report."),
    l("No playing {span}. The brief picks up again when the music does."),
)

/** What the headline is built from; the order of the findings is the order of importance. */
internal fun headline(c: Ctx): String {
    val f = c.f
    val subject = if (c.kid) c.name.ifBlank { "Your child" }.take(24) else "You"
    val span = c.span.replaceFirstChar { it.uppercaseChar() }
    val mid = if (c.kid) subject else "you"

    val fact = f.facts.firstNotNullOfOrNull { fa -> factClause(c, fa) }
    val praise = f.praise.firstOrNull()?.let { praiseClause(c, it) }
    val flagRaw = f.flags.firstOrNull()
    val flag = flagRaw?.let { flagClause(c, it) }
    val hard = flagRaw != null && flagRaw.kind !in FlagBank.GOOD
    val tail = if (flagRaw == null) null else (if (hard) HARD_TAILS else GOOD_TAILS).let { it[c.index("headline tail", it.size)] }

    val shapes = ArrayList<String>()
    if (fact != null && praise != null) {
        shapes += "$subject $fact, and $praise."
        shapes += "$subject $praise, and $fact."
        if (!fact.contains(c.span)) shapes += "$span, $mid $fact and $praise."
        shapes += "$subject $praise and $fact."
    }
    if (fact != null && flag != null && tail != null) {
        shapes += "$subject $fact, $tail."
        if (praise == null) { shapes += "$subject $fact, and $flag."; if (!fact.contains(c.span)) shapes += "$span, $mid $fact and $flag." }
    }
    if (fact != null && praise == null && flag == null) {
        shapes += "$subject $fact."
        if (!fact.contains(c.span)) shapes += "$span, $mid $fact."
        shapes += "$subject $fact, and the details are below."
    }
    if (fact == null && praise != null) {
        shapes += "$subject $praise."
        shapes += "$span, $mid $praise."
        shapes += "$subject $praise. The details are below."
    }
    if (fact == null && praise == null && flag != null) {
        shapes += "$subject $flag."
        shapes += "$span, $mid $flag."
        shapes += "$subject $flag, and there is a next step below."
    }
    val fits = shapes.map(::tidy).filter { it.length <= HEADLINE_MAX }
    if (fits.isNotEmpty()) return fits[c.index("headline shape", fits.size)]

    // Nothing to build from, or every shape ran long.
    if (fact != null && "$subject $fact.".length <= HEADLINE_MAX) return tidy("$subject $fact.")
    if (f.isEmpty) return c.phrase("headline quiet", QUIET, c.common())!!.take(HEADLINE_MAX)
    return tidy("$subject had a ${c.spanNoun} at the piano.").take(HEADLINE_MAX)
}

/** A predicate from [bank] with no digits in it (a headline is never a number), lower case; null when none applies. */
private fun clause(c: Ctx, slot: String, bank: List<L>, vars: Map<String, String?>): String? {
    val usable = c.usable(bank, vars, capital = false).filter { s -> s.none { it.isDigit() } }
    return if (usable.isEmpty()) null else usable[c.index(slot, usable.size)]
}
