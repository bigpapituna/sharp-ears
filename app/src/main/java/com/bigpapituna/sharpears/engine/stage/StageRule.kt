package com.bigpapituna.sharpears.engine.stage

import com.bigpapituna.sharpears.engine.lessons.ChimeMode

/**
 * 2.18, the one speaker-and-mic rule. audio/Stage.kt asks it; screens ask the Stage, never re-derive it (StageWiringTest).
 *
 * The phone's speaker sits next to its microphone: while the mic listens, a sound the app makes on the speaker would be taken
 * for the player's note. So, with App sound on:
 * - an echo (the sound of a key the player touched, the app's notes in a song, a click under a clap): quiet while the mic
 *   listens, unless headphones are on;
 * - a cue (a sound the player has to hear: a hint's notes, a step pop's chime, a demo, a line said over the listening mic):
 *   played, the mic deaf for it and the room's ring after it, unless headphones are on (then there is nothing to drop).
 * App sound off: neither. Pure (StageRuleTest).
 */
object StageRule {
    /** The app must stay quiet on the speaker now: the mic listens and the sound would come out next to it. */
    fun quiet(listening: Boolean, headphones: Boolean): Boolean = listening && !headphones

    /** An echo may sound: App sound on and not [quiet]. */
    fun echo(soundOn: Boolean, listening: Boolean, headphones: Boolean): Boolean = soundOn && !quiet(listening, headphones)

    /** A cue: skipped with App sound off, played with the mic deaf for it while [quiet], else played as it is. */
    fun cue(soundOn: Boolean, listening: Boolean, headphones: Boolean): ChimeMode = when {
        !soundOn -> ChimeMode.SKIP
        quiet(listening, headphones) -> ChimeMode.PLAY_DEAF
        else -> ChimeMode.PLAY
    }

    /**
     * Whether a sound on the phone's speaker makes the mic deaf at all: always, unless headphones are on. The mic need not be
     * listening yet: a step's mic can start while a line still plays, and the window outlives the start.
     */
    fun deafens(headphones: Boolean): Boolean = !headphones

    /**
     * 2.17 (audit M3), moved here in 2.18: "Listening again" shows as the mic hears again after a line that made it deaf
     * ([deafened]) when the mic is listening ([listening]: a lesson step, a song) once the room's ring has passed.
     */
    fun listeningCue(deafened: Boolean, listening: Boolean): Boolean = deafened && listening

    /** How long "Listening again" stays up. */
    const val CUE_MS = 1_600L

    /**
     * The longest a line on the speaker may keep the mic deaf when its end is never reported (a stuck player): its known
     * length plus [LIMIT_SLACK_MS], or [UNKNOWN_LIMIT_MS] before the length is known. A line that ends, fails or is stopped
     * ends its window at once (DeafWindow.close), so this only bounds a lost callback.
     */
    const val LIMIT_SLACK_MS = 2_000L
    const val UNKNOWN_LIMIT_MS = 30_000L
    fun limitMs(lengthMs: Long?): Long = if (lengthMs != null && lengthMs > 0) lengthMs + LIMIT_SLACK_MS else UNKNOWN_LIMIT_MS
}
