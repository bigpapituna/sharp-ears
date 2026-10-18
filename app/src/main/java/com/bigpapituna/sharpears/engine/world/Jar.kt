package com.bigpapituna.sharpears.engine.world

/**
 * The treat jar: every note played drops a crumb in; [CAPACITY] notes fill it. A full jar can be tipped: the pup eats
 * (a treat fed) and the jar starts again from empty. Notes past full are not counted (the jar just stays full).
 */
object TreatJar {
    const val CAPACITY = 200
    const val PER_BISCUIT = 10
    /** Biscuits in a full jar. */
    const val BISCUITS = CAPACITY / PER_BISCUIT

    fun add(notes: Int, more: Int): Int = (notes + more.coerceAtLeast(0)).coerceIn(0, CAPACITY)
    fun fraction(notes: Int): Float = notes.coerceIn(0, CAPACITY) / CAPACITY.toFloat()
    fun full(notes: Int): Boolean = notes >= CAPACITY
    /** Notes still to play before it can be tipped. */
    fun toFull(notes: Int): Int = (CAPACITY - notes).coerceAtLeast(0)

    /** Biscuits drawn in the jar at [notes]: one per [PER_BISCUIT] notes. */
    fun biscuits(notes: Int): Int = notes.coerceIn(0, CAPACITY) / PER_BISCUIT

    /** What the pup says about the jar: nothing to count for kids, the notes left for grown-ups. Never a nag. */
    fun line(notes: Int, kid: Boolean, treats: Int): String = when {
        full(notes) -> if (kid) "The jar is full! Tip it for me?" else "The jar is full. Tip it and I get a treat."
        notes == 0 && treats > 0 -> "Yum. Every note you play drops another crumb in."
        notes == 0 -> "Every note you play drops a crumb in my jar."
        kid -> if (fraction(notes) >= 0.75f) "Almost full! A few more notes." else "Play some notes to fill it up."
        else -> "${toFull(notes)} more notes and it's full."
    }

    /** The kid's jar lines, which the kid home speaks when the jar is tapped (the voice catalog records them). */
    val KID_LINES = listOf(line(CAPACITY, true, 0), line(0, true, 1), line(0, true, 0), line(CAPACITY * 3 / 4, true, 0), line(1, true, 0))
}
