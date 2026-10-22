package com.bigpapituna.sharpears.engine.arcade

import com.bigpapituna.sharpears.engine.MiniJson
import java.time.LocalDate

/** One game's record for a kid: best stars, the level it is played at next, rounds played, its sticker earned. */
data class GameRecord(val best: Int = 0, val level: Int = 0, val rounds: Int = 0, val sticker: Boolean = false, val last: Int = 0)

/**
 * A kid's arcade (kept per profile in the "play" file, data/ArcadeStore.kt): a record per game, the sparkles the ledger
 * held when the arcade was first opened ([base], the unlock rule counts from there), how many games the kid has seen open
 * ([seen], so a newly opened one is announced once), and the songs Song painter has finished ([painted]).
 */
data class ArcadeState(
    val games: Map<String, GameRecord> = emptyMap(), val base: Int? = null, val seen: Int = ArcadeUnlocks.FIRST,
    val painted: Set<String> = emptySet(),
) {
    fun of(game: ArcadeGame): GameRecord = games[game.id] ?: GameRecord()

    fun encode(): String = MiniJson.write(linkedMapOf(
        "games" to games.mapValues { (_, r) -> linkedMapOf("best" to r.best, "level" to r.level, "rounds" to r.rounds, "sticker" to r.sticker, "last" to r.last) },
        "base" to base, "seen" to seen, "painted" to painted.sorted(),
    ))

    companion object {
        /** Reads [encode]'s text; an empty arcade for nothing or anything it cannot read. */
        fun decode(text: String?): ArcadeState = text?.let { t ->
            runCatching {
                val o = MiniJson.read(t) as Map<*, *>
                fun int(v: Any?) = (v as? Number)?.toInt() ?: 0
                val games = (o["games"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                    val m = v as? Map<*, *> ?: return@mapNotNull null
                    k.toString() to GameRecord(int(m["best"]).coerceIn(0, 3), int(m["level"]).coerceAtLeast(0), int(m["rounds"]),
                        m["sticker"] == true, int(m["last"]).coerceIn(0, 3))
                }.toMap()
                ArcadeState(games, (o["base"] as? Number)?.toInt(), (o["seen"] as? Number)?.toInt() ?: ArcadeUnlocks.FIRST,
                    (o["painted"] as? List<*>).orEmpty().map { it.toString() }.toSet())
            }.getOrNull()
        } ?: ArcadeState()
    }
}

/**
 * What a round's end did for the kid: its [stars], whether it was the game's [firstThree] (its sticker), a [newBest], the
 * level before and after, and for Song painter whether this song's picture was [paintedNew].
 */
data class RoundEnd(val stars: Int, val firstThree: Boolean, val newBest: Boolean, val levelBefore: Int, val levelAfter: Int, val paintedNew: Boolean = false)

object ArcadeBook {
    /** [state] after a round of [result]: the best, the next level, the rounds, the sticker on the first three stars. */
    fun record(state: ArcadeState, result: RoundResult): Pair<ArcadeState, RoundEnd> {
        val g = result.game
        val r = state.of(g)
        val stars = result.stars
        val firstThree = stars >= 3 && !r.sticker
        val level = ArcadeLevels.next(g, r.level, result)
        val song = if (g == ArcadeGame.PAINTER && stars >= 2) ArcadeSongs.of(result.level).id else null
        val paintedNew = song != null && song !in state.painted
        val next = r.copy(best = maxOf(r.best, stars), level = level, rounds = r.rounds + 1, sticker = r.sticker || stars >= 3, last = stars)
        return state.copy(games = state.games + (g.id to next), painted = state.painted + listOfNotNull(song)) to
            RoundEnd(stars, firstThree, stars > r.best, r.level, level, paintedNew)
    }
}

/**
 * Which games are open, like the wardrobe: by practice. The first [FIRST] at once; then one more for every [STEP] sparkles
 * the practice ledger (engine/world/Wardrobe.kt) gains after the arcade's first open, about two sessions of a typical kid
 * (a round of a game counts like a song to the end, and its minutes are practice, so playing opens games too).
 */
object ArcadeUnlocks {
    const val FIRST = 3
    const val STEP = 40

    /** The arcade's state on its first open with the ledger at [sparkles]: counted from there. */
    fun opened(state: ArcadeState, sparkles: Int): ArcadeState = if (state.base != null) state else state.copy(base = sparkles)

    /** How many games are open with the ledger at [sparkles]. */
    fun open(state: ArcadeState, sparkles: Int): Int {
        val since = (sparkles - (state.base ?: sparkles)).coerceAtLeast(0)
        return (FIRST + since / STEP).coerceAtMost(ArcadeGame.entries.size)
    }

    fun isOpen(game: ArcadeGame, state: ArcadeState, sparkles: Int): Boolean = game.ordinal < open(state, sparkles)

    /** How far the next game is along, 0..1 (the locked tile's ring), or null when every game is open. */
    fun towardNext(state: ArcadeState, sparkles: Int): Float? {
        if (open(state, sparkles) >= ArcadeGame.entries.size) return null
        val since = (sparkles - (state.base ?: sparkles)).coerceAtLeast(0)
        return (since % STEP).toFloat() / STEP
    }
}

/**
 * Pup's quest asks for a game every other day (2.7): on an even day, one of the open games, turn by turn, takes the place
 * of "Copy the pup three times". Null on the other days, or with nothing open.
 */
object ArcadeQuest {
    fun pick(day: LocalDate, open: List<ArcadeGame>): ArcadeGame? {
        if (open.isEmpty() || day.toEpochDay() % 2 != 0L) return null
        return open[((day.toEpochDay() / 2) % open.size).toInt()]
    }

    /** The quest task's words: "Play Balloon pop". */
    fun title(game: ArcadeGame) = "Play ${game.title}"
}
