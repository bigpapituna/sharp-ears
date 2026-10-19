package com.bigpapituna.sharpears.engine

import java.util.Random

/** How a key is lit in a game: shown as a hint, the given starting note, or found. */
enum class Lit { SHOWN, GIVEN, FOUND }

/**
 * What the game screen needs from any of the Play games: rounds and scores, the prompt, what the app plays at the
 * start of a round, what the keys show, what the mic listens for, and the player's answers.
 */
interface EarGame {
    val kind: GameKind
    val level: Int
    val rounds: Int
    val round: Int
    val score: Int
    val streak: Int
    val bestStreak: Int
    val finished: Boolean
    fun prompt(): GameLine
    /** Played at the start of each round (and on Hear again): note groups in order, each group struck together. */
    fun toPlay(): List<List<Int>>
    fun lit(): Map<Int, Lit>
    fun micNotes(): Set<Int>
    /** The matcher's groups (notes that ring together). */
    fun micGroups(): List<Set<Int>>
    fun input(midi: Int, nowMs: Long = 0): ChordGame.Answer
    fun skip()
    fun next()
    /** The round's answer, for the log. */
    fun describe(): String
}

fun newGame(kind: GameKind, level: Int, seed: Long = System.nanoTime()): EarGame = when (kind) {
    GameKind.INTERVALS -> IntervalGame(level, seed)
    GameKind.PLAYBACK -> PlaybackGame(level, seed)
    else -> ChordGame(kind, level, seed)
}

private val INTERVAL_NAMES = listOf("unison", "minor second", "major second", "minor third", "major third", "perfect fourth", "tritone",
    "perfect fifth", "minor sixth", "major sixth", "minor seventh", "major seventh", "octave")

fun intervalName(semitones: Int) = INTERVAL_NAMES[kotlin.math.abs(semitones).coerceIn(0, 12)]
/** "a major third", "an octave". */
fun withArticle(name: String) = (if (name.first() in "aeiou") "an " else "a ") + name

/** Shared round bookkeeping for the two melodic games. */
abstract class RoundGame(final override val kind: GameKind, final override val level: Int, final override val rounds: Int) : EarGame {
    final override var round = 0; private set
    final override var score = 0; private set
    final override var streak = 0; private set
    final override var bestStreak = 0; private set
    var roundOver = false; protected set
    override val finished get() = round >= rounds

    protected fun endRound(clean: Boolean, line: GameLine): ChordGame.Answer {
        roundOver = true
        if (clean) { score++; streak++; bestStreak = maxOf(bestStreak, streak) } else streak = 0
        return ChordGame.Answer(ChordGame.Outcome.DONE, line, clean)
    }

    override fun skip() { if (!roundOver) { roundOver = true; streak = 0 }; next() }
    override fun next() {
        if (!roundOver || finished) return
        round++
        if (!finished) { roundOver = false; newRound() }
    }
    protected abstract fun newRound()
}

/**
 * Intervals: the app plays two notes, the first shown on the keys; the player plays the second. Level 0: up to a
 * fifth above, 1: up to an octave above, 2: up to an octave above or below. After two wrong notes the answer is lit.
 */
class IntervalGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : RoundGame(GameKind.INTERVALS, level, rounds) {
    private val rnd = Random(seed)
    var first = 60; private set
    var second = 64; private set
    var wrongs = 0; private set
    val interval get() = second - first

    init { newRound() }

    override fun newRound() {
        wrongs = 0
        var a: Int; var b: Int
        do {
            val size = 1 + rnd.nextInt(if (level == 0) 7 else 12)
            val down = level >= 2 && rnd.nextBoolean()
            a = 55 + rnd.nextInt(18)                                      // G3..C5
            b = if (down) a - size else a + size
        } while (b !in 48..84 || (a == first && b == second))
        first = a; second = b
    }

    override fun prompt() = GameLine("Second note?", "Play the second note.")
    override fun toPlay() = listOf(listOf(first), listOf(second))
    override fun lit(): Map<Int, Lit> = buildMap { put(first, Lit.GIVEN); if (wrongs >= 2) put(second, Lit.SHOWN); if (roundOver) put(second, Lit.FOUND) }
    override fun micNotes() = setOf(second)
    override fun micGroups() = listOf(setOf(second))
    override fun describe() = "${noteName(first)} to ${noteName(second)} (${intervalName(interval)} ${if (interval > 0) "up" else "down"})"

    override fun input(midi: Int, nowMs: Long): ChordGame.Answer {
        if (finished || roundOver || midi == first) return ChordGame.Answer(ChordGame.Outcome.IGNORED, null)
        val name = intervalName(interval)
        val dir = if (interval > 0) "up" else "down"
        if (midi != second) {
            wrongs++
            val played = midi - first
            val what = when {
                played == 0 -> "the same note"
                // Past an octave there is no one name here; "an octave up" for a tenth was wrong.
                kotlin.math.abs(played) > 12 -> "more than an octave ${if (played > 0) "up" else "down"}"
                else -> "${withArticle(intervalName(played))} ${if (played > 0) "up" else "down"}"
            }
            val shown = if (wrongs == 2) " Here is the note." else ""
            return ChordGame.Answer(ChordGame.Outcome.WRONG, GameLine("That was $what.$shown", "That was $what.$shown"))
        }
        val clean = wrongs < 2
        return endRound(clean, GameLine("Yes: ${withArticle(name)} $dir, ${noteName(first)} to ${noteName(second)}.",
            "Yes, ${withArticle(name)} $dir, ${spokenNote(first)} to ${spokenNote(second)}."))
    }
}

/**
 * Play it back: the app plays a short melody in a five-finger position; the player plays it back in order. A wrong
 * note starts the attempt over; after two failed attempts the notes are shown. The first half of the rounds has
 * three notes, the second half five. Level 0: C position, 1: G position, 2: D position (one black key, F sharp).
 */
class PlaybackGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : RoundGame(GameKind.PLAYBACK, level, rounds) {
    private val rnd = Random(seed)
    /** The five keys under the hand. */
    val position: List<Int> = when (level) {
        0 -> listOf(60, 62, 64, 65, 67)
        1 -> listOf(67, 69, 71, 72, 74)
        else -> listOf(62, 64, 66, 67, 69)
    }
    var melody: List<Int> = emptyList(); private set
    var progress = 0; private set
    var attempts = 0; private set
    val notesShown get() = attempts >= 2

    init { newRound() }

    override fun newRound() {
        progress = 0; attempts = 0
        val n = if (round < rounds / 2) 3 else 5
        var m: List<Int>
        do {
            // Mostly steps along the position, now and then a skip of a third.
            var i = rnd.nextInt(5)
            m = buildList {
                add(position[i])
                repeat(n - 1) {
                    val step = if (rnd.nextInt(4) == 0) 2 else 1
                    i = (if (rnd.nextBoolean()) i + step else i - step).let { if (it !in 0..4) i - (it - i) else it }.coerceIn(0, 4)
                    add(position[i])
                }
            }
        } while (m == melody || m.distinct().size < 2)
        melody = m
    }

    override fun prompt() = GameLine("Play it back, ${melody.size} notes", "Play it back: ${melody.size} notes.")
    override fun toPlay() = melody.map { listOf(it) }
    override fun lit(): Map<Int, Lit> = buildMap {
        if (notesShown) melody.forEach { put(it, Lit.SHOWN) }
        melody.take(progress).forEach { put(it, Lit.FOUND) }
    }
    override fun micNotes() = melody.getOrNull(progress)?.let { setOf(it) }.orEmpty()
    override fun micGroups() = listOf(micNotes())
    override fun describe() = melody.joinToString(" ") { noteName(it) }

    override fun input(midi: Int, nowMs: Long): ChordGame.Answer {
        if (finished || roundOver) return ChordGame.Answer(ChordGame.Outcome.IGNORED, null)
        if (midi != melody[progress]) {
            attempts++
            progress = 0
            val line = if (attempts == 2) "Not quite. Here are the notes, from the start." else "Not quite. From the start."
            return ChordGame.Answer(ChordGame.Outcome.WRONG, GameLine(line))
        }
        progress++
        if (progress < melody.size) return ChordGame.Answer(ChordGame.Outcome.FOUND, null)
        val names = melody.joinToString(", ") { rootName(it) }
        return endRound(!notesShown, GameLine(if (attempts == 0) "Yes, first time: $names." else "Yes: $names.",
            if (attempts == 0) "Yes, first time." else "Yes."))
    }
}
