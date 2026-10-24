package com.bigpapituna.sharpears.engine.playground

/**
 * Time at a Playground stage is practice, as time in the embedded player is: the daily goal, its cheer, bedtime and the
 * paw calendar all read today's practice seconds (Audit 2026-10-20: the stages counted none, so a kid could play the
 * fair's four games, or most of the night sky, and get no paw for the day). The concert and the set list run the
 * embedded player inside their stage, which counts its own time, so they are left to it.
 */
object StageTime {
    /** The games whose stage runs the embedded player (PlayerScreen) and so already counts its time. */
    val RUN_THE_PLAYER: Set<Game> = setOf(Game.CONCERT, Game.SET_LIST)

    /** True when [game]'s stage should count its own time on screen as practice. */
    fun counts(game: Game?): Boolean = game != null && game !in RUN_THE_PLAYER

    /** How often a stage writes the time so far (the goal is checked when a step or a lesson ends), as the player does. */
    const val FLUSH_MS = 15_000L
}

/**
 * A button that comes up where a kid was tapping keys (a step's cheer bar, a finish) ignores taps for [MS] after it shows.
 * QA 2.8.3: taps meant for the keys at the end of "Let's hear you play" pressed "To the map", and the next one My stickers.
 */
object SettleTaps {
    const val MS = 900L

    /** True once a button shown at [shownAtMs] may take a tap at [nowMs] (a monotonic clock). */
    fun settled(shownAtMs: Long, nowMs: Long): Boolean = nowMs - shownAtMs >= MS
}
