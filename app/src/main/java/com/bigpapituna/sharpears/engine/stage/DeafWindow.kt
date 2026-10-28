package com.bigpapituna.sharpears.engine.stage

/**
 * 2.18: when the mic must ignore what it hears because the app is making sound on the speaker. Two kinds of window:
 * - timed ([deafFor]): a sound of known length (a hint's notes, a chime, a stage's ding) and the room's ring after it;
 * - held ([open]/[close]): a line whose end is reported (the voice, the coach). The window lasts exactly until [close], then
 *   its tail. A held window also carries a limit ([open]'s limitMs, StageRule.limitMs) so a lost end never leaves the mic
 *   deaf for good, and [closeAll] ends every held window at once (a screen left, the voice stopped, a profile closed).
 *
 * Read on the mic's capture thread, written on the main thread: every call is synchronized. [clock] is in milliseconds
 * (monotonic; tests pass their own). Pure (DeafWindowTest).
 */
class DeafWindow(private val clock: () -> Long) {
    private var until = Long.MIN_VALUE
    /** Held windows by owner: the time each must end by. */
    private val holds = LinkedHashMap<Any, Long>()
    private var lastDeaf = Long.MIN_VALUE / 2

    /** Deaf for [ms] from now (a window already longer stays). */
    @Synchronized fun deafFor(ms: Long) { until = maxOf(until, clock() + ms) }

    /** Deaf from now until [close] of [owner] (or [limitMs] from now, whichever comes first). Opening it again moves its limit. */
    @Synchronized fun open(owner: Any, limitMs: Long) { holds[owner] = clock() + limitMs }

    /** [owner]'s window ends now; the mic stays deaf [tailMs] more (the room's ring). False when it held none. */
    @Synchronized fun close(owner: Any, tailMs: Long): Boolean {
        val end = holds.remove(owner) ?: return false
        val live = end >= clock()              // a window past its limit already ended: no tail after the fact
        if (live && tailMs > 0) deafFor(tailMs)
        return live
    }

    /** Every held window ends now, with [tailMs] after (0: none, the sound is cut off with the screen). */
    @Synchronized fun closeAll(tailMs: Long = 0L) {
        val any = holds.isNotEmpty()
        holds.clear()
        if (any && tailMs > 0) deafFor(tailMs)
    }

    /** [owner] holds a window now. */
    @Synchronized fun holds(owner: Any): Boolean { expire(); return owner in holds }

    /** Whether what the mic hears is dropped now. */
    @Synchronized fun deaf(): Boolean {
        val now = clock()
        expire()
        val d = now < until || holds.isNotEmpty()
        if (d) lastDeaf = now
        return d
    }

    /** Deaf now or within the last [ms] (readers that decide late, like the pitch tracker). */
    @Synchronized fun deafWithin(ms: Long): Boolean = deaf() || clock() - lastDeaf < ms

    /** When the mic hears again if nothing else happens: the timed window's end, or Long.MAX_VALUE while a window is held. */
    @Synchronized fun hearsAgainAt(): Long { expire(); return if (holds.isNotEmpty()) Long.MAX_VALUE else until }

    private fun expire() { val now = clock(); holds.values.removeAll { it < now } }
}
