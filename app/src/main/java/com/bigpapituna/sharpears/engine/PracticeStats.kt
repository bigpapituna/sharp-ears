package com.bigpapituna.sharpears.engine

import java.time.LocalDate

/** Practice time per day (seconds the transport was playing), kept for [KEEP_DAYS] days. */
object PracticeStats {
    const val KEEP_DAYS = 60L

    /** Days in a row with practice, counting back from today (or from yesterday while today is still empty). */
    fun streak(seconds: Map<LocalDate, Int>, today: LocalDate): Int {
        var day = if ((seconds[today] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((seconds[day] ?: 0) > 0) { n++; day = day.minusDays(1) }
        return n
    }

    /**
     * Whole minutes over the last seven days, today included (any practice shows as at least 1 minute). Counted down,
     * as the goal line above it is: rounded, one day's 16.6 minutes read "Goal met, 16 min today" over "This week: 17 min".
     */
    fun weekMinutes(seconds: Map<LocalDate, Int>, today: LocalDate): Int {
        val total = (0L..6L).sumOf { seconds[today.minusDays(it)] ?: 0 }
        return if (total == 0) 0 else maxOf(1, total / 60)
    }

    /** "This week: 42 min, 4 days in a row", or null when there was no practice this week. A streak shows from 2 days. */
    fun summary(seconds: Map<LocalDate, Int>, today: LocalDate): String? {
        val min = weekMinutes(seconds, today)
        if (min == 0) return null
        val s = streak(seconds, today)
        return "This week: $min min" + if (s >= 2) ", $s days in a row" else ""
    }

    /** Days old enough to forget. */
    fun stale(days: Collection<LocalDate>, today: LocalDate) = days.filter { it.isBefore(today.minusDays(KEEP_DAYS - 1)) }
}
