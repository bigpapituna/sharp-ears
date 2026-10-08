package com.bigpapituna.sharpears.engine

/**
 * Song time to tutorial-video time (DESIGN 3.1): a line through the anchors. [first] is the video second where
 * the song's first note ([firstNoteMs]) sounds; [last], if set, where its last note ([lastNoteMs]) sounds.
 * One anchor: the video runs at the song's rate (1.0). Two anchors: rate = video seconds / song seconds between
 * them, so a video played a little faster or slower than the MIDI stays in step to the end.
 */
data class VideoSync(val firstNoteMs: Long, val lastNoteMs: Long, val first: Float?, val last: Float? = null) {
    private val twoPointRate: Double? get() =
        if (first == null || last == null || lastNoteMs <= firstNoteMs) null
        else ((last - first) / ((lastNoteMs - firstNoteMs) / 1000.0)).takeIf { it in 0.5..2.0 }

    /** Both anchors set and giving a believable rate (0.5x to 2x); otherwise the second one is ignored. */
    val twoPoints get() = twoPointRate != null

    val rate: Double get() = twoPointRate ?: 1.0

    fun videoSecFor(songMs: Double): Float? = first?.let { (it + (songMs - firstNoteMs) / 1000.0 * rate).toFloat().coerceAtLeast(0f) }
}
