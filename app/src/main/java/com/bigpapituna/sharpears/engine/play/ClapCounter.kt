package com.bigpapituna.sharpears.engine.play

/**
 * Counts claps (and taps on the piano lid, and drum hits) in raw mic audio: a short broadband burst, no pitch, at
 * least [REFRACTORY_MS] apart. Feed it every chunk in order; [process] returns how many claps it confirmed in that
 * chunk. A clap is confirmed [CONFIRM_MS] after it starts (once it has died away), so a screen that times claps
 * subtracts that.
 *
 * Same interface as the playground team's engine/playground/ClapCounter.kt (lesson 7); the integrator keeps one.
 *
 * How: 5 ms frames. A clap starts where a frame is [JUMP_DB] above the room's background, got there within 10 ms
 * (a hammer-like attack, not a voice swelling), is louder than [FLOOR_DB], and is broadband: the energy of the first
 * difference is at least [BROADBAND] of the energy itself (white noise reads 2, a voice's vowel about 0.05, a piano
 * note less). It counts when, [CONFIRM_MS] later, the sound has fallen [DECAY_DB] below its peak: a hiss or a held
 * "shh" stays loud and is dropped.
 */
class ClapCounter(val sampleRate: Int = 48_000) {
    private val frame = sampleRate / 200                      // 5 ms
    private var acc = 0.0                                     // sum of squares in the frame so far
    private var accDiff = 0.0                                 // ... of the first difference
    private var filled = 0
    private var prev = 0f
    private var frames = 0L

    private val recent = DoubleArray(3)                       // energies of the last three frames (newest last)
    private var background = 1e-7                             // slow estimate of the room, mean square
    private var lastClap = Long.MIN_VALUE / 2                 // frame index of the last onset that counted or waits
    private var pendingAt = -1L                               // frame of an onset waiting to be confirmed
    private var pendingPeak = 0.0

    /** Claps counted so far. */
    var total = 0; private set

    fun process(chunk: FloatArray): Int {
        var found = 0
        for (x in chunk) {
            val d = x - prev
            prev = x
            acc += x * x
            accDiff += d * d
            if (++filled == frame) {
                if (frameDone(acc / frame, accDiff / frame)) found++
                acc = 0.0; accDiff = 0.0; filled = 0
            }
        }
        total += found
        return found
    }

    /** One 5 ms frame of mean square [e] (and [hf] for the first difference). True when a clap is confirmed here. */
    private fun frameDone(e: Double, hf: Double): Boolean {
        val f = frames++
        val tenMsAgo = recent[1]
        recent[0] = recent[1]; recent[1] = recent[2]; recent[2] = e
        var confirmed = false

        if (pendingAt >= 0) {
            pendingPeak = maxOf(pendingPeak, e)
            if (f - pendingAt >= CONFIRM_MS / 5) {
                // Died away: a clap. Still loud: a hiss, a voice or a held note; not counted.
                confirmed = e <= pendingPeak * DECAY
                pendingAt = -1
            }
        } else if (f - lastClap >= REFRACTORY_MS / 5 &&
            e > FLOOR && e > background * JUMP && e > tenMsAgo * ATTACK && hf >= BROADBAND * e) {
            pendingAt = f; pendingPeak = e; lastClap = f
        }

        // The background follows the room slowly upward and quickly downward, so a clap barely moves it.
        background = if (e > background) background + (e - background) * 0.01 else background + (e - background) * 0.2
        background = background.coerceAtLeast(1e-9)
        return confirmed
    }

    companion object {
        const val REFRACTORY_MS = 300L
        /** A clap is reported this long after it starts. */
        const val CONFIRM_MS = 80L
        const val JUMP_DB = 15.0
        const val FLOOR_DB = -50.0
        const val DECAY_DB = 10.0
        /** Energy of the first difference over the energy: 2 for white noise, near 0 for low tones. */
        const val BROADBAND = 0.35
        private val JUMP = Math.pow(10.0, JUMP_DB / 10)
        private val FLOOR = Math.pow(10.0, FLOOR_DB / 10)
        private val DECAY = Math.pow(10.0, -DECAY_DB / 10)
        /** Ten times the energy of 10 ms before: an attack, not a swell. */
        private const val ATTACK = 10.0
    }
}
