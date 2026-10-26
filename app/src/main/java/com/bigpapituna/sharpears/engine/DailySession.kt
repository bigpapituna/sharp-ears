package com.bigpapituna.sharpears.engine

import kotlin.math.roundToInt

/**
 * "Practice today": one tap runs a short session in a fixed order. Chords that are due (spaced repetition,
 * at most [MAX_CHORDS]), the next Learn lesson, then the current song: its worst bar as a two-minute loop,
 * or the song itself in wait mode when it has no trouble spot. The plan aims at [TARGET_MIN] minutes.
 */
sealed interface SessionPart {
    /** Estimated minutes. */
    val minutes: Double
    /** What the interstitial says before the part: "Next: chords". Spoken as written (no symbols). */
    val next: String
    /** How the summary names it once done: "5 chords". */
    val done: String
    /** Its line in the "Practice today" panel. */
    val row: String

    data class Chords(val itemIds: List<String>) : SessionPart {
        override val minutes get() = (itemIds.size * CHORD_MIN).coerceAtLeast(1.0)
        override val next get() = "Next: chords"
        override val done get() = "${itemIds.size} ${if (itemIds.size == 1) "chord" else "chords"}"
        override val row get() = "${itemIds.size} ${if (itemIds.size == 1) "chord" else "chords"} from the daily review"
    }
    data class Lesson(val lessonId: String, val title: String) : SessionPart {
        override val minutes get() = LESSON_MIN
        override val next get() = "Next: the lesson ${com.bigpapituna.sharpears.engine.theory.spokenNumerals(title)}"
        override val done get() = "the lesson $title"
        override val row get() = "Lesson: $title"
    }
    data class Loop(val songId: String, val title: String, val bar: Int) : SessionPart {
        override val minutes get() = LOOP_MIN
        override val next get() = "Next: bar $bar of $title"
        override val done get() = "bar $bar of $title"
        override val row get() = "Bar $bar of $title, looped for two minutes"
    }
    data class Song(val songId: String, val title: String, val songMinutes: Double, val fill: Double) : SessionPart {
        override val minutes get() = fill
        override val next get() = "Next: $title"
        override val done get() = title
        override val row get() = "$title, with the notes waiting for you"
    }

    companion object {
        const val CHORD_MIN = 0.75
        const val LESSON_MIN = 5.0
        const val LOOP_MIN = 2.0
    }
}

data class SessionPlan(val parts: List<SessionPart>) {
    val minutes get() = parts.sumOf { it.minutes }
    /** "about 15 min": rounded to 5 minutes, at least 5. */
    val aboutLabel get() = "about ${roundedMinutes(minutes)} min"
    val isEmpty get() = parts.isEmpty()
}

fun roundedMinutes(m: Double) = ((m / 5).roundToInt() * 5).coerceAtLeast(5)

const val TARGET_MIN = 15.0
const val MAX_CHORDS = 6

/** A song the session can use: its id, title, length, and the worst bar of its last run (if any). */
data class SessionSong(val id: String, val title: String, val minutes: Double, val troubleBar: Int?)

/**
 * The session for today. [dueChordIds]: the Daily queue's chord items, oldest due first. [nextLesson]: id and
 * title of the first Learn lesson not done. [song]: the Continue song. [lessonFirst] (2.14, engine/nav/DayOne.kt): a
 * beginner's plan opens on the lesson, the chords after it.
 */
fun planSession(dueChordIds: List<String>, nextLesson: Pair<String, String>?, song: SessionSong?, lessonFirst: Boolean = false): SessionPlan {
    val parts = mutableListOf<SessionPart>()
    val lesson = nextLesson?.let { (id, title) -> SessionPart.Lesson(id, title) }
    if (lessonFirst && lesson != null) parts += lesson
    if (dueChordIds.isNotEmpty()) parts += SessionPart.Chords(dueChordIds.take(MAX_CHORDS))
    if (!lessonFirst && lesson != null) parts += lesson
    if (song != null) {
        val bar = song.troubleBar
        parts += if (bar != null) SessionPart.Loop(song.id, song.title, bar)
        else {
            // The song fills what is left of the target, at least 3 minutes, never more than the song itself takes.
            val left = (TARGET_MIN - parts.sumOf { it.minutes }).coerceAtLeast(3.0)
            SessionPart.Song(song.id, song.title, song.minutes, minOf(left, song.minutes.coerceAtLeast(1.0)))
        }
    }
    return SessionPlan(parts)
}

/** Whether this part of the plan was done: a chord part also with fewer chords than planned (Skip the rest, or Back). */
fun SessionPart.doneIn(done: List<SessionPart>): Boolean = done.any { it == this || (it is SessionPart.Chords && this is SessionPart.Chords) }

/**
 * Whether the plan is through (2.8.4, QA D5): every part was done ([done]; a chord part with at least one chord answered)
 * or passed over with Skip ([skipped]), and at least one was done. Only then does a grown-up's session end on "Done for
 * today!" and the summary; a part left part way (Back inside it) or a plan ended early leaves today's plan open.
 */
fun SessionPlan.through(done: List<SessionPart>, skipped: List<SessionPart>): Boolean =
    done.isNotEmpty() && parts.all { it.doneIn(done) || it in skipped }

/**
 * The summary row: "Done today: 6 chords, the lesson Rests and bar 12 of Minuet in G. 14 min." [seconds] is the practice
 * the session added, in whole minutes as the goal line counts them ("3 of 10 min today"); under a minute names none.
 */
fun sessionSummary(done: List<SessionPart>, seconds: Long): String {
    val min = seconds / 60
    val what = when (done.size) {
        0 -> "nothing yet"
        1 -> done[0].done
        else -> done.dropLast(1).joinToString(", ") { it.done } + " and " + done.last().done
    }
    return "Done today: $what." + if (min > 0) " $min min." else ""
}
