package com.bigpapituna.sharpears.engine.playground

import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt

/** A note heard: its pitch as a (fractional) midi number, where it began, and how clearly it repeated (0 to 1). */
data class Heard(val midi: Double, val atSample: Long, val clarity: Double) {
    /** The nearest key. */
    val key get() = midi.roundToInt()
}

data class PitchConfig(
    val frameMs: Double = 5.0,
    /**
     * A note starts with a jump in at least one of four bands (under 250 Hz, to 1 kHz, to 4 kHz, above): that band's
     * frame stands this far over its loudest frame of the 20 to 60 ms before... A low note after a high one, still
     * ringing, is new in the low band; a high one after a low one, in the upper bands.
     */
    val riseDb: Double = 9.0,
    /** ...and over the frame just before it: a hammer is fast, a voice or a swell is not. */
    val jumpDb: Double = 5.0,
    /** The band that jumps is at least this loud... */
    val bandFloorDb: Double = -66.0,
    /** ...and the whole sound at least this loud (dBFS of a 5 ms frame). */
    val floorDb: Double = -55.0,
    /** The pitch is measured this long after the start, once the hammer's knock has gone. */
    val settleMs: Double = 40.0,
    /** On this much sound (YIN's window). */
    val windowMs: Double = 40.0,
    /** YIN: the first dip of the normalised difference under this is the period... */
    val threshold: Double = 0.15,
    /** ...and with no dip under it, the deepest one is used if it is under this; otherwise the sound has no pitch. */
    val maxAperiodicity: Double = 0.35,
    /** Notes at least this far apart. */
    val gapMs: Double = 120.0,
    val lowestHz: Double = 26.0,
    val highestHz: Double = 4500.0,
)

/**
 * Hears which note was just played, from raw microphone audio, without being told what to expect (2.1, the Playground's
 * high-and-low game and key hunts, where any key of a set will do). The note matcher checks a list of expected keys:
 * its cost grows with every one (engine/Transport.kt MIC_ANY_OF_LIMIT), and asked about many keys at once it now and then
 * credits one the struck note merely lit up. Here a new note is found by its attack (a sudden jump in one of four bands),
 * then its pitch is measured once with YIN (de Cheveigne and Kawahara, 2002) on 40 ms of its sound, so the key named is
 * the key struck. Work is done only when something is struck. Noise (a clap, a knock) has no pitch and is ignored.
 *
 * ponytail: a toy-grade pitch for the flying pup and the hunts, good to a semitone across the piano on PianoSynth; a word
 * that starts with a hard consonant can still be heard as a low note. The matcher stays the judge wherever a lesson
 * needs one exact key.
 */
class PitchHeight(val sampleRate: Int = 48_000, val config: PitchConfig = PitchConfig()) {
    private val frameLen = (sampleRate * config.frameMs / 1000).toInt()
    private fun frames(ms: Double) = (ms / config.frameMs).toInt()
    private val history = 32
    private val bands = 4
    private val bandDb = Array(bands) { DoubleArray(history) { -120.0 } }
    private val bandAcc = DoubleArray(bands)
    private val levelDb = DoubleArray(history) { -120.0 }
    private val filters = listOf(
        listOf(Biquad(250.0, sampleRate, high = false)),
        listOf(Biquad(250.0, sampleRate, high = true), Biquad(1000.0, sampleRate, high = false)),
        listOf(Biquad(1000.0, sampleRate, high = true), Biquad(4000.0, sampleRate, high = false)),
        listOf(Biquad(4000.0, sampleRate, high = true)),
    )
    private val window = (sampleRate * config.windowMs / 1000).toInt()
    private val maxLag = (sampleRate / config.lowestHz).toInt()
    private val minLag = (sampleRate / config.highestHz).toInt().coerceAtLeast(2)
    private val ring = FloatArray(Integer.highestOneBit((window + maxLag + sampleRate / 4) * 2))
    private var written = 0L
    private var frameAcc = 0.0
    private var frameFill = 0
    private var frame = -1L
    private var lastOnset = Long.MIN_VALUE / 2
    /** Sample index where the next measurement is due (an onset waiting for its sound), or -1. */
    private var due = -1L
    private var dueOnset = 0L

    fun reset() {
        written = 0; frameAcc = 0.0; frameFill = 0; frame = -1; lastOnset = Long.MIN_VALUE / 2; due = -1
        bandDb.forEach { it.fill(-120.0) }; bandAcc.fill(0.0); levelDb.fill(-120.0); ring.fill(0f)
        filters.flatten().forEach { it.reset() }
    }

    /** Takes the next [count] samples of [chunk]; returns the notes whose pitch was measured in them. */
    fun process(chunk: FloatArray, count: Int = chunk.size): List<Heard> {
        var out: MutableList<Heard>? = null
        val mask = (ring.size - 1).toLong()
        for (i in 0 until count) {
            val x = chunk[i]
            ring[(written and mask).toInt()] = x
            written++
            if (due >= 0 && written >= due) {
                due = -1
                measure(dueOnset)?.let { (out ?: mutableListOf<Heard>().also { l -> out = l }).add(it) }
            }
            frameAcc += x * x
            for (b in 0 until bands) {
                var y = x.toDouble()
                for (f in filters[b]) y = f.f(y)
                bandAcc[b] += y * y
            }
            if (++frameFill == frameLen) {
                frame++
                val slot = (frame % history).toInt()
                levelDb[slot] = 10 * log10(frameAcc / frameLen + 1e-12)
                for (b in 0 until bands) { bandDb[b][slot] = 10 * log10(bandAcc[b] / frameLen + 1e-12); bandAcc[b] = 0.0 }
                frameAcc = 0.0; frameFill = 0
                onFrame()
            }
        }
        return out ?: emptyList()
    }

    private fun db(b: Int, f: Long) = if (f < 0 || frame - f >= history) -120.0 else bandDb[b][(f % history).toInt()]

    private fun onFrame() {
        val c = config
        if (levelDb[(frame % history).toInt()] < c.floorDb || (frame - lastOnset) * c.frameMs < c.gapMs) return
        val jumped = (0 until bands).any { b ->
            val now = db(b, frame)
            now >= c.bandFloorDb && now - (frames(20.0)..frames(60.0)).maxOf { db(b, frame - it) } >= c.riseDb && now - db(b, frame - 1) >= c.jumpDb
        }
        if (!jumped) return
        lastOnset = frame
        dueOnset = frame * frameLen
        due = dueOnset + (sampleRate * c.settleMs / 1000).toLong() + window + maxLag
    }

    /** YIN on the window [settleMs] after [onset]. */
    private fun measure(onset: Long): Heard? {
        val c = config
        val start = onset + (sampleRate * c.settleMs / 1000).toLong()
        val mask = (ring.size - 1).toLong()
        val x = FloatArray(window + maxLag) { ring[((start + it) and mask).toInt()] }
        // Difference function, then its cumulative mean normalised form.
        val d = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (j in 0 until window) { val e = x[j] - x[j + lag]; sum += e * e }
            d[lag] = sum
        }
        val nd = DoubleArray(maxLag + 1).also { it[0] = 1.0 }
        var running = 0.0
        for (lag in 1..maxLag) { running += d[lag]; nd[lag] = if (running == 0.0) 1.0 else d[lag] * lag / running }
        // The first dip under the threshold, followed down to its bottom; failing that the deepest dip, if deep enough.
        var lag = (minLag..maxLag).firstOrNull { nd[it] < c.threshold }
            ?: (minLag..maxLag).minBy { nd[it] }.takeIf { nd[it] < c.maxAperiodicity }
            ?: return null
        while (lag < maxLag && nd[lag + 1] < nd[lag]) lag++
        // A parabola through the dip and its neighbours puts the period between samples.
        val t = if (lag in 1 until maxLag) {
            val a = nd[lag - 1]; val b = nd[lag]; val cc = nd[lag + 1]
            val den = a - 2 * b + cc
            lag + if (den > 0) 0.5 * (a - cc) / den else 0.0
        } else lag.toDouble()
        val hz = sampleRate / t
        return Heard(69 + 12 * log2(hz / 440.0), onset, (1 - nd[lag]).coerceIn(0.0, 1.0))
    }
}

/**
 * Where the pup flies for a note (0 = on the grass, 1 = the top of the sky): low A to the C three octaves over middle C
 * spread evenly, so every octave a child can reach moves him the same distance; anything beyond sits at the edge.
 */
fun pupHeight(midi: Double): Float = ((midi - 33.0) / (96.0 - 33.0)).toFloat().coerceIn(0f, 1f)
