package com.bigpapituna.sharpears.engine

enum class Mode { LISTEN, WAIT }
enum class Practice { BOTH, LEFT, RIGHT }
enum class InputResult { CORRECT, GROUP_DONE, WRONG, IGNORED }

/** A correct note this far (song ms) before its group still counts, so playing at tempo does not stutter. */
const val EARLY_HIT_MS = 200.0

/**
 * Plays a song through time. Pure state machine: the UI ticks it once per frame with the real frame
 * delta and forwards the returned events to the sampler.
 *
 * Listen mode plays every note. Wait mode stops at each onset group until the player has played every
 * *required* note in it: notes of the practiced hand that are on the visible keyboard ([playable]).
 * Other notes are accompaniment and play by themselves.
 */
class Transport(val song: Song, val playable: IntRange = 0..127) {
    var positionMs = 0.0
        private set
    var playing = false
        private set
    var speed = 1.0
        set(value) { field = value.coerceIn(0.25, 1.0) }
    var mode = Mode.LISTEN
        set(value) { field = value; resetGroups() }
    var practice = Practice.BOTH
        set(value) { field = value; resetGroups() }
    /** A–B loop in song ms. Reaching the end jumps back to the start. */
    var loop: ClosedFloatingPointRange<Double>? = null

    /** True while wait mode holds the song at a group the player has not finished. */
    var waiting = false
        private set

    /** Notes currently sounding, at most one per key. The UI lights these keys. */
    val sounding: List<Note> get() = _sounding
    private val _sounding = mutableListOf<Note>()
    private var nextIndex = 0                     // first note that has not started yet
    private var groupIndex = 0                    // next group that needs the player's input (wait mode)
    private val satisfied = mutableSetOf<Int>()   // keys already played for that group

    fun required(n: Note) = mode == Mode.WAIT && n.midi in playable && when (practice) {
        Practice.BOTH -> true
        Practice.LEFT -> n.hand == Hand.LEFT
        Practice.RIGHT -> n.hand == Hand.RIGHT
    }

    /** Required notes of the group being waited for that are still to be played. */
    val expected: List<Note>
        get() = if (!waiting) emptyList()
        else song.groups.getOrNull(groupIndex)?.notes?.filter { required(it) && it.midi !in satisfied }.orEmpty()

    /** What the microphone should listen for: the next group's required notes not yet played (wait mode only). */
    val listenFor: Set<Int>
        get() = if (mode != Mode.WAIT) emptySet()
        else song.groups.getOrNull(groupIndex)?.notes?.filter { required(it) && it.midi !in satisfied }?.map { it.midi }?.toSet().orEmpty()

    /** All required notes of that group, including ones already played; they ring together. */
    val listenGroup: Set<Int>
        get() = if (mode != Mode.WAIT) emptySet()
        else song.groups.getOrNull(groupIndex)?.notes?.filter(::required)?.map { it.midi }?.toSet().orEmpty()

    fun play() {
        if (positionMs >= song.durationMs) seek(0.0)
        playing = true
    }

    fun pause(): List<NoteEvent> { playing = false; return silence() }

    fun seek(ms: Double): List<NoteEvent> {
        val off = silence()
        positionMs = ms.coerceIn(0.0, song.durationMs.toDouble())
        nextIndex = firstStartAtOrAfter(song.notes, positionMs)
        resetGroups()
        return off
    }

    fun tick(dtMs: Double): List<NoteEvent> {
        if (!playing) return emptyList()
        var end = (positionMs + dtMs * speed).coerceAtMost(song.durationMs.toDouble())
        // "<=" so pressing B at the playhead wraps on the next frame; a seek past B plays on.
        loop?.let { if (positionMs <= it.endInclusive && end >= it.endInclusive) return seek(it.start) }
        if (mode == Mode.WAIT) {
            val g = song.groups.getOrNull(groupIndex)
            if (g != null && g.startMs <= end) { end = g.startMs.toDouble(); waiting = true }
        }
        val events = mutableListOf<NoteEvent>()
        // Release first, so a note that ends exactly where the next one on the same key starts re-triggers cleanly.
        _sounding.removeAll { n -> (n.startMs + n.durationMs <= end).also { if (it) events += NoteEvent.Off(n.midi) } }
        while (nextIndex < song.notes.size && song.notes[nextIndex].startMs < end) {
            val n = song.notes[nextIndex++]
            if (required(n)) continue                 // the player plays these
            _sounding.removeAll { it.midi == n.midi } // re-strike: the sampler restarts the key
            _sounding += n
            events += NoteEvent.On(n.midi, n.velocity)
        }
        positionMs = end
        if (end >= song.durationMs) { playing = false; events += silence() }
        return events
    }

    /** A key the player struck. Only wait mode cares; listen mode ignores input. */
    fun noteInput(midi: Int): InputResult {
        if (mode != Mode.WAIT) return InputResult.IGNORED
        val g = song.groups.getOrNull(groupIndex) ?: return InputResult.IGNORED
        if (!waiting && g.startMs - positionMs > EARLY_HIT_MS) return InputResult.IGNORED
        val need = g.notes.filter(::required).map { it.midi }.toSet()
        if (midi !in need) return InputResult.WRONG
        satisfied += midi
        if (!satisfied.containsAll(need)) return InputResult.CORRECT
        satisfied.clear()
        waiting = false
        groupIndex = nextRequiredGroup(groupIndex + 1)
        return InputResult.GROUP_DONE
    }

    private fun resetGroups() {
        satisfied.clear()
        waiting = false
        groupIndex = nextRequiredGroup(firstGroupAtOrAfter(song.groups, positionMs))
    }

    private fun nextRequiredGroup(from: Int): Int {
        var i = from
        while (i < song.groups.size && song.groups[i].notes.none(::required)) i++
        return i
    }

    private fun silence(): List<NoteEvent> = _sounding.map { NoteEvent.Off(it.midi) }.also { _sounding.clear() }
}

fun firstGroupAtOrAfter(groups: List<OnsetGroup>, ms: Double): Int {
    var lo = 0
    var hi = groups.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (groups[mid].startMs < ms) lo = mid + 1 else hi = mid
    }
    return lo
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
