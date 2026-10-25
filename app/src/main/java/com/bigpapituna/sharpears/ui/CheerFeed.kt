package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.TroubleSpots
import com.bigpapituna.sharpears.engine.barMs
import com.bigpapituna.sharpears.engine.firstGroupAtOrAfter
import com.bigpapituna.sharpears.engine.lessons.Cheer
import com.bigpapituna.sharpears.engine.lessons.CheerRules
import com.bigpapituna.sharpears.engine.lessons.CheerSide
import com.bigpapituna.sharpears.ui.pup.CheerPops
import com.bigpapituna.sharpears.ui.pup.CheerSituation

/**
 * What the player tells the cheer pops (2.13), kept out of PlayerScreen's body (one object in its PlayerVars, so the body
 * holds no more locals; see PlayerVars for why that matters). A right note is a wait group done (a chord counts once) or a
 * Perform note hit, with its timing; a wrong key and a Perform note let pass break the run; each frame passes the time, the
 * misses and the run's progress. The moment it reports: busy while the app plays (Listen) or plays a hint, the tail (a
 * song's last two bars, a step's last stretch), and between notes when the next note is two seconds or more away.
 */
internal class CheerFeed {
    /** The song player's corner chooser (its lane sets it each composition); null in a lesson, which pops over its header. */
    var place: ((Cheer) -> CheerSide?)? = null

    private var song: Song? = null
    private var ready = false
    private var misses = 0
    private var firstMs = 0.0
    private var lastMs = 0.0
    private var long = false
    private var endMs = 0.0
    private var tailMs = 0.0
    private var bar = 1.0
    private var lastFrame = 0L

    /** A fresh run starts (Play from the top or after a seek): a new step for the rules. */
    fun began(cheers: CheerPops?, song: Song) {
        cheers ?: return
        cheers.stepBegan(song.id)
        ready = false
    }

    /** The run's span, its long-or-not, its tail: worked out at its first frame or note, once the transport has its start. */
    private fun ensure(t: Transport, song: Song, embedded: Boolean) {
        if (ready && this.song === song) return
        this.song = song
        val from = t.runFromMs
        val end = t.loop?.endInclusive ?: song.durationMs.toDouble()
        val need = song.groups.filter { g -> g.startMs >= from - 1 && g.startMs <= end && g.notes.any { t.required(it) } }
        firstMs = need.firstOrNull()?.startMs?.toDouble() ?: from
        lastMs = need.lastOrNull()?.startMs?.toDouble() ?: end
        long = need.size >= LONG_GROUPS
        endMs = end
        bar = barMs(song).coerceAtLeast(1.0)
        // A song lets its last two bars be; a lesson's step (often four bars) its last quarter at most.
        tailMs = if (embedded) minOf(2 * bar, (end - from) * 0.25) else 2 * bar
        misses = t.troubles.missedAt.size
        ready = true
    }

    private fun barOf(ms: Double) = TroubleSpots.barIndex(ms, bar)

    /** Song time to the next note the learner plays, from now. */
    private fun nextGapMs(t: Transport, song: Song): Double {
        val pos = t.positionMs
        if (t.mode == Mode.WAIT) return t.nextGroup?.let { it.startMs - pos } ?: Double.MAX_VALUE
        var i = firstGroupAtOrAfter(song.groups, pos)
        var steps = 0
        while (i < song.groups.size && steps++ < 64) {
            val g = song.groups[i]
            if (g.notes.any { t.required(it) }) return g.startMs - pos
            i++
        }
        return Double.MAX_VALUE
    }

    private fun situation(t: Transport, song: Song, hinting: Boolean) = CheerSituation(
        busy = t.mode == Mode.LISTEN || hinting,
        tail = t.positionMs >= endMs - tailMs,
        betweenNotes = !t.waiting && nextGapMs(t, song) >= CheerRules.BETWEEN_NOTES_MS * t.speed,
        place = place)

    /** A key struck: [result] as the transport saw it, [perfect] in the good window (Perform's "Perfect" light). */
    fun struck(cheers: CheerPops?, t: Transport, song: Song, result: InputResult, perfect: Boolean, embedded: Boolean, hinting: Boolean) {
        cheers ?: return
        if (t.mode == Mode.LISTEN || !t.playing) return
        ensure(t, song, embedded)
        val at = barOf(t.positionMs)
        when {
            result == InputResult.WRONG -> cheers.wrong(at)
            t.mode == Mode.WAIT && result == InputResult.GROUP_DONE -> cheers.right(at, null, situation(t, song, hinting))
            t.mode == Mode.PERFORM && result == InputResult.CORRECT -> cheers.right(at, perfect, situation(t, song, hinting))
        }
    }

    /** A frame while the run plays: the notes let pass, the run's progress and the time (ten times a second). */
    fun frame(cheers: CheerPops?, t: Transport, song: Song, nowMs: Long, hinting: Boolean, embedded: Boolean) {
        cheers ?: return
        if (nowMs - lastFrame < FRAME_MS) return
        lastFrame = nowMs
        ensure(t, song, embedded)
        val missed = t.troubles.missedAt
        if (missed.size < misses) misses = missed.size
        while (misses < missed.size) cheers.wrong(barOf(missed[misses++].toDouble()))
        val s = situation(t, song, hinting)
        if (lastMs > firstMs) cheers.progress((t.positionMs - firstMs) / (lastMs - firstMs), long, s)
        cheers.tick(running = t.mode != Mode.LISTEN, s)
    }

    companion object {
        /** A run with this many notes to play (groups) is long enough for a halfway pop. */
        const val LONG_GROUPS = 16
        const val FRAME_MS = 100L
        /** About how wide a song's pop is (the pup and a two-line bubble), for the side it takes. */
        const val POP_WIDTH_DP = 300f
    }
}
