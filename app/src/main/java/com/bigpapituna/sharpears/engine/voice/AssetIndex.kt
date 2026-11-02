package com.bigpapituna.sharpears.engine.voice

import java.util.concurrent.ConcurrentHashMap

/**
 * The files of one asset folder, for "is this line recorded?" (QA 2026-11-02 B-02). The folder's listing is read once, but a
 * listing can come back short: on the afterdark build `assets.list("voice")` held 596 of 3,975 names in about 1 launch in 25
 * (while the coach listed its own folders on another thread), and every line missing from it went to the phone's voice for the
 * rest of the process. So a name the listing lacks is asked of the file itself ([exists], an open and close) before it counts
 * as missing, and the answer is kept: a short listing costs one open per line, never a line in the wrong voice.
 * [onRescued] hears each name the listing missed but the file had (logged, to see how often it happens).
 */
class AssetIndex(list: () -> Collection<String>, private val exists: (String) -> Boolean,
                 private val onRescued: (String) -> Unit = {}) : AbstractSet<String>() {
    private val listed: Set<String> by lazy { runCatching { list().toSet() }.getOrDefault(emptySet()) }
    private val checked = ConcurrentHashMap<String, Boolean>()

    /** Reads the listing now (a background thread calls this so the first line does not wait for it). */
    fun prime() = listed.size

    override fun contains(element: String): Boolean = element in listed ||
        checked.getOrPut(element) { runCatching { exists(element) }.getOrDefault(false).also { if (it) onRescued(element) } }

    override val size get() = listed.size
    override fun iterator() = listed.iterator()
}
