package com.bigpapituna.sharpears.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Perform timing feedback (2.14, Sam's "thin timing feedback"): where each note of a Perform run landed against the beat,
 * a verdict per bar, and one plain line about the pattern. Pure; the recap's timing panel (ui/player/TimingPanel.kt) draws
 * it, the player's live tick (ui/HitLights.kt) uses [tick].
 *
 * Offsets come from [PerformScore] in song ms ([NoteTiming.offsetMs]); here they are turned into real ms (divided by the
 * run's speed), the time the player felt, and every threshold below is in real ms.
 */
enum class BarVerdict(val word: String) { CLEAN("Clean"), RUSHED("Rushed"), DRAGGED("Dragged"), MESSY("Messy") }

/**
 * One graded note in its bar: [bar] from 1 (as "Loop bar 3" counts), [beat] where it falls in the bar (0 is the first beat,
 * 1.5 the "and" of the second), the keys it wanted, and [offsetMs] in real ms (negative early, null missed).
 */
data class TimedNote(val bar: Int, val beat: Double, val keys: List<Int>, val offsetMs: Int?) {
    val missed get() = offsetMs == null
    val early get() = offsetMs != null && offsetMs < -PerformTiming.OFF_MS
    val late get() = offsetMs != null && offsetMs > PerformTiming.OFF_MS
    /** Within [PerformTiming.ON_BEAT] of a whole beat: the beat it sits on (0-based), else null. */
    val onBeat: Int? get() = beat.roundToInt().takeIf { abs(beat - it) <= PerformTiming.ON_BEAT }
}

/** A bar of the strip: its notes and verdict; [verdict] is null for a bar with nothing to play (a rest, the other hand's). */
data class BarTiming(val bar: Int, val verdict: BarVerdict?, val notes: List<TimedNote>)

/** The panel's data: every bar from the first graded one to the last, the one line, and the run's average offset. */
data class TimingReport(val bars: List<BarTiming>, val line: String, val biasMs: Int) {
    val isEmpty get() = bars.none { it.verdict != null }
}

object PerformTiming {
    /**
     * Early or late beyond this (real ms) is felt; inside it a note is on the beat. Between RunWords' "right on the beat" (25)
     * and its "a little ahead of the beat" (80), so a bar called rushed is one the recap's own words would call early too.
     */
    const val OFF_MS = 60
    /** A note this close to a whole beat (in beats) sits on it, so a line may name the beat. */
    const val ON_BEAT = 0.1
    /** A bar is rushed (or dragged) once this share of its notes came early (late), none the other way. */
    const val BAR_SHARE = 0.25
    /** A pattern on one beat needs this many bars before the line names the beat. */
    const val PATTERN_BARS = 2
    /** Bars the line names before it says "and N more". */
    const val NAMED_BARS = 4

    /**
     * The report of a run's graded [notes] (song order, song ms) for a song whose bars are [barMs] long with [beatsPerBar]
     * beats, played at [speed]. [midi]: the notes came from a MIDI keyboard, so a run that is early or late all the way
     * through points at the MIDI timing offset.
     */
    fun of(notes: List<NoteTiming>, barMs: Double, beatsPerBar: Int, speed: Double = 1.0, midi: Boolean = false): TimingReport {
        if (notes.isEmpty() || barMs <= 0 || !barMs.isFinite()) return TimingReport(emptyList(), STEADY, 0)
        val beatMs = barMs / beatsPerBar.coerceAtLeast(1)
        val sp = speed.takeIf { it > 0 && it.isFinite() } ?: 1.0
        val timed = notes.map { n ->
            val bar = TroubleSpots.barIndex(n.startMs.toDouble(), barMs)
            TimedNote(bar + 1, (n.startMs - bar * barMs) / beatMs, n.keys, n.offsetMs?.let { (it / sp).roundToInt() })
        }
        val byBar = timed.groupBy { it.bar }
        val first = timed.minOf { it.bar }; val last = timed.maxOf { it.bar }
        val bars = (first..last).map { b -> byBar[b].orEmpty().let { ns -> BarTiming(b, ns.takeIf { it.isNotEmpty() }?.let(::verdict), ns) } }
        val hits = timed.mapNotNull { it.offsetMs }
        val bias = if (hits.isEmpty()) 0 else hits.average().roundToInt()
        return TimingReport(bars, line(bars, midi), bias)
    }

    /**
     * A bar's verdict: messy with a note missed or notes both early and late; rushed when at least [BAR_SHARE] of its notes
     * came early and none late (dragged the other way round); clean otherwise.
     */
    fun verdict(notes: List<TimedNote>): BarVerdict {
        val early = notes.count { it.early }; val late = notes.count { it.late }
        return when {
            notes.any { it.missed } -> BarVerdict.MESSY
            early > 0 && late > 0 -> BarVerdict.MESSY
            early > 0 && early >= notes.size * BAR_SHARE -> BarVerdict.RUSHED
            late > 0 && late >= notes.size * BAR_SHARE -> BarVerdict.DRAGGED
            else -> BarVerdict.CLEAN
        }
    }

    const val STEADY = "Your timing was steady all the way through."
    /** A run where no note came in time: nothing to say about timing (the recap's own line says what to try). */
    const val NOTHING = "No notes came in time, so there is no timing to show yet."

    /**
     * The one plain line, the strongest pattern first: one beat early (late) in two bars or more ("You rush the first beat of
     * bars 3 and 7."); the whole run early or late on a MIDI keyboard (the offset); rushed or dragged bars; the whole run a
     * little ahead or behind; uneven bars; or steady.
     */
    fun line(bars: List<BarTiming>, midi: Boolean = false): String {
        val notes = bars.flatMap { it.notes }
        val hits = notes.filter { !it.missed }
        if (hits.isEmpty()) return NOTHING
        // 1. The same beat early (or late) in two bars or more: name the beat, only for notes that sit on it.
        val pattern = listOf(true, false).flatMap { early ->
            hits.filter { if (early) it.early else it.late }.mapNotNull { n -> n.onBeat?.let { b -> Triple(early, b, n.bar) } }
        }.groupBy({ it.first to it.second }, { it.third }).mapValues { it.value.distinct().sorted() }
            .filter { it.value.size >= PATTERN_BARS }
            .maxWithOrNull(compareBy<Map.Entry<Pair<Boolean, Int>, List<Int>>> { it.value.size }.thenBy { -it.key.second })
        // 2. Every note to one side on a MIDI keyboard: most likely the keyboard's delay, which the offset is for.
        val oneSide = hits.size >= 8 && (hits.count { it.early } >= hits.size * 0.8 || hits.count { it.late } >= hits.size * 0.8)
        if (midi && oneSide) return if (hits.count { it.early } > hits.count { it.late })
            "Nearly every note came early. If it felt in time, set the MIDI timing offset in Settings."
        else "Nearly every note came late. If it felt in time, set the MIDI timing offset in Settings."
        if (pattern != null) {
            val (early, beat) = pattern.key
            return "You ${if (early) "rush" else "drag"} the ${ordinal(beat + 1)} beat of ${barList(pattern.value)}."
        }
        // 3. Bars rushed or dragged as a whole.
        val rushed = bars.filter { it.verdict == BarVerdict.RUSHED }.map { it.bar }
        val dragged = bars.filter { it.verdict == BarVerdict.DRAGGED }.map { it.bar }
        if (rushed.isNotEmpty() || dragged.isNotEmpty()) {
            return if (rushed.size >= dragged.size) "You rush ${barList(rushed)}." else "You drag ${barList(dragged)}."
        }
        // 4. A lean across the whole run, inside the bars' own tolerance.
        if (hits.size >= 4) {
            val bias = hits.map { it.offsetMs!! }.average()
            if (bias < -OFF_MS / 2.0) return "You play a little ahead of the beat all the way through."
            if (bias > OFF_MS / 2.0) return "You sit a little behind the beat all the way through."
        }
        // 5. Uneven bars (a miss, or early and late together).
        val messy = bars.filter { it.verdict == BarVerdict.MESSY }.map { it.bar }
        if (messy.isNotEmpty()) return "${barList(messy).replaceFirstChar { it.uppercase() }} ${if (messy.size == 1) "was" else "were"} uneven. Try ${if (messy.size == 1) "it" else "them"} on a loop."
        return STEADY
    }

    /** "bar 3", "bars 3 and 7", "bars 2, 5 and 9", "bars 2, 5, 9, 12 and 3 more". */
    fun barList(bars: List<Int>): String = when {
        bars.size == 1 -> "bar ${bars[0]}"
        bars.size <= NAMED_BARS -> "bars " + bars.dropLast(1).joinToString(", ") + " and " + bars.last()
        else -> "bars " + bars.take(NAMED_BARS).joinToString(", ") + " and ${bars.size - NAMED_BARS} more"
    }

    private val ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth")
    fun ordinal(n: Int) = ORDINALS.getOrNull(n - 1) ?: "number $n"

    /** Where a note falls, for its row in a bar's detail: "Beat 1", "Beat 2 and" (an eighth after), else "Beat 2.75". */
    fun beatLabel(beat: Double): String {
        val whole = floor(beat + 1e-6).toInt()
        val frac = beat - whole
        return when {
            abs(frac) <= ON_BEAT -> "Beat ${whole + 1}"
            abs(frac - 1) <= ON_BEAT -> "Beat ${whole + 2}"
            abs(frac - 0.5) <= ON_BEAT -> "Beat ${whole + 1} and"
            else -> "Beat " + "%.2f".format(java.util.Locale.ROOT, beat + 1).trimEnd('0').trimEnd('.')
        }
    }

    /** "12 ms early", "on the beat", "40 ms late", "missed" (no dashes, no signs: the words say which way). */
    fun offsetWords(offsetMs: Int?): String = when {
        offsetMs == null -> "missed"
        abs(offsetMs) <= 10 -> "on the beat"
        offsetMs < 0 -> "${-offsetMs} ms early"
        else -> "$offsetMs ms late"
    }

    /**
     * The live tick under the hit line: where a hit's mark sits, -1 (as early as the good window) to 1 (as late), 0 on the
     * beat. [offsetMs] is the hit's offset in song ms, the good window's own unit.
     */
    fun tick(offsetMs: Double): Float = (offsetMs / GOOD_MS).coerceIn(-1.0, 1.0).toFloat()
}
