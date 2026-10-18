package com.bigpapituna.sharpears.engine

/**
 * How "Practice today" names its parts between them: a short chip label per part, and the pup's line with the
 * title in quotes (`Next: the lesson "Sit, then shape your hand"`). The quotes are for the eye only; the voice
 * says [SessionPart.next] word for word, which is the same words.
 */
object SessionWords {
    /** Kids (2.1): no counts on the chips, no bar numbers, no minutes; see the kid section at the end. */
    fun chip(part: SessionPart, kid: Boolean): String = if (!kid) chip(part) else when (part) {
        is SessionPart.Chords -> "Chords"
        is SessionPart.Lesson -> "Lesson"
        is SessionPart.Loop -> "Tricky bit"
        is SessionPart.Song -> "Song"
    }

    fun chip(part: SessionPart): String = when (part) {
        is SessionPart.Chords -> "${part.itemIds.size} ${if (part.itemIds.size == 1) "chord" else "chords"}"
        is SessionPart.Lesson -> "Lesson"
        is SessionPart.Loop -> "Bar ${part.bar}"
        is SessionPart.Song -> "Song"
    }

    fun quoted(part: SessionPart, kid: Boolean): String = if (!kid) quoted(part) else when (part) {
        is SessionPart.Chords -> "Next: chords!"
        is SessionPart.Lesson -> "Next: a lesson! “${part.title}”"
        is SessionPart.Loop -> "Next: the tricky bit of “${part.title}”"
        is SessionPart.Song -> "Next: “${part.title}”!"
    }

    /** What the voice says before a kid's part: the [quoted] words without the quotes. */
    fun kidNext(part: SessionPart): String = quoted(part, kid = true).replace("“", "").replace("”", "")

    fun quoted(part: SessionPart): String = when (part) {
        is SessionPart.Chords -> part.next
        is SessionPart.Lesson -> "Next: the lesson “${part.title}”"
        is SessionPart.Loop -> "Next: bar ${part.bar} of “${part.title}”"
        is SessionPart.Song -> "Next: “${part.title}”"
    }

    /** A kid's line under the pup's: what the part is, never how long it takes. */
    fun about(part: SessionPart, kid: Boolean): String = if (!kid) about(part) else when (part) {
        is SessionPart.Chords -> "Chords to find, one at a time."
        is SessionPart.Lesson -> "Something new with the pup."
        is SessionPart.Loop -> "One tricky bit, nice and slow. The notes wait for you."
        is SessionPart.Song -> "The whole song. The notes wait for you."
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

    // Kids (2.1): the session goodbye, the goal line and the paw calendar. "{name}" becomes ", Mia" (RunWords.named).

    /** What the pup says (and the voice says, name and all, generated on the phone) when a kid's daily goal is reached. */
    val GOODNIGHTS = listOf(
        "Goodnight{name}! You played so well today. The pup needs a nap.",
        "What a lot of piano{name}! The pup is yawning. See you tomorrow.",
        "That's today's playing done{name}. Goodnight from the pup!",
    )

    /** The goodnight for [name]; [seed] picks the variant (the day of the year keeps it fresh). */
    fun goodnight(name: String?, seed: Int = 0): String = RunWords.named(GOODNIGHTS[Math.floorMod(seed, GOODNIGHTS.size)], name)

    /** Under the goodnight: what is still open. */
    const val STILL_OPEN = "Songs and the Jam are still open."

    /** When a kid opens a new lesson after the goodbye: the pup is asleep, lessons are for tomorrow. */
    fun lessonsAsleep(name: String?): String = RunWords.named("Shh{name}, the pup is asleep. New lessons tomorrow!", name)

    /** The home goal line for a kid: how the pup feels, never minutes. */
    fun kidGoal(practisedSec: Int, goalMin: Int): String {
        val done = if (goalMin <= 0) 0f else practisedSec / (goalMin * 60f)
        return when {
            practisedSec <= 0 -> "The pup is ready to play!"
            done >= 1f -> "All done for today. Goodnight, paws!"
            done >= 0.75f -> "Almost bedtime for the pup."
            else -> "Keep playing! The pup is wide awake."
        }
    }

    /** A kid's row in the Practice today panel: what the part is, no counts, bars or minutes. */
    fun kidRow(part: SessionPart): String = when (part) {
        is SessionPart.Chords -> "Chords to find"
        is SessionPart.Lesson -> "A lesson: ${part.title}"
        is SessionPart.Loop -> "The tricky bit of ${part.title}"
        is SessionPart.Song -> "${part.title}, the notes wait for you"
    }

    /** The Practice today panel's headline for a kid, in place of "About 10 min". */
    const val KID_TODAY = "Today with the pup"

    /** The panel's line once a kid's session is done (the grown-up's names minutes). */
    const val KID_DONE_TODAY = "Done for today. The pup is proud of you!"

    /** The celebration line after a kid's Practice today, said with the name (generated on the phone). */
    fun kidSessionDone(name: String?): String = RunWords.named("You did it all{name}! See you tomorrow.", name)

    /** The line on the paw calendar: grown-ups get the count, kids the idea. */
    fun pawLine(practisedDays: Int, kid: Boolean): String = when {
        kid -> if (practisedDays == 0) "Play today to stamp a paw." else "A paw for every day you play."
        practisedDays == 0 -> "No paws yet this week."
        else -> "$practisedDays ${if (practisedDays == 1) "day" else "days"} this week"
    }
}
