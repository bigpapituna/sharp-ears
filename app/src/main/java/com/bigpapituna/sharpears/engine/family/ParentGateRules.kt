package com.bigpapituna.sharpears.engine.family

import kotlin.random.Random

/**
 * The parent gate's rules (docs/KIDS-PLAN.md section 2): hold the pup for [HOLD_MS], then answer a sum of two
 * numbers from 2 to 9 ("7 plus 5"). Passing keeps the gate open for [OPEN_MS], so a grown-up changing several
 * settings is asked once. [TRIES] wrong answers in a row make the gate wait ([Lockout]), so a kid cannot guess
 * through it.
 */
object ParentGateRules {
    const val HOLD_MS = 3_000L
    const val OPEN_MS = 10 * 60_000L
    val ADDENDS = 2..9
    /** Wrong answers in a row before the gate waits. */
    const val TRIES = 3
    /** The first wait; each next one doubles, up to [MAX_WAIT_MS]. */
    const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 10 * 60_000L
    /** A day after the last wait ended, the waits start again from [FIRST_WAIT_MS]. */
    const val WAITS_FORGOTTEN_MS = 24 * 60 * 60_000L

    /** The wait after the gate's [n]th lockout (0 the first): 30 s, 60 s, 120 s, doubling, at most ten minutes. */
    fun waitMs(n: Int): Long = if (n >= 10) MAX_WAIT_MS else (FIRST_WAIT_MS shl n.coerceAtLeast(0)).coerceAtMost(MAX_WAIT_MS)

    /** What the gate says while it waits: "Try again in 30 seconds." */
    fun waitLine(seconds: Int): String = if (seconds == 1) "Try again in 1 second." else "Try again in $seconds seconds."

    /**
     * The gate's wrong answers (QA 2.8.3): [wrong] in a row since the last pass or wait, [waits] made so far, and the wait
     * running until [until] (epoch ms, 0 none). A pass forgets all of it.
     */
    data class Lockout(val wrong: Int = 0, val waits: Int = 0, val until: Long = 0L) {
        /** True while the wait runs at [now]. A clock turned back never makes it longer than [MAX_WAIT_MS]. */
        fun locked(now: Long): Boolean = until > 0 && now < until && until - now <= MAX_WAIT_MS

        /** Whole seconds left of the wait at [now], rounded up; 0 when there is none. */
        fun secondsLeft(now: Long): Int = if (!locked(now)) 0 else ((until - now + 999) / 1000).toInt()

        /** A wrong answer at [now]: the [TRIES]th in a row starts the next wait. An answer while it waits does not count. */
        fun wrongAt(now: Long): Lockout {
            if (locked(now)) return this
            val past = if (until > 0 && now - until >= WAITS_FORGOTTEN_MS) Lockout() else this
            return if (past.wrong + 1 >= TRIES) Lockout(0, past.waits + 1, now + waitMs(past.waits)) else past.copy(wrong = past.wrong + 1)
        }

        /** "wrong,waits,until", what the phone keeps. */
        fun encode(): String = "$wrong,$waits,$until"

        companion object {
            /** Reads [encode]'s text; anything else is no lockout. */
            fun decode(text: String?): Lockout {
                val parts = text?.split(',')?.takeIf { it.size == 3 } ?: return Lockout()
                val wrong = parts[0].toIntOrNull() ?: return Lockout()
                val waits = parts[1].toIntOrNull() ?: return Lockout()
                val until = parts[2].toLongOrNull() ?: return Lockout()
                return if (wrong < 0 || waits < 0 || until < 0) Lockout() else Lockout(wrong.coerceAtMost(TRIES - 1), waits, until)
            }
        }
    }

    /**
     * The launch picker (QA 2.8.3): once the family has a kid, a grown-up's tile and Add someone ([picked] null) ask the
     * gate, so a kid cannot open the grown-ups' app or make themselves a profile. A kid's own tile never asks.
     */
    fun pickerAsks(family: List<Profile>, picked: Profile?): Boolean = family.any { it.isKid } && picked?.isKid != true

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
