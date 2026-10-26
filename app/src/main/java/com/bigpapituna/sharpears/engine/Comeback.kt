package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Hands
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
        /** A song, in the [hands] its clean run was played with (the easier way back). */
        data class Song(val id: String, val hands: Hands = Hands.BOTH) : Piece
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
                is BriefEvent.SongRun -> if (e.mode != RunMode.LISTEN && song(e.songId)) Triple(e.at, e.stars >= 3, Piece.Song(e.songId, e.hands)) else null
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
        else -> plural(n, "day")
    }

    /** The grown-up's card (Today): its line for the piece, plain and short. */
    fun grownUpLine(title: String?, lesson: Boolean): String = when {
        title == null -> "Three easy minutes to warm up. Pick a song you know."
        lesson -> "Three easy minutes to warm up. Play “$title” once more."
        else -> "Three easy minutes to warm up. Play “$title” a little slower, and the notes wait for you."
    }

    // ---- 2.14: a concrete step, and words for each audience --------------------------------------------------------------

    /**
     * Where the comeback's button goes (2.14): never a list. Marc came back after a week, tapped Start and landed on the Songs
     * list ("Pick a song you know"), because he had no clean song to replay.
     */
    sealed interface Resume {
        /** A lesson left part way, opened at the step it was left on. */
        data class LessonStep(val id: String, val step: Int) : Resume
        /** The song by chords played last, on its page. */
        data class PopChart(val id: String) : Resume
        /** The library song played last, in the player: a little slower, the notes waiting, three minutes. */
        data class Song(val id: String) : Resume
        /** Nothing left open: the next lesson on the path, from its first step. */
        data class NextLesson(val id: String) : Resume
    }

    /**
     * The step a comeback opens: the lesson [left] part way (its id and step, kept as the steps go by; null once it was
     * finished) while it still exists and is not done, else what was played last ([last]: a chart's page, a song, a lesson not
     * finished), else the path's [next] lesson. [exists] says the thing is still there; [done] that a lesson is finished.
     * Null only when there is nothing at all (no course left and nothing played).
     */
    fun resume(left: Pair<String, Int>?, last: com.bigpapituna.sharpears.engine.nav.ContinueWith?, next: String?,
               done: (String) -> Boolean, exists: (com.bigpapituna.sharpears.engine.nav.ContinueWith) -> Boolean): Resume? {
        left?.takeIf { (id, _) -> !done(id) && exists(com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson(id)) }
            ?.let { (id, step) -> return Resume.LessonStep(id, step.coerceAtLeast(0)) }
        when (last) {
            is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong -> if (exists(last)) return Resume.PopChart(last.id)
            is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> if (exists(last)) return Resume.Song(last.id)
            is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> if (exists(last) && !done(last.id)) return Resume.LessonStep(last.id, 0)
            null -> {}
        }
        return next?.let(Resume::NextLesson)
    }

    /** The left lesson as Prefs keeps it ("lessonId:step"), or null. */
    fun parseLeft(s: String?): Pair<String, Int>? {
        val id = s?.substringBeforeLast(':', "")?.takeIf { it.isNotEmpty() } ?: return null
        return id to (s.substringAfterLast(':').toIntOrNull() ?: 0)
    }

    fun encodeLeft(id: String, step: Int): String = "$id:$step"

    /** A big kid's card (2.14): plain, the tabs' words, no pup voice. */
    const val BIG_KID_TITLE = "Welcome back."
    const val BIG_KID_LINE = "Pick up where you left off."

    /** The grown-up's line for a [Resume] with its [title] ("as today", 2.9's three easy minutes). */
    fun grownUpLine(r: Resume, title: String?): String = when (r) {
        is Resume.LessonStep -> if (title == null) grownUpLine(null, true) else "Three easy minutes to warm up. Pick up “$title” where you left off."
        is Resume.NextLesson -> if (title == null) grownUpLine(null, true) else "Three easy minutes to warm up. Start “$title”."
        is Resume.PopChart -> if (title == null) grownUpLine(null, false) else "Three easy minutes to warm up. Play “$title” with the band."
        is Resume.Song -> grownUpLine(title, false)
    }

    /** A gap of this many days or more (the last practice a week or more ago) and the card says what became of the streak. */
    const val STREAK_LINE_DAYS = 7

    /**
     * After a gap of [STREAK_LINE_DAYS] days or more since the last practice ([practised], back [today]: Marc's 26 October to
     * 2 November is a week), one line about the streak:
     * "Your 12-day streak ended on the 9th. A new one starts today." The day it ended is the first missed day neither a rest
     * day nor a freeze could keep (engine/progress Streaks). A streak of one day or none: "A new streak starts today." Null
     * for a shorter gap or a first day. Grown-ups and big kids only (Ages.streakLine: a little kid sees no numbers).
     */
    fun streakLine(practised: Set<LocalDate>, today: LocalDate): String? {
        val last = practised.filter { it.isBefore(today) }.maxOrNull() ?: return null
        if (ChronoUnit.DAYS.between(last, today) < STREAK_LINE_DAYS) return null
        val upToLast = practised.filter { !it.isAfter(last) }.toSet()
        val run = com.bigpapituna.sharpears.engine.progress.Streaks.compute(upToLast, last).streak
        if (run < 2) return "A new streak starts today."
        var ended = last.plusDays(1)
        while (ended.isBefore(today) && com.bigpapituna.sharpears.engine.progress.Streaks.compute(upToLast, ended.plusDays(1)).streak > 0) ended = ended.plusDays(1)
        return "Your $run-day streak ended on the ${ordinal(ended.dayOfMonth)}. A new one starts today."
    }

    /** "1st", "2nd", "3rd", "11th", "22nd". */
    fun ordinal(n: Int): String = "$n" + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    /** The kid's card: what the pup says (said aloud, no numbers). */
    const val KID_TITLE = "You're back!"
    const val KID_LINE = "Yay! Let's play one you know."
    const val KID_LINE_NEXT = "Yay! Let's play."
    const val GROWN_UP_TITLE = "Welcome back"
}
