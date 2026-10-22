package com.bigpapituna.sharpears.engine.arcade

import com.bigpapituna.sharpears.engine.isBlack

/*
 * The kid arcade (Roy: "games where they play keys and pop balloons, or play keys that make things grow or build; not
 * only lessons but games that help them remember what key is where, work their dexterity, and potentially learn songs").
 * Pure rules, JVM-tested; ui/arcade draws them and hears the piano through the Playground's ear (strict keys).
 *
 * Every game is a round of 60 to 90 seconds in one of two shapes:
 *  - a stream ([StreamRound]): targets come on a clock and go by on their own (balloons rise, treats fly past, moles
 *    peek); a key that matches a live target hits it. Nothing waits, so nothing can stick; two misses in a row light the
 *    next targets' keys until a hit.
 *  - a call ([CallRound]): the pup calls a short line of keys (a walk, a crossing, a song's bite), shows it first, and
 *    waits for it note by note. A slip or a quiet beat is a stuck beat: the first lights the key, the second has the pup
 *    play it, the third lets it go (the pup plays it and the line moves on). A round never dead-ends.
 * Stars come from [ArcadeStars], a pure rule per game; the level a game is played at moves with them ([ArcadeLevels]).
 */

/** What a game teaches, for the hub's grown-up line and the brief. */
enum class Skill { KEY_MEMORY, SEQUENCES, SPEED, SCALES_AND_HANDS, SONGS, REACTION, KEY_LOCATION }

/** The two shapes of round. */
enum class RoundShape { STREAM, CALL }

/**
 * The games, in the order the hub opens them ([ArcadeUnlocks]): the first three at once, then one more every
 * [ArcadeUnlocks.STEP] sparkles. [id] is saved and logged (the brief's GameRound), [title] is what a kid sees and hears.
 * [maxLevel] is the highest level ([ArcadeLevels]); the song games have one level per song.
 */
enum class ArcadeGame(val id: String, val title: String, val skill: Skill, val shape: RoundShape, val maxLevel: Int = 4) {
    BALLOONS("balloons", "Balloon pop", Skill.KEY_MEMORY, RoundShape.STREAM),
    GARDEN("garden", "Grow the garden", Skill.SEQUENCES, RoundShape.CALL),
    FEED("feed", "Feed the pup", Skill.REACTION, RoundShape.STREAM),
    TOWER("tower", "Build the tower", Skill.SPEED, RoundShape.CALL),
    MOLES("moles", "Boop the moles", Skill.KEY_LOCATION, RoundShape.STREAM),
    FROGS("frogs", "Frog hop", Skill.SCALES_AND_HANDS, RoundShape.CALL),
    FIREWORKS("fireworks", "Fireworks", Skill.SONGS, RoundShape.CALL, maxLevel = ArcadeSongs.ALL.size - 1),
    PAINTER("painter", "Song painter", Skill.SONGS, RoundShape.CALL, maxLevel = ArcadeSongs.ALL.size - 1),
    BRIDGE("bridge", "Rainbow bridge", Skill.SCALES_AND_HANDS, RoundShape.CALL);

    companion object {
        fun byId(id: String?): ArcadeGame? = entries.firstOrNull { it.id == id }
    }
}

/** Which side of middle C a target wants: any, below it (the left hand), or middle C and up (the right hand). */
enum class Side {
    ANY, LOW, HIGH;

    fun allows(midi: Int): Boolean = when (this) { ANY -> true; LOW -> midi < ArcadeKeys.MIDDLE_C; HIGH -> midi >= ArcadeKeys.MIDDLE_C }
}

/**
 * One thing to play: its [keys] (one, or two for "two at once"), the [side] of the piano it wants, and for a stream when it
 * comes ([atMs] after the stream starts) and how long it stays ([lifeMs]); [lane] is where across the field it shows
 * (0..1), null: over its own key. In a call, [phrase] and [index] place it in its line.
 */
data class Target(
    val id: Int, val keys: List<Int>, val side: Side = Side.ANY, val atMs: Long = 0, val lifeMs: Long = 0,
    val lane: Float? = null, val phrase: Int = 0, val index: Int = 0,
) {
    val pair: Boolean get() = keys.size > 1
    /** The key a demo, a nudge or a let go plays (the first). */
    val key: Int get() = keys.first()

    /** Whether [midi] is one of this target's keys: the same letter in any octave, on the side it wants. */
    fun takes(midi: Int): Boolean = side.allows(midi) && keys.any { Math.floorMod(it - midi, 12) == 0 }

    /** Which of this target's keys [midi] stands for (same letter), or null. */
    fun keyFor(midi: Int): Int? = if (!side.allows(midi)) null else keys.firstOrNull { Math.floorMod(it - midi, 12) == 0 }
}

/**
 * What a round tells the screen, in order. The screen plays the demo's keys (the mic deaf), lights what a nudge points
 * at, plays a let go's key for the kid, and draws everything else.
 */
sealed interface Cue {
    /** The pup plays [keys] [gapMs] apart after [leadMs] (his words first), lighting each; the mic is deaf till it ends. */
    data class Demo(val keys: List<Int>, val gapMs: Long, val leadMs: Long, val first: Boolean) : Cue
    /** The demo is over: the kid's turn. */
    data class Listen(val first: Boolean) : Cue
    /** A stream's target came. [helped]: its keys glow (two missed in a row). */
    data class Spawned(val target: Target, val helped: Boolean) : Cue
    /** [target] was played ([key] the key that finished it); [streak] hits in a row. */
    data class Hit(val target: Target, val key: Int, val streak: Int) : Cue
    /** One key of a two-at-once target, waiting for its partner. */
    data class Half(val target: Target, val key: Int) : Cue
    /** A key that was none of the live targets (or not the called one); [near] the key that was wanted, when there is one. */
    data class Slip(val key: Int, val near: Int?) : Cue
    /** A stream's target went by unplayed. */
    data class Expired(val target: Target) : Cue
    /** A call's target is stuck: [beat] 1 lights its key, 2 has the pup play it too. */
    data class Nudge(val target: Target, val beat: Int) : Cue
    /** A call's target was let go after three stuck beats: the pup plays it and the line moves on. */
    data class LetGo(val target: Target) : Cue
    /** A call's line ended; [clean] when the kid played every note of it with no slip and nothing let go. */
    data class LineDone(val phrase: Int, val clean: Boolean) : Cue
    /** Time is up in a song game: the pup plays the rest of the song ([keys]) for the kid. */
    data class Rest(val keys: List<Int>) : Cue
    /** The round is over: its [result]. */
    data class Finished(val result: RoundResult) : Cue
}

/**
 * How a round went: [offered] targets the kid could play, [hits] they played, [helped] let go or gone by, [slips], the
 * [lines] finished (walks, crossings, bites, bridges; for the tower its blocks) and how many of them [clean]; [keys] the
 * keys the round listened for and [durationMs] its length on the clock; [needLines] the lines three stars ask for (a
 * call's plan says). [stars] by [ArcadeStars].
 */
data class RoundResult(
    val game: ArcadeGame, val level: Int, val offered: Int, val hits: Int, val helped: Int, val slips: Int,
    val lines: Int, val clean: Int, val keys: Int, val durationMs: Long, val needLines: Int = 0,
) {
    val stars: Int get() = ArcadeStars.of(this)
    /** The share of the targets the kid played themselves, 0..1. */
    val share: Double get() = if (offered <= 0) 0.0 else hits.toDouble() / offered
}

/**
 * The stars a round earns, never fewer than one (playing to the end is worth a star, as a let-go step is):
 * - streams (balloons, treats, moles): three for three quarters of the targets played, two for under half of them;
 * - the tower: three for reaching the clouds ([ArcadeStars.clouds] blocks), two for halfway;
 * - calls: three when the kid played 85% of the notes themselves and finished [RoundResult.needLines] lines (the
 *   plan's [minLines]; a song game, every line of it), two from 55%.
 */
object ArcadeStars {
    const val STREAM_THREE = 0.75
    const val STREAM_TWO = 0.45
    const val CALL_THREE = 0.85
    const val CALL_TWO = 0.55

    /** Blocks to the clouds at each level of the tower. */
    fun clouds(level: Int): Int = 10 + 2 * level.coerceIn(0, 4)

    /** Lines an endless call round needs for three stars: four walks, four crossings, two bridges (song games: all of them). */
    fun minLines(game: ArcadeGame): Int = when (game) {
        ArcadeGame.GARDEN, ArcadeGame.FROGS -> 4
        ArcadeGame.BRIDGE -> 2
        else -> 0
    }

    fun of(r: RoundResult): Int = when {
        r.game == ArcadeGame.TOWER -> when {
            r.lines >= clouds(r.level) -> 3
            r.lines * 2 >= clouds(r.level) -> 2
            else -> 1
        }
        r.game.shape == RoundShape.STREAM -> when {
            r.share >= STREAM_THREE -> 3
            r.share >= STREAM_TWO -> 2
            else -> 1
        }
        else -> when {
            r.share >= CALL_THREE && r.lines >= r.needLines -> 3
            r.share >= CALL_TWO -> 2
            else -> 1
        }
    }
}

/**
 * The level a game is played at next: up one after three stars, down one after a round where the kid played under a
 * third of it (so it stays fun), else the same. Within 0..[ArcadeGame.maxLevel].
 */
object ArcadeLevels {
    const val DOWN_BELOW = 0.3

    fun next(game: ArcadeGame, level: Int, result: RoundResult): Int = when {
        result.stars >= 3 -> level + 1
        result.share < DOWN_BELOW -> level - 1
        else -> level
    }.coerceIn(0, game.maxLevel)
}

/** The keys the arcade uses: white keys (they have colours and animals), from C3 to C6, at most twelve a round. */
object ArcadeKeys {
    const val MIDDLE_C = 60
    /** A kid's range: C3 to C6. */
    val RANGE: IntRange = 48..84
    /** The note matcher listens for at most this many keys at once (MIC_ANY_OF_LIMIT). */
    const val MAX = 12

    /** The white keys from [low] to [high], both included. */
    fun whites(low: Int, high: Int): List<Int> = (low..high).filter { !isBlack(it) }

    val CDE = listOf(60, 62, 64)
    val FIVE = whites(60, 67)
    val OCTAVE = whites(60, 72)
    val LEFT_FIVE = whites(48, 55)
    val LOW_OCTAVE = whites(57, 72)
    /** C4 to B4 with its black keys: the moles' twelve. */
    val CHROMATIC = (60..71).toList()
}

/**
 * A round as the screen drives it, whichever its shape: [start] it, [tick] it every frame, hand it every [key] heard or
 * touched, and draw what the cues say. [keys] are what the ear listens for (at most [ArcadeKeys.MAX], strict); [pairs]
 * when two keys at once are wanted (the ear then lets both keys of a chord through).
 */
interface ArcadeRound {
    val game: ArcadeGame
    val level: Int
    val keys: Set<Int>
    val pairs: Boolean
    /** Keys count now (not a demo, not over). */
    val listening: Boolean
    val over: Boolean
    fun start(nowMs: Long): List<Cue>
    fun tick(nowMs: Long): List<Cue>
    fun key(midi: Int, nowMs: Long): List<Cue>
    fun progress(nowMs: Long): Float
    fun result(nowMs: Long): RoundResult
}

/** A round's plan, whichever its shape: every target it can ask for and the keys its ear listens for. */
sealed interface RoundPlan {
    val game: ArcadeGame
    val level: Int
    val roundMs: Long
    val keys: Set<Int>
    val targets: List<Target>
}
