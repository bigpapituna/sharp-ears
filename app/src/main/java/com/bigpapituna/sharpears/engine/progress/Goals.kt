package com.bigpapituna.sharpears.engine.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The daily goal: minutes of practice a day. */
object DailyGoal {
    val OPTIONS = listOf(5, 10, 15, 20, 30)
    const val DEFAULT = 10

    fun met(seconds: Int, goalMinutes: Int) = seconds >= goalMinutes * 60

    /** 0..1 of the goal done. */
    fun fraction(seconds: Int, goalMinutes: Int): Float =
        if (goalMinutes <= 0) 0f else (seconds / (goalMinutes * 60f)).coerceIn(0f, 1f)

    /** Whole minutes practised, rounded to the nearest, but at least 1 once there is any practice. */
    fun minutes(seconds: Int): Int = if (seconds <= 0) 0 else maxOf(1, (seconds + 30) / 60)

    fun name(minutes: Int) = when (minutes) { 5 -> "Light"; 10 -> "Steady"; 15 -> "Regular"; 20 -> "Serious"; 30 -> "Dedicated"; else -> "Custom" }

    /** What a goal gets you, in plain words. */
    fun note(minutes: Int) = when (minutes) {
        5 -> "A gentle habit. About 35 minutes a week keeps what you know fresh."
        10 -> "Steady progress. Your first songs come together in a few weeks."
        15 -> "A good rhythm. Lessons and a song fit in most days."
        20 -> "Real momentum. Scales, a lesson and a song every day."
        30 -> "All in. Fast progress, if your hands and time allow it."
        else -> "$minutes minutes a day."
    }
}

/** The weekly goal: how many days a week to practise. Weeks run Monday to Sunday. */
object WeeklyGoal {
    val OPTIONS = listOf(2, 3, 4, 5, 6, 7)
    const val DEFAULT = 4

    fun weekStart(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

    /** Days practised in the week that holds [today]. */
    fun daysThisWeek(practiced: Set<LocalDate>, today: LocalDate): Int {
        val start = weekStart(today)
        return practiced.count { !it.isBefore(start) && !it.isAfter(today) }
    }

    fun met(practiced: Set<LocalDate>, today: LocalDate, goalDays: Int) = daysThisWeek(practiced, today) >= goalDays

    /** Weeks (current one included) in which at least [goalDays] days were practised. */
    fun weeksMet(practiced: Set<LocalDate>, goalDays: Int): Int =
        practiced.groupBy { weekStart(it) }.count { it.value.size >= goalDays }
}

/** The streak, its freezes and what it looks like today. */
data class StreakResult(
    /** Days in a row, counting back from today (or yesterday while today is still empty). Frozen days hold it, they do not add. */
    val streak: Int,
    val longest: Int,
    /** Freezes held now (0..[Streaks.MAX_FREEZES]). */
    val freezes: Int,
    /** Freezes spent over the whole history. */
    val freezesUsed: Int,
    /** Missed days a freeze covered. */
    val frozenDays: Set<LocalDate>,
    val practicedToday: Boolean,
) {
    /** The streak is alive but today has no practice yet. */
    val atRisk get() = streak > 0 && !practicedToday
}

/**
 * Streak with freezes. Every 7th day in a row earns a freeze (up to two held). A missed day spends one instead of
 * breaking the streak; with none held, the streak resets. Worked out from the days practised, so it never drifts and
 * survives a restored backup. Today is never counted as missed: the day is not over.
 */
object Streaks {
    const val MAX_FREEZES = 2
    const val EARN_EVERY = 7

    fun compute(practiced: Set<LocalDate>, today: LocalDate): StreakResult {
        val first = practiced.filter { !it.isAfter(today) }.minOrNull()
            ?: return StreakResult(0, 0, 0, 0, emptySet(), false)
        var streak = 0; var longest = 0; var freezes = 0; var used = 0
        val frozen = mutableSetOf<LocalDate>()
        var d = first
        while (!d.isAfter(today)) {
            if (d in practiced) {
                streak++
                if (streak > longest) longest = streak
                if (streak % EARN_EVERY == 0) freezes = minOf(MAX_FREEZES, freezes + 1)
            } else if (d != today) {                         // today is still open
                if (streak > 0 && freezes > 0) { freezes--; used++; frozen += d }
                else streak = 0
            }
            d = d.plusDays(1)
        }
        return StreakResult(streak, longest, freezes, used, frozen, today in practiced)
    }

    /** Full days without practice between [last] and [now] (practising the day after is 0). */
    fun daysAway(last: LocalDate, now: LocalDate): Int = (ChronoUnit.DAYS.between(last, now) - 1).toInt().coerceAtLeast(0)

    /** Practising at [now] after [last] counts as a comeback when at least a week went by. */
    fun isComeback(last: LocalDate?, now: LocalDate) = last != null && daysAway(last, now) >= 7
}

/** The calendar heatmap's tint steps. */
object Heat {
    /** 0 for no practice, 1 under half the goal, 2 under the goal, 3 goal met, 4 twice the goal or more. */
    fun level(seconds: Int, goalMinutes: Int): Int {
        if (seconds <= 0) return 0
        val goal = goalMinutes * 60f
        return when {
            seconds >= 2 * goal -> 4
            seconds >= goal -> 3
            seconds >= goal / 2 -> 2
            else -> 1
        }
    }
}
