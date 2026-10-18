package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Naming wrong keys (MicEvent.Wrong), the noise and candidates hooks, and the per-profile mic offset. */
class WrongKeyMatcherTest {
    private val fs = 48_000

    private class Heard(val events: List<MicEvent>, val noise: Int, val candidates: List<Candidate>)

    private fun listen(audio: FloatArray, expected: Set<Int>, cfg: MatcherConfig = MatcherConfig(), offsetDb: Double = 0.0): Heard {
        val m = NoteMatcher(fs, cfg)
        m.offsetDb = offsetDb
        m.expected = expected
        m.group = expected
        var noise = 0
        val cands = mutableListOf<Candidate>()
        m.noise = { noise++ }
        m.candidates = { cands += it }
        val out = mutableListOf<MicEvent>()
        var i = 0
        while (i < audio.size) { val n = minOf(480, audio.size - i); out += m.process(audio.copyOfRange(i, i + n)); i += n }
        return Heard(out, noise, cands)
    }

    private fun wrongs(h: Heard) = h.events.filterIsInstance<MicEvent.Wrong>()

    @Test fun aWrongKeyIsNamedWithItsNearestExpectedNoteAndDistance() {
        // played, expected, nearest, distance
        for ((played, expected, nearest, distance) in listOf(
            listOf(67, 60, 60, 7), listOf(62, 60, 60, 2), listOf(59, 60, 60, 1), listOf(53, 57, 57, 4), listOf(79, 72, 72, 7),
        )) {
            val h = listen(PianoSynth.render(listOf(SynthNote(played, 500.0)), 2000.0), setOf(expected))
            val w = wrongs(h)
            assertEquals("played ${noteName(played)} for ${noteName(expected)}: ${h.events}", 1, w.size)
            assertEquals(played, w[0].midi)
            assertEquals(nearest, w[0].nearest)
            assertEquals(distance, w[0].distance)
            assertEquals(distance <= 2, w[0].slip)
            assertEquals("no right note", 0, h.events.count { it is MicEvent.Strike })
        }
    }

    @Test fun theNearestOfSeveralExpectedNotes() {
        val h = listen(PianoSynth.render(listOf(SynthNote(65, 500.0)), 2000.0), setOf(53, 69))   // F4 for F3 + A4
        val w = wrongs(h).single()
        assertEquals(65, w.midi); assertEquals(69, w.nearest); assertEquals(4, w.distance)
    }

    @Test fun anOctaveOffIsNamedTwelveAway() {
        for ((played, expected) in listOf(81 to 69, 57 to 69, 76 to 64, 48 to 60)) {
            val h = listen(PianoSynth.render(listOf(SynthNote(played, 500.0)), 2000.0), setOf(expected))
            val w = wrongs(h)
            assertEquals("played ${noteName(played)} for ${noteName(expected)}: ${h.events}", listOf(played), w.map { it.midi })
            assertEquals(12, w[0].distance)
        }
    }

    @Test fun theRightNoteIsNeverAlsoAWrongKey() {
        for (midi in listOf(45, 57, 60, 64, 69, 76, 84)) {
            val h = listen(PianoSynth.render(listOf(SynthNote(midi, 500.0)), 2000.0), setOf(midi))
            assertEquals(listOf(midi), h.events.filterIsInstance<MicEvent.Strike>().map { it.midi })
            assertEquals("${noteName(midi)}: ${h.events}", 0, wrongs(h).size)
        }
        // Roy's E4 with a thin fundamental under its octave: heard, and its octave never called a wrong key.
        val thin = PianoSynth.render(listOf(SynthNote(64, 500.0, gainDb = -24.0), SynthNote(76, 500.0)), 2000.0)
        assertEquals(0, wrongs(listen(thin, setOf(64))).size)
    }

    /** A voice: harmonics on a gliding, wobbling pitch (vibrato), started abruptly so the room jump fires. */
    private fun voice(f0From: Double, f0To: Double, startMs: Double, lengthMs: Double, vibratoCents: Double, gainDb: Double, into: FloatArray) {
        val a0 = 10.0.pow(gainDb / 20)
        val start = (startMs * fs / 1000).toInt()
        val n = (lengthMs * fs / 1000).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / fs
            val f = (f0From + (f0To - f0From) * i / n) * 2.0.pow(vibratoCents / 1200 * sin(2 * PI * 5.5 * t))
            phase += 2 * PI * f / fs
            val env = minOf(1.0, t / 0.008) * minOf(1.0, (n - i).toDouble() / (0.03 * fs))
            var x = 0.0
            for (h in 1..8) x += sin(h * phase) / h * (if (h in 2..3) 1.6 else 1.0)    // a vowel's formant lift
            if (start + i < into.size) into[start + i] += (a0 * env * x).toFloat()
        }
    }

    @Test fun singingAndTalkingAreNoiseNotWrongKeys() {
        // Sung notes near the expected C4 (wide vibrato), and speech-like syllables gliding through 110-220 Hz.
        val sung = FloatArray(fs * 5)
        voice(220.0, 222.0, 500.0, 900.0, 60.0, -20.0, sung)      // around A3
        voice(330.0, 326.0, 1900.0, 900.0, 80.0, -20.0, sung)     // around E4
        voice(262.0, 262.0, 3300.0, 900.0, 50.0, -22.0, sung)     // around C4 itself
        // Speech: each syllable's pitch rises or falls 3 to 5 semitones (intonation), with a little wobble. A dead
        // monotone vowel would look like a held note; real talk and TV in Roy's sessions never did (0 wrong keys in
        // session-160604 and session-155837).
        val talk = FloatArray(fs * 5)
        for (k in 0 until 14) {
            val from = 110.0 + 15 * (k % 5)
            val semis = (3 + k % 3) * (if (k % 2 == 0) 1 else -1)
            voice(from, from * 2.0.pow(semis / 12.0), 300.0 + k * 320.0, 220.0, 30.0, -22.0, talk)
        }
        for ((name, audio) in listOf("singing" to sung, "talking" to talk)) {
            val noisy = PianoSynth.render(emptyList(), 5000.0, noiseDb = -62.0)
            for (i in audio.indices) audio[i] += noisy[i]
            val h = listen(audio, setOf(60))
            println("$name: ${h.events.size} events (${wrongs(h).map { noteName(it.midi) }}), ${h.noise} noise")
            // A syllable whose pitch barely moves between the two looks can pass as a held note: at most one of 14.
            assertTrue("$name painted wrong keys: ${wrongs(h)}", wrongs(h).size <= (if (name == "talking") 1 else 0))
            assertTrue("$name should reach the noise hook", h.noise > 0)
        }
    }

    @Test fun nothingIsCalledWrongWhenNamingIsOff() {
        val h = listen(PianoSynth.render(listOf(SynthNote(67, 500.0)), 2000.0), setOf(60), MatcherConfig(nameWrongKeys = false))
        assertEquals(0, wrongs(h).size)
        assertEquals("the strike is noise instead", 1, h.noise)
    }

    @Test fun candidatesSayWhenTheOctaveWasPlayed() {
        val up = listen(PianoSynth.render(listOf(SynthNote(81, 500.0)), 2000.0), setOf(69))      // A5 for A4
        assertTrue("A4 turned down as a higher octave: ${up.candidates.map { it.reason }.distinct()}",
            up.candidates.any { it.midi == 69 && it.reason == Reject.HIGHER_OCTAVE })
        val down = listen(PianoSynth.render(listOf(SynthNote(57, 500.0)), 2000.0), setOf(69))    // A3 for A4
        assertTrue("A4 turned down for its octave below: ${down.candidates.map { it.reason }.distinct()}",
            down.candidates.any { it.midi == 69 && it.reason == Reject.LOWER_OCTAVE })
        assertTrue("never the other way", down.candidates.none { it.reason == Reject.HIGHER_OCTAVE })
    }

    @Test fun theMicOffsetMovesTheThresholds() {
        val cfg = MatcherConfig()
        val kid = cfg.easierBy(3.0)
        assertEquals(cfg.sensitivityDb - 3.0, kid.sensitivityDb, 1e-9)
        assertEquals(cfg.roomMarginDb - 2.5, kid.roomMarginDb, 1e-9)            // 10 -> 7.5, the floor
        assertEquals(MatcherConfig.MIN_ROOM_MARGIN_DB, cfg.easierBy(6.0).roomMarginDb, 1e-9)
        assertEquals(cfg.sensitivityDb - 1.0, cfg.easierBy(1.0).sensitivityDb, 1e-9)
        assertEquals(cfg.roomMarginDb - 1.0, cfg.easierBy(1.0).roomMarginDb, 1e-9)
        assertTrue(cfg.easierBy(0.0) === cfg)
        assertEquals("the other checks stay", cfg.oddRatio, kid.oddRatio, 0.0)
    }

    @Test fun aSoftNoteJustUnderTheFloorCountsWithTheKidOffset() {
        // Softer and softer C4s: some fall under the grown-up floor (-66 dB rise) but over the kid's (-69 dB), and
        // nothing a grown-up's mic hears is lost to a kid's.
        var onlyKid = 0
        var g = -56.0
        while (g >= -76.0) {
            val audio = PianoSynth.render(listOf(SynthNote(60, 500.0, gainDb = g)), 2000.0, noiseDb = -110.0)
            val adult = listen(audio, setOf(60)).events.count { it is MicEvent.Strike }
            val kid = listen(audio, setOf(60), offsetDb = 3.0).events.count { it is MicEvent.Strike }
            assertTrue("C4 at $g dB: grown-up $adult, kid $kid", kid >= adult)
            if (adult == 0 && kid == 1) onlyKid++
            g -= 1.0
        }
        println("soft C4: $onlyKid gain steps heard with the kid offset only")
        assertTrue("the offset should hear softer notes", onlyKid >= 1)
        // The stored setting is untouched by the offset.
        val m = NoteMatcher(fs); m.offsetDb = 3.0
        assertEquals(MatcherConfig().sensitivityDb, m.config.sensitivityDb, 0.0)
    }
}
