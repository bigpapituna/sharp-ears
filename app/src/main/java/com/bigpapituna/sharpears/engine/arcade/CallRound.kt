package com.bigpapituna.sharpears.engine.arcade

/** One line the pup calls: its [targets] in order, shown first when [demo]. */
data class Line(val targets: List<Target>, val demo: Boolean) {
    val keys: List<Int> get() = targets.map { it.key }
}

/**
 * A call round's plan: its [lines] (walks, crossings, bites, calls), the [keys] the ear listens for, [roundMs] on the
 * clock, a stuck beat's length given the hits so far ([beat]: the tower's quickens as it grows), the pause after a line
 * ([gapMs], a flower opening), and [song]: the lines are a song's, so when time is up the pup plays the rest. Endless
 * games plan more lines than a round can use and end on the clock. [needLines] for three stars.
 */
class CallPlan(
    override val game: ArcadeGame, override val level: Int, override val roundMs: Long, val lines: List<Line>, override val keys: Set<Int>,
    val song: Boolean = false, val demoGapMs: Long = 450, val gapMs: Long = 1_100, val needLines: Int = 0,
    val beat: (hits: Int) -> Long = { DEFAULT_BEAT_MS },
) : RoundPlan {
    override val targets: List<Target> get() = lines.flatMap { it.targets }

    companion object {
        /** A quiet beat with no key: a stuck beat. */
        const val DEFAULT_BEAT_MS = 3_000L
        /** Stuck beats before a target is let go. */
        const val LET_GO_BEATS = 3
    }
}

/**
 * A call round (garden, tower, frogs, fireworks, painter, bridge): line after line, each shown first by the pup when its
 * plan says (the mic deaf meanwhile), then heard note by note. A key that is the called target's moves on ([Cue.Hit]);
 * any other key is a slip and a stuck beat, as is a quiet [CallPlan.beat] with nothing right. One stuck beat lights the
 * key ([Cue.Nudge] 1), two have the pup play it as well (2), three let it go ([Cue.LetGo]: the pup plays it, the line
 * moves on). A line ends with [Cue.LineDone] and a pause; the round ends after its last line or when [CallPlan.roundMs]
 * is up (a song's rest is then played by the pup, [Cue.Rest]). Times are any forward clock in ms.
 */
class CallRound(val plan: CallPlan) : ArcadeRound {
    override val game get() = plan.game
    override val level get() = plan.level
    override val keys get() = plan.keys
    override val pairs get() = false
    override val listening get() = phase == Phase.LISTEN
    override val over get() = phase == Phase.DONE

    enum class Phase { IDLE, DEMO, LISTEN, GAP, DONE }

    var phase = Phase.IDLE; private set
    private var startAt = 0L
    private var demoEnds = 0L
    private var gapEnds = 0L
    private var quietFrom = 0L
    private var endedAt = 0L
    private var firstDemo = true
    /** The line and the note in it the kid is on. */
    var line = 0; private set
    var note = 0; private set
    /** Stuck beats on the current note. */
    var tries = 0; private set
    private var lineClean = true
    var hits = 0; private set
    var helped = 0; private set
    var slips = 0; private set
    var streak = 0; private set
    var linesDone = 0; private set
    var cleanLines = 0; private set

    /** When the current stuck beat began (the tower's ring), and how long a beat is now. */
    val beatFrom: Long get() = quietFrom
    val beatMs: Long get() = plan.beat(hits)

    /** The note being waited for, or null between lines or at the end. */
    val current: Target? get() = if (phase == Phase.LISTEN || phase == Phase.DEMO) plan.lines.getOrNull(line)?.targets?.getOrNull(note) else null

    /** The line being played (or shown), or null at the end. */
    val currentLine: Line? get() = if (phase == Phase.DONE) null else plan.lines.getOrNull(line)

    /** How far through the round, 0..1: the clock, or for a song the notes played or let go (whichever is further). */
    override fun progress(nowMs: Long): Float {
        val clock = (((if (phase == Phase.DONE) endedAt else nowMs) - startAt).toFloat() / plan.roundMs).coerceIn(0f, 1f)
        if (!plan.song) return clock
        val all = plan.lines.sumOf { it.targets.size }.coerceAtLeast(1)
        return maxOf(clock, (hits + helped).toFloat() / all)
    }

    override fun start(nowMs: Long): List<Cue> {
        startAt = nowMs
        return begin(0, nowMs)
    }

    private fun begin(i: Int, nowMs: Long): List<Cue> {
        line = i; note = 0; tries = 0; lineClean = true
        val l = plan.lines[i]
        if (!l.demo) { phase = Phase.LISTEN; quietFrom = nowMs; return listOf(Cue.Listen(first = false)) }
        val lead = if (firstDemo) FIRST_LEAD_MS else 0L
        phase = Phase.DEMO
        demoEnds = nowMs + lead + l.targets.size * plan.demoGapMs + DEMO_TAIL_MS
        return listOf(Cue.Demo(l.keys, plan.demoGapMs, lead, firstDemo))
    }

    override fun tick(nowMs: Long): List<Cue> {
        if (phase == Phase.IDLE || phase == Phase.DONE) return emptyList()
        if (nowMs - startAt >= plan.roundMs) return timeUp(nowMs)
        return when (phase) {
            Phase.DEMO -> if (nowMs < demoEnds) emptyList() else {
                phase = Phase.LISTEN; quietFrom = nowMs
                listOf(Cue.Listen(first = firstDemo)).also { firstDemo = false }
            }
            Phase.LISTEN -> if (nowMs - quietFrom >= plan.beat(hits)) stuck(nowMs) else emptyList()
            Phase.GAP -> if (nowMs < gapEnds) emptyList() else if (line + 1 < plan.lines.size) begin(line + 1, nowMs) else listOf(finish(nowMs))
            else -> emptyList()
        }
    }

    /** A key heard (or touched) at [nowMs]: only while listening (a demo is deaf). */
    override fun key(midi: Int, nowMs: Long): List<Cue> {
        if (phase != Phase.LISTEN) return emptyList()
        val t = current ?: return emptyList()
        if (t.takes(midi)) {
            hits++; streak++
            return listOf<Cue>(Cue.Hit(t, midi, streak)) + advance(nowMs)
        }
        slips++; streak = 0; lineClean = false
        return listOf<Cue>(Cue.Slip(midi, t.key)) + stuck(nowMs)
    }

    private fun stuck(nowMs: Long): List<Cue> {
        val t = current ?: return emptyList()
        tries++
        quietFrom = nowMs
        if (tries < CallPlan.LET_GO_BEATS) return listOf(Cue.Nudge(t, tries))
        helped++; streak = 0; lineClean = false
        return listOf<Cue>(Cue.LetGo(t)) + advance(nowMs)
    }

    private fun advance(nowMs: Long): List<Cue> {
        note++; tries = 0; quietFrom = nowMs
        val l = plan.lines[line]
        if (note < l.targets.size) return emptyList()
        linesDone++
        if (lineClean) cleanLines++
        phase = Phase.GAP
        gapEnds = nowMs + plan.gapMs
        return listOf(Cue.LineDone(line, lineClean))
    }

    private fun timeUp(nowMs: Long): List<Cue> {
        val out = mutableListOf<Cue>()
        if (plan.song) {
            // The rest of the song, from the note the kid is on (none in a gap) to the end: the pup plays it.
            val from = if (phase == Phase.GAP) line + 1 else line
            val rest = plan.lines.drop(from).flatMapIndexed { i, l -> if (i == 0 && phase != Phase.GAP) l.keys.drop(note) else l.keys }
            helped += rest.size
            if (rest.isNotEmpty()) out += Cue.Rest(rest)
        }
        out += finish(nowMs)
        return out
    }

    private fun finish(nowMs: Long): Cue.Finished {
        phase = Phase.DONE
        endedAt = nowMs
        return Cue.Finished(result(nowMs))
    }

    /** How the round went so far ([nowMs] for its length). */
    override fun result(nowMs: Long): RoundResult = RoundResult(plan.game, plan.level, offered = hits + helped, hits = hits, helped = helped,
        slips = slips, lines = if (plan.game == ArcadeGame.TOWER) hits else linesDone, clean = cleanLines, keys = plan.keys.size,
        durationMs = (if (phase == Phase.DONE) endedAt else nowMs) - startAt, needLines = plan.needLines)

    companion object {
        /** Before the round's first demo the pup says "Let me show you." */
        const val FIRST_LEAD_MS = 1_300L
        /** After a demo's last key, before listening. */
        const val DEMO_TAIL_MS = 700L
    }
}
