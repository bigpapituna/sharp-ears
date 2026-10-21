package com.bigpapituna.sharpears.engine

enum class Mode { LISTEN, WAIT, PERFORM }
enum class Practice { BOTH, LEFT, RIGHT }
enum class InputResult { CORRECT, GROUP_DONE, WRONG, IGNORED }

/** A correct note this far (song ms) before its group still counts, so playing at tempo does not stutter. */
const val EARLY_HIT_MS = 200.0

/**
 * The most keys of one any-of group the microphone is asked to listen for. The matcher's work grows with every key it
 * listens for (measured on the JVM: about 1.5% of a desktop core per key, so 36 keys took half a core), so a bigger range
 * is listened for through this many keys spread over it ([micKeys]). The Playground's high-and-low game, whose ranges
 * are half the piano, hears pitch its own way (engine/playground/PitchHeight.kt).
 */
const val MIC_ANY_OF_LIMIT = 12

/**
 * At most [limit] of an any-of group's [keys] (demonstration key first) for the microphone: the demonstration key, then
 * white keys spread evenly over the range. All of them when they fit.
 */
fun micKeys(keys: List<Int>, limit: Int = MIC_ANY_OF_LIMIT): Set<Int> {
    if (keys.size <= limit) return keys.toSet()
    val demo = keys.first()
    val whites = keys.drop(1).filter { !isBlack(it) }.sorted().ifEmpty { keys.drop(1).sorted() }
    val k = limit - 1
    val picked = if (whites.size <= k) whites else (0 until k).map { i -> whites[(i * (whites.size - 1).toDouble() / (k - 1)).let(Math::round).toInt()] }
    return linkedSetOf(demo) + picked
}

/**
 * Plays a song through time. Pure state machine: the UI ticks it once per frame with the real frame
 * delta and forwards the returned events to the sampler.
 *
 * Listen mode plays every note. Wait mode stops at each onset group until the player has played every
 * *required* note in it: notes of the practiced hand that are on the visible keyboard ([playable]).
 * Other notes are accompaniment and play by themselves.
 *
 * Any-of groups (2.1, [OnsetGroup.anyOf]: "any key above middle C") are alternatives: one of their keys completes the
 * group, in wait mode and in Perform. Their keys count as required wherever they are (a real piano is wider than the
 * keyboard on screen), and only the first, the demonstration key, ever sounds by itself.
 */
class Transport(val song: Song, val playable: IntRange = 0..127) {
    var positionMs = 0.0
        private set
    var playing = false
        private set
    var speed = 1.0
        set(value) { field = value.coerceIn(0.25, 1.0) }
    var mode = Mode.LISTEN
        set(value) { field = value; resetGroups() }
    var practice = Practice.BOTH
        set(value) { field = value; resetGroups() }
    /** A–B loop in song ms. Reaching the end jumps back to the start. */
    var loop: ClosedFloatingPointRange<Double>? = null

    /**
     * Notes a group of four or more may still be missing when it counts as played. The player sets 1 while
     * the mic listens: in dense chords some notes' overtones are entirely shared with others and cannot be
     * heard apart. Touch and MIDI keep 0 (every note).
     */
    var chordSlack = 0

    /** Perform-mode scoring for the run that started at [scoreFromMs]; a seek or a mode change starts a new run. */
    var score: PerformScore? = null
        private set
    var scoreFromMs = 0.0
        private set

    /** Wrong keys and retries since the last seek or mode change (a run from where it started). */
    val log = RunLog()
    /** Where the run was hard: holds, wrong keys and misses by song time, for [TroubleSpots]. */
    val troubles = TroubleLog()
    /**
     * What the parent brief keeps of the run (engine/brief/RunTally): wrong keys by the note wanted, hints per stuck wait,
     * octave hints, groups let go. The player feeds it and clears it when a fresh run starts. Kept here rather than as
     * one more local in PlayerScreen: that composable is near the size where D8 allocated a null check to the wrong
     * register (a `meta?.let` tested another parameter and crashed on a lesson's player, found on the emulator).
     * The transport feeds it the notes itself: each wait group as it comes due and its keys as they are heard (the
     * hesitation and each hand's clean notes), each Perform group played in time or missed.
     */
    val tally = com.bigpapituna.sharpears.engine.brief.RunTally()
    /** The group the tally is timing (wait mode), so it is registered once. */
    private var dueGroup = -1

    /** The run so far, for feedback. */
    fun report() = log.report(score?.recap())

    /** Bumps each time the A-B loop wraps; [lastPass] is the report of the pass that just ended. */
    var passes = 0
        private set
    var lastPass: Report? = null
        private set

    /** True while wait mode holds the song at a group the player has not finished. */
    var waiting = false
        private set

    /** Notes currently sounding, at most one per key. The UI lights these keys. */
    val sounding: List<Note> get() = _sounding
    private val _sounding = mutableListOf<Note>()
    private var nextIndex = 0                     // first note that has not started yet
    private var groupIndex = 0                    // next group that needs the player's input (wait mode)
    private val satisfied = mutableSetOf<Int>()   // keys already played for that group
    /** Notes of any-of groups, and the ones among them that never sound by themselves (all but each group's first). */
    private val choiceNotes: Set<Note> = song.groups.filter { it.anyOf }.flatMapTo(HashSet()) { it.notes }
    private val silentChoices: Set<Note> = song.groups.filter { it.anyOf }.flatMapTo(HashSet()) { it.notes.drop(1) }

    fun required(n: Note) = mode != Mode.LISTEN && (n.midi in playable || n in choiceNotes) && when (practice) {
        Practice.BOTH -> true
        Practice.LEFT -> n.hand == Hand.LEFT
        Practice.RIGHT -> n.hand == Hand.RIGHT
    }

    /** Required notes of the group being waited for that are still to be played. */
    val expected: List<Note>
        get() = if (!waiting) emptyList()
        else song.groups.getOrNull(groupIndex)?.notes?.filter { required(it) && it.midi !in satisfied }.orEmpty()

    /**
     * What the microphone should listen for: the next group's required notes not yet played (wait mode only). For an
     * any-of group, its keys, at most [MIC_ANY_OF_LIMIT] of them ([micKeys]).
     */
    val listenFor: Set<Int>
        get() = if (mode == Mode.PERFORM) score?.listening(positionMs).orEmpty() else if (mode != Mode.WAIT) emptySet()
        else song.groups.getOrNull(groupIndex)?.let { g ->
            val keys = g.notes.filter { required(it) && it.midi !in satisfied }.map { it.midi }
            if (g.anyOf) micKeys(keys) else keys.toSet()
        }.orEmpty()

    /** All required notes of that group, including ones already played; they ring together. An any-of group: as [listenFor]. */
    val listenGroup: Set<Int>
        get() = if (mode == Mode.PERFORM) listenFor else if (mode != Mode.WAIT) emptySet()
        else song.groups.getOrNull(groupIndex)?.let { g ->
            val keys = g.notes.filter(::required).map { it.midi }
            if (g.anyOf) micKeys(keys) else keys.toSet()
        }.orEmpty()

    /**
     * [listenGroup] as NoteMatcher.groups: one group for a chord, and every key on its own for an any-of group, whose
     * keys never sound together (as one group the matcher would set their shared overtones aside and hear less).
     */
    val listenGroups: List<Set<Int>>
        get() = if (nextGroup?.anyOf == true && mode == Mode.WAIT) listenGroup.map { setOf(it) } else listOf(listenGroup)

    /** The next group that needs the player's input (wait mode), whether or not the song has reached it yet. */
    val nextGroup: OnsetGroup? get() = if (mode == Mode.LISTEN) null else song.groups.getOrNull(groupIndex)

    /**
     * Keys to show or play as a hint for the group being waited for: its notes still to play, or for an any-of group
     * only its demonstration key (playing every alternative would be a cluster).
     */
    val hintKeys: List<Int>
        get() = if (!waiting) emptyList() else song.groups.getOrNull(groupIndex)?.let { g ->
            val keys = g.notes.filter { required(it) && it.midi !in satisfied }.map { it.midi }
            if (g.anyOf) keys.take(1) else keys
        }.orEmpty()

    /**
     * Where the current run starts: the top, the last seek, or where the hands or mode last changed. Notes
     * before it are behind the player. The timeline shows this while the lead-in runs.
     */
    var runFromMs = 0.0
        private set
    /** The first group of the run (or [runFromMs] when none is left): the lead-in and count-in end here. */
    var anchorMs = 0.0
        private set
    /** The run has not been played yet, so the next [play] may start with a lead-in. */
    var fresh = true
        private set
    /** In the lead-in (or the preview before it): the song has not reached the run's first notes yet. */
    val inLeadIn get() = positionMs < anchorMs
    /** The position to remember and show: never earlier than where the run started. */
    val shownMs get() = maxOf(positionMs, runFromMs)

    init { resetGroups() }

    /**
     * Before Play: shows the run's first notes [aheadMs] above the hit line (the lane looks [LOOKAHEAD_MS] ahead).
     * Only while the run is fresh; it does not change what is played.
     */
    fun preview(aheadMs: Double) {
        if (!fresh || playing) return
        positionMs = anchorMs - aheadMs
    }

    /**
     * [leadInMs]: a fresh run (from the top, or after a seek) starts this far before its first group, so those
     * notes are seen falling (and, in Perform, counted in) before they arrive. A preview further back is kept.
     * Resuming after a pause has no lead-in.
     */
    fun play(leadInMs: Double = 0.0) {
        if (positionMs >= song.durationMs) seek(0.0)
        if (fresh && leadInMs > 0) positionMs = minOf(positionMs, anchorMs - leadInMs)
        fresh = false
        playing = true
    }

    fun pause(): List<NoteEvent> { playing = false; return silence() }

    fun seek(ms: Double): List<NoteEvent> {
        val off = silence()
        positionMs = ms.coerceIn(0.0, song.durationMs.toDouble())
        runFromMs = positionMs
        nextIndex = firstStartAtOrAfter(song.notes, positionMs)
        resetGroups()
        return off
    }

    /** Real ms the run has played since its first group (holds included, the lead-in not), for the Auto speed. */
    var runRealMs = 0.0
        private set
    /** Song ms the run has covered since its first group. */
    val runSongMs get() = (positionMs - anchorMs).coerceAtLeast(0.0)

    fun tick(dtMs: Double): List<NoteEvent> {
        if (!playing) return emptyList()
        if (positionMs >= anchorMs) runRealMs += dtMs
        var end = (positionMs + dtMs * speed).coerceAtMost(song.durationMs.toDouble())
        // "<=" so pressing B at the playhead wraps on the next frame; a seek past B plays on. A loop whose B lies past the
        // end wraps at the end; one that runs backwards (B before A) or starts past the end is no loop.
        loop?.takeIf { it.start < it.endInclusive && it.start < song.durationMs }?.let { l ->
            val b = minOf(l.endInclusive, song.durationMs.toDouble())
            if (positionMs <= b && end >= b) {
                // Grade the groups before B (the ones after it were never reached), then start the next pass.
                score?.advance(b + OK_MS)
                lastPass = report(); passes++
                return seek(l.start)
            }
        }
        if (mode == Mode.WAIT) {
            val g = song.groups.getOrNull(groupIndex)
            if (waiting && g != null) troubles.waited.merge(g.startMs, dtMs, Double::plus)
            if (g != null && g.startMs <= end) { end = g.startMs.toDouble(); waiting = true; due(groupIndex, runRealMs) }
        }
        val events = mutableListOf<NoteEvent>()
        // Release first, so a note that ends exactly where the next one on the same key starts re-triggers cleanly.
        _sounding.removeAll { n -> (n.startMs + n.durationMs <= end).also { if (it) events += NoteEvent.Off(n.midi) } }
        while (nextIndex < song.notes.size && song.notes[nextIndex].startMs < end) {
            val n = song.notes[nextIndex++]
            if (required(n)) continue                 // the player plays these
            if (n in silentChoices) continue          // an any-of group sounds only its demonstration key
            _sounding.removeAll { it.midi == n.midi } // re-strike: the sampler restarts the key
            _sounding += n
            events += NoteEvent.On(n.midi, n.velocity)
        }
        positionMs = end
        score?.advance(end)
        if (end >= song.durationMs) { playing = false; events += silence(); score?.advance(Double.MAX_VALUE) }
        return events
    }

    /**
     * A key the player struck. Listen mode ignores input. In Perform mode [latencyMs] (real time between the
     * strike and this call, e.g. the mic's detection delay) moves the strike back to when it was played.
     */
    fun noteInput(midi: Int, latencyMs: Double = 0.0): InputResult {
        if (mode == Mode.PERFORM) {
            if (!playing) return InputResult.IGNORED
            val at = positionMs - latencyMs * speed
            if (at < anchorMs - OK_MS) return InputResult.IGNORED      // the count-in: hands finding their place
            val open = score?.listening(at).orEmpty()
            if (score?.input(midi, at) != null) return InputResult.CORRECT
            log.wrong(midi, open)
            troubles.wrongAt += at
            return InputResult.WRONG
        }
        if (mode != Mode.WAIT) return InputResult.IGNORED
        val g = song.groups.getOrNull(groupIndex) ?: return InputResult.IGNORED
        if (!waiting && g.startMs - positionMs > EARLY_HIT_MS) return InputResult.IGNORED
        val need = g.notes.filter(::required).map { it.midi }.toSet()
        if (midi !in need) {
            // A slip is charged to the nearest note it was reaching for; no single key of an any-of group was the one.
            log.wrong(midi, if (g.anyOf) emptyList() else need - satisfied)
            troubles.wrongAt += g.startMs.toDouble()
            return InputResult.WRONG
        }
        // The brief's tally times the wait from the group coming due (a key struck just before it waited for nothing).
        due(groupIndex, runRealMs + ((g.startMs - positionMs) / speed).coerceAtLeast(0.0))
        tally.heard(midi, runRealMs - latencyMs)
        // A chord needs every note (less the mic's slack); one key of an any-of group is enough.
        if (!g.anyOf) {
            satisfied += midi
            val slack = if (need.size >= 4) chordSlack else 0
            if (need.count { it !in satisfied } > slack) return InputResult.CORRECT
        }
        satisfied.clear()
        waiting = false
        log.groupDone()
        tally.groupDone()
        groupIndex = nextRequiredGroup(groupIndex + 1)
        return InputResult.GROUP_DONE
    }

    /** The group at [index] came due at [atMs] (the run's real time): the tally starts timing its notes, once per group. */
    private fun due(index: Int, atMs: Double) {
        if (dueGroup == index) return
        dueGroup = index
        val g = song.groups.getOrNull(index) ?: return
        tally.due(g.notes.filter(::required), g.anyOf, atMs)
    }

    /** The group starting at [startMs] (a Perform target), for the tally's hands. */
    private fun groupAt(startMs: Long): OnsetGroup? =
        song.groups.getOrNull(firstGroupAtOrAfter(song.groups, startMs.toDouble()))?.takeIf { it.startMs == startMs }

    private fun resetGroups() {
        log.reset()
        troubles.reset()
        dueGroup = -1
        tally.dropDue()
        runRealMs = 0.0
        satisfied.clear()
        waiting = false
        // In a lead-in or preview the run still starts where it was cued, not at the earlier playhead.
        val from = maxOf(positionMs, runFromMs)
        runFromMs = from
        anchorMs = song.groups.getOrNull(firstGroupAtOrAfter(song.groups, from))?.startMs?.toDouble() ?: from
        fresh = true
        scoreFromMs = from
        score = if (mode == Mode.PERFORM) PerformScore(song.groups, ::required, from,
            onHit = { start, off -> groupAt(start)?.let { g -> tally.performHit(g.notes.filter(::required), g.anyOf, off) } },
            onMiss = { start -> troubles.missedAt += start; groupAt(start)?.let { g -> tally.performMiss(g.notes.filter(::required), g.anyOf) } },
        ) else null
        groupIndex = nextRequiredGroup(firstGroupAtOrAfter(song.groups, from))
    }

    private fun nextRequiredGroup(from: Int): Int {
        var i = from
        while (i < song.groups.size && song.groups[i].notes.none(::required)) i++
        return i
    }

    private fun silence(): List<NoteEvent> = _sounding.map { NoteEvent.Off(it.midi) }.also { _sounding.clear() }
}

fun firstGroupAtOrAfter(groups: List<OnsetGroup>, ms: Double): Int {
    var lo = 0
    var hi = groups.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (groups[mid].startMs < ms) lo = mid + 1 else hi = mid
    }
    return lo
}

/** Index of the first note starting at or after [ms]. Notes are sorted by start time. */
fun firstStartAtOrAfter(notes: List<Note>, ms: Double): Int {
    var lo = 0
    var hi = notes.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (notes[mid].startMs < ms) lo = mid + 1 else hi = mid
    }
    return lo
}

const val LOOKAHEAD_MS = 2500.0
const val LOOKBEHIND_MS = 500.0

/** Vertical geometry of the falling-notes lane. Song time maps linearly to y; the hit line is the lane's bottom edge. */
class LaneGeometry(val laneHeight: Float) {
    val hitLineY = laneHeight
    val pxPerMs = laneHeight / LOOKAHEAD_MS.toFloat()
    /** Bottom edge of a note block (its start); the block extends upward by its duration. */
    fun bottomY(note: Note, positionMs: Double) = hitLineY - ((note.startMs - positionMs) * pxPerMs).toFloat()
    fun topY(note: Note, positionMs: Double) = bottomY(note, positionMs) - maxOf(note.durationMs * pxPerMs, 8f)

    /** Notes that can be on screen: started at most [LOOKBEHIND_MS] (plus their length) ago, starting within the lookahead. */
    fun visible(song: Song, positionMs: Double, longestNoteMs: Long): List<Note> {
        val from = firstStartAtOrAfter(song.notes, positionMs - LOOKBEHIND_MS - longestNoteMs)
        val to = firstStartAtOrAfter(song.notes, positionMs + LOOKAHEAD_MS)
        return song.notes.subList(from, to).filter { it.startMs + it.durationMs >= positionMs - LOOKBEHIND_MS }
    }
}

/**
 * Keyboard range for a song: lowest to highest note, widened to whole octaves (C to C), at least 2 and
 * at most 5 octaves. A wider song keeps the 5-octave window that covers the most notes.
 */
fun fitRange(notes: List<Note>): IntRange {
    if (notes.isEmpty()) return 48..84
    val low = notes.minOf { it.midi }.let { it - it % 12 }
    val high = notes.maxOf { it.midi }.let { if (it % 12 == 0) it else it + 12 - it % 12 }
    return when {
        high - low < 24 -> low..(low + 24)
        high - low <= 60 -> low..high
        // ponytail: notes outside the window are not drawn; a pan/zoom keyboard is the upgrade if a song needs it
        else -> (low..(high - 60) step 12).maxBy { lo -> notes.count { it.midi in lo..(lo + 60) } }.let { it..(it + 60) }
    }
}
