package com.bigpapituna.sharpears.extras

import com.bigpapituna.sharpears.engine.lessons.CheerTrigger

/**
 * 2.16: the moments the app already detects, handed to the build's optional extra through [Extras] (the door: src/afterdark
 * on a build with it, src/noafterdark's empty one otherwise; CLAUDE.md, 2.16). Names only: no words, no sounds
 * here. The cheer pops' triggers ([of]), a lesson's first playing step ([START]) and its finish with its stars ([FINISH],
 * [STARS3], [STARS2], also a song's recap), a wrong key or a try that did not pass ([MISTAKE]), a hint or a second failed try
 * ([HARD]), the comeback card ([COMEBACK]) and Practice today's "Done for today" ([STREAK] with two days or more in a row,
 * else [GOODNIGHT]).
 */
enum class Moment {
    START, RUN, RECOVERY, HALFWAY, HARD_PASSED, STEADY, FOCUS, NUDGE, STEP_PASSED,
    MISTAKE, HARD, FINISH, STARS3, STARS2, COMEBACK, STREAK, GOODNIGHT;

    companion object {
        /** The cheer pop's trigger as a moment. */
        fun of(t: CheerTrigger): Moment = when (t) {
            CheerTrigger.RUN -> RUN
            CheerTrigger.RECOVERY -> RECOVERY
            CheerTrigger.HALFWAY -> HALFWAY
            CheerTrigger.HARD_PASSED -> HARD_PASSED
            CheerTrigger.STEADY -> STEADY
            CheerTrigger.FOCUS -> FOCUS
            CheerTrigger.NUDGE -> NUDGE
            CheerTrigger.STEP_PASSED -> STEP_PASSED
        }

        /** A finish with its stars: three or two get their own moment, anything else is the plain finish. */
        fun finish(stars: Int): Moment = when (stars) { 3 -> STARS3; 2 -> STARS2; else -> FINISH }
    }
}

/** What the extra took a moment for: the [words] the screen shows in its own bubble, for [ms]. */
data class ExtraLine(val words: String, val ms: Long)
