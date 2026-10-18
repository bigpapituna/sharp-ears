package com.bigpapituna.sharpears.engine.hints

import kotlin.math.abs

/**
 * Naming a wrong key against the notes the song wanted (mic, touch or MIDI): which expected note it was closest to,
 * how far, and whether that is a slip to a neighbour (drawn yellow) or a miss further away (drawn red).
 */
object WrongKeys {
    /** A wrong key this close (semitones) to an expected note is a slip to a neighbour: yellow, not red. */
    const val SLIP = 2
    /** How far from an expected note the mic looks for a wrong key (an octave, so octave slips are named too). */
    const val REACH = 12

    /** The expected note closest to [played] (the lower one on a tie), or null when nothing is expected. */
    fun nearest(played: Int, expected: Collection<Int>): Int? =
        expected.minWithOrNull(compareBy<Int>({ abs(it - played) }, { it }))

    /** Distance in semitones to the [nearest] expected note, or null when nothing is expected. */
    fun distance(played: Int, expected: Collection<Int>): Int? = nearest(played, expected)?.let { abs(it - played) }

    /** Within [SLIP] semitones (and not the note itself): a neighbour. */
    fun isSlip(distance: Int) = distance in 1..SLIP
}

/**
 * A wrong key to show, from any input (mic, touch or MIDI): [midi] was played, [nearest] is the expected note closest
 * to it, [distance] semitones away (both null when nothing was expected). [slip] keys are drawn yellow, the rest red.
 * [id] grows with each one, so the same key twice in a row still flashes twice.
 */
data class WrongKey(val midi: Int, val nearest: Int?, val distance: Int?, val id: Long) {
    val slip: Boolean get() = distance != null && WrongKeys.isSlip(distance)

    companion object {
        /** [played] against the [expected] notes. */
        fun of(played: Int, expected: Collection<Int>, id: Long): WrongKey =
            WrongKey(played, WrongKeys.nearest(played, expected), WrongKeys.distance(played, expected), id)
    }
}
