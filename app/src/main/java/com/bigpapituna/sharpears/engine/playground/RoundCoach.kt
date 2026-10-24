package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.OnsetGroup
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Transport

/*
 * No dead ends in the Playground (2.1.1). Every round of a stage is shown first ("Let me show you": the app plays its
 * keys and lights them while the mic is deaf, then "Your turn!"), then heard. A key that is not the round's answer is a
 * try; so is a quiet stretch with no try at all (the round is shown again). After three tries the pup says "Good try,
 * let's keep going" and the round goes by with one star. A kid who plays nothing right still reaches the end of every
 * lesson (PlaygroundFlowTest walks all twelve).
 */

/** How many tries a round (or a step) gets before the pup lets it go. */
const val PLAYGROUND_TRIES = 3

/**
 * One round at a time, from its demo to its answer: pure timing and counting. [begin] a round (its demo plays for
 * [firstDemoMs] the first time in a step, which speaks, and [demoMs] after), then [tick] every frame or so, and say
 * [miss] or [found] as tries come in. A stretch of [quietMs] in [Phase.LISTEN] without a try counts as one and shows the
 * round again. [maxTries] tries let the round go ([Cue.LetGo]).
 */
class RoundCoach(
    val maxTries: Int = PLAYGROUND_TRIES,
    val quietMs: Long = 7_000,
    val firstDemoMs: Long = 3_000,
    val demoMs: Long = 1_500,
    val againDemoMs: Long = 2_200,
) {
    enum class Phase { IDLE, DEMO, LISTEN, DONE }

    sealed interface Cue {
        val round: Int
        /** Show the round: play its keys, light them, mic deaf. [first]: the step's first ("Let me show you"); [again]: after a quiet stretch. */
        data class Demo(override val round: Int, val first: Boolean, val again: Boolean, val ms: Long) : Cue
        /** The demo is over: listen ("Your turn!" after the first). */
        data class Listen(override val round: Int, val first: Boolean) : Cue
        /** Out of tries: "Good try, let's keep going", the round goes by with one star. */
        data class LetGo(override val round: Int) : Cue
    }

    var phase = Phase.IDLE; private set
    var round = -1; private set
    /** Tries used in this round. */
    var tries = 0; private set
    private var begun = 0
    private var demoEnds = 0L
    private var quietFrom = 0L
    private var firstDemo = false

    /** Round [round] begins at [nowMs] with its demo. */
    fun begin(round: Int, nowMs: Long): Cue.Demo {
        this.round = round
        tries = 0
        firstDemo = begun++ == 0
        return demo(nowMs, first = firstDemo, again = false)
    }

    private fun demo(nowMs: Long, first: Boolean, again: Boolean): Cue.Demo {
        val ms = when { first -> firstDemoMs; again -> againDemoMs; else -> demoMs }
        phase = Phase.DEMO
        demoEnds = nowMs + ms
        return Cue.Demo(round, first, again, ms)
    }

    /** The clock moved on: the demo ends ([Cue.Listen]), or a quiet stretch counts as a try (a demo again, or the let go). */
    fun tick(nowMs: Long): Cue? = when {
        phase == Phase.DEMO && nowMs >= demoEnds -> {
            phase = Phase.LISTEN; quietFrom = nowMs
            Cue.Listen(round, first = firstDemo).also { firstDemo = false }
        }
        phase == Phase.LISTEN && nowMs - quietFrom >= quietMs -> {
            tries++
            if (tries >= maxTries) letGo() else demo(nowMs, first = false, again = true)
        }
        else -> null
    }

    /** A try that was not it, at [nowMs]: the let go on the last one. Only while listening (a demo is deaf). */
    fun miss(nowMs: Long): Cue? {
        if (phase != Phase.LISTEN) return null
        tries++
        quietFrom = nowMs
        return if (tries >= maxTries) letGo() else null
    }

    /** The round's answer was heard: done. */
    fun found() { phase = Phase.DONE }

    /**
     * Part of the round's answer was heard (a note of a phrase, 2.2): the round goes on listening and the quiet stretch
     * starts again from [nowMs]. Tries stay as they are.
     */
    fun progress(nowMs: Long) { if (phase == Phase.LISTEN) quietFrom = nowMs }

    private fun letGo(): Cue.LetGo { phase = Phase.DONE; return Cue.LetGo(round) }
}

/**
 * A Playground stage's rounds: the step's exercise in wait mode (its report is what the lesson grades), each round begun
 * by the [coach] [gapMs] after the last one ended (the pup's call-out between rounds). The stage on screen feeds [tick],
 * [found] and [miss] and draws what they return; PlaygroundFlowTest drives it the same way.
 *
 * A round is one group (a key, an any-of group or a chord), or with [phrases] (2.2: the copy games and the soft and loud
 * tune) a phrase: the notes between two rests of the exercise, played in order. The coach shows a phrase whole and
 * listens for its notes one after another; each note heard is progress ([RoundCoach.progress]), its last note finds the
 * round, and a let go plays the rest of the phrase in for the kid.
 */
class StageRounds(val song: Song, practice: Practice, val coach: RoundCoach = RoundCoach(), val gapMs: Long = 1_400, phrases: Boolean = false) {
    val transport = Transport(song).apply { mode = Mode.WAIT; this.practice = practice }
    /** The groups to play (those with a required note), in order. */
    private val required: List<OnsetGroup> = song.groups.filter { g -> g.notes.any(transport::required) }
    /** The round each required group belongs to: its own, or its phrase's (a rest in the exercise ends a phrase). */
    private val roundOf: IntArray = if (!phrases) IntArray(required.size) { it } else {
        val gaps = silences(song)
        var r = 0
        IntArray(required.size) { i -> if (i > 0 && gaps.any { it > required[i - 1].startMs && it < required[i].startMs }) r++; r }
    }
    /** Rounds to play: groups, or phrases. */
    val rounds = if (required.isEmpty()) 0 else roundOf.last() + 1
    /** Rounds let go after three tries, by index: each shows one star. */
    val letGo = mutableListOf<Int>()
    /** Groups done so far, found or let go. */
    val groupsDone get() = transport.log.groups
    /** Rounds done so far, found or let go. */
    val done get() = if (groupsDone >= required.size) rounds else roundOf[groupsDone]
    /** The group being played (or waited for). */
    val current: OnsetGroup? get() = transport.nextGroup
    /** The groups of the round being played, in order (one, unless rounds are phrases). */
    val roundGroups: List<OnsetGroup> get() = if (groupsDone >= required.size) emptyList()
        else required.filterIndexed { i, _ -> roundOf[i] == roundOf[groupsDone] }
    /** How many groups of the round being played are done (a phrase's notes so far). */
    val doneInRound: Int get() = if (groupsDone >= required.size) 0 else (0 until groupsDone).count { roundOf[it] == roundOf[groupsDone] }
    var started = false; private set
    /** The song has run out after its last round: the run's report is ready. */
    val ended get() = started && !transport.playing
    private var beginAt = 0L

    fun start(nowMs: Long) { transport.play(); started = true; beginAt = nowMs }

    /** Whether the group being played begins its round (always, with a group a round). */
    private fun atRoundStart() = groupsDone >= required.size || groupsDone == 0 || roundOf[groupsDone - 1] != roundOf[groupsDone]
    /** Whether the group being played ends its round. */
    private fun atRoundEnd() = groupsDone >= required.size - 1 || roundOf[groupsDone + 1] != roundOf[groupsDone]

    /**
     * Real time moves on by [dtMs] to [nowMs]: the transport ticks (its note events go to the sampler), the next round
     * begins when the last is over and the gap has passed, and the coach's cues come back.
     */
    fun tick(dtMs: Double, nowMs: Long): Pair<List<NoteEvent>, RoundCoach.Cue?> {
        if (!started) return emptyList<NoteEvent>() to null
        val events = transport.tick(dtMs)
        val idle = coach.phase == RoundCoach.Phase.IDLE || coach.phase == RoundCoach.Phase.DONE
        if (idle && nowMs >= beginAt && current != null && transport.playing && atRoundStart()) {
            // The song waits for each round; between rounds the time is only pacing, so jump to the next one.
            val g = current!!
            val ahead = g.startMs - transport.positionMs
            val more = if (ahead > 0) transport.tick(ahead / transport.speed) else emptyList()
            return (events + more) to coach.begin(done, nowMs)
        }
        val cue = coach.tick(nowMs)
        if (cue is RoundCoach.Cue.LetGo) settle(nowMs, null)
        return events to cue
    }

    /**
     * The keys the round's demo plays: its notes (an any-of group's first, its demonstration key; a chord's every key),
     * or a phrase's notes in order from its first (shown whole; the kid goes on from where they are).
     */
    fun demoKeys(): List<Int> = roundGroups.let { gs ->
        if (gs.size > 1) gs.map(::demoKey)
        else current?.let { g -> if (g.anyOf) listOf(g.notes.first().midi) else g.notes.filter(transport::required).map { it.midi } }.orEmpty()
    }

    private fun demoKey(g: OnsetGroup) = if (g.anyOf) g.notes.first().midi else g.notes.first(transport::required).midi

    /** The key the round waits for now: the group's demonstration key (in a phrase, its next note). */
    fun expectedKey(): Int? = current?.let(::demoKey)

    /** What [answer] did with a round's answer. */
    sealed interface Outcome {
        /** The group is done: [key] went to the transport (in a phrase the round may go on). */
        data class Found(val key: Int, val result: InputResult) : Outcome
        /** A try; [cue] is the let go when it was the last. */
        data class Missed(val cue: RoundCoach.Cue?) : Outcome
        /** Nothing to count (a sound with no key, or the round is not listening: a demo plays). */
        data object Ignored : Outcome
    }

    /**
     * Whether a key touched now, while the round's demo still plays, may count (QA 2.8.3): [shown] is how many of the demo's
     * keys ([demoKeys]) have sounded so far. A kid who copies the pup at once was not heard (the demo is deaf), and the quiet
     * stretches that followed let the round go: "Let's hear you play" put a kid who had played a bit in the garden. A touch
     * is certain (no echo of the demo to fear), so it may count once the demo has played the key the round waits for (in a
     * phrase, its next note). The mic stays deaf through a demo, as before.
     */
    fun earlyTouchMayCount(shown: Int): Boolean = coach.phase == RoundCoach.Phase.DEMO && current != null && shown > doneInRound

    /**
     * Whether [a], touched during the demo after [shown] of its keys sounded, counts: only the round's answer, once shown.
     * The caller plays it in with `found(key, nowMs, early = true)`. Anything else is no try at all (the demo goes on and
     * stays deaf), so an early touch can never bring a let go.
     */
    fun takesEarly(a: Answer, shown: Int): Boolean = a is Answer.Found && earlyTouchMayCount(shown)

    /** A judged sound ([Answer]) at [nowMs], applied to the round being played. */
    fun answer(a: Answer, nowMs: Long): Outcome = when {
        coach.phase != RoundCoach.Phase.LISTEN -> Outcome.Ignored
        a is Answer.Found -> Outcome.Found(a.key, found(a.key, nowMs))
        a is Answer.Miss -> Outcome.Missed(miss(nowMs))
        else -> Outcome.Ignored
    }

    /**
     * The group's answer was heard: [key] goes to the transport (a key of the group, else its first). The round is done
     * at its last group; a phrase's earlier notes are progress. [early]: touched during the demo ([takesEarly]); the demo
     * then goes on (deaf to anything else) and listening starts when it ends, from the note reached.
     */
    fun found(key: Int, nowMs: Long, early: Boolean = false): InputResult {
        val open = coach.phase == RoundCoach.Phase.LISTEN || (early && coach.phase == RoundCoach.Phase.DEMO)
        if (!open) return InputResult.IGNORED
        if (atRoundEnd()) coach.found() else coach.progress(nowMs)
        return settle(nowMs, key)
    }

    /** A try that was not it; the let go when it was the last (the round is then done). */
    fun miss(nowMs: Long): RoundCoach.Cue? {
        val cue = coach.miss(nowMs)
        if (cue is RoundCoach.Cue.LetGo) settle(nowMs, null)
        return cue
    }

    /**
     * Plays the group waited for into the transport: [key] found (with a chord's other keys, which the stage heard with
     * it), or null, let go, and with it the rest of its round. The next round begins [gapMs] after a round ends.
     */
    private fun settle(nowMs: Long, key: Int?): InputResult {
        if (current == null) return InputResult.IGNORED
        val round = done
        if (key == null) letGo += round
        var r = InputResult.IGNORED
        do {
            val g = current ?: break
            val keys = g.notes.filter(transport::required).map { it.midi }
            val play = key?.takeIf { it in keys } ?: keys.firstOrNull() ?: break
            if (!transport.waiting) transport.tick((g.startMs - transport.positionMs).coerceAtLeast(0.0) / transport.speed)
            r = transport.noteInput(play)
            // A chord needs every note: one found was heard whole (the stage's judge collected it); a let go plays them all.
            for (k in keys) if (k != play && r == InputResult.CORRECT) r = transport.noteInput(k)
        } while (key == null && groupsDone < required.size && roundOf[groupsDone] == round)
        if (groupsDone >= required.size || roundOf[groupsDone] != round) beginAt = nowMs + gapMs
        return r
    }

    /** The run's report (wait mode: groups and wrong keys). */
    fun report(): Report = transport.report()

    private companion object {
        /** Where the song falls silent (a rest in the exercise): a moment inside each gap between notes, in ms. */
        fun silences(song: Song): List<Long> {
            val out = ArrayList<Long>()
            var end = Long.MIN_VALUE
            for (n in song.notes.sortedBy { it.startMs }) {
                // A note sounds for 92% of its length (Exercise notation); a gap longer than that tail is a rest.
                if (end != Long.MIN_VALUE && n.startMs > end + 50) out += end + 25
                end = maxOf(end, n.startMs + (n.durationMs / 0.92).toLong())
            }
            return out
        }
    }
}

/**
 * A step of a Playground lesson that ends with a verdict (a Perform run under its pass mark, a concert): after
 * [PLAYGROUND_TRIES] tries that did not pass, the pup says "Good try, let's keep going" and the lesson moves on, as a
 * round of a stage does. Grown-up lessons keep their "Try again".
 */
object StepRetry {
    /** Every lesson of every Playground page (the garden, the pond, the concert hall). */
    fun isPlayground(lessonId: String) = isPlaygroundLesson(lessonId)

    /** After [fails] tries of a step that did not pass: let it go now. */
    fun letGo(lessonId: String, fails: Int): Boolean = isPlayground(lessonId) && fails >= PLAYGROUND_TRIES
}

/**
 * The drum (lesson 7): a try is one run of the drum. With claps heard it ends (a toy that never fails); with nothing
 * heard the pup asks again by himself, and after [PLAYGROUND_TRIES] silent tries he says "Good try, let's keep going"
 * and the step ends with no claps.
 */
object ClapTries {
    enum class Next { DONE, AGAIN, LET_GO }

    /** What follows a try ([attempt] from 0) in which claps were [heard] or not. */
    fun after(attempt: Int, heard: Boolean): Next = when {
        heard -> Next.DONE
        attempt + 1 >= PLAYGROUND_TRIES -> Next.LET_GO
        else -> Next.AGAIN
    }
}
