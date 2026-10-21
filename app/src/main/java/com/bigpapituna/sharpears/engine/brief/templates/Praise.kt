package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind

/*
 * What to praise at home, per praise kind: at least four phrasings each, some that need no title, some that use the
 * child's name (never more than once in a line), and adult wordings in "you".
 */
internal object PraiseBank {
    private val m = HashMap<PraiseKind, List<L>>()
    private fun p(k: PraiseKind, vararg ls: L) { m[k] = ls.toList() }
    fun lines(k: PraiseKind): List<L> = m.getValue(k)
    fun sizes(): Map<String, Int> = m.entries.associate { "praise ${it.key}" to it.value.size }

    init {
        p(PraiseKind.FINALE,
            l("Ask {name} to tell you about the finale of {page}. It played all the way through.", "You played the finale of {page} all the way through."),
            l("{Page} is done, fireworks and all. That is worth a cheer at home.", "{Page} is done, fireworks and all. Take the cheer."),
            l("Worth a high five: the finale of {page} played through to the end.", "The finale of {page} played through to the end. That one is yours."),
            l("Finished {page}. Ask {name} what happened at the end.", "You finished {page}, the finale included."),
            l("A whole page of the playground is done, finale included. Worth a high five at home.", "A whole page is done, finale included. Take the cheer."),
        )
        p(PraiseKind.FIRST_CHORD,
            l("A first chord, in {title}: several keys sounding together. That deserves a high five.", "Your first chord, in {title}: several keys sounding together."),
            l("{Title} brought {name}'s first chord. Ask for a demonstration.", "{Title} brought your first chord. Worth a moment of pride."),
            l("First chord played: {title}. Keys together are a real step.", "First chord played: {title}. Keys together are a real step."),
            l("A first chord has landed: several keys sounding together. Worth a cheer at home.", "Your first chord has landed: several keys sounding together."),
        )
        p(PraiseKind.STUCK_WITH_IT,
            l("{Titles} took several goes and ended well. Tell {name} you noticed the sticking power.", "{Titles} took several goes and ended well. That persistence is yours."),
            l("Sticking with {titles} paid off: it finished with two stars or more.", "Sticking with {titles} paid off: two stars or more."),
            l("Worth a high five at home: {titles} needed more than one go, and ended on a good note.", "{Titles} needed more than one go and ended on a good note."),
            l("{Name} kept going with {titles} until it worked. Say so out loud.", "You kept going with {titles} until it worked."),
            l("A lesson that took several goes finished well. Worth a high five at home.", "A lesson that took several goes finished well. That counts."),
        )
        p(PraiseKind.SHINY_STICKER,
            l("{Stickers} of the shiny kind {span}. Ask {name} to show you the album.", "{Stickers} of the shiny kind {span}."),
            l("A shiny sticker landed {span}. Worth a look at the album together.", "A shiny sticker landed {span}."),
            l("Something shiny in the album {span}: {stickers}. Say congratulations.", "Something shiny in the album {span}: {stickers}."),
            l("{Name} found {stickers} of the shiny kind {span}. A good moment to ask what else is in the album.", "You found {stickers} of the shiny kind {span}."),
        )
        p(PraiseKind.RARE_STICKER,
            l("{Stickers} of the rare kind {span}. Ask {name} to show you the album.", "{Stickers} of the rare kind {span}."),
            l("A rare sticker landed {span}. Worth a look at the album together.", "A rare sticker landed {span}."),
            l("Something rare in the album {span}: {stickers}. Say congratulations.", "Something rare in the album {span}: {stickers}."),
            l("{Name} found {stickers} of the rare kind {span}. Ask which one is the favourite.", "You found {stickers} of the rare kind {span}."),
        )
        p(PraiseKind.GOAL_STREAK,
            l("The daily goal reached {days} in a row. Worth saying out loud.", "The daily goal reached {days} in a row. Worth saying out loud."),
            l("A streak: {days} running with the goal met.", "A streak: {days} running with the goal met."),
            l("{Days} in a row of reaching the daily goal. {name} will want to hear it.", "{Days} in a row of reaching the daily goal."),
            l("The goal has been met {days} running. A good moment for a high five.", "You have met the goal {days} running."),
        )
        p(PraiseKind.THREE_STARS,
            l("Three stars on {titles}. Ask {name} to play one for you.", "Three stars on {titles}."),
            l("{Titles} earned three stars. A request to hear it is the best praise.", "{Titles} earned three stars."),
            l("Three stars: {titles}. Clean playing, and worth a listen at home.", "Three stars: {titles}. Clean playing."),
            l("{Lessons} finished with three stars {span}. Worth a high five at home.", "{Lessons} finished with three stars {span}."),
        )
        p(PraiseKind.TIMING_IMPROVED,
            l("Timing is tighter: about {ms} from the beat now, closer than before.", "Timing is tighter: about {ms} from the beat now, closer than before."),
            l("The beat is landing better, {ms} off on average and down from earlier.", "The beat is landing better, {ms} off on average and down from earlier."),
            l("Notes are sitting closer to the beat than they used to, at about {ms} off. Tell {name}.", "Notes are sitting closer to the beat than they used to, at about {ms} off."),
            l("A steadier beat: {ms} from the beat on average, better than before.", "A steadier beat: {ms} from the beat on average."),
        )
        p(PraiseKind.ACCURACY_IMPROVED,
            l("Accuracy in Perform is up to {pct}. Tell {name} the work shows.", "Accuracy in Perform is up to {pct}."),
            l("More notes are landing on time: {pct} in Perform, better than before.", "More notes are landing on time: {pct} in Perform, better than before."),
            l("A better Perform score: {pct}, up on earlier weeks.", "A better Perform score: {pct}, up on earlier weeks."),
            l("Playing along with the beat is landing more often, at {pct} now.", "Playing along with the beat is landing more often, at {pct} now."),
        )
        p(PraiseKind.MISSES_CLOSER,
            l("When a key is missed it is now usually the neighbour: {pct} next door. The hand is finding its way.", "When a key is missed it is now usually the neighbour: {pct} next door."),
            l("Slips are landing closer to the target, {pct} of them a key next door.", "Slips are landing closer to the target, {pct} of them a key next door."),
            l("Even the misses are better: {pct} are only a key or two off.", "Even the misses are better: {pct} are only a key or two off."),
            l("The hand is learning the layout: {pct} of slips now land next door. Tell {name}.", "Your hand is learning the layout: {pct} of slips now land next door."),
        )
        p(PraiseKind.EAR_IMPROVED,
            l("Ear games are going better: {pct} right, up on before.", "Ear games are going better: {pct} right, up on before."),
            l("The ear is sharpening: {pct} right in the ear games.", "Your ear is sharpening: {pct} right in the ear games."),
            l("More ear rounds are right, {pct} now. Ask {name} to show you a game.", "More ear rounds are right, {pct} now."),
            l("Listening is paying off: {pct} of ear rounds right, up on earlier.", "Listening is paying off: {pct} of ear rounds right."),
        )
        p(PraiseKind.LEFT_HAND_IMPROVED,
            l("The left hand is gaining: {pct} of its notes land, up on before.", "Your left hand is gaining: {pct} of its notes land, up on before."),
            l("Left-hand notes are landing more often, {pct} now. Tell {name} you noticed.", "Left-hand notes are landing more often, {pct} now."),
            l("The left hand is coming along: {pct} of its notes land now.", "Your left hand is coming along: {pct} of its notes land now."),
            l("Both hands are working harder together, with the left at {pct}.", "Both hands are working together, with the left at {pct}."),
        )
        p(PraiseKind.CONSISTENT,
            l("{Days} at the piano {lastseven}. The routine is taking shape.", "{Days} at the piano {lastseven}. The routine is taking shape."),
            l("Showing up counts: {days} {lastseven}. Mention it to {name}.", "Showing up counts: {days} {lastseven}."),
            l("{Days} {lastseven}. That is a pattern worth noticing.", "{Days} {lastseven}. That is a pattern worth noticing."),
            l("The piano has seen {days} {lastseven}. A good habit is forming.", "You have played on {days} {lastseven}. A good habit is forming."),
        )
        p(PraiseKind.CLEAN_SONG,
            l("{Songs} played at three stars. Ask {name} for a performance.", "{Songs} played at three stars."),
            l("Clean playing: {songs} at three stars.", "Clean playing: {songs} at three stars."),
            l("{Songs} came out at three stars. That deserves an audience.", "{Songs} came out at three stars."),
            l("A song played at three stars {span}. That deserves an audience.", "A song played at three stars {span}."),
        )
        p(PraiseKind.NEW_LESSONS,
            l("New ground: {titles}. Worth a high five.", "New ground: {titles}."),
            l("{Titles} finished for the first time {span}. Ask {name} to show you.", "{Titles} finished for the first time {span}."),
            l("A new lesson done: {titles}. Say well done.", "A new lesson done: {titles}."),
            l("{Lessons} finished for the first time {span}. Say well done.", "{Lessons} finished for the first time {span}."),
        )
        p(PraiseKind.QUEST_DAY,
            l("All three of Pup's quest stars on {days}. Ask {name} which one was the favourite.", "All three of Pup's quest stars on {days}."),
            l("Pup's quest was completed on {days}.", "Pup's quest was completed on {days}."),
            l("A full quest, three stars, on {days} {lastweek}.", "A full quest, three stars, on {days} {lastweek}."),
            l("Pup's quest got all three stars on {days}. Worth a word of praise.", "Pup's quest got all three stars on {days}."),
        )
    }
}

/** The tokens a praise's lines may use. */
internal fun praiseVars(c: Ctx, p: Praise): Map<String, String?> {
    val v = p.value
    return c.common() + mapOf(
        "title" to p.lessons.firstOrNull()?.let(c::quoted), "titles" to c.titles(p.lessons),
        "songs" to c.titles(p.songs), "song" to p.songs.firstOrNull()?.let(c::quoted),
        "page" to p.page?.let(c::pageLower),
        "days" to count(v, "day"), "ms" to "$v ms", "pct" to percent(v),
        "stickers" to count(v, "sticker"), "lessons" to count(v, "lesson"),
        "lastseven" to "in the last seven days", "lastweek" to c.span,
    )
}

/** One thing to praise at home; null when no phrasing applies to these findings. */
internal fun praiseLine(c: Ctx, p: Praise, extra: Int = 0, allowName: Boolean = true): String? {
    val vars = praiseVars(c, p).let { if (allowName) it else it + ("name" to null) }
    return c.phrase("praise ${p.kind}", PraiseBank.lines(p.kind), vars, extra)
}

/** The praise as a plain statement, for when nothing in the bank applies. */
internal fun genericPraise(c: Ctx, p: Praise): String = when (p.kind) {
    PraiseKind.FINALE -> "A page of the playground is done, finale included."
    PraiseKind.FIRST_CHORD -> "A first chord has landed: several keys sounding together."
    PraiseKind.STUCK_WITH_IT -> "A lesson that took several goes finished well."
    PraiseKind.SHINY_STICKER, PraiseKind.RARE_STICKER -> "A special sticker landed in the album ${c.span}."
    PraiseKind.GOAL_STREAK -> "The daily goal has been reached several days in a row."
    PraiseKind.THREE_STARS -> "A lesson finished with three stars ${c.span}."
    PraiseKind.TIMING_IMPROVED -> "Timing is closer to the beat than before."
    PraiseKind.ACCURACY_IMPROVED -> "Accuracy in Perform is up on before."
    PraiseKind.MISSES_CLOSER -> "Slips are landing closer to the target than before."
    PraiseKind.EAR_IMPROVED -> "Ear games are going better than before."
    PraiseKind.LEFT_HAND_IMPROVED -> "The left hand is landing more notes than before."
    PraiseKind.CONSISTENT -> "Practice has been regular in the last seven days."
    PraiseKind.CLEAN_SONG -> "A song came out at three stars ${c.span}."
    PraiseKind.NEW_LESSONS -> "New lessons were finished for the first time ${c.span}."
    PraiseKind.QUEST_DAY -> "Pup's quest got all three stars."
}
