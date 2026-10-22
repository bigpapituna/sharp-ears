package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.ToyKind

/*
 * The fact lines. Every kind has at least three phrasings for every period; where the grammar changes with the period
 * (minutes, sessions, new lessons, song runs, replays, the goal) the lists are written per period, elsewhere one list
 * carries {span} ("this week", "today", "this session"). The parent's text: numbers present, never a name, and a wrong
 * key is a "slip", a "near miss" or a "far miss".
 */
internal object FactBank {
    private val m = HashMap<Pair<FactKind, Period?>, List<L>>()
    private fun bank(k: FactKind, p: Period?, vararg ls: L) { m[k to p] = ls.toList() }

    /** The lines for [k] in [p]: the period's own list, or the one for all periods. */
    fun lines(k: FactKind, p: Period): List<L> = m[k to p] ?: m.getValue(k to null)

    /** Every list and its size, for the catalog and the tests. */
    fun sizes(): Map<String, Int> = m.entries.associate { (k, v) -> "fact ${k.first}/${k.second ?: "ALL"}" to v.size } +
        mapOf("fact PRACTICE_NONE" to NONE.size, "fact DAYS_ALL" to ALL_DAYS.size, "fact DAYS_NONE" to NO_DAYS.size)

    /** The app was open but nothing counted toward practice. */
    val NONE: List<L> = listOf(
        l("No practice minutes counted {span}."),
        l("The app was open {screen} {span}, with no practice minutes counted."),
        l("Just a look around {span}: no practice minutes counted yet."),
        l("Nothing counted toward practice {span}, though the app was opened."),
    )

    /** A full week of practice. */
    val ALL_DAYS: List<L> = listOf(
        l("Every day this week had some practice: seven out of seven."),
        l("Seven days out of seven at the piano this week."),
        l("The piano saw action on all seven days this week."),
        l("No day was skipped this week. Seven out of seven."),
    )

    /** No practice day at all. */
    val NO_DAYS: List<L> = listOf(
        l("No practice days this week, so far."),
        l("The piano stayed closed on all seven days this week."),
        l("Zero days out of seven at the piano this week."),
    )

    init {
        // ---- minutes ----
        bank(FactKind.PRACTICE_MINUTES, Period.SESSION,
            l("{min} of practice this session."),
            l("A session of {min}.", "You practised for {min}."),
            l("The piano had {min} of attention this session."),
            l("{min} counted as practice, out of {screen} on screen this session."),
        )
        bank(FactKind.PRACTICE_MINUTES, Period.DAY,
            l("{min} at the piano today."),
            l("Practice today came to {min}."),
            l("{min} of practice so far today.", "You have put in {min} so far today."),
            l("The day's practice: {min}, out of {screen} on screen."),
        )
        bank(FactKind.PRACTICE_MINUTES, Period.WEEK,
            l("{min} at the piano this week."),
            l("The week's practice came to {min}.", "You put in {min} this week."),
            l("{min} of practice across the week."),
            l("This week added up to {min} of practice, with {screen} on screen in all."),
        )

        // ---- habit ----
        bank(FactKind.DAYS_PRACTISED, null,
            l("{days} out of seven at the piano."),
            l("Practice happened on {days} this week.", "You practised on {days} this week."),
            l("The piano saw action on {days} of the seven."),
            l("Played on {days}, with {rest} off.", "You played on {days}, with {rest} off."),
        )
        bank(FactKind.SESSIONS, Period.DAY,
            l("The piano came out {times} today."),
            l("{Sessions} today, not just one."),
            l("Back at the keys {times} today.", "You came back to the keys {times} today."),
            l("Today came in {sessions}."),
        )
        bank(FactKind.SESSIONS, Period.WEEK,
            l("The piano came out {times} this week."),
            l("{Sessions} this week.", "You sat down at the piano {times} this week."),
            l("Practice came in {sessions} this week."),
            l("{Sessions} on the calendar this week."),
        )
        bank(FactKind.SESSIONS, Period.SESSION,
            l("This was one of {sessions} {span}."),
            l("The piano came out {times} {span}."),
            l("{Sessions} {span}, this one included."),
        )
        bank(FactKind.GOAL_DAYS, Period.WEEK,
            l("The daily goal was met {times} this week.", "You met the daily goal {times} this week."),
            l("{Days} reached the daily goal."),
            l("Goal days: {days} out of seven."),
            l("The daily goal of {goal} was reached on {days} of the seven.", "You reached the {goal} daily goal on {days} of the seven."),
        )
        bank(FactKind.GOAL_DAYS, Period.DAY,
            l("The daily goal is done for today."),
            l("Today's goal is met."),
            l("The goal for today is reached.", "You have reached today's goal."),
        )
        bank(FactKind.GOAL_DAYS, Period.SESSION,
            l("The daily goal was reached during this session."),
            l("This session took the day to its goal.", "This session took you to the day's goal."),
            l("The daily goal was met before the session ended."),
        )
        for ((kind, whenWord, parts) in listOf(
            Triple(FactKind.MOSTLY_MORNING, "in the morning", "mornings"),
            Triple(FactKind.MOSTLY_AFTERNOON, "in the afternoon", "afternoons"),
            Triple(FactKind.MOSTLY_EVENING, "in the evening", "evenings"),
            Triple(FactKind.MOSTLY_NIGHT, "late at night", "late nights"),
        )) bank(kind, null,
            l("Most practice happens $whenWord.", "Most of your playing happens $whenWord."),
            l("${parts.replaceFirstChar { it.uppercaseChar() }} are piano time: {pct} of the minutes."),
            l("{Pct} of the minutes this week fell $whenWord."),
            l("The piano's busiest part of the day is $parts."),
        )

        // ---- lessons and songs ----
        bank(FactKind.NEW_LESSONS, Period.WEEK,
            l("{Lessons} finished for the first time: {titles}."),
            l("New this week: {titles}."),
            l("First finishes this week: {titles}.", "You finished {titles} for the first time this week."),
            l("{Lessons} done for the first time this week."),
            l("{Lessons} new this week."),
        )
        bank(FactKind.NEW_LESSONS, Period.DAY,
            l("Finished for the first time today: {titles}."),
            l("New today: {titles}."),
            l("{Lessons} done for the first time today."),
            l("Today's first finishes: {titles}.", "You finished {titles} for the first time today."),
            l("{Lessons} new today."),
        )
        bank(FactKind.NEW_LESSONS, Period.SESSION,
            l("This session finished {titles} for the first time.", "You finished {titles} for the first time."),
            l("First finish this session: {titles}."),
            l("{Lessons} new in this session."),
            l("{Lessons} done for the first time, this session."),
        )
        bank(FactKind.LESSONS_REPLAYED, Period.WEEK,
            l("Replayed this week: {titles}."),
            l("Went back to {titles}, {times} in all.", "You went back to {titles}, {times} in all."),
            l("{Replays} of lessons already finished, including {titles}."),
            l("{Replays} this week of lessons from before."),
            l("Lessons from before came out {times} this week."),
        )
        bank(FactKind.LESSONS_REPLAYED, Period.DAY,
            l("Went back to {titles} today.", "You went back to {titles} today."),
            l("Replays today: {titles}."),
            l("{Replays} today of lessons from before."),
            l("A lesson from before came out {times} today."),
        )
        bank(FactKind.LESSONS_REPLAYED, Period.SESSION,
            l("This session went back to {titles}.", "You went back to {titles} this session."),
            l("Replayed this session: {titles}."),
            l("{Replays} of lessons from before this session."),
            l("A lesson from before came out {times} this session."),
        )
        bank(FactKind.SONG_RUNS, Period.WEEK,
            l("Songs played to the end {times} this week, most often {first}."),
            l("{Runs} this week: {titles}."),
            l("{First} led the song list this week."),
            l("The song shelf got {runs} this week.", "You played {runs} this week."),
            l("Songs played to the end {times} this week."),
        )
        bank(FactKind.SONG_RUNS, Period.DAY,
            l("Songs played to the end {times} today, led by {first}."),
            l("{Runs} today: {titles}."),
            l("{First} was today's song."),
            l("{Runs} today."),
            l("Songs played to the end {times} today."),
        )
        bank(FactKind.SONG_RUNS, Period.SESSION,
            l("Played to the end this session: {titles}.", "You played {titles} to the end this session."),
            l("{Runs} this session, led by {first}."),
            l("{First} got played through this session."),
            l("{Runs} this session."),
            l("Songs played to the end {times} this session."),
        )

        // ---- skills ----
        bank(FactKind.NEAR_MISSES, null,
            l("{Slips} {span}, {near} of them a near miss on a key next door."),
            l("Of {slips}, {near} were only a key or two off."),
            l("{NearPct} of the slips were near misses."),
            l("Slips {span}: {total}, with {near} a near miss and {far} a far miss."),
        )
        bank(FactKind.TIMING, null,
            l("Timing averaged {ms} from the beat over {runs}."),
            l("About {ms} off the beat on average, {feel}."),
            l("Across {runs}, the notes sat {ms} from the beat on average."),
            l("Timing: {feel}, {ms} from the beat on average."),
        )
        bank(FactKind.HANDS, null,
            l("Left hand {left}, right hand {right}."),
            l("The right hand hit {right} of its notes, the left {left}."),
            l("Hands side by side: left {left}, right {right}."),
            l("Hit rate by hand: right {right}, left {left}."),
        )
        bank(FactKind.EAR_ROUNDS, null,
            l("{correct} of {total} ear rounds right {span}."),
            l("By ear {span}: {correct} right out of {total}."),
            l("Ear games {span}: {correct} of {total} right."),
            l("{Rounds} {span}, {correct} of them right."),
        )
        bank(FactKind.CLAP, null,
            l("Clapped along on {pct} of the beats."),
            l("A clap landed on {pct} of the drum's beats."),
            l("Clap along: {pct} of beats, over {steps}."),
            l("{Pct} of the drum beats got a clap."),
        )
        bank(FactKind.TOY_MINUTES, null,
            l("{Toymin} of free play in the toys {span}."),
            l("{Toymin} went to {toys} {span}."),
            l("Play corner {span}: {toymin}, out of {screen} on screen."),
            l("{Toymin} of jamming and games {span}."),
        )
        bank(FactKind.STICKERS, null,
            l("{Stickers} {span}, {newn} of them new to the album."),
            l("The sticker album gained {newst} {span}."),
            l("{Stickers} earned {span}."),
            l("The pup handed out {stickers} {span}."),
        )
        bank(FactKind.QUEST_STARS, null,
            l("{Stars} on Pup's quest {span}."),
            l("Pup's quest gave {stars} {span}."),
            l("{Stars} ticked off Pup's quest {span}."),
        )
        bank(FactKind.GAMES, null,
            l("Played {game} to {beststars} {span}."),
            l("{Rounds} in the arcade {span}, the best at {beststars}."),
            l("The arcade games {span}: {rounds}, the best at {beststars}."),
            l("{Rounds} of arcade games {span}."),
        )
        bank(FactKind.HINTS, null,
            l("Of {stuck} moments that needed a hint {span}, {play} ended with the app playing the key."),
            l("Hints {span}: {stuck} stuck moments, {play} of them needing the app to play the key."),
            l("The app helped {stucktimes} {span}; in {play} it played the key itself."),
        )

        // ---- notes, keys and the beat (the email brief's extra data) ----
        bank(FactKind.SKIPPED_NOTES, null,
            l("{Skipped} went by unplayed {span}, out of {due}: let go by the pup or missed in time."),
            l("Notes left unplayed {span}: {skippedn} of {duen}."),
            l("Of {due} {span}, {skippedn} went by without being played."),
            l("{Skippedn} of {duen} notes were let go or missed {span}."),
        )
        bank(FactKind.HESITATION, null,
            l("Finding each next key took {wait} on average {span}."),
            l("From a note coming up to its key being played: {wait} on average."),
            l("The wait for each key {span} was {wait}, over {notes}."),
            l("Keys came {wait} after their notes, on average {span}."),
        )
        bank(FactKind.TEMPO_SPREAD, null,
            l("In Perform the beat held to within about {ms} from note to note, {feel}."),
            l("Tempo steadiness: the notes wandered about {ms} around the beat."),
            l("Across {runs}, the beat wandered about {ms} from note to note."),
            l("The beat stayed {feel} in Perform, about {ms} of wobble."),
        )
    }
}

private fun toyName(t: ToyKind) = when (t) { ToyKind.JAM -> "Jam"; ToyKind.PUP_SAYS -> "Pup says"; ToyKind.CLAP_ALONG -> "Clap along"; ToyKind.ARCADE -> "the games" }

/** The tokens a fact's lines may use. */
internal fun factVars(c: Ctx, f: Fact): Map<String, String?> {
    val v = f.value
    val of = f.of
    val common = c.common() + mapOf(
        "titles" to c.titles(f.lessons.ifEmpty { f.songs }),
        "first" to (f.songs.firstOrNull() ?: f.lessons.firstOrNull())?.let(c::quoted),
        "times" to timesWord(v),
    )
    val kind = when (f.kind) {
        FactKind.PRACTICE_MINUTES -> mapOf("min" to count(v, "minute"), "screen" to of?.takeIf { it > v }?.let { count(it, "minute") })
        FactKind.DAYS_PRACTISED -> mapOf("days" to count(v, "day"), "rest" to count((of ?: 7L) - v, "day"))
        FactKind.SESSIONS -> mapOf("sessions" to count(v, "session"))
        FactKind.GOAL_DAYS -> mapOf("days" to count(v, "day"), "goal" to c.f.goalMinutes?.takeIf { it > 0 }?.let { count(it.toLong(), "minute") })
        FactKind.MOSTLY_MORNING, FactKind.MOSTLY_AFTERNOON, FactKind.MOSTLY_EVENING, FactKind.MOSTLY_NIGHT ->
            mapOf("pct" to of?.takeIf { it > 0 }?.let { percent(v * 100 / it) })
        FactKind.NEW_LESSONS -> mapOf("lessons" to count(v, "lesson"))
        FactKind.LESSONS_REPLAYED -> mapOf("replays" to count(v, "replay"))
        FactKind.SONG_RUNS -> mapOf("runs" to count(v, "play-through"))
        FactKind.NEAR_MISSES -> {
            val all = of ?: v
            mapOf("slips" to count(all, "slip"), "total" to words(all), "near" to words(v), "far" to words((all - v).coerceAtLeast(0)),
                "nearPct" to if (all > 0) percent(v * 100 / all) else null)
        }
        FactKind.TIMING -> mapOf("ms" to "$v ms", "runs" to of?.let { count(it, "timed run") },
            "feel" to when { v < 25 -> "right on the beat"; v < 80 -> "close to the beat"; else -> "a little loose on the beat" })
        FactKind.HANDS -> mapOf("left" to percent(v), "right" to of?.let(::percent))
        FactKind.EAR_ROUNDS -> mapOf("correct" to words(v), "total" to of?.let(::words), "rounds" to of?.let { count(it, "ear round") })
        FactKind.CLAP -> mapOf("pct" to percent(v), "steps" to of?.let { count(it, "drum step") })
        FactKind.TOY_MINUTES -> mapOf(
            "toymin" to count(v, "minute"), "screen" to of?.takeIf { it > v }?.let { count(it, "minute") },
            "toys" to c.f.stats.toys.filter { it.minutes > 0 }.sortedByDescending { it.minutes }.map { toyName(it.toy) }
                .takeIf { it.isNotEmpty() }?.let(::joinList),
        )
        FactKind.STICKERS -> mapOf("stickers" to count(v, "sticker"), "newn" to of?.takeIf { it > 0 }?.let(::words),
            "newst" to of?.takeIf { it > 0 }?.let { count(it, "new sticker") })
        FactKind.QUEST_STARS -> mapOf("stars" to count(v, "quest star"))
        FactKind.GAMES -> mapOf("rounds" to count(v, "game round"), "beststars" to of?.let { count(it.coerceIn(1, 3), "star") },
            "game" to f.songs.firstOrNull()?.let { id -> com.bigpapituna.sharpears.engine.arcade.ArcadeGame.byId(id)?.title ?: c.quoted(id) })
        FactKind.HINTS -> mapOf("stuck" to of?.let(::words), "stucktimes" to of?.let(::timesWord), "play" to words(v))
        FactKind.SKIPPED_NOTES -> mapOf("skipped" to count(v, "note"), "skippedn" to words(v),
            "due" to of?.let { count(it, "note") }, "duen" to of?.let(::words))
        FactKind.HESITATION -> mapOf("wait" to seconds(v), "notes" to of?.let { count(it, "note") })
        FactKind.TEMPO_SPREAD -> mapOf("ms" to "$v ms", "runs" to of?.let { count(it, "timed run") },
            "feel" to when { v < 30 -> "very steady"; v < 60 -> "fairly steady"; else -> "still settling" })
    }
    return common + kind
}

/** One fact as a sentence ([extra] varies a second use of the kind); null when no phrasing applies. */
internal fun factLine(c: Ctx, f: Fact, extra: Int = 0): String? {
    val vars = factVars(c, f)
    val slot = "fact ${f.kind}"
    return when {
        f.kind == FactKind.PRACTICE_MINUTES && f.value == 0L -> c.phrase("$slot none", FactBank.NONE, vars, extra)
        f.kind == FactKind.DAYS_PRACTISED && f.value >= 7 -> c.phrase("$slot all", FactBank.ALL_DAYS, vars, extra)
        f.kind == FactKind.DAYS_PRACTISED && f.value == 0L -> c.phrase("$slot none", FactBank.NO_DAYS, vars, extra)
        else -> c.phrase(slot, FactBank.lines(f.kind, c.period), vars, extra)
    }
}
