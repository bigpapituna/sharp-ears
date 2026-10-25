package com.bigpapituna.sharpears.engine.lessons

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.pup.PupMood

/**
 * How the step pop's chime may sound now (the mic rule). [PLAY]: through the speaker as it is (the mic is off, or
 * headphones are on, so the mic cannot hear it). [PLAY_DEAF]: the mic is listening with no headphones: it is made deaf for
 * the chime and the room's ring after it ([StepPopRules.DEAF_MS], MicInput.deafFor) before the first note, so the chime is
 * never taken for the learner's note. [SKIP]: App sound is off (the burst still shows).
 */
enum class ChimeMode { PLAY, PLAY_DEAF, SKIP }

/**
 * What the end of a step's try means for the pop. [POP]: passed cleanly and another step follows (the burst, the chime,
 * the pup's mini hop). [LAST]: passed, and the lesson's Celebrate takes over (no pop; it still counts for the streak).
 * [LET_GO]: let go after three tries, or a stage or run that was mostly let go (no pop, never a sad sound; the climb
 * starts again). [TRY_AGAIN]: not passed yet (nothing changes). [NONE]: a listening step (nothing to judge).
 */
enum class StepOutcome { POP, LAST, LET_GO, TRY_AGAIN, NONE }

/**
 * The step pop's four pictures (Roy: "3 or 4 different step-pop variants to keep it fresh"), each about 600 ms
 * (StepPopMotion): [BURST] the check with a ring and flying stars, [CONFETTI] paper bits popping up from the step's bottom
 * edge round the check, [RAINBOW] an arc drawn across the step with the check popping at its end, [PAW] a paw print
 * stamping in with the check inside it. The chime is the same family for all four (the sound is the steady hook while the
 * picture varies). [big]: the larger ones, for longer streaks.
 */
enum class PopVariant(val big: Boolean) { BURST(false), CONFETTI(true), RAINBOW(true), PAW(false) }

/** One note of the chime: its key, how hard (MIDI velocity) and when after the pop starts (ms). */
data class ChimeNote(val midi: Int, val velocity: Int, val atMs: Int)

/**
 * The run of clean steps in one lesson (2.11): one more for every step passed cleanly, back to none on a let-go or a
 * graded step skipped. A new lesson makes a new one. The chime climbs with it ([StepPopRules.climb]).
 */
class StepStreak {
    var length = 0
        private set

    /** The step's [outcome]: returns the streak after it. */
    fun after(outcome: StepOutcome): Int {
        when (outcome) {
            StepOutcome.POP, StepOutcome.LAST -> length++
            StepOutcome.LET_GO -> length = 0
            StepOutcome.TRY_AGAIN, StepOutcome.NONE -> {}
        }
        return length
    }

    /** A graded step skipped with Next (not a clean step): the climb starts again. */
    fun skipped() { length = 0 }
}

/**
 * The step pop (2.11, Roy: "a little animation with a fun quick audio after every lesson page completion ... similar to
 * Duolingo when you get a right answer"): when a lesson's step is passed and another follows, a green-gold check burst
 * over the step, the pup's mini hop and a short rising chime from the piano samples. Duolingo's climb: each clean step in a
 * row raises the chime one step of the lesson's major scale, up to [MAX_CLIMB] steps. Android-free; StepPopRulesTest.
 */
object StepPopRules {
    /** Notes of the chime start this far apart (an arpeggio, not a chord). */
    const val NOTE_GAP_MS = 60
    /** The chime's notes are let go this long after it starts, after a short fade ([FADE_MS]) so the cut does not click. */
    const val CHIME_MS = 400
    const val FADE_MS = 60
    /** The room rings on after the speaker stops (MicInput.VOICE_TAIL_MS for the voice). */
    const val ROOM_TAIL_MS = 300
    /** How long the mic is deaf for a chime it could hear: the chime and the room's ring after it. */
    const val DEAF_MS = CHIME_MS + ROOM_TAIL_MS
    /** The voice's line for the step waits this long when a chime plays, so the chime never talks over the pup's praise. */
    const val VOICE_AFTER_MS = CHIME_MS
    /** The climb stops after five steps up (the sixth clean step in a row and every one after sound the same). */
    const val MAX_CLIMB = 5
    /** The chime's notes get a little louder as they rise. */
    val VELOCITIES = listOf(72, 80, 88)
    /** The tonic's place: its note nearest C6 (84), so the chime sits high and bright and stays on the piano's samples. */
    const val HOME = 84

    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    /** From this streak on the big pictures ([PopVariant.big]) are preferred; up to [SMALL_UNTIL] the small ones. */
    const val BIG_FROM = 4
    const val SMALL_UNTIL = 2
    /** A preferred picture is this many times as likely as another. */
    const val PREFER_WEIGHT = 3

    /** The pictures a [streak] prefers: BURST and PAW at one or two, RAINBOW and CONFETTI from four, any of the four at three. */
    fun preferred(streak: Int): Set<PopVariant> = when {
        streak >= BIG_FROM -> PopVariant.entries.filter { it.big }.toSet()
        streak <= SMALL_UNTIL -> PopVariant.entries.filter { !it.big }.toSet()
        else -> PopVariant.entries.toSet()
    }

    /** The seed of a lesson step's roll: the same lesson and step roll the same picture (a replay is the same). */
    fun seedOf(lessonId: String, step: Int): Long = lessonId.hashCode().toLong() * 1_000_003L + step

    /**
     * The picture for a pop: rolled from [seed] ([seedOf]), never [previous] (never the same twice in a row), the ones the
     * [streak] prefers [PREFER_WEIGHT] times as likely as the rest.
     */
    fun variant(streak: Int, previous: PopVariant?, seed: Long): PopVariant {
        val pool = PopVariant.entries.filter { it != previous }
        val liked = preferred(streak)
        val weights = pool.map { if (it in liked) PREFER_WEIGHT else 1 }
        var roll = java.util.Random(StepPopMotion.mix(seed)).nextInt(weights.sum())
        for ((i, w) in weights.withIndex()) { if (roll < w) return pool[i]; roll -= w }
        return pool.last()
    }

    /** The pup's beat with each picture: his happy hop with BURST and PAW, the cheer wag with CONFETTI and RAINBOW. */
    fun pupBeat(variant: PopVariant): PupMood = when (variant) {
        PopVariant.BURST, PopVariant.PAW -> PupMood.POP
        PopVariant.CONFETTI, PopVariant.RAINBOW -> PupMood.CHEER_WAG
    }

    /**
     * What a step's try came to. [listen]: a listening step; [judged]: it passed on its own terms; [letGo]: let go after
     * three tries (StepRetry); [mostlyLetGo]: it passed only because its rounds or notes were let go ([mostlyLetGo]);
     * [last]: the lesson's final step.
     */
    fun outcome(listen: Boolean, judged: Boolean, letGo: Boolean, last: Boolean, mostlyLetGo: Boolean = false): StepOutcome = when {
        listen -> StepOutcome.NONE
        letGo || (judged && mostlyLetGo) -> StepOutcome.LET_GO
        !judged -> StepOutcome.TRY_AGAIN
        last -> StepOutcome.LAST
        else -> StepOutcome.POP
    }

    /**
     * Half or more of a run let go ([letGone] of [of]): a stage's rounds (Report.letGoRounds of rounds) or the player's
     * groups (RunTally.letGos of Report.groups). The same line as a kid's stars (LessonStars: one star there), so a kid
     * who played nothing gets no pop and no climb, only the soft let-go.
     */
    fun mostlyLetGo(letGone: Int, of: Int): Boolean = letGone > 0 && of > 0 && letGone * 2 >= of

    /** Scale steps the chime is raised for a [streak] of clean steps: 0 for the first, one more each, at most [MAX_CLIMB]. */
    fun climb(streak: Int): Int = (streak - 1).coerceIn(0, MAX_CLIMB)

    /**
     * The chime for a [streak] in the key of [tonic] (a pitch class, 0 = C): the triad on the scale step [climb] above the
     * tonic, each note in the major scale (I, ii, iii, IV, V, vi), rising, [NOTE_GAP_MS] apart. In C: C6 E6 G6, then D6 F6
     * A6, ... up to A6 C7 E7.
     */
    fun chime(tonic: Int, streak: Int): List<ChimeNote> {
        val pc = Math.floorMod(tonic, 12)
        val root = HOME + if (pc <= 6) pc else pc - 12
        val step = climb(streak)
        return listOf(0, 2, 4).mapIndexed { i, third -> ChimeNote(root + degree(step + third), VELOCITIES[i], i * NOTE_GAP_MS) }
    }

    /** Semitones above the tonic of major-scale step [n] (0 the tonic, 7 the octave). */
    fun degree(n: Int): Int = 12 * Math.floorDiv(n, 7) + MAJOR[Math.floorMod(n, 7)]

    /**
     * The mic rule for the chime ([ChimeMode]). [micListening]: the mic (or the debug injector) feeds the matcher now;
     * [headphones]: MicInput.headphonesOn(); [soundOn]: Settings > App sound.
     */
    fun chimeMode(micListening: Boolean, headphones: Boolean, soundOn: Boolean): ChimeMode = when {
        !soundOn -> ChimeMode.SKIP
        micListening && !headphones -> ChimeMode.PLAY_DEAF
        else -> ChimeMode.PLAY
    }

    /**
     * The lesson's key as a pitch class (0 = C), for the chime: the lowest key sounding at the last moment of its last
     * exercise that ends on a plain note or chord (music ends on its home chord, root in the bass). Steps whose last moment is a
     * choice of keys (the Playground's "any key up high") say nothing; a lesson with no such exercise is in C.
     */
    fun tonicOf(exercises: List<Exercise>): Int {
        for (e in exercises.asReversed()) {
            val tokens = runCatching { parseTokens(e.right) + parseTokens(e.left) }.getOrNull() ?: continue
            val notes = tokens.filter { it.keys.isNotEmpty() }
            val end = notes.maxOfOrNull { it.startBeat } ?: continue
            if (notes.any { it.anyOf && kotlin.math.abs(it.startBeat - end) < 1e-6 }) continue
            // Everything still sounding at the last moment: a bass held under the tune's last note counts.
            val sounding = notes.filter { !it.anyOf && it.startBeat <= end + 1e-6 && it.startBeat + it.beats > end + 1e-6 }
            return Math.floorMod(sounding.flatMap { it.keys }.min(), 12)
        }
        return 0
    }
}
