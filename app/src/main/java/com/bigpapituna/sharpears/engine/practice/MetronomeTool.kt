package com.bigpapituna.sharpears.engine.practice

import kotlin.random.Random

/*
 * The metronome tool's logic: tempo limits, tap tempo, time signatures with accent patterns, subdivisions, a
 * trainer that speeds up every few bars, and random muted bars. Pure: [TickSchedule] only says WHEN each tick
 * falls (milliseconds since the start) and what kind it is; the screen plays them against the wall clock.
 */

const val MIN_BPM = 30
const val MAX_BPM = 300

fun clampBpm(bpm: Int) = bpm.coerceIn(MIN_BPM, MAX_BPM)

/** How strongly a tick sounds. STRONG is the bar's first beat, MEDIUM a secondary stress, WEAK any other beat, SUB a subdivision. */
enum class TickLevel { STRONG, MEDIUM, WEAK, SUB }

/**
 * A time signature: [beats] clicks per bar at the tempo, the pulse being the note value [unit] (4 = quarter, 8 =
 * eighth). Tempo counts pulses: 6/8 at 120 clicks six eighths per bar at 120 per minute. [accents] gives each
 * beat its level (the grouping: 3+2 in 5/4, 2+2+3 in 7/8).
 */
enum class TimeSig(val label: String, val beats: Int, val unit: Int, val grouping: String, val accents: List<TickLevel>) {
    FOUR_FOUR("4/4", 4, 4, "Four beats, a lighter stress on the third", listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK)),
    THREE_FOUR("3/4", 3, 4, "Three beats, stress on the first", listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.WEAK)),
    SIX_EIGHT("6/8", 6, 8, "Clicks every eighth note, in two groups of three", listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK, TickLevel.WEAK)),
    FIVE_FOUR("5/4", 5, 4, "Five beats, grouped three plus two", listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK)),
    SEVEN_EIGHT("7/8", 7, 8, "Clicks every eighth note, grouped two, two and three", listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK, TickLevel.WEAK));

    init { require(accents.size == beats) }
}

/** Clicks per beat: 1 none, 2 eighths, 3 triplets, 4 sixteenths. */
enum class Subdivision(val perBeat: Int, val label: String) {
    NONE(1, "Off"), EIGHTHS(2, "Eighths"), TRIPLETS(3, "Triplets"), SIXTEENTHS(4, "Sixteenths");
}

/** Speed the tempo up by [stepBpm] after every [everyBars] bars, up to [maxBpm]. */
data class Trainer(val stepBpm: Int = 5, val everyBars: Int = 4, val maxBpm: Int = MAX_BPM) {
    init { require(stepBpm >= 1 && everyBars >= 1) }
}

/**
 * Silence some bars at random: each bar after the first is muted with probability [chance] (0..1), but never
 * more than [maxInARow] in a row, so you keep your own time through the gap and then check it against the click.
 */
data class RandomMute(val chance: Double = 0.25, val maxInARow: Int = 1) {
    init { require(chance in 0.0..1.0 && maxInARow >= 1) }
}

data class MetronomeConfig(
    val bpm: Int = 80,
    val timeSig: TimeSig = TimeSig.FOUR_FOUR,
    val subdivision: Subdivision = Subdivision.NONE,
    val trainer: Trainer? = null,
    val mute: RandomMute? = null,
    val seed: Long = 1,
)

/**
 * One click. [timeMs] counts from the start. [bar] and [beat] are 0-based; [sub] is 0 on the beat itself. [muted]
 * ticks keep their place (the display still moves) but make no sound. [bpm] is the tempo at that tick.
 */
data class Tick(val timeMs: Double, val bar: Int, val beat: Int, val sub: Int, val level: TickLevel, val muted: Boolean, val bpm: Int) {
    val onBeat get() = sub == 0
}

/** The endless sequence of ticks for a [MetronomeConfig], one at a time or a window at a time. */
class TickSchedule(val config: MetronomeConfig) {
    private val rnd = Random(config.seed)
    private var bar = 0
    private var beat = 0
    private var sub = 0
    private var time = 0.0
    private var bpm = clampBpm(config.bpm)
    private var mutedBars = 0
    private var barMuted = false

    init { startBar() }

    private fun startBar() {
        val m = config.mute
        barMuted = bar > 0 && m != null && mutedBars < m.maxInARow && rnd.nextDouble() < m.chance
        mutedBars = if (barMuted) mutedBars + 1 else 0
    }

    /** The next tick; call again for the one after. */
    fun next(): Tick {
        val level = if (sub > 0) TickLevel.SUB else config.timeSig.accents[beat]
        val tick = Tick(time, bar, beat, sub, level, barMuted, bpm)
        // Advance one subdivision: the beat lasts 60000 / bpm and splits evenly.
        time += 60_000.0 / bpm / config.subdivision.perBeat
        if (++sub == config.subdivision.perBeat) {
            sub = 0
            if (++beat == config.timeSig.beats) {
                beat = 0
                bar++
                val t = config.trainer
                if (t != null && bar % t.everyBars == 0) bpm = minOf(clampBpm(bpm + t.stepBpm), clampBpm(t.maxBpm))
                startBar()
            }
        }
        return tick
    }

    /** Ticks whose time falls before [untilMs], continuing from where the last call stopped. */
    fun takeUntil(untilMs: Double): List<Tick> {
        val out = ArrayList<Tick>()
        while (time < untilMs) out += next()
        return out
    }

    /** The time the next tick will fall at. */
    val nextTimeMs get() = time

    /** Changes the tempo from the next beat on (a bar already under way keeps its place). */
    fun setBpm(newBpm: Int) { bpm = clampBpm(newBpm) }
}

/** The classical word for a tempo. */
fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    else -> "Presto"
}

/** Ticks in the first [ms] milliseconds of a fresh schedule (a convenience for tests and previews). */
fun ticksIn(config: MetronomeConfig, ms: Double): List<Tick> = TickSchedule(config).takeUntil(ms)

/**
 * Tap tempo: the median of the gaps between the last few taps, so one stray tap does not wreck the estimate.
 * A pause of more than [resetMs] starts a new count.
 */
class TapTempo(private val keep: Int = 6, private val resetMs: Long = 2500) {
    private val taps = ArrayDeque<Long>()

    /** A tap at [nowMs]; returns the tempo once there are two taps, otherwise null. */
    fun tap(nowMs: Long): Int? {
        if (taps.isNotEmpty() && nowMs - taps.last() > resetMs) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > keep + 1) taps.removeFirst()
        if (taps.size < 2) return null
        val gaps = taps.zipWithNext { a, b -> b - a }.sorted()
        val median = if (gaps.size % 2 == 1) gaps[gaps.size / 2].toDouble() else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2.0
        if (median <= 0) return null
        return clampBpm(Math.round(60_000.0 / median).toInt())
    }

    fun reset() = taps.clear()
}
