package com.bigpapituna.sharpears.engine.arcade

/**
 * A stream round's plan: the [targets] in the order they come ([Target.atMs] after the stream starts, [Target.lifeMs]
 * on screen), the [demo] the pup plays first (the round's keys, "meet the keys"), the [keys] the ear listens for, and
 * how long the stream lasts ([roundMs]; nothing new comes in its last [TAIL_MS], and the round ends when the last target
 * is played or gone).
 */
data class StreamPlan(
    override val game: ArcadeGame, override val level: Int, override val roundMs: Long, override val targets: List<Target>, val demo: List<Int>,
    override val keys: Set<Int>, val demoGapMs: Long = 420, val pairWindowMs: Long = 500,
) : RoundPlan {
    val pairs: Boolean get() = targets.any { it.pair }
    /** When the last target is gone at the latest, from the stream's start. */
    val endsBy: Long get() = targets.maxOfOrNull { it.atMs + it.lifeMs } ?: roundMs

    companion object {
        /** The quiet end of a stream: nothing new comes this close to [roundMs]. */
        const val TAIL_MS = 2_000L
    }
}

/**
 * A stream round (balloons, treats, moles): the pup plays the round's keys first (the mic deaf), then the targets come on
 * their clock and go by on their own. A key heard ([key]) hits the oldest live target it finishes (a two-at-once target
 * needs both keys within [StreamPlan.pairWindowMs]); a key that is no live target's is a slip (nothing is lost). Two
 * targets gone by in a row light the keys of the ones that come next until the kid hits one: the stream's help, so a
 * kid who is lost is shown where to go and a round never dead-ends (it ends on its clock whatever happens).
 * Times are any forward clock in ms.
 */
class StreamRound(val plan: StreamPlan) : ArcadeRound {
    override val game get() = plan.game
    override val level get() = plan.level
    override val keys get() = plan.keys
    override val pairs get() = plan.pairs
    override val listening get() = phase == Phase.PLAY
    override val over get() = phase == Phase.DONE

    enum class Phase { IDLE, DEMO, PLAY, DONE }

    var phase = Phase.IDLE; private set
    private var demoEnds = 0L
    private var playFrom = 0L
    private var next = 0
    /** The targets on screen now, oldest first. */
    val live = mutableListOf<Target>()
    /** Each live pair's keys heard so far and when. */
    private val halves = HashMap<Int, MutableMap<Int, Long>>()
    var hits = 0; private set
    var gone = 0; private set
    var slips = 0; private set
    var streak = 0; private set
    private var missedInRow = 0
    /** The stream's help is on: new targets come lit. */
    var helping = false; private set
    private var endedAt = 0L

    /** The stream's own clock: ms since it began (0 before), for drawing where a target is. */
    fun clock(nowMs: Long): Long = if (phase == Phase.PLAY || phase == Phase.DONE) (if (phase == Phase.DONE) endedAt else nowMs) - playFrom else 0

    /** How far through the round, 0..1 (for the timer). */
    override fun progress(nowMs: Long): Float = (clock(nowMs).toFloat() / plan.roundMs).coerceIn(0f, 1f)

    /** The pup shows the keys first. */
    override fun start(nowMs: Long): List<Cue> {
        phase = Phase.DEMO
        demoEnds = nowMs + CallRound.FIRST_LEAD_MS + plan.demo.size * plan.demoGapMs + CallRound.DEMO_TAIL_MS
        return listOf(Cue.Demo(plan.demo, plan.demoGapMs, CallRound.FIRST_LEAD_MS, first = true))
    }

    override fun tick(nowMs: Long): List<Cue> {
        val out = mutableListOf<Cue>()
        if (phase == Phase.DEMO && nowMs >= demoEnds) {
            phase = Phase.PLAY; playFrom = nowMs
            out += Cue.Listen(first = true)
        }
        if (phase != Phase.PLAY) return out
        val t = nowMs - playFrom
        val gone = live.filter { t >= it.atMs + it.lifeMs }
        for (g in gone) {
            live.remove(g); halves.remove(g.id); this.gone++; streak = 0; missedInRow++
            if (missedInRow >= 2) helping = true
            out += Cue.Expired(g)
        }
        while (next < plan.targets.size && plan.targets[next].atMs <= t) {
            val s = plan.targets[next++]
            live += s
            out += Cue.Spawned(s, helping)
        }
        if (next >= plan.targets.size && live.isEmpty()) out += finish(nowMs)
        return out
    }

    /** A key heard (or touched) at [nowMs]. */
    override fun key(midi: Int, nowMs: Long): List<Cue> {
        if (phase != Phase.PLAY) return emptyList()
        // A single target it finishes, oldest first.
        live.firstOrNull { !it.pair && it.takes(midi) }?.let { return hit(it, midi, nowMs) }
        // Else a pair it belongs to: a half, or the pair whole with its partner heard just before.
        live.firstOrNull { it.pair && it.takes(midi) }?.let { p ->
            val heard = halves.getOrPut(p.id) { HashMap() }
            heard.entries.removeAll { nowMs - it.value > plan.pairWindowMs }
            heard[p.keyFor(midi)!!] = nowMs
            return if (p.keys.all { it in heard }) hit(p, midi, nowMs) else listOf(Cue.Half(p, midi))
        }
        slips++; streak = 0
        val near = live.flatMap { it.keys }.minByOrNull { kotlin.math.abs(it - midi) }
        return listOf(Cue.Slip(midi, near))
    }

    private fun hit(t: Target, midi: Int, nowMs: Long): List<Cue> {
        live.remove(t); halves.remove(t.id)
        hits++; streak++; missedInRow = 0; helping = false
        val out = mutableListOf<Cue>(Cue.Hit(t, midi, streak))
        if (next >= plan.targets.size && live.isEmpty()) out += finish(nowMs)
        return out
    }

    private fun finish(nowMs: Long): Cue.Finished {
        phase = Phase.DONE
        endedAt = nowMs
        return Cue.Finished(result(nowMs))
    }

    /** How the round went so far ([nowMs] for its length). */
    override fun result(nowMs: Long): RoundResult = RoundResult(plan.game, plan.level, offered = hits + gone,
        hits = hits, helped = gone, slips = slips, lines = hits, clean = hits, keys = plan.keys.size,
        durationMs = (if (phase == Phase.DONE) endedAt else nowMs) - playFrom)
}
