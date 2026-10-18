package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMatcherTest {
    private val fs = 48_000

    /** Event with its time in ms. */
    private data class At(val ms: Double, val e: MicEvent)

    /** Feeds audio in 10 ms chunks (not hop-aligned, like a real device); [expectedAt] gives the expected set over time. */
    private fun listen(audio: FloatArray, cfg: MatcherConfig = MatcherConfig(), expectedAt: (Double) -> Set<Int>): List<At> {
        val m = NoteMatcher(fs, cfg)
        val chunk = 480
        val out = ArrayList<At>()
        var i = 0
        while (i < audio.size) {
            m.expected = expectedAt(i * 1000.0 / fs)
            val n = minOf(chunk, audio.size - i)
            m.process(audio.copyOfRange(i, i + n)).forEach { out += At(it.sample * 1000.0 / fs, it) }
            i += n
        }
        return out
    }

    private fun strikes(ev: List<At>) = ev.filter { it.e is MicEvent.Strike }
    private fun wrongs(ev: List<At>) = ev.filter { it.e is MicEvent.Wrong }
    private fun midiOf(a: At) = (a.e as MicEvent.Strike).midi

    @Test fun singleNotesAcrossTheKeyboard() {
        for (midi in listOf(45, 52, 57, 60, 64, 67, 72, 76, 84, 93)) {
            val audio = PianoSynth.render(listOf(SynthNote(midi, 500.0)), 2000.0)
            val ev = listen(audio) { setOf(midi) }
            val s = strikes(ev)
            assertEquals("${noteName(midi)}: $ev", listOf(midi), s.map(::midiOf))
            val latency = s.single().ms - 500
            println("${noteName(midi)} window=${NoteMatcher().windowFor(midi)} latency=${latency.toInt()} ms rise=${"%.1f".format((s.single().e as MicEvent.Strike).riseDb)} dB")
            assertTrue("${noteName(midi)} latency $latency", latency in 0.0..(if (midi < 55) 220.0 else 150.0))
            assertEquals("${noteName(midi)} wrongs", 0, wrongs(ev).size)
        }
    }

    @Test fun semitoneNeighboursAreWrongNotes() {
        for ((played, expected) in listOf(60 to 61, 76 to 75, 57 to 56, 57 to 58, 45 to 46)) {
            val audio = PianoSynth.render(listOf(SynthNote(played, 500.0)), 2000.0)
            val ev = listen(audio) { setOf(expected) }
            assertEquals("played ${noteName(played)} expecting ${noteName(expected)}: $ev", 0, strikes(ev).size)
            assertEquals("played ${noteName(played)} expecting ${noteName(expected)}", 1, wrongs(ev).size)
        }
    }

    @Test fun lowerNotesWhoseOvertonesMatchAreRejected() {
        // octave below, two octaves below, a twelfth below (its 3rd partial is the expected fundamental)
        for ((played, expected) in listOf(64 to 76, 45 to 57, 48 to 72, 53 to 72, 57 to 69)) {
            val audio = PianoSynth.render(listOf(SynthNote(played, 500.0)), 2000.0)
            val ev = listen(audio) { setOf(expected) }
            assertEquals("played ${noteName(played)} expecting ${noteName(expected)}: $ev", 0, strikes(ev).size)
        }
    }

    @Test fun higherOctaveIsRejected() {
        for ((played, expected) in listOf(76 to 64, 69 to 57, 72 to 60)) {
            val audio = PianoSynth.render(listOf(SynthNote(played, 500.0)), 2000.0)
            assertEquals("played ${noteName(played)} expecting ${noteName(expected)}", 0, strikes(listen(audio) { setOf(expected) }).size)
        }
    }

    /** Roy's E4 through the Fold mic: a thin fundamental under strong even partials (here an octave struck with it). */
    @Test fun noteWithAThinFundamentalStillCounts() {
        val audio = PianoSynth.render(listOf(SynthNote(64, 500.0, gainDb = -24.0), SynthNote(76, 500.0)), 2000.0)
        assertEquals(listOf(64), strikes(listen(audio) { setOf(64) }).map(::midiOf))
        assertEquals("the old threshold refused it", 0, strikes(listen(audio, MatcherConfig(oddRatio = 0.15)) { setOf(64) }).size)
    }

    private class PhraseResult(val missed: List<String>, val early: List<String>, val wrongs: Int, val medianMs: Int?)

    /** The opening of Für Elise, both hands, with the expected set advancing like wait mode. */
    private fun playFurElise(step: Double, noiseDb: Double, bScale: Double, seed: Long, touchSpreadDb: Double, trace: ((String) -> Unit)? = null): PhraseResult {
        // E5 D#5 E5 D#5 E5 B4 D5 C5 | A2+A4 E3 A3 C4 E4 A4 | E2+B4 E3 G#3 E4 G#4 B4 | A2+C5
        val seq = listOf(listOf(76), listOf(75), listOf(76), listOf(75), listOf(76), listOf(71), listOf(74), listOf(72),
            listOf(45, 69), listOf(52), listOf(57), listOf(60), listOf(64), listOf(69),
            listOf(40, 71), listOf(52), listOf(56), listOf(64), listOf(68), listOf(71), listOf(45, 72))
        val rnd = java.util.Random(seed)
        val notes = seq.flatMapIndexed { i, g -> g.map {
            SynthNote(it, 400 + i * step, durationMs = if (it < 60) 4.6 * step else 0.92 * step,
                gainDb = -14.0 + (rnd.nextDouble() * 2 - 1) * touchSpreadDb)
        } }
        val audio = PianoSynth.render(notes, 400 + seq.size * step + 800, noiseDb = noiseDb, bScale = bScale, seed = seed)
        var idx = 0
        val pending = seq[0].toMutableSet()
        val m = NoteMatcher(fs)
        m.trace = trace
        val missed = mutableListOf<String>()
        val early = mutableListOf<String>()       // a strike before its note sounded is a false positive
        val latencies = mutableListOf<Double>()
        var wrong = 0
        var i = 0
        while (i < audio.size && idx < seq.size) {
            // Give up on a group 400 ms after its notes sounded, like a player who moves on.
            if (i * 1000.0 / fs > 400 + idx * step + 400) {
                missed += seq[idx].joinToString("+") { noteName(it) } + " (unheard: " + pending.joinToString { noteName(it) } + ")"
                idx++; pending.clear(); if (idx < seq.size) pending += seq[idx]
            }
            if (idx >= seq.size) break
            m.expected = pending.toSet()
            m.group = seq[idx].toSet()
            for (e in m.process(audio.copyOfRange(i, minOf(audio.size, i + 480)))) {
                val rel = e.sample * 1000.0 / fs - (400 + idx * step)
                if (e is MicEvent.Wrong) wrong++
                if (e is MicEvent.Strike && rel < 0) { early += "${noteName(e.midi)}@${rel.toInt()}"; trace?.invoke("EARLY ${noteName(e.midi)} at ${(e.sample * 1000 / fs)} ms, group $idx ${seq[idx].map { noteName(it) }} sounds at ${(400 + idx * step).toInt()}") }
                if (e is MicEvent.Strike && pending.remove(e.midi) && pending.isEmpty()) {
                    latencies += rel
                    idx++; if (idx < seq.size) pending += seq[idx]
                }
            }
            i += 480
        }
        return PhraseResult(missed, early, wrong, latencies.sorted().getOrNull(latencies.size / 2)?.toInt())
    }

    @Test fun furEliseOpeningInWaitModeOrder() {
        // ponytail: at full tempo E3 comes 200 ms after E2, its octave below, while E2 still rings; every E3
        // overtone is an E2 overtone, so the quick E3 can go unheard (wait mode just waits). Allowed: 1 miss.
        val variants = listOf(
            "tempo 260 ms, quiet room" to listOf(260.0, -62.0, 1.4, 1.0, 0.0),
            "fast 200 ms" to listOf(200.0, -62.0, 1.4, 2.0, 0.0, 1.0),
            "slow 450 ms" to listOf(450.0, -62.0, 1.4, 3.0, 0.0),
            "noisy room" to listOf(300.0, -50.0, 1.4, 4.0, 0.0),
            "stiff strings" to listOf(300.0, -62.0, 2.5, 5.0, 0.0),
            "soft strings" to listOf(300.0, -62.0, 0.6, 6.0, 0.0),
            "uneven touch +-8 dB" to listOf(300.0, -62.0, 1.4, 7.0, 8.0),
        )
        val failures = mutableListOf<String>()
        for ((name, v) in variants) {
            val r = playFurElise(v[0], v[1], v[2], v[3].toLong(), v[4])
            println("$name: missed=${r.missed} early=${r.early} wrongs=${r.wrongs} median=${r.medianMs} ms")
            if (r.missed.size > v.getOrElse(5) { 0.0 }.toInt() || r.early.isNotEmpty()) failures += name
        }
        assertTrue("failed variants: $failures", failures.isEmpty())
    }

    @Test fun chordNeedsEachNoteAndHearsEach() {
        val triad = PianoSynth.render(listOf(60, 64, 67).map { SynthNote(it, 500.0) }, 2000.0)
        assertEquals(setOf(60, 64, 67), strikes(listen(triad) { setOf(60, 64, 67) }).map(::midiOf).toSet())
        val onlyC = PianoSynth.render(listOf(SynthNote(60, 500.0)), 2000.0)
        assertEquals(listOf(60), strikes(listen(onlyC) { setOf(60, 67) }).map(::midiOf))
        val bassAndMelody = PianoSynth.render(listOf(SynthNote(45, 500.0), SynthNote(69, 500.0)), 2000.0)
        assertEquals(setOf(45, 69), strikes(listen(bassAndMelody) { setOf(45, 69) }).map(::midiOf).toSet())
    }

    /**
     * Roy's E minor in chord practice: E4 and G4 heard, then B4 on a dull piano (weak upper partials). Once the
     * chord-mates' shared overtones are set aside, only B4's fundamental clearly jumps. It must still count,
     * thanks to the hammer attack; the same lone partial without an attack (beating) must not.
     */
    @Test fun dullChordNoteCountsOnItsFundamentalWithAnAttack() {
        val notes = listOf(SynthNote(64, 200.0, 2600.0), SynthNote(67, 600.0, 2200.0), SynthNote(71, 1500.0))
        val audio = PianoSynth.render(notes, 2800.0, rolloff = 2.5)
        val m = NoteMatcher(fs)
        m.group = setOf(64, 67, 71)
        val heardAt = mutableMapOf<Int, Double>()
        var i = 0
        while (i < audio.size) {
            m.expected = setOf(64, 67, 71) - heardAt.keys
            m.process(audio.copyOfRange(i, minOf(audio.size, i + 480))).filterIsInstance<MicEvent.Strike>()
                .forEach { heardAt[it.midi] = it.sample * 1000.0 / fs }
            i += 480
        }
        println("dull E minor: " + heardAt.entries.joinToString { "${noteName(it.key)}@${it.value.toInt()}ms" })
        assertEquals(setOf(64, 67, 71), heardAt.keys)
        assertTrue("B4 at ${heardAt[71]}", heardAt.getValue(71) in 1500.0..1700.0)
    }

    /**
     * Roy's false greens: a faint note left ringing from the previous chord wobbles by a few dB while room sounds
     * (thumps, voices) supply "attacks". It must not count as played again. The same audio with the faint-note gate
     * switched off shows the gate is what stops it.
     */
    @Test fun faintRingingNoteWobblingInARoomDoesNotCount() {
        var withGate = 0; var withoutGate = 0
        for (midi in listOf(50, 56, 62, 65, 69)) for (seed in 1L..4L) {
            val rnd = java.util.Random(seed)
            val audio = FloatArray(fs * 6)
            val f0 = midiFreq(midi)
            for (h in 1..5) {                                  // the leftover note: about -55 dB, wobbling +-4 dB at 1-2 Hz
                val a = Math.pow(10.0, -55.0 / 20) / Math.pow(h.toDouble(), 1.1)
                val fh = partialFreq(midi, h); val ph = rnd.nextDouble() * 6.28; val wob = 1.0 + rnd.nextDouble()
                for (i in audio.indices) {
                    val t = i.toDouble() / fs
                    audio[i] += (a * (1 + 0.6 * kotlin.math.sin(2 * Math.PI * wob * t)) * kotlin.math.sin(2 * Math.PI * fh * t + ph)).toFloat()
                }
            }
            for (i in audio.indices) audio[i] += (rnd.nextGaussian() * Math.pow(10.0, -58.0 / 20)).toFloat()   // room noise
            for (k in 0 until 8) {                                                                       // room thumps
                val at = (0.6 + k * 0.7 + rnd.nextDouble() * 0.2) * fs
                for (j in 0 until fs / 100) audio[(at + j).toInt()] += (rnd.nextGaussian() * Math.pow(10.0, -38.0 / 20) * kotlin.math.exp(-j / 80.0)).toFloat()
            }
            withGate += strikes(listen(audio) { setOf(midi) }).size
            withoutGate += strikes(listen(audio, MatcherConfig(freshJumpDb = -999.0, roomMarginDb = -999.0)) { setOf(midi) }).size
        }
        println("faint ringing note: $withGate false strikes with the gate, $withoutGate without")
        assertEquals("a faint wobbling leftover note counted as played", 0, withGate)
        assertTrue("the gate should be what rejects it (without it: $withoutGate)", withoutGate > 0)
    }

    /** Wait mode on a chord: each note leaves the expected set once heard. Returns what was heard. */
    private fun heardInChord(audio: FloatArray, chord: Set<Int>): Set<Int> {
        val m = NoteMatcher(fs)
        m.group = chord
        val heard = mutableSetOf<Int>()
        var i = 0
        while (i < audio.size) {
            m.expected = chord - heard
            m.process(audio.copyOfRange(i, minOf(audio.size, i + 480))).filterIsInstance<MicEvent.Strike>().forEach { heard += it.midi }
            i += 480
        }
        return heard
    }

    /**
     * Roy's Alicia: C3 and C6 together, C6 much softer. C6 is C3's 8th partial, and C3's higher partials land on
     * C6's checks for a lower note and its semitone neighbours, so C6 was never heard and wait mode stuck. Three and
     * four octaves up count as doublings too. Slack strings (Roy's Yamaha, bScale ~0.2) and stiff ones.
     */
    @Test fun wideOctaveChordHearsItsTopNote() {
        for (b in listOf(0.2, 1.0)) for (top in listOf(84, 96)) {
            val audio = PianoSynth.render(listOf(SynthNote(48, 500.0), SynthNote(top, 500.0, gainDb = -31.0)), 2000.0, bScale = b, partials = 24)
            assertEquals("C3 + ${noteName(top)}, bScale $b", setOf(48, top), heardInChord(audio, setOf(48, top)))
        }
    }

    @Test fun octaveDoublingsInAChordAreCredited() {
        for (chord in listOf(listOf(55, 59, 62, 67), listOf(57, 69, 72, 76, 79), listOf(53, 60, 63, 65, 69), listOf(48, 60, 64, 67))) {
            val audio = PianoSynth.render(chord.map { SynthNote(it, 500.0) }, 2000.0)
            val m = NoteMatcher(fs)
            m.group = chord.toSet()
            val heard = mutableSetOf<Int>()
            var i = 0
            while (i < audio.size) {
                m.expected = chord.toSet() - heard
                m.process(audio.copyOfRange(i, minOf(audio.size, i + 480))).filterIsInstance<MicEvent.Strike>().forEach { heard += it.midi }
                i += 480
            }
            assertEquals("chord ${chord.map { noteName(it) }}", chord.toSet(), heard)
        }
    }

    @Test fun repeatedNoteIsHeardAgain() {
        val audio = PianoSynth.render(listOf(SynthNote(64, 300.0, 250.0), SynthNote(64, 700.0, 250.0), SynthNote(64, 1100.0)), 2500.0)
        assertEquals(3, strikes(listen(audio) { setOf(64) }).size)
    }

    @Test fun softNotesCountAndInaudibleOnesDoNot() {
        val soft = PianoSynth.render(listOf(SynthNote(60, 500.0, gainDb = -40.0)), 2000.0, noiseDb = -80.0)
        assertEquals(1, strikes(listen(soft) { setOf(60) }).size)
        val whisper = PianoSynth.render(listOf(SynthNote(60, 500.0, gainDb = -75.0)), 2000.0, noiseDb = -90.0)
        assertEquals(0, strikes(listen(whisper) { setOf(60) }).size)
    }

    @Test fun roomNoiseStaysSilent() {
        val audio = PianoSynth.render(emptyList(), 30_000.0, noiseDb = -55.0)
        // add mains hum and its harmonics
        for (i in audio.indices) for (h in 1..4) audio[i] += (0.003 / h * kotlin.math.sin(2 * Math.PI * 60 * h * i / fs)).toFloat()
        val ev = listen(audio) { setOf(48, 60, 64, 67, 76) }
        assertEquals("events in 30 s of noise: $ev", 0, ev.size)
    }

    @Test fun costPerHop() {
        val audio = PianoSynth.render(listOf(SynthNote(45, 0.0), SynthNote(52, 200.0)), 10_000.0)
        val m = NoteMatcher(fs)
        m.expected = setOf(33, 40, 45, 52)                 // worst case: four bass notes with 8192 windows
        m.process(audio.copyOfRange(0, fs))                 // warm up the JIT
        val t0 = System.nanoTime()
        m.process(audio)
        val perHop = (System.nanoTime() - t0) / 1e6 / (audio.size / m.hop)
        println("matcher cost: %.3f ms per %d-sample hop (%.1f ms of audio), %.0f%% of one core on this PC"
            .format(perHop, m.hop, m.hop * 1000.0 / fs, 100 * perHop / (m.hop * 1000.0 / fs)))
    }
}
