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
}
