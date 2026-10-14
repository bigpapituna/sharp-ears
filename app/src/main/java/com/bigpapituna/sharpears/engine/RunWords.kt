package com.bigpapituna.sharpears.engine

/**
 * The words on a song's end-of-run recap, in the pup's voice: a headline by result and one coaching line.
 * No milliseconds and no symbols: timing reads as "right on the beat" or "a hair late".
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
}
