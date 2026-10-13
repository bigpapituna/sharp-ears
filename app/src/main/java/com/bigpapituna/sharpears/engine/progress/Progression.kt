package com.bigpapituna.sharpears.engine.progress

import kotlin.math.pow
import kotlin.math.roundToInt

/** What each thing is worth, in XP. */
object Xp {
    const val PRACTICE_MINUTE = 10
    const val LESSON_STEP = 15
    const val LESSON_COMPLETE = 100
    const val SONG_RUN = 50
    const val SONG_NEW_BEST = 50
    const val CHORD_ROUND = 5
    const val GAME_COMPLETE = 40
    const val THEORY_LESSON = 60
    const val THEORY_UNIT = 200
    const val DAILY_GOAL_MET = 75
    const val EAR_ROUND = 5
    /** Not in the original rule list: sight-reading and the daily session earn a little too. */
    const val SIGHT_READ = 20
    const val DAILY_SESSION = 25
}

/** The level reached after a gain: [from] to [to] (to > from). */
data class LevelUp(val from: Int, val to: Int) {
    val title get() = Progression.title(to)
}

/**
 * XP, levels and their names.
 *
 * Level n begins at 100 * (n - 1)^1.5 cumulative XP: level 2 at 100, level 5 at 800, level 10 at 2,700,
 * level 20 at 8,281, level 40 at 24,357. The gaps grow, so early levels come quickly and later ones take weeks.
 */
object Progression {
    const val MAX_LEVEL = 99

    /** Cumulative XP at which [level] begins (level 1 begins at 0). */
    fun xpToReach(level: Int): Int = if (level <= 1) 0 else (100.0 * (level - 1).toDouble().pow(1.5)).roundToInt()

    /** The level that [xp] reaches (1..[MAX_LEVEL]). */
    fun levelFor(xp: Int): Int {
        if (xp <= 0) return 1
        var n = minOf(MAX_LEVEL, ((xp / 100.0).pow(2.0 / 3.0)).toInt() + 1)      // close; the loops fix rounding at the edges
        while (n > 1 && xpToReach(n) > xp) n--
        while (n < MAX_LEVEL && xpToReach(n + 1) <= xp) n++
        return n.coerceIn(1, MAX_LEVEL)
    }

    /** XP earned inside the current level. */
    fun xpIntoLevel(xp: Int): Int = xp.coerceAtLeast(0) - xpToReach(levelFor(xp))

    /** XP the current level spans (its start to the next level's start). */
    fun levelSpan(xp: Int): Int { val l = levelFor(xp); return xpToReach(l + 1) - xpToReach(l) }

    /** XP still needed for the next level. */
    fun xpToNext(xp: Int): Int = if (levelFor(xp) >= MAX_LEVEL) 0 else levelSpan(xp) - xpIntoLevel(xp)

    /** How far through the current level, 0..1 (1 at the maximum level). */
    fun fractionToNext(xp: Int): Float {
        if (levelFor(xp) >= MAX_LEVEL) return 1f
        return (xpIntoLevel(xp).toFloat() / levelSpan(xp)).coerceIn(0f, 1f)
    }

    /** Set when adding [gain] to [before] crosses into a higher level. */
    fun levelUp(before: Int, gain: Int): LevelUp? {
        val a = levelFor(before); val b = levelFor(before + gain)
        return if (b > a) LevelUp(a, b) else null
    }

    // The named ranks. A level without its own name takes the rank below it, so the names run on without gaps.
    private val TITLES = sortedMapOf(
        1 to "Newcomer", 2 to "Key Finder", 3 to "Note Spotter", 4 to "Beat Keeper", 5 to "Tune Finder",
        6 to "Melody Maker", 7 to "Hand Warmer", 8 to "Scale Climber", 9 to "Rhythm Rider", 10 to "Steady Hands",
        11 to "Pedal Pal", 12 to "Chord Starter", 13 to "Harmony Seeker", 14 to "Key Changer", 15 to "Chord Builder",
        16 to "Ear Opener", 17 to "Phrase Shaper", 18 to "Fluent Fingers", 19 to "Page Turner", 20 to "Sight Reader",
        22 to "Expressive Player", 24 to "Repertoire Builder", 26 to "Dynamic Player", 28 to "Stage Ready",
        30 to "Performer", 33 to "Crowd Pleaser", 36 to "Concert Regular", 40 to "Virtuoso",
        50 to "Maestro", 60 to "Grand Master", 75 to "Living Legend", 99 to "Golden Keys",
    )

    fun title(level: Int): String = TITLES.entries.last { it.key <= level.coerceAtLeast(1) }.value

    /** XP for the event itself. [firstTime] matters for lessons and theory, which pay once. Practice is paid by [practiceXp]. */
    fun xpFor(event: ProgressEvent, firstTime: Boolean = true): Int = when (event) {
        is ProgressEvent.Practice -> 0
        ProgressEvent.LessonStepPassed -> Xp.LESSON_STEP
        is ProgressEvent.LessonComplete -> if (firstTime) Xp.LESSON_COMPLETE else 0
        is ProgressEvent.SongRun -> Xp.SONG_RUN + if (event.newBest) Xp.SONG_NEW_BEST else 0
        is ProgressEvent.ChordRound -> Xp.CHORD_ROUND
        is ProgressEvent.GameComplete -> Xp.GAME_COMPLETE
        is ProgressEvent.TheoryLesson -> if (firstTime) Xp.THEORY_LESSON else 0
        is ProgressEvent.TheoryUnit -> if (firstTime) Xp.THEORY_UNIT else 0
        is ProgressEvent.EarRound -> Xp.EAR_ROUND
        is ProgressEvent.SightRead -> Xp.SIGHT_READ
        is ProgressEvent.DailySession -> Xp.DAILY_SESSION
        is ProgressEvent.FeatureUsed -> 0
    }

    /** Practice pays 10 XP per whole minute: the minutes crossed when the lifetime total goes from [beforeSec] to [afterSec]. */
    fun practiceXp(beforeSec: Long, afterSec: Long): Int = ((afterSec / 60) - (beforeSec / 60)).toInt().coerceAtLeast(0) * Xp.PRACTICE_MINUTE
}
