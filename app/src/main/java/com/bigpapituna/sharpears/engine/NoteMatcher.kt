package com.bigpapituna.sharpears.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

data class MatcherConfig(
    /** Minimum power rise across a note's partials for a strike, in dBFS. The "sensitivity" slider. */
    val sensitivityDb: Double = -66.0,
    /** A partial counts as freshly struck when it holds this many times its power in the window before. */
    val riseRatio: Double = 2.0,
    /** The rise must beat the same rise a semitone up and down by this factor. */
    val decoyRatio: Double = 2.0,
    /** ...and be at least this fraction of what a semitone neighbour is sounding now (catches spill from a ringing neighbour). */
    val neighbourRatio: Double = 0.1,
    /** Rise at sub-harmonics (a lower note whose overtones include this one) may be at most this fraction. */
    val subRatio: Double = 0.1,
    /** Odd partials (1st, 3rd, 5th) must carry at least this share of the rise; an octave above has none there. */
    val oddRatio: Double = 0.15,
    /** A broadband loudness jump of this many dB is a strike; with no expected note in it, it is a wrong note. */
    val wrongOnsetDb: Double = 7.0,
    val refractoryMs: Double = 120.0,
)

sealed interface MicEvent {
    /** Sample index (since the matcher started) where the event was decided. */
    val sample: Long
    data class Strike(val midi: Int, override val sample: Long, val riseDb: Double) : MicEvent
    data class Wrong(override val sample: Long) : MicEvent
}

/**
 * Hears whether the *expected* notes were just struck, from a mono audio stream. It does not transcribe:
 * wait mode always knows which keys come next, so each hop it asks, per expected key, whether the power
 * at that key's partials jumped, more than the same jump a semitone away and more than at sub-harmonics
 * (which would mean a lower note was played). Low notes use longer windows to separate semitones.
 *
 * Threading: [process] runs on the audio thread; [expected] and [config] are set from the UI thread.
 */
class NoteMatcher(val sampleRate: Int = 48_000, config: MatcherConfig = MatcherConfig()) {
    @Volatile var config: MatcherConfig = config
    @Volatile var expected: Set<Int> = emptySet()
    /**
     * Every note of the group being played, including ones already heard. They ring together, so their
     * partials never count against each other. Notes from earlier groups are *not* included: a note just
     * played must still count against its semitone neighbour that comes next.
     */
    @Volatile var group: Set<Int> = emptySet()
    /** Debug: explains rejected candidates that were loud enough. Called on the audio thread. */
    @Volatile var trace: ((String) -> Unit)? = null
    /** Loudness of the latest hop in dBFS, for the level meter. */
    @Volatile var levelDb: Double = -120.0
        private set

    val hop = 512
    private val maxWindow = 8192
    private val history = FloatArray(2 * maxWindow)   // newest sample last; "before" windows need 2x
    private var filled = 0
    private val pending = FloatArray(hop)
    private var pendingCount = 0
    var samplesProcessed = 0L
        private set

    private val hopDb = DoubleArray(32)                // ring of recent hop loudness; 0 dB at start so startup is no strike
    private var lastOnset = Long.MIN_VALUE / 2
    private val onsets = ArrayDeque<Long>()            // broadband strikes waiting to be explained
    private val recentStrikes = ArrayDeque<Long>()
    private val lastStrike = HashMap<Int, Long>()

    fun reset() {
        history.fill(0f); filled = 0; pendingCount = 0; samplesProcessed = 0
        hopDb.fill(0.0); onsets.clear(); recentStrikes.clear(); lastStrike.clear()
        lastOnset = Long.MIN_VALUE / 2
    }

    fun process(samples: FloatArray, count: Int = samples.size): List<MicEvent> {
        var out: MutableList<MicEvent>? = null
        var i = 0
        while (i < count) {
            val n = minOf(hop - pendingCount, count - i)
            System.arraycopy(samples, i, pending, pendingCount, n)
            pendingCount += n; i += n
            if (pendingCount == hop) {
                pendingCount = 0
                val events = analyzeHop()
                if (events.isNotEmpty()) (out ?: ArrayList<MicEvent>().also { out = it }).addAll(events)
            }
        }
        return out ?: emptyList()
    }

    private fun analyzeHop(): List<MicEvent> {
        System.arraycopy(history, hop, history, 0, history.size - hop)
        System.arraycopy(pending, 0, history, history.size - hop, hop)
        filled = minOf(history.size, filled + hop)
        samplesProcessed += hop
        val now = samplesProcessed
        val cfg = config
        val exp = expected
        val events = ArrayList<MicEvent>(1)

        // Loudness and broadband strikes (used only to call wrong notes).
        var ms = 0.0
        for (k in history.size - hop until history.size) ms += history[k] * history[k]
        val db = 10 * log10(ms / hop + 1e-12)
        levelDb = db
        val slot = ((now / hop) % hopDb.size).toInt()
        val db40msAgo = hopDb[((slot - 4 + hopDb.size) % hopDb.size)]
        hopDb[slot] = db
        if (db - db40msAgo >= cfg.wrongOnsetDb && db > -70 && now - lastOnset > ms(80.0)) {
            lastOnset = now
            onsets.addLast(now)
        }

        val chord = exp + group

        // Expected notes.
        for (midi in exp) {
            val n = windowFor(midi)
            if (filled < 2 * n) continue
            // The "before" window trails by one window, so a strike keeps looking like a rise for up to two windows.
            val refractory = maxOf(ms(cfg.refractoryMs), 2L * n + ms(20.0))
            if (now - (lastStrike[midi] ?: Long.MIN_VALUE / 2) < refractory) continue
            // Notes heard within the last two windows are still "rising"; overtones of this note that coincide
            // with theirs prove nothing. (Only this note's own overtones: a just-played semitone neighbour
            // must keep counting against it.)
            val recent = lastStrike.filter { (m, at) -> m != midi && now - at < 2L * windowFor(m) }.keys - chord
            val rise = strikeRise(midi, n, chord, recent, cfg) ?: continue
            lastStrike[midi] = now
            recentStrikes.addLast(now)
            events += MicEvent.Strike(midi, now, rise)
        }

        // A broadband strike with no expected note around it is a wrong note.
        while (recentStrikes.isNotEmpty() && now - recentStrikes.first() > ms(400.0)) recentStrikes.removeFirst()
        while (onsets.isNotEmpty() && now - onsets.first() >= ms(150.0)) {
            val o = onsets.removeFirst()
            val explained = recentStrikes.any { it >= o - ms(60.0) && it <= o + ms(150.0) }
            if (!explained && exp.isNotEmpty()) events += MicEvent.Wrong(o)
        }
        return events
    }

    /** Returns the rise in dBFS if [midi] was just struck, else null. */
    private fun strikeRise(midi: Int, n: Int, chord: Set<Int>, recent: Set<Int>, cfg: MatcherConfig): Double? {
        val f = freqsFor(midi, chord, recent)
        if (f.note.size < 2) {
            trace?.invoke("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: overtones explained by notes just played ${recent.map(::noteName)}")
            return null
        }
        val nowP = DoubleArray(f.all.size)
        val beforeP = DoubleArray(f.all.size)
        powers(f.all, history.size, n, nowP)
        powers(f.all, history.size - n, n, beforeP)

        // Each partial is probed at a few candidate frequencies (string stiffness varies between pianos);
        // its power is the strongest candidate.
        fun nowV(p: IntArray) = p.maxOf { nowP[it] }
        fun beforeV(p: IntArray) = p.maxOf { beforeP[it] }
        // Count only partials that genuinely jumped (at least riseRatio times their power in the window before).
        // A ringing note that merely wobbles has no such partials; a fresh strike over a ringing note that
        // shares some overtones still has the others.
        fun fresh(ps: List<IntArray>) = ps.sumOf { p -> val a = nowV(p); val b = beforeV(p); if (a >= cfg.riseRatio * b) a - b else 0.0 }
        fun riseOf(ps: List<IntArray>) = ps.sumOf { p -> maxOf(0.0, nowV(p) - beforeV(p)) }
        fun nowOf(ps: List<IntArray>) = ps.sumOf { p -> nowV(p) }
        val noteFresh = fresh(f.note)
        val riseDb = 10 * log10(noteFresh + 1e-20)
        // Partials carrying a real share of the jump. Two ringing notes with nearly equal overtones beat,
        // which makes a single partial pulse; a strike lifts several partials at once.
        val freshPartials = f.note.count { p -> val a = nowV(p); val b = beforeV(p); a >= cfg.riseRatio * b && a - b >= 0.02 * noteFresh }

        if (riseDb < cfg.sensitivityDb) return null
        val reason = when {
            noteFresh < 0.5 * riseOf(f.note) -> "not fresh (fresh ${db(noteFresh)} of rise ${db(riseOf(f.note))})"
            freshPartials < minOf(2, f.note.size) -> "only $freshPartials partial jumped (beating?)"
            noteFresh < 0.25 * nowOf(f.note) -> "jump is small next to what is already ringing (now ${db(nowOf(f.note))})"
            noteFresh < cfg.decoyRatio * maxOf(riseOf(f.up), riseOf(f.down)) -> "semitone neighbour (up ${db(riseOf(f.up))}, down ${db(riseOf(f.down))})"
            noteFresh < cfg.neighbourRatio * maxOf(nowOf(f.up), nowOf(f.down)) -> "spill from a ringing neighbour (up ${db(nowOf(f.up))}, down ${db(nowOf(f.down))})"
            riseOf(f.sub) > cfg.subRatio * noteFresh -> "lower note (sub ${db(riseOf(f.sub))})"
            fresh(f.odd) < cfg.oddRatio * noteFresh -> "no odd partials (odd ${db(fresh(f.odd))}), higher octave?"
            else -> {
                trace?.invoke("accept ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: fresh ${db(noteFresh)} rise ${db(riseOf(f.note))} " +
                    "up ${db(riseOf(f.up))} down ${db(riseOf(f.down))} sub ${db(riseOf(f.sub))} odd ${db(fresh(f.odd))} | " +
                    f.note.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[p.size / 2]], db(nowV(p)), db(beforeV(p))) } + " | sub " +
                    f.sub.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[0]], db(nowV(p)), db(beforeV(p))) })
                return riseDb
            }
        }
        trace?.invoke("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: rise ${db(noteFresh)} dB, $reason")
        return null
    }

    /** Frequencies probed for one note. Each group is a list of partials; each partial a few candidate indices into [all]. */
    private class Probe(
        val all: DoubleArray,
        val note: List<IntArray>, val odd: List<IntArray>,
        val up: List<IntArray>, val down: List<IntArray>, val sub: List<IntArray>,
    )

    private var probeFor: Set<Int> = emptySet()
    private val probes = HashMap<Pair<Int, Set<Int>>, Probe>()
    private fun freqsFor(midi: Int, chord: Set<Int>, recent: Set<Int>): Probe {
        if (chord != probeFor) { probes.clear(); probeFor = chord }
        return probes.getOrPut(midi to recent) { buildProbe(midi, chord, recent) }
    }

    private fun buildProbe(midi: Int, chord: Set<Int>, recent: Set<Int>): Probe {
        val list = ArrayList<Double>(48)
        fun add(fs: List<Double>): IntArray = fs.map { list += it; list.size - 1 }.toIntArray()
        val nyq = 0.45 * sampleRate
        // Low partials barely move with string stiffness; from the 3rd up, try soft, typical and stiff strings.
        fun candidates(m: Int, h: Int) = if (h <= 2) listOf(partialFreq(m, h)) else listOf(0.4, 1.0, 2.5).map { partialFreq(m, h, it) }
        // Partials of the other notes in this group are legitimately rising too (a chord). Where a probe
        // cannot tell them apart (within 1.5 bins of the window, or 1.2 %), leave that probe out.
        val chordPartials = (chord - midi).flatMap { o -> (1..8).map { partialFreq(o, it) } }
        val recentPartials = recent.flatMap { o -> (1..8).map { partialFreq(o, it) } }
        val tolerance = 1.5 * sampleRate / windowFor(midi)
        fun near(list: List<Double>, fr: Double) = list.any { kotlin.math.abs(it - fr) < maxOf(fr * 0.012, tolerance) }
        fun shared(fr: Double) = near(chordPartials, fr)
        fun partialsOf(m: Int, dropShared: Boolean): List<Pair<Int, IntArray>> {
            val hs = (1..6).filter { partialFreq(m, it) in 70.0..nyq }
            val kept = if (!dropShared) hs else hs.filterNot { shared(partialFreq(m, it)) }.ifEmpty { hs }
            return kept.map { h -> h to add(candidates(m, h)) }
        }
        val note = partialsOf(midi, dropShared = true).let { mine ->
            if (mine.size >= 2) mine else partialsOf(midi, dropShared = false)
        }.filterNot { (h, _) -> near(recentPartials, partialFreq(midi, h)) }   // may leave < 2: then no evidence yet
        val f0 = midiFreq(midi)
        // Octave below (odd partials) and a twelfth below: frequencies where only a lower note would add energy.
        val sub = listOf(f0 / 2, 1.5 * f0, 2.5 * f0, f0 / 3, 2 * f0 / 3, 4 * f0 / 3)
            .filter { it in 70.0..nyq && !shared(it) }.map { add(listOf(it)) }
        val up = partialsOf(midi + 1, dropShared = true).map { it.second }
        val down = partialsOf(midi - 1, dropShared = true).map { it.second }
        return Probe(                                   // after every add(): `all` must hold all of them
            list.toDoubleArray(),
            note = note.map { it.second },
            odd = note.filter { it.first % 2 == 1 }.map { it.second },
            up = up, down = down, sub = sub,
        )
    }

    /** Hann-windowed Goertzel power at each frequency over history[end-n, end); a sine of amplitude A reads A^2. */
    private fun powers(freqs: DoubleArray, end: Int, n: Int, out: DoubleArray) {
        val win = hann(n)
        val nf = freqs.size
        val c = DoubleArray(nf) { 2 * cos(2 * PI * freqs[it] / sampleRate) }
        val s1 = DoubleArray(nf); val s2 = DoubleArray(nf)
        val start = end - n
        for (i in 0 until n) {
            val x = history[start + i] * win[i]
            for (k in 0 until nf) {               // independent chains: lets the CPU overlap the multiplies
                val s0 = x + c[k] * s1[k] - s2[k]
                s2[k] = s1[k]; s1[k] = s0
            }
        }
        val norm = 4.0 / (n.toDouble() * n) * 4.0   // Hann coherent gain 0.5 squared
        for (k in 0 until nf) out[k] = (s1[k] * s1[k] + s2[k] * s2[k] - c[k] * s1[k] * s2[k]) * norm
    }

    private val hanns = HashMap<Int, FloatArray>()
    private fun hann(n: Int) = hanns.getOrPut(n) { FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() } }

    /**
     * Window length per register: long enough that a semitone neighbour's fundamental sits about 3 bins
     * away, outside the Hann main lobe. Bass notes rely on their upper partials, which are further apart.
     */
    // ponytail: three fixed windows; per-note lengths only if the fixtures show a register that needs one
    fun windowFor(midi: Int): Int = midiFreq(midi).let { if (it >= 1000) 2048 else if (it >= 500) 4096 else 8192 }

    private fun db(p: Double) = "%.1f".format(10 * log10(p + 1e-20))
    private fun ms(v: Double) = (v * sampleRate / 1000).toLong()
}
