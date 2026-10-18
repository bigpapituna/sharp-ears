package com.bigpapituna.sharpears.engine.family

import kotlin.random.Random

/**
 * The parent gate's rules (docs/KIDS-PLAN.md section 2): hold the pup for [HOLD_MS], then answer a sum of two
 * numbers from 2 to 9 ("7 plus 5"). Passing keeps the gate open for [OPEN_MS], so a grown-up changing several
 * settings is asked once.
 */
object ParentGateRules {
    const val HOLD_MS = 3_000L
    const val OPEN_MS = 10 * 60_000L
    val ADDENDS = 2..9

    /** A sum to answer. [spoken] is how the gate shows and says it. */
    data class Sum(val a: Int, val b: Int) {
        val answer: Int get() = a + b
        val spoken: String get() = "$a plus $b"
        /** [typed] is the number pad's digits; leading zeros and blanks are not an answer. */
        fun isRight(typed: String): Boolean = typed.isNotEmpty() && typed.length <= 2 && typed.all { it.isDigit() } &&
            !typed.startsWith("0") && typed.toInt() == answer
    }

    /** A new sum, never the same as [previous] (a wrong answer always brings a different question). */
    fun nextSum(random: Random = Random.Default, previous: Sum? = null): Sum {
        while (true) {
            val s = Sum(random.nextInt(ADDENDS.first, ADDENDS.last + 1), random.nextInt(ADDENDS.first, ADDENDS.last + 1))
            if (s != previous) return s
        }
    }

    /** True while a pass at [passedAt] (epoch ms, 0 = never) still holds at [now]. A pass "from the future" does not. */
    fun isOpen(passedAt: Long, now: Long): Boolean = passedAt > 0 && now >= passedAt && now - passedAt < OPEN_MS

    /** How far the hold ring is, 0..1, after holding for [heldMs]. */
    fun holdProgress(heldMs: Long): Float = (heldMs.coerceAtLeast(0).toFloat() / HOLD_MS).coerceAtMost(1f)

    /** The number pad's edit: a digit is added (two at most), "<" deletes one. */
    fun type(typed: String, key: Char): String = when {
        key == '<' -> typed.dropLast(1)
        key.isDigit() && typed.length < 2 -> typed + key
        else -> typed
    }
}
