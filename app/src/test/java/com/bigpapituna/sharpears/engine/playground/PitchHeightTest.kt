package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.noteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pitch the flying pup follows (lesson 1), heard from PianoSynth notes with no expected keys. */
class PitchHeightTest {
    private val fs = 48_000

    private fun hear(audio: FloatArray, p: PitchHeight = PitchHeight(fs)): List<Heard> {
        val out = mutableListOf<Heard>()
        var i = 0
        while (i < audio.size) { val n = minOf(480, audio.size - i); out += p.process(audio.copyOfRange(i, i + n)); i += n }
        return out
    }

    @Test fun everyKeyAcrossThePianoToASemitone() {
        val misses = mutableListOf<String>()
        for (midi in 24..105) {
            val heard = hear(PianoSynth.render(listOf(SynthNote(midi, 300.0, 700.0)), 1300.0, seed = midi.toLong()))
            if (heard.size != 1 || abs(heard.single().midi - midi) > 0.6) misses += "${noteName(midi)}: ${heard.map { "%.2f".format(it.midi) }}"
        }
        assertTrue("missed: $misses", misses.isEmpty())
    }

    @Test fun softAndLoudAndQuickNotes() {
        for (gain in listOf(-34.0, -24.0, -6.0)) {
            val notes = listOf(72, 43, 88, 55).mapIndexed { i, m -> SynthNote(m, 400.0 + i * 350, 300.0, gainDb = gain) }
            val heard = hear(PianoSynth.render(notes, 2200.0))
            assertEquals("at $gain dB: ${heard.map { it.key }}", listOf(72, 43, 88, 55), heard.map { it.key })
        }
    }

    @Test fun onlyOnceANoteAndNothingForNoise() {
        // A held note rings on without starting again; claps and knocks have no pitch.
        assertEquals(1, hear(PianoSynth.render(listOf(SynthNote(60, 300.0, 2500.0)), 3200.0)).size)
        assertEquals(0, hear(ClapSynth.render(listOf(300.0, 900.0, 1500.0), 2000.0, peakDb = -10.0)).size)
        assertEquals(0, hear(FloatArray(fs * 2)).size)
    }

    @Test fun theSideOfMiddleCIsNeverMistaken() {
        // What the high-and-low game decides on: far keys are never heard on the wrong side.
        for (midi in (28..55) + (65..100)) {
            val k = hear(PianoSynth.render(listOf(SynthNote(midi, 300.0, 600.0)), 1200.0, seed = 40L + midi)).single().key
            assertEquals(noteName(midi), midi > 60, k > 60)
        }
    }

    @Test fun thePupFliesEvenlyOverTheOctaves() {
        assertEquals(0f, pupHeight(21.0), 0f)
        assertEquals(1f, pupHeight(108.0), 0f)
        val octave = pupHeight(72.0) - pupHeight(60.0)
        assertEquals(octave, pupHeight(48.0 + 12) - pupHeight(48.0), 1e-6f)
        assertTrue("middle C sits a little under halfway", pupHeight(60.0) in 0.35f..0.5f)
    }
}
