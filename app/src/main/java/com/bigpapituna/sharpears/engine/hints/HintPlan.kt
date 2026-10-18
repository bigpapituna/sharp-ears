package com.bigpapituna.sharpears.engine.hints

/** How far hint escalation has gone in one stuck moment. */
enum class HintStep {
    NONE,
    /** The waiting key glows. */
    GLOW,
    /** The painted hand points at it (and it keeps glowing). */
    POINT,
    /** The app plays it and the pup says "your turn" (glow and hand stay). */
    PLAY,
}

/**
 * Hint escalation while wait mode is stuck on the same notes (docs/KIDS-PLAN.md 3.6): after a, b and c seconds
 * (Profile.hintSeconds: grown-ups 6/12/18, kids 4/8/12) the key glows, the painted hand points at it, then the app
 * plays it. The old single hint (the 5 s stuck moment) is step a's place now. Pure timing: the player says when a
 * wait begins and ends, and asks [advance] every frame or so.
 */
class HintPlan(seconds: List<Int>) {
    /** The three thresholds in ms, increasing; anything malformed falls back to the grown-up timings. */
    val stepsMs: List<Long> = (seconds.takeIf { s -> s.size == 3 && s[0] > 0 && s[0] < s[1] && s[1] < s[2] } ?: DEFAULT)
        .map { it * 1000L }
    private var startMs = 0L
    private var reached = HintStep.NONE
    /** True between [begin] and [end]. */
    var waiting = false; private set

    /** A wait began at [nowMs]: escalation starts over. */
    fun begin(nowMs: Long) { startMs = nowMs; reached = HintStep.NONE; waiting = true }

    /** The wait ended (played, paused, left). */
    fun end() { waiting = false; reached = HintStep.NONE }

    /** The step for a wait of [waitedMs]. */
    fun stepAt(waitedMs: Long): HintStep = when {
        waitedMs >= stepsMs[2] -> HintStep.PLAY
        waitedMs >= stepsMs[1] -> HintStep.POINT
        waitedMs >= stepsMs[0] -> HintStep.GLOW
        else -> HintStep.NONE
    }

    /** Where the hint is now (NONE when not waiting). */
    fun current(): HintStep = if (waiting) reached else HintStep.NONE

    /**
     * The step newly reached by [nowMs], once each (a frame that jumps past two steps reports the later one), or null
     * when nothing changed.
     */
    fun advance(nowMs: Long): HintStep? {
        if (!waiting) return null
        val s = stepAt(nowMs - startMs)
        if (s.ordinal <= reached.ordinal) return null
        reached = s
        return s
    }

    companion object {
        val DEFAULT = listOf(6, 12, 18)
        /** What the pup says after the app plays the waiting keys (the PLAY step). */
        const val YOUR_TURN = "Your turn."
    }
}
