package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.ToyKind

/*
 * The child's card: the pup speaking in the first person, two or three short lines, positives only, and one "next time,
 * let's try". Spoken aloud, so: no numbers over ten ("lots"), no digits, no symbols, no percentages and no scolding
 * words, every line at most 70 characters. Lines that would run long (a long title) fall back to a phrasing without it.
 */
internal const val KID_LINE_MAX = 70

private val KID_BANNED = Regex(
    "[" + Char(0x2014) + Char(0x2013) + Char(0xB7) + "#&/%]| - |\\b(wrong|mistakes?|errors?|fail\\w*|percent|behind|stuck|hard|tricky|bad)\\b",
    RegexOption.IGNORE_CASE,
)

/** True when [s] is a kid line the rules allow: short, a sentence, no digits, no symbols, no scolding words. */
internal fun kidOk(s: String): Boolean =
    s.length in 2..KID_LINE_MAX && s.none { it.isDigit() } && !KID_BANNED.containsMatchIn(s) &&
        s.first().isUpperCase() && s.last() in ".!?" && !s.contains("  ")

/** A title a kid's line can hold: short, letters and a little punctuation, no digits. */
private fun kidTitle(c: Ctx, id: String?): String? {
    val t = id?.let { c.title(it) } ?: return null
    return t.takeIf { it.length in 1..30 && Regex("[\\p{L}' ,.!?-]+").matches(it) && !it.contains(" - ") }
}

private fun lots(n: Long, one: String, many: String = one + "s"): String =
    if (n > 10) "lots of $many" else count(n, one, many)

internal object KidBank {
    private val praise = HashMap<PraiseKind, List<L>>()
    private val facts = HashMap<FactKind, List<L>>()
    private fun p(k: PraiseKind, vararg ls: String) { praise[k] = ls.map { l(it) } }
    private fun f(k: FactKind, vararg ls: String) { facts[k] = ls.map { l(it) } }

    fun praise(k: PraiseKind): List<L> = praise.getValue(k)
    fun fact(k: FactKind): List<L>? = facts[k]

    val OPENERS = listOf(
        l("{name}, I am so glad you came to see me!"),
        l("Hello, {name}! I am so happy you are here!"),
        l("{name}, it is lovely to see you!"),
        l("Hooray, you are here, {name}!"),
    )
    val EMPTY_SECOND = listOf(
        l("Let's make some music soon!"),
        l("The piano is waiting for us!"),
        l("We can play whenever you like!"),
        l("I will wait right here for you!"),
    )
    val CLOSERS = listOf(
        l("I had so much fun with you!"),
        l("You are the best piano buddy!"),
        l("See you soon, piano star!"),
        l("Woof! That was a good one!"),
        l("Thank you for playing with me!"),
        l("Your music makes my tail wag!"),
        l("I love playing with you!"),
        l("What a lovely time we had!"),
        l("See you soon, {name}!"),
        l("{name}, you make my tail wag!"),
    )
    /** The last resort for a line that will not fit. */
    val SAFE = listOf("You are doing so well!", "I love hearing you play!", "What a good time we had!", "You are a piano star!")

    val STONE = listOf(
        l("Next time, let's try {title}!"),
        l("Next time, let's play {title}, nice and slow."),
        l("Let's start with {title} next time!"),
        l("Next time, I will wait for you at {title}!"),
        l("Next time, let's find the next stone on the path!"),
        l("Next time, let's walk to the next stone together!"),
        l("Next time, let's see what is on the next stone!"),
        l("Let's go to the next stone next time!"),
    )
    val REPLAY = listOf(
        l("Next time, let's play {title} again, nice and slow."),
        l("Let's go back to {title} next time and win more stars!"),
        l("{Title} again next time? I think you will shine!"),
        l("Next time, let's play {title} once more. I know you can!"),
        l("Next time, let's play a lesson again for even more stars!"),
        l("Next time, let's go back and catch more stars!"),
        l("Let's replay a favourite next time and shine even more!"),
        l("Next time, let's visit an old friend, nice and slow!"),
    )
    val TOYS = mapOf(
        ToyKind.JAM to listOf(
            l("Next time, let's jam together!"),
            l("Let's make up music in Jam next time!"),
            l("Next time, let's jam and make up a tune!"),
            l("Jam time! Next time, let's make up a song!"),
        ),
        ToyKind.PUP_SAYS to listOf(
            l("Next time, let's play Pup says!"),
            l("Let's play Pup says next time. Can you copy me?"),
            l("Next time, let's see if you can copy me in Pup says!"),
            l("Pup says play! Next time, let's try it!"),
        ),
        ToyKind.CLAP_ALONG to listOf(
            l("Next time, let's clap along together!"),
            l("Let's clap along to the drum next time!"),
            l("Next time, let's clap the beat with the drum!"),
            l("Clap, clap, clap! Next time, let's clap along!"),
        ),
        ToyKind.ARCADE to listOf(
            l("Next time, let's play a game together!"),
            l("Let's pop some balloons next time!"),
            l("Next time, let's play in the arcade!"),
            l("Pop, hop, boop! Next time, let's play a game!"),
        ),
    )

    fun sizes(): Map<String, Int> = praise.entries.associate { "kid praise ${it.key}" to it.value.size } +
        facts.entries.associate { "kid fact ${it.key}" to it.value.size } +
        mapOf("kid openers" to OPENERS.size, "kid closers" to CLOSERS.size, "kid next: next stone" to STONE.size,
            "kid next: a replay" to REPLAY.size) + TOYS.entries.associate { "kid next: toy ${it.key}" to it.value.size }

    init {
        p(PraiseKind.FINALE,
            "{name}, you finished {page}! The fireworks were for you!", "You played the big finale of {page}, {name}! Wow!",
            "{Page} is all done! What a big show you gave!", "Fireworks for you! You finished {page}!",
            "{name}, you played a whole big finale! Fireworks for you!", "A whole page is done and the finale played! Hooray!")
        p(PraiseKind.FIRST_CHORD,
            "{name}, you played your very first chord!", "Lots of keys at once! Your first chord, {name}!",
            "Your first chord! I did a happy dance!", "Keys together at the same time! What a first chord!",
            "{name}, you played a chord in {title}! Your first one!")
        p(PraiseKind.STUCK_WITH_IT,
            "{name}, you kept going with {title} and you did it!", "{Title} took some tries, and you kept going, {name}!",
            "You tried and tried, and {title} worked! Good going!", "I am so proud of you for sticking with it!",
            "{name}, you kept trying and you did it!", "You never gave up, and it worked! Hooray!")
        p(PraiseKind.SHINY_STICKER,
            "{name}, you found a shiny sticker! It sparkles!", "A shiny sticker for you, {name}! Look at it glow!",
            "Ooh, something shiny in your album!", "You got a shiny sticker {span}! Lucky you!")
        p(PraiseKind.RARE_STICKER,
            "{name}, you found a rare sticker! Wow!", "A rare sticker for you, {name}! Hooray!",
            "Look at your new rare sticker! So special!", "You got a rare sticker {span}! Lucky you!")
        p(PraiseKind.GOAL_STREAK,
            "{name}, you did your goal {days} in a row!", "Your goal, {days} in a row, {name}! Amazing!",
            "{Days} in a row! You are on a streak!", "You keep reaching your goal! What a streak!")
        p(PraiseKind.THREE_STARS,
            "{name}, three stars on {title}! You shine!", "{Title} got three stars, {name}! Woof!",
            "Three shiny stars for {title}! Great job!", "Three stars! I am doing a happy dance!",
            "{name}, you got three stars! Great job!")
        p(PraiseKind.TIMING_IMPROVED,
            "{name}, your beat is getting steady! I can feel it.", "You are playing right on the beat more, {name}!",
            "Tap, tap, tap! Your beat is so steady now!", "Your playing keeps the beat so well!")
        p(PraiseKind.ACCURACY_IMPROVED,
            "{name}, more of your notes are just right now!", "You played more notes just right, {name}! Nice!",
            "Your playing is getting better and better!", "So many right notes! You are on a roll!")
        p(PraiseKind.MISSES_CLOSER,
            "{name}, your fingers are finding the keys faster!", "You are so close to the right keys now, {name}!",
            "Your hands know the piano better and better!", "Your fingers are getting to know the keys!")
        p(PraiseKind.EAR_IMPROVED,
            "{name}, your ears are getting so sharp!", "You heard so many notes right, {name}! Clever ears!",
            "Your listening ears are working so well!", "Good ears! You hear more and more!")
        p(PraiseKind.LEFT_HAND_IMPROVED,
            "{name}, your left paw is getting strong!", "Your left hand is learning fast, {name}!",
            "Both paws are working together now! Hooray!", "Look at your left hand go!")
        p(PraiseKind.CONSISTENT,
            "{name}, you came to play on lots of days! I love that!", "You keep coming back to play with me, {name}! Hooray!",
            "Another day at the piano with you! Best day!", "You are a piano star! You come to play so much!")
        p(PraiseKind.CLEAN_SONG,
            "{name}, you played {song} so well! Three stars!", "{Song} sounded so lovely, {name}!",
            "You played {song} like a star!", "Three stars for a whole song! Woof woof!")
        p(PraiseKind.NEW_LESSONS,
            "{name}, you finished {title} for the first time!", "A new lesson done: {title}! Hooray, {name}!",
            "You learned {title}! Brand new!", "{name}, you learned something brand new!", "A brand new lesson is done! Hooray!")
        p(PraiseKind.QUEST_DAY,
            "{name}, you got all three quest stars! Hooray!", "All the stars on my quest, {name}! You did it!",
            "Every quest star is yours! Hooray!", "You finished my whole quest! What a pup helper!")
        p(PraiseKind.STEADIER_TEMPO,
            "{name}, your beat is so steady now!", "You and the beat are best friends, {name}!",
            "Tap, tap, tap! You keep the beat like a drummer!", "Your playing marches right along with the beat!")
        p(PraiseKind.FINDS_KEYS_FASTER,
            "{name}, your fingers find the keys so fast now!", "Zoom! You found the keys super quick, {name}!",
            "Your hands know where the keys live now!", "You find the keys faster and faster! Woof!")

        f(FactKind.NEW_LESSONS,
            "You finished {title} for the first time!", "You learned something brand new {span}!",
            "A new lesson is done, and it is all yours!", "You finished {lessons} for the first time!")
        f(FactKind.SONG_RUNS,
            "You played {title} all the way to the end!", "You played songs all the way through {span}!",
            "{Title} sounded great, all the way to the end!", "You played {songs} from start to finish!")
        f(FactKind.STICKERS,
            "You got {stickers} {span}! Look at your album!", "Your sticker album is growing!",
            "Stickers for you! {Stickers} dropped {span}!", "More stickers for the album, and they are all yours!")
        f(FactKind.QUEST_STARS,
            "You earned {stars} on my quest!", "My quest gave you {stars}! Hooray!",
            "Quest stars for you! {Stars}!", "You are a quest star helper!")
        f(FactKind.GAMES,
            "You played {game} with me, and you were so quick!", "We played games together {span}!",
            "Pop, hop, boop! What fun games we played!", "You played the arcade games like a star!")
        f(FactKind.TOY_MINUTES,
            "We had fun in the play corner {span}!", "You jammed and played games with me!",
            "What a fun time we had in the toys!", "Playing in the toys is music too!")
        f(FactKind.PRACTICE_MINUTES,
            "You played with me {span}, and it was lovely!", "We made music together {span}!",
            "Thank you for coming to play with me!", "You sat down and played, and I loved it!")
        f(FactKind.DAYS_PRACTISED,
            "You came to play on {days} this week!", "You keep coming back to play with me!",
            "You came back to play again and again!", "A whole week of music with you!")
        f(FactKind.GOAL_DAYS,
            "You reached your goal {span}! Hooray!", "Your goal is done, and I am so proud!",
            "You did your goal again! High paw!", "Goal done! You are a star!")
        f(FactKind.EAR_ROUNDS,
            "Your ears got {right} right {span}!", "You listened so well {span}!",
            "Clever ears! You heard lots of notes right!", "You are a good listener!")
        f(FactKind.CLAP,
            "You clapped along with the drum!", "Clap, clap, clap! You kept the beat!",
            "You clapped right along with me!", "Your claps were so good!")
        f(FactKind.LESSONS_REPLAYED,
            "You played {title} again, and it got even better!", "Going back to a favourite is a great idea!",
            "You went back to a lesson you love!", "Playing old favourites again feels good!")
        f(FactKind.SESSIONS,
            "You came to play with me {times} {span}!", "You came back to play again {span}!",
            "More than one visit to the piano {span}!", "Back at the piano again! I missed you!")
    }
}

private fun kidVars(c: Ctx): Map<String, String?> {
    val short = c.name.substringBefore(' ').take(12).ifBlank { "piano star" }
    return c.common() + ("name" to short)
}

/** One of [bank] for [slot] that passes [kidOk]; null when none does. */
private fun kidSay(c: Ctx, slot: String, bank: List<L>, vars: Map<String, String?>, extra: Int = 0): String? {
    val usable = c.usable(bank, vars).filter(::kidOk)
    return if (usable.isEmpty()) null else usable[c.index(slot, usable.size, extra)]
}

private fun praiseKidVars(c: Ctx, p: Praise, allowName: Boolean): Map<String, String?> {
    val v = p.value
    return kidVars(c) + mapOf(
        "title" to kidTitle(c, p.lessons.firstOrNull()), "song" to kidTitle(c, p.songs.firstOrNull()),
        "page" to p.page?.let(c::pageLower)?.takeIf { Regex("[\\p{L}' ,.!?-]+").matches(it) },
        "days" to lots(v, "day"),
    ) + if (allowName) emptyMap() else mapOf("name" to null)
}

private fun factKidVars(c: Ctx, f: Fact): Map<String, String?> {
    val v = f.value
    return kidVars(c) + mapOf(
        "title" to kidTitle(c, f.lessons.firstOrNull() ?: f.songs.firstOrNull()),
        "songs" to lots(v, "song"), "lessons" to lots(v, "new lesson"),
        "stickers" to lots(v, "sticker"), "stars" to lots(v, "quest star"),
        "days" to lots(v, "day"), "times" to if (v > 10) "lots of times" else timesWord(v),
        "right" to v.takeIf { it > 0 }?.let { if (it > 10) "lots" else words(it) },
    )
}

private fun kidPraiseLine(c: Ctx, p: Praise, allowName: Boolean, extra: Int = 0): String? =
    kidSay(c, "kid praise ${p.kind}", KidBank.praise(p.kind), praiseKidVars(c, p, allowName), extra)

private fun kidFactLine(c: Ctx, f: Fact): String? {
    val bank = KidBank.fact(f.kind) ?: return null
    if (f.kind == FactKind.PRACTICE_MINUTES && f.value == 0L) return null
    return kidSay(c, "kid fact ${f.kind}", bank.map { it }, factKidVars(c, f))
}

private enum class NextSource { STONE, REPLAY, TOY }

/** The one "next time" line: the next stone, a replay for stars, or a toy. Never null. */
internal fun kidNextTry(c: Ctx): String {
    val f = c.f
    val vars = kidVars(c)
    val stone = f.candidates.firstOrNull { it.action == ActionId.SET_NEXT && it.params["lesson"] != null }?.params?.get("lesson")
    val replay: String? = f.candidates.firstOrNull { it.action == ActionId.REPLAY_FOR_STARS }?.params?.get("lesson")
        ?: f.flags.firstOrNull { it.kind == FlagKind.LOW_STARS }?.lessons?.firstOrNull()
        ?: f.flags.firstOrNull { it.kind == FlagKind.STUCK_LESSON }?.lessons?.firstOrNull()
        ?: f.stats.lessons.firstOrNull { (it.stars ?: 3) in 1..2 }?.lessonId
    val moving = f.flags.any { it.kind == FlagKind.CRUISING || it.kind == FlagKind.READY_FOR_ROAD }
    val toyOk = f.flags.none { it.kind == FlagKind.TOY_HEAVY }

    val sources = ArrayList<NextSource>()
    repeat(if (stone != null || moving) 2 else 1) { sources += NextSource.STONE }
    if (replay != null) repeat(2) { sources += NextSource.REPLAY }
    if (toyOk) sources += NextSource.TOY
    val source = sources[c.index("kid next source", sources.size)]

    fun pick(slot: String, bank: List<L>, id: String?): String? {
        val title = kidTitle(c, id)
        return kidSay(c, slot, bank, vars + ("title" to title)) ?: if (title != null) kidSay(c, slot, bank, vars + ("title" to null)) else null
    }
    val line = when (source) {
        NextSource.STONE -> pick("kid next stone", KidBank.STONE, stone)
        NextSource.REPLAY -> pick("kid next replay", KidBank.REPLAY, replay)
        NextSource.TOY -> {
            val used = f.stats.toys.filter { it.minutes > 0 }.sortedByDescending { it.minutes }.map { it.toy }
            val fresh = ToyKind.entries.filter { it != used.firstOrNull() }
            val toy = fresh[c.index("kid next toy", fresh.size)]
            kidSay(c, "kid next toy ${toy}", KidBank.TOYS.getValue(toy), vars)
        }
    }
    return line ?: "Next time, let's play together again!"
}

/** The child's card. */
internal fun kidCard(c: Ctx, writtenAt: Long): KidCard {
    val f = c.f
    val lines = ArrayList<String>()
    val usedPraise = HashSet<PraiseKind>()

    // The praise: the first line may use the name, a second praise reads without it.
    var named = false
    for (p in f.praise) {
        if (lines.size >= 2) break
        val line = kidPraiseLine(c, p, allowName = !named) ?: kidPraiseLine(c, p, allowName = false) ?: continue
        if (line.contains(kidVars(c).getValue("name")!!)) named = true
        lines += line; usedPraise += p.kind
    }
    // Then what happened.
    for (fact in f.facts) {
        if (lines.size >= 2) break
        if (fact.kind == FactKind.NEW_LESSONS && PraiseKind.NEW_LESSONS in usedPraise) continue
        if (fact.kind == FactKind.STICKERS && (PraiseKind.SHINY_STICKER in usedPraise || PraiseKind.RARE_STICKER in usedPraise)) continue
        val line = kidFactLine(c, fact) ?: continue
        lines += line
    }
    // Nothing to say yet: a warm hello.
    if (lines.isEmpty()) {
        kidSay(c, "kid opener", KidBank.OPENERS, kidVars(c))?.let { lines += it }
        kidSay(c, "kid empty second", KidBank.EMPTY_SECOND, kidVars(c))?.let { lines += it }
    } else {
        val noName = kidVars(c) + ("name" to if (named) null else kidVars(c)["name"])
        kidSay(c, "kid closer", KidBank.CLOSERS, noName)?.let { lines += it }
    }
    while (lines.size < 2) lines += KidBank.SAFE[c.index("kid safe", KidBank.SAFE.size, lines.size)]
    return KidCard(lines.take(3), kidNextTry(c), Brief.Writer.TEMPLATE, writtenAt)
}
