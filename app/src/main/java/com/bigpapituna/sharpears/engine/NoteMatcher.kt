package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.hints.WrongKeys
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
    /**
     * Odd partials (1st, 3rd, 5th) must carry at least this share of the rise; an octave above has none there.
     * Roy's piano through the Fold's mic: a real E4 carried 8% (weak fundamental, strong 2nd partial) and was
     * refused at 0.15 six times in a row; a real octave-up (A5 for A4) carried 0.02%. 0.04 sits between them.
     */
    val oddRatio: Double = 0.04,
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
     * Last gate against faint false notes (ringing notes from the previous chord wobbling, flukes at the room's
     * noise floor). A strike must either jump this much at its lowest partial (a fresh note out of quiet)...
     */
    val freshJumpDb: Double = 18.0,
    /**
     * ...or rise this far above the room's background (a firm re-strike over a ringing note). On Roy's sessions the
     * 14 false greens jumped 3-15 dB and sat -14..+7 dB above the room; every real strike passed one of the two.
     */
    val roomMarginDb: Double = 10.0,
    /**
     * The room's background: the quietest 5% of hop loudness (~10.7 ms each) over the last 20 s. A short window
     * (5 s) measured the music itself during continuous playing and rejected real notes (8 missed in the synthetic
     * Für Elise checks); 20 s reaches back to the room between phrases.
     */
    val roomPercentile: Double = 0.05,
    val roomWindowHops: Int = 1875,
    /**
     * Share of the window spent fading out at its newest end. 0.5 is a symmetric Hann window; smaller values
     * weight recent sound more, so a fresh note counts sooner, at some cost in frequency precision.
     */
    val recentTaper: Double = 0.25,   // Roy's piano: 124 -> 113 ms median, same accuracy (0.15 starts missing)
    /** Notes below this fundamental are analysed at 12 kHz (170 ms windows), up to [band2BelowHz] at 24 kHz (85 ms). */
    val band4BelowHz: Double = 500.0,
    val band2BelowHz: Double = 1000.0,
    /**
     * Look for the key behind each unexplained strike (MicEvent.Wrong names it; without one found it is noise). Off,
     * nothing is ever called a wrong key: for screens that only listen for right notes, it saves the search.
     */
    val nameWrongKeys: Boolean = true,
) {
    /**
     * This config made [db] easier to trigger (a kid's lighter touch, Profile.micOffsetDb): the floor a strike must
     * rise past ([sensitivityDb]) and its margin over the room's background ([roomMarginDb]) both drop by [db]. The
     * relative checks (fresh partials, neighbours, octaves, sharp peaks) stay as they are. The room margin never
     * drops below [MIN_ROOM_MARGIN_DB]: Roy's false greens sat up to 7 dB over the room.
     */
    fun easierBy(db: Double): MatcherConfig = if (db == 0.0) this else
        copy(sensitivityDb = sensitivityDb - db, roomMarginDb = (roomMarginDb - db).coerceAtLeast(minOf(roomMarginDb, MIN_ROOM_MARGIN_DB)))

    companion object {
        const val MIN_ROOM_MARGIN_DB = 7.5
    }
}

/**
 * When a wrong-key candidate is judged, in hops (10.7 ms) after the broadband jump, per analysis band (decimation
 * factor): twice each, so its "now" window is mostly the new sound (21 and 43 ms for high notes, 43 and 85 ms for
 * the middle, 85 and 139 ms for the bass).
 */
internal val WRONG_LOOKS = mapOf(1 to (2 to 4), 2 to (4 to 8), 4 to (8 to 13))

/** How many keys per band and look are judged in full after the quick look at their lowest partials. */
internal const val WRONG_FULL_LOOKS = 3

/** [NoteMatcher.strictKeys]: the share of a strike's rise its two lowest partials must carry. */
internal const val STRICT_LOW_SHARE = 0.1
/** [NoteMatcher.strictKeys]: a lower note's first partials rising this many times the strike's own rise explain it. */
internal const val STRICT_LOWER_RATIO = 0.25
/**
 * [NoteMatcher.strictKeys]: a strike must carry the sound, not a sliver of it: its rise at most this many dB under the
 * loudest moment of its window (a key's partials hold most of its power; on Roy's piano a faint 131 Hz bump under A4
 * and A3 passed for C2 and C3 at 12 to 16 dB under the note that made it).
 */
internal const val STRICT_LEVEL_DB = 12.0

sealed interface MicEvent {
    /** Sample index (since the matcher started) where the event was decided. */
    val sample: Long
    /** [delayMs]: time since the loudness jump that started it, when there was one (how late the decision came). */
    data class Strike(
        val midi: Int, override val sample: Long, val riseDb: Double, val delayMs: Double? = null,
        /** Credited because its octave below, in the same chord, was heard (see [NoteMatcher]). */
        val inferredFrom: Int? = null,
    ) : MicEvent
    /**
     * A confident strike of a key nobody asked for: a broadband attack that no expected note explains, in which one
     * note within an octave of the expected ones passed every check a right note must pass (fresh partials, several
     * of them, a sharp piano peak, clear of the room, no lower note under it). Talking, singing, TV and knocks find no
     * such note and go to [NoteMatcher.noise] instead. [midi] was played; [nearest] is the expected note closest to it,
     * [distance] semitones away (always 1..12). [slip]: a neighbour (within [WrongKeys.SLIP]), drawn yellow, not red.
     */
    data class Wrong(
        override val sample: Long, val midi: Int, val nearest: Int, val distance: Int, val riseDb: Double,
    ) : MicEvent {
        val slip: Boolean get() = WrongKeys.isSlip(distance)
    }
}

/** Why the matcher turned an expected note down (see [NoteMatcher.candidates]). */
enum class Reject {
    NOT_FRESH, ONE_PARTIAL, SMALL_JUMP, SEMITONE, SPILL,
    /** A lower note's partials rose under it: mostly the octave below ([LOWER_OCTAVE]) or a twelfth below. */
    LOWER_NOTE, LOWER_OCTAVE,
    /** Its odd partials stayed quiet: only its octave above's partials rose ("higher octave?"). */
    HIGHER_OCTAVE,
    /**
     * Only its 3rd and 6th partials rose: the note a twelfth above was struck (G4 read as C3, its 3rd partial; found by
     * the Playground's range tests on PianoSynth, 2.1). Its 1st, 2nd, 4th and 5th partials stayed quiet.
     */
    HIGHER_TWELFTH,
    /**
     * A high note (analysed at the full rate, about C6 and up, where the fundamental is the strongest partial on any
     * piano) whose fundamental did not rise: a lower note's upper partials landed on its overtones (D4 read as C6).
     */
    NO_FUNDAMENTAL,
    NOT_SHARP, TOO_FAINT,
    /**
     * [NoteMatcher.strictKeys] only: the note's two lowest partials carried almost none of the rise, so another key's
     * upper partials landed on its overtones (on PianoSynth: C#4 credited as E3, G4 as E5, D6 as E6).
     */
    NO_LOW_PARTIALS,
}

/** An expected note that rose above the sensitivity floor at [sample] but was turned down for [reason]. */
data class Candidate(val midi: Int, val sample: Long, val riseDb: Double, val reason: Reject)

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
    /**
     * Every expected note turned down although it rose above the sensitivity floor, with the reason as a value (the
     * octave hint listens for [Reject.HIGHER_OCTAVE] and [Reject.LOWER_OCTAVE]). Called on the audio thread, several
     * times a hop while something sounds; keep it cheap. Null costs nothing.
     */
    @Volatile var candidates: ((Candidate) -> Unit)? = null
    /**
     * A loudness jump no expected note explains and no piano note was found in (talking, TV, a knock, a cough), at
     * this sample. The room-noise watchdog counts them. Called on the audio thread, about 150 ms after the jump.
     */
    @Volatile var noise: ((sample: Long) -> Unit)? = null
    /**
     * This person's mic offset (Profile.micOffsetDb, +3 for kids): the strike floors ([MatcherConfig.sensitivityDb] and
     * [MatcherConfig.roomMarginDb]) are this many dB easier. Applied on top of [config], which stays the stored setting.
     */
    @Volatile var offsetDb: Double = 0.0
    /**
     * Strict keys (2.1.1, the Playground's stages and the Jam): listening for a set of keys of which the player strikes
     * any one, or none (a hunt, high and low, free play), where a key outside the set must not pass for one inside it.
     * In wait mode the expected note is the one the player is reaching for; here the struck key may be anywhere, and
     * one of its upper partials can land on an expected key's overtones. So an accepted strike must also show (1) its
     * own lowest partials: the two lowest carry at least [STRICT_LOW_SHARE] of the rise; (2) no lower note under it:
     * no fresh fundamental at p/h of the note's 1st, 2nd or 3rd partial (h up to 9, beyond the octave and twelfth below
     * that every strike is checked for) with its own first partials at [STRICT_LOWER_RATIO] of the rise. Measured on
     * PianoSynth: A1 credited as C#4 (its 5th partial), A2 as G5 (7th), C4 as A#5 (its 7th on A#5's 2nd), G1 as E2
     * (its 5th on E2's 3rd). The lower
     * notes are probed only when a strike is about to be accepted, so it costs next to nothing. (3) No single-partial
     * slack (that is for chords, whose shared overtones are set aside; a stage hears one key), and (4) the strike
     * carries the sound: its rise within [STRICT_LEVEL_DB] of the loudest moment of its window. The wrong-key search
     * (MicEvent.Wrong) is held to the same checks, since a stage acts on the key it names. Off for the player.
     */
    @Volatile var strictKeys: Boolean = false
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
    private val roomHist = DoubleArray(4000)            // up to ~43 s of hop loudness, for the room's background level
    private var roomCount = 0
    /** The room's background level in dBFS (see [MatcherConfig.roomPercentile]). */
    @Volatile var roomFloorDb: Double = -120.0
        private set
    private var lastOnset = Long.MIN_VALUE / 2
    private var lastAttack = Long.MIN_VALUE / 2           // softer than an onset: any hammer attack
    /** A broadband strike waiting to be explained; [midi] is the wrong note found in it so far (-1: none yet). */
    private class Onset(val at: Long) {
        var midi = -1; var riseDb = 0.0
        /** Keys that passed at their band's first look; only these can be confirmed at the second. */
        val passed = HashSet<Int>(4)
    }
    private val onsets = ArrayDeque<Onset>()            // broadband strikes waiting to be explained
    private val recentStrikes = ArrayDeque<Long>()
    private val lastStrike = HashMap<Int, Long>()

    fun reset() {
        history.fill(0f); filled = 0; pendingCount = 0; samplesProcessed = 0
        band2.reset(); band4.reset()
        hopDb.fill(0.0); onsets.clear(); recentStrikes.clear(); lastStrike.clear(); nowHistory.clear(); probes.clear()
        roomCount = 0; roomFloorDb = -120.0; wrongScan = null
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
        val cfg = effectiveConfig()
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
        roomHist[roomCount++ % roomHist.size] = db
        if (roomCount % 25 == 0) {                         // re-estimate the room's background every ~0.25 s
            val n = minOf(roomCount, cfg.roomWindowHops.coerceAtMost(roomHist.size))
            val recent = DoubleArray(n) { roomHist[((roomCount - 1 - it) % roomHist.size + roomHist.size) % roomHist.size] }
            recent.sort()
            roomFloorDb = recent[(n * cfg.roomPercentile).toInt().coerceIn(0, n - 1)]
        }
        if (db - db40msAgo >= cfg.attackDb && db > -75) lastAttack = now
        if (db - db40msAgo >= cfg.wrongOnsetDb && db > -70 && now - lastOnset > ms(80.0)) {
            lastOnset = now
            onsets.addLast(Onset(now))
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
        // its own. Once the lower note of the pair is heard, credit X too (same pitch class). Up to four octaves:
        // in Alicia, C6 over C3 is C3's 8th partial, and C3's higher partials land on C6's checks for a lower note
        // and its semitone neighbours, so C6 was never heard and wait mode stuck there.
        // ponytail: a player who drops the upper octave still gets credit; fine in wait mode, where stuck is worse
        for (midi in exp) {
            if (now - (lastStrike[midi] ?: Long.MIN_VALUE / 2) < ms(600.0)) continue
            val lower = listOf(midi - 12, midi - 24, midi - 36, midi - 48).firstOrNull { l -> gs.any { midi in it && l in it } && now - (lastStrike[l] ?: Long.MIN_VALUE / 2) < ms(600.0) } ?: continue
            lastStrike[midi] = now
            recentStrikes.addLast(now)
            events += MicEvent.Strike(midi, now, -99.0, inferredFrom = lower)
        }

        // A broadband strike with no expected note around it: a wrong key when a piano note is found in it, else noise.
        while (recentStrikes.isNotEmpty() && now - recentStrikes.first() > ms(400.0)) recentStrikes.removeFirst()
        if (exp.isNotEmpty() && cfg.nameWrongKeys) for (o in onsets) if (o.midi < 0 && recentStrikes.none { it >= o.at - ms(60.0) }) findWrong(o, now, exp, gs, cfg)
        while (onsets.isNotEmpty() && now - onsets.first().at >= ms(150.0)) {
            val o = onsets.removeFirst()
            val explained = recentStrikes.any { it >= o.at - ms(60.0) && it <= o.at + ms(150.0) }
            if (explained || exp.isEmpty()) continue
            val near = if (o.midi >= 0) WrongKeys.nearest(o.midi, exp) else null
            // The key was found against the notes expected then; when they changed since (the song went on), a key now
            // expected, or out of reach of the new ones, is no wrong key.
            if (near != null) { if (kotlin.math.abs(o.midi - near) in 1..WrongKeys.REACH) events += MicEvent.Wrong(o.at, o.midi, near, kotlin.math.abs(o.midi - near), o.riseDb) }
            else noise?.invoke(o.at)
        }
        return events
    }

    /** The config in force: the stored one made [offsetDb] easier (cached; the audio thread asks every hop). */
    private var effective: Triple<MatcherConfig, Double, MatcherConfig>? = null
    private fun effectiveConfig(): MatcherConfig {
        val base = config; val off = offsetDb
        effective?.let { (b, o, e) -> if (b === base && o == off) return e }
        return base.easierBy(off).also { effective = Triple(base, off, it) }
    }

    /**
     * The keys a wrong note is looked for among (within [WrongKeys.REACH] of an expected note, not expected, in no
     * group), per analysis band, with their three lowest partials' frequencies for the quick first look.
     */
    private class WrongScan(val exp: Set<Int>, val gs: List<Set<Int>>, val byBand: List<Triple<Int, IntArray, DoubleArray>>)
    private var wrongScan: WrongScan? = null
    private fun wrongScanFor(exp: Set<Int>, gs: List<Set<Int>>): WrongScan {
        wrongScan?.let { if (it.exp == exp && it.gs == gs) return it }
        val inGroups = gs.flatten().toSet()
        val keys = exp.flatMap { e -> (e - WrongKeys.REACH)..(e + WrongKeys.REACH) }.distinct()
            .filter { it in 21..108 && it !in exp && it !in inGroups }.sorted()
        val byBand = keys.groupBy(::factorFor).map { (f, ks) ->
            Triple(f, ks.toIntArray(), DoubleArray(ks.size * 3) { i -> partialFreq(ks[i / 3], i % 3 + 1) })
        }
        return WrongScan(exp, gs, byBand).also { wrongScan = it }
    }

    /**
     * Looks for the key behind an unexplained strike [o], at two moments after the jump per band ([WRONG_LOOKS]), so
     * the band's "now" window is mostly the new sound. A quick look first: the fresh rise at each candidate's three
     * lowest partials, all keys of a band in one pass. Only the [WRONG_FULL_LOOKS] strongest are then judged like an
     * expected note (same probes and checks, stricter where a right note gets slack). A key must pass at both
     * moments: a piano string holds its pitch, a voice moves (talk glides a semitone or more between the two looks).
     * The strongest key that passed both is it.
     */
    private fun findWrong(o: Onset, now: Long, exp: Set<Int>, gs: List<Set<Int>>, cfg: MatcherConfig) {
        val k = ((now - o.at) / hop).toInt()
        var best = -1; var bestRise = 0.0
        val floor = Math.pow(10.0, cfg.sensitivityDb / 10)
        for ((f, keys, freqs) in wrongScanFor(exp, gs).byBand) {
            val looks = WRONG_LOOKS.getValue(f)
            if (k != looks.first && k != looks.second) continue
            val confirm = k == looks.second
            val ready = when (f) { 1 -> filled >= 2 * window; 2 -> band2.filled >= 2 * window; else -> band4.filled >= 2 * window }
            if (!ready) continue
            val buf = when (f) { 1 -> history; 2 -> band2.buf; else -> band4.buf }
            val rate = sampleRate / f
            val nowP = DoubleArray(freqs.size); val beforeP = DoubleArray(freqs.size)
            powers(freqs, buf, rate, buf.size, nowP)
            powers(freqs, buf, rate, buf.size - window, beforeP)
            val nyq = 0.45 * rate
            val score = DoubleArray(keys.size) { i ->
                var sum = 0.0
                for (j in 3 * i until 3 * i + 3) if (freqs[j] in 70.0..nyq && nowP[j] >= cfg.riseRatio * beforeP[j]) sum += nowP[j] - beforeP[j]
                sum
            }
            val top = keys.indices.filter { score[it] >= floor }.sortedByDescending { score[it] }.take(WRONG_FULL_LOOKS).map { keys[it] }
            // At the second look, the keys that passed the first are judged again even if others now score higher.
            val judge = if (confirm) (top + keys.filter { it in o.passed }).distinct() else top
            for (c in judge) {
                val recent = lastStrike.filter { (m, at) -> m != c && now - at < 2L * windowFor(m) }.keys
                val rise = strikeRise(c, setOf(c), recent, cfg, wrongScan = true) ?: continue
                if (!confirm) { o.passed += c; continue }
                if (c !in o.passed) continue
                if (best < 0 || rise > bestRise) { best = c; bestRise = rise }
            }
        }
        if (best >= 0) { o.midi = best; o.riseDb = bestRise }
    }

    /**
     * Returns the rise in dBFS if [midi] was just struck, else null. [wrongScan]: judging a key nobody asked for (see
     * [findWrong]); a wrong key must be beyond doubt, so it gets no single-partial slack, and the rise under it from
     * a lower note may be no more than [MatcherConfig.oddRatio] (a right note an octave down with a weak fundamental
     * must never be called its octave above). Turned-down expected notes go to [candidates] and [trace].
     */
    private fun strikeRise(midi: Int, chord: Set<Int>, recent: Set<Int>, cfg: MatcherConfig, wrongScan: Boolean = false): Double? {
        val f = freqsFor(midi, chord, recent)
        if (f.note.size < 2) {
            if (!wrongScan) trace?.invoke("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: overtones explained by notes just played ${recent.map(::noteName)}")
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
        if (riseDb < cfg.sensitivityDb) return null
        // Partials carrying a real share of the jump. Two ringing notes with nearly equal overtones beat,
        // which makes a single partial pulse; a strike lifts several partials at once.
        val freshIdx = f.note.indices.filter { i -> val a = nowV(f.note[i]); val b = beforeV(f.note[i]); a >= cfg.riseRatio * b && a - b >= 0.02 * noteFresh }
        val freshPartials = freshIdx.size
        // One partial is enough when the note has few of its own (chord-mates share the rest), or when it is the
        // fundamental, a hammer attack just happened, and the note as a whole is not fading (a fading sound with a
        // wobbling fundamental is not a strike: in Roy's scale recording a "C4" rose 6 dB at the fundamental while
        // its other partials fell 10-20 dB).
        val beforeTotal = f.note.sumOf { beforeV(it) }
        val singleOk = !wrongScan && !strictKeys && freshPartials == 1 && (f.note.size <= 2 ||
            (f.noteH[freshIdx[0]] == 1 && nowOf(f.note) >= beforeTotal &&
                samplesProcessed - lastAttack <= (cfg.attackWindowMs * sampleRate / 1000).toLong()))
        val subRatio = if (wrongScan) minOf(cfg.subRatio, cfg.oddRatio) else cfg.subRatio
        val subRise = riseOf(f.sub)

        val reason = when {
            noteFresh < 0.5 * riseOf(f.note) -> Reject.NOT_FRESH
            freshPartials < 2 && !singleOk -> Reject.ONE_PARTIAL
            noteFresh < 0.25 * nowOf(f.note) -> Reject.SMALL_JUMP
            noteFresh < cfg.decoyRatio * maxOf(riseOf(f.up), riseOf(f.down)) -> Reject.SEMITONE
            noteFresh < cfg.neighbourRatio * maxOf(nowOf(f.up), nowOf(f.down)) -> Reject.SPILL
            subRise > subRatio * noteFresh -> if (riseOf(f.sub.subList(0, f.subOctave)) >= 0.5 * subRise) Reject.LOWER_OCTAVE else Reject.LOWER_NOTE
            fresh(f.odd) < cfg.oddRatio * noteFresh -> Reject.HIGHER_OCTAVE
            // A key heard through a struck key's overtones (2.1, the Playground's findings): only for expected notes, the
            // wrong-key search keeps the detector's tuning (the room-noise watchdog counts its painted keys), except with
            // strict keys (2.1.1), where a stage acts on the key it names (G#7 was named C#6, its twelfth below). The twelfth
            // is checked only while the note's own 1st or 2nd partial is probed (in a chord they can be set aside as shared).
            (!wrongScan || strictKeys) && f.noteH.any { it <= 2 } && fresh(f.notThird) < cfg.oddRatio * noteFresh -> Reject.HIGHER_TWELFTH
            (!wrongScan || strictKeys) && factor == 1 && f.noteH.firstOrNull() == 1 && fresh(f.note.subList(0, 1)) < cfg.oddRatio * noteFresh -> Reject.NO_FUNDAMENTAL
            !sharpEnough(f, nowP, beforeP, cfg) -> Reject.NOT_SHARP
            !strongEnough(f, nowP, beforeP, riseDb, cfg) -> Reject.TOO_FAINT
            strictKeys && fresh(f.note.take(2)) < STRICT_LOW_SHARE * noteFresh -> Reject.NO_LOW_PARTIALS
            strictKeys && riseDb < loudestDb(windowFor(midi)) + 3.0 - STRICT_LEVEL_DB -> Reject.TOO_FAINT
            strictKeys && lowerNoteUnder(midi, chord, f, buf, rate, factor, noteFresh, cfg) -> Reject.LOWER_NOTE
            else -> null
        }
        val tr = trace
        if (reason == null) {
            tr?.invoke((if (wrongScan) "wrong key " else "accept ") + "${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: fresh ${db(noteFresh)} rise ${db(riseOf(f.note))} " +
                "up ${db(riseOf(f.up))} down ${db(riseOf(f.down))} sub ${db(riseOf(f.sub))} odd ${db(fresh(f.odd))} " +
                "sharp ${sharpness(f, nowP, beforeP, cfg)?.let { "%.1f".format(it) }} partials $freshPartials/${f.note.size} " +
                "attack ${(samplesProcessed - lastAttack) * 1000 / sampleRate}ms single=${freshPartials == 1} | " +
                f.note.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[p.size / 2]], db(nowV(p)), db(beforeV(p))) } + " | sub " +
                f.sub.joinToString { p -> "%.0fHz %s/%s".format(f.all[p[0]], db(nowV(p)), db(beforeV(p))) })
            return riseDb
        }
        if (wrongScan) return null
        candidates?.invoke(Candidate(midi, samplesProcessed, riseDb, reason))
        if (tr != null) {
            val why = when (reason) {
                Reject.NOT_FRESH -> "not fresh (fresh ${db(noteFresh)} of rise ${db(riseOf(f.note))})"
                Reject.ONE_PARTIAL -> "only $freshPartials partial jumped and no attack (beating?)"
                Reject.SMALL_JUMP -> "jump is small next to what is already ringing (now ${db(nowOf(f.note))})"
                Reject.SEMITONE -> "semitone neighbour (up ${db(riseOf(f.up))}, down ${db(riseOf(f.down))})"
                Reject.SPILL -> "spill from a ringing neighbour (up ${db(nowOf(f.up))}, down ${db(nowOf(f.down))})"
                Reject.LOWER_NOTE -> "lower note (sub ${db(subRise)})"
                Reject.LOWER_OCTAVE -> "lower note (sub ${db(subRise)}), octave below?"
                Reject.HIGHER_OCTAVE -> "no odd partials (odd ${db(fresh(f.odd))}), higher octave?"
                Reject.HIGHER_TWELFTH -> "only every third partial rose (others ${db(fresh(f.notThird))}), a twelfth above?"
                Reject.NO_FUNDAMENTAL -> "a high note whose fundamental did not rise (a lower note's overtones?)"
                Reject.NOT_SHARP -> "not a sharp peak (gliding sound: voice, whistle?)"
                Reject.NO_LOW_PARTIALS -> "its two lowest partials barely rose (another key's overtones?)"
                Reject.TOO_FAINT -> "too faint: lowest partial jumped ${"%.0f".format(firstJumpDb(f, nowP, beforeP))} dB, " +
                    "${"%.0f".format(riseDb - roomFloorDb)} dB above the room"
            }
            tr("reject ${noteName(midi)} at ${samplesProcessed * 1000 / sampleRate} ms: rise ${db(noteFresh)} dB, $why")
        }
        return null
    }

    /** How much the note's lowest probed partial jumped against the window before, in dB. */
    private fun firstJumpDb(f: Probe, nowP: DoubleArray, beforeP: DoubleArray): Double {
        val p = f.note.firstOrNull() ?: return 0.0
        val now = p.maxOf { nowP[it] }; val before = p.maxOf { beforeP[it] }
        return 10 * log10((now + 1e-20) / (before + 1e-20))
    }

    /**
     * [strictKeys]: whether a lower note was struck whose upper partial is this note's 1st, 2nd or 3rd (see [strictKeys]).
     * Its candidates are the fundamentals p/h of those partials (p = 1, 2, 3; h = p + 1 .. 9, less the octave and twelfth
     * below, which every strike is checked for); each is probed at its own first three partials where they are not
     * this note's partials. All of them rising, with fresh power of [STRICT_LOWER_RATIO] times this note's fresh rise,
     * means the lower note was struck and this one is only its overtone. A lower note of the same [chord] group is meant
     * to sound with it (2.2, the pond's C chord: G4's 2nd partial is C4's 3rd, and C4 is struck with it), so it explains
     * nothing; with a key alone in its group (every stage before 2.2, the Jam) nothing changes.
     */
    private fun lowerNoteUnder(midi: Int, chord: Set<Int>, f: Probe, buf: FloatArray, rate: Int, factor: Int, noteFresh: Double, cfg: MatcherConfig): Boolean {
        val (freqs, ranges, roots) = lowerProbes.getOrPut(midi to factor) { buildLowerProbes(midi, factor) }
        if (freqs.isEmpty()) return false
        val mates = chord.filter { it != midi }.map(::midiFreq)
        val now = DoubleArray(freqs.size); val before = DoubleArray(freqs.size)
        powers(freqs, buf, rate, buf.size, now)
        powers(freqs, buf, rate, buf.size - window, before)
        for ((k, r) in ranges.withIndex()) {
            if (mates.any { kotlin.math.abs(it - roots[k]) < roots[k] * 0.01 }) continue
            // Every probe of the lower note must rise: a real lower note lifts all of its first partials, while notes of
            // a chord can land on some of a phantom's (C4 and G4 are the 2nd and 3rd partials of a C3 nobody played).
            if (r.any { i -> now[i] < cfg.riseRatio * before[i] }) continue
            var fresh = 0.0
            for (i in r) fresh += now[i] - before[i]
            if (fresh >= STRICT_LOWER_RATIO * noteFresh) {
                trace?.invoke("strict ${noteName(midi)}: a lower note at %.0f Hz rose %s against the note's %s".format(freqs[r.first], db(fresh), db(noteFresh)))
                return true
            }
        }
        return false
    }

    /** Per key and band: the lower-note probes (frequencies) and, per candidate fundamental, its slice of them and its frequency. */
    private val lowerProbes = HashMap<Pair<Int, Int>, Triple<DoubleArray, List<IntRange>, DoubleArray>>()
    private fun buildLowerProbes(midi: Int, factor: Int): Triple<DoubleArray, List<IntRange>, DoubleArray> {
        val nyq = 0.45 * sampleRate / factor
        val tolerance = 1.5 * sampleRate / windowFor(midi)
        val own = (1..16).map { partialFreq(midi, it) }
        fun mine(fr: Double) = own.any { kotlin.math.abs(it - fr) < maxOf(fr * 0.012, tolerance) }
        val roots = ArrayList<Double>()
        for (p in 1..3) for (h in p + 1..9) {
            if (p == 1 && h <= 3) continue
            val g = partialFreq(midi, p) / h
            if (roots.none { kotlin.math.abs(it - g) < g * 0.01 }) roots += g
        }
        val freqs = ArrayList<Double>(); val ranges = ArrayList<IntRange>(); val kept = ArrayList<Double>()
        for (g in roots) {
            val probes = (1..3).map { g * it }.filter { it in 70.0..nyq && !mine(it) }
            if (probes.size < 2) continue
            ranges += freqs.size until freqs.size + probes.size
            freqs += probes
            kept += g
        }
        return Triple(freqs.toDoubleArray(), ranges, kept.toDoubleArray())
    }

    /** The loudest hop (dBFS, mean square) of the last [span] samples, at most the hop ring's length (about 340 ms). */
    private fun loudestDb(span: Int): Double {
        val hops = (span / hop).coerceIn(1, hopDb.size)
        val slot = ((samplesProcessed / hop) % hopDb.size).toInt()
        var best = -120.0
        for (k in 0 until hops) best = maxOf(best, hopDb[(slot - k + hopDb.size) % hopDb.size])
        return best
    }

    /** A fresh note out of quiet (big jump) or a firm strike well above the room's background. */
    private fun strongEnough(f: Probe, nowP: DoubleArray, beforeP: DoubleArray, riseDb: Double, cfg: MatcherConfig) =
        firstJumpDb(f, nowP, beforeP) >= cfg.freshJumpDb || riseDb - roomFloorDb >= cfg.roomMarginDb

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
        /** The note's partials that are not a multiple of 3 (1st, 2nd, 4th, 5th): a twelfth above has none there. */
        val notThird: List<IntArray>,
        val up: List<IntArray>, val down: List<IntArray>,
        /** Lower-note probes: the first [subOctave] are the octave below's odd partials, the rest a twelfth below. */
        val sub: List<IntArray>, val subOctave: Int,
        /** Per note partial: for each candidate, the probes a quarter-tone below and above (empty if unresolvable). */
        val sharp: List<IntArray>,
    )

    private data class ProbeKey(val midi: Int, val chord: Set<Int>, val recent: Set<Int>)
    private val probes = HashMap<ProbeKey, Probe>()
    private val nowHistory = HashMap<Probe, ArrayDeque<Pair<Long, DoubleArray>>>()   // per probe: recent "now" powers
    private fun freqsFor(midi: Int, chord: Set<Int>, recent: Set<Int>): Probe {
        // ponytail: whole cache dropped when it grows past 256 probes (chord changes, wrong-key candidates); an LRU if that ever shows up in profiles
        if (probes.size > 256) { probes.clear(); nowHistory.clear() }
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
        val subOct = listOf(f0 / 2, 1.5 * f0, 2.5 * f0).filter { it in 70.0..nyq && !shared(it) }.map { add(listOf(it)) }
        val sub = subOct + listOf(f0 / 3, 2 * f0 / 3, 4 * f0 / 3).filter { it in 70.0..nyq && !shared(it) }.map { add(listOf(it)) }
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
            notThird = note.filter { it.first % 3 != 0 }.map { it.second },
            up = up, down = down, sub = sub, subOctave = subOct.size, sharp = sharp,
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
