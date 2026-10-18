package com.bigpapituna.sharpears.engine.playground

/*
 * The 2.2 pages' games that hear more than one key a round (the pond and the concert hall): the copy games, where a
 * round is a little tune played back note by note (StageRounds with phrases), the pond's first chord, three keys at
 * once, and the soft and loud tune, Ode to Joy's first phrase graded for loudness as a whole. Pure rules: the stages in
 * ui/playground feed them what the microphone heard, PlaygroundFlowTest and StageHearingTest drive them on the JVM.
 */

/**
 * A copy game or a tune (pond 3, hall 6 and 9): the phrase's next note. A key the matcher confirmed or named counts when
 * it is the [expected] key, or the same note in another octave (a tune played back an octave up is still the tune);
 * any other key is a try. A pitch guess or a plain onset is only heard.
 */
object NoteJudge {
    fun judge(sound: StageSound, expected: Int?): Answer {
        if (sound.by != HeardBy.MATCHER && sound.by != HeardBy.NAMED) return Answer.Heard
        val key = sound.midi ?: return Answer.Heard
        val want = expected ?: return Answer.Heard
        return if (Math.floorMod(key - want, 12) == 0) Answer.Found(want) else Answer.Miss(MissWhy.OTHER_NOTE, key)
    }
}

/**
 * The pond's first chord (lesson 9): the round is found once every key of [chord] has been heard within [windowMs] of
 * each other (the matcher listens for the chord as one group, so each key comes as its own strike). A chord key heard
 * alone is progress, shown lit ([lit]); a key outside the chord is a try, unless it came with chord keys (a hand that
 * landed a little wide is still the chord's moment). Times are any forward clock.
 */
class ChordJudge(val chord: Set<Int>, val windowMs: Long = 1_200) {
    private val heard = HashMap<Int, Long>()

    /** The chord's keys heard so far in this moment. */
    val lit: Set<Int> get() = heard.keys.toSet()

    fun judge(sound: StageSound): Answer {
        val key = sound.midi
        if (key == null || (sound.by != HeardBy.MATCHER && sound.by != HeardBy.NAMED)) return Answer.Heard
        forget(sound.atMs)
        if (key in chord && sound.by == HeardBy.MATCHER) {
            heard[key] = sound.atMs
            if (!heard.keys.containsAll(chord)) return Answer.Heard
            heard.clear()
            return Answer.Found(chord.min())
        }
        if (key in chord) return Answer.Heard
        return if (heard.isNotEmpty()) Answer.Heard else Answer.Miss(MissWhy.NOT_CHORD, key)
    }

    /** Some of the chord was heard at least [windowMs] ago and the rest never came: when the first of it was. */
    fun partialSince(nowMs: Long): Long? = heard.values.minOrNull()?.takeIf { nowMs - it >= windowMs }

    /** Forget keys older than the window (the moment is over). */
    fun forget(nowMs: Long) { heard.entries.removeAll { nowMs - it.value > windowMs } }

    fun reset() = heard.clear()
}

/**
 * What the soft and loud tune made of a phrase: how loud it sounded ([levelDb], its notes' middle rise), what the round
 * wanted, and whether that matched.
 */
data class TuneVerdict(val heard: Loudness, val target: Loudness?, val match: Boolean, val levelDb: Double) {
    /** A loud phrase wakes the pup, whatever the round asked. */
    val woke get() = heard == Loudness.LOUD
}

/**
 * Soft and loud for real (the concert hall's lesson 6): Ode to Joy's first phrase once the usual way (it sets the
 * level), then softly, loudly and softly again. Each note's rise is kept ([note]); when a phrase is done its middle rise is graded
 * like the garden's single notes ([SoftLoud.grade], against the first phrase). A soft round wants soft (in between
 * only stirs the pup), a loud round wants loud. A toy: it never holds the lesson up, it only shows how it sounded.
 */
class TuneLoudness(val rounds: List<Loudness?> = ROUNDS, private val offsetDb: Double = 0.0) {
    /** The first phrase's middle rise. */
    var anchorDb: Double? = null
        private set
    private val rises = ArrayList<Double>()

    /** A note of the phrase was heard with this rise. */
    fun note(riseDb: Double) { rises += riseDb }

    /** Notes kept for the phrase so far. */
    val notes get() = rises.size

    /** Phrase [round] is done: its verdict (the first only sets the level). Null when no note was heard (let go). */
    fun phraseDone(round: Int): TuneVerdict? {
        if (rises.isEmpty()) return null
        val level = rises.sorted()[rises.size / 2]
        rises.clear()
        val target = rounds.getOrNull(round)
        if (target == null) {
            if (round == 0) anchorDb = level
            return TuneVerdict(Loudness.MEDIUM, null, match = true, levelDb = level)
        }
        val heard = SoftLoud.grade(level, anchorDb, offsetDb)
        return TuneVerdict(heard, target, heard == target, level)
    }

    /** The phrase went by without being played (three tries): nothing to grade. */
    fun skipped() = rises.clear()

    companion object {
        /** The usual way, then soft, then loud, then soft again (a lullaby to end on). */
        val ROUNDS: List<Loudness?> = listOf(null, Loudness.SOFT, Loudness.LOUD, Loudness.SOFT)
    }
}
