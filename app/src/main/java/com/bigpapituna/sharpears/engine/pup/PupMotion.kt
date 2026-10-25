package com.bigpapituna.sharpears.engine.pup

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the pup is doing (2.10, the living pup). A screen holds one of the held moods ([IDLE], [CHEER], [POINT], [LISTEN],
 * [SLEEP]) and plays beats ([HAPPY], [POP], [CHEER_WAG], [TILT], [WAG]) that run once and hand back to it. Never sad: a wrong key gets a puzzled
 * tilt, nothing more. [beat] is true for the ones that play once.
 */
enum class PupMood(val beat: Boolean) {
    /** Breathing, a blink every few seconds, an ear or tail flick now and then. */
    IDLE(false),
    /** A jump for joy: squash, stretch up, hang, land with two bounces. Three stars, a sticker, a surprise, a step done. */
    HAPPY(true),
    /** 2.11: a quick little hop, under 0.6 s: a lesson step passed with another to come (the step pop). */
    POP(true),
    /** 2.11: two quick bobs swaying side to side with his tail going, about half a second: the step pop's bigger pictures. */
    CHEER_WAG(true),
    /** Bobbing and swaying on the beat, in time to a bpm: finales, Clap along, a celebration after its jump. */
    CHEER(false),
    /** A puzzled head tilt held a moment: a wrong key. */
    TILT(true),
    /** A quick wag, three wobbles: a right key, a tap. */
    WAG(true),
    /** Slides toward a key on the keyboard while a demo plays it. */
    POINT(false),
    /** A slow lean toward the microphone while the kid plays. */
    LISTEN(false),
    /** Slow, deep breathing: bedtime. */
    SLEEP(false),
}

/**
 * One moment of motion on top of the pup's pose: scales and a rotation (degrees, clockwise), [lift] off the ground and
 * [shift] sideways, both as a share of his size. The screen multiplies the scales and adds the rest.
 */
data class PupFrame(val scaleX: Float = 1f, val scaleY: Float = 1f, val rotation: Float = 0f, val lift: Float = 0f, val shift: Float = 0f) {
    companion object { val REST = PupFrame() }
}

/** A beat, a fade or a one-off move, by name and length, for the table's checks. */
data class Timing(val name: String, val ms: Int)

/**
 * Every number of the pup's motion in one place: durations (ms), scales, amplitudes, and the curves made of them, as pure
 * functions of time so the JVM tests can walk them frame by frame (PupMotionTest: every beat under 1.5 s, the idle loop
 * between 2.5 and 4 s, no scale beyond 1.25). ui/pup/Pup.kt reads them in its graphicsLayer and draw blocks only.
 */
object PupMotion {
    // ---- Idle: breathing, a blink, a flick ------------------------------------------------------------------------------
    /** One breath, in and out: the idle loop. */
    const val BREATH_MS = 3200
    /** Breathing scale: 1.0 .. 1.02 (height; the width moves half as much). */
    const val BREATH_SCALE = 0.02f
    /** A blink comes every 3 to 6 s, at random. */
    const val BLINK_MIN_MS = 3000L
    const val BLINK_MAX_MS = 6000L
    /** A pose with a closed-eyes twin cross-fades to it and back ([BLINK_FADE_MS] each way, shut for [BLINK_SHUT_MS]). */
    const val BLINK_FADE_MS = 120
    const val BLINK_SHUT_MS = 60
    /** Without a twin: a squint, the head's top [HEAD_SHARE] of the picture squashed to [SQUINT_Y] and back. */
    const val SQUINT_MS = 90
    const val SQUINT_Y = 0.92f
    const val HEAD_SHARE = 0.45f
    /** An ear or tail flick every 8 to 15 s: a corner of the picture turns [FLICK_DEG] and rings back. */
    const val FLICK_MIN_MS = 8000L
    const val FLICK_MAX_MS = 15000L
    const val FLICK_DEG = 3f
    const val FLICK_OUT_MS = 70
    const val FLICK_MS = 280
    /** How far from its corner the flick reaches, as a share of the picture's width (it fades to nothing there). */
    const val FLICK_REACH = 0.55f
    /** Drowsy (Today after half a minute alone): breathing and blinks slow by this much. */
    const val SLEEPY_SLOW = 1.4f

    // ---- A new pose ------------------------------------------------------------------------------------------------------
    /** Switching pictures: a cross-fade, never a hard swap, with a small pop. */
    const val SWAP_MS = 150
    const val SWAP_POP = 1.04f
    const val SWAP_POP_MS = 220
    /** Arriving on screen: from this scale, springing to 1. */
    const val ENTER_FROM = 0.9f
    const val ENTER_DAMPING = 0.55f
    const val ENTER_STIFFNESS = 500f

    // ---- Animations off ----------------------------------------------------------------------------------------------------
    /** With the phone's animations off: no idle motion, and a reaction is one fade of his glow. */
    const val CALM_MS = 150

    // ---- HAPPY: the jump --------------------------------------------------------------------------------------------------
    const val SQUASH_MS = 80
    const val SQUASH_X = 1.10f
    const val SQUASH_Y = 0.90f
    const val RISE_MS = 220
    const val STRETCH_X = 0.92f
    const val STRETCH_Y = 1.12f
    /** The share of the rise spent turning the squash into the stretch. */
    const val STRETCH_IN = 0.3f
    /** How high he gets, as a share of his size. */
    const val JUMP = 0.22f
    const val HANG_MS = 80
    const val FALL_MS = 180
    const val FALL_X = 0.97f
    const val FALL_Y = 1.04f
    /** The landing: a spring struck at the ground. Damped so two bounces show and a third is under a pixel. */
    const val LAND_DAMPING = 0.41f
    /** Each bounce's time in the air. */
    const val LAND_LOBE_MS = 170
    /** Bounces drawn (the last one too small to see), so the beat ends on the ground. */
    const val LAND_LOBES = 3
    /** The first bounce, as a share of the jump. */
    const val LAND_FIRST = 0.25f
    /** The squash at touchdown, ringing out with the bounces. */
    const val LAND_SQUASH = 0.08f
    /** A bounce counts as seen above this share of the jump. */
    const val SEEN = 0.03f
    const val HAPPY_MS = SQUASH_MS + RISE_MS + HANG_MS + FALL_MS + LAND_LOBE_MS * LAND_LOBES

    // ---- POP: the step pop's mini hop (2.11), a small HAPPY that is over before the burst ---------------------------------
    const val POP_SQUASH_MS = 60
    const val POP_SQUASH_X = 1.06f
    const val POP_SQUASH_Y = 0.94f
    const val POP_RISE_MS = 140
    const val POP_STRETCH_X = 0.96f
    const val POP_STRETCH_Y = 1.06f
    /** How high the hop gets, as a share of his size (HAPPY's jump is 0.22). */
    const val POP_JUMP = 0.10f
    const val POP_FALL_MS = 120
    /** The landing: a squash at touchdown ringing out over [POP_LAND_MS]. */
    const val POP_LAND_MS = 200
    const val POP_LAND_SQUASH = 0.05f
    const val POP_BEAT_MS = POP_SQUASH_MS + POP_RISE_MS + POP_FALL_MS + POP_LAND_MS

    // ---- CHEER_WAG: two bobs and a sway, the tail going (2.11) ------------------------------------------------------------
    /** Each bob (a hop off the ground and back) takes this long; two of them, swaying one way then the other. */
    const val CHEER_WAG_BOB_MS = 260
    const val CHEER_WAG_BOBS = 2
    const val CHEER_WAG_LIFT = 0.06f
    const val CHEER_WAG_DEG = 6f
    const val CHEER_WAG_SQUASH = 0.04f
    const val CHEER_WAG_MS = CHEER_WAG_BOB_MS * CHEER_WAG_BOBS

    // ---- TILT: the puzzled head tilt ---------------------------------------------------------------------------------------
    const val TILT_DEG = 12f
    const val TILT_IN_MS = 160
    const val TILT_HOLD_MS = 300
    const val TILT_OUT_MS = 240
    /** He tilts round a point low in the picture (share of his height from the top), so the head swings and the feet stay. */
    const val TILT_PIVOT_Y = 0.8f
    const val TILT_MS = TILT_IN_MS + TILT_HOLD_MS + TILT_OUT_MS

    // ---- WAG: three quick wobbles ------------------------------------------------------------------------------------------
    const val WAG_DEG = 4f
    const val WAG_CYCLES = 3
    const val WAG_CYCLE_MS = 150
    const val WAG_MS = WAG_CYCLES * WAG_CYCLE_MS

    // ---- CHEER: bob and sway on the beat -----------------------------------------------------------------------------------
    /** The beat when a screen has no music of its own (a celebration, the finale). */
    const val CHEER_BPM = 112
    /** Beats outside this range are halved or doubled: a hop per beat stays a hop, never a shiver or a crawl. */
    const val CHEER_MIN_BPM = 60
    const val CHEER_MAX_BPM = 150
    const val CHEER_BOB = 0.05f
    const val CHEER_TILT_DEG = 5f
    const val CHEER_SQUASH = 0.04f
    /** Coming into or out of a held mood (the bob, the slow breath) takes this long. */
    const val HELD_MS = 300

    // ---- POINT, LISTEN, SLEEP ----------------------------------------------------------------------------------------------
    /** The furthest he slides toward a key, as a share of his size. */
    const val POINT_MAX = 0.6f
    const val POINT_LEAN_DEG = 3f
    const val POINT_MS = 450
    const val LISTEN_DEG = 4f
    const val LISTEN_MS = 700
    const val SLEEP_BREATH_MS = 4400
    const val SLEEP_SCALE = 0.03f

    // ---- Juice: the hop on every note (engine/world/Bounce gives the height) ------------------------------------------------
    const val JUICE_UP_MS = 90
    const val JUICE_DOWN_MS = 160
    const val JUICE_BIG_UP_MS = 120
    const val JUICE_BIG_DOWN_MS = 220
    /** The landing squash after a note's hop rings out on this spring. */
    const val JUICE_LAND_DAMPING = 0.4f
    const val JUICE_LAND_STIFFNESS = 700f
    /** "I heard you" on a stage: a little dip (and an ear flick) for every sound, springing back. */
    const val HEARD_DIP = 0.06f
    const val HEARD_DAMPING = 0.45f
    const val HEARD_STIFFNESS = 500f
    /** Drowsy (Today): waking takes [WAKE_MS], dozing off [DROWSE_MS]. */
    const val WAKE_MS = 400
    const val DROWSE_MS = 2500
    /** A surprise waiting on the kid home: he waves at it (the wave clip, 3.7 s), then again this long after. */
    const val SURPRISE_WAVE_EVERY_MS = 9_000L
    /** A stage's or a lesson's pup slides at most this share of his reach toward a key (he never wanders over his words). */
    const val NEAR_POINT = 0.5f

    // ---- The layered poses (nine stills cut into body, head, ears, tail and eyes) --------------------------------------------
    /** The blink with an eyes layer: the eyes squeezed to [EYES_SHUT] of their height about their middle, and open again. */
    const val EYES_SHUT = 0.1f
    const val BLINK_CLOSE_MS = 60
    const val BLINK_HOLD_MS = 30
    const val BLINK_OPEN_MS = 60
    const val BLINK_EYES_MS = BLINK_CLOSE_MS + BLINK_HOLD_MS + BLINK_OPEN_MS
    /** The flick with ear and tail layers: one ear (or the tail) turns round its own base, on the flick's curve. */
    const val EAR_FLICK_DEG = 10f
    const val TAIL_FLICK_DEG = 12f
    /** The wag with a tail layer: the tail swings, the body wobbles half as much as without one. */
    const val TAIL_WAG_DEG = 16f
    const val WAG_BODY_SHARE = 0.5f
    /** No part turns further than this (the cut's fills under each part reach about this far). */
    const val PART_MAX_DEG = 20f

    // ---- Limits ------------------------------------------------------------------------------------------------------------
    /** Nothing ever scales him beyond these, whatever plays at once. */
    const val MAX_SCALE = 1.25f
    const val MIN_SCALE = 0.75f
    /** Smaller than this (dp) he is a picture: no breathing, blinking or flicking anyone could see. */
    const val MIN_LIVE_DP = 40

    /** Every one-off move with its length: each one is over in under 1.5 s. */
    val TIMINGS: List<Timing> = listOf(
        Timing("happy", HAPPY_MS), Timing("pop", POP_BEAT_MS), Timing("cheer wag", CHEER_WAG_MS), Timing("tilt", TILT_MS), Timing("wag", WAG_MS),
        Timing("blink", BLINK_FADE_MS * 2 + BLINK_SHUT_MS), Timing("squint", SQUINT_MS), Timing("flick", FLICK_MS),
        Timing("swap fade", SWAP_MS), Timing("swap pop", SWAP_POP_MS), Timing("calm", CALM_MS),
        Timing("held", HELD_MS), Timing("point", POINT_MS), Timing("listen", LISTEN_MS),
        Timing("juice", JUICE_UP_MS + JUICE_DOWN_MS), Timing("big juice", JUICE_BIG_UP_MS + JUICE_BIG_DOWN_MS),
        Timing("blink eyes", BLINK_EYES_MS),
    )

    /** Every scale the table names (the curves stay within them). */
    val SCALES: List<Float> = listOf(1f + BREATH_SCALE, SQUINT_Y, SWAP_POP, ENTER_FROM, SQUASH_X, SQUASH_Y, STRETCH_X, STRETCH_Y,
        FALL_X, FALL_Y, 1f + LAND_SQUASH, 1f - LAND_SQUASH, 1f + CHEER_SQUASH, 1f - CHEER_SQUASH, 1f + SLEEP_SCALE, 1f - HEARD_DIP,
        POP_SQUASH_X, POP_SQUASH_Y, POP_STRETCH_X, POP_STRETCH_Y, 1f + POP_LAND_SQUASH * 0.75f, 1f - POP_LAND_SQUASH,
        1f + CHEER_WAG_SQUASH * 0.6f, 1f - CHEER_WAG_SQUASH)

    /** A beat's length (0 for a held mood). */
    fun beatMs(mood: PupMood): Int = when (mood) {
        PupMood.HAPPY -> HAPPY_MS
        PupMood.POP -> POP_BEAT_MS
        PupMood.CHEER_WAG -> CHEER_WAG_MS
        PupMood.TILT -> TILT_MS
        PupMood.WAG -> WAG_MS
        else -> 0
    }

    /** Which beat wins when two come close: a jump is not cut short by a wag, a wag gives way to anything (the pop's hop
     *  cuts the last right key's wag short, and gives way to a jump). */
    fun priority(mood: PupMood): Int = when (mood) { PupMood.HAPPY -> 4; PupMood.POP, PupMood.CHEER_WAG -> 3; PupMood.TILT -> 2; PupMood.WAG -> 1; else -> 0 }

    /** A beat at [t] ms; [side] 1 or -1 turns a tilt the other way. Rest outside its time. */
    fun beat(mood: PupMood, t: Float, side: Float = 1f): PupFrame = when (mood) {
        PupMood.HAPPY -> happy(t)
        PupMood.POP -> pop(t)
        PupMood.CHEER_WAG -> cheerWag(t)
        PupMood.TILT -> tilt(t, side)
        PupMood.WAG -> wag(t)
        else -> PupFrame.REST
    }

    // ---- The curves ---------------------------------------------------------------------------------------------------------

    /** Breath at [phase] (cycles, any real number): 0 .. [amp] and back, a sine. */
    fun breath(phase: Double, amp: Float = BREATH_SCALE): Float = (amp * (0.5 - 0.5 * cos(2 * PI * phase))).toFloat()

    // The landing spring: a spring at rest struck by the fall. Its displacement e^(-σt) sin(ωt) bounces off the ground, so
    // the lift is its size; ω makes each bounce LAND_LOBE_MS long, σ follows from the damping ratio.
    private val omega = PI / (LAND_LOBE_MS / 1000.0)
    private val sigma = LAND_DAMPING / sqrt(1.0 - LAND_DAMPING * LAND_DAMPING) * omega
    private val firstPeak: Double = run { val t = atan(omega / sigma) / omega; exp(-sigma * t) * sin(omega * t) }

    /** The landing's lift [t] ms after touchdown, as a share of the jump (the first bounce is [LAND_FIRST]). */
    fun landLift(t: Float): Float {
        if (t <= 0f || t >= LAND_LOBE_MS * LAND_LOBES) return 0f
        val s = t / 1000.0
        return (LAND_FIRST * exp(-sigma * s) * abs(sin(omega * s)) / firstPeak).toFloat()
    }

    /** Bounce [n] (1, 2, ...) as a share of the jump: each is the one before times e^(-σπ/ω). */
    fun bounce(n: Int): Float = (LAND_FIRST * exp(-sigma * (n - 1) * PI / omega)).toFloat()

    /** The jump at [t] ms (0 .. [HAPPY_MS]). */
    fun happy(t: Float): PupFrame {
        var u = t
        if (u <= 0f || u >= HAPPY_MS) return PupFrame.REST
        if (u < SQUASH_MS) {
            val p = easeOut(u / SQUASH_MS)
            return PupFrame(lerp(1f, SQUASH_X, p), lerp(1f, SQUASH_Y, p))
        }
        u -= SQUASH_MS
        if (u < RISE_MS) {
            val p = u / RISE_MS
            val lift = JUMP * (1f - (1f - p) * (1f - p))                    // fast off the ground, slowing to the top
            return if (p < STRETCH_IN) {
                val q = easeOut(p / STRETCH_IN)
                PupFrame(lerp(SQUASH_X, STRETCH_X, q), lerp(SQUASH_Y, STRETCH_Y, q), lift = lift)
            } else {
                val q = easeInOut((p - STRETCH_IN) / (1f - STRETCH_IN))
                PupFrame(lerp(STRETCH_X, 1f, q), lerp(STRETCH_Y, 1f, q), lift = lift)
            }
        }
        u -= RISE_MS
        if (u < HANG_MS) return PupFrame(lift = JUMP)
        u -= HANG_MS
        if (u < FALL_MS) {
            val p = u / FALL_MS
            val k = p * p                                                      // gathering speed
            return PupFrame(lerp(1f, FALL_X, k), lerp(1f, FALL_Y, k), lift = JUMP * (1f - k))
        }
        u -= FALL_MS
        val s = u / 1000.0
        val ring = (exp(-sigma * s) * cos(omega * s) * cos(omega * s)).toFloat()   // a squash at each touchdown
        return PupFrame(1f + LAND_SQUASH * 0.75f * ring, 1f - LAND_SQUASH * ring, lift = JUMP * landLift(u))
    }

    /** The step pop's mini hop at [t] ms (0 .. [POP_BEAT_MS]): a squash, a small stretch up, down, a squash ringing out. */
    fun pop(t: Float): PupFrame {
        var u = t
        if (u <= 0f || u >= POP_BEAT_MS) return PupFrame.REST
        if (u < POP_SQUASH_MS) {
            val p = easeOut(u / POP_SQUASH_MS)
            return PupFrame(lerp(1f, POP_SQUASH_X, p), lerp(1f, POP_SQUASH_Y, p))
        }
        u -= POP_SQUASH_MS
        if (u < POP_RISE_MS) {
            val p = u / POP_RISE_MS
            val lift = POP_JUMP * (1f - (1f - p) * (1f - p))
            return if (p < STRETCH_IN) {
                val q = easeOut(p / STRETCH_IN)
                PupFrame(lerp(POP_SQUASH_X, POP_STRETCH_X, q), lerp(POP_SQUASH_Y, POP_STRETCH_Y, q), lift = lift)
            } else {
                val q = easeInOut((p - STRETCH_IN) / (1f - STRETCH_IN))
                PupFrame(lerp(POP_STRETCH_X, 1f, q), lerp(POP_STRETCH_Y, 1f, q), lift = lift)
            }
        }
        u -= POP_RISE_MS
        if (u < POP_FALL_MS) {
            val k = (u / POP_FALL_MS).let { it * it }
            return PupFrame(lift = POP_JUMP * (1f - k))
        }
        u -= POP_FALL_MS
        val s = u / POP_LAND_MS
        val ring = (1f - s) * (1f - s) * cos(PI * 2.5 * s).toFloat()
        return PupFrame(1f + POP_LAND_SQUASH * 0.75f * ring, 1f - POP_LAND_SQUASH * ring)
    }

    /**
     * The cheer wag at [t] ms (0 .. [CHEER_WAG_MS]): a bob off the ground and back per [CHEER_WAG_BOB_MS], swaying one way on
     * the first and the other on the second, a little squash between, all of it easing in and out so it starts and ends at rest.
     */
    fun cheerWag(t: Float): PupFrame {
        if (t <= 0f || t >= CHEER_WAG_MS) return PupFrame.REST
        val b = t / CHEER_WAG_BOB_MS                                       // 0 .. 2 bobs
        val hop = abs(sin(PI * b)).toFloat()
        val env = sin(PI * t / CHEER_WAG_MS).toFloat()
        val land = (1f - hop).let { it * it * it * it } * env
        return PupFrame(1f + CHEER_WAG_SQUASH * 0.6f * land, 1f - CHEER_WAG_SQUASH * land,
            rotation = CHEER_WAG_DEG * sin(PI * b).toFloat() * (1f - 0.3f * t / CHEER_WAG_MS), lift = CHEER_WAG_LIFT * hop)
    }

    /** The head tilt at [t] ms: over, held, back. [side] -1 tilts the other way. */
    fun tilt(t: Float, side: Float = 1f): PupFrame {
        if (t <= 0f || t >= TILT_MS) return PupFrame.REST
        val k = when {
            t < TILT_IN_MS -> easeOut(t / TILT_IN_MS)
            t < TILT_IN_MS + TILT_HOLD_MS -> 1f
            else -> 1f - easeInOut((t - TILT_IN_MS - TILT_HOLD_MS) / TILT_OUT_MS)
        }
        return PupFrame(rotation = side * TILT_DEG * k)
    }

    /** The wag at [t] ms: three wobbles, the last ones a little smaller. */
    fun wag(t: Float): PupFrame {
        if (t <= 0f || t >= WAG_MS) return PupFrame.REST
        val p = t / WAG_MS
        return PupFrame(rotation = WAG_DEG * sin(2 * PI * t / WAG_CYCLE_MS).toFloat() * (1f - 0.4f * p))
    }

    /** A bpm folded into [CHEER_MIN_BPM] .. [CHEER_MAX_BPM] by halving or doubling (none or a bad one: [CHEER_BPM]). */
    fun cheerBpm(bpm: Int): Int {
        var b = if (bpm <= 0) CHEER_BPM else bpm
        while (b > CHEER_MAX_BPM) b /= 2
        while (b < CHEER_MIN_BPM) b *= 2
        return b
    }

    /** The cheer [beats] beats in (fractional): a hop between beats landing on each one, swaying one way then the other. */
    fun cheer(beats: Double): PupFrame {
        val b = beats - 2.0 * floor(beats / 2.0)                             // 0 .. 2: two beats, one sway each way
        val hop = abs(sin(PI * b)).toFloat()
        val land = (1f - hop).let { it * it * it * it }
        return PupFrame(1f + CHEER_SQUASH * 0.6f * land, 1f - CHEER_SQUASH * land, rotation = CHEER_TILT_DEG * sin(PI * b).toFloat(),
            lift = CHEER_BOB * hop)
    }

    /** The squint at [t] ms: 0 open, 1 squeezed, and open again. */
    fun squint(t: Float): Float = if (t <= 0f || t >= SQUINT_MS) 0f else 1f - abs(2f * t / SQUINT_MS - 1f)

    /** The blink with a closed-eyes twin at [t] ms: how much of the twin shows. */
    fun blinkTwin(t: Float): Float = when {
        t <= 0f -> 0f
        t < BLINK_FADE_MS -> t / BLINK_FADE_MS
        t < BLINK_FADE_MS + BLINK_SHUT_MS -> 1f
        t < BLINK_FADE_MS * 2 + BLINK_SHUT_MS -> 1f - (t - BLINK_FADE_MS - BLINK_SHUT_MS) / BLINK_FADE_MS
        else -> 0f
    }

    /** The flick at [t] ms, degrees: out quickly, back with one small swing past. */
    fun flick(t: Float): Float = when {
        t <= 0f || t >= FLICK_MS -> 0f
        t < FLICK_OUT_MS -> FLICK_DEG * easeOut(t / FLICK_OUT_MS)
        else -> {
            val u = (t - FLICK_OUT_MS) / (FLICK_MS - FLICK_OUT_MS)
            FLICK_DEG * (1f - u) * (1f - u) * cos(2 * PI * u).toFloat()
        }
    }

    /** The eyes layer's height during a blink, [t] ms in: 1 open, [EYES_SHUT] shut. */
    fun eyesBlink(t: Float): Float = when {
        t <= 0f || t >= BLINK_EYES_MS -> 1f
        t < BLINK_CLOSE_MS -> 1f - (1f - EYES_SHUT) * easeOut(t / BLINK_CLOSE_MS)
        t < BLINK_CLOSE_MS + BLINK_HOLD_MS -> EYES_SHUT
        else -> EYES_SHUT + (1f - EYES_SHUT) * easeInOut((t - BLINK_CLOSE_MS - BLINK_HOLD_MS) / BLINK_OPEN_MS)
    }

    /** The tail during a wag, [t] ms in, degrees: three swings with the body's wobble, a little smaller each time. */
    fun tailWag(t: Float): Float {
        if (t <= 0f || t >= WAG_MS) return 0f
        return TAIL_WAG_DEG * sin(2 * PI * t / WAG_CYCLE_MS).toFloat() * (1f - 0.4f * t / WAG_MS)
    }

    /** The flick's curve as a share of its turn (1 out, a little past 0 on the way back). */
    fun flickShare(t: Float): Float = flick(t) / FLICK_DEG

    /** The pop of a new pose, [t] ms in. */
    fun swapPop(t: Float): Float = if (t <= 0f || t >= SWAP_POP_MS) 1f else 1f + (SWAP_POP - 1f) * sin(PI * t / SWAP_POP_MS).toFloat()

    /** The time to the next blink for a random [r] in 0..1 (slower when drowsy). */
    fun blinkGap(r: Double, sleepy: Boolean = false): Long =
        ((BLINK_MIN_MS + r * (BLINK_MAX_MS - BLINK_MIN_MS)) * (if (sleepy) SLEEPY_SLOW else 1f)).toLong()

    /** The time to the next flick for a random [r] in 0..1. */
    fun flickGap(r: Double): Long = (FLICK_MIN_MS + r * (FLICK_MAX_MS - FLICK_MIN_MS)).toLong()

    /** A scale kept inside the limits, whatever stacks up. */
    fun clamp(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun easeOut(x: Float): Float { val k = 1f - x.coerceIn(0f, 1f); return 1f - k * k * k }
    private fun easeInOut(x: Float): Float {
        val k = x.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).let { it * it * it } / 2f
    }
}
