package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.noteName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The tuner's pitch detection. YIN (de Cheveigne and Kawahara, 2002) finds the period of the sound over a
 * 2048..4096 sample window; a short spectral search around that pitch then pins down the fundamental itself,
 * which matters on a piano: its upper partials run sharp (inharmonicity), and a tuner should show the note
 * the string is tuned to, not an average of its overtones.
 * ponytail: single pitch only (no chords), 55..2200 Hz; upgrade: a peak tracker for chords, or an FFT for the
 * difference function if the 2 ms analysis ever shows up in a profile.
 */

/** [midi] is the nearest note; [cents] how far the sound is from it (-50..+50, positive = sharp). */
data class PitchResult(val freq: Double, val midi: Int, val cents: Double, val confidence: Double) {
    /** "A4" style name with the octave; see [noteLetter] for the letter alone. */
    val name get() = noteName(midi)
    val noteLetter get() = noteName(midi).dropLast(1)
}

fun midiToFreq(midi: Double, a4: Double = 440.0) = a4 * 2.0.pow((midi - 69) / 12.0)

/** Frequency to (nearest MIDI note, cents off). */
fun freqToNote(freq: Double, a4: Double = 440.0): Pair<Int, Double> {
    val exact = 69 + 12 * ln(freq / a4) / ln(2.0)
    val nearest = exact.roundToInt()
    return nearest to (exact - nearest) * 100
}

class PitchDetector(
    val sampleRate: Int = 48_000,
    val a4: Double = 440.0,
    val minHz: Double = 55.0,
    val maxHz: Double = 2200.0,
    /** YIN's threshold on the normalised difference: lower is stricter. */
    val threshold: Double = 0.15,
    /** Frames quieter than this (RMS, full scale 1.0) are silence. */
    val silenceRms: Double = 0.002,
) {
    private val integration = 2048
    private val tauMin = (sampleRate / maxHz).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minHz).toInt()
    /** Samples needed for one detection. */
    val needed = integration + tauMax + 2

    private val diff = DoubleArray(tauMax + 2)
    private val cmnd = DoubleArray(tauMax + 2)
    private var hann = DoubleArray(0)

    /** The pitch of the last [WINDOW] samples of [x] (of its first [count]), or null for silence, noise and unpitched sound. */
    fun detect(x: FloatArray, count: Int = x.size): PitchResult? {
        if (count < needed) return null
        val len = minOf(count, WINDOW)
        val base = count - len
        var energy = 0.0
        for (i in 0 until len) energy += x[base + i].toDouble() * x[base + i]
        if (sqrt(energy / len) < silenceRms) return null

        // Difference function over the integration window, for lags up to the lowest pitch we want.
        val w = integration
        val lagMax = minOf(tauMax, len - w - 1)
        var e0 = 0.0
        for (j in 0 until w) e0 += x[base + j].toDouble() * x[base + j]
        var eLag = e0                                                       // energy of x[base+tau ..< base+tau+w)
        for (tau in 1..lagMax) {
            val drop = x[base + tau - 1].toDouble(); val add = x[base + tau + w - 1].toDouble()
            eLag += add * add - drop * drop
            var r = 0.0
            for (j in 0 until w) r += x[base + j] * x[base + j + tau]
            diff[tau] = (e0 + eLag - 2 * r).coerceAtLeast(0.0)
        }
        // Cumulative mean normalised difference.
        var run = 0.0
        cmnd[0] = 1.0
        for (tau in 1..lagMax) {
            run += diff[tau]
            cmnd[tau] = if (run <= 0.0) 1.0 else diff[tau] * tau / run
        }
        // The first dip under the threshold, followed down to its bottom.
        var tau = -1
        var t = tauMin
        while (t <= lagMax) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= lagMax && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) return null
        val confidence = (1.0 - cmnd[tau]).coerceIn(0.0, 1.0)

        // Parabolic interpolation of the dip for a sub-sample period.
        var period = tau.toDouble()
        if (tau > 1 && tau < lagMax) {
            val a = cmnd[tau - 1]; val b = cmnd[tau]; val c = cmnd[tau + 1]
            val den = a - 2 * b + c
            if (den > 1e-12) period += 0.5 * (a - c) / den
        }
        val coarse = sampleRate / period
        val freq = refine(x, base, len, coarse)
        val (midi, cents) = freqToNote(freq, a4)
        return PitchResult(freq, midi, cents, confidence)
    }

    /**
     * Searches +-40 cents around [coarse] for the strongest sinusoid (Hann window, direct DFT) and
     * interpolates the peak. Falls back to [coarse] unless the peak is clear and inside the search range.
     */
    private fun refine(x: FloatArray, base: Int, len: Int, coarse: Double): Double {
        if (hann.size != len) hann = DoubleArray(len) { 0.5 - 0.5 * cos(2 * PI * it / (len - 1)) }
        val steps = 21
        val centsAt = DoubleArray(steps) { -40.0 + 4.0 * it }
        val mag = DoubleArray(steps)
        for (k in 0 until steps) {
            val f = coarse * 2.0.pow(centsAt[k] / 1200)
            val omega = 2 * PI * f / sampleRate
            val cw = cos(omega); val sw = sin(omega)
            var re = 0.0; var im = 0.0
            var pr = 1.0; var pi = 0.0                                       // e^{-j omega n}, as a rotating phasor
            for (n in 0 until len) {
                val s = x[base + n] * hann[n]
                re += s * pr; im -= s * pi
                val nr = pr * cw + pi * sw
                pi = pi * cw - pr * sw
                pr = nr
            }
            mag[k] = sqrt(re * re + im * im)
        }
        val best = mag.indices.maxBy { mag[it] }
        if (best == 0 || best == steps - 1) return coarse
        val a = ln(mag[best - 1] + 1e-12); val b = ln(mag[best] + 1e-12); val c = ln(mag[best + 1] + 1e-12)
        val den = a - 2 * b + c
        val shift = if (den < -1e-12) 0.5 * (a - c) / den else 0.0           // in steps, -0.5..0.5
        val cents = centsAt[best] + shift * 4.0
        // A fundamental that is missing or buried would peak at a harmonic's skirt; trust YIN then.
        if (abs(cents) > 30) return coarse
        return coarse * 2.0.pow(cents / 1200)
    }

    companion object { const val WINDOW = 4096 }
}

/**
 * Collects raw mic samples (from the audio thread) and hands the last window to the analysis thread.
 * [feed] and [analyze] may be called from different threads.
 */
class PitchTracker(sampleRate: Int = 48_000, a4: Double = 440.0) {
    private val detector = PitchDetector(sampleRate, a4)
    private val ring = FloatArray(PitchDetector.WINDOW * 2)
    private var written = 0L
    private val window = FloatArray(PitchDetector.WINDOW)
    private val lock = Any()

    fun feed(samples: FloatArray, n: Int) {
        synchronized(lock) {
            for (i in 0 until n) { ring[(written % ring.size).toInt()] = samples[i]; written++ }
        }
    }

    /** Pitch of the most recent window, or null for silence or unpitched sound. Call from one analysis thread. */
    fun analyze(): PitchResult? {
        val have: Int
        synchronized(lock) {
            have = minOf(written, window.size.toLong()).toInt()
            val start = written - have
            for (i in 0 until have) window[i] = ring[((start + i) % ring.size).toInt()]
        }
        return detector.detect(window, have)
    }

    fun clear() = synchronized(lock) { written = 0 }
}

/** What the gauge shows: the held note and a steadied needle. */
data class TunerReading(val result: PitchResult, val cents: Double)

/**
 * Steadies raw readings for display: the needle moves with an exponential average while the note stays the same,
 * jumps when the note changes, and the last reading lingers for [holdMs] after the sound stops so the needle
 * does not flicker away between detections.
 */
class PitchSmoother(private val holdMs: Long = 400, private val blend: Double = 0.45) {
    private var current: TunerReading? = null
    private var seenAt = 0L

    fun update(r: PitchResult?, nowMs: Long): TunerReading? {
        if (r != null) {
            val prev = current
            val cents = if (prev != null && prev.result.midi == r.midi) prev.cents + blend * (r.cents - prev.cents) else r.cents
            current = TunerReading(r, cents)
            seenAt = nowMs
        } else if (nowMs - seenAt > holdMs) current = null
        return current
    }

    fun clear() { current = null }
}
