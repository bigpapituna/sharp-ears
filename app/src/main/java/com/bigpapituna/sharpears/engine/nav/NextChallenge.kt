package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.DrillScore
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.PROGRESSION_KEYS
import com.bigpapituna.sharpears.engine.SightLevel
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.rootName
import kotlin.math.roundToInt

/**
 * Today's next challenge (2.14, Sam: "a next challenge row on Today ... not an easy song"): one item at the player's level
 * in today's plan, with a target to hit. A Perform run of a song at their level ("3 stars at 90 bpm"), a staff
 * sight-reading drill at their level ("18 right in 2 minutes"), or a chord-change drill (a progression in Perform). Pure;
 * MainActivity's todayPlan asks [pick] and the plan walks it as [com.bigpapituna.sharpears.engine.SessionPart.Challenge].
 */
sealed interface Challenge {
    /** What it is, as Today's row names it: "Minuet in G". */
    val title: String
    /** What to hit: "3 stars at 90 bpm". */
    val target: String
    /** About how long it takes, for the plan's minutes. */
    val minutes: Double

    /** A library song in Perform at [bpm] ([speed] of its own [songBpm]), for three stars. */
    data class Perform(val songId: String, override val title: String, val bpm: Int, val songBpm: Double, val songMinutes: Double) : Challenge {
        val speed: Double get() = (bpm / songBpm).coerceIn(0.25, 1.0)
        override val target get() = "3 stars at $bpm bpm"
        override val minutes get() = (songMinutes / speed).coerceIn(1.0, 8.0)
    }

    /** The staff drill at [level], for [goal] notes right in its two minutes. */
    data class SightRead(val level: Int, val goal: Int) : Challenge {
        override val title get() = "Sight-read: " + SightLevel.of(level).title.replaceFirstChar { it.lowercase() }.let { "level $level, $it" }
        override val target get() = "$goal right in 2 minutes"
        override val minutes get() = 2.5
    }

    /** A chord progression in Perform at [bpm], in [key] (a pitch class), for three stars: the changes in time. */
    data class ChordChange(val progression: Int, val key: Int, val bpm: Int) : Challenge {
        override val title get() = "Chord changes: ${PROGRESSIONS[progression].name.substringBefore(" (")} in ${rootName(key)}"
        override val target get() = "3 stars at $bpm bpm"
        override val minutes get() = 2.0
    }
}

object NextChallenge {
    enum class Kind { PERFORM, SIGHT, CHORDS }

    /** The course's first three units (Basics, Beginner, Rhythm): a grown-up past them gets a challenge, placed or not. */
    const val FIRST_UNITS = 3
    /** From the Keys unit (A lot's start) the challenge is a step harder. */
    const val KEYS_UNIT = 4

    /**
     * Who gets one: a grown-up placed at Some or A lot, or whose next lesson is past the first three units ([unitIndex]: the
     * unit's place in the main course, Basics 0; Intermediate 3). Kids never (their home is the pup's map).
     */
    fun eligible(kid: Boolean, placement: Level, unitIndex: Int): Boolean =
        !kid && (placement.ordinal >= Level.SOME.ordinal || unitIndex >= FIRST_UNITS)

    /** 1 at Some (or Intermediate), 2 at A lot (or from Keys on). */
    fun tier(placement: Level, unitIndex: Int): Int = if (placement == Level.A_LOT || unitIndex >= KEYS_UNIT) 2 else 1

    /** Round robin: one kind a day, the three in turn, where the turn starts set by the profile's [seed]. */
    fun kindFor(epochDay: Long, seed: Long): Kind = Kind.entries[Math.floorMod(epochDay + seed, Kind.entries.size.toLong()).toInt()]

    /** A library song the challenge may pick: its [difficulty] as index.json has it, and whether a Perform run of it got 3 stars. */
    data class SongChoice(val id: String, val title: String, val difficulty: String, val bpm: Double, val minutes: Double, val threeStars: Boolean = false)

    /** The songs at a tier: medium for 1, hard for 2. */
    fun difficulty(tier: Int) = if (tier >= 2) "hard" else "medium"
    /** Perform targets: 85% of a song's own tempo at tier 1, all of it at tier 2. */
    fun tempoShare(tier: Int) = if (tier >= 2) 1.0 else 0.85
    /** Sight-reading: the treble staff at tier 1, both staves at tier 2; a level up once its best reaches [SIGHT_UP]. */
    fun sightLevel(tier: Int) = if (tier >= 2) SightLevel.GRAND.n else SightLevel.TREBLE.n
    const val SIGHT_UP = 20
    const val SIGHT_GOAL = 15
    /** Chord changes: the plain-triad progressions at 80 bpm at tier 1, ii-V-I and the blues at 100 at tier 2. */
    fun progressions(tier: Int) = if (tier >= 2) listOf(1, 3) else listOf(0, 2)
    fun chordBpm(tier: Int) = if (tier >= 2) 100 else 80

    /** A tempo target: [share] of [songBpm], to the nearest 5, never above the song's own tempo (the player cannot speed up). */
    fun targetBpm(songBpm: Double, share: Double): Int {
        val raw = (songBpm * share / 5).roundToInt() * 5
        val cap = (songBpm / 5).toInt() * 5
        return raw.coerceAtMost(maxOf(cap, 5)).coerceAtLeast(minOf(40, cap.coerceAtLeast(5)))
    }

    /**
     * Today's challenge for a player at [tier] on day [epochDay] with the profile's [seed]: the day's kind ([kindFor]), or the
     * next kind in turn when the day's has nothing to offer (no song at the tier, or the staff levels not open). Within a
     * kind the pick moves on each time the kind comes round (every three days): the next song not yet three-starred, the next
     * progression and key. Null only when nothing at all fits.
     */
    fun pick(tier: Int, epochDay: Long, seed: Long, songs: List<SongChoice>, staffOpen: Boolean, sightBest: (Int) -> DrillScore?): Challenge? {
        val first = kindFor(epochDay, seed)
        val turn = Math.floorDiv(epochDay + seed, Kind.entries.size.toLong())
        for (k in 0 until Kind.entries.size) {
            val kind = Kind.entries[(first.ordinal + k) % Kind.entries.size]
            val c = when (kind) {
                Kind.PERFORM -> perform(tier, turn, songs)
                Kind.SIGHT -> if (staffOpen) sight(tier, sightBest) else null
                Kind.CHORDS -> chords(tier, turn)
            }
            if (c != null) return c
        }
        return null
    }

    private fun perform(tier: Int, turn: Long, songs: List<SongChoice>): Challenge? {
        val at = songs.filter { it.difficulty == difficulty(tier) && it.bpm > 0 }
        val pool = at.filter { !it.threeStars }.ifEmpty { at }
        if (pool.isEmpty()) return null
        val s = pool[Math.floorMod(turn, pool.size.toLong()).toInt()]
        return Challenge.Perform(s.id, s.title, targetBpm(s.bpm, tempoShare(tier)), s.bpm, s.minutes)
    }

    private fun sight(tier: Int, best: (Int) -> DrillScore?): Challenge {
        var level = sightLevel(tier)
        while (level < SightLevel.MAX && (best(level)?.right ?: 0) >= SIGHT_UP) level++
        return Challenge.SightRead(level, maxOf(SIGHT_GOAL, (best(level)?.right ?: 0) + 1))
    }

    private fun chords(tier: Int, turn: Long): Challenge {
        val ps = progressions(tier)
        val p = ps[Math.floorMod(turn, ps.size.toLong()).toInt()]
        val key = PROGRESSION_KEYS[Math.floorMod(turn, PROGRESSION_KEYS.size.toLong()).toInt()]
        return Challenge.ChordChange(p, key, chordBpm(tier))
    }
}
