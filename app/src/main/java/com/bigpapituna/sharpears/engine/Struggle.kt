package com.bigpapituna.sharpears.engine

/**
 * Spots a learner fighting through a hard passage: [mistakes] wrong notes within [windowMs], then [clean] right
 * notes in a row. That moment earns the pup's high-five.
 */
class Struggle(private val windowMs: Long = 8000, private val mistakes: Int = 3, private val clean: Int = 8) {
    private val recent = ArrayDeque<Long>()
    private var run = 0
    private var struggling = false

    fun mistake(nowMs: Long) {
        recent.addLast(nowMs)
        while (recent.first() < nowMs - windowMs) recent.removeFirst()
        if (recent.size >= mistakes) struggling = true
        run = 0
    }

    /** A right note. True once, when a struggle ends in a clean stretch. */
    fun right(): Boolean {
        run++
        if (!struggling || run < clean) return false
        struggling = false; recent.clear()
        return true
    }
}
