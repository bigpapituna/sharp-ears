package com.bigpapituna.sharpears.engine

/**
 * How "Practice today" names its parts between them: a short chip label per part, and the pup's line with the
 * title in quotes (`Next: the lesson "Sit, then shape your hand"`). The quotes are for the eye only; the voice
 * says [SessionPart.next] word for word, which is the same words.
 */
object SessionWords {
    fun chip(part: SessionPart): String = when (part) {
        is SessionPart.Chords -> "${part.itemIds.size} ${if (part.itemIds.size == 1) "chord" else "chords"}"
        is SessionPart.Lesson -> "Lesson"
        is SessionPart.Loop -> "Bar ${part.bar}"
        is SessionPart.Song -> "Song"
    }

    fun quoted(part: SessionPart): String = when (part) {
        is SessionPart.Chords -> part.next
        is SessionPart.Lesson -> "Next: the lesson “${part.title}”"
        is SessionPart.Loop -> "Next: bar ${part.bar} of “${part.title}”"
        is SessionPart.Song -> "Next: “${part.title}”"
    }

    /** One line under the pup's: what the part is and about how long it takes. */
    fun about(part: SessionPart): String {
        val m = part.minutes
        val min = if (m < 5) maxOf(1, kotlin.math.round(m).toInt()) else roundedMinutes(m)
        return when (part) {
            is SessionPart.Chords -> "Chords from your daily review, one at a time."
            is SessionPart.Lesson -> "The next lesson on your path."
            is SessionPart.Loop -> "One bar on a loop at half speed, the notes waiting for you."
            is SessionPart.Song -> "The whole song, the notes waiting for you."
        } + " About $min min."
    }

    /** The same for a bare line ("Next: the lesson X") when the part itself is not at hand. */
    fun quoted(line: String): String {
        Regex("^Next: the lesson (.+)$").matchEntire(line)?.let { return "Next: the lesson “${it.groupValues[1]}”" }
        Regex("""^Next: bar (\d+) of (.+)$""").matchEntire(line)?.let { return "Next: bar ${it.groupValues[1]} of “${it.groupValues[2]}”" }
        if (line == "Next: chords" || !line.startsWith("Next: ")) return line
        return "Next: “${line.removePrefix("Next: ")}”"
    }
}
