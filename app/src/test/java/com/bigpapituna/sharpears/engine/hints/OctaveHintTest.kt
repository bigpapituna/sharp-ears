package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.Candidate
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.Reject
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OctaveHintTest {
    @Test fun anOctaveUpHeldForTwoSecondsFiresOnce() {
        val h = OctaveHint()
        h.reset(setOf(69))
        h.candidate(1_000, Candidate(69, 0, -20.0, Reject.HIGHER_OCTAVE))
        h.wrong(1_010, 81, -18.0)
        assertNull("not yet", h.check(2_500))
        val hint = h.check(3_000)
        assertEquals(OctaveHint.Hint(69, 1), hint)
        assertEquals("That's the right note, but one octave up. Try lower.", hint!!.line)
        assertNull("once per wait", h.check(4_000))
        h.reset(setOf(69))
        h.wrong(5_000, 57, -20.0)
        assertEquals(OctaveHint.Hint(69, -1), h.check(7_100))
        assertEquals("That's the right note, but one octave down. Try higher.", OctaveHint.Hint(69, -1).line)
    }

    @Test fun somethingStrongerSinceBlocksIt() {
        val h = OctaveHint()
        h.reset(setOf(69))
        h.wrong(1_000, 81, -25.0)
        h.wrong(1_800, 67, -15.0)                // a louder G4: the player is searching, not stuck an octave off
        assertNull(h.check(3_100))
        // Weaker rivals do not matter.
        h.reset(setOf(69))
        h.wrong(10_000, 81, -15.0)
        h.candidate(10_500, Candidate(69, 0, -30.0, Reject.SEMITONE))
        assertNotNull(h.check(12_000))
    }

    @Test fun hearingTheRightNoteOrMovingOnClearsIt() {
        val h = OctaveHint()
        h.reset(setOf(69))
        h.wrong(1_000, 81, -15.0)
        h.right(69)
        assertNull(h.check(3_500))
        h.reset(setOf(60))
        h.wrong(5_000, 81, -15.0)                // not an octave of anything expected now
        assertNull(h.check(8_000))
    }

    @Test fun anyOfLessonsAreLeftAlone() {
        assertFalse(OctaveHint.appliesTo("lesson-high-low", setOf("high-low")))
        assertTrue(OctaveHint.appliesTo("lesson-find-c", setOf("high-low")))
        assertTrue("songs are not lessons", OctaveHint.appliesTo("high-low", setOf("high-low")))
    }

    /** Replays audio through the matcher the way wait mode does and feeds the hint as MicInput does. */
    private fun replay(samples: FloatArray, expected: Set<Int>, waitFromS: Double = 0.0): Pair<OctaveHint.Hint, Double>? {
        val m = NoteMatcher(48_000)
        m.group = expected
        val hint = OctaveHint()
        val heard = mutableSetOf<Int>()
        fun ms(sample: Long) = sample * 1000 / 48_000
        m.candidates = { c -> hint.candidate(ms(c.sample), c) }
        var waiting = false
        var i = 0
        while (i < samples.size) {
            if (!waiting && i >= waitFromS * 48_000) { waiting = true; hint.reset(expected - heard) }
            m.expected = expected - heard
            val n = minOf(480, samples.size - i)
            for (e in m.process(samples.copyOfRange(i, i + n))) when (e) {
                is MicEvent.Strike -> { heard += e.midi; hint.right(e.midi) }
                is MicEvent.Wrong -> hint.wrong(ms(e.sample), e.midi, e.riseDb)
            }
            i += n
            if (waiting) hint.check(ms(m.samplesProcessed))?.let { return it to i / 48_000.0 }
        }
        return null
    }

    @Test fun synthesizedWrongOctaves() {
        val up = replay(PianoSynth.render(listOf(SynthNote(81, 500.0, 3000.0)), 4000.0), setOf(69))
        assertEquals(OctaveHint.Hint(69, 1), up?.first)
        assertTrue("about two seconds after the strike: ${up?.second}", up!!.second in 2.4..3.0)
        val down = replay(PianoSynth.render(listOf(SynthNote(48, 500.0, 3000.0)), 4000.0), setOf(60))
        assertEquals(OctaveHint.Hint(60, -1), down?.first)
        assertNull("the right note: no hint", replay(PianoSynth.render(listOf(SynthNote(69, 500.0, 3000.0)), 4000.0), setOf(69)))
        assertNull("a fifth off: no hint", replay(PianoSynth.render(listOf(SynthNote(76, 500.0, 3000.0)), 4000.0), setOf(69)))
    }

    private val stalls = File("src/test/resources/stalls")

    /**
     * Roy's stuck moment of 2026-10-17 (lesson together-4, F3 + A4 expected): he played A5 for A4 at 8.03 s into the
     * clip. The wait began 2 s into the clip (clip_starts_s=2.00). The hint must say "an octave up" about A4.
     */
    @Test fun roysA5ForA4() {
        val wav = File(stalls, "stall-20261017-210524-lesson-together-4.wav")
        if (!wav.exists()) { println("no stall clips (gitignored); pull them with scripts\\pull-stalls.ps1"); return }
        val got = replay(Wav.read(wav).samples, setOf(53, 69), waitFromS = 2.0)
        println("together-4: $got")
        assertNotNull("no octave hint on Roy's A5", got)
        assertEquals(OctaveHint.Hint(69, 1), got!!.first)
        assertTrue("fires about two seconds after the A5 (8.03 s): ${got.second}", got.second in 9.9..10.6)
    }

    /** together-3: Roy's E4 (weak fundamental) is heard at 2.39 s. No octave hint before or after. */
    @Test fun roysE4GetsNoOctaveHint() {
        val wav = File(stalls, "stall-20261017-210443-lesson-together-3.wav")
        if (!wav.exists()) return
        assertNull(replay(Wav.read(wav).samples, setOf(64), waitFromS = 2.0))
    }
}
