package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringTest {
    private fun line(hand: Hand, vararg midi: Int): Song {
        val notes = midi.mapIndexed { i, m -> Note(m, i * 400L, 350, hand, 80) }
        return Song("f", "f", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), midi.size * 400L)
    }
    private fun fingers(s: Song) = Fingering.of(s).let { f -> s.notes.map { f[it] } }

    @Test fun cMajorScaleRightHandUsesThumbUnder() =
        assertEquals(listOf(1, 2, 3, 1, 2, 3, 4, 5), fingers(line(Hand.RIGHT, 60, 62, 64, 65, 67, 69, 71, 72)))

    @Test fun cMajorScaleLeftHandIsTheMirror() =
        assertEquals(listOf(5, 4, 3, 2, 1, 3, 2, 1), fingers(line(Hand.LEFT, 48, 50, 52, 53, 55, 57, 59, 60)))

    @Test fun fivePositionMelodyStaysInPlace() =
        // Ode to Joy opening: E E F G G F E D C, all inside one hand position.
        assertEquals(listOf(3, 3, 4, 5, 5, 4, 3, 2, 1), fingers(line(Hand.RIGHT, 64, 64, 65, 67, 67, 65, 64, 62, 60)))

    @Test fun triadsTakeTheNaturalShape() {
        val notes = listOf(60, 64, 67).map { Note(it, 0, 500, Hand.RIGHT, 80) } + listOf(48, 52, 55).map { Note(it, 0, 500, Hand.LEFT, 80) }
        val s = Song("c", "c", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 500)
        val f = Fingering.of(s)
        assertEquals(listOf(1, 3, 5), listOf(60, 64, 67).map { m -> f[notes.first { it.midi == m }] })
        assertEquals(listOf(5, 3, 1), listOf(48, 52, 55).map { m -> f[notes.first { it.midi == m }] })
    }

    @Test fun everyNoteOfARealSongGetsAFingerAndNoSameFingerJumps() {
        val notes = (0 until 64).map { i -> Note(60 + listOf(0, 2, 4, 5, 7, 9, 7, 5, 4, 2, 0, 4, 7, 12, 7, 4)[i % 16], i * 300L, 250, Hand.RIGHT, 80) }
        val s = Song("r", "r", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 64 * 300L)
        val f = fingers(s)
        assertTrue(f.all { it in 1..5 })
        val sameFingerNewKey = (1 until notes.size).count { f[it] == f[it - 1] && notes[it].midi != notes[it - 1].midi }
        assertEquals(0, sameFingerNewKey)
    }
}
