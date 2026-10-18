package com.bigpapituna.sharpears.engine.hints

/**
 * The room-noise watchdog (FAMILY.md, detector): a room with talking or TV makes the mic hear strikes that are not
 * the song. Most do not look like a piano at all (the matcher's noise: a loudness jump with no piano note found in
 * it); those are never painted, but a room full of them is a room where the odd wrong key may be the TV. This counts,
 * over the last [windowMs], the noise heard while the keys the song expects stay silent (no right note for
 * [quietMs]). Past [threshold] of them, with at least [minPainted] wrong keys painted in the same stretch, it
 * suggests once per session that the app stop painting wrong keys. Wrong keys alone never trip it: they look like a
 * piano, and a learner hitting wrong keys is not a noisy room. Feed it from the mic's events; ask [check].
 *
 * Tuned on Roy's recordings (NoiseWatchTest): the scale session with talking in the room (33/33 right notes, 57
 * noise jumps, no wrong keys) and the TV session with nothing played (119 noise jumps, nothing painted) never trip
 * it; the two sessions where wrong keys are painted among the noise trip it (10 s and 58 s in).
 */
class NoiseWatch(
    val threshold: Int = 6,
    val windowMs: Long = 60_000,
    val quietMs: Long = 4_000,
    val minPainted: Int = 2,
    /** With no right note for this long, the suggestion also offers to pause listening (listen or wait mode). */
    val idleMs: Long = 20_000,
) {
    private val suspicious = ArrayDeque<Long>()
    private val painted = ArrayDeque<Long>()
    private var lastRight = Long.MIN_VALUE / 2
    private var started = Long.MIN_VALUE
    /** True once the suggestion was made: it is made once per session (this object's life). */
    var offered = false; private set

    /** What the pup offers: stop painting wrong keys, and pause listening when nothing has been played for a while. */
    data class Suggestion(val atMs: Long, val pauseListening: Boolean, val noise: Int, val painted: Int)

    /** A right note was heard. */
    fun right(nowMs: Long) { first(nowMs); lastRight = nowMs }

    /** A wrong key was heard and painted. */
    fun wrong(nowMs: Long) { first(nowMs); if (quiet(nowMs)) painted.addLast(nowMs) }

    /** A strike with no piano note in it (the matcher's noise). */
    fun noise(nowMs: Long) { first(nowMs); if (quiet(nowMs)) suspicious.addLast(nowMs) }

    /** Noise counted in the window now. */
    fun count(nowMs: Long): Int { trim(nowMs); return suspicious.size }

    /** The suggestion, the first time the room crosses the line; null before and ever after. */
    fun check(nowMs: Long): Suggestion? {
        if (offered) return null
        trim(nowMs)
        if (suspicious.size < threshold || painted.size < minPainted) return null
        offered = true
        val sinceRight = if (lastRight > Long.MIN_VALUE / 2) nowMs - lastRight else nowMs - started
        return Suggestion(nowMs, pauseListening = sinceRight >= idleMs, noise = suspicious.size, painted = painted.size)
    }

    private fun quiet(nowMs: Long) = nowMs - lastRight >= quietMs

    companion object {
        /** The pup's offer when the watchdog trips (shown and spoken). */
        const val LINE = "Noisy room? I can stop painting wrong keys for now."
        /** The same offer to a kid: no "wrong", the colours they see instead (KIDS-PLAN: nothing called wrong). */
        const val LINE_KID = "It's noisy in here! Shall I stop the yellow and red keys for now?"
        fun line(kid: Boolean) = if (kid) LINE_KID else LINE
    }
    private fun first(nowMs: Long) { if (started == Long.MIN_VALUE) started = nowMs }
    private fun trim(nowMs: Long) {
        while (suspicious.isNotEmpty() && nowMs - suspicious.first() > windowMs) suspicious.removeFirst()
        while (painted.isNotEmpty() && nowMs - painted.first() > windowMs) painted.removeFirst()
    }
}
