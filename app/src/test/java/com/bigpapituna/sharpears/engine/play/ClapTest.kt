package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.play.ClapAlong.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ClapTest {
    private val rate = 48_000

    /** Room noise at about -70 dBFS, [ms] long. */
    private fun room(ms: Int, seed: Long = 1): FloatArray {
        val r = Random(seed)
        return FloatArray(ms * rate / 1000) { (r.nextGaussian() * 0.0003).toFloat() }
    }

    /** A hand clap: a white-noise burst with a 1 ms rise and an 8 ms decay. */
    private fun clap(into: FloatArray, atMs: Int, amp: Double = 0.4, seed: Long = 7) {
        val r = Random(seed + atMs)
        val start = atMs * rate / 1000
        for (i in 0 until rate / 10) {
            val k = start + i
            if (k >= into.size) break
            val t = i.toDouble() / rate
            val env = minOf(1.0, t / 0.001) * exp(-t / 0.008)
            into[k] += (r.nextGaussian() * amp * env).toFloat()
        }
    }

    /** Feeds [audio] in chunks of [chunk] samples; returns the clap count. */
    private fun count(audio: FloatArray, chunk: Int = 512): Int {
        val c = ClapCounter(rate)
        var n = 0
        var i = 0
        while (i < audio.size) { val k = minOf(chunk, audio.size - i); n += c.process(audio.copyOfRange(i, i + k)); i += k }
        assertEquals(n, c.total)
        return n
    }

    @Test fun oneClapCountsOnce() {
        val a = room(1000); clap(a, 300)
        assertEquals(1, count(a))
        assertEquals(0, count(room(2000)))                                           // the room alone: nothing
    }

    @Test fun clapsCountWhateverTheChunkSize() {
        val a = room(3000); listOf(200, 900, 1600, 2300).forEach { clap(a, it) }
        for (chunk in listOf(1, 37, 256, 512, 4800, a.size)) assertEquals("chunk $chunk", 4, count(a, chunk))
    }

    @Test fun claps300msApartBothCountButAFlamCountsOnce() {
        val apart = room(1500); clap(apart, 200); clap(apart, 520)
        assertEquals(2, count(apart))
        val flam = room(1500); clap(flam, 200); clap(flam, 330)                     // inside the refractory 300 ms
        assertEquals(1, count(flam))
    }

    @Test fun aFastSteadyBeatAt120bpmCountsEveryClap() {
        val a = room(4500); (0 until 8).forEach { clap(a, 300 + it * 500, amp = 0.15 + 0.05 * (it % 3)) }
        assertEquals(8, count(a))
    }

    @Test fun aHissIsNoClap() {
        // Broadband and sudden, but it stays loud: a "shh", a long hiss.
        val a = room(1500); val r = Random(3)
        for (i in (300 * 48) until (900 * 48)) a[i] += (r.nextGaussian() * 0.2).toFloat()
        assertEquals(0, count(a))
    }

    @Test fun aVoiceIsNoClap() {
        // A vowel: 140 Hz with twenty harmonics, a 30 ms swell, 250 ms long, said twice.
        val a = room(1500)
        for (start in listOf(200, 800)) for (i in 0 until 250 * 48) {
            val t = i.toDouble() / rate
            val env = minOf(1.0, t / 0.03) * minOf(1.0, (0.25 - t) / 0.03)
            var v = 0.0
            for (h in 1..20) v += sin(2 * PI * 140 * h * t) / h
            a[start * 48 + i] += (0.15 * env * v).toFloat()
        }
        assertEquals(0, count(a))
    }

    @Test fun pianoNotesAreNoClaps() {
        val notes = listOf(48, 60, 64, 67, 72, 84, 96).mapIndexed { i, m -> SynthNote(m, 300.0 + i * 400, gainDb = -8.0) }
        assertEquals(0, count(PianoSynth.render(notes, 300.0 + notes.size * 400 + 600, noiseDb = -70.0)))
    }

    @Test fun quietClapsUnderTheFloorAreIgnored() {
        val a = room(1000); clap(a, 300, amp = 0.002)
        assertEquals(0, count(a))
    }

    // ---- Clap along: judging claps against the beat ----

    @Test fun speedsAndTolerance() {
        assertEquals(listOf(60, 90, 120), ClapSpeed.entries.map { it.bpm })
        assertEquals(1000L, ClapSpeed.SLOW.beatMs)
        assertEquals(667L, ClapSpeed.WALK.beatMs)
        assertEquals(500L, ClapSpeed.RUN.beatMs)
        assertEquals(150L, ClapAlong(ClapSpeed.SLOW, 0).toleranceMs)
        assertEquals(125L, ClapAlong(ClapSpeed.RUN, 0).toleranceMs)
    }

    @Test fun clapsNearTheBeatAreInTimeAndOthersEarlyOrLate() {
        val g = ClapAlong(ClapSpeed.RUN, startMs = 10_000)
        assertEquals(Verdict.BEFORE_START, g.clap(9_000).verdict)
        assertEquals(Verdict.IN_TIME, g.clap(10_040).verdict)
        assertEquals(Verdict.EXTRA, g.clap(9_990).verdict)                           // the same beat twice
        g.clap(10_480).let { assertEquals(Verdict.IN_TIME, it.verdict); assertEquals(1, it.beat); assertEquals(-20L, it.offMs) }
        assertEquals(Verdict.LATE, g.clap(11_200).verdict)                           // 200 ms after beat 2
        assertEquals(Verdict.EARLY, g.clap(11_300).verdict)                          // 200 ms before beat 3
        assertEquals(2, g.inTime)
        assertTrue(g.hit(0) && g.hit(1) && !g.hit(2))
    }

    @Test fun aRunOfBeatsCheersAndAMissedBeatStartsItAgain() {
        val g = ClapAlong(ClapSpeed.SLOW, startMs = 0)
        val lines = (0 until 4).map { g.clap(it * 1000L + 30); g.tick(it * 1000L + 30); g.cheer() }
        assertEquals(listOf(null, null, null, "You're right with me!"), lines)
        assertEquals(4, g.streak)
        assertFalse(g.tick(4_100))                                                    // beat 4 is still open
        assertTrue(g.tick(4_200))                                                     // closed with no clap
        assertEquals(0, g.streak)
        g.clap(5_000)
        assertEquals(1, g.streak)
        assertEquals(4, g.bestStreak)
        assertNull(g.cheer())
    }
}
