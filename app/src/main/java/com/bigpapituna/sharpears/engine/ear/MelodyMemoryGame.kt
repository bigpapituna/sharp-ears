package com.bigpapituna.sharpears.engine.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.noteName
import java.util.Random
import kotlin.math.abs

/**
 * Melody memory, Simon style. One melody in one key is drawn for the whole game; round 1 plays its first notes
 * and every round adds one more. The player plays it back from the start, on the keys (touch, MIDI or mic). A wrong
 * note restarts the try (and the app plays the melody again); after two failed tries the notes are shown, and the
 * round gives no point. The melody keeps growing either way, so a game is always [rounds] long.
 *
 * Level 0: C major, five notes under the hand, steps and now and then a skip, starts on the tonic, two notes to
 * begin. Level 1: an octave in C, G, F or D major, leaps up to a fourth, three notes to begin. Level 2: about an
 * octave and a half, major and minor keys, leaps up to a fifth.
 */
class MelodyMemoryGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : TrainerBase(EarKind.MELODY, level, rounds) {
    private val rnd = Random(seed)
    val tonicPc: Int
    val minor: Boolean
    /** The notes of the key the melody may use, ascending. */
    val pool: List<Int>
    /** The largest jump, in steps of the scale. */
    val maxLeap = when (level) { 0 -> 2; 1 -> 3; else -> 4 }
    val startLength = if (level == 0) 2 else 3
    /** The whole melody; each round plays one more note of it. */
    val full: List<Int>

    init {
        val keys: List<Pair<Int, Boolean>> = when (level) {
            0 -> listOf(0 to false)
            1 -> listOf(0 to false, 7 to false, 5 to false, 2 to false)
            else -> listOf(0 to false, 7 to false, 5 to false, 10 to false, 9 to true, 4 to true, 2 to true, 0 to true)
        }
        val (pc, mn) = keys[rnd.nextInt(keys.size)]
        tonicPc = pc; minor = mn
        val t = tonicMidi(pc)
        pool = when (level) {
            0 -> scaleNotes(pc, mn, t, t + 7).take(5)
            1 -> scaleNotes(pc, mn, t, t + 12)
            else -> scaleNotes(pc, mn, t - 5, t + 14)
        }
        val walk = MelodyWalk(pool, MelodyWalk.stepsFor(level), rnd, start = if (level < 2) 0 else rnd.nextInt(pool.size))
        full = buildList { repeat(rounds + startLength - 1) { add(walk.next()) } }
    }

    var progress = 0; private set
    var attempts = 0; private set
    /** The melody of this round. */
    val melody: List<Int> get() = full.take(startLength + round)
    val notesShown get() = attempts >= 2
    val keyLabel get() = keyName(tonicPc, minor)

    override val answering get() = Answering.KEYS
    override val replayOnWrong get() = true

    init { newRound() }

    override fun newRound() { progress = 0; attempts = 0 }

    override fun prompt(): GameLine {
        val n = melody.size
        return GameLine("Play it back: $n notes in $keyLabel", "Play it back: $n notes, in ${keySpoken(tonicPc, minor)}.")
    }

    override fun toPlay() = melody.map { Step(listOf(it), 480, 70) }

    override fun lit(): Map<Int, Lit> = buildMap {
        if (level == 0 && progress == 0 && !roundOver) put(melody[0], Lit.GIVEN)       // the first level shows where to start
        if (notesShown || roundOver) melody.forEach { put(it, Lit.SHOWN) }
        melody.take(progress).forEach { put(it, Lit.FOUND) }
    }

    override fun micNotes() = if (roundOver) emptySet() else melody.getOrNull(progress)?.let { setOf(it) }.orEmpty()
    override fun micGroups() = listOf(micNotes())
    override fun describe() = melody.joinToString(" ") { noteName(it) }

    /** The melody's notes by letter, no octave: "C D E G". */
    private fun letters() = melody.joinToString(" ") { spell(it % 12, flatKey(tonicPc, minor)) }

    override fun input(midi: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        if (midi != melody[progress]) {
            attempts++; wrongs++
            progress = 0
            val line = if (attempts == 2) "Not quite. Here are the notes, from the start." else "Not quite. Listen again, then from the start."
            return wrong(GameLine(line))
        }
        progress++
        if (progress < melody.size) return found()
        val first = attempts == 0
        return endRound(!notesShown, GameLine(
            (if (first) "Yes, first time: " else "Yes: ") + letters() + ".",
            if (first) "Yes, first time." else "Yes."))
    }

    /** The largest jump between neighbours in the whole melody, in scale steps (for tests). */
    fun biggestLeap(): Int = full.zipWithNext { a, b -> abs(pool.indexOf(b) - pool.indexOf(a)) }.maxOrNull() ?: 0
}

/**
 * The melody's path through a [pool] of scale notes: it starts on `pool[start]`, then each next note moves by a step
 * drawn from [steps] (scale steps, a step that would leave the pool goes the other way, never a repeat). Melody memory
 * draws its whole melody with it; Pup says (engine/play) keeps drawing for as long as the game goes on.
 */
class MelodyWalk(val pool: List<Int>, private val steps: List<Int>, private val rnd: Random, start: Int = 0) {
    init { require(pool.size >= 2 && start in pool.indices) { "a walk needs two notes and a start inside the pool" } }
    private var i = start
    private var first = true

    /** The next note of the melody (the first call gives the start). */
    fun next(): Int {
        if (first) { first = false; return pool[i] }
        var next: Int
        do {
            val s = steps[rnd.nextInt(steps.size)]
            next = i + s
            if (next !in pool.indices) next = i - s
        } while (next !in pool.indices || next == i)
        i = next
        return pool[i]
    }

    companion object {
        /** Melody memory's steps per level: mostly neighbours, skips up to two, three or four scale steps. */
        fun stepsFor(level: Int): List<Int> = when (level) {
            0 -> listOf(-2, -1, -1, -1, 1, 1, 1, 2)
            1 -> listOf(-3, -2, -1, -1, -1, 1, 1, 1, 2, 3)
            else -> listOf(-4, -3, -2, -2, -1, -1, -1, 1, 1, 1, 2, 2, 3, 4)
        }
    }
}
