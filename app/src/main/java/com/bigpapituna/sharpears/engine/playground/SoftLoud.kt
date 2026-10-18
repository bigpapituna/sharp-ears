package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice

/** How loud a note sounded to the microphone: a rough guess (the mic hears the room, not the key's speed). */
enum class Loudness { SOFT, MEDIUM, LOUD }

/**
 * Soft and loud from the microphone (2.1, Playground lesson 11), graded on a strike's rise (MicEvent.Strike.riseDb: the
 * fresh power of the note's partials in dBFS). A toy, and the lesson says so: the same key sounds louder to a phone that
 * is closer, on a brighter piano or in a smaller room. So the game asks first for a note played "the way you always do"
 * and grades the rest against it; before that note (or without one) it falls back to fixed thresholds.
 * Real dynamics need MIDI (the course's "Soft and loud" step).
 */
object SoftLoud {
    /** Without a first note to compare with: a rise at or under this is soft... */
    const val SOFT_AT_MOST_DB = -40.0
    /** ...and at or over this, loud (PianoSynth at -14 dB gain rises about -26 dB; a hard strike near the phone, more). */
    const val LOUD_AT_LEAST_DB = -28.0
    /** Against the first note: this much quieter is soft, this much louder is loud. */
    const val MARGIN_DB = 5.0

    /** What each round asks for, after the first note, which only sets the level. */
    val ROUNDS = listOf(Loudness.SOFT, Loudness.SOFT, Loudness.LOUD, Loudness.SOFT, Loudness.LOUD, Loudness.SOFT, Loudness.SOFT, Loudness.LOUD,
        Loudness.SOFT, Loudness.LOUD)

    /** A round that was not matched is let go after this many tries ("close enough"): the game never gets stuck. */
    const val TRIES = 2

    /**
     * How loud a strike that rose [riseDb] sounded. With [anchorDb] (the player's own first note) it is relative to
     * that; otherwise fixed thresholds, moved down by [offsetDb] (Profile.micOffsetDb: kids strike lighter, +3 dB).
     */
    fun grade(riseDb: Double, anchorDb: Double? = null, offsetDb: Double = 0.0): Loudness = when {
        anchorDb != null -> when {
            riseDb <= anchorDb - MARGIN_DB -> Loudness.SOFT
            riseDb >= anchorDb + MARGIN_DB -> Loudness.LOUD
            else -> Loudness.MEDIUM
        }
        riseDb <= SOFT_AT_MOST_DB - offsetDb -> Loudness.SOFT
        riseDb >= LOUD_AT_LEAST_DB - offsetDb -> Loudness.LOUD
        else -> Loudness.MEDIUM
    }

    /** The lesson's exercise: middle C once to set the level, then once per round (the notes wait, as in any game). */
    fun exercise() = Exercise(right = List(ROUNDS.size + 1) { "C4:2" }.joinToString(" "), bpm = 60.0, practice = Practice.RIGHT)
}

/**
 * The verdict on one note: how loud it sounded, whether that is what the round asked for, whether the game moves on,
 * and whether it woke the sleeping pup (anything loud does, even in a soft round).
 */
data class Verdict(val heard: Loudness, val target: Loudness?, val match: Boolean, val advance: Boolean, val woke: Boolean)

/**
 * The soft-and-loud game as a pure state machine: [heard] each strike's rise. The first note sets the level; then each
 * round wants [SoftLoud.ROUNDS] in turn. A soft round takes anything that is not loud (the pup only stirs); a loud round
 * takes only loud. A round not matched in [SoftLoud.TRIES] tries is let go anyway, so nobody is stuck on a toy.
 */
class SoftLoudGame(val rounds: List<Loudness> = SoftLoud.ROUNDS, private val offsetDb: Double = 0.0) {
    /** The first note's rise: the level everything after is compared with. */
    var anchorDb: Double? = null
        private set
    /** -1 while the first note is awaited, then the round being played. */
    var round = -1
        private set
    var tries = 0
        private set
    /** Rounds matched at the first or a later try (not let go). */
    var matched = 0
        private set
    val done get() = round >= rounds.size
    /** What the current round asks for (null for the first note). */
    val target: Loudness? get() = rounds.getOrNull(round)

    /**
     * The round goes by with nothing heard for it (2.1.1: the stage's quiet tries ran out). The first note: no level of
     * their own, so the fixed thresholds grade the rest. A round: moved on, not matched.
     */
    fun skip() {
        check(!done) { "the game is over" }
        if (round < 0) round = 0 else round++
        tries = 0
    }

    fun heard(riseDb: Double): Verdict {
        check(!done) { "the game is over" }
        if (round < 0) {
            anchorDb = riseDb
            round = 0
            return Verdict(Loudness.MEDIUM, null, match = true, advance = true, woke = false)
        }
        val target = rounds[round]
        val heard = SoftLoud.grade(riseDb, anchorDb, offsetDb)
        val match = if (target == Loudness.LOUD) heard == Loudness.LOUD else heard != Loudness.LOUD
        tries++
        val advance = match || tries >= SoftLoud.TRIES
        if (match) matched++
        if (advance) { round++; tries = 0 }
        return Verdict(heard, target, match, advance, woke = heard == Loudness.LOUD)
    }
}
