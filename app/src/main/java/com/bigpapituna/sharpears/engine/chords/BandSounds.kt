package com.bigpapituna.sharpears.engine.chords

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The three drum sounds of the pop band, made from sine and noise at run time like the metronome's clicks (no sample
 * files, nothing recorded): a kick that falls in pitch, a snare of noise over a short tone, a closed hat of bright
 * noise. Noise comes from a fixed seed, so the same sound is made every time and its cached file keeps its name.
 */
object BandSounds {
    const val RATE = 48_000

    fun samples(kit: PopPlay.Kit): FloatArray = when (kit) {
        PopPlay.Kit.KICK -> kick()
        PopPlay.Kit.SNARE -> snare()
        PopPlay.Kit.HAT -> hat()
    }

    private fun noise(seed: Long): () -> Float {
        var s = seed
        return { s = s * 6364136223846793005L + 1442695040888963407L; ((s ushr 40).toInt() / 8388608f) - 1f }
    }

    private fun kick(): FloatArray {
        val n = RATE * 220 / 1000
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val f = 48.0 + 110.0 * exp(-t / 0.028)               // from a thump down to a low note
            phase += 2 * PI * f / RATE
            val click = if (i < 24) 0.3f * (1f - i / 24f) else 0f
            (0.95 * exp(-t / 0.06) * sin(phase)).toFloat() + click
        }
    }

    private fun snare(): FloatArray {
        val n = RATE * 190 / 1000
        val r = noise(7)
        var low = 0f
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            low += 0.35f * (r() - low)                           // a one-pole smoother takes the harshest top off
            val body = sin(2 * PI * 190 * t) * exp(-t / 0.03)
            (0.55 * exp(-t / 0.05) * (0.6f * r() + 0.4f * low) + 0.4 * body).toFloat()
        }
    }

    private fun hat(): FloatArray {
        val n = RATE * 60 / 1000
        val r = noise(11)
        var prev = 0f
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val x = r()
            val high = x - prev                                  // first difference: a plain high-pass
            prev = x
            (0.5 * exp(-t / 0.014) * high).toFloat().coerceIn(-1f, 1f)
        }
    }
}
