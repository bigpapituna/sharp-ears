package com.bigpapituna.sharpears.engine.pup

import com.bigpapituna.sharpears.engine.lessons.CheerLines
import com.bigpapituna.sharpears.engine.lessons.CheerTrigger

/**
 * Tap the pup to play it again (2.14, Roy), the rules without Android (PupTapTest): a finish screen's pup replays the clip or
 * beat that played as he landed, with a small sparkle, as often as anyone taps; his voice line is said the first tap only.
 * ui/pup/PupReplay.kt draws it.
 */
object PupTap {
    /** How many sparkles fly out at a tap, and for how long (ms). */
    const val SPARKLES = 6
    const val SPARKLE_MS = 500

    /**
     * The line he says on the first tap: a kid hears one of his own recorded cheers ("Woof woof! Amazing!"); a grown-up's
     * finish stays quiet (null), as their pops are (Settings > Playing > Encouragement, "Say it aloud").
     */
    fun line(kid: Boolean): String? = if (kid) LINE else null
    const val LINE = "Woof woof! Amazing!"

    /** Whether the tap speaks: there is a line and it has not been said yet. */
    fun speaks(alreadySpoke: Boolean, line: String?): Boolean = !alreadySpoke && line != null

    /** The line is one of the recorded kid cheers, so the voice has it (PupTapTest). */
    fun isRecorded(line: String): Boolean = line in CheerLines.family(CheerTrigger.RUN, kid = true)
}
