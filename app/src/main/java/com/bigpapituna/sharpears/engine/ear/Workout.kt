package com.bigpapituna.sharpears.engine.ear

import java.util.Random

/** What the workout planner knows about the player: best scores and when each game was last played (EarStore). */
interface EarProgress {
    /** Best score (clean rounds) for a game and level, or -1 before the first full game. */
    fun best(kind: EarKind, level: Int): Int
    /** Days since 1970-01-01 of the last play of [kind], or -1 when never played. */
    fun lastPlayedDay(kind: EarKind): Long
}

/** One game of a workout. */
data class WorkoutItem(val kind: EarKind, val level: Int, val rounds: Int) {
    val estimatedSeconds get() = kind.secondsPerRound * rounds
}

data class WorkoutPlan(val day: Long, val items: List<WorkoutItem>) {
    val estimatedSeconds get() = items.sumOf { it.estimatedSeconds }
    val totalRounds get() = items.sumOf { it.rounds }
    /** "About N minutes", rounded: what the card and the workout's first screen both say. */
    val minutes get() = (estimatedSeconds + 30) / 60
}

/** How one game of a workout went. */
data class WorkoutGame(val kind: EarKind, val level: Int, val score: Int, val rounds: Int, val bestStreak: Int)

data class WorkoutResult(val games: List<WorkoutGame>) {
    val score get() = games.sumOf { it.score }
    val rounds get() = games.sumOf { it.rounds }
    val bestStreak get() = games.maxOfOrNull { it.bestStreak } ?: 0
}

/**
 * The daily ear workout: three different games in about five minutes. The planner prefers the games played least
 * recently (never played first), puts each at the first level whose best is below [MASTERED] (so it climbs as
 * scores improve), and gives each as many rounds as fit its share of the time. Same day and same history give the
 * same plan.
 */
object Workout {
    const val GAMES = 3
    const val TARGET_SECONDS = 300
    /** A best of 7 or more out of 10 moves the workout up a level. */
    const val MASTERED = 7

    fun levelFor(progress: EarProgress, kind: EarKind): Int {
        for (l in 0 until kind.levels.size) if (progress.best(kind, l) < MASTERED) return l
        return kind.levels.size - 1
    }

    fun roundsFor(kind: EarKind) = (TARGET_SECONDS / GAMES / kind.secondsPerRound).coerceIn(4, 8)

    fun plan(progress: EarProgress, day: Long, seed: Long = day): WorkoutPlan {
        val rnd = Random(seed)
        val picked = EarKind.entries.shuffled(rnd).sortedBy { progress.lastPlayedDay(it) }.take(GAMES).shuffled(rnd)
        return WorkoutPlan(day, picked.map { WorkoutItem(it, levelFor(progress, it), roundsFor(it)) })
    }

    /** Days in a row with a workout, counting back from [today] (or from yesterday while today's is still to do). */
    fun streak(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    /** The headline on the final celebration. */
    fun headline(score: Int, rounds: Int) = when {
        rounds == 0 -> "Workout done!"
        score * 10 >= rounds * 8 -> "Great workout!"
        score * 2 >= rounds -> "Workout done!"
        else -> "Good effort!"
    }
}
