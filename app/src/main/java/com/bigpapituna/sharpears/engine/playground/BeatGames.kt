package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.isBlack
import kotlin.math.abs

/*
 * 2.3 part B, the night sky's beat games: a key on every beat (the steady beat at three speeds, the metronome game),
 * where each beat is a star that lights when a key lands on it, and the black-key jam, where every note is a star and
 * the tune is drawn across the sky. Pure rules; ui/playground BeatStarsStage and StarJamStage play and draw them.
 */

/** What keeps the beat: the pup's drum, or the metronome's click (high on beat one). */
enum class BeatSound { DRUM, CLICK }

/**
 * A key on every beat: [hits] (beats from the first counted beat, after a bar of count-in) at [bpm]. A key heard
 * within [windowMs] of a hit lights its star ([heard]); a key between them lights nothing and costs nothing. The run's
 * report counts the stars lit (its own score the share of them, as the drum's claps): a toy that never fails.
 */
class BeatStars(val hits: List<Double>, val bpm: Double, val windowMs: Double = WINDOW_MS) {
    init { require(hits.isNotEmpty()) { "a beat game needs a beat" } }

    val beatMs get() = 60_000.0 / bpm
    /** Hits with a key on them, by index. */
    val lit: Set<Int> get() = litSet
    private val litSet = sortedSetOf<Int>()
    /** Keys heard in this try, on a star or between them. */
    var keys = 0; private set

    /** A key at [ms] from the first counted beat: the star it lit (null between stars, or one already lit). */
    fun heard(ms: Double): Int? {
        keys++
        val i = hits.indices.minByOrNull { abs(ms - hits[it] * beatMs) } ?: return null
        if (abs(ms - hits[i] * beatMs) > windowMs || i in litSet) return null
        litSet += i
        return i
    }

    /** How early (negative) or late a key at [ms] was against its nearest hit, in ms. */
    fun offset(ms: Double): Double = hits.map { ms - it * beatMs }.minBy { abs(it) }

    /** From the first count-in beat to the end of the last hit's beat, in ms. */
    val lengthMs get() = (COUNT_IN + kotlin.math.ceil(hits.last() + 1.0)) * beatMs

    fun reset() { litSet.clear(); keys = 0 }

    fun report(): Report = Report(emptyList(), 0, 0, null, groups = litSet.size, extraScore = litSet.size * 100 / hits.size)

    companion object {
        /** A bar of count-in beats before the first star. */
        const val COUNT_IN = 4
        /** In time: within a quarter of a second of the beat (a child's beat, and the microphone's own delay after the delay is taken off). */
        const val WINDOW_MS = 250.0
    }
}

/** The black keys a child can reach in the two octaves from middle C (the jam listens for these: ten, under the cap of twelve). */
val JAM_KEYS: Set<Int> = (61..82).filter(::isBlack).toSet()

/** A star the jam drew: the note's [midi], where it sits across the sky ([x], 0..1 as the band plays) and up it ([y], 0 low .. 1 high). */
data class JamStar(val midi: Int, val x: Float, val y: Float)

/**
 * The black-key jam (night 11): the band plays [bars] of four beats at [bpm]; every black key heard ([heard]) draws a
 * star at its height, across the sky as the band goes on. A white key draws nothing (the pup points at the black ones,
 * once). No grading beyond "you played": the report's own score is full once a note was played, its groups the notes.
 * Nothing played for [quietMs] is a try (the pup plays a few notes again); [maxTries] quiet tries end the jam early.
 */
class StarJam(val bars: Int, val bpm: Double, val quietMs: Long = 7_000, val maxTries: Int = PLAYGROUND_TRIES) {
    val beatMs get() = 60_000.0 / bpm
    val lengthMs get() = bars * 4 * beatMs
    val stars = mutableListOf<JamStar>()
    /** White keys heard (no star). */
    var whites = 0; private set
    var tries = 0; private set
    private var lastMs = 0.0

    /** The band starts: the quiet clock with it. */
    fun start() { lastMs = 0.0 }

    /** A key [midi] at [ms] from the band's start: the star it drew, or null for a white key. */
    fun heard(midi: Int, ms: Double): JamStar? {
        lastMs = ms
        if (!isBlack(midi)) { whites++; return null }
        val star = JamStar(midi, (ms / lengthMs).toFloat().coerceIn(0f, 1f), ((midi - 54) / 34f).coerceIn(0.05f, 0.95f))
        stars += star
        return star
    }

    sealed interface Tick {
        /** A quiet stretch: the pup plays a few notes to start you off again. */
        data object Nudge : Tick
        /** Out of quiet tries: the jam ends early ("Good try, let's keep going"). */
        data object LetGo : Tick
        /** The band reached its end. */
        data object End : Tick
    }

    /** The band is at [ms]: a quiet stretch, the end, or nothing. */
    fun tick(ms: Double): Tick? = when {
        ms >= lengthMs -> Tick.End
        ms - lastMs >= quietMs -> { tries++; lastMs = ms; if (tries >= maxTries) Tick.LetGo else Tick.Nudge }
        else -> null
    }

    fun report(): Report = Report(emptyList(), 0, 0, null, groups = stars.size, extraScore = if (stars.isNotEmpty()) 100 else 0)

    companion object {
        /**
         * The band: one bar, in G flat major's five notes (the black keys'), low enough to stay out of the keys the jam
         * listens for: (beat, keys) a bass on one and three, a soft chord on two and four.
         */
        val BAND_BAR: List<Pair<Int, List<Int>>> = listOf(0 to listOf(42), 1 to listOf(49, 54, 58), 2 to listOf(49), 3 to listOf(46, 51, 54))
        /** The microphone is deaf this long from each of the band's notes (a note's attack must not be the child's). */
        const val DEAF_MS = 300L
        /** A little tune the pup plays to start you off: black keys only. */
        val NUDGE = listOf(66, 68, 70, 73, 70, 68)
    }
}
