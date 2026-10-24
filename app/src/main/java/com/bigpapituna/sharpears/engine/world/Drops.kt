package com.bigpapituna.sharpears.engine.world

/** Where a sticker came from: a finished lesson, a song played to its end, a mystery egg hatching, or (2.9) Tomorrow's surprise. */
enum class DropSource { LESSON, SONG, EGG, GIFT }

/** One sticker given: which, from where, and whether it filled a gap in the album ([isNew]) or is a double. */
data class Drop(val sticker: Sticker, val source: DropSource, val isNew: Boolean) {
    val rarity: Rarity get() = sticker.rarity
}

/**
 * What the drops need to know about the past: how many of each sticker are owned (album gaps are the zeros),
 * drops since the last rare or shiny (the floor), and how many rolls so far (each roll gets its own random stream,
 * so the same history always rolls the same sticker; that is what lets the "one more" button preview it).
 */
data class DropHistory(val owned: Map<String, Int> = emptyMap(), val sinceRare: Int = 0, val rolls: Int = 0) {
    val unique: Int get() = owned.count { it.value > 0 }
    val total: Int get() = owned.values.sum()
    fun count(id: String): Int = owned[id] ?: 0

    /** The history once [drop] is in it. */
    fun after(drop: Drop): DropHistory = DropHistory(
        owned = owned + (drop.sticker.id to count(drop.sticker.id) + 1),
        sinceRare = if (drop.rarity.special) 0 else sinceRare + 1,
        rolls = rolls + 1,
    )
}

/**
 * Sticker drops, deterministic: [roll] is a pure function of the profile's seed and the history.
 *
 * - Rarity: common 70%, rare 25%, shiny 5% ([rarityFor]).
 * - The floor: after [FLOOR] drops in a row without a rare or shiny, the next is at least rare (rare 25 to shiny 5,
 *   the same odds between them). With the floor the long-run shares come out near 60.5% common, 32.9% rare, 6.6% shiny.
 * - An egg hatches one rarity up ([bump]).
 * - Which sticker: one of that rarity in [place] when the drop has one (a Playground lesson drops its own place's
 *   stickers); otherwise in the first place that still has gaps, spilling over to the next place when that rarity is
 *   complete there. Gaps are preferred 4 times in 5, so a double turns up now and then while the album still fills.
 */
object Drops {
    const val FLOOR = 3
    const val COMMON_ODDS = 0.70
    const val RARE_ODDS = 0.25
    const val SHINY_ODDS = 0.05
    /** How often a gap is picked over a double while gaps of the rolled rarity remain. */
    const val PREFER_NEW = 0.8

    /** The rarity for a uniform number [u] in 0..1: shiny below 0.05, rare below 0.30, common above. */
    fun rarityFor(u: Double): Rarity = when {
        u < SHINY_ODDS -> Rarity.SHINY
        u < SHINY_ODDS + RARE_ODDS -> Rarity.RARE
        else -> Rarity.COMMON
    }

    /** True when the next drop must be rare or better. */
    fun floorDue(history: DropHistory): Boolean = history.sinceRare >= FLOOR

    /** The place a drop without one fills: the first with gaps (garden, pond, concert hall), or [fallback] when the album is full. */
    fun currentPlace(owned: Map<String, Int>, fallback: Place = Place.GARDEN): Place =
        Place.entries.firstOrNull { p -> p.stickers.any { (owned[it.id] ?: 0) == 0 } } ?: fallback

    /** The rarity the next roll gives (before the sticker is picked); used by [roll] and the preview. */
    fun rarity(seed: Long, history: DropHistory, bump: Int = 0): Rarity {
        val rng = Rng.of(seed, history.rolls.toLong())
        var r = rarityFor(rng.nextDouble())
        val second = rng.nextDouble()
        if (floorDue(history) && r == Rarity.COMMON) r = if (second < SHINY_ODDS / (SHINY_ODDS + RARE_ODDS)) Rarity.SHINY else Rarity.RARE
        repeat(bump) { r = r.up() }
        return r
    }

    /**
     * The next drop for [history], from [source]: in [place] when given, else the album's current place. [bump] moves
     * the rarity up (1 for an egg). Same seed and history, same sticker.
     */
    fun roll(seed: Long, history: DropHistory, source: DropSource = DropSource.LESSON, place: Place? = null, bump: Int = 0): Drop {
        val rarity = rarity(seed, history, bump)
        val rng = Rng.of(seed, history.rolls.toLong())
        rng.nextDouble(); rng.nextDouble()                 // the two the rarity used
        val owned = history.owned
        fun gaps(list: List<Sticker>) = list.filter { (owned[it.id] ?: 0) == 0 }
        val home = place ?: currentPlace(owned, fallback = Place.entries[rng.nextInt(Place.entries.size)])
        val here = Stickers.of(home, rarity)
        val pick: Sticker = when {
            place != null -> gaps(here).takeIf { it.isNotEmpty() && rng.nextDouble() < PREFER_NEW }?.let { it[rng.nextInt(it.size)] }
                ?: here[rng.nextInt(here.size)]
            else -> {
                // The first place, from the current one on, with a gap of this rarity.
                val order = Place.entries.drop(home.ordinal) + Place.entries.take(home.ordinal)
                val spill = order.map { Stickers.of(it, rarity) }.firstOrNull { gaps(it).isNotEmpty() }
                if (spill != null && rng.nextDouble() < PREFER_NEW) gaps(spill).let { it[rng.nextInt(it.size)] }
                else here[rng.nextInt(here.size)]
            }
        }
        return Drop(pick, source, isNew = (owned[pick.id] ?: 0) == 0)
    }
}

/**
 * SplitMix64: a tiny, well-mixed random stream that is the same on every JVM and Android version (java.util.Random
 * would do, but this keeps a drop's numbers independent of how many were drawn before it).
 */
class Rng(private var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in 0 until 1. */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Uniform in 0 until [n]. */
    fun nextInt(n: Int): Int { require(n > 0); return ((nextLong() ushr 1) % n).toInt() }

    companion object {
        /** The stream for roll number [index] of [seed]. */
        fun of(seed: Long, index: Long): Rng = Rng(seed * 0x2545F4914F6CDD1DL + index * -0x61c8864680b583ebL)
    }
}
