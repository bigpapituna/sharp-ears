package com.bigpapituna.sharpears.engine

sealed interface NoteEvent {
    val midi: Int
    data class On(override val midi: Int, val velocity: Int) : NoteEvent
    data class Off(override val midi: Int) : NoteEvent
}

fun isBlack(midi: Int) = midi % 12 in BLACK_PITCH_CLASSES
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)
private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
fun noteName(midi: Int) = NAMES[midi % 12] + (midi / 12 - 1)
/** "Bb4" with [flats], "A#4" without (as [noteName]). */
fun noteName(midi: Int, flats: Boolean) = (if (flats) FLAT_NAMES else NAMES)[midi % 12] + (midi / 12 - 1)

data class KeyRect(val midi: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val black get() = isBlack(midi)
    val centerX get() = (left + right) / 2
    /** A point low on the key, where a finger lands; for black keys it is still inside the black key. */
    val touchY get() = top + (bottom - top) * 0.75f
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/** Piano key geometry for the white-key range [lowMidi, highMidi]. Pure math, shared by drawing, touch and tests. */
class KeyboardLayout(val lowMidi: Int, val highMidi: Int, val width: Float, val height: Float) {
    init { require(!isBlack(lowMidi) && !isBlack(highMidi) && lowMidi < highMidi) { "range must start and end on white keys" } }

    private val whites = (lowMidi..highMidi).filter { !isBlack(it) }
    val whiteWidth = width / whites.size
    private val blackWidth = whiteWidth * 0.6f
    private val blackHeight = height * 0.6f

    val whiteKeys: List<KeyRect> = whites.mapIndexed { i, m -> KeyRect(m, i * whiteWidth, 0f, (i + 1) * whiteWidth, height) }
    val blackKeys: List<KeyRect> = (lowMidi..highMidi).filter(::isBlack).map { m ->
        val boundary = (whites.indexOf(m - 1) + 1) * whiteWidth   // between the white key below and the one above
        KeyRect(m, boundary - blackWidth / 2, 0f, boundary + blackWidth / 2, blackHeight)
    }
    private val byMidi = (whiteKeys + blackKeys).associateBy { it.midi }

    fun rect(midi: Int): KeyRect = byMidi.getValue(midi)

    /** Black keys sit on top of white keys, so they win the hit test. */
    fun keyAt(x: Float, y: Float): Int? =
        (blackKeys.firstOrNull { it.contains(x, y) } ?: whiteKeys.firstOrNull { it.contains(x, y) })?.midi
}

/**
 * Picks the recorded sample for a key and the playback rate that shifts it to the right pitch.
 * Samples are every 4 semitones, so the shift is at most 2 semitones (rate 0.89–1.12).
 */
fun sampleFor(midi: Int, available: Collection<Int>): Pair<Int, Float> {
    val sample = available.minBy { kotlin.math.abs(it - midi) }
    return sample to Math.pow(2.0, (midi - sample) / 12.0).toFloat()
}
