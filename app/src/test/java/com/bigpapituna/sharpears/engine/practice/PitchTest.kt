package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.midiFreq
import com.bigpapituna.sharpears.engine.partialFreq
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTest {
    private val detector = PitchDetector()

    private fun centsBetween(a: Double, b: Double) = 1200 * Math.log(a / b) / Math.log(2.0)

    @Test fun everyPianoNoteFromA2ToC6IsWithinFiveCents() {
        for (midi in 45..84) {
            val audio = PianoSynth.render(listOf(SynthNote(midi, 0.0, 1500.0)), 1500.0, bScale = 1.0)
            // A window starting 150 ms in: after the attack, while the note rings.
            val start = 7200
            val window = audio.copyOfRange(start, start + 4096)
            val r = detector.detect(window)
            assertNotNull("note $midi", r)
            r!!
            assertEquals("note $midi: $r", midi, r.midi)
            // Against the nominal pitch: a piano is tuned with a little stretch, so 5 cents is the budget.
            assertTrue("note $midi off by ${centsBetween(r.freq, midiFreq(midi))} cents", abs(centsBetween(r.freq, midiFreq(midi))) < 5.0)
            // Against the fundamental the synth actually plays, the detector is much tighter.
            assertTrue("note $midi vs fundamental: ${centsBetween(r.freq, partialFreq(midi, 1, 1.0))}", abs(centsBetween(r.freq, partialFreq(midi, 1, 1.0))) < 2.0)
            assertTrue("note $midi confidence ${r.confidence}", r.confidence > 0.8)
        }
    }

    @Test fun detectionIsCheapEnoughForTwentyHertz() {
        val audio = PianoSynth.render(listOf(SynthNote(45, 0.0, 1500.0)), 1500.0)
        val window = audio.copyOfRange(7200, 7200 + 4096)
        repeat(20) { detector.detect(window) }                          // warm up the JIT
        val t0 = System.nanoTime()
        repeat(50) { detector.detect(window) }
        val ms = (System.nanoTime() - t0) / 50 / 1e6
        println("detect: $ms ms")
        assertTrue("detect takes $ms ms", ms < 25)
    }

    @Test fun pureTonesReportTheirCents() {
        for (cents in listOf(-45.0, -20.0, -5.0, 0.0, 7.0, 30.0, 45.0)) {
            val f = 440.0 * Math.pow(2.0, cents / 1200)
            val x = FloatArray(4096) { (0.3 * sin(2 * PI * f * it / 48_000)).toFloat() }
            val r = detector.detect(x)!!
            assertEquals(69, r.midi)
            assertEquals("target $cents", cents, r.cents, 0.5)
        }
        // A tone just past a quarter tone belongs to the next note.
        val x = FloatArray(4096) { (0.3 * sin(2 * PI * 453.0 * it / 48_000)).toFloat() }
        assertEquals(70, detector.detect(x)!!.midi)
    }

    @Test fun otherReferencePitchShiftsTheCents() {
        val d = PitchDetector(a4 = 442.0)
        val x = FloatArray(4096) { (0.3 * sin(2 * PI * 442.0 * it / 48_000)).toFloat() }
        val r = d.detect(x)!!
        assertEquals(69, r.midi)
        assertEquals(0.0, r.cents, 0.5)
    }

    @Test fun silenceAndNoiseGiveNoPitch() {
        assertNull(detector.detect(FloatArray(4096)))
        val quiet = PianoSynth.render(emptyList(), 200.0)                  // only the synth's -70 dB hiss
        assertNull(detector.detect(quiet.copyOf(4096)))
        val rnd = Random(3)
        for (amp in listOf(0.01, 0.05, 0.3)) {
            val noise = FloatArray(4096) { (rnd.nextGaussian() * amp).toFloat() }
            assertNull("noise $amp", detector.detect(noise))
        }
        assertNull(detector.detect(FloatArray(100) { 0.5f }))                // too short
    }

    @Test fun roomNoiseUnderANoteStillFindsTheNote() {
        val rnd = Random(5)
        val audio = PianoSynth.render(listOf(SynthNote(57, 0.0, 1500.0)), 1500.0)
        val noisy = FloatArray(4096) { audio[7200 + it] + (rnd.nextGaussian() * 0.003).toFloat() }
        assertEquals(57, detector.detect(noisy)!!.midi)
    }

    @Test fun trackerSlidesAWindowOverIncomingChunks() {
        val tracker = PitchTracker()
        assertNull(tracker.analyze())
        val f = 261.63
        val chunk = 512
        var n = 0
        repeat(12) { c ->
            val buf = FloatArray(chunk) { (0.3 * sin(2 * PI * f * (c * chunk + it) / 48_000)).toFloat() }
            tracker.feed(buf, chunk); n += chunk
            if (n < 3000) assertNull("only $n samples", tracker.analyze())
        }
        val r = tracker.analyze()!!
        assertEquals(60, r.midi)
        assertEquals(0.0, r.cents, 1.0)
        tracker.clear()
        assertNull(tracker.analyze())
    }

    @Test fun smootherSteadiesTheNeedleAndHoldsBriefly() {
        val s = PitchSmoother(holdMs = 400, blend = 0.5)
        fun r(midi: Int, cents: Double) = PitchResult(440.0, midi, cents, 0.9)
        assertEquals(10.0, s.update(r(69, 10.0), 0)!!.cents, 1e-9)
        assertEquals(15.0, s.update(r(69, 20.0), 50)!!.cents, 1e-9)           // halfway
        assertEquals(-30.0, s.update(r(70, -30.0), 100)!!.cents, 1e-9)       // a new note jumps
        assertNotNull(s.update(null, 300))                                   // lingers
        assertNull(s.update(null, 600))                                      // then lets go
    }
}
