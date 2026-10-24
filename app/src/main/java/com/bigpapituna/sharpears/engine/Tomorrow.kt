package com.bigpapituna.sharpears.engine

/**
 * The grown-ups' "tomorrow" line (2.9): on a finish screen once the day's goal is met, the next thing, named from
 * tomorrow's plan, instead of a gift: "Tomorrow: the left hand of Twinkle". The plan's song comes first when its hands
 * tip has a next step (engine/Course.kt handsTip: "right", "left", "together"), then the next lesson, then the song
 * again. Null with nothing to name (the line is left out).
 */
object Tomorrow {
    fun line(song: String?, handsTip: String?, nextLesson: String?): String? = when {
        song != null && handsTip == "right" -> "Tomorrow: the right hand of $song"
        song != null && handsTip == "left" -> "Tomorrow: the left hand of $song"
        song != null && handsTip == "together" -> "Tomorrow: $song with both hands"
        nextLesson != null -> "Tomorrow: $nextLesson"
        song != null -> "Tomorrow: $song again"
        else -> null
    }
}
