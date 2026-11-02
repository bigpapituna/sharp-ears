package com.bigpapituna.sharpears.engine.praise

import com.bigpapituna.sharpears.engine.CHEER_TITLES

/** Where a bag's history is kept between sessions: one small string per bag (Prefs, the profile's own file). */
interface PraiseStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/**
 * Shuffle bags for praise (QA praise pass: "a lot of the audio seems repetitive"): one bag per moment, so every line of the
 * pool is heard once before any is heard again, and a new round does not open with a line of the last few the old one ended
 * on ([carry]: a third of the pool, at least one, so never the same line twice in a row). The history ([bind] a [PraiseStore])
 * is kept across screens, lessons and app restarts, so a new lesson does not start the pool over. Android-free; a test makes
 * its own.
 *
 * What is kept per bag is "carry;played" (hex hashes of the lines, in the order heard): the tail of the last round and this
 * round so far. A line added or dropped by an update just joins or leaves the round; nothing here can fail a pick.
 */
class PraiseBags(private val random: java.util.Random = java.util.Random(), private var store: PraiseStore? = null) {
    private class Round(val carry: List<Int>, val played: LinkedHashSet<Int>)
    private val rounds = HashMap<String, Round>()

    /** A profile opened (or none): its history from now on. The bags read it afresh. */
    @Synchronized fun bind(store: PraiseStore?) { this.store = store; rounds.clear() }

    /**
     * The next line of [pool] for bag [key]: one not heard this round and not among the carried tail of the last. A line in
     * [prefer] (a subset of the pool) goes first while one is still unheard this round.
     */
    @Synchronized fun next(key: String, pool: List<String>, prefer: Set<String> = emptySet()): String {
        require(pool.isNotEmpty()) { "empty pool for $key" }
        val lines = pool.distinctBy { it.hashCode() }
        var r = round(key)
        var fresh = lines.filter { it.hashCode() !in r.played }
        if (fresh.isEmpty()) {
            // The round is over: its last few are carried into the next, which opens with something else.
            r = Round(r.played.toList().takeLast(carry(lines.size)), LinkedHashSet()).also { rounds[key] = it }
            fresh = lines
        }
        val last = r.played.lastOrNull() ?: r.carry.lastOrNull()
        if (fresh.size > 1) fresh = fresh.filter { it.hashCode() !in r.carry }.ifEmpty { fresh.filter { it.hashCode() != last } }.ifEmpty { fresh }
        val line = (fresh.filter { it in prefer }.ifEmpty { fresh }).let { it[random.nextInt(it.size)] }
        r.played += line.hashCode()
        store?.write(key, "${r.carry.joinToString(",") { hex(it) }};${r.played.joinToString(",") { hex(it) }}")
        return line
    }

    /** [next] over the positions 0 until [size], for lists a screen indexes (a kid recap's three titles). */
    // ponytail: positions go through the string bag as "#n" rather than a second int implementation.
    fun index(key: String, size: Int): Int = next(key, List(size) { "#$it" }).substring(1).toInt()

    private fun round(key: String): Round = rounds.getOrPut(key) {
        val parts = store?.read(key)?.split(';')
        fun ints(i: Int) = parts?.getOrNull(i)?.split(',')?.mapNotNull(::unhex).orEmpty()
        Round(ints(0), LinkedHashSet(ints(1)))
    }

    // ponytail: lines are tracked by String.hashCode (a clash inside one pool of a dozen lines is a 1 in 300 million shrug) so a reworded line simply rejoins the round; ids would need a catalogue.
    private fun hex(i: Int) = Integer.toHexString(i)
    private fun unhex(s: String): Int? = s.takeIf { it.isNotEmpty() }?.let { runCatching { Integer.parseUnsignedInt(it, 16) }.getOrNull() }

    companion object {
        /** How many of a round's last lines the next round keeps off its opening: a third of the pool, at least one. */
        fun carry(poolSize: Int): Int = if (poolSize < 2) 0 else (poolSize / 3).coerceAtLeast(1)
    }
}

/**
 * The app's one set of bags (the pup's cheer pops, the cheer bars' titles, a kid's recap words; the coach's lines on a build
 * with it): MainActivity binds the open profile's store each time a profile opens.
 */
object Praise {
    val bags = PraiseBags()

    /** The cheer bar's title now ("Nice work!", "Spot on!"): every one of [CHEER_TITLES] before any comes round again. */
    fun title(b: PraiseBags = bags): String = b.next("title", CHEER_TITLES)
}
