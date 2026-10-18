package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.midiFreq
import kotlin.math.PI
import kotlin.math.cos

/**
 * The Jam's chord ear. The note matcher only confirms notes it is told to expect, and in free play a chord's notes
 * share overtones, so it usually reports one note of three (measured on PianoSynth chords: C E G gave E only).
 * After the matcher hears a note, this looks at the raw audio around that note's onset and lists every key whose
 * fundamental just rose there ([freshNotes]): a sharp semitone peak that is at least [FRESH] times louder than
 * just before the onset, not an overtone of a lower fresh peak. One note gives one; a chord gives each of its notes.
 * Speech alone never gets here: it is only asked after a real piano strike.
 *
 * Threading: [feed] runs on the audio thread, [notesAt] on any other; both lock the ring.
 */
class ChordEar(val rate: Int = 48_000, val low: Int = LOW, val high: Int = HIGH) {
    private val ring = FloatArray(rate * 2)
    /** Absolute index (in samples since the mic started) one past the newest sample in [ring]. */
    private var end = 0L

    /**
     * Stores [count] samples that start at absolute sample [at] (the matcher's sample clock). A gap is left silent; a
     * clock that went back (the mic restarted and the matcher counts from 0 again) starts the ring over.
     */
    @Synchronized fun feed(chunk: FloatArray, count: Int = chunk.size, at: Long = end) {
        if (at < end) { ring.fill(0f); end = at }
        if (at > end) for (k in end until minOf(at, end + ring.size)) ring[(k % ring.size).toInt()] = 0f
        for (i in 0 until count) ring[((at + i) % ring.size).toInt()] = chunk[i]
        end = maxOf(end, at + count)
    }

    /** The newest sample index stored, for waiting until [notesAt] can answer. */
    @Synchronized fun newest(): Long = end

    /**
     * The keys that started sounding at [onset] (absolute sample), or null when the audio around it is not all in the
     * ring yet (ask again later) or is already gone.
     */
    fun notesAt(onset: Long): Set<Int>? {
        val before = FloatArray(WINDOW); val now = FloatArray(WINDOW)
        val beforeEnd = onset - ms(BEFORE_GAP_MS)
        val nowEnd = onset + ms(AFTER_MS)
        synchronized(this) {
            if (nowEnd > end || beforeEnd - WINDOW < end - ring.size || beforeEnd - WINDOW < 0) return null
            for (i in 0 until WINDOW) {
                before[i] = ring[((beforeEnd - WINDOW + i) % ring.size).toInt()]
                now[i] = ring[((nowEnd - WINDOW + i) % ring.size).toInt()]
            }
        }
        return freshNotes(before, now, rate, low, high)
    }

    private fun ms(v: Long) = v * rate / 1000

    companion object {
        /** About 85 ms at 48 kHz: resolves semitones from about C4 up (the Jam's keys), short enough to stay on the onset. */
        const val WINDOW = 4096
        /** The "now" window ends this long after the onset (a piano note is fully out by then; the decision waits for it). */
        const val AFTER_MS = 170L
        /** The "before" window ends this long before the onset, clear of the attack. */
        const val BEFORE_GAP_MS = 15L
        /** A fresh peak holds this many times its power before the onset. */
        const val FRESH = 4.0
        /** ...and at least this share of the loudest peak (quieter ones are overtones and room). */
        const val SHARE = 0.02
        /** Fundamentals looked at: A2 to F#6, a little wider than any Jam keyboard. */
        const val LOW = 45
        const val HIGH = 90
        /** Harmonic partials 2 to 6 of a note, in semitones above it. */
        private val PARTIALS = listOf(12, 19, 24, 28, 31)

        /**
         * The keys in [low]..[high] whose fundamentals rose from [before] to [now] (two equal windows, oldest sample
         * first): local maxima across semitones, [FRESH] times their old power and [SHARE] of the loudest, minus the
         * ones that are only a partial of a lower fresh key.
         */
        fun freshNotes(before: FloatArray, now: FloatArray, rate: Int, low: Int = LOW, high: Int = HIGH): Set<Int> {
            val keys = (low - 1)..(high + 1)
            val p = keys.associateWith { power(now, midiFreq(it), rate) }
            val q = keys.associateWith { power(before, midiFreq(it), rate) }
            val top = p.values.max()
            if (top <= 1e-12) return emptySet()
            val peaks = (low..high).filter { m ->
                val v = p.getValue(m)
                v > p.getValue(m - 1) && v >= p.getValue(m + 1) && v > SHARE * top && v > FRESH * q.getValue(m)
            }.toSet()
            return peaks.filterTo(LinkedHashSet()) { m -> PARTIALS.none { (m - it) in peaks } }
        }

        /** Hann-windowed Goertzel power of [x] at [freq]; a sine of amplitude A reads about A^2. */
        internal fun power(x: FloatArray, freq: Double, rate: Int): Double {
            val w = hann(x.size)
            val c = 2 * cos(2 * PI * freq / rate)
            var s1 = 0.0; var s2 = 0.0
            for (i in x.indices) {
                val s0 = x[i] * w[i] + c * s1 - s2
                s2 = s1; s1 = s0
            }
            return (s1 * s1 + s2 * s2 - c * s1 * s2) * 4 / (w.sum * w.sum)
        }

        private class Window(val w: DoubleArray, val sum: Double) { operator fun get(i: Int) = w[i] }
        private val windows = java.util.concurrent.ConcurrentHashMap<Int, Window>()
        private fun hann(n: Int) = windows.getOrPut(n) {
            val w = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
            Window(w, w.sum())
        }
    }
}
