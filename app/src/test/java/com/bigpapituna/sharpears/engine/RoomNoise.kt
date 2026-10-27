package com.bigpapituna.sharpears.engine

import java.util.Random
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A room with talking in it, for tests (2.15, "could not hear"): bursts of filtered noise the length of syllables (120 to
 * 320 ms) with short gaps (60 to 260 ms), each burst's level [levelDb] (dBFS, RMS) give or take 4 dB, over a faint floor
 * 35 dB under it; or, [steady], one level wobbling 3 dB at half a hertz (a vacuum, a fan). No pitch in it. Deterministic
 * for a [seed]. Not meant to sound like anyone.
 *
 * Harsher than a real room for the player's matcher: Gaussian noise bursts pass for a key there about once a second (strict
 * keys, the stages' and the room check's, credit none), where Roy's TV recording (session-160604) passes for none in either.
 */
object RoomNoise {
    fun render(lengthMs: Double, levelDb: Double = -35.0, sampleRate: Int = 48_000, seed: Long = 7, attackMs: Double = 15.0, lowHz: Double = 2_500.0, steady: Boolean = false): FloatArray {
        val out = FloatArray((lengthMs / 1000 * sampleRate).toInt())
        val rnd = Random(seed)
        // White noise through two one-pole low-passes (a soft, voice-like tilt) and one high-pass at about 120 Hz.
        val lp = exp(-2 * Math.PI * lowHz / sampleRate)
        val hp = exp(-2 * Math.PI * 120.0 / sampleRate)
        var a = 0.0; var b = 0.0; var hpState = 0.0; var prev = 0.0
        val floor = 10.0.pow((levelDb - 35) / 20)
        var i = 0
        var burstGain = 0.0
        var burstEnd = 0
        var nextBurst = (sampleRate * 0.05).toInt()
        val attack = (sampleRate * attackMs / 1000).toInt().coerceAtLeast(1)
        var burstStart = 0
        // Filtered noise has a lower RMS than its source: measured once so a burst lands at [levelDb].
        val norm = 1.0 / sqrt(filteredPower(lp, hp))
        while (i < out.size) {
            if (i >= nextBurst) {
                burstStart = i
                burstEnd = i + (sampleRate * (0.12 + 0.2 * rnd.nextDouble())).toInt()
                nextBurst = burstEnd + (sampleRate * (0.06 + 0.2 * rnd.nextDouble())).toInt()
                burstGain = 10.0.pow((levelDb + (rnd.nextDouble() * 8 - 4)) / 20)
            }
            val w = rnd.nextGaussian()
            a = lp * a + (1 - lp) * w
            b = lp * b + (1 - lp) * a
            hpState = hp * (hpState + b - prev); prev = b
            val env = if (steady) 10.0.pow((levelDb + 3 * kotlin.math.sin(2 * Math.PI * 0.5 * i / sampleRate)) / 20)
                else if (i < burstEnd) burstGain * minOf(1.0, (i - burstStart + 1).toDouble() / attack) else 0.0
            out[i] = ((env + floor) * hpState * norm).toFloat()
            i++
        }
        return out
    }

    /** The power of unit white noise after the filters, measured on a fixed stretch. */
    private fun filteredPower(lp: Double, hp: Double): Double {
        val rnd = Random(99)
        var a = 0.0; var b = 0.0; var h = 0.0; var prev = 0.0; var sum = 0.0
        val n = 48_000
        for (k in 0 until n) {
            val w = rnd.nextGaussian()
            a = lp * a + (1 - lp) * w; b = lp * b + (1 - lp) * a
            h = hp * (h + b - prev); prev = b
            if (k >= 4_800) sum += h * h
        }
        return sum / (n - 4_800)
    }
}
