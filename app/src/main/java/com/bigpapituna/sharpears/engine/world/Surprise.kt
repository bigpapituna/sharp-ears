package com.bigpapituna.sharpears.engine.world

import java.time.LocalDate

/** What Tomorrow's surprise holds (2.9): a sticker for the album, something to wear the kid has not got, or sparkles. */
enum class GiftKind { STICKER, OUTFIT, SPARKLES }

/**
 * Tomorrow's surprise (2.9), kept in the world: the day a gift was [wrappedOn] and whether it was [opened]. One is wrapped
 * on a kid's finish screen once the day's goal is met; it opens on the kid home from the next calendar day on.
 */
data class Gift(val wrappedOn: LocalDate? = null, val opened: Boolean = true) {
    /** Wrapped and not opened yet. */
    val pending: Boolean get() = wrappedOn != null && !opened
}

/**
 * The surprise's rules. Kids only (the caller's rule); no countdown anywhere: before the next day the gift is simply
 * "Something for tomorrow", and on the next day it is ready, whatever the hour.
 *
 * - One a day: nothing is wrapped twice on one day.
 * - Never two waiting: while one is wrapped and not opened, no other is wrapped (a kid who skips the home keeps the one).
 * - It opens on a later calendar day than it was wrapped, never the same day.
 * - What is inside is rolled when it opens, from the world's seed and the day it was wrapped ([roll]): a sticker 45, sparkles
 *   40, something to wear 15 (the outfit is the rare one). An outfit with nothing left to give turns into sparkles.
 */
object Surprise {
    const val SPARKLES = 25
    /** The table, by weight: the outfit is the rarest. */
    val TABLE: List<Pair<GiftKind, Int>> = listOf(GiftKind.STICKER to 45, GiftKind.SPARKLES to 40, GiftKind.OUTFIT to 15)
    private const val SALT = 0x5EED_6F17L

    /** A gift may be wrapped on [today]: the [goalMet], none waiting, none wrapped today already. */
    fun canWrap(g: Gift, today: LocalDate, goalMet: Boolean): Boolean = goalMet && !g.pending && g.wrappedOn != today

    /** The gift wrapped on [today] when [canWrap], else [g] as it was. */
    fun wrap(g: Gift, today: LocalDate, goalMet: Boolean): Gift = if (canWrap(g, today, goalMet)) Gift(today, opened = false) else g

    /** Wrapped today and waiting: the finish screens show it as "Something for tomorrow". */
    fun wrappedToday(g: Gift, today: LocalDate): Boolean = g.pending && g.wrappedOn == today

    /** Ready to open on [today]: waiting, and wrapped on an earlier day. */
    fun ready(g: Gift, today: LocalDate): Boolean = g.pending && today.isAfter(g.wrappedOn)

    /** The kind for a uniform [u] in 0 until 1, by [TABLE]'s weights. */
    fun kindFor(u: Double): GiftKind {
        val total = TABLE.sumOf { it.second }
        var at = u * total
        for ((k, w) in TABLE) { if (at < w) return k; at -= w }
        return TABLE.last().first
    }

    /** What the gift wrapped on [day] holds, for the world's [seed]: the same every time it is asked. */
    fun roll(seed: Long, day: LocalDate): GiftKind = kindFor(Rng.of(seed xor SALT, day.toEpochDay()).nextDouble())

    /**
     * Something to wear the kid has not got: one of the practice track's sets ([Wardrobe.UNITS], commons along the track)
     * with nothing of it open, picked from [seed] and [day]; null when every one is open.
     */
    fun outfit(seed: Long, day: LocalDate, open: Set<WearItem>): List<WearItem>? {
        val sets = Wardrobe.UNITS.filter { set -> set.first().earn is Earn.Practice && set.none { it in open } }
        if (sets.isEmpty()) return null
        return sets[Rng.of(seed xor SALT, day.toEpochDay() + 1).nextInt(sets.size)]
    }

    /** What the pup says as it opens (no numbers for a kid). */
    fun line(kind: GiftKind): String = when (kind) {
        GiftKind.STICKER -> "A sticker for your album!"
        GiftKind.OUTFIT -> "Something new to wear!"
        GiftKind.SPARKLES -> "A big pile of sparkles!"
    }

    const val WRAPPED_TITLE = "Something for tomorrow"
    const val WRAPPED_LINE = "A surprise from the pup. It opens tomorrow."
    const val READY_TITLE = "Your surprise is ready!"
    const val READY_LINE = "Tap the present to open it."
}
