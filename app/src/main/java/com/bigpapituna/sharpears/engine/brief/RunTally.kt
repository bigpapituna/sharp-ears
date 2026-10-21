package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.hints.WrongKeys
import kotlin.math.abs

/**
 * What one run of the player did that the brief wants and the transport's report does not keep: wrong keys by the note
 * they were reaching for (near or far), the hint escalation of each stuck wait, octave hints and groups let go. The
 * player (ui/PlayerScreen.kt) feeds it as things happen and hands it over when the run ends; a lesson adds its tries up
 * ([plus]) and logs one LessonStep, a song logs a SongRun. Pure, so it is tested on the JVM.
 */
class RunTally {
    private val pairs = LinkedHashMap<Pair<Int, Int>, Int>()
    private val hints = ArrayList<Int>()
    private var deepest = 0

    /** Wrong keys within two semitones of the note wanted. */
    var near = 0; private set
    /** Wrong keys further away (or with nothing wanted to compare with). */
    var far = 0; private set
    /** Octave hints shown. */
    var octaves = 0; private set
    /** Groups let go after three tries (Pup's Playground). */
    var letGos = 0; private set

    val wrongKeys get() = near + far
    /** One [HintDepth] per wait that needed a hint, in order (at most [MAX_HINTS]). */
    val hintsUsed: List<Int> get() = hints.toList()

    /** [played] was a wrong key; [nearest] is the expected note closest to it (null when nothing was expected). */
    fun wrong(played: Int, nearest: Int?) {
        val d = nearest?.let { abs(played - it) }
        if (d != null && WrongKeys.isSlip(d)) near++ else far++
        if (nearest != null) pairs.merge(nearest to played, 1, Int::plus)
    }

    /** Hint escalation reached [depth] ([HintDepth]) in the wait under way. */
    fun hint(depth: Int) { deepest = maxOf(deepest, depth.coerceIn(0, HintDepth.PLAY)) }

    /** The wait is over (played, paused, left): its deepest hint, if any, is counted. */
    fun waitEnded() {
        if (deepest > 0 && hints.size < MAX_HINTS) hints += deepest
        deepest = 0
    }

    fun octave() { octaves++ }
    fun letGo() { letGos++ }

    /** A fresh run starts: everything forgotten. */
    fun clear() { pairs.clear(); hints.clear(); deepest = 0; near = 0; far = 0; octaves = 0; letGos = 0 }

    /** This tally with [other]'s added (a lesson step's tries together). */
    fun plus(other: RunTally): RunTally = RunTally().also { t ->
        for (s in listOf(this, other)) {
            s.pairs.forEach { (k, v) -> t.pairs.merge(k, v, Int::plus) }
            s.hints.forEach { if (t.hints.size < MAX_HINTS) t.hints += it }
            t.near += s.near; t.far += s.far; t.octaves += s.octaves; t.letGos += s.letGos
        }
    }

    /** A copy that later feeding does not change (the player keeps its own for the next run). */
    fun copy(): RunTally = RunTally().plus(this)

    /** The run's most frequent wrong-key pairs as events, most first ([top] of them), for a lesson or a song. */
    fun wrongKeyEvents(at: Long, lessonId: String? = null, songId: String? = null, top: Int = TOP_PAIRS): List<BriefEvent.WrongKey> =
        pairs.entries.sortedWith(compareBy({ -it.value }, { it.key.first }, { it.key.second })).take(top).map { (k, n) ->
            BriefEvent.WrongKey(at, k.first, k.second, WrongKeys.isSlip(abs(k.first - k.second)), n, lessonId, songId)
        }

    /** The octave hints of the run as one event, or null when there were none. */
    fun octaveEvent(at: Long, lessonId: String? = null, songId: String? = null): BriefEvent.OctaveHint? =
        if (octaves == 0) null else BriefEvent.OctaveHint(at, lessonId, songId, octaves)

    companion object {
        /** Wrong-key pairs written per run: enough to see a habit, not a key-by-key log. */
        const val TOP_PAIRS = 5
        /** Waits remembered per run (a long stuck run would make a long line). */
        const val MAX_HINTS = 32
    }
}
