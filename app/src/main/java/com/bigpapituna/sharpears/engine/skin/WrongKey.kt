package com.bigpapituna.sharpears.engine.skin

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** How far off a wrong key was: a slip to a neighbour (yellow) or somewhere else entirely (red). */
enum class WrongTone { NEAR, FAR }

/**
 * A wrong key the player struck: [midi] is the key, [distance] the semitones to the nearest note the song expected
 * (FAMILY.md, "detector and skin, together"). Within [NEAR_MAX] semitones it is a slip to a neighbour and lights
 * yellow; further away it lights red. Kids see the same colours (no words). The detector's MicEvent.Wrong carries
 * `nearest` and `distance`; touch and MIDI build one with [of].
 */
data class WrongKey(val midi: Int, val distance: Int) {
    init { require(distance >= 0) { "distance is a count of semitones" } }

    val tone: WrongTone get() = tone(distance)

    companion object {
        /** A slip of one or two semitones (the key next door, or the one after) is yellow. */
        const val NEAR_MAX = 2

        fun tone(distance: Int): WrongTone = if (abs(distance) <= NEAR_MAX) WrongTone.NEAR else WrongTone.FAR

        /** [midi] against the notes the song waits for: the nearest one sets the distance. Null when nothing is expected. */
        fun of(midi: Int, expected: Collection<Int>): WrongKey? =
            expected.minOfOrNull { abs(it - midi) }?.let { WrongKey(midi, it) }
    }
}

/**
 * The shape of a wrong-key flash in time, shared by the key and its column on the lane so they move together.
 * About [DURATION_MS]: the glow jumps on, holds, then fades; a short shake rings out over the first [SHAKE_MS].
 * With the phone's animations off there is no shake and the glow stays steady until it ends.
 */
object WrongFlashCurve {
    const val DURATION_MS = 600.0
    const val SHAKE_MS = 320.0
    /** Shakes per second while it rings out. */
    private const val HZ = 22.0

    /** Glow strength 0..1 at [ms] after the strike. */
    fun glow(ms: Double, still: Boolean = false): Float = when {
        ms < 0 || ms >= DURATION_MS -> 0f
        still -> 1f
        ms < 60 -> (0.55 + 0.45 * ms / 60).toFloat()                      // snaps on
        ms < 260 -> 1f                                                    // holds
        else -> (1 - (ms - 260) / (DURATION_MS - 260)).let { it * it }.toFloat()   // eases out
    }

    /** Sideways offset as a fraction of the shake's reach (-1..1) at [ms]; 0 when still. */
    fun shake(ms: Double, still: Boolean = false): Float {
        if (still || ms < 0 || ms >= SHAKE_MS) return 0f
        val decay = exp(-ms / (SHAKE_MS / 3.2))
        return (sin(2 * PI * HZ * ms / 1000.0) * decay).toFloat()
    }

    fun alive(ms: Double) = ms >= 0 && ms < DURATION_MS
}
