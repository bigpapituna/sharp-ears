package com.bigpapituna.sharpears.engine

/**
 * The keyboard as answer key: with a MIDI keyboard on USB and the mic on, the mic drives wait mode and the keyboard
 * only checks it. A key pressed among the notes the mic was listening for, that the mic does not report within
 * [windowMs], is a miss; a note the mic reports with no such key pressed around it is a false note. Either is a
 * confirmed detection error (no guessing whether the player hit a wrong key).
 */
class AnswerKey(private val windowMs: Long = 600, private val earlyMs: Long = 300) {
    enum class Kind { MISSED, FALSE }
    data class Mismatch(val kind: Kind, val midi: Int, val atMs: Long)

    private class Ev(val at: Long, val midi: Int, var matched: Boolean = false)
    private val keys = ArrayList<Ev>()      // keyboard presses of notes the mic was listening for
    private val heard = ArrayList<Ev>()     // mic strikes (heard or credited)

    /** A key pressed on the keyboard; [listened] is whether the mic was listening for that note then. */
    fun key(nowMs: Long, midi: Int, listened: Boolean) { if (listened) Ev(nowMs, midi).also { keys += it; pair(it) } }

    /** A note the mic reported. */
    fun mic(nowMs: Long, midi: Int) { Ev(nowMs, midi).also { heard += it; pair(it) } }

    /** Matches a new event with an unmatched one of the other kind: the mic up to [windowMs] after the key, or slightly before. */
    private fun pair(e: Ev) {
        val other = if (e in keys) heard else keys
        val isKey = e in keys
        val m = other.firstOrNull { o ->
            !o.matched && o.midi == e.midi && (if (isKey) o.at - e.at else e.at - o.at) in -earlyMs..windowMs
        } ?: return
        m.matched = true; e.matched = true
    }

    /** Mismatches decided by [nowMs] (events old enough that no partner can still come), oldest first. */
    fun settle(nowMs: Long): List<Mismatch> {
        val out = ArrayList<Mismatch>()
        val doneBefore = nowMs - windowMs - earlyMs
        keys.filter { it.at < doneBefore }.forEach { if (!it.matched) out += Mismatch(Kind.MISSED, it.midi, it.at) }
        heard.filter { it.at < doneBefore }.forEach { if (!it.matched) out += Mismatch(Kind.FALSE, it.midi, it.at) }
        keys.removeAll { it.at < doneBefore }; heard.removeAll { it.at < doneBefore }
        return out.sortedBy { it.atMs }
    }
}
