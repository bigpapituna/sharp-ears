package com.bigpapituna.sharpears.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A streak as it reads: [days] practised in a row and the [rest] days inside the run that kept it (2.9). A rest day holds
 * the run; it never adds a day.
 */
data class StreakRun(val days: Int, val rest: Int = 0)

/** Practice time per day (seconds the transport was playing), kept for [KEEP_DAYS] days. */
object PracticeStats {
    const val KEEP_DAYS = 60L

    /** At most one rest day in any window of this many days (2.9). */
    const val REST_WINDOW_DAYS = 7L

    /**
     * Days in a row with practice, counting back from today (or from yesterday while today is still empty). 2.9: a single
     * missed day between two practice days is a rest day and keeps the run ([restDays]); it holds the count, never adds.
     */
    fun streak(seconds: Map<LocalDate, Int>, today: LocalDate): Int = streakRun(seconds.filterValues { it > 0 }.keys, today).days

    /**
     * The rest days (2.9, "Rest day"): a gap of exactly one day keeps the streak, automatically, at most once in any
     * [REST_WINDOW_DAYS] days. A missed day counts when the day before it was practised and the day after it was too, or
     * the day after is [today] (still open: until today ends without practice, yesterday reads as a rest day). Rest days
     * are given oldest first, so a later gap inside the same seven days is a plain missed day. Nothing to buy, nothing
     * to switch on; worked out from the days practised, so it never drifts and survives a restored backup.
     */
    fun restDays(practised: Set<LocalDate>, today: LocalDate): Set<LocalDate> {
        val out = sortedSetOf<LocalDate>()
        var last: LocalDate? = null
        for (p in practised.filter { it.isBefore(today) }.sorted()) {
            val d = p.plusDays(1)
            if (!d.isBefore(today) || d in practised) continue
            val after = d.plusDays(1)
            if (after != today && after !in practised) continue          // a gap of two days or more: not a rest day
            if (last != null && ChronoUnit.DAYS.between(last, d) < REST_WINDOW_DAYS) continue
            out += d; last = d
        }
        return out
    }

    /** The streak with its rest days, counting back as [streak] does. */
    fun streakRun(practised: Set<LocalDate>, today: LocalDate): StreakRun {
        val rest = restDays(practised, today)
        var day = if (today in practised) today else today.minusDays(1)
        var n = 0; var r = 0
        while (true) {
            when (day) { in practised -> n++; in rest -> r++; else -> break }
            day = day.minusDays(1)
        }
        return StreakRun(n, if (n == 0) 0 else r)
    }

    /** "6 days in a row", or "6 days, with one rest day" (2.9: the line says the rest day plainly). */
    fun streakWords(days: Int, rest: Int): String = when (rest) {
        0 -> plural(days, "day") + " in a row"
        1 -> plural(days, "day") + ", with one rest day"
        else -> plural(days, "day") + ", with " + small(rest) + " rest days"
    }

    private fun small(n: Int) = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten").getOrElse(n) { n.toString() }

    /**
     * Whole minutes over the last seven days, today included (any practice shows as at least 1 minute). Counted down,
     * as the goal line above it is: rounded, one day's 16.6 minutes read "Goal met, 16 min today" over "This week: 17 min".
     */
    fun weekMinutes(seconds: Map<LocalDate, Int>, today: LocalDate): Int {
        val total = (0L..6L).sumOf { seconds[today.minusDays(it)] ?: 0 }
        return if (total == 0) 0 else maxOf(1, total / 60)
    }

    /**
     * Whole minutes from [weekStart] to [today], both included, counted down like [weekMinutes] (any practice is at least
     * 1). You's goal card counts its week from the goal's Monday (2.7: the home's "This week" line moved there).
     */
    fun minutesSince(seconds: Map<LocalDate, Int>, weekStart: LocalDate, today: LocalDate): Int {
        var total = 0L
        var day = weekStart
        while (!day.isAfter(today)) { total += seconds[day] ?: 0; day = day.plusDays(1) }
        return if (total == 0L) 0 else maxOf(1L, total / 60).toInt()
    }

    /**
     * "This week: 42 min, 4 days in a row", or null when there was no practice this week. A streak shows from 2 days.
     * [streak]: the profile's (progress.streak(), with its freezes), so the home says what the greeting and You say; [rest]
     * the rest days inside it (2.9): "This week: 42 min, 6 days, with one rest day".
     */
    fun summary(seconds: Map<LocalDate, Int>, today: LocalDate, streak: Int = streak(seconds, today), rest: Int = 0): String? {
        val min = weekMinutes(seconds, today)
        if (min == 0) return null
        val s = streak
        return "This week: $min min" + if (s >= 2) ", " + streakWords(s, rest) else ""
    }

    /** Days old enough to forget. */
    fun stale(days: Collection<LocalDate>, today: LocalDate) = days.filter { it.isBefore(today.minusDays(KEEP_DAYS - 1)) }
}
