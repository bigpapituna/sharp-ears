package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.fitRange
import com.bigpapituna.sharpears.engine.Note

/**
 * How big the on-screen keys are. [REGULAR] is the player as it always was (at least two octaves); [BIG] is for kids
 * (docs/KIDS-PLAN.md section 3, "Big and slow"): the keyboard shows just the octave the notes need, so each key is
 * about twice as wide (C4 to C5 is 8 white keys instead of 15 on the Fold), and the keyboard takes a bigger share of
 * the stage ([heightShare], against the lane's 1). Labels and colour pads grow with [labelScale].
 * The lane uses the same range, so falling notes stay over their keys.
 */
enum class KeyboardSize(val minSpan: Int, val heightShare: Float, val labelScale: Float) {
    REGULAR(24, 0.45f, 1f),
    BIG(12, 0.62f, 1.35f);

    /**
     * The keys to show for notes at [midis]: from the C at or below the lowest to the C at or above the highest, at
     * least [minSpan] semitones. REGULAR is exactly the player's [fitRange]; songs wider than five octaves fall back to it.
     */
    fun range(midis: Collection<Int>): IntRange {
        if (this == REGULAR || midis.isEmpty()) return fitRange(asNotes(midis))
        val low = midis.min().let { it - Math.floorMod(it, 12) }
        val top = midis.max().let { if (Math.floorMod(it, 12) == 0) it else it + 12 - Math.floorMod(it, 12) }
        return when {
            top - low < minSpan -> low..(low + minSpan)
            top - low <= 60 -> low..top
            else -> fitRange(asNotes(midis))
        }
    }

    fun rangeOf(notes: List<Note>): IntRange = range(notes.map { it.midi })

    companion object {
        /** Kids get the big keys. */
        fun forKid(kid: Boolean) = if (kid) BIG else REGULAR
    }
}

private fun asNotes(midis: Collection<Int>) = midis.map { Note(it, 0L, 1L, Hand.RIGHT, 80) }
