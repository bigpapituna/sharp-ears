package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamTest {
    private fun Jam.play(vararg notes: Pair<Int, Long>) = notes.flatMap { (m, t) -> strike(m, t) }

    @Test fun boomwhackerColoursForTheWhiteKeysAndHalfwayForTheBlack() {
        val names = listOf(60, 62, 64, 65, 67, 69, 71).map { NoteColours.name(it) }
        assertEquals(listOf("red", "orange", "yellow", "green", "teal", "blue", "purple"), names)
        assertEquals(7, NoteColours.WHITE.toSet().size)
        assertEquals(NoteColours.argb(60), NoteColours.argb(72))                 // every C is red, in any octave
        assertEquals(NoteColours.argb(36), NoteColours.argb(84))
        assertEquals(NoteColours.mix(NoteColours.argb(60), NoteColours.argb(62)), NoteColours.argb(61))
        assertEquals("red orange", NoteColours.name(61))
        assertEquals(0xFF, NoteColours.argb(66) ushr 24)                         // opaque
    }

    @Test fun everyNoteBurstsInItsColourAndBouncesThePupByPitch() {
        val jam = Jam()
        val fx = jam.strike(64, 0)
        assertEquals(Jam.Effect.Burst(64, NoteColours.argb(64)), fx[0])
        val heights = (60..72).map { jam.bounceHeight(it) }
        assertTrue(heights.zipWithNext().all { (a, b) -> b > a })
        assertEquals(Jam.MIN_BOUNCE, heights.first(), 1e-6f)
        assertEquals(1f, heights.last(), 1e-6f)
        assertEquals(Jam.MIN_BOUNCE, jam.bounceHeight(36), 1e-6f)                  // low C outside the keys: clamped
        assertEquals(1f, jam.bounceHeight(84), 1e-6f)
        assertTrue(fx[1] is Jam.Effect.Bounce)
    }

    @Test fun threeNotesWithin150msThrowConfettiOnce() {
        val jam = Jam()
        val fx = jam.play(60 to 1000L, 64 to 1040L, 67 to 1120L, 72 to 1140L)
        val chords = fx.filterIsInstance<Jam.Effect.Chord>()
        assertEquals(1, chords.size)
        assertEquals(listOf(60, 64, 67), chords[0].notes)
        // The next chord, later, throws again.
        assertEquals(1, jam.play(62 to 2000L, 65 to 2010L, 69 to 2020L).filterIsInstance<Jam.Effect.Chord>().size)
    }

    @Test fun spreadNotesOrRepeatsAreNoChord() {
        val jam = Jam()
        assertTrue(jam.play(60 to 0L, 64 to 100L, 67 to 200L).none { it is Jam.Effect.Chord })   // 200 ms from the first
        assertTrue(jam.play(60 to 1000L, 60 to 1050L, 64 to 1100L).none { it is Jam.Effect.Chord })   // two keys only
    }

    @Test fun aQuickRisingRunOfFivePaintsARainbowOnce() {
        val jam = Jam()
        val fx = jam.play(60 to 0L, 62 to 200L, 64 to 400L, 65 to 600L, 67 to 800L, 69 to 1000L)
        val rainbows = fx.filterIsInstance<Jam.Effect.Rainbow>()
        assertEquals(1, rainbows.size)
        assertEquals(listOf(60, 62, 64, 65, 67), rainbows[0].notes)
        // Falling, slow, or broken runs paint nothing.
        assertTrue(Jam().play(72 to 0L, 71 to 100L, 69 to 200L, 67 to 300L, 65 to 400L).none { it is Jam.Effect.Rainbow })
        assertTrue(Jam().play(60 to 0L, 62 to 500L, 64 to 1000L, 65 to 1500L, 67 to 2000L).none { it is Jam.Effect.Rainbow })
        assertTrue(Jam().play(60 to 0L, 62 to 100L, 61 to 200L, 64 to 300L, 65 to 400L).none { it is Jam.Effect.Rainbow })
        // A new run after a break paints again.
        val again = jam.play(60 to 3000L, 62 to 3100L, 64 to 3200L, 65 to 3300L, 67 to 3400L)
        assertEquals(1, again.filterIsInstance<Jam.Effect.Rainbow>().size)
    }

    @Test fun notesStruckTogetherAreOneStepOfARun() {
        val jam = Jam()
        // C E G as a chord, then D E F G A rising: the chord is one step standing for its G, so D starts a new run.
        val fx = jam.play(60 to 0L, 64 to 10L, 67 to 20L, 62 to 300L, 64 to 450L, 65 to 600L, 67 to 750L)
        assertTrue(fx.none { it is Jam.Effect.Rainbow })
        assertEquals(1, jam.play(69 to 900L).filterIsInstance<Jam.Effect.Rainbow>().size)
    }

    @Test fun theMicHearsEveryJamKeyAndTheCsExactly() {
        val jam = Jam()
        val tune = listOf(60, 62, 64, 65, 67, 69, 71, 72, 61, 63, 66, 68, 70, 64, 60, 36, 48, 84, 72)
        val audio = PianoSynth.render(tune.mapIndexed { i, m -> SynthNote(m, 500.0 + i * 450) }, 500.0 + tune.size * 450 + 800)
        // As the screen does: a strike that is only the echo of a higher note just played is dropped.
        val heard = MicRig.strikes(audio, jam.micNotes(), jam.micGroups())
            .filter { s -> val at = s.sample / 48; !jam.micEcho(s.midi, at).also { if (!it) jam.strike(s.midi, at) } }.map { it.midi }
        assertEquals(tune, heard)
        assertEquals(16, jam.micNotes().size)
    }

    @Test fun theChordEarFindsEveryNoteOfAChordAndOneForASingleNote() {
        val chords = listOf(listOf(60, 64, 67), listOf(65, 69, 72), listOf(62, 65, 69), listOf(64, 67, 72), listOf(55, 59, 62), listOf(60, 62, 64))
        val audio = PianoSynth.render(chords.flatMapIndexed { i, c -> c.map { SynthNote(it, 500.0 + i * 900, gainDb = -16.0) } }, 500.0 + chords.size * 900 + 500)
        val ear = ChordEar()
        ear.feed(audio, minOf(audio.size, 48_000 * 2 - 10))                          // the ring holds two seconds
        chords.take(2).forEachIndexed { i, c -> assertEquals("chord $c", c.toSet(), ear.notesAt((500L + i * 900) * 48)) }
        // Across the whole take, window by window (fresh ear each time so the ring holds it).
        chords.forEachIndexed { i, c ->
            val onset = (500 + i * 900) * 48
            val e = ChordEar(); e.feed(audio.copyOfRange(onset - 24_000, onset + 24_000), 48_000, at = onset - 24_000L)
            assertEquals("chord $c", c.toSet(), e.notesAt(onset.toLong()))
        }
        // Single notes in a legato line (the last one still ringing): one note each, the new one.
        val line = listOf(60, 62, 64, 65, 67, 69, 71, 72, 61, 66, 48, 84, 64)
        val legato = PianoSynth.render(line.mapIndexed { i, m -> SynthNote(m, 500.0 + i * 250, durationMs = 600.0) }, 500.0 + line.size * 250 + 800)
        line.forEachIndexed { i, m ->
            val onset = (500 + i * 250) * 48
            val e = ChordEar(); e.feed(legato.copyOfRange(onset - 24_000, onset + 24_000), 48_000, at = onset - 24_000L)
            assertEquals("note $m", setOf(m), e.notesAt(onset.toLong()))
        }
    }

    @Test fun theChordEarWaitsForTheAudioAndForgetsOldAudio() {
        val ear = ChordEar()
        val a = PianoSynth.render(listOf(SynthNote(64, 500.0)), 600.0)
        ear.feed(a)
        assertNull(ear.notesAt(500L * 48))                                          // 170 ms after the onset is not in yet
        val b = PianoSynth.render(listOf(SynthNote(64, 500.0)), 900.0)
        val ear2 = ChordEar(); ear2.feed(b)
        assertNotNull(ear2.notesAt(500L * 48))
        ear2.feed(FloatArray(48_000 * 3))                                           // three seconds later: gone
        assertNull(ear2.notesAt(500L * 48))
        assertTrue(ChordEar.freshNotes(FloatArray(4096), FloatArray(4096), 48_000).isEmpty())   // silence
        // The mic restarted: the matcher counts from 0 again, and so does the ring.
        ear2.feed(b, b.size, at = 0)
        assertEquals(setOf(64), ear2.notesAt(500L * 48))
    }

    @Test fun aChordOnTheMicThrowsConfettiWithTheChordEar() {
        // What JamScreen does: the matcher hears one note of the chord, the chord ear the rest at the same onset.
        val jam = Jam()
        val audio = PianoSynth.render(listOf(60, 64, 67).map { SynthNote(it, 600.0, gainDb = -16.0) } + SynthNote(72, 2000.0), 3000.0)
        val ear = ChordEar()
        val m = com.bigpapituna.sharpears.engine.NoteMatcher()
        m.expected = jam.micNotes(); m.groups = jam.micGroups()
        val effects = ArrayList<Jam.Effect>()
        val waiting = ArrayList<Pair<com.bigpapituna.sharpears.engine.MicEvent.Strike, Long>>()   // strike, onset
        var strikes = 0
        MicRig.run(audio, m, onChunk = { c, n, at ->
            ear.feed(c, n, at)
            // Like the screen: once the audio is in, ask the chord ear about each strike's onset.
            waiting.removeAll { (s, onset) ->
                val notes = ear.notesAt(onset) ?: return@removeAll false
                notes.filter { it != s.midi && kotlin.math.abs(it - s.midi) != 1 }.forEach { effects += jam.strike(it, onset / 48) }
                true
            }
        }) { e ->
            if (e !is com.bigpapituna.sharpears.engine.MicEvent.Strike) return@run
            strikes++
            val onset = e.sample - ((e.delayMs ?: 0.0) * 48).toLong()
            effects += jam.strike(e.midi, onset / 48)
            waiting += e to onset
        }
        assertTrue("the matcher alone misses most of a free-play chord", strikes < 4)
        val chord = effects.filterIsInstance<Jam.Effect.Chord>()
        assertEquals(1, chord.size)
        assertEquals(listOf(60, 64, 67), chord[0].notes)
        assertFalse(effects.filterIsInstance<Jam.Effect.Burst>().any { it.midi !in setOf(60, 64, 67, 72) })
    }

    @Test fun aHighNotesEchoOnALowerKeyIsDropped() {
        val jam = Jam()
        jam.strike(84, 1000)
        assertTrue(jam.micEcho(65, 1100))                                           // F4: C6 is its third partial
        assertTrue(jam.micEcho(53, 1100))                                           // F3: C6 is its sixth
        assertFalse(jam.micEcho(72, 1100))                                          // an octave is played on purpose
        assertFalse(jam.micEcho(65, 1400))                                          // too late to be an echo
        assertFalse(jam.micEcho(64, 1100))
    }
}
