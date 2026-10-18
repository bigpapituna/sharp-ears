package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoiseWatchTest {
    @Test fun noiseAndPaintedKeysWhileTheSongWaitsTripItOnce() {
        val w = NoiseWatch()
        for (t in 0L until 6) w.noise(1_000 + t * 500)
        assertNull("noise alone paints nothing: nothing to stop", w.check(4_000))
        w.wrong(4_200)
        assertNull("one painted key is not enough", w.check(4_300))
        w.wrong(4_800)
        val s = w.check(5_000)
        assertNotNull(s)
        assertEquals(6, s!!.noise); assertEquals(2, s.painted)
        assertFalse("only 4 s in: too soon to offer a pause", s.pauseListening)
        assertNull("once per session", w.check(6_000))
        assertTrue(w.offered)
    }

    @Test fun wrongKeysAloneAreALearnerNotARoom() {
        val w = NoiseWatch()
        for (t in 0L until 30) w.wrong(t * 300)
        assertNull(w.check(10_000))
    }

    @Test fun noiseRightAfterRightNotesDoesNotCount() {
        val w = NoiseWatch()
        // Playing along: a right note every 1.5 s, the room jumping between them, a slip now and then.
        var t = 0L
        repeat(40) { w.right(t); w.noise(t + 400); w.noise(t + 900); if (it % 5 == 0) w.wrong(t + 1200); t += 1_500 }
        assertNull(w.check(t))
        assertEquals(0, w.count(t))
    }

    @Test fun oldNoiseLeavesTheWindow() {
        val w = NoiseWatch()
        for (k in 0L until 5) w.noise(k * 1_000)
        w.wrong(5_000); w.wrong(5_500)
        assertNull(w.check(6_000))
        w.noise(70_000)                              // the first five are more than a minute old by now
        w.wrong(70_500); w.wrong(71_000)
        assertNull(w.check(71_000))
        assertEquals(1, w.count(71_000))
    }

    @Test fun pauseListeningWhenNothingWasPlayedForAWhile() {
        val w = NoiseWatch()
        w.right(0)
        for (k in 0L until 6) w.noise(25_000 + k * 200)
        w.wrong(26_500); w.wrong(27_000)
        assertTrue(w.check(27_000)!!.pauseListening)
        val busy = NoiseWatch()
        busy.right(10_000)
        for (k in 0L until 6) busy.noise(15_000 + k * 200)
        busy.wrong(16_500); busy.wrong(17_000)
        assertFalse(busy.check(17_000)!!.pauseListening)
    }

    private val dir = File("src/test/resources/fixtures")

    /**
     * Replays a recording the way the player feeds the watch: expected notes from the MIDI truth (the next truth
     * note, given up 500 ms after it sounded), or C3/C4/E4/G4/C5 for sessions without truth. Returns the offer time in s.
     */
    private fun offerIn(name: String): Double? {
        val wav = File(dir, "$name.wav")
        val audio = Wav.read(wav)
        val fs = audio.sampleRate
        val truth = File(dir, "$name.notes").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(",").map(String::trim) }.filter { it[2].toInt() > 0 && it[0].toLong() >= 0 }
            .map { it[0].toLong() to it[1].toInt() }.sortedBy { it.first }
        val m = NoteMatcher(fs)
        val watch = NoiseWatch()
        m.noise = { s -> watch.noise(s * 1000 / fs) }
        var gi = 0
        var i = 0
        while (i < audio.samples.size) {
            if (truth.isEmpty()) m.expected = setOf(48, 60, 64, 67, 72) else {
                while (gi < truth.size && i > truth[gi].first + fs / 2) gi++
                m.expected = if (gi < truth.size) setOf(truth[gi].second) else emptySet()
                m.group = m.expected
            }
            val n = minOf(480, audio.samples.size - i)
            for (e in m.process(audio.samples.copyOfRange(i, i + n))) when (e) {
                is MicEvent.Strike -> { watch.right(e.sample * 1000 / fs); if (gi < truth.size && e.midi == truth[gi].second) gi++ }
                is MicEvent.Wrong -> watch.wrong(e.sample * 1000 / fs)
            }
            i += n
            watch.check(i * 1000L / fs)?.let { return it.atMs / 1000.0 }
        }
        return null
    }

    @Test fun tunedOnRoysRoom() {
        if (!File(dir, "session-155837.wav").exists()) { println("no fixtures (gitignored); pull them with scripts\\pull-fixtures.ps1"); return }
        // The scale with MIDI truth, talking in the room: 57 noise jumps, no wrong keys. Never offered.
        assertNull(offerIn("session-155837"))
        // TV on, nothing played: 119 noise jumps, nothing painted, nothing to offer.
        assertNull(offerIn("session-160604"))
        // Chord practice and the false-green session: wrong keys painted among the noise. Offered (10 s and 58 s in).
        for (name in listOf("session-163012", "session-164847")) {
            val at = offerIn(name)
            println("$name: offered at $at s")
            assertNotNull("$name: never offered", at)
        }
    }
}
