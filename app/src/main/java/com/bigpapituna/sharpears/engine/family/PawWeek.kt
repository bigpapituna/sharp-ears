package com.bigpapituna.sharpears.engine.family

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One paw print on the family wall: a day of this week, practised or not, and whether it is still to come. [rest] (2.9): a
 * missed day the streak kept as its rest day (engine/PracticeStats.restDays), drawn as a sleeping paw, "Rest day".
 */
data class PawDay(val date: LocalDate, val practised: Boolean, val future: Boolean, val rest: Boolean = false) {
    /** "M", "T", ... under the paw. */
    val initial: String get() = date.dayOfWeek.name.take(1)
}

/**
 * This week's paw prints, Monday to Sunday (docs/KIDS-PLAN.md: a missed day is a blank paw, never a lost streak).
 * [practice] is seconds per day (ProgressStore.practiceLog); any practice counts, as it does for the streak.
 */
object PawWeek {
    fun of(practice: Map<LocalDate, Int>, today: LocalDate): List<PawDay> {
        val monday = today.with(DayOfWeek.MONDAY)
        val rest = com.bigpapituna.sharpears.engine.PracticeStats.restDays(practice.filterValues { it > 0 }.keys, today)
        return (0L until 7L).map { i ->
            val d = monday.plusDays(i)
            PawDay(d, !d.isAfter(today) && (practice[d] ?: 0) > 0, d.isAfter(today), rest = d in rest)
        }
    }

    /** How many paws are filled. */
    fun count(practice: Map<LocalDate, Int>, today: LocalDate) = of(practice, today).count { it.practised }
}
