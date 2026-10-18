package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.clickSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The clap counter (lesson 7): synthetic claps against talking, piano notes and the app's own sounds. */
class ClapCounterTest {
    private val fs = 48_000

    /** Feeds [audio] in 10 ms chunks, as the mic does; returns the claps' times in ms. */
    private val traces = mutableListOf<String>()

    private fun count(audio: FloatArray, counter: ClapCounter = ClapCounter(fs)): List<Double> {
        val times = mutableListOf<Double>()
        counter.listener = { times += it.atSample * 1000.0 / fs }
        counter.trace = { traces += it }
        var i = 0; var sum = 0
        while (i < audio.size) { val n = minOf(480, audio.size - i); sum += counter.process(audio.copyOfRange(i, i + n)); i += n }
        assertEquals("process() counts what the listener hears", times.size, sum)
        assertEquals(times.size, counter.total)
        return times
    }

    private fun mix(vararg parts: FloatArray) = FloatArray(parts.maxOf { it.size }) { i -> parts.sumOf { p -> (p.getOrNull(i) ?: 0f).toDouble() }.toFloat() }

    @Test fun countsClapsAtEveryLoudnessAndTempo() {
        val r = Random(3)
        var t = 600.0
        val times = List(16) { val at = t; t += 330 + r.nextInt(500); at }
        for (peak in listOf(-30.0, -20.0, -12.0, -6.0)) {
            val heard = count(ClapSynth.render(times, t + 500, peakDb = peak))
            assertEquals("claps at $peak dB: $heard", times.size, heard.size)
            heard.zip(times).forEach { (h, c) -> assertTrue("on time: $h for $c", h in c - 10..c + 10) }
        }
    }

    @Test fun clapsTooCloseTogetherCountOnce() {
        // A flam (two hands a moment apart) and a clap's own echo are one clap.
        val heard = count(ClapSynth.render(listOf(500.0, 620.0, 1500.0, 1700.0, 2600.0), 3200.0))
        assertEquals(listOf(500.0, 1500.0, 2600.0), heard.map { Math.round(it / 100) * 100.0 })
    }

    @Test fun ignoresTalking() {
        assertEquals("speech-shaped noise", 0, count(speechNoise(10_000.0, seed = 1)).size)
        assertEquals("voiced syllables", 0, count(syllables(10_000.0, seed = 2)).size)
        assertEquals("a child's voice", 0, count(syllables(10_000.0, seed = 3, pitch = 290.0)).size)
        assertEquals("loud talking", 0, count(syllables(8_000.0, seed = 4, gainDb = -8.0)).size)
    }

    @Test fun ignoresThePianoTheClickAndThePupsOwnDrum() {
        val notes = listOf(48, 60, 64, 67, 72, 76, 84, 96, 36).mapIndexed { i, m -> SynthNote(m, 400.0 + i * 450, 400.0, gainDb = -8.0) } +
            SynthNote(60, 4800.0) + SynthNote(64, 4800.0) + SynthNote(67, 4800.0)      // a chord
        assertEquals("piano notes", 0, count(PianoSynth.render(notes, 6500.0)).size)
        val clicks = FloatArray(fs * 4).also { a -> for (b in 0 until 8) clickSamples(b % 4 == 0).copyInto(a, (0.3 + b * 0.45).times(fs).toInt()) }
        assertEquals("metronome clicks", 0, count(clicks).size)
        val drum = drumSamples(fs)
        val beats = FloatArray(fs * 5).also { a -> for (b in 0 until 8) drum.copyInto(a, (0.4 + b * 0.6).times(fs).toInt(), 0, minOf(drum.size, a.size - (0.4 + b * 0.6).times(fs).toInt())) }
        assertEquals("the pup's drum", 0, count(beats).size)
    }

    @Test fun hearsClapsOverTalkAndTheDrum() {
        // Lesson 7 as it happens: the drum plays, the child claps with it, someone talks in the background.
        val claps = List(8) { 400.0 + it * 750 }
        val drum = drumSamples(fs, gain = 0.25)
        val beats = FloatArray(fs * 7).also { a -> claps.forEach { c -> val at = ((c + 15) / 1000 * fs).toInt(); drum.copyInto(a, at, 0, minOf(drum.size, a.size - at)) } }
        val heard = count(mix(ClapSynth.render(claps, 7000.0, peakDb = -12.0), beats, syllables(7000.0, seed = 9, gainDb = -34.0)))
        assertEquals("claps at $claps, heard $heard; $traces", claps.size, heard.size)
        // The drum alone, and the talk alone, are not claps.
        assertEquals(0, count(beats).size)
        assertEquals(0, count(syllables(7000.0, seed = 9, gainDb = -34.0)).size)
    }

    @Test fun resetStartsOver() {
        val c = ClapCounter(fs)
        count(ClapSynth.render(listOf(500.0, 1200.0), 1800.0), c)
        assertEquals(2, c.total)
        c.reset()
        assertEquals(0, c.total)
        assertEquals(1, count(ClapSynth.render(listOf(300.0), 900.0), c).size)
    }

    // ---- talking, synthesized ----------------------------------------------------------------------------------------

    /** Noise shaped like speech: a low-mid spectrum whose loudness rises and falls four to six times a second. */
    private fun speechNoise(ms: Double, seed: Long, gainDb: Double = -16.0): FloatArray {
        val r = Random(seed)
        val n = (ms / 1000 * fs).toInt()
        val amp = 10.0.pow(gainDb / 20)
        var lp = 0.0; var lp2 = 0.0
        val a = exp(-2 * PI * 1200 / fs)
        var phase = 0.0
        return FloatArray(n) { i ->
            val rate = 4.0 + 2 * sin(i * 2 * PI / fs * 0.3)
            phase += 2 * PI * rate / fs
            val env = sin(phase).let { s -> if (s > 0) s * s else 0.05 }
            lp = (1 - a) * r.nextGaussian() + a * lp; lp2 = (1 - a) * lp + a * lp2
            (amp * 3 * env * lp2).toFloat()
        }
    }

    /**
     * Talking: syllables of a buzzing voice through two formants, [gainDb] loud (RMS), each led by a consonant: a soft
     * one (a 25 to 45 ms hiss, 14 dB under the vowel) or a hard one (a 12 ms burst, 6 dB under the vowel, the voice
     * starting 20 ms after it, as in "ta" or "ka"). 120 to 300 ms long, with short gaps.
     */
    private fun syllables(ms: Double, seed: Long, pitch: Double = 140.0, gainDb: Double = -16.0): FloatArray {
        val r = Random(seed)
        val out = FloatArray((ms / 1000 * fs).toInt())
        val amp = 10.0.pow(gainDb / 20)
        var t = 200.0
        while (t < ms - 400) {
            val len = 120 + r.nextInt(180)
            val start = (t / 1000 * fs).toInt()
            val f0 = pitch * (0.85 + 0.3 * r.nextDouble())
            val (f1, f2) = listOf(700.0 to 1200.0, 300.0 to 2300.0, 500.0 to 900.0, 400.0 to 1900.0)[r.nextInt(4)]
            val hard = r.nextInt(3) == 0
            // The vowel: a pulse train (glottal buzz) through two resonators, a 25 ms rise and a 40 ms fall, scaled to [amp].
            var y1 = 0.0; var y2 = 0.0; var z1 = 0.0; var z2 = 0.0; var ph = 0.0
            fun coeffs(f: Double, bw: Double) = exp(-PI * bw / fs).let { rr -> Pair(2 * rr * Math.cos(2 * PI * f / fs), -rr * rr) }
            val (a1, a2) = coeffs(f1, 90.0); val (b1, b2) = coeffs(f2, 120.0)
            val vlen = len * fs / 1000
            val vowel = DoubleArray(vlen) { i ->
                ph += f0 * (1 + 0.02 * sin(i * 2 * PI * 5.0 / fs)) / fs
                val pulse = if (ph >= 1) { ph -= 1; 1.0 } else 0.0
                val ya = pulse + a1 * y1 + a2 * y2; y2 = y1; y1 = ya
                val yb = pulse + b1 * z1 + b2 * z2; z2 = z1; z1 = yb
                minOf(1.0, i / (0.025 * fs)) * minOf(1.0, (vlen - i) / (0.04 * fs)) * (ya + 0.6 * yb)
            }
            val rms = sqrt(vowel.sumOf { it * it } / vlen)
            val consMs = if (hard) 12 else 25 + r.nextInt(20)
            val consAmp = amp * 10.0.pow((if (hard) -6.0 else -14.0) / 20)
            for (i in 0 until consMs * fs / 1000) {
                val k = start + i; if (k >= out.size) break
                val e = if (hard) exp(-i.toDouble() / fs / 0.004) * 1.8 else sin(PI * i / (consMs * fs / 1000.0))
                out[k] += (consAmp * e * r.nextGaussian()).toFloat()
            }
            val v0 = start + (consMs + if (hard) 20 else 5) * fs / 1000
            for (i in 0 until vlen) { val k = v0 + i; if (k < out.size) out[k] += (amp * vowel[i] / rms).toFloat() }
            t += consMs + len + 30 + r.nextInt(150)
        }
        for (i in out.indices) out[i] += (r.nextGaussian() * 10.0.pow(-66.0 / 20)).toFloat()
        return out
    }
}
