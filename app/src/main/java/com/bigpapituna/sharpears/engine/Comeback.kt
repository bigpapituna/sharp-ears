package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.RunMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The comeback (2.9): a person back after [AWAY_DAYS] or more full days without practice is met with one easy thing they
 * already know, never with a lost streak. A kid gets the pup, glad, and one favourite stone to replay for stars (about two
 * minutes); a grown-up gets "Welcome back" on Today and three easy minutes: the last clean song at a slower tempo in wait
 * mode, or the last clean lesson again. Nothing new. The card shows once per return ([due] with the return it answered).
 * Pure: MainActivity reads the practice log, the kid store and the brief's log and keeps [Return.lastPlayed] in the play file.
 */
object Comeback {
    /** Full days without practice before a return counts as a comeback. */
    const val AWAY_DAYS = 3
    /** A kid's stone is picked from the last this-many stones walked (what they know, still fresh). */
    const val RECENT_STONES = 12
    /** A grown-up's song plays at this speed, in wait mode, for [GROWN_UP_MS]. */
    const val GROWN_UP_SPEED = 0.75
    const val GROWN_UP_MS = 3 * 60_000L

    /** A return: the last day played before it and the full days away. */
    data class Return(val lastPlayed: LocalDate, val daysAway: Int)

    /** Full days without practice between [last] and [today] (playing the day after is 0). */
    fun daysAway(last: LocalDate, today: LocalDate): Int = (ChronoUnit.DAYS.between(last, today) - 1).toInt().coerceAtLeast(0)

    /**
     * The return to greet, or null: nothing played yet [today], the last practice before it [AWAY_DAYS] or more full days
     * back, and not greeted already ([greeted] is the last-played day of the return the card was shown for).
     */
    fun due(practised: Set<LocalDate>, today: LocalDate, greeted: LocalDate?): Return? {
        if (today in practised) return null
        val last = practised.filter { it.isBefore(today) }.maxOrNull() ?: return null
        val away = daysAway(last, today)
        if (away < AWAY_DAYS || last == greeted) return null
        return Return(last, away)
    }

    /**
     * A kid's comeback stone: among the last [RECENT_STONES] stones [walked] (played, in path order), the one with the most
     * stars, the earliest of those (the easier); null when nothing was walked (the card then points at the next stone).
     */
    fun kidStone(walked: List<String>, stars: (String) -> Int?): String? {
        val recent = walked.takeLast(RECENT_STONES)
        if (recent.isEmpty()) return null
        val best = recent.maxOf { stars(it) ?: 0 }
        return recent.first { (stars(it) ?: 0) == best }
    }

    /** What a grown-up replays. */
    sealed interface Piece {
        data class Song(val id: String) : Piece
        data class Lesson(val id: String) : Piece
    }

    /**
     * A grown-up's comeback piece from their brief log: the latest song played clean (three stars, not Listen) or lesson
     * finished with three stars, else the latest song played or lesson finished; only what still exists ([song], [lesson]).
     * Null with nothing played: Today then offers the songs.
     */
    fun grownUpPiece(events: List<BriefEvent>, song: (String) -> Boolean, lesson: (String) -> Boolean): Piece? {
        val pieces = events.sortedBy { it.at }.mapNotNull { e ->
            when (e) {
                is BriefEvent.SongRun -> if (e.mode != RunMode.LISTEN && song(e.songId)) Triple(e.at, e.stars >= 3, Piece.Song(e.songId)) else null
                is BriefEvent.LessonDone -> if (lesson(e.lessonId)) Triple(e.at, e.stars >= 3, Piece.Lesson(e.lessonId)) else null
                else -> null
            }
        }
        return (pieces.lastOrNull { it.second } ?: pieces.lastOrNull())?.third
    }

    /** "four days", for the brief and the logs. */
    fun daysWords(n: Int): String = when (n) {
        1 -> "one day"
        in 2..10 -> listOf("two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")[n - 2] + " days"
        else -> "$n days"
    }

    /** The grown-up's card (Today): its line for the piece, plain and short. */
    fun grownUpLine(title: String?, lesson: Boolean): String = when {
        title == null -> "Three easy minutes to warm up. Pick a song you know."
        lesson -> "Three easy minutes to warm up: $title, once more."
        else -> "Three easy minutes to warm up: $title, a little slower, the notes wait for you."
    }

    /** The kid's card: what the pup says (said aloud, no numbers). */
    const val KID_TITLE = "You're back!"
    const val KID_LINE = "Yay! Let's play one you know."
    const val KID_LINE_NEXT = "Yay! Let's play."
    const val GROWN_UP_TITLE = "Welcome back"
}
