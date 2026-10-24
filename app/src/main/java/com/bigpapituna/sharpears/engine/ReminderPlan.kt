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

    /** 2.9: the line on the day a kid's surprise is ready to open (Tomorrow's surprise), instead of the usual one. */
    const val SURPRISE = "Your surprise is ready."

    /**
     * "Time to practise. 5 chords due, Minuet in G is waiting." Parts that are missing are left out. 2.9: with a kid's
     * surprise ready ([surpriseReady]), [SURPRISE] instead.
     */
    fun text(chordsDue: Int, song: String?, surpriseReady: Boolean = false): String {
        if (surpriseReady) return SURPRISE
        val parts = listOfNotNull(
            when (chordsDue) { 0 -> null; 1 -> "1 chord due"; else -> "$chordsDue chords due" },
            song?.let { "$it is waiting" },
        )
        return "Time to practise." + if (parts.isEmpty()) "" else " " + parts.joinToString(", ") + "."
    }

    /**
     * Whether today's reminder stays quiet (2.8.4, QA D7: it fired at 6:30 pm after "Done for today"): today's goal is met
     * ([secondsToday] against [goalMinutes]) or today's plan was played through ([planDone]). 2.9: a kid's surprise ready
     * to open is always said ([surpriseReady]): the owner's goal says nothing about the kid's day.
     */
    fun skip(secondsToday: Int, goalMinutes: Int, planDone: Boolean, surpriseReady: Boolean = false): Boolean =
        !surpriseReady && (planDone || com.bigpapituna.sharpears.engine.progress.DailyGoal.met(secondsToday, goalMinutes))

    /** "18:30" */
    fun label(minutes: Int) = "%d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)
}
