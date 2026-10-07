package com.bigpapituna.sharpears.engine

const val MIC_CHECK_ID = "mic-check"

/**
 * "Mic check": a slow C major scale, right hand C4 up to C5 and back, then left hand C3 up to C4 and back,
 * one note per second. Used to measure the microphone against MIDI truth.
 */
fun micCheckExercise(): Song {
    val up = listOf(0, 2, 4, 5, 7, 9, 11, 12)
    val scale = up + up.reversed().drop(1)
    val notes = scale.mapIndexed { i, s -> Note(60 + s, i * 1000L, 900, Hand.RIGHT, 80) } +
        scale.mapIndexed { i, s -> Note(48 + s, (scale.size + 1 + i) * 1000L, 900, Hand.LEFT, 80) }
    return Song(MIC_CHECK_ID, "Mic check: C major scale", 60.0, 4 to 4, notes, groupOnsets(notes),
        listOf(Section("Right hand", 0, scale.size * 1000L), Section("Left hand", (scale.size + 1) * 1000L, notes.last().let { it.startMs + it.durationMs })),
        notes.last().let { it.startMs + it.durationMs })
}
