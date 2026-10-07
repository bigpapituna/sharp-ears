package com.bigpapituna.sharpears.engine

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun midiFreq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

/**
 * Approximate piano string inharmonicity coefficient B, so partial h sits at h*f0*sqrt(1+B*h^2).
 * Rises from ~0.00016 in the bass to ~0.009 at the top. Real pianos vary; the matcher only needs to be close.
 */
fun inharmonicity(midi: Int) = 10.0.pow(-3.8 + (midi - 21) * 0.02)

fun partialFreq(midi: Int, h: Int, bScale: Double = 1.0) = h * midiFreq(midi) * sqrt(1 + bScale * inharmonicity(midi) * h * h)

data class SynthNote(val midi: Int, val startMs: Double, val durationMs: Double = 900.0, val gainDb: Double = -14.0)

/**
 * A rough piano: inharmonic partials with fast attack, register-dependent decay (higher partials die
 * faster), a weak fundamental in the bass, damper release at note end, plus white noise.
 * Used by tests and by the debug audio injector; it is not meant to sound good.
 */
object PianoSynth {
    fun render(
        notes: List<SynthNote>,
        lengthMs: Double,
        sampleRate: Int = 48_000,
        noiseDb: Double = -70.0,
        bScale: Double = 1.4,          // deliberately off from the matcher's model
        seed: Long = 1,
    ): FloatArray {
        val out = FloatArray((lengthMs / 1000 * sampleRate).toInt())
        val rnd = Random(seed)
        for (n in notes) {
            val f0 = midiFreq(n.midi)
            val amp = 10.0.pow(n.gainDb / 20)
            val t60 = (3.0 * sqrt(261.6 / f0)).coerceIn(0.6, 8.0)
            val start = (n.startMs / 1000 * sampleRate).toInt()
            val release = start + (n.durationMs / 1000 * sampleRate).toInt()
            val end = minOf(out.size, release + (0.4 * sampleRate).toInt())
            for (h in 1..10) {
                val f = partialFreq(n.midi, h, bScale)
                if (f > 0.45 * sampleRate) break
                var a = amp / h.toDouble().pow(1.1)
                if (n.midi < 48 && h == 1) a *= 0.4                       // thin bass fundamental
                val tau = t60 / 6.9 / (1 + 0.25 * (h - 1))
                // Rotating phasor instead of sin() per sample.
                val w = 2 * PI * f / sampleRate
                val cw = cos(w); val sw = sin(w)
                val phase = rnd.nextDouble() * 2 * PI
                var re = cos(phase); var im = sin(phase)
                for (i in start until end) {
                    val t = (i - start).toDouble() / sampleRate
                    val attack = minOf(1.0, t / 0.004)
                    val damp = if (i < release) 1.0 else exp(-(i - release).toDouble() / sampleRate / 0.06)
                    out[i] += (a * attack * exp(-t / tau) * damp * im).toFloat()
                    val r = re * cw - im * sw
                    im = re * sw + im * cw; re = r
                }
            }
        }
        val noise = 10.0.pow(noiseDb / 20)
        for (i in out.indices) out[i] += (rnd.nextGaussian() * noise).toFloat()
        return out
    }
}
