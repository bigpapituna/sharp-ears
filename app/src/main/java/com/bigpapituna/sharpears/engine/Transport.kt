package com.bigpapituna.sharpears.engine

/**
 * Plays a song through time. Pure state machine: the UI ticks it once per frame with the real frame
 * delta and forwards the returned events to the sampler. Listen mode only; wait mode arrives in P4.
 */
class Transport(val song: Song) {
    var positionMs = 0.0
        private set
    var playing = false
        private set
    var speed = 1.0
        set(value) { field = value.coerceIn(0.25, 1.0) }

    /** Notes currently sounding, at most one per key. The UI lights these keys. */
    val sounding: List<Note> get() = _sounding
    private val _sounding = mutableListOf<Note>()
    private var nextIndex = 0                     // first note that has not started yet

    fun play() {
        if (positionMs >= song.durationMs) seek(0.0)
        playing = true
    }

    fun pause(): List<NoteEvent> { playing = false; return silence() }

    fun seek(ms: Double): List<NoteEvent> {
        val off = silence()
        positionMs = ms.coerceIn(0.0, song.durationMs.toDouble())
        nextIndex = firstStartAtOrAfter(song.notes, positionMs)
        return off
    }

    fun tick(dtMs: Double): List<NoteEvent> {
        if (!playing) return emptyList()
        val end = (positionMs + dtMs * speed).coerceAtMost(song.durationMs.toDouble())
        val events = mutableListOf<NoteEvent>()
        // Release first, so a note that ends exactly where the next one on the same key starts re-triggers cleanly.
        _sounding.removeAll { n -> (n.startMs + n.durationMs <= end).also { if (it) events += NoteEvent.Off(n.midi) } }
        while (nextIndex < song.notes.size && song.notes[nextIndex].startMs < end) {
            val n = song.notes[nextIndex++]
            _sounding.removeAll { it.midi == n.midi }   // re-strike: the sampler restarts the key
            _sounding += n
            events += NoteEvent.On(n.midi, n.velocity)
        }
        positionMs = end
        if (end >= song.durationMs) { playing = false; events += silence() }
        return events
    }

    private fun silence(): List<NoteEvent> = _sounding.map { NoteEvent.Off(it.midi) }.also { _sounding.clear() }
}

/** Index of the first note starting at or after [ms]. Notes are sorted by start time. */
fun firstStartAtOrAfter(notes: List<Note>, ms: Double): Int {
    var lo = 0
    var hi = notes.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (notes[mid].startMs < ms) lo = mid + 1 else hi = mid
    }
    return lo
}

const val LOOKAHEAD_MS = 2500.0
const val LOOKBEHIND_MS = 500.0

/** Vertical geometry of the falling-notes lane. Song time maps linearly to y; the hit line is the lane's bottom edge. */
class LaneGeometry(val laneHeight: Float) {
    val hitLineY = laneHeight
    val pxPerMs = laneHeight / LOOKAHEAD_MS.toFloat()
    /** Bottom edge of a note block (its start); the block extends upward by its duration. */
    fun bottomY(note: Note, positionMs: Double) = hitLineY - ((note.startMs - positionMs) * pxPerMs).toFloat()
    fun topY(note: Note, positionMs: Double) = bottomY(note, positionMs) - maxOf(note.durationMs * pxPerMs, 8f)

    /** Notes that can be on screen: started at most [LOOKBEHIND_MS] (plus their length) ago, starting within the lookahead. */
    fun visible(song: Song, positionMs: Double, longestNoteMs: Long): List<Note> {
        val from = firstStartAtOrAfter(song.notes, positionMs - LOOKBEHIND_MS - longestNoteMs)
        val to = firstStartAtOrAfter(song.notes, positionMs + LOOKAHEAD_MS)
        return song.notes.subList(from, to).filter { it.startMs + it.durationMs >= positionMs - LOOKBEHIND_MS }
    }
}

/**
 * Keyboard range for a song: lowest to highest note, widened to whole octaves (C to C), at least 2 and
 * at most 5 octaves. A wider song keeps the 5-octave window that covers the most notes.
 */
fun fitRange(notes: List<Note>): IntRange {
    if (notes.isEmpty()) return 48..84
    val low = notes.minOf { it.midi }.let { it - it % 12 }
    val high = notes.maxOf { it.midi }.let { if (it % 12 == 0) it else it + 12 - it % 12 }
    return when {
        high - low < 24 -> low..(low + 24)
        high - low <= 60 -> low..high
        // ponytail: notes outside the window are not drawn; a pan/zoom keyboard is the upgrade if a song needs it
        else -> (low..(high - 60) step 12).maxBy { lo -> notes.count { it.midi in lo..(lo + 60) } }.let { it..(it + 60) }
    }
}
