package com.bigpapituna.sharpears.engine

import java.time.LocalDateTime
import java.time.LocalTime

/** The daily practice reminder: when it next goes off, and what it says. */
object ReminderPlan {
    /** The next time [minutes] past midnight comes round after [now] (later today, or tomorrow). */
    fun next(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
        return if (today.isAfter(now.plusSeconds(30))) today else today.plusDays(1)
    }

    /** "Time to practise. 5 chords due, Minuet in G is waiting." Parts that are missing are left out. */
    fun text(chordsDue: Int, song: String?): String {
        val parts = listOfNotNull(
            when (chordsDue) { 0 -> null; 1 -> "1 chord due"; else -> "$chordsDue chords due" },
            song?.let { "$it is waiting" },
        )
        return "Time to practise." + if (parts.isEmpty()) "" else " " + parts.joinToString(", ") + "."
    }

    /** "18:30" */
    fun label(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)
}
