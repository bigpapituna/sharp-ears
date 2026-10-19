package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Report

/*
 * 2.3, the workshop's pick game ([Game.PICK]): the pup asks a question, two or three pictures appear, and the kid taps the
 * one that is right: how a pianist sits, a wrist like a bridge, a note short or long, the pedal down or up. The listening
 * rounds play their sound first. No microphone: a tap is certain. Like every Playground round it is shown first, a quiet
 * stretch shows it again, and three tries let it go with a small star (RoundCoach). Pure rules; ui/playground/PickStage.kt
 * draws the pictures, PlaygroundFlowTest walks it.
 */

/** A picture to tap. */
enum class Pick {
    /** The player sitting just right: tall, at the right distance, feet flat. */
    SIT_TALL,
    SIT_SLOUCH,
    /** The bench pushed in too close: knees under the keys, elbows squashed. */
    SIT_CLOSE,
    /** The feet tucked under the bench. */
    SIT_FEET,
    WRIST_LEVEL,
    WRIST_LOW,
    WRIST_HIGH,
    /** A short note, bouncing off the key. */
    SHORT,
    /** A long note, held down. */
    LONG,
    /** The foot on the pedal, pressing it. */
    PEDAL_DOWN,
    /** The foot beside the pedal, resting. */
    PEDAL_UP,
}

/** What the pup plays before a listening round: middle C short or long, or C E G with the pedal down or without it. */
enum class PickSound { SHORT, LONG, PEDAL, DRY }

/** One round: the question the pup asks, the pictures in their order on screen, the one that is right, the sound first. */
data class PickRound(val ask: String, val options: List<Pick>, val answer: Pick, val sound: PickSound? = null) {
    init { require(answer in options && options.size in 2..3 && options.distinct().size == options.size) { "a pick round: $this" } }
}

/** What the pup says to a tap. */
object PickLines {
    /** The right picture tapped. */
    fun yes(r: PickRound): String = when (r.answer) {
        Pick.SIT_TALL -> "Yes! Tall, with room to move."
        Pick.WRIST_LEVEL -> "Yes! A level bridge."
        Pick.SHORT -> "Yes, short and bouncy!"
        Pick.LONG -> "Yes, long and smooth!"
        Pick.PEDAL_DOWN -> "Yes, the pedal was down!"
        Pick.PEDAL_UP -> "Yes, no pedal that time!"
        else -> "Yes!"
    }

    /** Another picture tapped: what it shows (a listening round: what the sound did). */
    fun notIt(tapped: Pick, r: PickRound): String = if (r.sound != null) when (r.answer) {
        Pick.SHORT -> "Listen again. That one bounced off, short."
        Pick.LONG -> "Listen again. That one stayed down, long."
        Pick.PEDAL_DOWN -> "Listen again. The notes rang on together."
        else -> "Listen again. Each note stopped by itself."
    } else when (tapped) {
        Pick.SIT_SLOUCH -> "That one slouches. Look for the one sitting tall."
        Pick.SIT_CLOSE -> "That one is squashed up close. Look for the one with room."
        Pick.SIT_FEET -> "Those feet are tucked under. Look for flat feet."
        Pick.WRIST_LOW -> "That wrist sags down. Look for the level one."
        Pick.WRIST_HIGH -> "That wrist pokes up. Look for the level one."
        else -> "Look again!"
    }

    /** Every line a pick game can say for [rounds], for the voice catalog. */
    fun all(rounds: List<PickRound>): List<String> = rounds.flatMap { r -> listOf(r.ask, yes(r)) + r.options.filter { it != r.answer }.map { notIt(it, r) } }.distinct()
}

/**
 * A pick game's rounds, in order: [tick] with a forward clock begins each round [gapMs] after the last ended (the coach's
 * demo: the question, and the sound of a listening round), then times its quiet stretches; [tap] answers. Done when
 * every round is found or let go.
 */
class PickRounds(val rounds: List<PickRound>, val coach: RoundCoach = RoundCoach(firstDemoMs = 2_400, demoMs = 2_000, againDemoMs = 2_200, quietMs = 8_000),
                 val gapMs: Long = 1_800) {
    /** Rounds done, found or let go. */
    var done = 0; private set
    /** Rounds let go after three tries, by index. */
    val letGo = mutableListOf<Int>()
    /** Taps on a picture that was not it. */
    var notIt = 0; private set
    private var started = false
    private var nextAt = 0L
    val ended get() = done >= rounds.size
    /** The round being played. */
    val current: PickRound? get() = rounds.getOrNull(done)

    fun start(nowMs: Long) { started = true; nextAt = nowMs }

    /** The clock moved on to [nowMs]: the next round begins ([RoundCoach.Cue.Demo]), its demo ends, or a quiet stretch counts. */
    fun tick(nowMs: Long): RoundCoach.Cue? {
        if (!started || ended) return null
        if (coach.phase == RoundCoach.Phase.IDLE || coach.phase == RoundCoach.Phase.DONE) return if (nowMs >= nextAt) coach.begin(done, nowMs) else null
        val cue = coach.tick(nowMs)
        if (cue is RoundCoach.Cue.LetGo) settle(nowMs, letGo = true)
        return cue
    }

    /** What a tap did. */
    sealed interface Tap {
        data class Right(val line: String) : Tap
        /** Not it: what the pup says, and the let go when it was the third try. */
        data class NotIt(val line: String, val cue: RoundCoach.Cue?) : Tap
        /** Not listening (the question is still being asked, or the game is over). */
        data object Ignored : Tap
    }

    fun tap(p: Pick, nowMs: Long): Tap {
        val r = current ?: return Tap.Ignored
        if (coach.phase != RoundCoach.Phase.LISTEN) return Tap.Ignored
        if (p == r.answer) {
            coach.found()
            settle(nowMs, letGo = false)
            return Tap.Right(PickLines.yes(r))
        }
        notIt++
        val cue = coach.miss(nowMs)
        if (cue is RoundCoach.Cue.LetGo) settle(nowMs, letGo = true)
        return Tap.NotIt(PickLines.notIt(p, r), cue)
    }

    private fun settle(nowMs: Long, letGo: Boolean) {
        if (letGo) this.letGo += done
        done++
        nextAt = nowMs + gapMs
    }

    /** The run as a lesson grades it: a round a group, the taps that were not it as slips (the stars). */
    fun report(): Report = Report(emptyList(), 0, notIt, null, groups = done - letGo.size)
}
