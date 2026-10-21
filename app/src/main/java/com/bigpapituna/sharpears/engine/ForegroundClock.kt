package com.bigpapituna.sharpears.engine

/**
 * A clock in ms that stands still while the app is off screen (MainActivity stops and starts it). Screens that time
 * themselves for practice minutes (Practice today's chords, theory lessons, ear games, the workout) read it, so a phone
 * locked on one of them overnight, or a wall clock set back, no longer credits hours (or negative time) to the morning.
 */
class ForegroundClock(private val time: () -> Long) {
    private var stoppedAt = -1L
    private var away = 0L

    @Synchronized fun stop() { if (stoppedAt < 0) stoppedAt = time() }
    @Synchronized fun start() { if (stoppedAt >= 0) { away += time() - stoppedAt; stoppedAt = -1 } }
    @Synchronized fun nowMs(): Long = time().let { t -> t - away - if (stoppedAt >= 0) t - stoppedAt else 0 }

    companion object {
        /** The app's own, on the monotonic clock. */
        val app = ForegroundClock { System.nanoTime() / 1_000_000 }
    }
}
