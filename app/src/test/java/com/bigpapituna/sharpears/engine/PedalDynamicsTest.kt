package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PedalDynamicsTest {
    private val chords = listOf(0L..4000L, 4000L..8000L, 8000L..12000L, 12000L..16000L)

    @Test fun legatoPedallingChangesAtEachChord() {
        val g = PedalGrader(chords)
        g.pedal(true, 120.0)                                  // down just after the first chord
        listOf(4000.0, 8000.0, 12000.0).forEach { t -> g.pedal(false, t + 10); g.pedal(true, t + 180) }
        assertEquals(100, g.score())
    }

    @Test fun holdingThePedalThroughAChangeOrPressingLateFails() {
        val g = PedalGrader(chords)
        g.pedal(true, 50.0)
        g.pedal(false, 4010.0); g.pedal(true, 4150.0)        // chord 2: fine
        // chord 3: the pedal stays down from chord 2 (blurred)
        g.pedal(false, 12_000.0); g.pedal(true, 12_600.0)    // chord 4: pressed 600 ms late
        assertFalse(g.chordOk(2)); assertFalse(g.chordOk(3))
        assertEquals(50, g.score())
        assertTrue(g.downAt(5000.0))
        assertFalse(g.downAt(12_300.0))
    }

    @Test fun chordSpansComeFromTheRightHandGroups() {
        val ex = Exercise(right = "C4+E4+G4:4 C4+E4+A4:4", bpm = 60.0)
        val spans = PedalGrader.chordsOf(ex.toSong("p", "p"))
        assertEquals(2, spans.size)
        assertEquals(0L, spans[0].first); assertEquals(4000L, spans[1].first)
    }

    @Test fun softThenLoudIsGradedPerNote() {
        val d = DynamicsGrader(notes = 10)
        assertEquals(true, d.nextIsSoft())
        listOf(40, 45, 30, 62, 48).forEach { d.note(it) }      // one too loud
        assertEquals(false, d.nextIsSoft())
        assertTrue(d.note(100)); assertFalse(d.note(85))       // one not loud enough
        listOf(95, 110, 127).forEach { d.note(it) }
        assertNull(d.nextIsSoft())
        assertEquals(80, d.score())
        assertFalse(d.note(100))                               // extra notes are not counted
        assertEquals(80, d.score())
    }

    @Test fun theMidiParserReportsTheSustainPedal() {
        val cc = mutableListOf<Pair<Int, Int>>()
        val p = MidiParser { c, v -> cc += c to v }
        val notes = p.feed(byteArrayOf(0xB0.toByte(), 64, 127, 0x90.toByte(), 60, 70, 0xB0.toByte(), 64, 0, 1, 20, 0x80.toByte(), 60, 0))
        assertEquals(listOf(64 to 127, 64 to 0, 1 to 20), cc)   // "1 20" rides on running status: the mod wheel
        assertEquals(listOf(NoteEvent.On(60, 70), NoteEvent.Off(60)), notes)
    }
}
