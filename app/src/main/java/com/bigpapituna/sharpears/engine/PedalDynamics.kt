package com.bigpapituna.sharpears.engine

import kotlin.math.roundToInt

/**
 * The sustain-pedal step: each chord needs a fresh press of the pedal soon after it is struck (the pedal comes
 * up as the chord changes and goes down again within [windowMs] of the new chord), and the pedal down while it
 * rings (at its middle). Pedal times are song ms. Score: chords pedalled right, in percent.
 */
class PedalGrader(private val chords: List<LongRange>, private val windowMs: Long = 300) {
    /** (song ms, down) in the order they came. */
    val events = mutableListOf<Pair<Double, Boolean>>()

    fun pedal(down: Boolean, atMs: Double) {
        if (events.lastOrNull()?.second == down) return       // repeats of the same state carry nothing
        events += atMs to down
    }

    /** Was the pedal down at [ms]? */
    fun downAt(ms: Double): Boolean = events.lastOrNull { it.first <= ms }?.second ?: false

    /** Chord [i] was pedalled right: pressed within the window after it starts, after a lift, and down at its middle. */
    fun chordOk(i: Int): Boolean {
        val c = chords[i]
        val start = c.first.toDouble()
        val press = events.indexOfFirst { (t, down) -> down && t >= start - 60 && t <= start + windowMs }
        if (press < 0) return false
        // The press must be fresh: the pedal was up just before it (it is the first event, or follows a lift).
        if (press > 0 && events[press - 1].second) return false
        return downAt((c.first + c.last) / 2.0)
    }

    fun score(): Int = if (chords.isEmpty()) 0 else (chords.indices.count(::chordOk) * 100.0 / chords.size).roundToInt()

    fun chordSpans(): List<LongRange> = chords

    companion object {
        /** Chord spans from a song: each onset group of the right hand, ending where the next begins (or at the end). */
        fun chordsOf(song: Song): List<LongRange> {
            val starts = song.groups.filter { g -> g.notes.any { it.hand == Hand.RIGHT } }.map { it.startMs }
            return starts.mapIndexed { i, s -> s..(starts.getOrNull(i + 1) ?: song.durationMs) }
        }
    }
}

/**
 * The soft-and-loud step: a phrase played twice, the first time under [softBelow] velocity, the second over
 * [loudAbove]. Notes are graded in order as they are played. Score: notes in range, in percent.
 */
class DynamicsGrader(private val notes: Int, val softBelow: Int = 50, val loudAbove: Int = 90) {
    val velocities = mutableListOf<Int>()
    private val half get() = notes / 2

    /** The next note should be soft (first half) or loud (second half); null once every note is in. */
    fun nextIsSoft(): Boolean? = if (velocities.size >= notes) null else velocities.size < half

    /** A right note at [velocity]: true when it was in range. */
    fun note(velocity: Int): Boolean {
        if (velocities.size >= notes) return false
        val soft = velocities.size < half
        velocities += velocity
        return if (soft) velocity < softBelow else velocity > loudAbove
    }

    fun inRange() = velocities.withIndex().count { (i, v) -> if (i < half) v < softBelow else v > loudAbove }

    fun score(): Int = if (notes == 0) 0 else (inRange() * 100.0 / notes).roundToInt()
}
