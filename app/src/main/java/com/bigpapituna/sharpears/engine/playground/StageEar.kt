package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.isBlack
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * How the Playground's stages hear (2.1.1). Roy, after the kids' first go: "sometimes playing the notes doesn't lead
 * to anything". The stages listened through the pitch tracker alone (PitchHeight, tuned on synthetic audio), which on a
 * real piano in a real room missed strikes and named others. Now the proven note matcher is the judge, asked about a
 * small set of keys per stage (StageKeys, at most 12, the cost cap) with NoteMatcher.strictKeys on (a key outside the
 * set must not pass for one inside it). Every clear sound still gets an answer: a key the matcher does not listen for
 * is named by its wrong-key search, a sound it cannot name is a plain onset, and the pitch tracker adds a guess only
 * when it is sure. StageEar turns those reports into one StageSound per moment; the judges below say what a round
 * makes of it.
 */

/** The keys each stage asks the note matcher about: each its own group, at most MIC_ANY_OF_LIMIT (12). */
object StageKeys {
    /** "High": a spread above middle C, C5 to G6. */
    val HIGH = listOf(72, 76, 79, 84, 88, 91)
    /** "Low": a spread below it, C2 to G3. */
    val LOW = listOf(36, 40, 43, 48, 52, 55)
    /** High and low listen to both sides at once, so a key on the wrong side is heard as itself. */
    val HIGH_LOW: Set<Int> = (LOW + HIGH).toSet()
    /** The two-black groups in the two octaves around middle C: C#4 D#4, C#5 D#5. */
    val DOG_HOUSES: Set<Int> = setOf(61, 63, 73, 75)
    /** The three-black groups there: F#4 G#4 A#4, F#5 G#5 A#5. */
    val FENCES: Set<Int> = setOf(66, 68, 70, 78, 80, 82)
    /** Every C from C2 to C6. */
    val CS: Set<Int> = setOf(36, 48, 60, 72, 84)
    /** Every key of pitch class [pc] from octave 2 to 6 (the concert hall's letter hunts: every D, every E...). */
    fun letter(pc: Int): Set<Int> = (2..6).map { 12 * (it + 1) + pc }.toSet()
    /** The C position, C4 to G4: the copy games and the soft and loud tune (2.2) listen for these. */
    val FIVE: Set<Int> = setOf(60, 62, 64, 65, 67)
    /** The pond's first chord, C4 E4 G4: one group, so its keys' shared partials never count against each other. */
    val C_CHORD: Set<Int> = setOf(60, 64, 67)
    /** Soft and loud: the white keys from A3 to G4 and the dog house between them; the game grades the rise. */
    val SOFT_LOUD: Set<Int> = setOf(57, 59, 60, 61, 62, 63, 64, 65, 67)

    fun forHunt(hunt: Hunt): Set<Int> = when (hunt) {
        Hunt.DOG_HOUSE -> DOG_HOUSES
        Hunt.FENCE -> FENCES
        else -> letter(hunt.pitchClasses.single())
    }
}

/** Where a stage's sound came from, the surest first. */
enum class HeardBy {
    /** The note matcher confirmed a key it listens for. */
    MATCHER,
    /** Its wrong-key search named a key it does not listen for (a confident piano strike). */
    NAMED,
    /** The pitch tracker heard a clear pitch; the matcher found no key in it. */
    PITCH,
    /** A clear onset and no key: talking, a clap, a knock, a key too soft to name. */
    ONSET,
}

/**
 * One moment a stage heard, at [atMs]: [midi] when a key is known (matcher or named), [guess] the pitch tracker's
 * (fractional midi), [riseDb] the strike's rise. Not [final]: the moment is shown at once (a ripple, the pup's ear)
 * but a confirmed key may still arrive for it; final: the round may judge it.
 */
data class StageSound(val atMs: Long, val by: HeardBy, val midi: Int? = null, val guess: Double? = null, val riseDb: Double? = null,
                      val final: Boolean = false) {
    /** The key to show it at: the one heard, the guess, or null (show it in the middle). */
    val near: Int? get() = midi ?: guess?.roundToInt()
}

/**
 * Turns what the microphone reports while a stage listens (note matcher strikes and named wrong keys, its plain
 * onsets, the pitch tracker's notes) into one [StageSound] per moment. A matcher strike is decided at once. Anything
 * else is shown at once and decided [mergeMs] later ([poll]), unless a strike arrives meanwhile and replaces it (a bass
 * note's strike can come after the onset was already reported as noise). Reports within [mergeMs] of a decided moment
 * are the same sound (a second key of a chord, the onset of a strike) and give nothing. The pitch tracker counts only
 * at [pitchClarity] or clearer. Times are any forward clock (the stage's uptime; the tests' own).
 */
class StageEar(val mergeMs: Long = 300, val pitchClarity: Double = 0.9, val chords: Boolean = false) {
    private var pending: StageSound? = null
    private var decidedAt = Long.MIN_VALUE / 2
    /** [chords]: when each key was last struck. */
    private val struckAt = HashMap<Int, Long>()

    /**
     * The matcher confirmed [midi]. With [chords] (2.2, the pond's chord) every key of a chord is a sound of its own:
     * only the same key again within [mergeMs] is the same strike.
     */
    fun strike(midi: Int, riseDb: Double, atMs: Long): List<StageSound> {
        if (chords) {
            if (struckAt[midi]?.let { atMs - it < mergeMs } == true) return emptyList()
            struckAt[midi] = atMs
            pending = null
            decidedAt = atMs
            return listOf(StageSound(atMs, HeardBy.MATCHER, midi, riseDb = riseDb, final = true))
        }
        if (atMs - decidedAt < mergeMs) return emptyList()
        pending = null
        decidedAt = atMs
        return listOf(StageSound(atMs, HeardBy.MATCHER, midi, riseDb = riseDb, final = true))
    }

    /** The matcher's wrong-key search named [midi] (MicEvent.Wrong). */
    fun wrong(midi: Int, riseDb: Double, atMs: Long): List<StageSound> {
        if (atMs - decidedAt < mergeMs) return emptyList()
        val p = pending
        if (p != null && atMs - p.atMs < mergeMs) {
            // The pitch tracker spoke first; the named key is surer and takes its place, shown already.
            pending = StageSound(p.atMs, HeardBy.NAMED, midi, riseDb = riseDb)
            return listOf(pending!!)
        }
        return listOf(StageSound(atMs, HeardBy.NAMED, midi, riseDb = riseDb).also { pending = it })
    }

    /** A loudness jump with no key in it (the matcher's noise). */
    fun noise(atMs: Long): List<StageSound> {
        if (atMs - decidedAt < mergeMs) return emptyList()
        val p = pending
        if (p != null && atMs - p.atMs < mergeMs) return emptyList()
        return listOf(StageSound(atMs, HeardBy.ONSET).also { pending = it })
    }

    /** The pitch tracker heard [midi] (fractional) with [clarity] 0..1. */
    fun pitch(midi: Double, clarity: Double, atMs: Long): List<StageSound> {
        if (clarity < pitchClarity || atMs - decidedAt < mergeMs) return emptyList()
        val p = pending
        if (p != null && atMs - p.atMs < mergeMs) {
            // A plain onset gains a guess; a named key stays as it is.
            if (p.by == HeardBy.ONSET) { pending = p.copy(by = HeardBy.PITCH, guess = midi); return listOf(pending!!) }
            return emptyList()
        }
        return listOf(StageSound(atMs, HeardBy.PITCH, guess = midi).also { pending = it })
    }

    /** The moment waiting since [mergeMs] or more before [nowMs], decided (final); empty when there is none. */
    fun poll(nowMs: Long): List<StageSound> {
        val p = pending ?: return emptyList()
        if (nowMs - p.atMs < mergeMs) return emptyList()
        pending = null
        decidedAt = p.atMs
        return listOf(p.copy(final = true))
    }

    /** Forget a moment in progress (the mic went deaf for a demo, a round ended). */
    fun clear() { pending = null }
}

/** Why a try was not the round's answer: the pup's line says which. */
enum class MissWhy {
    /** High and low: the other side of middle C. */
    OTHER_SIDE,
    /** High and low: middle C itself, neither side. */
    MIDDLE,
    /** A black-key hunt: a white key. */
    WHITE_KEY,
    /** A black-key hunt: the other black group (a fence for a dog house). */
    OTHER_GROUP,
    /** The C hunt: another key. */
    NOT_C,
    /** A letter hunt (D, E, F, G): another key. */
    NOT_LETTER,
    /** A copy game or a tune: not the next note. */
    OTHER_NOTE,
    /** The chord: a key that is not one of its three. */
    NOT_CHORD,
    /** Soft and loud: loud in a soft round (he woke up). */
    TOO_LOUD,
    /** Soft and loud: not loud enough in a loud round. */
    TOO_SOFT,
}

/** What a round makes of a decided sound. */
sealed interface Answer {
    /** The round's answer: [key] completes it (a key of the round's group). */
    data class Found(val key: Int) : Answer
    /** A try that was not it. [key] is what was heard, when known. */
    data class Miss(val why: MissWhy, val key: Int?) : Answer
    /** Heard, nothing to judge (no key): the pup's ear, no try counted. */
    data object Heard : Answer
}

/**
 * High and low (lesson 1): any key on the round's side of middle C. A key the matcher confirmed or named counts as
 * itself; the pitch tracker's guess counts only when it is at least [clearSide] semitones from middle C (its error is
 * under a semitone on PianoSynth; a guess near the middle is not a side).
 */
object HighLowJudge {
    const val MIDDLE_C = 60
    const val CLEAR_SIDE = 2.5

    fun judge(sound: StageSound, wantHigh: Boolean, clearSide: Double = CLEAR_SIDE): Answer {
        val key = when (sound.by) {
            HeardBy.MATCHER, HeardBy.NAMED -> sound.midi ?: return Answer.Heard
            HeardBy.PITCH -> sound.guess?.takeIf { abs(it - MIDDLE_C) >= clearSide }?.roundToInt() ?: return Answer.Heard
            HeardBy.ONSET -> return Answer.Heard
        }
        return when {
            key == MIDDLE_C -> Answer.Miss(MissWhy.MIDDLE, key)
            (key > MIDDLE_C) == wantHigh -> Answer.Found(key)
            else -> Answer.Miss(MissWhy.OTHER_SIDE, key)
        }
    }
}

/**
 * The key hunts (the garden's lessons 2 and 3, the concert hall's letters): a key of the hunted group. The matcher listens for [StageKeys.forHunt]; a named key
 * of the same group in another octave counts too (any dog house will do), handed to the round as the listened-for key
 * of its pitch class nearest to it. A named white key in a black-key hunt is [MissWhy.WHITE_KEY] (the pup points at the
 * black ones); a pitch-tracker guess is never a find, only heard.
 */
object HuntJudge {
    fun judge(sound: StageSound, hunt: Hunt, members: Set<Int> = StageKeys.forHunt(hunt)): Answer {
        if (sound.by != HeardBy.MATCHER && sound.by != HeardBy.NAMED) return Answer.Heard
        val key = sound.midi ?: return Answer.Heard
        if (key in members) return Answer.Found(key)
        if (Math.floorMod(key, 12) in hunt.pitchClasses) {
            val same = members.filter { Math.floorMod(it - key, 12) == 0 }.minByOrNull { abs(it - key) }
            if (same != null) return Answer.Found(same)
        }
        return Answer.Miss(when {
            hunt == Hunt.C -> MissWhy.NOT_C
            hunt.letter != null -> MissWhy.NOT_LETTER
            !isBlack(key) -> MissWhy.WHITE_KEY
            else -> MissWhy.OTHER_GROUP
        }, key)
    }
}
