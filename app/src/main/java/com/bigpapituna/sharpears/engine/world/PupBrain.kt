package com.bigpapituna.sharpears.engine.world

/**
 * A moment the pup reacts to, shown for [ms] and then he goes back to what he was doing. Never sad: a wrong key gets
 * a puzzled head tilt, nothing more.
 */
enum class PupReaction(val ms: Long) {
    /** A rare or shiny sticker dropped. */
    SURPRISED(1800),
    /** An ear game asks something. */
    THINKING(2200),
    /** A listening step starts. */
    SHH(2200),
    /** A hint is on its way. */
    PEEK(2000),
    /** A wrong key (kids: instead of any red). */
    HEAD_TILT(1300),
    /** The treat jar was tipped: he eats. */
    TREAT(3000),
    CLAP(1800),
    DANCE(2600),
    FLY(1500),
    /** Crown of the day. */
    CROWN(3200),
    /** The session goodbye: a yawn and a wave, then he sleeps until the next touch or note. */
    GOODNIGHT(3200),
}

/** A held state: the pup stays in it while it is set (an ear game is [THINKING], a listening step is [SHH]). */
enum class PupMood { NONE, THINKING, SHH, LISTENING, PEEK, SLEEPING }

/** The things he does on his own when nobody plays or touches anything for a while. */
enum class IdleAct { SNEEZE, TAIL_CHASE, BUTTERFLY, SCRATCH }

/** What the pup shows right now, most important first: a reaction, a mood, asleep, an idle act, or the screen's pose. */
sealed interface PupLook {
    data class Reacting(val reaction: PupReaction) : PupLook
    data class Mood(val mood: PupMood) : PupLook
    data object Asleep : PupLook
    data class Idle(val act: IdleAct) : PupLook
    data object Base : PupLook
}

/**
 * The living pup's state machine, pure (times are milliseconds on any clock that only goes forward):
 *
 * - [IDLE_FIRST_MS] after the last input he does an idle act ([IdleAct]) for [ACT_MS]; another every [IDLE_EVERY_MS]
 *   after that, never the same one twice in a row; at [SLEEP_MS] he falls asleep. The next input wakes him.
 * - A [react]ion shows for its time and counts as input (something happened). GOODNIGHT ends in sleep.
 * - A [mood] holds: no idle acts and no sleep while one is set (he keeps thinking while you think).
 *
 * The acts are picked from [seed] and the time of the last input, so the same quiet stretch always plays the same
 * acts (tests), and two pups on one screen do not do the same thing.
 */
class PupBrain(private val seed: Long, now: Long, /** False: no idle acts and no sleep (a pup that must keep his pose). */ val idle: Boolean = true) {
    var lastInput: Long = now
        private set
    private var reaction: PupReaction? = null
    private var reactionUntil = 0L
    private var goodnight = false
    var mood: PupMood = PupMood.NONE

    /** Someone touched the screen or played a note: wakes him and restarts the idle clock. */
    fun input(now: Long) {
        lastInput = now
        goodnight = false
    }

    fun react(r: PupReaction, now: Long, ms: Long = r.ms) {
        reaction = r
        reactionUntil = now + ms
        input(now)
        goodnight = r == PupReaction.GOODNIGHT
    }

    fun look(now: Long): PupLook {
        reaction?.let { if (now < reactionUntil) return PupLook.Reacting(it) }
        if (mood != PupMood.NONE) return if (mood == PupMood.SLEEPING) PupLook.Asleep else PupLook.Mood(mood)
        if (goodnight) return PupLook.Asleep
        if (!idle) return PupLook.Base
        val t = now - lastInput
        if (t >= SLEEP_MS) return PupLook.Asleep
        val k = actIndex(t) ?: return PupLook.Base
        return PupLook.Idle(act(k))
    }

    /** The next time [look] can change by itself (a reaction ending, an act starting or ending, sleep); null: never. */
    fun nextChange(now: Long): Long? {
        val times = mutableListOf<Long>()
        if (reaction != null && now < reactionUntil) times += reactionUntil
        if (mood == PupMood.NONE && !goodnight && idle) {
            for (k in 0 until ACTS) { val s = lastInput + IDLE_FIRST_MS + k * IDLE_EVERY_MS; times += s; times += s + ACT_MS }
            times += lastInput + SLEEP_MS
        }
        return times.filter { it > now }.minOrNull()
    }

    /** The act number playing [t] ms after the last input, or null between acts. */
    private fun actIndex(t: Long): Int? {
        if (t < IDLE_FIRST_MS) return null
        val k = ((t - IDLE_FIRST_MS) / IDLE_EVERY_MS).toInt()
        if (k >= ACTS) return null
        return if (t - IDLE_FIRST_MS - k * IDLE_EVERY_MS < ACT_MS) k else null
    }

    /** The [k]th act of this quiet stretch: the first is random, each next one differs from the one before. */
    fun act(k: Int): IdleAct {
        val acts = IdleAct.entries
        val rng = Rng.of(seed, lastInput)
        var i = rng.nextInt(acts.size)
        repeat(k) { i = (i + 1 + rng.nextInt(acts.size - 1)) % acts.size }
        return acts[i]
    }

    companion object {
        const val IDLE_FIRST_MS = 10_000L
        const val IDLE_EVERY_MS = 15_000L
        const val ACT_MS = 2_800L
        const val SLEEP_MS = 60_000L
        /** Acts before he sleeps: at 10, 25, 40 and 55 s. */
        val ACTS = ((SLEEP_MS - IDLE_FIRST_MS - ACT_MS) / IDLE_EVERY_MS + 1).toInt()
    }
}

/**
 * How far the pup jumps for a note: higher notes jump higher, louder ones a little more. As a share of his height;
 * the Jam's big bounce is [BIG_SCALE] times the small one on ordinary screens.
 */
object Bounce {
    const val LOW = 36          // C2
    const val HIGH = 96         // C7
    const val BIG_SCALE = 3.2f

    /** 0 for the lowest key, 1 for the highest. */
    fun pitch(midi: Int): Float = ((midi - LOW).toFloat() / (HIGH - LOW)).coerceIn(0f, 1f)

    /** 0 for a soft strike (a 6 dB rise), 1 for a loud one (30 dB or more). */
    fun loudness(riseDb: Double): Float = ((riseDb - 6.0) / 24.0).toFloat().coerceIn(0f, 1f)

    /** Jump height as a share of the pup's size. */
    fun height(midi: Int, riseDb: Double, big: Boolean): Float {
        val small = (0.03f + 0.09f * pitch(midi)) * (0.75f + 0.5f * loudness(riseDb))
        return if (big) (small * BIG_SCALE).coerceAtMost(0.55f) else small
    }

    /** Squash on landing: low notes land heavy (more squash), high ones light. */
    fun squash(midi: Int, big: Boolean): Float = (0.10f - 0.06f * pitch(midi)) * (if (big) 1.6f else 1f)
}
