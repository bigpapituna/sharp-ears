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

    /**
     * 2.17 (audit M4): the third answer, "Just use my name": only the lines for anyone, and the course's lines with a woman's
     * and a man's take in the normal voice (as unset). Stored as [NAME].
     */
    const val NAME = "name"

    /** How the coach talks to the player: "w" or "m" as saved, else null (only the lines for anyone: unset, or [NAME]). */
    fun forOf(stored: String?): String? = stored?.takeIf { it == "w" || it == "m" }

    /** The question "How should your coach talk to you?" was answered: as a woman, as a man, or just by name. */
    fun answered(stored: String?): Boolean = stored == "w" || stored == "m" || stored == NAME

    /** The tester's `coach_for`: "w", "m", "name", or "unset". */
    fun forProperty(stored: String?): String = stored?.takeIf { answered(it) } ?: "unset"

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
 * second failed try ([Moment.HARD]) once a step. QA praise pass: each bank (and the player's variant) is a shuffle bag
 * ([com.bigpapituna.sharpears.engine.praise.PraiseBags]): every line of it before any repeats, never the same twice in a row,
 * and the history kept across lessons and app restarts (the host hands the app's own bags; a test gets its own).
 */
class CoachBrain(seed: Long = System.nanoTime(), bags: com.bigpapituna.sharpears.engine.praise.PraiseBags? = null) {
    private val random = java.util.Random(seed)
    private val bags = bags ?: com.bigpapituna.sharpears.engine.praise.PraiseBags(random)
    private val recent = ArrayDeque<String>()
    private val times = ArrayDeque<Long>()
    private var lastMs: Long? = null
    private var mistakeThisStep = false
    private var hardThisStep = false

    /** The last [RECENT] ids played, newest last (tests). */
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
        val name = CoachMoments.bankOf(m, count, numbered = random.nextBoolean())
        val pool = name?.let { bank.of(it, forWho) }.orEmpty()
        if (pool.isEmpty()) return null
        val id = bags.next("coach.$name.${forWho ?: "-"}", pool.map { it.id })
        val line = pool.first { it.id == id }
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
     * The step pop chime's mic rule (StepPopRules.chimeMode: PLAY_DEAF, Stage.deafFor) for the line's whole length and the
     * room's ring after it (StepPopRules.ROOM_TAIL_MS), whenever the phone's speaker plays it: with the mic listening, and with it
     * off too, since a step's mic can start while the line still plays (the deaf window outlives a start). Headphones on: 0.
     */
    fun deafMs(lineMs: Long, headphones: Boolean): Long = if (headphones) 0L else lineMs + StepPopRules.ROOM_TAIL_MS

    /**
     * Whether a line makes the mic deaf at all: the phone's speaker plays it (no headphones). 2.18: the Stage's rule
     * (engine/stage/StageRule.deafens); the window itself, "Listening again" after it and the duck are the Stage's
     * (audio/Stage.lineStarts, from the line's start until exactly its end plus StepPopRules.ROOM_TAIL_MS).
     */
    fun deafens(headphones: Boolean): Boolean = com.bigpapituna.sharpears.engine.stage.StageRule.deafens(headphones)

    /**
     * 2.18.3 (Roy: "some keys are not being played when it's asking me to play the keys"): a line may start only when the mic
     * is not listening through the phone's speaker (StageRule.quiet: the player's turn, no headphones). Before, a line in the
     * middle of a try (a mistake, a recovery, a run, a nudge, the hint's "hard") made the mic deaf for its whole length, 3 to
     * 6 s, and the keys played meanwhile were dropped (emulator: 7 injected notes lost to a mistake line plus the octave hint).
     * The pup's cheers already skip the player's turn (CheerRules.micRule); the coach's lines are longer than any rest.
     */
    fun mayStart(micListening: Boolean, headphones: Boolean): Boolean =
        !com.bigpapituna.sharpears.engine.stage.StageRule.quiet(micListening, headphones)

    /**
     * How long a line asked for may wait before it is dropped: one asked while the mic listens on the speaker ([inTurn]) waits
     * [SHOWN_WAIT_MS] (the end of a try: the mic stops within a frame or two and the line plays; the middle of a run: the mic
     * keeps listening, the moment has passed and the line goes); else [WAIT_MS] for an overlay line, [SHOWN_WAIT_MS] for a
     * bubble's.
     */
    fun waitMs(overlay: Boolean, inTurn: Boolean): Long = if (overlay && !inTurn) WAIT_MS else SHOWN_WAIT_MS

    /** How long a line with no bubble of its own may wait for the voice, the chime or another line, before it is dropped. */
    const val WAIT_MS = 8_000L
    /** A line whose words a screen's bubble already shows must start almost at once: it waits this long at most past its delay. */
    const val SHOWN_WAIT_MS = 1_200L
    /** How long its words stay up: the line's length, at least the cheer pop's 1.6 s, at most [MAX_SHOW_MS]. */
    const val MAX_SHOW_MS = 6_500L
    fun showMs(lineMs: Long): Long = lineMs.coerceIn(CheerCoach.HOLD_MS, MAX_SHOW_MS)
}

/**
 * 2.19.0: a coach reaction asked in the middle of a try (a mistake, a recovery, a run, a nudge, a hint) while the mic listens
 * through the phone's speaker. 2.18.3 dropped it, since a line there makes the mic deaf for 3 to 6 s and eats the player's
 * keys; but the coach reacting to what was just played is the point. So the most relevant one is held (one at most) and said
 * at the next natural pause: the player quiet for [quietMs] (a stall in wait mode, a stop), or in place of the line the step's
 * end would get. One older than [staleMs] is dropped; a newer reaction of the same rank or higher replaces the held one ([rank]:
 * a hint over a mistake or a recovery, those over a run or a halfway, everything over a nudge). Pure; the host polls it.
 */
class CoachHold<T : Any>(private val staleMs: Long = STALE_MS, private val quietMs: Long = QUIET_MS) {
    enum class Next { NONE, WAIT, SPEAK, STALE }

    private class Held<T>(val item: T, val rank: Int, val atMs: Long)
    private var held: Held<T>? = null

    /** The line held now, fresh or not (logs). */
    val item: T? get() = held?.item

    /** How long the held line has waited at [nowMs]. */
    fun ageMs(nowMs: Long): Long = held?.let { nowMs - it.atMs } ?: 0L

    /** Holds [item] of [rank] asked at [nowMs]: returns false (not held) when a fresher line of a higher rank is held. */
    fun offer(item: T, rank: Int, nowMs: Long): Boolean {
        val h = held
        if (h != null && nowMs - h.atMs <= staleMs && rank < h.rank) return false
        held = Held(item, rank, nowMs)
        return true
    }

    /**
     * At [nowMs], with the player's last key at [lastKeyMs]: [Next.SPEAK] (and the line, now let go) when the player has been
     * quiet for [quietMs] since the later of the key and the line; [Next.STALE] (dropped) past [staleMs]; else WAIT, or NONE.
     */
    fun next(nowMs: Long, lastKeyMs: Long): Pair<Next, T?> {
        val h = held ?: return Next.NONE to null
        if (nowMs - h.atMs > staleMs) { held = null; return Next.STALE to h.item }
        if (nowMs - maxOf(lastKeyMs, h.atMs) >= quietMs) { held = null; return Next.SPEAK to h.item }
        return Next.WAIT to null
    }

    /** A moment of [rank] gets a line now (the step's end, a failed try): the held line instead, when fresh and at least as relevant. */
    fun claim(rank: Int, nowMs: Long): T? {
        val h = held ?: return null
        held = null
        return h.item.takeIf { nowMs - h.atMs <= staleMs && h.rank >= rank }
    }

    fun clear() { held = null }

    companion object {
        const val STALE_MS = 8_000L
        const val QUIET_MS = 1_500L

        /** How much a moment says about what was just played. */
        fun rank(m: Moment): Int = when (m) {
            Moment.HARD -> 3
            Moment.MISTAKE, Moment.RECOVERY -> 2
            Moment.NUDGE, Moment.STEP_PASSED, Moment.START -> 0
            else -> 1
        }
    }
}
