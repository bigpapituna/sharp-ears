package com.bigpapituna.sharpears.engine

/**
 * When the voice may talk. The phone's speaker is next to its microphone, so while the mic listens (and no
 * headphones are on) a spoken line would be heard as piano notes: the line waits here until the mic stops.
 * Held lines go stale: anything older than [maxAgeMs] is dropped when the mic stops, and at most [max] are kept.
 */
class SpeechQueue(private val maxAgeMs: Long = 30_000, private val max: Int = 4) {
    private val held = ArrayDeque<Pair<String, Long>>()

    val size get() = held.size

    /**
     * A new line at [now]. [canSpeak]: the mic is off or headphones are on. [interrupt] drops what was waiting.
     * [deferrable] false: a line that only makes sense right now (e.g. naming a wrong note) is dropped instead of held.
     * Returns the lines to speak now, in order (empty when the line was held or dropped).
     */
    fun offer(text: String, now: Long, canSpeak: Boolean, interrupt: Boolean = false, deferrable: Boolean = true): List<String> {
        if (interrupt) held.clear()
        if (canSpeak) return release(now) + text
        if (!deferrable) return emptyList()
        held.addLast(text to now)
        while (held.size > max) held.removeFirst()
        return emptyList()
    }

    /** The mic stopped: the lines still fresh, oldest first. */
    fun release(now: Long): List<String> {
        val out = held.filter { now - it.second <= maxAgeMs }.map { it.first }
        held.clear()
        return out
    }

    fun clear() = held.clear()
}
