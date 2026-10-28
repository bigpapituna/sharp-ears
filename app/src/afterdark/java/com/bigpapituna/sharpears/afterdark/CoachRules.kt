package com.bigpapituna.sharpears.afterdark

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.lessons.CheerCoach
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import com.bigpapituna.sharpears.extras.Moment

/*
 * 2.16, the coach voice (CLAUDE.md "2.16 coach voice"): Roy's ElevenLabs banks, one per voice (157 lines each), a low, flirty
 * grown-up coach for consenting adults, talking to the player as a woman ("good girl") or a man ("good boy"). Everything about it lives in src/afterdark; this file is its rules, Android-free and tested on the JVM
 * (src/afterdark/test). The door (extras/Extras.kt here) plays what [CoachBrain] picks, only while [CoachGate.on] says so.
 */

/**
 * One recorded line: its file ("run5_03_w" is assets/coach/<voice>/run5_03_w.ogg), its bank, who it is for ([forWho]: "any",
 * or "w" / "m", the line said to a woman or to a man), the caption shown, its length.
 */
data class CoachLine(val id: String, val moment: String, val forWho: String, val caption: String, val ms: Long)

/**
 * A voice: its folder under assets/coach ([id], the tester's `coach_voice_id`), the name Settings shows and where in the list
 * (assets/coach/<voice>/voice.json, written by scripts/coach_bank.py). The voices are whatever folders the build has: a new
 * folder is a new choice, no code change.
 */
data class CoachVoice(val id: String, val name: String, val order: Int) {
    companion object {
        fun parse(json: String): CoachVoice = (MiniJson.read(json) as Map<*, *>).let { m ->
            CoachVoice(m["id"] as String, m["name"] as String, (m["order"] as? Number)?.toInt() ?: 99)
        }
        /** The voices in the order Settings lists them (then by name). */
        fun sorted(voices: List<CoachVoice>) = voices.sortedWith(compareBy<CoachVoice> { it.order }.thenBy { it.name })
    }
}

/** A voice's bank, read from assets/coach/<voice>/lines.json (scripts/coach_bank.py writes it): the lines of each moment. */
class CoachBank(val lines: List<CoachLine>) {
    private val byMoment = lines.groupBy { it.moment }
    /** The lines of [moment] for a player who is [forWho] ("w" or "m"; null: not said, so only the lines for anyone). */
    fun of(moment: String, forWho: String? = null): List<CoachLine> = byMoment[moment].orEmpty().filter { it.forWho == ANY || it.forWho == forWho }

    companion object {
        /** Every bank the recordings have, one or more lines each. */
        val MOMENTS = listOf("start", "run5", "run10", "run20", "run40", "run", "recovery", "halfway", "hard_passed", "steady",
            "focus5", "focus10", "focus15", "focus20", "focus30", "nudge", "step_passed", "mistake", "hard", "finish", "stars3",
            "stars2", "comeback", "streak", "goodnight")
        const val ANY = "any"

        fun parse(json: String): CoachBank = CoachBank((MiniJson.read(json) as List<*>).map { e ->
            val m = e as Map<*, *>
            CoachLine(m["id"] as String, m["moment"] as String, (m["for"] as? String) ?: ANY, m["caption"] as String, (m["ms"] as Number).toLong())
        })
    }
}

/**
 * The wall between the coach and anyone but a grown-up. It asks the profile's own kind and band (Profile.isKid, true for a
 * little kid and a big kid alike, and Profile.bigKid), never the kid skin (ui/family/KidsMode.kt's UI helper: the little kid's skin only,
 * false for a big kid). A kid or a big kid never sees the setting and never hears a line, whatever is stored (a grown-up
 * made a kid in Family keeps the value, unused). Off until a grown-up turns it on behind the parent gate.
 */
object CoachGate {
    /** Prefs key (the profile's own file, Prefs.extraString): the voice chosen (its folder), absent or "off" for Off. */
    const val KEY = "coachVoice"
    /** Prefs key: how the coach talks to the player, "w" (as a woman) or "m" (as a man); asked when it is turned on. */
    const val FOR_KEY = "coachFor"
    const val OFF = "off"
    /** Prefs key: the one-time notice was read and accepted on this profile. */
    const val NOTICE_KEY = "coachNotice"

    /** The setting is offered: a grown-up's profile only. */
    fun offered(p: Profile): Boolean = p.kind == ProfileKind.ADULT && !p.isKid && !p.bigKid

    /**
     * The voice that plays for [stored] (the folder saved, or null / "off") among the build's [voices]: the saved one, or the
     * first when the saved one is not in this build; null for Off, and with no voice in the build at all.
     */
    fun voiceOf(stored: String?, voices: List<String>): String? = when {
        stored.isNullOrBlank() || stored == OFF -> null
        stored in voices -> stored
        else -> voices.firstOrNull()
    }

    /** How the coach talks to the player: "w" or "m" as saved, else null (only the lines for anyone). */
    fun forOf(stored: String?): String? = stored?.takeIf { it == "w" || it == "m" }

    /**
     * Lines play: a grown-up ([offered]) who chose a [voice] (null: Off, the default), with Encouragement not Off ([frequency],
     * with the Plain look already folded in by PlainLook.cheerFrequency) and the Plain look off ([plain]).
     */
    fun on(p: Profile, voice: String?, frequency: CheerFrequency, plain: Boolean): Boolean =
        offered(p) && voice != null && frequency != CheerFrequency.OFF && !plain

    /**
     * The course is narrated in the coach's voice (the spoken guidance, not the cheers, so Encouragement does not matter): a
     * grown-up ([offered]) with a [voice] chosen and the Plain look off.
     */
    fun narrates(p: Profile, voice: String?, plain: Boolean): Boolean = offered(p) && voice != null && !plain

    /** The narration's file for [key] in a voice's course folder ([files]): the player's own variant first, then the one for anyone. */
    fun narrationOf(key: String, forWho: String?, files: Set<String>): String? =
        listOfNotNull(forWho?.let { "${key}_$it.ogg" }, "$key.ogg").firstOrNull { it in files }
}

/**
 * Which lines a moment draws from, and when a line may play. Two kinds of moment: a play moment (the cheer pops' triggers, a
 * wrong key, a hint) keeps Encouragement's wait between lines and its cap a minute ([CheerCoach.gapMs] for a grown-up,
 * [CheerCoach.perMinute]); an event (a lesson's first playing step and its finish, the stars, a step passed, the comeback card,
 * Done for today) happens once where it happens, so it has no wait, but it counts toward the next play moment's.
 */
object CoachMoments {
    /** The events: no wait before them. */
    val EVENTS = setOf(Moment.START, Moment.STEP_PASSED, Moment.FINISH, Moment.STARS3, Moment.STARS2, Moment.COMEBACK, Moment.STREAK, Moment.GOODNIGHT)

    /** The focus minutes that have their own lines; any other (25, 35, ...) keeps the cheer pop's words. */
    val FOCUS_MINUTES = setOf(5, 10, 15, 20, 30)

    /**
     * The bank for [m]: a run of 20 or 40 always says its number; 5 and 10 roll ([numbered]) between their number and the plain
     * run lines, as the cheer pops do (CheerCoach.pool), so the plain ones are heard too; a run with another count is plain. A
     * focus pop names its minutes (null when none are recorded for them).
     */
    fun bankOf(m: Moment, count: Int?, numbered: Boolean): String? = when (m) {
        Moment.RUN -> when (count) {
            20, 40 -> "run$count"
            5, 10 -> if (numbered) "run$count" else "run"
            else -> "run"
        }
        Moment.FOCUS -> count?.takeIf { it in FOCUS_MINUTES }?.let { "focus$it" }
        else -> m.name.lowercase()
    }
}

/**
 * The coach's choices, deterministic for a seed: which line, if any, a moment gets now. Off with Encouragement Off; a play
 * moment waits [CheerCoach.gapMs] (15 s, 30 s with Less) after the last line and keeps to [CheerCoach.perMinute] (four, two
 * with Less) in any minute; a wrong key or a failed try ([Moment.MISTAKE], the sparing one) at most once a step, a hint or a
 * second failed try ([Moment.HARD]) once a step; never a line among the last [RECENT] played while its bank has another (a bank
 * of two plays them in turn).
 */
class CoachBrain(seed: Long = System.nanoTime()) {
    private val random = java.util.Random(seed)
    private val recent = ArrayDeque<String>()
    private val times = ArrayDeque<Long>()
    private var lastMs: Long? = null
    private var mistakeThisStep = false
    private var hardThisStep = false

    /** The ids played, newest last (tests). */
    val played: List<String> get() = recent.toList()

    /** A step, a try or a run began: a wrong key and a hint may have their line again. */
    fun stepBegan() { mistakeThisStep = false; hardThisStep = false }

    /** Whether [m] may have a line at [nowMs] (the waits and caps), before a line is picked. */
    fun allowed(m: Moment, nowMs: Long, frequency: CheerFrequency): Boolean {
        if (frequency == CheerFrequency.OFF) return false
        if (m == Moment.MISTAKE && mistakeThisStep) return false
        if (m == Moment.HARD && hardThisStep) return false
        if (m in CoachMoments.EVENTS) return true
        while (times.isNotEmpty() && nowMs - times.first() >= 60_000) times.removeFirst()
        if (lastMs?.let { nowMs - it < CheerCoach.gapMs(kid = false, frequency) } == true) return false
        return times.size < CheerCoach.perMinute(frequency)
    }

    /** The line for [m] now for a player who is [forWho] (null: none allowed, or none recorded for it), recorded as played. */
    fun pick(m: Moment, count: Int?, nowMs: Long, frequency: CheerFrequency, bank: CoachBank, forWho: String? = null): CoachLine? {
        if (!allowed(m, nowMs, frequency)) return null
        val pool = CoachMoments.bankOf(m, count, numbered = random.nextBoolean())?.let { bank.of(it, forWho) }.orEmpty()
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.id !in recent }.ifEmpty { pool.filter { it.id != recent.lastOrNull() }.ifEmpty { pool } }
        val line = fresh[random.nextInt(fresh.size)]
        recent.addLast(line.id); while (recent.size > RECENT) recent.removeFirst()
        times.addLast(nowMs); lastMs = nowMs
        if (m == Moment.MISTAKE) mistakeThisStep = true
        if (m == Moment.HARD) hardThisStep = true
        return line
    }

    companion object {
        const val RECENT = 6
    }
}

/** How a line plays round the microphone and the other sounds. */
object CoachSound {
    /**
     * The step pop chime's mic rule (StepPopRules.chimeMode: PLAY_DEAF, MicInput.deafFor) for the line's whole length and the
     * room's ring after it (StepPopRules.ROOM_TAIL_MS), whenever the phone's speaker plays it: with the mic listening, and with it
     * off too, since a step's mic can start while the line still plays (the deaf window outlives a start). Headphones on: 0.
     */
    fun deafMs(lineMs: Long, headphones: Boolean): Long = if (headphones) 0L else lineMs + StepPopRules.ROOM_TAIL_MS

    /** Whether a line makes the mic deaf at all: the phone's speaker plays it (no headphones). */
    fun deafens(headphones: Boolean): Boolean = !headphones

    /**
     * The deaf window is laid down a [SLICE_MS] slice at a time, renewed every [RENEW_MS] while the line plays, then
     * StepPopRules.ROOM_TAIL_MS after its end: [deafMs] in all, and a line cut short (Continue, a screen left) leaves the mic
     * hearing again within a slice instead of for the rest of the line.
     */
    const val SLICE_MS = 500L
    const val RENEW_MS = 250L

    /** How long a line with no bubble of its own may wait for the voice, the chime or another line, before it is dropped. */
    const val WAIT_MS = 8_000L
    /** A line whose words a screen's bubble already shows must start almost at once: it waits this long at most past its delay. */
    const val SHOWN_WAIT_MS = 1_200L
    /** How long its words stay up: the line's length, at least the cheer pop's 1.6 s, at most [MAX_SHOW_MS]. */
    const val MAX_SHOW_MS = 6_500L
    fun showMs(lineMs: Long): Long = lineMs.coerceIn(CheerCoach.HOLD_MS, MAX_SHOW_MS)
}
