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

// ---- Phrases shared by the course and Pup's Playground (2.1) ---------------------------------------------------------
// Here, not in Course.kt, so engine/playground can reuse them without depending on the course's own initialisation.

/** Mary Had a Little Lamb, the opening four bars (repertoire-1 and the Playground). The kid bite is bar 2: "Mary had a little lamb". */
internal val MARY_X = Exercise(right = "E4:1 D4:1 C4:1 D4:1 E4:1 E4:1 E4:2 D4:1 D4:1 D4:2 E4:1 G4:1 G4:2",
    left = "C3:4 C3:4 G2:4 C3:4", bpm = 90.0, phraseEnd = 8.0)

/** Hot Cross Buns, the whole four bars. The kid bite is bar 2: "hot cross buns, hot cross buns". */
internal val BUNS_X = Exercise(right = "E4:1 D4:1 C4:2 E4:1 D4:1 C4:2 C4:0.5 C4:0.5 C4:0.5 C4:0.5 D4:0.5 D4:0.5 D4:0.5 D4:0.5 E4:1 D4:1 C4:2",
    left = "C3:4 C3:4 C3:2 G2:2 C3:4", bpm = 80.0, phraseEnd = 8.0)

/** Every C from low to high, the low ones in the left hand ("Find C anywhere" and the Playground's "Where C lives"). */
internal val FIND_C = Exercise(right = "r:4 C4:2 C5:2 C6:2", left = "C2:2 C3:2 r:6", bpm = 60.0)

/** Ode to Joy's first line in the C position ("Your first melody"). The kid bite is bar 2, before the hand turns back. */
internal val ODE_LINE = Exercise(right = "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:0.5 D4:2", bpm = 90.0,
    practice = Practice.RIGHT, phraseEnd = 8.0)
