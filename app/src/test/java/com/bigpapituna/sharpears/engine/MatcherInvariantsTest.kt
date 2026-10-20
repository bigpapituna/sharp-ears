package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.hints.WrongKeys
import com.bigpapituna.sharpears.engine.play.Jam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit (docs/AUDIT-ENGINE.md): what the note matcher must never do, whatever it is fed. Synthetic audio only
 * (PianoSynth and plain signals); FixtureTest and StallReviewTest hold it to Roy's recordings.
 */
class MatcherInvariantsTest {
    private val fs = 48_000

    /** Feeds [audio] in 10 ms chunks; [expectedAt] gives the expected set (one chord group) at each ms. */
    private fun run(audio: FloatArray, rate: Int = fs, expectedAt: (Double) -> Set<Int>): List<Pair<Set<Int>, MicEvent>> {
        val m = NoteMatcher(rate)
        val out = ArrayList<Pair<Set<Int>, MicEvent>>()
        var i = 0
        val chunk = rate / 100
        while (i < audio.size) {
            val exp = expectedAt(i * 1000.0 / rate)
            m.expected = exp; m.groups = listOf(exp)
            val n = minOf(chunk, audio.size - i)
            m.process(audio.copyOfRange(i, i + n)).forEach { out += exp to it }
            i += n
        }
        return out
    }

    /**
     * A wrong key is looked for against the notes expected when its sound began, and reported about 150 ms later. When
     * the expected notes changed in between (the player hit the next note first, then the right one), the report named
     * a key that was now expected (distance 0, painted red on the right key) or one far from every expected note.
     */
    @Test fun aWrongKeyIsNeverOneExpectedWhenItIsReported() {
        val d6 = 86
        val audio = PianoSynth.render(listOf(SynthNote(d6, 500.0)), 1500.0)
        // C6 expected while D6 sounds and is looked at; then the song moves on, before the report.
        val toD6 = run(audio) { ms -> if (ms < 620) setOf(84) else setOf(d6) }
        val toFar = run(audio) { ms -> if (ms < 620) setOf(84) else setOf(60) }
        for ((exp, e) in toD6 + toFar) if (e is MicEvent.Wrong) {
            assertTrue("$e with $exp expected", e.midi !in exp)
            assertEquals("$e", WrongKeys.distance(e.midi, exp), e.distance)
            assertTrue("$e", e.distance in 1..WrongKeys.REACH)
        }
        // Expected all along, the same strike is still named (the guard does not hide real wrong keys).
        assertEquals(listOf(d6), run(audio) { setOf(84) }.map { it.second }.filterIsInstance<MicEvent.Wrong>().map { it.midi })
    }

    /**
     * Silence, a DC offset, a DC step, a click, clipped noise and a signal pinned to the rails are no piano strikes for
     * the sets the app listens for (a key, a chord, the Jam's sixteen). Not covered: every key of the piano at once (no
     * screen asks for that; a DC step then passes for D1 and D2) and loud broadband noise (see docs/AUDIT-ENGINE.md).
     */
    @Test fun silenceDcClicksAndRailsAreNoStrikes() {
        val signals = mapOf(
            "silence" to FloatArray(fs * 3),
            "dc" to FloatArray(fs * 3) { 0.5f },
            "dc step" to FloatArray(fs * 3) { if (it > fs) 0.8f else 0f },
            "dc step down" to FloatArray(fs * 3) { if (it > fs) -1f else 0f },
            "click" to FloatArray(fs * 3) { if (it in fs..fs + 48) 0.8f else 0f },
            "rails" to FloatArray(fs * 3) { if (it in fs..2 * fs) 1f else -1f },
        )
        val sets = listOf(setOf(28), setOf(36), setOf(48), setOf(60), setOf(72), setOf(84), setOf(96), setOf(48, 52, 55), setOf(60, 64, 67, 70),
            Jam().micNotes())
        for ((name, audio) in signals) for (keys in sets) {
            val strikes = run(audio) { keys }.map { it.second }.filterIsInstance<MicEvent.Strike>()
            assertEquals("$name with $keys", emptyList<MicEvent.Strike>(), strikes)
        }
    }

    /** A note so loud it clips is still that note, and still not its neighbour. */
    @Test fun aClippedNoteIsStillItself() {
        for (midi in listOf(48, 60, 72)) {
            val audio = PianoSynth.render(listOf(SynthNote(midi, 500.0, gainDb = 6.0)), 2500.0).map { it.coerceIn(-1f, 1f) }.toFloatArray()
            assertEquals(noteName(midi), listOf(midi), run(audio) { setOf(midi) }.map { it.second }.filterIsInstance<MicEvent.Strike>().map { it.midi })
            assertEquals(noteName(midi), emptyList<Int>(), run(audio) { setOf(midi + 2) }.map { it.second }.filterIsInstance<MicEvent.Strike>().map { it.midi })
        }
    }

    /**
     * The octave hint listens to [NoteMatcher.candidates] for an expected note turned down as its own octave. A right
     * note, struck alone, is heard and never offered there as an octave off.
     */
    @Test fun aRightNoteIsNeverACandidateForItsOwnOctave() {
        for (midi in 33..96) {
            val m = NoteMatcher(fs).apply { expected = setOf(midi); groups = listOf(setOf(midi)) }
            val octave = mutableListOf<Candidate>()
            m.candidates = { c -> if (c.reason == Reject.HIGHER_OCTAVE || c.reason == Reject.LOWER_OCTAVE) octave += c }
            val audio = PianoSynth.render(listOf(SynthNote(midi, 500.0)), 2000.0)
            val strikes = mutableListOf<Int>()
            var i = 0
            while (i < audio.size) { val n = minOf(480, audio.size - i); m.process(audio.copyOfRange(i, i + n)).filterIsInstance<MicEvent.Strike>().forEach { strikes += it.midi }; i += n }
            assertEquals(noteName(midi), listOf(midi), strikes)
            assertEquals(noteName(midi), emptyList<Candidate>(), octave)
        }
    }

    /**
     * 44.1 kHz: the app always records at 48 kHz (MicInput.RATE; Android resamples) and resamples injected WAVs, so
     * the matcher never sees 44.1 kHz on a phone. It still hears every key once there, should that change.
     */
    @Test fun the441kHzMatcherHearsEveryKeyOnce() {
        val rate = 44_100
        for (midi in 33..96 step 3) {
            val audio = PianoSynth.render(listOf(SynthNote(midi, 500.0)), 2000.0, sampleRate = rate)
            val ev = run(audio, rate) { setOf(midi) }.map { it.second }
            assertEquals(noteName(midi), listOf(midi), ev.filterIsInstance<MicEvent.Strike>().map { it.midi })
        }
    }
}
