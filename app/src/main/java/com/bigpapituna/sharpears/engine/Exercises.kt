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

/** Twinkle Twinkle's first line (the A section), both hands ("Hands together", "Your first whole song", the pond). The bite is bar 2. */
internal val TWINKLE = Exercise(right = "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2",
    left = "C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2", bpm = 80.0, phraseEnd = 8.0)

// Opening phrases (four bars) of the repertoire songs, written in the same notation as scripts/gen_songs.py; Repertoire 1
// and 2 teach them, the Playground's pond and concert hall (2.2) play their first phrase and perform them with the band.
internal val ROW_X = Exercise(right = "C4:1.5 C4:1.5 C4:1 D4:0.5 E4:1.5 E4:1 D4:0.5 E4:1 F4:0.5 G4:3",
    left = "C3:3 C3:3 C3:3 C3:3", bpm = 84.0, beats = 3)
internal val LONDON_X = Exercise(right = "G4:0.75 A4:0.25 G4:0.5 F4:0.5 E4:0.5 F4:0.5 G4:1 D4:0.5 E4:0.5 F4:1 E4:0.5 F4:0.5 G4:1",
    left = "C3:2 C3:2 G2:2 C3:2", bpm = 96.0, beats = 2)
internal val YANKEE_X = Exercise(right = "C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 D4:1 G3:1 C4:1 C4:1 D4:1 E4:1 C4:1 B3:3",
    left = "C3:4 C3:2 G2:2 C3:4 C3:2 G2:2", bpm = 100.0)
internal val MACDONALD_X = Exercise(right = "C4:1 C4:1 C4:1 G3:1 A3:1 A3:1 G3:2 E4:1 E4:1 D4:1 D4:1 C4:4",
    left = "C3:4 F3:2 C3:2 C3:2 G2:2 C3:4", bpm = 96.0)
internal val LIGHTLY_X = Exercise(right = "G4:1 E4:1 E4:2 F4:1 D4:1 D4:2 C4:1 D4:1 E4:1 F4:1 G4:1 G4:1 G4:2",
    left = "C3:2 G3:2 G2:2 D3:2 C3:2 G3:2 G2:2 D3:2", bpm = 92.0)

// One bar patterns on middle C from the Rhythm unit, which the Playground's rhythm words reuse (2.2).
/** Steady quarter notes: two bars of them. */
internal const val QUARTERS_PATTERN = "C4:1 C4:1 C4:1 C4:1 C4:1 C4:1 C4:1 C4:1"
/** Half and whole notes, then quarters. */
internal const val HALVES_PATTERN = "C4:2 C4:2 C4:4 C4:1 C4:1 C4:2 C4:4"
/** A quarter, two eighths, a quarter, two eighths; then a run of eighths. */
internal const val EIGHTHS_PATTERN = "C4:1 C4:0.5 C4:0.5 C4:1 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:2"

/** The left hand's five fingers up and back down from the C below middle C ("The left hand mirrors it", the pond's left paw). */
internal val LEFT_HAND_WALK = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)

/** Each finger's key in the five-finger position on [notes] (letters, "Bb" for a flat), from the root in [octave] upward. */
internal fun fiveFingerKeys(notes: List<String>, octave: Int): List<String> {
    val flats = notes.any { it.endsWith("b") }
    var prev = noteNumber("${notes[0]}$octave") - 1
    return notes.map { n -> var m = noteNumber("${n}0"); while (m <= prev) m += 12; prev = m; noteName(m, flats) }
}

/**
 * Five fingers up and down twice, both hands an octave apart, in the five-finger position on [notes] (the Technique
 * unit's "Five fingers in C" and its kin, at 100; the concert hall plays C's slowly, one way up and down).
 */
internal fun fiveFingerExercise(notes: List<String>): Exercise {
    val pattern = listOf(0, 1, 2, 3, 4, 3, 2, 1, 0, 1, 2, 3, 4, 3, 2, 1)
    fun line(octave: Int) = fiveFingerKeys(notes, octave).let { k -> pattern.joinToString(" ") { "${k[it]}:1" } + " ${k[0]}:4" }
    return Exercise(right = line(4), left = line(3), bpm = 100.0, flats = notes.any { it.endsWith("b") })
}
