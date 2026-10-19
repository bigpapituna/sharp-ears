package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.skin.Stars

/**
 * The words on a song's end-of-run recap, in the pup's voice: a headline by result and one coaching line.
 * No milliseconds and no symbols: timing reads as "right on the beat" or "a hair late".
 *
 * Kids (2.1, docs/KIDS-PLAN.md section 3) get their own vocabulary, chosen by the profile kind in [headline] and
 * [coaching]: picked by the stars (engine/skin/Stars), never a number, never "New best", and the pup says the kid's
 * name on every recap. Grown-ups keep the words below unchanged.
 */
object RunWords {
    /** A best worth a party: the first score of a song is always a "best", but 2% is not a celebration. */
    fun celebrates(accuracy: Int, newBest: Boolean) = newBest && accuracy >= CELEBRATE_FROM
    const val CELEBRATE_FROM = 60

    /** The headline for a Perform run at [accuracy] percent. */
    fun title(accuracy: Int, newBest: Boolean): String = when {
        celebrates(accuracy, newBest) -> "New best!"
        accuracy >= 90 -> "Beautifully played"
        accuracy >= 70 -> "Nicely done"
        accuracy >= 40 -> "Getting there"
        else -> "A first pass"
    }

    /** The headline after a Wait run with [wrongKeys] wrong notes. */
    fun waitTitle(wrongKeys: Int): String = when {
        wrongKeys == 0 -> "Clean run!"
        wrongKeys <= 5 -> "Played through"
        else -> "You made it to the end"
    }

    /** Timing in words: mean offset [biasMs] (positive is late). */
    fun timing(biasMs: Int): String = when {
        kotlin.math.abs(biasMs) < 25 -> "right on the beat"
        kotlin.math.abs(biasMs) < 80 -> if (biasMs > 0) "a hair late" else "a hair early"
        else -> if (biasMs > 0) "a little behind the beat" else "a little ahead of the beat"
    }

    /**
     * The coaching line for a Perform run: [hardest] is the first bar worth looping (null when none), [speed] the
     * playback speed (1.0 is full).
     */
    fun line(r: Recap, hardest: Int?, speed: Double): String = when {
        r.good + r.ok == 0 -> "No notes landed in time yet. Try Wait mode first: the notes wait for you."
        r.accuracy < 40 -> if (hardest != null) "Try bar $hardest on a loop at half speed, then come back." else "Try it at half speed. Slow and steady makes it stick."
        r.accuracy < 70 -> "Your timing was ${timing(r.biasMs)}." + (hardest?.let { " Bar $it is the one to loop." } ?: " One more go?")
        r.accuracy < 90 -> "Your timing was ${timing(r.biasMs)}. A few more runs and it's yours."
        speed < 1.0 -> "Your timing was ${timing(r.biasMs)}. Ready to nudge the speed up?"
        else -> "Your timing was ${timing(r.biasMs)}. That one sang."
    }

    /** The coaching line after a Wait run. */
    fun waitLine(wrongKeys: Int, hardest: Int?): String = when {
        wrongKeys == 0 -> "Not one wrong note. Try it in Perform next, the notes keep moving."
        hardest != null -> "$wrongKeys wrong ${if (wrongKeys == 1) "note" else "notes"} on the way. Bar $hardest is worth a loop."
        else -> "$wrongKeys wrong ${if (wrongKeys == 1) "note" else "notes"} on the way. Once more and they'll settle."
    }

    // Kids (2.1). Templates: "{name}" becomes ", Mia" (or nothing without a name), so a line reads the same either way.

    /** Kid headlines by stars (index 0 is one star). Short enough to read aloud in one breath. */
    val KID_TITLES: List<List<String>> = listOf(
        listOf("You played it!", "You got to the end!", "Good going!"),
        listOf("Nice playing!", "That sounded lovely!", "Woof! Nice one!"),
        listOf("You did it!", "The pup is dancing!", "Wow, wow, wow!"),
    )

    /** Kid lines by stars, with the name. */
    val KID_LINES: List<List<String>> = listOf(
        listOf("You got to the end{name}! Let's play it once more, nice and slow.",
            "Every try makes it easier{name}. Again?",
            "Good job sticking with it{name}. Shall we try again?"),
        listOf("Lovely playing{name}. Play it again and catch every note?",
            "So close to every star{name}. One more go?",
            "That sounded great{name}. Ready for another try?"),
        // Three stars in Perform start at 90 percent, so no line claims every note was right.
        listOf("You played it so well{name}. The pup wants to hear it again!",
            "That was perfect{name}! Paws in the air!",
            "You played it just right{name}. What a star!"),
    )

    /** "{name}" filled: ", Mia", or nothing when there is no name. */
    fun named(template: String, name: String?): String =
        template.replace("{name}", name?.trim()?.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: "")

    /** A kid's headline for [stars] (1..3); [seed] picks among the variants so runs do not all sound the same. */
    fun kidTitle(stars: Int, seed: Int = 0): String = KID_TITLES[stars.coerceIn(1, 3) - 1].let { it[Math.floorMod(seed, it.size)] }

    /** A kid's line for [stars] with their [name]. */
    fun kidLine(stars: Int, name: String?, seed: Int = 0): String =
        named(KID_LINES[stars.coerceIn(1, 3) - 1].let { it[Math.floorMod(seed, it.size)] }, name)

    /** The recap headline for whoever played: kids by the stars, grown-ups by the accuracy (Perform) or wrong keys (Wait). */
    fun headline(kind: ProfileKind, recap: Recap?, wrongKeys: Int, newBest: Boolean, seed: Int = 0): String = when {
        kind == ProfileKind.KID -> kidTitle(Stars.of(recap, wrongKeys), seed)
        recap != null -> title(recap.accuracy, newBest)
        else -> waitTitle(wrongKeys)
    }

    /** The recap's one line for whoever played: kids hear their [name], grown-ups get the coaching above. */
    fun coaching(kind: ProfileKind, recap: Recap?, wrongKeys: Int, hardest: Int?, speed: Double, name: String? = null, seed: Int = 0): String = when {
        kind == ProfileKind.KID -> kidLine(Stars.of(recap, wrongKeys), name, seed)
        recap != null -> line(recap, hardest, speed)
        else -> waitLine(wrongKeys, hardest)
    }

    /** The party (sunburst, confetti, flourish): grown-ups on a new best worth it ([celebrates]), kids on three stars. */
    fun party(kind: ProfileKind, recap: Recap?, wrongKeys: Int, newBest: Boolean): Boolean =
        if (kind == ProfileKind.KID) Stars.of(recap, wrongKeys) == Stars.MAX else celebrates(recap?.accuracy ?: 0, newBest)

    /** After a kid's lesson step: on to the next one, or once more (no "not quite", no numbers). */
    fun kidStepLine(passed: Boolean, last: Boolean, name: String?): String = named(when {
        passed && last -> "You did it{name}! That's the whole lesson."
        passed -> "You did it{name}! On to the next one."
        else -> "Almost{name}! Let's try that again."
    }, name)

    /** The end of a kid's lesson: the headline and the line under it. */
    const val KID_LESSON_TITLE = "You did it!"
    fun kidLessonLine(name: String?): String = named("Lesson done{name}. The pup is so proud of you.", name)
}
