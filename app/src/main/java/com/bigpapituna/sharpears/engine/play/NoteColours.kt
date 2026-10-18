package com.bigpapituna.sharpears.engine.play

/**
 * Boomwhacker colours for the Play corner, as ARGB ints (no Android here): C red, D orange, E yellow, F green,
 * G teal, A blue, B purple, the colours many kids' piano books use. A black key takes the colour halfway between
 * its two white neighbours (C sharp is red-orange). The skin team keeps the same table for the lane and the keys;
 * the integrator keeps one of the two.
 */
object NoteColours {
    /** The seven white keys, C to B. Warm and a little soft, so they sit on the app's palette in light and dark. */
    val WHITE: List<Int> = listOf(
        0xFFE5534B.toInt(),   // C red
        0xFFF08C3A.toInt(),   // D orange
        0xFFF2C94C.toInt(),   // E yellow
        0xFF6CC04A.toInt(),   // F green
        0xFF2BB5A8.toInt(),   // G teal
        0xFF4A8FE0.toInt(),   // A blue
        0xFF9B6BD6.toInt(),   // B purple
    )

    /** Names as a child hears them, C to B. */
    val NAMES = listOf("red", "orange", "yellow", "green", "teal", "blue", "purple")

    private val WHITE_INDEX = mapOf(0 to 0, 2 to 1, 4 to 2, 5 to 3, 7 to 4, 9 to 5, 11 to 6)

    /** The colour of any key, as ARGB. */
    fun argb(midi: Int): Int {
        val pc = Math.floorMod(midi, 12)
        WHITE_INDEX[pc]?.let { return WHITE[it] }
        return mix(WHITE.getValue(pc - 1), WHITE.getValue(pc + 1))
    }

    /** The colour's name for a white key ("red"), or the two it sits between for a black key ("red orange"). */
    fun name(midi: Int): String {
        val pc = Math.floorMod(midi, 12)
        WHITE_INDEX[pc]?.let { return NAMES[it] }
        return NAMES.getValue(pc - 1) + " " + NAMES.getValue(pc + 1)
    }

    private fun List<Int>.getValue(pc: Int): Int = this[WHITE_INDEX.getValue(pc)]
    @JvmName("nameAt") private fun List<String>.getValue(pc: Int): String = this[WHITE_INDEX.getValue(pc)]

    /** Channel-wise halfway mix of two opaque colours. */
    internal fun mix(a: Int, b: Int): Int {
        fun ch(c: Int, shift: Int) = (c ushr shift) and 0xFF
        val r = (ch(a, 16) + ch(b, 16)) / 2
        val g = (ch(a, 8) + ch(b, 8)) / 2
        val bl = (ch(a, 0) + ch(b, 0)) / 2
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
