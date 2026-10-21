package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.hints.WrongKeys
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What one run of the player did that the brief wants and the transport's report does not keep: wrong keys by the note
 * they were reaching for (near or far), the hint escalation of each stuck wait, octave hints and groups let go; and, per
 * note that came due, how long its key took to come (hesitation), whether it was played clean, and which hand it was for;
 * notes never played (let go, missed in Perform); the spread of a Perform run's beat offsets. The player
 * (ui/PlayerScreen.kt) feeds the wrong keys and hints as things happen, the transport (engine/Transport.kt) feeds the
 * notes ([due], [heard], [groupDone], [performHit], [performMiss]), and the run's tally is handed over when it ends; a
 * lesson adds its tries up ([plus]) and logs one LessonStep, a song logs a SongRun, each with its [detail]. Pure, so it
 * is tested on the JVM.
 *
 * Times given to [due] and [heard] are the run's own real-time clock (Transport.runRealMs: holds count, pauses do not).
 */
class RunTally {
    private val pairs = LinkedHashMap<Pair<Int, Int>, Int>()
    private val hints = ArrayList<Int>()
    private var deepest = 0
    /** Waits that needed a hint, by their deepest step (glow, point, play), never capped. */
    private val steps = IntArray(3)

    /** A note waited for: its key (or an any-of group's keys), its hand, and what became of it. */
    private class Due(val keys: Set<Int>, val hand: Hand, val single: Boolean, val atMs: Double) {
        var heard = false; var slipped = false; var letGo = false
    }
    private val pending = ArrayList<Due>()
    private val handDue = IntArray(2)
    private val handClean = IntArray(2)
    private val handHesSum = LongArray(2)
    private val handHesN = IntArray(2)
    private var hesSum = 0L
    private var hesN = 0
    /** Key to the longest it took to come in this run. */
    private val slowest = HashMap<Int, Int>()
    private var offN = 0
    private var offSum = 0.0
    private var offSq = 0.0

    /** Wrong keys within two semitones of the note wanted. */
    var near = 0; private set
    /** Wrong keys further away (or with nothing wanted to compare with). */
    var far = 0; private set
    /** Octave hints shown. */
    var octaves = 0; private set
    /** Groups let go after three tries (Pup's Playground). */
    var letGos = 0; private set
    /** Notes that came due and were never played: let go, missed in Perform, or left out of a big chord by the mic's slack. */
    var skipped = 0; private set

    val wrongKeys get() = near + far
    /** A run is under way and has not ended (the player sets it; a copy never carries it). */
    var open = false
    /** One [HintDepth] per wait that needed a hint, in order (at most [MAX_HINTS]). */
    val hintsUsed: List<Int> get() = hints.toList()

    /** [played] was a wrong key; [nearest] is the expected note closest to it (null when nothing was expected). */
    fun wrong(played: Int, nearest: Int?) {
        val d = nearest?.let { abs(played - it) }
        if (d != null && WrongKeys.isSlip(d)) near++ else far++
        if (nearest != null) {
            pairs.merge(nearest to played, 1, Int::plus)
            pending.firstOrNull { !it.heard && nearest in it.keys }?.slipped = true
        }
    }

    /** Hint escalation reached [depth] ([HintDepth]) in the wait under way. */
    fun hint(depth: Int) { deepest = maxOf(deepest, depth.coerceIn(0, HintDepth.PLAY)) }

    /** The wait is over (played, paused, left): its deepest hint, if any, is counted. */
    fun waitEnded() {
        if (deepest > 0) {
            if (hints.size < MAX_HINTS) hints += deepest
            steps[deepest - 1]++
        }
        deepest = 0
    }

    fun octave() { octaves++ }

    /** The Playground let the group go: its notes not yet played count as never played (and never as clean). */
    fun letGo() {
        letGos++
        pending.filter { !it.heard && !it.letGo }.forEach { it.letGo = true; skipped++ }
    }

    // ---- notes, fed by the transport ----

    /**
     * A wait-mode group came due at [atMs]: its [notes] (the required ones). An any-of group ([anyOf]) is one note, any of
     * whose keys completes it. A group left pending (a seek, a mode change) is forgotten.
     */
    fun due(notes: List<Note>, anyOf: Boolean, atMs: Double) {
        pending.clear()
        if (notes.isEmpty()) return
        if (anyOf) pending += Due(notes.map { it.midi }.toSet(), notes.first().hand, single = false, atMs = atMs)
        else notes.forEach { pending += Due(setOf(it.midi), it.hand, single = true, atMs = atMs) }
    }

    /** [midi] was heard for the group due, its strike at [atMs]: the wait from due to heard is its hesitation. */
    fun heard(midi: Int, atMs: Double) {
        val d = pending.firstOrNull { !it.heard && !it.letGo && midi in it.keys } ?: return
        d.heard = true
        val ms = (atMs - d.atMs).coerceIn(0.0, MAX_HESITATION_MS.toDouble()).roundToInt()
        hesSum += ms; hesN++
        val h = d.hand.ordinal
        handHesSum[h] += ms.toLong(); handHesN[h]++
        if (d.single) slowest.merge(midi, ms, ::maxOf)
    }

    /** The group due is done: each note counts for its hand, clean when heard with no slip charged to it. */
    fun groupDone() {
        for (d in pending) {
            val h = d.hand.ordinal
            handDue[h]++
            if (d.heard && !d.slipped && !d.letGo) handClean[h]++
            if (!d.heard && !d.letGo) skipped++
        }
        pending.clear()
    }

    /** The group due was abandoned (a seek, a mode change): it counts for nothing. */
    fun dropDue() = pending.clear()

    /** A Perform group was played in time, [offsetMs] from its beat (negative early): its notes count clean. */
    fun performHit(notes: List<Note>, anyOf: Boolean, offsetMs: Double) {
        perNote(notes, anyOf) { h -> handDue[h]++; handClean[h]++ }
        offN++; offSum += offsetMs; offSq += offsetMs * offsetMs
    }

    /** A Perform group went by unplayed: its notes count as never played. */
    fun performMiss(notes: List<Note>, anyOf: Boolean) = perNote(notes, anyOf) { h -> handDue[h]++; skipped++ }

    private inline fun perNote(notes: List<Note>, anyOf: Boolean, each: (Int) -> Unit) {
        if (notes.isEmpty()) return
        if (anyOf) each(notes.first().hand.ordinal) else notes.forEach { each(it.hand.ordinal) }
    }

    // ---- results ----

    /** Notes that came due and were settled ([groupDone], [performHit], [performMiss]). */
    val notesDue: Int get() = handDue.sum()

    /** The mean ms from a note becoming due to its key being heard (wait mode), or null with none heard. */
    val hesitationMs: Int? get() = if (hesN == 0) null else (hesSum.toDouble() / hesN).roundToInt()

    /**
     * The standard deviation of a Perform run's beat offsets in ms (how steady the tempo was, whatever the run's own lean
     * early or late), or null with fewer than [MIN_SPREAD_HITS] notes graded.
     */
    val spreadMs: Int? get() {
        if (offN < MIN_SPREAD_HITS) return null
        val mean = offSum / offN
        return sqrt((offSq / offN - mean * mean).coerceAtLeast(0.0)).roundToInt()
    }

    /** The run's measures for its event (LessonStep or SongRun). */
    fun detail(): RunDetail = RunDetail(
        skippedNotes = skipped,
        hesitationMs = hesitationMs, hesitationN = hesN,
        slowKeys = slowest.entries.sortedWith(compareBy({ -it.value }, { it.key })).take(SLOW_KEYS).map { KeyMs(it.key, it.value) },
        left = hand(Hand.LEFT), right = hand(Hand.RIGHT),
        spreadMs = spreadMs,
        // The counts only when the list of waits was cut short: otherwise the list says the same.
        hintSteps = if (steps.sum() > hints.size) steps.toList() else emptyList(),
    )

    private fun hand(hand: Hand): HandNotes {
        val h = hand.ordinal
        return HandNotes(handDue[h], handClean[h], if (handHesN[h] == 0) 0 else (handHesSum[h].toDouble() / handHesN[h]).roundToInt(), handHesN[h])
    }

    /** A fresh run starts: everything forgotten. */
    fun clear() {
        pairs.clear(); hints.clear(); deepest = 0; steps.fill(0); near = 0; far = 0; octaves = 0; letGos = 0; open = false
        pending.clear(); handDue.fill(0); handClean.fill(0); handHesSum.fill(0); handHesN.fill(0); hesSum = 0; hesN = 0
        slowest.clear(); offN = 0; offSum = 0.0; offSq = 0.0; skipped = 0
    }

    /** This tally with [other]'s added (a lesson step's tries together). A group still pending is not carried. */
    fun plus(other: RunTally): RunTally = RunTally().also { t ->
        for (s in listOf(this, other)) {
            s.pairs.forEach { (k, v) -> t.pairs.merge(k, v, Int::plus) }
            s.hints.forEach { if (t.hints.size < MAX_HINTS) t.hints += it }
            for (i in 0..2) t.steps[i] += s.steps[i]
            t.near += s.near; t.far += s.far; t.octaves += s.octaves; t.letGos += s.letGos; t.skipped += s.skipped
            for (h in 0..1) {
                t.handDue[h] += s.handDue[h]; t.handClean[h] += s.handClean[h]
                t.handHesSum[h] += s.handHesSum[h]; t.handHesN[h] += s.handHesN[h]
            }
            t.hesSum += s.hesSum; t.hesN += s.hesN
            s.slowest.forEach { (k, v) -> t.slowest.merge(k, v, ::maxOf) }
            t.offN += s.offN; t.offSum += s.offSum; t.offSq += s.offSq
        }
    }

    /** A copy that later feeding does not change (the player keeps its own for the next run). */
    fun copy(): RunTally = RunTally().plus(this)

    /** The run's wrong-key pairs as events, most first (all of them, at most [top]), for a lesson or a song. */
    fun wrongKeyEvents(at: Long, lessonId: String? = null, songId: String? = null, top: Int = TOP_PAIRS): List<BriefEvent.WrongKey> =
        pairs.entries.sortedWith(compareBy({ -it.value }, { it.key.first }, { it.key.second })).take(top).map { (k, n) ->
            BriefEvent.WrongKey(at, k.first, k.second, WrongKeys.isSlip(abs(k.first - k.second)), n, lessonId, songId)
        }

    /** The octave hints of the run as one event, or null when there were none. */
    fun octaveEvent(at: Long, lessonId: String? = null, songId: String? = null): BriefEvent.OctaveHint? =
        if (octaves == 0) null else BriefEvent.OctaveHint(at, lessonId, songId, octaves)

    companion object {
        /**
         * Wrong-key pairs written per run: every (expected, played) pair, up to this many (the parent's slip map needs them
         * all; 24 keeps even a very rough run to a couple of dozen short lines).
         */
        const val TOP_PAIRS = 24
        /** Waits remembered per run (a long stuck run would make a long line); [RunDetail.hintSteps] counts past it. */
        const val MAX_HINTS = 32
        /** Keys whose slowest find is written per run ([RunDetail.slowKeys]). */
        const val SLOW_KEYS = 6
        /** A wait longer than this counts as this long: a child who wandered off without pausing is not "slow to find a key". */
        const val MAX_HESITATION_MS = 20_000
        /** Graded Perform notes a run needs before its spread means anything. */
        const val MIN_SPREAD_HITS = 4
    }
}
