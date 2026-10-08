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

    private fun waiting(practice: Practice = Practice.BOTH, playable: IntRange = 0..127) =
        Transport(song, playable).apply { mode = Mode.WAIT; this.practice = practice; play() }

    @Test fun waitModeHoldsAtEachGroupUntilPlayed() {
        val t = waiting()
        assertEquals(emptyList<NoteEvent>(), t.run(100.0))                      // C4 is the player's, not auto-played
        assertTrue(t.waiting)
        assertEquals(0.0, t.positionMs, 0.0)
        assertEquals(listOf(60), t.expected.map { it.midi })
        assertEquals(InputResult.GROUP_DONE, t.noteInput(60))
        t.run(400.0)
        assertEquals(400.0, t.positionMs, 1e-6)                                  // free until E4's group at 500
        t.run(400.0)
        assertEquals(500.0, t.positionMs, 0.0)                                   // held at E4
        assertTrue(t.waiting)
    }

    @Test fun wrongNoteChangesNothing() {
        val t = waiting()
        t.run(50.0)
        assertEquals(InputResult.WRONG, t.noteInput(62))
        assertEquals(listOf(60), t.expected.map { it.midi })
        t.run(500.0)
        assertEquals(0.0, t.positionMs, 0.0)
    }

    @Test fun chordNeedsEveryNote() {
        val t = waiting()
        t.run(10.0); t.noteInput(60); t.run(600.0); t.noteInput(64); t.run(600.0)
        assertEquals(1000.0, t.positionMs, 0.0)
        assertEquals(setOf(48, 67), t.expected.map { it.midi }.toSet())
        assertEquals(InputResult.CORRECT, t.noteInput(67))
        assertEquals(listOf(48), t.expected.map { it.midi })
        t.run(100.0)
        assertEquals(1000.0, t.positionMs, 0.0)                                  // still held: C3 missing
        assertEquals(InputResult.GROUP_DONE, t.noteInput(48))
        t.run(100.0)
        assertEquals(1100.0, t.positionMs, 1e-6)
    }

    @Test fun earlyHitWithin200msCounts() {
        val t = waiting()
        t.run(10.0); t.noteInput(60)                                             // held at 0 until C4 is played
        t.run(250.0)                                                             // 250 ms: E4 is 250 ms away
        assertEquals(InputResult.IGNORED, t.noteInput(64))
        t.run(100.0)                                                             // 350 ms: 150 ms away
        assertEquals(InputResult.GROUP_DONE, t.noteInput(64))
        t.run(300.0)
        assertFalse(t.waiting)
        assertEquals(650.0, t.positionMs, 1e-6)                                  // went straight through 500
    }

    @Test fun handFilterAutoPlaysTheOtherHand() {
        val t = waiting(Practice.LEFT)
        val events = t.run(1100.0)                                               // right-hand C4 and E4 play themselves
        assertTrue(events.containsAll(listOf(NoteEvent.On(60, 90), NoteEvent.On(64, 90))))
        assertEquals(1000.0, t.positionMs, 0.0)
        assertEquals(listOf(48), t.expected.map { it.midi })                     // only the left-hand C3 is needed
        assertEquals(InputResult.GROUP_DONE, t.noteInput(48))
        assertTrue(t.run(16.0).contains(NoteEvent.On(67, 90)))                   // G4 is accompaniment
    }

    @Test fun notesOffTheKeyboardAreSkipped() {
        val t = waiting(playable = 55..84)                                       // C3 (48) is not on this keyboard
        t.run(10.0); t.noteInput(60); t.run(600.0); t.noteInput(64); t.run(600.0)
        assertEquals(listOf(67), t.expected.map { it.midi })
        assertEquals(InputResult.GROUP_DONE, t.noteInput(67))
        assertTrue(t.run(16.0).contains(NoteEvent.On(48, 70)))                   // C3 plays itself
    }

    @Test fun seekResetsWhatWasPlayed() {
        val t = waiting()
        t.run(10.0); t.noteInput(60); t.run(600.0); t.run(600.0); t.noteInput(64)
        t.run(600.0); t.noteInput(67)                                            // half of the chord
        t.seek(900.0)
        assertFalse(t.waiting)
        t.run(200.0)
        assertEquals(setOf(48, 67), t.expected.map { it.midi }.toSet())        // G4 has to be played again
    }

    @Test fun loopWrapsBackToItsStart() {
        val t = Transport(song).apply { loop = 400.0..900.0; play() }
        t.run(300.0)
        t.run(400.0)                                                             // crosses 900 at some frame
        assertTrue(t.positionMs < 400.0 + 16.0 * 20)
        assertTrue(t.positionMs >= 400.0)
        t.loop = null
        t.seek(950.0)
        t.run(100.0)
        assertEquals(1050.0, t.positionMs, 1e-6)                                 // no loop: runs on
    }

    @Test fun loopEndSetAtThePlayheadWrapsNextFrame() {
        val t = Transport(song).apply { play() }
        t.run(800.0)
        t.loop = 300.0..t.positionMs                                             // press B right now
        t.run(16.0)
        assertEquals(300.0, t.positionMs, 0.0)
    }

    @Test fun micListensForTheNextGroup() {
        val t = waiting()
        assertEquals(setOf(60), t.listenFor)                                     // before the song even reaches it
        t.run(10.0); t.noteInput(60); t.run(600.0); t.noteInput(64); t.run(600.0)
        assertEquals(setOf(48, 67), t.listenFor)
        t.noteInput(67)
        assertEquals(setOf(48), t.listenFor)
        assertEquals(setOf(48, 67), t.listenGroup)                              // G4 still rings with the chord
        assertEquals(emptySet<Int>(), Transport(song).listenFor)                  // listen mode: mic idle
    }

    @Test fun micCheckIsAScaleForEachHand() {
        val s = micCheckExercise()
        assertEquals(30, s.notes.size)
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72, 71), s.notes.take(9).map { it.midi })
        assertTrue(s.notes.take(15).all { it.hand == Hand.RIGHT } && s.notes.drop(15).all { it.hand == Hand.LEFT })
        assertEquals(30, s.groups.size)
    }

    @Test fun chordSlackLetsABigChordPassWithOneNoteUnheard() {
        val big = listOf(48, 55, 64, 67, 72).map { Note(it, 0, 1000, if (it < 60) Hand.LEFT else Hand.RIGHT, 80) }
        val s = Song("c", "c", 120.0, 4 to 4, big, groupOnsets(big), emptyList(), 1000)
        val strict = Transport(s).apply { mode = Mode.WAIT; play() }
        strict.run(10.0); listOf(48, 55, 64, 67).forEach { strict.noteInput(it) }
        assertTrue(strict.waiting)                                               // touch/MIDI: every note
        val lenient = Transport(s).apply { mode = Mode.WAIT; chordSlack = 1; play() }
        lenient.run(10.0)
        val results = listOf(48, 55, 64, 67).map { lenient.noteInput(it) }
        assertEquals(InputResult.GROUP_DONE, results.last())
        assertFalse(lenient.waiting)
    }

    @Test fun listenModeIgnoresInput() {
        val t = Transport(song).apply { play() }
        assertEquals(InputResult.IGNORED, t.noteInput(60))
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

    // Lead-in and count-in (a fresh run starts before its first group so the notes are seen falling).

    @Test fun leadInFromTheTopStartsBeforeTheFirstGroup() {
        val t = Transport(song).apply { mode = Mode.WAIT; play(leadInMs = 1000.0) }
        assertEquals(-1000.0, t.positionMs, 0.0)
        assertTrue(t.inLeadIn)
        assertEquals(0.0, t.shownMs, 0.0)                                         // the timeline shows 0:00
        t.run(500.0)
        assertFalse(t.waiting)                                                     // falling, not held yet
        assertEquals(InputResult.IGNORED, t.noteInput(62))                        // too early to be wrong
        assertEquals(0, t.report().wrongKeys)
        t.run(600.0)
        assertTrue(t.waiting)                                                      // held at the first group
        assertEquals(0.0, t.positionMs, 0.0)
        assertFalse(t.inLeadIn)
    }

    @Test fun leadInAfterASeekStartsBeforeTheNextGroupAndSkipsEarlierNotes() {
        val t = Transport(song)
        t.seek(700.0)                                                              // next group: the chord at 1000
        assertEquals(1000.0, t.anchorMs, 0.0)
        t.play(leadInMs = 500.0)
        assertEquals(500.0, t.positionMs, 0.0)
        assertEquals(700.0, t.shownMs, 0.0)
        assertEquals(emptyList<NoteEvent>(), t.run(400.0))                         // E4 (500..1000) is behind the seek point
        assertEquals(setOf(NoteEvent.On(48, 70), NoteEvent.On(67, 90)), t.run(200.0).toSet())
    }

    @Test fun resumingAfterAPauseHasNoLeadIn() {
        val t = Transport(song).apply { play() }
        t.run(600.0); t.pause()
        t.play(leadInMs = 1000.0)
        assertEquals(600.0, t.positionMs, 1e-6)
    }

    @Test fun countInIsNotScoredInPerform() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play(leadInMs = 4000.0) }
        t.run(3000.0)
        assertTrue(t.inLeadIn)
        assertEquals(emptySet<Int>(), t.listenFor)
        assertEquals(InputResult.IGNORED, t.noteInput(62))                         // hands finding their place
        assertEquals(0, t.report().wrongKeys)
        assertEquals(0, t.score!!.recap().total)                                   // nothing graded in the count-in
        t.run(1000.0)
        assertEquals(InputResult.CORRECT, t.noteInput(60))                         // on time at 0
        t.run(2500.0)
        val r = t.score!!.recap()
        assertEquals(3, r.total)                                                   // only the song's three groups
        assertEquals(1, r.good)
    }

    @Test fun previewShowsTheFirstNotesAndPlayStartsFromIt() {
        val late = notes.map { it.copy(startMs = it.startMs + 3000) }
        val s = Song("l", "l", 120.0, 4 to 4, late, groupOnsets(late), emptyList(), 5000)
        val t = Transport(s).apply { mode = Mode.WAIT }
        t.preview(LOOKAHEAD_MS - 300)
        assertEquals(3000 - LOOKAHEAD_MS + 300, t.positionMs, 0.0)               // first group just under the lane's top
        assertTrue(LaneGeometry(1000f).visible(s, t.positionMs, 1000).isNotEmpty())
        t.play(leadInMs = 1000.0)                                                  // already further back: no jump
        assertEquals(3000 - LOOKAHEAD_MS + 300, t.positionMs, 0.0)
        t.pause(); t.preview(100.0)                                                // no longer fresh: preview does nothing
        assertEquals(3000 - LOOKAHEAD_MS + 300, t.positionMs, 0.0)
    }
}
