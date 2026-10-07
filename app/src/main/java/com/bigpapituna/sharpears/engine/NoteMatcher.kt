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
    /**
     * A piano partial is a sharp, steady peak; voices and whistles glide and smear. Where the window can resolve
     * a quarter-tone, the jumping partials must hold this many times the power a quarter-tone up or down.
     */
    val sharpRatio: Double = 2.0,   // synthetic piano scores ~5; loose until real fixtures say otherwise
    /** Odd partials (1st, 3rd, 5th) must carry at least this share of the rise; an octave above has none there. */
    val oddRatio: Double = 0.15,
    /** A broadband loudness jump of this many dB is a strike; with no expected note in it, it is a wrong note. */
    val wrongOnsetDb: Double = 7.0,
    val refractoryMs: Double = 120.0,
    /**
     * A strike whose only clearly jumping partial is its fundamental still counts when a hammer attack (a broadband
     * jump of this many dB over 40 ms) happened within [attackWindowMs]. On Roy's piano through the phone mic, notes
     * inside a chord often show just the fundamental once the chord-mates' shared overtones are set aside; beating
     * between ringing notes, the false case, has no attack.
     */
    val attackDb: Double = 3.0,
    val attackWindowMs: Double = 180.0,   // Roy's real single-partial strikes: 0-170 ms after their attack
    /**
     * Share of the window spent fading out at its newest end. 0.5 is a symmetric Hann window; smaller values
     * weight recent sound more, so a fresh note counts sooner, at some cost in frequency precision.
     */
    val recentTaper: Double = 0.25,   // Roy's piano: 124 -> 113 ms median, same accuracy (0.15 starts missing)
    /** Notes below this fundamental are analysed at 12 kHz (170 ms windows), up to [band2BelowHz] at 24 kHz (85 ms). */
    val band4BelowHz: Double = 500.0,
    val band2BelowHz: Double = 1000.0,
)

sealed interface MicEvent {
    /** Sample index (since the matcher started) where the event was decided. */
    val sample: Long
    /** [delayMs]: time since the loudness jump that started it, when there was one (how late the decision came). */
    data class Strike(
        val midi: Int, override val sample: Long, val riseDb: Double, val delayMs: Double? = null,
        /** Credited because its octave below, in the same chord, was heard (see [NoteMatcher]). */
        val inferredFrom: Int? = null,
    ) : MicEvent
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
    var group: Set<Int>
        get() = groups.flatten().toSet()
        set(v) { groups = listOf(v) }

    /**
     * Several independent chord groups, e.g. chord practice listening to the same chord in two octaves: each note is
     * judged against the chord-mates of its own group only (putting both octaves in one group hides every upper note's
     * overtones inside its lower octave and weakens the checks for the rest).
     */
    @Volatile var groups: List<Set<Int>> = emptyList()
    /** Debug: explains rejected candidates that were loud enough. Called on the audio thread. */
    @Volatile var trace: ((String) -> Unit)? = null
    /** Loudness of the latest hop in dBFS, for the level meter. */
    @Volatile var levelDb: Double = -120.0
        private set

    val hop = 512
    /** Every probe uses this many samples; low registers get them from a decimated band, so the time span grows. */
    private val window = 2048
    private val history = FloatArray(16384)            // full rate, newest sample last; feeds the decimation filters
    private var filled = 0

    /**
     * A low-passed, decimated copy of the input. Bass and middle notes have no overtones above ~6 kHz, so they
     * are analysed at 12 or 24 kHz: same frequency resolution as a long full-rate window, a quarter of the work.
     */
    private inner class Band(val factor: Int, taps: Int, cutoffHz: Double) {
        val rate = sampleRate / factor
        val buf = FloatArray(2 * window)               // "before" and "now" windows
        var filled = 0
        private val h = FloatArray(taps).also { h ->
            val fc = cutoffHz / sampleRate
            val m = taps - 1
            for (j in 0 until taps) {
                val x = j - m / 2.0
                val sinc = if (x == 0.0) 2 * fc else kotlin.math.sin(2 * PI * fc * x) / (PI * x)
                val blackman = 0.42 - 0.5 * cos(2 * PI * j / m) + 0.08 * cos(4 * PI * j / m)
                h[j] = (sinc * blackman).toFloat()
            }
            val sum = h.sum(); for (j in h.indices) h[j] /= sum
        }
        fun update() {
            val outN = hop / factor
            System.arraycopy(buf, outN, buf, 0, buf.size - outN)
            val base = history.size - hop
            for (k in 0 until outN) {
                val pos = base + (k + 1) * factor - 1
                var acc = 0f
                for (j in h.indices) acc += h[j] * history[pos - j]
                buf[buf.size - outN + k] = acc
            }
            filled = minOf(buf.size, filled + outN)
        }
        fun reset() { buf.fill(0f); filled = 0 }
    }
    private val band2 = Band(2, 63, 10_400.0)
    private val band4 = Band(4, 95, 5_200.0)
    private fun factorFor(midi: Int) = midiFreq(midi).let { if (it >= config.band2BelowHz) 1 else if (it >= config.band4BelowHz) 2 else 4 }
    private val pending = FloatArray(hop)
    private var pendingCount = 0
    @Volatile var samplesProcessed = 0L
        private set

    private val hopDb = DoubleArray(32)                // ring of recent hop loudness; 0 dB at start so startup is no strike
    private var lastOnset = Long.MIN_VALUE / 2
    private var lastAttack = Long.MIN_VALUE / 2           // softer than an onset: any hammer attack
    private val onsets = ArrayDeque<Long>()            // broadband strikes waiting to be explained
    private val recentStrikes = ArrayDeque<Long>()
    private val lastStrike = HashMap<Int, Long>()

    fun reset() {
        history.fill(0f); filled = 0; pendingCount = 0; samplesProcessed = 0
        band2.reset(); band4.reset()
        hopDb.fill(0.0); onsets.clear(); recentStrikes.clear(); lastStrike.clear(); nowHistory.clear(); probes.clear()
        lastOnset = Long.MIN_VALUE / 2
        lastAttack = Long.MIN_VALUE / 2
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
        band2.update(); band4.update()
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
        if (db - db40msAgo >= cfg.attackDb && db > -75) lastAttack = now
        if (db - db40msAgo >= cfg.wrongOnsetDb && db > -70 && now - lastOnset > ms(80.0)) {
            lastOnset = now
            onsets.addLast(now)
        }

        val gs = groups
        // A note's chord-mates: its own group(s); notes in no group fall back to everything expected.
        fun chordOf(midi: Int): Set<Int> = gs.filter { midi in it }.flatten().toSet().ifEmpty { exp }

        // Expected notes.
        for (midi in exp) {
            val chord = chordOf(midi)
            val n = windowFor(midi)                       // effective length in full-rate samples
            val ready = when (factorFor(midi)) { 1 -> filled >= 2 * window; 2 -> band2.filled >= 2 * window; else -> band4.filled >= 2 * window }
            if (!ready) continue
            // The "before" window trails by one window, so a strike keeps looking like a rise for up to two windows.
            val refractory = maxOf(ms(cfg.refractoryMs), 2L * n + ms(20.0))
            if (now - (lastStrike[midi] ?: Long.MIN_VALUE / 2) < refractory) continue
            // Notes heard within the last two windows are still "rising"; overtones of this note that coincide
            // with theirs prove nothing. (Only this note's own overtones: a just-played semitone neighbour
            // must keep counting against it.)
            val recent = lastStrike.filter { (m, at) -> m != midi && now - at < 2L * windowFor(m) }.keys - chord
            val rise = strikeRise(midi, chord, recent, cfg) ?: continue
            lastStrike[midi] = now
            recentStrikes.addLast(now)
            val delay = (now - lastOnset).takeIf { it in 0..ms(400.0) }?.let { it * 1000.0 / sampleRate }
            events += MicEvent.Strike(midi, now, rise, delay)
        }

        // Octave doublings inside a chord: every overtone of X is an overtone of X-12, so X cannot be heard on
        // its own. Once the lower note of the pair is heard, credit X too (same pitch class).
        // ponytail: a player who drops the upper octave still gets credit; fine for chord practice
        for (midi in exp) {
            if (now - (lastStrike[midi] ?: Long.MIN_VALUE / 2) < ms(600.0)) continue
            val lower = listOf(midi - 12, midi - 24).firstOrNull { l -> gs.any { midi in it && l in it } && now - (lastStrike[l] ?: Long.MIN_VALUE / 2) < ms(600.0) } ?: continue
            lastStrike[midi] = now
            recentStrikes.addLast(now)
            events += MicEvent.Strike(midi, now, -99.0, inferredFrom = lower)
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
    private fun strikeRise(midi: Int, chord: Set<Int>, recent: Set<Int>, cfg: MatcherConfig): Double? {
        val f = freqsFor(midi, chord, recent)
        if (f.note.size < 2) {
            trace?.invoke("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: overtones explained by notes just played ${recent.map(::noteName)}")
            return null
        }
        val nowP = DoubleArray(f.all.size)
        val beforeP = DoubleArray(f.all.size)
        val factor = factorFor(midi)
        val buf = when (factor) { 1 -> history; 2 -> band2.buf; else -> band4.buf }
        val rate = sampleRate / factor
        powers(f.all, buf, rate, buf.size, nowP)
        // The "before" window is exactly the "now" window from 4 x factor hops ago: reuse it when we have it.
        val hopIndex = samplesProcessed / hop
        val past = nowHistory.getOrPut(f) { ArrayDeque() }
        val cached = past.firstOrNull { it.first == hopIndex - 4L * factor }
        if (cached != null) cached.second.copyInto(beforeP) else powers(f.all, buf, rate, buf.size - window, beforeP)
        past.addLast(hopIndex to nowP)
        while (past.size > 4 * factor + 1) past.removeFirst()

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
        val freshIdx = f.note.indices.filter { i -> val a = nowV(f.note[i]); val b = beforeV(f.note[i]); a >= cfg.riseRatio * b && a - b >= 0.02 * noteFresh }
        val freshPartials = freshIdx.size
        // One partial is enough when the note has few of its own (chord-mates share the rest), or when it is the
        // fundamental, a hammer attack just happened, and the note as a whole is not fading (a fading sound with a
        // wobbling fundamental is not a strike: in Roy's scale recording a "C4" rose 6 dB at the fundamental while
        // its other partials fell 10-20 dB).
        val beforeTotal = f.note.sumOf { beforeV(it) }
        val singleOk = freshPartials == 1 && (f.note.size <= 2 ||
            (f.noteH[freshIdx[0]] == 1 && nowOf(f.note) >= beforeTotal &&
                samplesProcessed - lastAttack <= (cfg.attackWindowMs * sampleRate / 1000).toLong()))

        if (riseDb < cfg.sensitivityDb) return null
        val reason = when {
            noteFresh < 0.5 * riseOf(f.note) -> "not fresh (fresh ${db(noteFresh)} of rise ${db(riseOf(f.note))})"
            freshPartials < 2 && !singleOk -> "only $freshPartials partial jumped and no attack (beating?)"
            noteFresh < 0.25 * nowOf(f.note) -> "jump is small next to what is already ringing (now ${db(nowOf(f.note))})"
            noteFresh < cfg.decoyRatio * maxOf(riseOf(f.up), riseOf(f.down)) -> "semitone neighbour (up ${db(riseOf(f.up))}, down ${db(riseOf(f.down))})"
            noteFresh < cfg.neighbourRatio * maxOf(nowOf(f.up), nowOf(f.down)) -> "spill from a ringing neighbour (up ${db(nowOf(f.up))}, down ${db(nowOf(f.down))})"
            riseOf(f.sub) > cfg.subRatio * noteFresh -> "lower note (sub ${db(riseOf(f.sub))})"
            fresh(f.odd) < cfg.oddRatio * noteFresh -> "no odd partials (odd ${db(fresh(f.odd))}), higher octave?"
            !sharpEnough(f, nowP, beforeP, cfg) -> "not a sharp peak (gliding sound: voice, whistle?)"
            else -> {
                trace?.invoke("accept ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: fresh ${db(noteFresh)} rise ${db(riseOf(f.note))} " +
                    "up ${db(riseOf(f.up))} down ${db(riseOf(f.down))} sub ${db(riseOf(f.sub))} odd ${db(fresh(f.odd))} " +
                    "sharp ${sharpness(f, nowP, beforeP, cfg)?.let { "%.1f".format(it) }} partials $freshPartials/${f.note.size} " +
                    "attack ${(samplesProcessed - lastAttack) * 1000 / sampleRate}ms single=${freshPartials == 1} | " +
                    f.note.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[p.size / 2]], db(nowV(p)), db(beforeV(p))) } + " | sub " +
                    f.sub.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[0]], db(nowV(p)), db(beforeV(p))) })
                return riseDb
            }
        }
        trace?.invoke("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: rise ${db(noteFresh)} dB, $reason")
        return null
    }

    /** Fresh partials, where resolvable, must stand above their quarter-tone neighbours. */
    private fun sharpEnough(f: Probe, nowP: DoubleArray, beforeP: DoubleArray, cfg: MatcherConfig): Boolean =
        sharpness(f, nowP, beforeP, cfg).let { it == null || it >= cfg.sharpRatio }

    /** Center / quarter-tone-side power over the fresh, resolvable partials; null when none qualify. */
    private fun sharpness(f: Probe, nowP: DoubleArray, beforeP: DoubleArray, cfg: MatcherConfig): Double? {
        var center = 0.0; var side = 0.0
        f.note.forEachIndexed { i, p ->
            val offs = f.sharp[i]
            if (offs.isEmpty()) return@forEachIndexed
            val best = p.indices.maxBy { nowP[p[it]] }
            val now = nowP[p[best]]
            if (now < cfg.riseRatio * beforeP[p[best]]) return@forEachIndexed     // only partials that jumped
            center += now
            side += maxOf(nowP[offs[2 * best]], nowP[offs[2 * best + 1]])
        }
        return if (center == 0.0) null else center / (side + 1e-20)
    }

    /** Frequencies probed for one note. Each group is a list of partials; each partial a few candidate indices into [all]. */
    private class Probe(
        val all: DoubleArray,
        val note: List<IntArray>, val noteH: IntArray, val odd: List<IntArray>,
        val up: List<IntArray>, val down: List<IntArray>, val sub: List<IntArray>,
        /** Per note partial: for each candidate, the probes a quarter-tone below and above (empty if unresolvable). */
        val sharp: List<IntArray>,
    )

    private data class ProbeKey(val midi: Int, val chord: Set<Int>, val recent: Set<Int>)
    private val probes = HashMap<ProbeKey, Probe>()
    private val nowHistory = HashMap<Probe, ArrayDeque<Pair<Long, DoubleArray>>>()   // per probe: recent "now" powers
    private fun freqsFor(midi: Int, chord: Set<Int>, recent: Set<Int>): Probe {
        // ponytail: whole cache dropped when it grows past 64 probes (chord changes); an LRU if that ever shows up in profiles
        if (probes.size > 64) { probes.clear(); nowHistory.clear() }
        return probes.getOrPut(ProbeKey(midi, chord, recent)) { buildProbe(midi, chord, recent) }
    }

    private fun buildProbe(midi: Int, chord: Set<Int>, recent: Set<Int>): Probe {
        val list = ArrayList<Double>(48)
        fun add(fs: List<Double>): IntArray = fs.map { list += it; list.size - 1 }.toIntArray()
        val nyq = 0.45 * sampleRate / factorFor(midi)    // the band this note is analysed in
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
        val bin = sampleRate.toDouble() / factorFor(midi) / window
        val q = Math.pow(2.0, 0.5 / 12)                  // a quarter-tone
        val sharp = note.map { (_, cands) ->
            val f = list[cands[cands.size / 2]]
            if (f * (q - 1) < 2.5 * bin || f * q > nyq) IntArray(0)
            else add(cands.flatMap { c -> listOf(list[c] / q, list[c] * q) })
        }
        return Probe(                                   // after every add(): `all` must hold all of them
            list.toDoubleArray(),
            note = note.map { it.second },
            noteH = note.map { it.first }.toIntArray(),
            odd = note.filter { it.first % 2 == 1 }.map { it.second },
            up = up, down = down, sub = sub, sharp = sharp,
        )
    }

    /** Hann-windowed Goertzel power at each frequency over buf[end-window, end); a sine of amplitude A reads A^2. */
    private fun powers(freqs: DoubleArray, buf: FloatArray, rate: Int, end: Int, out: DoubleArray) {
        val n = window
        val win = taperWindow(n, config.recentTaper)
        val nf = freqs.size
        val c = DoubleArray(nf) { 2 * cos(2 * PI * freqs[it] / rate) }
        val s1 = DoubleArray(nf); val s2 = DoubleArray(nf)
        val start = end - n
        for (i in 0 until n) {
            val x = buf[start + i] * win.first[i]
            for (k in 0 until nf) {               // independent chains: lets the CPU overlap the multiplies
                val s0 = x + c[k] * s1[k] - s2[k]
                s2[k] = s1[k]; s1[k] = s0
            }
        }
        val norm = 4.0 / (win.second * win.second)    // a sine of amplitude A reads A^2
        for (k in 0 until nf) out[k] = (s1[k] * s1[k] + s2[k] * s2[k] - c[k] * s1[k] * s2[k]) * norm
    }

    private val windows = HashMap<Pair<Int, Double>, Pair<FloatArray, Double>>()
    /** Cosine fade-in over (1 - taper) of the window, fade-out over the newest taper share. Returns (weights, sum). */
    private fun taperWindow(n: Int, taper: Double) = windows.getOrPut(n to taper) {
        val fall = (n * taper).toInt().coerceAtLeast(1)
        val rise = n - fall
        val w = FloatArray(n) { i ->
            if (i < rise) (0.5 - 0.5 * cos(PI * i / rise)).toFloat() else (0.5 + 0.5 * cos(PI * (i - rise) / fall)).toFloat()
        }
        w to w.sumOf { it.toDouble() }
    }

    /**
     * Window length per register: long enough that a semitone neighbour's fundamental sits about 3 bins
     * away, outside the Hann main lobe. Bass notes rely on their upper partials, which are further apart.
     */
    // ponytail: three fixed bands; per-note lengths only if the fixtures show a register that needs one
    fun windowFor(midi: Int): Int = window * factorFor(midi)   // effective span in full-rate samples

    private fun db(p: Double) = "%.1f".format(10 * log10(p + 1e-20))
    private fun ms(v: Double) = (v * sampleRate / 1000).toLong()
}
