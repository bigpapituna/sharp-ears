package com.bigpapituna.sharpears.engine.lessons

/**
 * What earns a cheer pop (2.13, Roy: "some things where they sometimes pop up just to say 'Good job,' 'Continue,' or 'Don't
 * stop. You're doing so well,' ... similar to what Duolingo does"). [wire] is the tester event's and the log's name.
 */
enum class CheerTrigger(val wire: String) {
    /** 5, 10, 20 and 40 clean notes in a row. */
    RUN("run"),
    /** Three right notes straight after a slip. */
    RECOVERY("recovery"),
    /** Half of a long step or song part played. */
    HALFWAY("halfway"),
    /** A bar (or a chord change) that went astray earlier this session, played clean. */
    HARD_PASSED("hard_passed"),
    /** Eight notes in a row in the good timing window, where timing is graded (Perform). */
    STEADY("steady"),
    /** Every five minutes of practice without a long break. */
    FOCUS("focus"),
    /** Ten seconds mid-step with nothing played, at most once a step. */
    NUDGE("nudge"),
    /** A step passed: the line goes to the step pop (2.11), which shows it in a bubble with its picture. */
    STEP_PASSED("step_passed"),
}

/**
 * Settings > Playing > Encouragement, per profile (Roy, 2026-10-25: Often for everyone by default): [OFTEN] waits 12 s
 * between pops for kids and 15 s for grown-ups, four a minute at most; [LESS] doubles the waits and allows two a minute;
 * [OFF] has none (the step pop stays).
 */
enum class CheerFrequency(val wire: String, val title: String) {
    OFTEN("often", "Often"), LESS("less", "Less"), OFF("off", "Off");

    companion object {
        val DEFAULT = OFTEN
        fun parse(wire: String?): CheerFrequency = entries.firstOrNull { it.wire == wire } ?: DEFAULT
    }
}

/**
 * How the pup comes in (Roy wanted it not to feel canned): [SLIDE] slides in from the edge on a spring, [POP_UP] springs up
 * from below its spot, [PEEK] leans in round the edge, tilted, then rights himself. Never the same twice in a row.
 */
enum class CheerEntry { SLIDE, POP_UP, PEEK }

/** The corner of its place a pop sits in. A place with both corners free alternates them; one with one uses it. */
enum class CheerSide { END, START }

/**
 * One pop: its [trigger], the [line], how it comes in ([entry]), [other] (true every second pop: the other corner where the
 * place has two, else the slide comes down from the top edge instead of in from the side), how long it stays ([holdMs]),
 * and the run's [count] for a run pop.
 */
data class Cheer(val trigger: CheerTrigger, val line: String, val entry: CheerEntry, val other: Boolean, val holdMs: Long, val count: Int? = null)

/**
 * When an event happens: [nowMs] (uptime), [busy] (the voice is saying something or the app is playing a demo or a hint:
 * no pop over it), [tail] (the last two bars of a song, or the last stretch of a step: let them finish).
 */
data class CheerMoment(val nowMs: Long, val busy: Boolean = false, val tail: Boolean = false)

/**
 * Bars that went astray, per part (an exercise or a song, by id), for "a hard bar passed cleanly after it was missed earlier
 * in this session". One per process ([SESSION]): a lesson's retries and the next visit to the song share it until the app
 * is closed. A test makes its own.
 */
class CheerMemory {
    private val hard = HashMap<String, MutableSet<Int>>()
    fun mark(part: String, bar: Int) { hard.getOrPut(part) { HashSet() } += bar }
    fun isHard(part: String, bar: Int) = hard[part]?.contains(bar) == true
    fun clear(part: String, bar: Int) { hard[part]?.remove(bar) }

    companion object { val SESSION = CheerMemory() }
}

/**
 * The cheer pops' rules (2.13), Android-free and deterministic for a [seed]: what the learner did goes in ([stepBegan],
 * [right], [wrong], [progress], [tick]) and a [Cheer] comes out when one is earned and allowed, else null. A trigger that
 * comes while a pop is not allowed is let go (the next milestone may pop), except the focus pop and the nudge, which wait
 * for a free moment (the nudge only until something is played).
 *
 * Rate limits: never in the first [STEP_QUIET_MS] of a step or run; [GAP_MS] between pops ([KID_GAP_MS] for kids; Less
 * doubles both); at most [PER_MINUTE] in any minute (Less: [LESS_PER_MINUTE]); never while [CheerMoment.busy] or in the
 * [CheerMoment.tail]; none at all when [frequency] is Off. Lines: from the trigger's family for the audience ([kid]), never one of the last [RECENT] shown.
 */
class CheerCoach(val kid: Boolean, var frequency: CheerFrequency = CheerFrequency.DEFAULT, seed: Long = System.nanoTime(),
                 private val memory: CheerMemory = CheerMemory.SESSION) {
    private val random = java.util.Random(seed)
    private val recent = ArrayDeque<String>()
    private val shown = ArrayDeque<Long>()
    private var lastPopMs: Long? = null
    private var lastEntry: CheerEntry? = null
    private var other = true

    // The step (or the run of a song) under way.
    private var part = ""
    private var stepAt = Long.MIN_VALUE / 2
    private var run = 0
    private var sinceSlip: Int? = null
    private var steady = 0
    private var halfwayDone = false
    private var nudged = false
    // The bar being played: clean so far in this pass, a right note heard in it, and whether it was hard on the way in.
    private var bar: Int? = null
    private var barClean = true
    private var barHeard = false
    private var barWasHard = false

    // Time: the idle stretch (for the nudge) and practice without a long break (for the focus pop).
    private var lastTick: Long? = null
    private var idleMs = 0L
    private var focusMs = 0L
    private var focusShown = 0

    /** The pops shown, newest last (the debug screen and the tests read them). */
    val lines: List<String> get() = recent.toList()

    /** A step opened, a try started, or a song's run started from its top or a seek: the counts start again. */
    fun stepBegan(part: String, nowMs: Long) {
        this.part = part
        stepAt = nowMs
        run = 0; sinceSlip = null; steady = 0
        halfwayDone = false; nudged = false
        bar = null; barClean = true; barHeard = false; barWasHard = false
        idleMs = 0
    }

    /**
     * A right note (a wait group done, a Perform note hit, a stage's key found; a chord counts once). [bar]: the bar it
     * belongs to (a stage: its round), for the hard-bar pop; [onTime]: whether it was in the good timing window, where
     * timing is graded (null where it is not).
     */
    fun right(m: CheerMoment, bar: Int? = null, onTime: Boolean? = null): Cheer? {
        idleMs = 0
        val passed = enterBar(bar)
        barHeard = true
        val recovered = sinceSlip?.let { n -> (n + 1).also { sinceSlip = it } } == RECOVERY_NOTES
        if (recovered) sinceSlip = null
        run++
        when (onTime) { true -> steady++; false -> steady = 0; null -> {} }
        val milestone = run in CheerLines.RUN_MILESTONES
        val steadyNow = onTime == true && steady > 0 && steady % STEADY_NOTES == 0
        // One pop at most: the rarest thing first.
        val trigger = when {
            passed -> CheerTrigger.HARD_PASSED
            recovered -> CheerTrigger.RECOVERY
            milestone -> CheerTrigger.RUN
            steadyNow -> CheerTrigger.STEADY
            else -> return null
        }
        return pop(trigger, m, count = if (trigger == CheerTrigger.RUN) run else null)
    }

    /** A wrong key, or a Perform note let pass (a miss): the run starts again, the bar is marked hard. Never a pop. */
    fun wrong(m: CheerMoment, bar: Int? = null) {
        idleMs = 0
        enterBar(bar)
        run = 0; steady = 0; sinceSlip = 0
        barClean = false
        bar?.let { memory.mark(part, it) }
    }

    /**
     * The step or song part has [fraction] of its notes behind it; [long]: it is long enough to say so (a short step's
     * halfway is a moment, not news). Once per step, between half and [HALFWAY_UNTIL].
     */
    fun progress(m: CheerMoment, fraction: Double, long: Boolean): Cheer? {
        if (!long || halfwayDone || fraction < 0.5 || fraction > HALFWAY_UNTIL) return null
        return pop(CheerTrigger.HALFWAY, m)?.also { halfwayDone = true }
    }

    /**
     * Time goes by. [running]: the step's exercise or the song is playing (the learner's to play). Ten seconds of it with
     * nothing played and nothing [CheerMoment.busy] is a stall (one nudge a step); [FOCUS_EVERY_MS] of it with no break
     * longer than [FOCUS_BREAK_MS] is a focus pop.
     */
    fun tick(m: CheerMoment, running: Boolean): Cheer? {
        val gap = lastTick?.let { m.nowMs - it } ?: 0L
        val dt = if (gap in 0..MAX_TICK_MS) gap else 0L
        lastTick = m.nowMs
        if (gap > FOCUS_BREAK_MS) { focusMs = 0; focusShown = 0 }
        if (!running) return null
        focusMs += dt
        idleMs = if (m.busy) 0 else idleMs + dt
        if (focusMs >= (focusShown + 1) * FOCUS_EVERY_MS) {
            val minutes = ((focusShown + 1) * FOCUS_EVERY_MS / 60_000).toInt()
            pop(CheerTrigger.FOCUS, m, count = minutes)?.let { focusShown++; return it }
        }
        if (!nudged && idleMs >= STALL_MS) pop(CheerTrigger.NUDGE, m)?.let { nudged = true; return it }
        return null
    }

    /**
     * A step passed (StepPopRules' POP): the line for the step pop's bubble, or null when Encouragement is Off. It is the step
     * pop's own moment, so no wait applies, but it counts as a pop for the next one's wait.
     */
    fun stepPassed(nowMs: Long): Cheer? {
        if (frequency == CheerFrequency.OFF) return null
        return made(CheerTrigger.STEP_PASSED, nowMs, null)
    }

    /**
     * The debug screen (`--es screen cheerpop`): a pop of [trigger] now whatever the waits, its line picked as any other's
     * (a run cycles through 5, 10, 20 and 40, a focus pop through five to thirty minutes); [entry] forces how it comes in.
     */
    fun forced(trigger: CheerTrigger, nowMs: Long, entry: CheerEntry? = null): Cheer {
        val count = when (trigger) {
            CheerTrigger.RUN -> CheerLines.RUN_MILESTONES[forcedRuns++ % CheerLines.RUN_MILESTONES.size]
            CheerTrigger.FOCUS -> 5 * (1 + forcedRuns++ % 6)
            else -> null
        }
        val c = made(trigger, nowMs, count)
        if (entry != null) lastEntry = entry
        return if (entry != null) c.copy(entry = entry) else c
    }
    private var forcedRuns = 0

    /** A pop now, if the rules allow one. */
    private fun pop(trigger: CheerTrigger, m: CheerMoment, count: Int? = null): Cheer? = if (!allowed(m)) null else made(trigger, m.nowMs, count)

    /** Whether a pop may show at [m] (the rate limits). */
    fun allowed(m: CheerMoment): Boolean {
        if (frequency == CheerFrequency.OFF || m.busy || m.tail) return false
        if (m.nowMs - stepAt < STEP_QUIET_MS) return false
        val gap = gapMs(kid, frequency)
        if (lastPopMs?.let { m.nowMs - it < gap } == true) return false
        while (shown.isNotEmpty() && m.nowMs - shown.first() >= 60_000) shown.removeFirst()
        return shown.size < perMinute(frequency)
    }

    private fun made(trigger: CheerTrigger, nowMs: Long, count: Int?): Cheer {
        val line = pick(pool(trigger, count))
        recent.addLast(line); while (recent.size > RECENT) recent.removeFirst()
        shown.addLast(nowMs); lastPopMs = nowMs
        val entry = CheerEntry.entries.filter { it != lastEntry }.let { it[random.nextInt(it.size)] }
        lastEntry = entry
        other = !other
        return Cheer(trigger, line, entry, other, holdMs(line), count)
    }

    /**
     * The lines a pop picks from: the family, plus for a run the lines with its number (a run of 20 or 40 always says it; 5
     * and 10 roll between the number and the family's plain lines), and for a focus pop the lines naming its minutes.
     */
    private fun pool(trigger: CheerTrigger, count: Int?): List<String> = when {
        trigger == CheerTrigger.RUN && count != null ->
            if (count >= 20 || random.nextBoolean()) CheerLines.runNumbered(count, kid) else CheerLines.family(trigger, kid)
        trigger == CheerTrigger.FOCUS && count != null ->
            CheerLines.focusMinutes(count, kid).filter { it !in recent }.ifEmpty { CheerLines.family(trigger, kid) }
        else -> CheerLines.family(trigger, kid)
    }

    private fun pick(pool: List<String>): String {
        val fresh = pool.filter { it !in recent }.ifEmpty { pool }
        return fresh[random.nextInt(fresh.size)]
    }

    /** Into [b] (null: no bars here). Leaving a bar that was hard on the way in and played clean now: true (the pop). */
    private fun enterBar(b: Int?): Boolean {
        if (b == null || b == bar) return false
        val left = bar
        val earned = left != null && barWasHard && barClean && barHeard
        if (earned) memory.clear(part, left!!)
        bar = b; barClean = true; barHeard = false
        barWasHard = memory.isHard(part, b)
        return earned
    }

    companion object {
        const val STEP_QUIET_MS = 3_000L
        const val GAP_MS = 15_000L
        const val KID_GAP_MS = 12_000L
        const val PER_MINUTE = 4
        const val LESS_PER_MINUTE = 2
        const val RECENT = 6
        const val RECOVERY_NOTES = 3
        const val STEADY_NOTES = 8
        const val STALL_MS = 10_000L
        const val FOCUS_EVERY_MS = 5 * 60_000L
        /** A break this long (nothing playing) starts the focus count again. */
        const val FOCUS_BREAK_MS = 2 * 60_000L
        /** A frame or tick longer than this (the app off screen, a long pause) adds no practice time. */
        const val MAX_TICK_MS = 1_000L
        /** Past this share of the step the halfway pop is no longer news. */
        const val HALFWAY_UNTIL = 0.65
        const val HOLD_MS = 1_600L
        const val HOLD_MAX_MS = 2_400L

        /** The wait between pops: 15 s for grown-ups, 12 s for kids, twice that with Less. */
        fun gapMs(kid: Boolean, frequency: CheerFrequency): Long =
            (if (kid) KID_GAP_MS else GAP_MS) * if (frequency == CheerFrequency.LESS) 2 else 1

        /** Pops in any minute at most: four, two with Less. */
        fun perMinute(frequency: CheerFrequency): Int = if (frequency == CheerFrequency.LESS) LESS_PER_MINUTE else PER_MINUTE

        /** How long a pop stays: 1.6 s, longer lines a little more (50 ms a character past 18), at most 2.4 s. */
        fun holdMs(line: String): Long = (HOLD_MS + (line.length - 18).coerceAtLeast(0) * 50L).coerceAtMost(HOLD_MAX_MS)
    }
}

/**
 * The sounds of a cheer pop by the mic rules (2.13, the 2.11 chime's [ChimeMode]), one table for the sparkle and the voice.
 * During the learner's turn with the mic listening through the speaker, nothing sounds (making the mic deaf then would
 * swallow their note): the bubble alone. Between notes (a stage between its rounds, a rest in the music) the mic is made
 * deaf for it ([ChimeMode.PLAY_DEAF]: speakOverMic, deafFor). With the mic off or headphones on it plays as it is.
 */
object CheerRules {
    /** The sparkle's two notes, quieter than the step pop's chime (72 to 88). */
    const val SPARKLE_VELOCITY = 55
    const val SPARKLE_GAP_MS = 70
    const val SPARKLE_MS = 300
    /** The mic is deaf this long for a sparkle between notes: the notes and the room's ring after them. */
    const val SPARKLE_DEAF_MS = SPARKLE_MS + StepPopRules.ROOM_TAIL_MS
    /** A rest at least this long (real time) before the next note counts as between notes in the player. */
    const val BETWEEN_NOTES_MS = 2_000L

    /**
     * The sparkle: App sound off or the step pop's chime still ringing ([chiming]), none; else the mic rule
     * ([betweenNotes]: not the learner's turn to play).
     */
    fun soundMode(soundOn: Boolean, chiming: Boolean, micListening: Boolean, headphones: Boolean, betweenNotes: Boolean): ChimeMode = when {
        !soundOn || chiming -> ChimeMode.SKIP
        else -> micRule(micListening, headphones, betweenNotes)
    }

    /**
     * The voice: [spoken] (a kid's pop, or a grown-up's with "Say it aloud" on, and Spoken guidance on) or not at all, then
     * the mic rule.
     */
    fun voiceMode(spoken: Boolean, micListening: Boolean, headphones: Boolean, betweenNotes: Boolean): ChimeMode = when {
        !spoken -> ChimeMode.SKIP
        else -> micRule(micListening, headphones, betweenNotes)
    }

    private fun micRule(micListening: Boolean, headphones: Boolean, betweenNotes: Boolean): ChimeMode = when {
        !micListening || headphones -> ChimeMode.PLAY
        betweenNotes -> ChimeMode.PLAY_DEAF
        else -> ChimeMode.SKIP
    }

    /** The sparkle in the key of [tonic]: its fifth then its octave, high (in C: G6 then C7), [SPARKLE_GAP_MS] apart. */
    fun sparkle(tonic: Int): List<ChimeNote> {
        val pc = Math.floorMod(tonic, 12)
        val root = StepPopRules.HOME + if (pc <= 6) pc else pc - 12
        return listOf(ChimeNote(root + 7, SPARKLE_VELOCITY, 0), ChimeNote(root + 12, SPARKLE_VELOCITY, SPARKLE_GAP_MS))
    }

    /** Grown-ups hear a pop only with "Say it aloud" on (default off); kids by default ([aloud] null takes the audience's). */
    fun aloud(kid: Boolean, aloud: Boolean?): Boolean = aloud ?: kid
}

/**
 * Where a pop fits (2.13). Lessons: over the end of the header (the title row and the step dots), above the pup's
 * instruction, never over it. The song player: a top corner of the falling notes, no taller than [LANE_BAND] of the lane, so
 * it only ever sits over notes still more than a second and a half from the line (each is in view again before it is
 * being read), on the side with fewer of them coming through while it shows.
 */
object CheerPlace {
    /** The pup's size in a place [heightDp] tall: up to [maxDp], at least [MIN_PUP_DP]; null: no room (the pop is skipped). */
    fun pupDp(heightDp: Float, maxDp: Float = 56f): Float? {
        val pup = (heightDp - 8f).coerceAtMost(maxDp)
        return pup.takeIf { it >= MIN_PUP_DP }
    }
    const val MIN_PUP_DP = 36f

    /** The lane's top band a pop may cover, as a share of its height: notes in it are still this far up the lane. */
    const val LANE_BAND = 0.36f

    /** How tall a pop may be in a lane [laneDp] tall. */
    fun laneHeightDp(laneDp: Float): Float = laneDp * LANE_BAND

    /**
     * The corners of the lane a pop [widthShare] of its width wide may take now, best first: the one with fewer notes
     * passing under it through the top band while it shows (song time [positionMs] on, at [speed], for [holdMs]), then
     * [preferStart] (pops alternate). [allowed]: the corners the screen leaves free (the chord names sit top left, the video
     * top right). Empty when none is.
     */
    fun laneSides(notes: List<com.bigpapituna.sharpears.engine.Note>, lowMidi: Int, highMidi: Int, positionMs: Double, speed: Double,
                  holdMs: Long, widthShare: Float, allowed: Set<CheerSide>, preferStart: Boolean): List<CheerSide> {
        val keys = com.bigpapituna.sharpears.engine.KeyboardLayout(lowMidi, highMidi, 1f, 1f)
        val lookahead = com.bigpapituna.sharpears.engine.LOOKAHEAD_MS
        val from = positionMs + lookahead * (1 - LANE_BAND)
        val to = positionMs + holdMs * speed + lookahead
        fun under(side: CheerSide, x: Float) = if (side == CheerSide.START) x < widthShare else x > 1f - widthShare
        val crossing = notes.filter { it.midi in lowMidi..highMidi && it.startMs + it.durationMs >= from && it.startMs <= to }
            .map { keys.rect(it.midi).centerX }
        return allowed.sortedWith(compareBy<CheerSide> { side -> crossing.count { under(side, it) } }
            .thenBy { if ((it == CheerSide.START) == preferStart) 0 else 1 })
    }
}
