package com.bigpapituna.sharpears.engine.playground

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One clap: where it began (sample index since the counter started) and how loud its peak was (dBFS of a 5 ms frame). */
data class Clap(val atSample: Long, val peakDb: Double)

data class ClapConfig(
    /**
     * The burst is judged above this frequency, where a clap has most of its energy and the pup's drum, a voice's buzz
     * and most of a piano's sound have little: the drum on the same beat as a clap would otherwise still be ringing
     * when the clap is meant to be over.
     */
    val highPassHz: Double = 1000.0,
    /** Loudness is followed in frames this long. */
    val frameMs: Double = 5.0,
    /** A clap's first frame stands this far above the loudest frame of the 20 to 60 ms before it: it comes out of quiet... */
    val riseDb: Double = 12.0,
    /** ...and this far above the frame just before it: a clap starts in a millisecond, a word or a swell takes tens. */
    val jumpDb: Double = 9.0,
    /** Its peak frame is at least this loud. Claps near the phone read -30 to -6; a distant tap on a table, below. */
    val floorDb: Double = -50.0,
    /** Already 15 to 35 ms after the peak it is this far down: the burst itself lasts only a few milliseconds... */
    val earlyDecayDb: Double = 8.0,
    /**
     * ...and 50 to 90 ms after the peak this far down: a clap is over by then (a room's echo stays well under the direct
     * burst); a word, a piano note and the drum still sound.
     */
    val decayDb: Double = 12.0,
    val tailFromMs: Double = 50.0,
    val tailToMs: Double = 90.0,
    /**
     * A hard consonant is a burst too, but a voice follows it within tens of milliseconds, loud where a clap has already
     * died away: from [voiceHz] up, 50 to 90 ms after the peak, the sound must be this far under the burst's peak.
     */
    val voiceHz: Double = 400.0,
    val voiceDb: Double = 6.0,
    /**
     * A clap is noise: it never repeats itself. Anything with a pitch does (a piano note, a voice, the metronome's click,
     * the pup's drum), so its autocorrelation at a lag of one period comes close to 1. At most this much is allowed,
     * over lags of 1 to 12 ms on the first 21 ms of the burst.
     */
    val maxPeriodicity: Double = 0.5,
    /** Claps at least this far apart (two hands cannot clap faster; it also swallows a clap's own echo). */
    val gapMs: Double = 300.0,
)

/**
 * Counts hand claps in raw microphone audio (2.1, Playground lesson 7 and the play team's Clap along). Onsets only: no
 * pitch, no expected notes. A clap is a short broadband burst out of quiet that is over within a tenth of a second, has
 * no pitch and no voice after it, at least [ClapConfig.gapMs] after the last one, judged above [ClapConfig.highPassHz].
 * Sustained sound (talking, a TV, a held note) never starts one, because the burst has to stand [ClapConfig.riseDb]
 * above what came just before.
 *
 * Feed it the mic's PCM in any chunk size ([process] returns the claps confirmed in that chunk); a clap is confirmed
 * about 110 ms after it starts, once its tail has been heard. [listener] hears each one as well. Not thread-safe: call
 * from one thread (the mic's).
 */
class ClapCounter(val sampleRate: Int = 48_000, val config: ClapConfig = ClapConfig()) {
    /** Called on the processing thread for each clap, as it is confirmed. */
    var listener: ((Clap) -> Unit)? = null
    /** Debug: why a burst was not a clap (on the processing thread). */
    var trace: ((String) -> Unit)? = null
    /** Claps since the start or the last [reset]. */
    var total = 0
        private set

    private val frameLen = (sampleRate * config.frameMs / 1000).toInt()
    private fun frames(ms: Double) = (ms / config.frameMs).toInt()
    private val history = 64                                  // frames of loudness kept (320 ms)
    private val burstDb = DoubleArray(history) { -120.0 }     // above highPassHz
    private val voiceBandDb = DoubleArray(history) { -120.0 } // above voiceHz
    private val ring = FloatArray(Integer.highestOneBit(sampleRate / 2) * 2)   // ~0.7 s of the burst band, for the pitch check
    private val high = listOf(Biquad(config.highPassHz, sampleRate, high = true), Biquad(config.highPassHz, sampleRate, high = true))
    private val voice = Biquad(config.voiceHz, sampleRate, high = true)
    private var written = 0L
    private var burstAcc = 0.0
    private var voiceAcc = 0.0
    private var frameFill = 0
    private var frame = -1L                                   // index of the newest complete frame
    private var lastClapFrame = Long.MIN_VALUE / 2

    private class Candidate(val onsetFrame: Long, var peakFrame: Long, var peakDb: Double)
    private var pending: Candidate? = null

    fun reset() {
        total = 0; written = 0; burstAcc = 0.0; voiceAcc = 0.0; frameFill = 0; frame = -1; lastClapFrame = Long.MIN_VALUE / 2; pending = null
        burstDb.fill(-120.0); voiceBandDb.fill(-120.0); ring.fill(0f); high.forEach { it.reset() }; voice.reset()
    }

    /** Takes the next [count] samples of [chunk]; returns how many claps were confirmed in them. */
    fun process(chunk: FloatArray, count: Int = chunk.size): Int {
        var found = 0
        for (i in 0 until count) {
            val input = chunk[i].toDouble()
            val b = high[1].f(high[0].f(input))                // fourth order: the drum and the buzz drop out
            val v = voice.f(input)
            ring[(written and (ring.size - 1).toLong()).toInt()] = b.toFloat()
            written++
            burstAcc += b * b; voiceAcc += v * v
            if (++frameFill == frameLen) {
                frame++
                val slot = (frame % history).toInt()
                burstDb[slot] = 10 * log10(burstAcc / frameLen + 1e-12)
                voiceBandDb[slot] = 10 * log10(voiceAcc / frameLen + 1e-12)
                burstAcc = 0.0; voiceAcc = 0.0; frameFill = 0
                if (onFrame()) found++
            }
        }
        return found
    }

    private fun db(f: Long, band: DoubleArray = burstDb) = if (f < 0 || frame - f >= history) -120.0 else band[(f % history).toInt()]

    /** One more frame of loudness: start, grow or judge a candidate. True when a clap was confirmed. */
    private fun onFrame(): Boolean {
        val c = config
        val now = db(frame)
        fun burst() = now >= c.floorDb && now - (frames(20.0)..frames(60.0)).maxOf { db(frame - it) } >= c.riseDb && now - db(frame - 1) >= c.jumpDb
        pending?.let { p ->
            if (frame <= p.onsetFrame + 1 && now > p.peakDb) { p.peakDb = now; p.peakFrame = frame }   // the peak, within 10 ms
            // A much louder burst while a quiet one is being judged (a clap right after a consonant) starts over from it.
            else if (now >= p.peakDb + c.riseDb / 2 && burst()) { pending = Candidate(frame, frame, now); return false }
            if (frame < p.onsetFrame + 3 + frames(c.tailToMs)) return false
            pending = null
            return judge(p)
        }
        if ((frame - lastClapFrame) * c.frameMs >= c.gapMs && burst()) pending = Candidate(frame, frame, now)
        return false
    }

    private fun judge(p: Candidate): Boolean {
        val c = config
        // Over fast: the mean power soon after the peak already well under it, and further under by 50 to 90 ms, with
        // no voice coming in after it; and no pitch.
        fun meanDb(fromMs: Double, toMs: Double, band: DoubleArray = burstDb) =
            10 * log10((p.peakFrame + frames(fromMs)..p.peakFrame + frames(toMs)).map { 10.0.pow(db(it, band) / 10) }.average() + 1e-12)
        val early = p.peakDb - meanDb(15.0, 35.0)
        val tail = p.peakDb - meanDb(c.tailFromMs, c.tailToMs)
        val voiced = p.peakDb - meanDb(c.tailFromMs, c.tailToMs, voiceBandDb)
        val reason = when {
            early < c.earlyDecayDb -> "lasts too long (down ${"%.0f".format(java.util.Locale.ROOT, early)} dB after 15 to 35 ms)"
            tail < c.decayDb -> "still sounding (down ${"%.0f".format(java.util.Locale.ROOT, tail)} dB after 50 to 90 ms)"
            voiced < c.voiceDb -> "a voice follows (${"%.0f".format(java.util.Locale.ROOT, voiced)} dB under the burst)"
            else -> periodicity(p.onsetFrame * frameLen).let { r -> if (r > c.maxPeriodicity) "has a pitch (${"%.2f".format(java.util.Locale.ROOT, r)})" else null }
        }
        if (reason != null) { trace?.invoke("not a clap at ${p.onsetFrame * c.frameMs.toInt()} ms, ${"%.0f".format(java.util.Locale.ROOT, p.peakDb)} dB: $reason"); return false }
        lastClapFrame = p.onsetFrame
        total++
        listener?.invoke(Clap(p.onsetFrame * frameLen, p.peakDb))
        return true
    }

    /** Highest normalised autocorrelation over lags of 1 to 12 ms, on the 21 ms of sound from [start]. */
    private fun periodicity(start: Long): Double {
        val n = sampleRate * 21 / 1000
        val lo = sampleRate / 1000; val hi = sampleRate * 12 / 1000
        val mask = (ring.size - 1).toLong()
        val x = FloatArray(n + hi) { ring[((start + it) and mask).toInt()] }
        var best = 0.0
        for (lag in lo..hi) {
            var xy = 0.0; var xx = 0.0; var yy = 0.0
            for (i in 0 until n) { val a = x[i]; val b = x[i + lag]; xy += a * b; xx += a * a; yy += b * b }
            val r = xy / sqrt(xx * yy + 1e-20)
            if (r > best) best = r
        }
        return best
    }
}

/**
 * Synthetic claps, for the tests and the debug injector: a burst of noise (most of it between 300 Hz and 6 kHz) that
 * starts within half a millisecond and dies in about 30 ms, with a faint room echo behind it.
 */
object ClapSynth {
    /** Claps at [timesMs] (each [peakDb] loud) over a quiet room ([noiseDb]), [lengthMs] long. */
    fun render(timesMs: List<Double>, lengthMs: Double, sampleRate: Int = 48_000, peakDb: Double = -14.0, noiseDb: Double = -66.0, seed: Long = 7): FloatArray {
        val out = FloatArray((lengthMs / 1000 * sampleRate).toInt())
        val rnd = Random(seed)
        for (t in timesMs) {
            val start = (t / 1000 * sampleRate).toInt()
            val amp = 10.0.pow(peakDb / 20) * 1.6      // white noise peaks well above its RMS
            var hp = 0.0; var prev = 0.0; var lp = 0.0
            val aHp = exp(-2 * PI * 300 / sampleRate); val aLp = exp(-2 * PI * 6000 / sampleRate)
            for (i in 0 until (0.25 * sampleRate).toInt()) {
                val k = start + i
                if (k >= out.size) break
                val s = i.toDouble() / sampleRate
                val env = minOf(1.0, s / 0.0005) * exp(-s / 0.007) + 0.06 * exp(-s / 0.06)   // the burst and its echo
                val w = rnd.nextGaussian()
                hp = aHp * (hp + w - prev); prev = w                                           // one-pole high-pass
                lp = (1 - aLp) * hp + aLp * lp                                                  // one-pole low-pass
                out[k] += (amp * env * lp * 2.2).toFloat()
            }
        }
        val noise = 10.0.pow(noiseDb / 20)
        for (i in out.indices) out[i] += (rnd.nextGaussian() * noise).toFloat()
        return out
    }
}

/**
 * The pup's drum (lesson 7): a low tom whose pitch falls from 150 to 90 Hz, with a soft felt attack. It has a pitch and
 * rings for a while, so the clap counter, listening while it plays, does not count it.
 */
fun drumSamples(sampleRate: Int = 48_000, gain: Double = 0.85): FloatArray {
    val n = (0.6 * sampleRate).toInt()
    var phase = 0.0
    return FloatArray(n) { i ->
        val t = i.toDouble() / sampleRate
        val f = 90 + 60 * exp(-t / 0.05)
        phase += 2 * PI * f / sampleRate
        val env = minOf(1.0, t / 0.003) * exp(-t / 0.16)
        (gain * env * (sin(phase) + 0.25 * sin(2 * phase))).toFloat()
    }
}
