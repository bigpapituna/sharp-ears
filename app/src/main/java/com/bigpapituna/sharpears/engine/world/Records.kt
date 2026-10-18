package com.bigpapituna.sharpears.engine.world

/** A record on the shelf, from a song's best Perform run: bronze from 60%, silver from 80%, gold from 95%. */
enum class Medal(val from: Int, val label: String) {
    BRONZE(60, "Bronze"), SILVER(80, "Silver"), GOLD(95, "Gold");

    companion object {
        /** The medal a best of [accuracy] (0..100) earns, or null under bronze. */
        fun of(accuracy: Int?): Medal? = if (accuracy == null) null else entries.lastOrNull { accuracy >= it.from }
    }
}

/** One song on the shelf. */
data class ShelfRecord(val songId: String, val best: Int) {
    val medal: Medal? get() = Medal.of(best)
}

/** A song's medal went up (or it got its first one): shown as a moment on the recap. */
data class RecordUp(val songId: String, val before: Medal?, val after: Medal)

object Records {
    /** A new best of [accuracy] on top of [before] (null when none): the higher one. */
    fun best(before: Int?, accuracy: Int): Int = maxOf(before ?: -1, accuracy.coerceIn(0, 100))

    /** The medal change a run makes, or null when it did not move up. */
    fun up(songId: String, before: Int?, accuracy: Int): RecordUp? {
        val was = Medal.of(before)
        val now = Medal.of(best(before, accuracy)) ?: return null
        return if (was == null || now > was) RecordUp(songId, was, now) else null
    }

    /** The shelf: every song with a medal, gold first, then by best, then by id (stable). */
    fun shelf(bests: Map<String, Int>): List<ShelfRecord> = bests.map { (id, b) -> ShelfRecord(id, b) }
        .filter { it.medal != null }
        .sortedWith(compareByDescending<ShelfRecord> { it.medal!!.ordinal }.thenByDescending { it.best }.thenBy { it.songId })

    /** "Songs I can play": the ids on the shelf, in shelf order. */
    fun canPlay(bests: Map<String, Int>): List<String> = shelf(bests).map { it.songId }

    /** How many of each medal, for "3 gold, 2 silver". */
    fun counts(bests: Map<String, Int>): Map<Medal, Int> = Medal.entries.associateWith { m -> bests.values.count { Medal.of(it) == m } }
}
