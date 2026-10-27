package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.RoomNoise
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MicHearingTest {
    /** Feeds [db] readings every [stepMs] from [from] to [to] (exclusive); returns the first cause and when. */
    private fun MicHearing.feed(from: Long, to: Long, stepMs: Long = 100, db: (Long) -> Double): Pair<Long, NotHeard>? {
        var t = from
        while (t < to) { level(t, db(t))?.let { return t to it }; t += stepMs }
        return null
    }

    @Test fun silenceForSixSecondsBeforeAnythingIsHeard() {
        val h = MicHearing()
        val (at, cause) = h.feed(0, 10_000) { -75.0 }!!
        assertEquals(NotHeard.SILENCE, cause)
        assertEquals(MicHearing.SILENCE_MS, at)
        assertTrue(h.shownInStep)
        assertNull("once per step", h.feed(10_000, 30_000) { -75.0 })
    }

    @Test fun noiseWithoutNotesForFourSeconds() {
        val h = MicHearing()
        // Talking: a jump with no note in it about every 700 ms, the level up and down around -50.
        var t = 0L
        var got: Pair<Long, NotHeard>? = null
        while (t < 8_000 && got == null) {
            if (t % 700 == 0L) h.unpitched(t)
            got = h.level(t, if (t % 700 < 300) -48.0 else -62.0)?.let { t to it }
            t += 100
        }
        assertEquals(NotHeard.NOISE, got!!.second)
        assertEquals(MicHearing.NOISE_MS, got.first)
    }

    @Test fun aLoudSteadyRoomIsNoiseWithoutAnyJump() {
        // A vacuum cleaner: no jumps, but loud all the time.
        assertEquals(NotHeard.NOISE to MicHearing.NOISE_MS, MicHearing().feed(0, 8_000) { -40.0 }?.let { it.second to it.first })
    }

    @Test fun aQuietRoomToneIsNeitherNoiseNorSilence() {
        // Roy's room between notes: -58 to -62 dBFS, no jumps. A learner thinking there is told nothing.
        assertNull(MicHearing().feed(0, 30_000) { t -> if (t % 400 < 200) -58.0 else -62.0 })
    }

    @Test fun aPitchedOnsetStartsTheClockAgainAndEndsSilenceForTheStep() {
        val h = MicHearing()
        assertNull(h.feed(0, 5_000) { -75.0 })
        h.pitched()                                   // a key heard at 5 s
        // Quiet again after it: silence no longer counts in this step ("nothing heard yet" is over).
        assertNull(h.feed(5_000, 20_000) { -75.0 })
        // A new step: it counts again, from the step's start.
        h.newStep()
        assertEquals(NotHeard.SILENCE, h.feed(20_000, 30_000) { -75.0 }?.second)
    }

    @Test fun keysPlayedEveryFewSecondsInANoisyRoomNeverTripIt() {
        val h = MicHearing()
        var t = 0L
        while (t < 30_000) {
            if (t % 3_000 == 0L) h.pitched()
            if (t % 600 == 0L) h.unpitched(t)
            assertNull("at $t", h.level(t, -50.0))
            t += 100
        }
    }

    @Test fun theClockStopsWhileHeld() {
        // A demo or the voice every three seconds: never four seconds of noise, never six of silence.
        val h = MicHearing()
        var t = 0L
        while (t < 30_000) {
            if (t % 3_000 == 0L) h.hold()
            if (t % 500 == 0L) h.unpitched(t)
            assertNull(h.level(t, -40.0))
            t += 100
        }
    }

    @Test fun jumpsBeforeTheStretchDoNotCount() {
        val h = MicHearing()
        repeat(5) { h.unpitched(it * 100L) }          // heard while held (nothing feeds the level yet)
        assertNull(h.feed(1_000, 4_900) { -62.0 })
    }

    @Test fun neverMoreThanTwiceAMinuteAcrossSteps() {
        val limit = HearingLimit()
        var shown = 0
        var t = 0L
        // A new step every 7 s, each silent: the hint would come in every one.
        for (step in 0 until 8) {
            val h = MicHearing(limit)
            h.feed(t, t + 7_000) { -80.0 }?.let { shown++ }
            t += 7_000
        }
        // 56 s: two in the first minute.
        assertEquals(2, shown)
        val h = MicHearing(limit)
        assertEquals("a minute after the first, one more", NotHeard.SILENCE, h.feed(t, t + 20_000) { -80.0 }?.second)
    }

    @Test fun theCopy() {
        assertEquals("I can't hear the piano clearly.", MicHearing.TITLE)
        assertEquals("It's a bit noisy here. Try moving the phone closer to the piano.", NotHeard.NOISE.line)
        assertEquals("I can't hear anything yet. Is the piano's volume up?", NotHeard.SILENCE.line)
        assertEquals("Move the phone closer", MicHearing.CLOSER)
        assertEquals("Use the on-screen keys", MicHearing.TOUCH)
        for (s in listOf(MicHearing.TITLE, MicHearing.NOISE_LINE, MicHearing.SILENCE_LINE, MicHearing.CLOSER, MicHearing.TOUCH) +
            RoomCheck.Verdict.entries.flatMap { listOf(it.title, it.line) } + RoomCheck.PROMPT)
            assertFalse(s, s.contains(Char(0x2014)) || s.contains(Char(0x2013)) || s.contains(" - "))
        assertEquals(NotHeard.NOISE, NotHeard.of("noise")); assertEquals(NotHeard.SILENCE, NotHeard.of("silence")); assertNull(NotHeard.of("x"))
    }

    // ---- through the note matcher, as the player feeds it ----

    /**
     * Replays [audio] the way the player does: the matcher listening for [expected], a level reading every 6 hops (64 ms,
     * the meter's pace), the matcher's noise as jumps, strikes and wrong keys as pitched. [rearm]: start a new step after each
     * hint, so every moment it would show is listed. Returns (ms, cause) for each.
     */
    private fun replay(audio: FloatArray, rate: Int, expected: (Int) -> Set<Int>, rearm: Boolean = true, strict: Boolean = false,
                       onStrike: (Int) -> Unit = {}): List<Pair<Long, NotHeard>> {
        val m = NoteMatcher(rate)
        m.strictKeys = strict
        val h = MicHearing(HearingLimit(max = Int.MAX_VALUE))
        val out = mutableListOf<Pair<Long, NotHeard>>()
        fun ms(i: Int) = i * 1000L / rate
        var noiseAt = -1L
        m.noise = { s -> noiseAt = s * 1000 / rate }
        var i = 0
        var hops = 0
        while (i + 512 <= audio.size) {
            m.expected = expected(i); m.group = m.expected
            for (e in m.process(audio.copyOfRange(i, i + 512))) when (e) {
                is MicEvent.Strike -> { h.pitched(); onStrike(e.midi) }
                is MicEvent.Wrong -> h.pitched()
            }
            if (noiseAt >= 0) { h.unpitched(noiseAt); noiseAt = -1 }
            i += 512
            if (++hops % 6 == 0) h.level(ms(i), m.levelDb)?.let { out += ms(i) to it; if (rearm) h.newStep() }
        }
        return out
    }

    private val ceg = setOf(48, 60, 64, 67, 72)

    /** As a stage or the room check hears it (strict keys): see [RoomNoise] for the player's matcher and this noise. */
    @Test fun talkingWithNoPianoIsNoiseAndNeverAKey() {
        val rate = 48_000
        val noise = RoomNoise.render(20_000.0, -35.0, rate)
        var keys = 0
        val got = replay(noise, rate, { ceg }, strict = true, onStrike = { keys++ })
        assertEquals("no key heard in talking", 0, keys)
        assertTrue("noise first: $got", got.isNotEmpty() && got.first().second == NotHeard.NOISE)
        assertTrue("within 4 to 5 s: ${got.first().first}", got.first().first in 4_000L..5_000L)
        assertTrue("never silence: $got", got.none { it.second == NotHeard.SILENCE })
    }

    @Test fun anInjectedSilenceIsSilenceAtSixSeconds() {
        val got = replay(FloatArray(48_000 * 10), 48_000, { ceg })
        assertEquals(NotHeard.SILENCE, got.first().second)
        assertTrue("${got.first().first}", got.first().first in 6_000L..6_200L)
    }

    @Test fun aSteadyLoudRoomIsNoiseWithoutJumps() {
        val got = replay(RoomNoise.render(12_000.0, -35.0, 48_000, steady = true), 48_000, { ceg }, strict = true)
        assertEquals(NotHeard.NOISE, got.first().second)
        assertTrue("${got.first().first}", got.first().first in 4_000L..4_200L)
    }

    @Test fun aSynthScaleIsNeverNotHeard() {
        val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72).mapIndexed { k, m -> SynthNote(m, 500.0 + k * 900, 800.0) }
        val audio = PianoSynth.render(notes, 500.0 + notes.size * 900 + 500, 48_000)
        var next = 0
        val got = replay(audio, 48_000, { setOf(notes[minOf(next, notes.lastIndex)].midi) }, onStrike = { m -> if (m == notes[minOf(next, notes.lastIndex)].midi) next++ })
        assertEquals("every note heard", notes.size, next)
        assertTrue("$got", got.isEmpty())
    }

    @Test fun theRoomCheck() {
        val clear = RoomCheck(0)
        clear.note(60); clear.note(64); clear.note(67)
        var t = 0L
        while (!clear.done(t)) { clear.level(t, -62.0); t += 100 }
        assertEquals(RoomCheck.Verdict.CLEAR, clear.verdict())

        val noisy = RoomCheck(0)
        noisy.note(60); noisy.note(64); noisy.note(67)
        t = 0L
        while (!noisy.done(t)) { if (t % 800 == 0L) noisy.unpitched(t); noisy.level(t, -50.0); t += 100 }
        assertEquals(RoomCheck.Verdict.NOISY, noisy.verdict())

        val nothing = RoomCheck(0)
        t = 0L
        while (!nothing.done(t)) { nothing.level(t, -75.0); t += 100 }
        assertEquals(RoomCheck.Verdict.TRY_CLOSER, nothing.verdict())

        val one = RoomCheck(0)
        one.note(60); one.note(61)                    // C and a key it does not ask for
        assertEquals(setOf(60), one.heard)
        assertEquals("not all three: closer", RoomCheck.Verdict.TRY_CLOSER, one.verdict())
        assertEquals(0.5, RoomCheck(1_000).progress(6_000), 1e-9)
    }

    // ---- Roy's recordings (gitignored; scripts\pull-fixtures.ps1) ----

    private val dir = File("src/test/resources/fixtures")

    /** As NoiseWatchTest replays them: the next truth note expected (given up 500 ms after it sounded), else C3 to C5. */
    private fun replayFixture(name: String): List<Pair<Long, NotHeard>> {
        val audio = Wav.read(File(dir, "$name.wav"))
        val fs = audio.sampleRate
        val truth = File(dir, "$name.notes").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(",").map(String::trim) }.filter { it[2].toInt() > 0 && it[0].toLong() >= 0 }
            .map { it[0].toLong() to it[1].toInt() }.sortedBy { it.first }
        var gi = 0
        return replay(audio.samples, fs, { i ->
            if (truth.isEmpty()) ceg else {
                while (gi < truth.size && i > truth[gi].first + fs / 2) gi++
                if (gi < truth.size) setOf(truth[gi].second) else emptySet()
            }
        }, onStrike = { m -> if (gi < truth.size && m == truth[gi].second) gi++ })
    }

    @Test fun tunedOnRoysRoom() {
        if (!File(dir, "session-155837.wav").exists()) { println("no fixtures (gitignored); pull them with scripts\\pull-fixtures.ps1"); return }
        val all = dir.listFiles { f -> f.name.startsWith("session-") && f.extension == "wav" && f.length() > 48_000 * 2 * 3 }!!.map { it.nameWithoutExtension }.sorted()
        val found = all.associateWith { replayFixture(it) }
        for ((name, got) in found) println("$name: ${got.joinToString { "${it.first / 1000.0} s ${it.second.wire}" }.ifEmpty { "nothing" }}")
        // No recording of Roy's room is ever silence: the room always has something in it.
        for ((name, got) in found) assertTrue("$name: silence ${got}", got.none { it.second == NotHeard.SILENCE })
        // The TV on, nothing played: noise, within about 4 to 6 s (and the player's matcher credits no key in it).
        val tv = found.getValue("session-160604")
        assertNotNull(tv.firstOrNull())
        assertTrue("TV: ${tv.first()}", tv.first().second == NotHeard.NOISE && tv.first().first in 4_000L..6_500L)
    }

    /**
     * The scale with MIDI truth and talking in the room: while Roy plays, no hint between his notes. The one it gives (13.8 s)
     * is in the only pause of four seconds or more (9.7 to 14.9 s, talking meanwhile): noise without notes, as the rule says.
     */
    @Test fun notWhileRoyPlaysHisScale() {
        if (!File(dir, "session-155837.wav").exists()) return
        val truth = File(dir, "session-155837.notes").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(",").map(String::trim) }.filter { it[2].toInt() > 0 && it[0].toLong() >= 0 }.map { it[0].toLong() * 1000 / 48_000 }.sorted()
        val pauses = truth.zipWithNext().filter { (a, b) -> b - a >= MicHearing.NOISE_MS }.map { (a, b) -> a..b }
        val got = replayFixture("session-155837").filter { it.first in truth.first()..truth.last() }
        assertEquals("one pause of 4 s or more: $pauses", 1, pauses.size)
        for (h in got) assertTrue("a hint at ${h.first} ms while he played: $got", pauses.any { h.first in it })
    }
}
