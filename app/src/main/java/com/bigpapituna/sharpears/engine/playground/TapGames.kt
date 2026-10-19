package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Report

/*
 * 2.3 part B, the tap games: the app plays, the child answers by tapping a big picture on screen (no microphone), the
 * way a teacher asks "did that go up or down?". The night sky asks about up and down, same and different, loud and
 * quiet, fast and slow, long and short, happy and sad; the treehouse asks which finger the painted hand used, and then
 * the child plays that key (the microphone checks the key, never the finger). Pure rules here; ui/playground TapStage
 * plays the sounds and draws the answers, TapGamesTest drives the same state machine.
 */

/** An answer button. */
enum class TapChoice {
    UP, DOWN, SAME, DIFFERENT, FIRST, SECOND, FAST, SLOW, LONG, SHORT, HAPPY, SAD, F1, F2, F3, F4, F5;

    /** The finger (1 thumb .. 5) a finger button stands for, or null. */
    val finger: Int? get() = when (this) { F1 -> 1; F2 -> 2; F3 -> 3; F4 -> 4; F5 -> 5; else -> null }

    companion object {
        fun finger(f: Int): TapChoice = listOf(F1, F2, F3, F4, F5)[f - 1]
    }
}

/** What a tap game asks, and the buttons it offers, in screen order. */
enum class TapKind(val choices: List<TapChoice>) {
    UP_DOWN(listOf(TapChoice.UP, TapChoice.DOWN)),
    SAME_DIFFERENT(listOf(TapChoice.SAME, TapChoice.DIFFERENT)),
    /** Two sounds, each lighting its own button as it plays: tap the louder one. */
    LOUDER(listOf(TapChoice.FIRST, TapChoice.SECOND)),
    FAST_SLOW(listOf(TapChoice.FAST, TapChoice.SLOW)),
    LONG_SHORT(listOf(TapChoice.LONG, TapChoice.SHORT)),
    HAPPY_SAD(listOf(TapChoice.HAPPY, TapChoice.SAD)),
    FINGER(listOf(TapChoice.F1, TapChoice.F2, TapChoice.F3, TapChoice.F4, TapChoice.F5)),
}

/**
 * A sound the app plays in a round: [keys] together at [velocity] for [ms], then [gapMs] of quiet. [finger] is the
 * painted hand's finger on it (which-finger rounds), [right] its hand.
 */
data class TapSound(val keys: List<Int>, val ms: Int, val velocity: Int = 80, val gapMs: Int = 120, val finger: Int? = null, val right: Boolean = true)

/**
 * One round: what it plays, in [parts] (the things compared: two notes, two tunes, two loudnesses; or one tune, note
 * or chord), which [answer] is right, and after it, a key to [play] on the piano (or none).
 */
data class TapRound(val parts: List<List<TapSound>>, val answer: TapChoice, val play: Int? = null) {
    /** How long its sounds take, from the first to the end of the last, with [PART_GAP_MS] between parts. */
    val soundMs: Long get() = parts.sumOf { p -> p.sumOf { (it.ms + it.gapMs).toLong() } } + PART_GAP_MS * (parts.size - 1)

    /** When part [i] begins, from the round's first sound. */
    fun partAt(i: Int): Long = parts.take(i).sumOf { p -> p.sumOf { (it.ms + it.gapMs).toLong() } + PART_GAP_MS }

    companion object {
        const val PART_GAP_MS = 650L
    }
}

/**
 * A tap game as a pure state machine, from the pup's example to the last round. [start] plays the [example] (the pup
 * answers it himself: "Let me show you"), then each of [rounds] is shown (its sounds play), then asked (the buttons
 * wake up). A tap that is not the answer, or [quietMs] with no tap, is a try and the round plays again ("Listen
 * again"); [maxTries] tries and the pup shows the answer and the round goes by with a small star. A round with a key to
 * [TapRound.play] then listens for it the same way: a key that is not it or a quiet stretch of [playQuietMs] is a try,
 * three let it go. [tick] moves the clock on; every call returns the cues for the screen, in order.
 */
class TapQuiz(
    val example: TapRound?, val rounds: List<TapRound>,
    val maxTries: Int = PLAYGROUND_TRIES, val quietMs: Long = 9_000, val playQuietMs: Long = 7_000,
    val leadMs: Long = 700, val gapMs: Long = 1_400, val exampleAnswerMs: Long = 2_400,
) {
    init { require(rounds.isNotEmpty()) { "a tap game needs a round" } }

    enum class Phase { IDLE, EXAMPLE, SHOW, ASK, PLAY, BETWEEN, DONE }

    sealed interface Cue {
        /** The pup's example: it plays from now, and after [answerAtMs] he taps its answer himself. */
        data class Example(val round: TapRound, val answerAtMs: Long, val ms: Long) : Cue
        /** Round [index] plays its sounds after [leadMs] ([first]: "Your turn!"; [again]: "Listen again."), [ms] in all. */
        data class Show(val index: Int, val first: Boolean, val again: Boolean, val leadMs: Long, val ms: Long) : Cue
        /** The answer buttons wake up for round [index]. */
        data class Ask(val index: Int) : Cue
        /** [choice] was the answer; [firstTry] when no try went before it. */
        data class Right(val index: Int, val choice: TapChoice, val firstTry: Boolean) : Cue
        /** [choice] was not the answer: try [tries] of [maxTries]. */
        data class NotYet(val index: Int, val choice: TapChoice?, val tries: Int) : Cue
        /** Out of tries: the pup shows [answer] and the round goes by. */
        data class LetGo(val index: Int, val answer: TapChoice) : Cue
        /** Now play [key] on the piano ([again]: shown again after a quiet stretch). */
        data class Play(val index: Int, val key: Int, val again: Boolean) : Cue
        /** [key] was heard. */
        data class Played(val index: Int, val key: Int) : Cue
        /** A key that was not it while playing: try [tries]. */
        data class PlayMiss(val index: Int, val key: Int?, val tries: Int) : Cue
        /** The key went by after three tries. */
        data class PlayLetGo(val index: Int) : Cue
        data object Done : Cue
    }

    var phase = Phase.IDLE; private set
    /** The round being played (-1 during the example). */
    var index = -1; private set
    /** Tries used on the round's question, and on its key. */
    var tries = 0; private set
    var playTries = 0; private set
    /** Rounds answered (on any try), and on the first try. */
    var right = 0; private set
    var firstTry = 0; private set
    /** Rounds whose answer the pup had to show, and keys let go. */
    val letGo = mutableListOf<Int>()
    val keysLetGo = mutableListOf<Int>()
    /** Keys heard in play parts. */
    var played = 0; private set
    private var until = 0L
    private var shownOnce = false

    val round: TapRound? get() = rounds.getOrNull(index)
    val done get() = phase == Phase.DONE
    /** Rounds finished so far (answered or let go, with their key). */
    val finished: Int get() = when (phase) { Phase.DONE -> rounds.size; Phase.BETWEEN -> index + 1; else -> index.coerceAtLeast(0) }

    /** The game begins at [nowMs]: the example, or the first round. */
    fun start(nowMs: Long): List<Cue> {
        check(phase == Phase.IDLE) { "started twice" }
        val ex = example ?: return begin(0, nowMs)
        phase = Phase.EXAMPLE
        val answerAt = leadMs + ex.soundMs + 500
        until = nowMs + answerAt + exampleAnswerMs
        return listOf(Cue.Example(ex, answerAt, answerAt + exampleAnswerMs))
    }

    private fun begin(i: Int, nowMs: Long): List<Cue> {
        if (i >= rounds.size) { phase = Phase.DONE; return listOf(Cue.Done) }
        index = i; tries = 0; playTries = 0
        val first = !shownOnce
        shownOnce = true
        return listOf(show(nowMs, first = first, again = false))
    }

    private fun show(nowMs: Long, first: Boolean, again: Boolean): Cue.Show {
        phase = Phase.SHOW
        val lead = if (first) leadMs + 900 else leadMs
        val ms = lead + rounds[index].soundMs
        until = nowMs + ms
        return Cue.Show(index, first, again, lead, ms)
    }

    /** The clock moved on to [nowMs]. */
    fun tick(nowMs: Long): List<Cue> = when {
        phase == Phase.EXAMPLE && nowMs >= until -> begin(0, nowMs)
        phase == Phase.SHOW && nowMs >= until -> { phase = Phase.ASK; until = nowMs + quietMs; listOf(Cue.Ask(index)) }
        phase == Phase.ASK && nowMs >= until -> miss(null, nowMs)
        phase == Phase.PLAY && nowMs >= until -> playTry(null, nowMs, quiet = true)
        phase == Phase.BETWEEN && nowMs >= until -> begin(index + 1, nowMs)
        else -> emptyList()
    }

    /** The child tapped [choice] at [nowMs]. Taps count only while the round is asked (not while it plays). */
    fun tap(choice: TapChoice, nowMs: Long): List<Cue> {
        if (phase != Phase.ASK) return emptyList()
        val r = rounds[index]
        if (choice != r.answer) return miss(choice, nowMs)
        right++
        val first = tries == 0
        if (first) firstTry++
        return listOf(Cue.Right(index, choice, first)) + afterAnswer(nowMs)
    }

    private fun miss(choice: TapChoice?, nowMs: Long): List<Cue> {
        tries++
        val notYet = Cue.NotYet(index, choice, tries)
        if (tries >= maxTries) {
            letGo += index
            return listOf(notYet, Cue.LetGo(index, rounds[index].answer)) + afterAnswer(nowMs, shown = true)
        }
        return listOf(notYet, show(nowMs, first = false, again = true))
    }

    /** After the question: the key to play, or the next round. [shown]: the pup showed the answer, give it time. */
    private fun afterAnswer(nowMs: Long, shown: Boolean = false): List<Cue> {
        val key = rounds[index].play ?: return between(nowMs + if (shown) 1_000 else 0)
        phase = Phase.PLAY
        playTries = 0
        until = nowMs + playQuietMs + if (shown) 1_000 else 0
        return listOf(Cue.Play(index, key, again = false))
    }

    /** A key was heard at [nowMs] while the round's key is wanted: [key] (any octave of it counts), or null for a sound with no key. */
    fun key(key: Int?, nowMs: Long): List<Cue> {
        if (phase != Phase.PLAY || key == null) return emptyList()
        val want = rounds[index].play!!
        if (Math.floorMod(key - want, 12) == 0) { played++; return listOf(Cue.Played(index, want)) + between(nowMs) }
        return playTry(key, nowMs, quiet = false)
    }

    private fun playTry(key: Int?, nowMs: Long, quiet: Boolean): List<Cue> {
        playTries++
        val want = rounds[index].play!!
        if (playTries >= maxTries) {
            keysLetGo += index
            return listOf(Cue.PlayMiss(index, key, playTries), Cue.PlayLetGo(index)) + between(nowMs + 600)
        }
        until = nowMs + playQuietMs
        return if (quiet) listOf(Cue.Play(index, want, again = true)) else listOf(Cue.PlayMiss(index, key, playTries))
    }

    private fun between(fromMs: Long): List<Cue> {
        phase = Phase.BETWEEN
        until = fromMs + gapMs
        return emptyList()
    }

    /**
     * The run's report: no keys to get wrong ([Report.wrongKeys] 0), [Report.groups] the rounds answered, and its own
     * score: the rounds answered on the first try, in percent (three stars from nine in ten, as a Perform run).
     */
    fun report(): Report = Report(emptyList(), 0, 0, null, groups = right + played, extraScore = firstTry * 100 / rounds.size)
}
