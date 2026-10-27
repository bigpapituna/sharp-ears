package com.bigpapituna.sharpears.engine.hints

/**
 * Why the microphone could not hear the piano (2.15 mic trust, docs/MIROFISH-REPORT-2026-10-26.md: 12 of 40 asked for a
 * clear "could not hear" state instead of a cheerful star). [wire] is the brief's and the tester event's word.
 */
enum class NotHeard(val wire: String) {
    /** Sound, but no piano note in it: talking, a TV, a loud room, or a piano too far from the phone. */
    NOISE("noise"),
    /** Near silence: nothing heard at all. */
    SILENCE("silence");

    /** The second line of the hint, under [MicHearing.TITLE]; also what the pup says to a kid. */
    val line: String get() = if (this == NOISE) MicHearing.NOISE_LINE else MicHearing.SILENCE_LINE

    companion object {
        fun of(s: String?): NotHeard? = entries.firstOrNull { it.wire == s || it.name == s }
    }
}

/**
 * "Never more than twice a minute", for the whole app: one lives on the microphone (MicInput.hearingLimit), so a lesson
 * whose player is made anew for every step still shares it.
 */
class HearingLimit(val max: Int = MicHearing.PER_MINUTE, val windowMs: Long = 60_000) {
    private val shown = ArrayDeque<Long>()

    fun allows(nowMs: Long): Boolean {
        while (shown.isNotEmpty() && nowMs - shown.first() >= windowMs) shown.removeFirst()
        return shown.size < max
    }

    fun shown(nowMs: Long) { shown.addLast(nowMs) }
}

/**
 * The "could not hear" rule (2.15). While the mic is on and the learner is expected to play, it reads the input level and
 * what the note matcher made of it, and answers once per step:
 *
 * - [NotHeard.NOISE]: [NOISE_MS] with no pitched onset (no right key, no wrong key, nothing touched) while the input
 *   shows sound: at least [NOISE_JUMPS] loudness jumps with no piano note in them (the matcher's noise, the same signal
 *   the room-noise watchdog counts) or at least [LOUD_SHARE] of the readings at [LOUD_DB] or louder (a vacuum, a loud TV).
 * - [NotHeard.SILENCE]: [SILENCE_MS] with nothing heard yet in this step and [QUIET_SHARE] of the readings under
 *   [QUIET_DB], with no jump at all.
 *
 * The clock only runs while the learner is expected to play: the caller says [hold] for a demo, the voice speaking, the
 * mic deaf, a count-in, a recap or anything else on top, and the stretch starts again when it resumes. Any pitched
 * onset ([pitched]) starts it again too. At most once per step ([newStep]) and, through [limit], twice a minute.
 *
 * Silence counts only before anything was heard in the step ("I can't hear anything yet"): a learner who played and is
 * now thinking for six seconds in a quiet room is not told to turn the piano up, and the hints' 6 s glow keeps that
 * moment to itself.
 *
 * Tuned on Roy's recordings (MicHearingTest.tunedOnRoysRoom, Fold7 bottom mic, voice recognition source): his room sits
 * at -58 to -66 dBFS between notes (TV and talking), so a level rule alone called every pause noisy; the jumps carry it.
 */
class MicHearing(private val limit: HearingLimit = HearingLimit()) {
    companion object {
        const val NOISE_MS = 4_000L
        const val SILENCE_MS = 6_000L
        /** Loudness jumps with no note in them, within [NOISE_MS], that make a room noisy. */
        const val NOISE_JUMPS = 3
        /** A reading this loud (dBFS) is sound whatever is in it; Roy's TV session peaks at -50, a piano note at -20 to -35. */
        const val LOUD_DB = -45.0
        const val LOUD_SHARE = 0.5
        /** Under this (dBFS) is near silence; Roy's room never got there (its quietest 5% sat at -58 to -66). */
        const val QUIET_DB = -60.0
        const val QUIET_SHARE = 0.9
        const val PER_MINUTE = 2

        /** The hint, shown near the listening indicator (2.15 copy). */
        const val TITLE = "I can't hear the piano clearly."
        const val NOISE_LINE = "It's a bit noisy here. Try moving the phone closer to the piano."
        const val SILENCE_LINE = "I can't hear anything yet. Is the piano's volume up?"
        /** Dismisses the hint. */
        const val CLOSER = "Move the phone closer"
        /** Switches this step to the screen's keys. */
        const val TOUCH = "Use the on-screen keys"
        /** What a kid hears the pup say (recorded at kid speed; SpokenCatalog). */
        val KID_LINES = listOf(NOISE_LINE, SILENCE_LINE)
        private const val NONE = Long.MIN_VALUE
    }

    private val levels = ArrayDeque<Reading>()
    private val jumps = ArrayDeque<Long>()
    private var since = NONE
    private var heardInStep = false

    /** The hint was shown in this step: not again until [newStep]. */
    var shownInStep = false
        private set

    private class Reading(val atMs: Long, val db: Double)

    /** A new step (a lesson step, a stage, a run of a song): one hint again, and silence counts again. */
    fun newStep() { shownInStep = false; heardInStep = false; restart() }

    /** A piano key was heard (right or wrong), or a key played on the screen or a MIDI keyboard: the stretch starts again. */
    fun pitched() { heardInStep = true; restart() }

    /** A loudness jump with no piano note in it (the matcher's noise), heard while the stretch runs. */
    fun unpitched(nowMs: Long) { if (since != NONE) jumps.addLast(nowMs) }

    /** The learner is not expected to play now (a demo, the voice, the mic deaf, a count-in, a sheet on top): the clock stops. */
    fun hold() = restart()

    /**
     * A level reading (dBFS) while the learner is expected to play. Returns the cause the moment the hint should show
     * (once per step, within the limit), else null.
     */
    fun level(nowMs: Long, db: Double): NotHeard? {
        observe(nowMs, db)
        if (shownInStep) return null
        val cause = judge(nowMs) ?: return null
        if (!limit.allows(nowMs)) return null
        limit.shown(nowMs)
        shownInStep = true
        return cause
    }

    /** Takes a reading without deciding (the room check reads [judge] itself). */
    fun observe(nowMs: Long, db: Double) {
        if (since == NONE) since = nowMs
        levels.addLast(Reading(nowMs, db))
        while (levels.isNotEmpty() && nowMs - levels.first().atMs > SILENCE_MS) levels.removeFirst()
        while (jumps.isNotEmpty() && nowMs - jumps.first() > SILENCE_MS) jumps.removeFirst()
    }

    /** What the stretch so far says now, ignoring the once-per-step rule and the limit. */
    fun judge(nowMs: Long): NotHeard? {
        if (since == NONE) return null
        val run = nowMs - since
        if (run >= NOISE_MS) {
            val window = levels.filter { nowMs - it.atMs <= NOISE_MS }
            val noisy = jumps.count { nowMs - it <= NOISE_MS } >= NOISE_JUMPS
            val loud = window.isNotEmpty() && window.count { it.db >= LOUD_DB } >= window.size * LOUD_SHARE
            if (noisy || loud) return NotHeard.NOISE
        }
        if (run >= SILENCE_MS && !heardInStep && jumps.isEmpty()) {
            val window = levels.filter { nowMs - it.atMs <= SILENCE_MS }
            if (window.isNotEmpty() && window.count { it.db < QUIET_DB } >= window.size * QUIET_SHARE) return NotHeard.SILENCE
        }
        return null
    }

    private fun restart() { since = NONE; levels.clear(); jumps.clear() }
}

/**
 * Microphone setup's room check (2.15): ten seconds to play C, E and G (middle C's octave, [KEYS]), judged by the same rule
 * as the "could not hear" hint. All three heard in a room that never read as noisy is [Verdict.CLEAR]; notes heard in a
 * room the rule calls noisy (in a stretch between notes, or [MicHearing.NOISE_JUMPS] jumps with no note in them within
 * [MicHearing.NOISE_MS] at any time) is [Verdict.NOISY]; nothing heard, or not all three, is [Verdict.TRY_CLOSER].
 */
class RoomCheck(val startMs: Long) {
    enum class Verdict(val title: String, val line: String) {
        CLEAR("Heard clearly", "C, E and G all came through. You're ready to play."),
        NOISY("Heard, a bit noisy", "The notes came through, with some noise around them. A quieter room or a closer phone helps."),
        TRY_CLOSER("Couldn't hear: try closer", "Put the phone on the music stand, close to the keys, and try again."),
    }

    companion object {
        const val DURATION_MS = 10_000L
        /** C4, E4 and G4: what the check listens for. */
        val KEYS = setOf(60, 64, 67)
        const val PROMPT = "Play C, E and G, one after another. It takes ten seconds."
    }

    private val hearing = MicHearing(HearingLimit(max = Int.MAX_VALUE))
    private val jumps = ArrayDeque<Long>()
    /** The keys of [KEYS] heard so far. */
    val heard = linkedSetOf<Int>()
    var noisy = false
        private set

    fun done(nowMs: Long) = nowMs - startMs >= DURATION_MS
    /** 0..1 of the ten seconds. */
    fun progress(nowMs: Long) = ((nowMs - startMs).toDouble() / DURATION_MS).coerceIn(0.0, 1.0)

    /** A key the matcher heard (only [KEYS] count). */
    fun note(midi: Int) { if (midi in KEYS) heard += midi; hearing.pitched() }

    /** A loudness jump with no note in it. */
    fun unpitched(nowMs: Long) {
        hearing.unpitched(nowMs)
        jumps.addLast(nowMs)
        while (jumps.isNotEmpty() && nowMs - jumps.first() > MicHearing.NOISE_MS) jumps.removeFirst()
        if (jumps.size >= MicHearing.NOISE_JUMPS) noisy = true
    }

    fun level(nowMs: Long, db: Double) {
        if (done(nowMs)) return
        hearing.observe(nowMs, db)
        if (hearing.judge(nowMs) == NotHeard.NOISE) noisy = true
    }

    fun verdict(): Verdict = when {
        heard.isEmpty() -> Verdict.TRY_CLOSER
        noisy -> Verdict.NOISY
        heard.size == KEYS.size -> Verdict.CLEAR
        else -> Verdict.TRY_CLOSER
    }
}
