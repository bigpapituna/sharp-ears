package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransportTest {
    // C4 at 0 (500 ms), E4 at 500 (500 ms), G4 at 1000 (1000 ms), C4 again at 1000 (left hand). Song ends at 2000.
    private val notes = listOf(
        Note(60, 0, 500, Hand.RIGHT, 90),
        Note(64, 500, 500, Hand.RIGHT, 90),
        Note(48, 1000, 1000, Hand.LEFT, 70),
        Note(67, 1000, 1000, Hand.RIGHT, 90),
    )
    private val song = Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 2000)

    private fun Transport.run(ms: Double, frame: Double = 16.0): List<NoteEvent> {
        val out = mutableListOf<NoteEvent>()
        var left = ms
        while (left > 1e-9) { val dt = minOf(frame, left); out += tick(dt); left -= dt }
        return out
    }

    @Test fun pausedTransportDoesNotMove() {
        val t = Transport(song)
        assertEquals(emptyList<NoteEvent>(), t.tick(100.0))
        assertEquals(0.0, t.positionMs, 0.0)
    }

    @Test fun emitsOnAndOffAtTheRightTimes() {
        val t = Transport(song).apply { play() }
        assertEquals(listOf(NoteEvent.On(60, 90)), t.run(16.0))
        assertEquals(emptyList<NoteEvent>(), t.run(480.0))                       // up to 496 ms: C4 still sounding
        assertEquals(listOf(NoteEvent.Off(60), NoteEvent.On(64, 90)), t.run(8.0)) // crossing 500 ms
        assertEquals(listOf(64), t.sounding.map { it.midi })
        val chord = t.run(500.0)
        assertTrue(chord.containsAll(listOf(NoteEvent.Off(64), NoteEvent.On(48, 70), NoteEvent.On(67, 90))))
        assertEquals(setOf(48, 67), t.sounding.map { it.midi }.toSet())
    }

    @Test fun speedScalesSongTime() {
        val t = Transport(song).apply { speed = 0.5; play() }
        t.run(1000.0)
        assertEquals(500.0, t.positionMs, 1e-6)
        t.speed = 0.1                                                             // clamped to 0.25
        assertEquals(0.25, t.speed, 0.0)
    }

    @Test fun seekSilencesAndSkipsAhead() {
        val t = Transport(song).apply { play() }
        t.run(600.0)
        assertEquals(listOf(NoteEvent.Off(64)), t.seek(990.0))
        assertTrue(t.sounding.isEmpty())
        assertEquals(setOf(NoteEvent.On(48, 70), NoteEvent.On(67, 90)), t.run(16.0).toSet())
        t.seek(250.0)
        assertEquals(listOf(NoteEvent.On(64, 90)), t.run(260.0))                // C4 at 0 is not replayed
    }

    @Test fun stopsAtTheEndAndRestartsFromZero() {
        val t = Transport(song).apply { play() }
        val events = t.run(5000.0)
        assertFalse(t.playing)
        assertEquals(2000.0, t.positionMs, 0.0)
        assertTrue(t.sounding.isEmpty())
        assertEquals(events.count { it is NoteEvent.On }, events.count { it is NoteEvent.Off })
        t.play()
        assertEquals(0.0, t.positionMs, 0.0)
    }

    @Test fun pauseReleasesSoundingNotes() {
        val t = Transport(song).apply { play() }
        t.run(100.0)
        assertEquals(listOf(NoteEvent.Off(60)), t.pause())
        assertEquals(emptyList<NoteEvent>(), t.run(100.0))
    }

    @Test fun laneGeometry() {
        val g = LaneGeometry(1000f)                                               // 0.4 px per ms
        val g4 = notes[3]
        assertEquals(1000f - 400f, g.bottomY(g4, 0.0), 0.01f)                    // starts 1000 ms ahead
        assertEquals(1000f, g.bottomY(g4, 1000.0), 0.01f)                        // on the hit line
        assertEquals(1000f - 400f, g.topY(g4, 1000.0), 0.01f)                    // 1000 ms long = 400 px tall
        assertEquals(notes, g.visible(song, 0.0, 1000))
        assertEquals(listOf(48, 67), g.visible(song, 1600.0, 1000).map { it.midi }) // C4 and E4 ended > 500 ms ago
    }

    @Test fun rangeFit() {
        fun n(vararg m: Int) = m.map { Note(it, 0, 1, Hand.RIGHT, 1) }
        assertEquals(60..84, fitRange(n(64, 67)))                                // widened to 2 octaves
        assertEquals(48..84, fitRange(n(50, 71, 83)))
        assertEquals(36..96, fitRange(n(36, 95)))                                // exactly 5 octaves
        assertEquals(48..108, fitRange(n(24, 50, 60, 70, 80, 90, 95, 107)))      // 7 octaves: window with the most notes
        val elise = SmfParser.parse(File("src/main/assets/songs/fur-elise.mid").readBytes(), "e", "e")
        val r = fitRange(elise.notes)
        println("fur-elise range ${noteName(elise.notes.minOf { it.midi })}..${noteName(elise.notes.maxOf { it.midi })} " +
            "-> ${noteName(r.first)}..${noteName(r.last)}, outside=${elise.notes.count { it.midi !in r }}")
    }
}
