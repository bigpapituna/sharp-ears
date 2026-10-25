package com.bigpapituna.sharpears.engine.lessons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The step pop's four pictures (2.11, [PopVariant]), every number and curve, as pure functions of the time since the pop
 * (ms) so the JVM tests walk them (StepPopMotionTest) and ui/StepPop.kt only reads them in its draw block. Each is over by
 * [TOTAL_MS]; with the phone's animations off every variant is the same single fade of the check over [CALM_MS].
 *
 * - BURST: a green disc springs in with a white check drawing on, a gold ring spreads from 0.6 to 1.2 and fades, [STARS]
 *   stars fly out on a spring.
 * - CONFETTI: paper bits pop up from the step's bottom edge, rise and fall with gravity, turning and flipping; the check
 *   in the middle as in BURST.
 * - RAINBOW: four bands sweep across the step from left to right as an arc; the check pops at its end; sparkles trail the tip.
 * - PAW: a big paw print stamps down, squashes and bounces on a spring, the check draws inside it, a ring ripples out.
 */
object StepPopMotion {
    const val TOTAL_MS = 600
    /** Animations off: one fade of the check. */
    const val CALM_MS = 150
    /** Everything fades out from here to [TOTAL_MS]. */
    const val FADE_FROM_MS = 430

    // ---- The check on its disc (BURST, CONFETTI, RAINBOW) ---------------------------------------------------------------
    /** The disc's radius, dp. */
    const val DISC_DP = 30f
    /** It springs in over [DISC_IN_MS] to [DISC_OVERSHOOT] and settles at 1, shrinking to [DISC_OUT_SCALE] as it fades. */
    const val DISC_IN_MS = 240
    const val DISC_OVERSHOOT = 1.15f
    const val DISC_OUT_SCALE = 0.9f
    /** The check draws on from [CHECK_FROM_MS] for [CHECK_MS] (BURST and CONFETTI). */
    const val CHECK_FROM_MS = 80
    const val CHECK_MS = 200

    // ---- BURST: the ring and the stars ----------------------------------------------------------------------------------
    /** The ring's radius at scale 1, dp: it spreads from [RING_FROM] to [RING_TO] of it over [RING_MS]. */
    const val RING_DP = 56f
    const val RING_FROM = 0.6f
    const val RING_TO = 1.2f
    const val RING_MS = 520
    const val RING_ALPHA = 0.9f
    const val STARS = 7
    /** Where the stars start (dp from the middle) and how far further they fly. */
    const val STAR_FROM_DP = 26f
    const val STAR_REACH_DP = 84f
    /** Their spring: about a fifth past the mark and back, settled in about 0.35 s. */
    const val STAR_DAMPING = 0.45f
    const val STAR_SETTLE_MS = 350
    /** A star's outer radius, dp, the smaller ones [STAR_SMALL] of it; stars fade from [STAR_FADE_FROM_MS], turning [STAR_SPIN_DEG]. */
    const val STAR_DP = 9f
    const val STAR_SMALL = 0.7f
    const val STAR_FADE_FROM_MS = 320
    const val STAR_SPIN_DEG = 90f

    // ---- CONFETTI: paper bits from the bottom edge ----------------------------------------------------------------------
    const val BITS = 18
    /** A bit's size, dp (a little paper rectangle). */
    const val BIT_W_DP = 11f
    const val BIT_H_DP = 6f
    /** Where along the bottom edge they start (a share of the step's width either side of the middle) and how far they drift. */
    const val BIT_SPREAD = 0.36f
    const val BIT_DRIFT = 0.16f
    /** How high they get, a share of the step's height above its bottom edge, and when (each bit its own, in these ranges). */
    const val BIT_RISE_MIN = 0.45f
    const val BIT_RISE_MAX = 0.8f
    const val BIT_PEAK_MIN_MS = 240
    const val BIT_PEAK_MAX_MS = 300
    /** A slight spin over the whole pop, degrees either way, and the paper's flip (turns a second). */
    const val BIT_SPIN_MAX_DEG = 160f
    const val BIT_FLIP_PER_S = 2.5f

    // ---- RAINBOW: the arc drawn on, left to right ----------------------------------------------------------------------
    const val BANDS = 4
    /** The arc runs clockwise from [ARC_FROM_DEG] (left, a little above the middle) through [ARC_SWEEP_DEG] (0 degrees is 3 o'clock). */
    const val ARC_FROM_DEG = 190f
    const val ARC_SWEEP_DEG = 160f
    /** The arc's radius: a share of the step's width or height, whichever is tighter, at most [ARC_MAX_DP]. */
    const val ARC_W_SHARE = 0.40f
    const val ARC_H_SHARE = 0.75f
    const val ARC_MAX_DP = 170f
    /** A band's width, a share of the radius. */
    const val BAND_SHARE = 0.09f
    /** The arc draws on over [ARC_DRAW_MS], each band [BAND_LAG_MS] behind the one outside it. */
    const val ARC_DRAW_MS = 300
    const val BAND_LAG_MS = 20
    /** The check pops at the arc's end as the bands get there. */
    const val ARC_CHECK_FROM_MS = ARC_DRAW_MS + BAND_LAG_MS * (BANDS - 1) - 40
    const val ARC_CHECK_IN_MS = 160
    const val ARC_CHECK_DRAW_MS = 110
    /** Sparkles trailing the tip: degrees behind it, and their size (dp). */
    val SPARKLE_LAG_DEG = listOf(10f, 22f, 36f)
    const val SPARKLE_DP = 6f

    // ---- PAW: the stamp ------------------------------------------------------------------------------------------------
    /** The paw's unit, dp: the main pad is 2 units wide, the whole print with its toes about 2.9 across and as high (110 dp). */
    const val PAW_DP = 38f
    /** It comes down from [PAW_FROM] of its size, faint to solid, and lands at [PAW_STAMP_MS]. */
    const val PAW_FROM = 1.6f
    const val PAW_STAMP_MS = 110
    /** The squash on landing, ringing out on a spring: wider and flatter by these, two bounces seen. */
    const val PAW_SQUASH_X = 0.16f
    const val PAW_SQUASH_Y = 0.20f
    const val PAW_RING_DECAY_MS = 90f
    const val PAW_BOUNCE_MS = 200f
    /** The check inside the main pad draws on from [PAW_CHECK_FROM_MS] for [PAW_CHECK_MS]. */
    const val PAW_CHECK_FROM_MS = 170
    const val PAW_CHECK_MS = 170
    /** The ripple from the landing: a ring from [PAW_RIPPLE_FROM] to [PAW_RIPPLE_TO] of [PAW_RIPPLE_DP], over [PAW_RIPPLE_MS]. */
    const val PAW_RIPPLE_DP = 86f
    const val PAW_RIPPLE_FROM = 0.7f
    const val PAW_RIPPLE_TO = 1.6f
    const val PAW_RIPPLE_MS = 400

    // ---- Shared -------------------------------------------------------------------------------------------------------

    /** How opaque everything is at [t] ms: solid, then fading from [FADE_FROM_MS] to nothing at [TOTAL_MS]. */
    fun fade(t: Float): Float = when {
        t < 0f || t >= TOTAL_MS -> 0f
        t < FADE_FROM_MS -> 1f
        else -> 1f - easeIn((t - FADE_FROM_MS) / (TOTAL_MS - FADE_FROM_MS))
    }

    /** A disc springing in from [fromMs] over [inMs]: up past 1 to [DISC_OVERSHOOT], back to 1, a little smaller as it fades. */
    fun springIn(t: Float, fromMs: Int = 0, inMs: Int = DISC_IN_MS): Float {
        val u = t - fromMs
        if (u <= 0f || t >= TOTAL_MS) return 0f
        val p = u / inMs
        val grow = when {
            p < 0.6f -> DISC_OVERSHOOT * easeOut(p / 0.6f)
            p < 1f -> DISC_OVERSHOOT + (1f - DISC_OVERSHOOT) * easeInOut((p - 0.6f) / 0.4f)
            else -> 1f
        }
        val shrink = if (t < FADE_FROM_MS) 1f else 1f + (DISC_OUT_SCALE - 1f) * (t - FADE_FROM_MS) / (TOTAL_MS - FADE_FROM_MS)
        return grow * shrink
    }

    /** How much of a check is drawn at [t] ms when it draws on from [fromMs] over [ms]: 0 .. 1. */
    fun drawOn(t: Float, fromMs: Int, ms: Int): Float = when {
        t <= fromMs -> 0f
        t >= fromMs + ms -> 1f
        else -> easeOut((t - fromMs) / ms)
    }

    /** The disc's scale (BURST and CONFETTI). */
    fun discScale(t: Float): Float = springIn(t)

    /** The disc's opacity (every variant with a disc). */
    fun discAlpha(t: Float): Float = fade(t)

    /** How much of the check is drawn (BURST and CONFETTI). */
    fun check(t: Float): Float = drawOn(t, CHECK_FROM_MS, CHECK_MS)

    // ---- BURST ----------------------------------------------------------------------------------------------------------

    /** The ring's scale at [t] ms ([RING_FROM] .. [RING_TO], fast then slowing). */
    fun ringScale(t: Float): Float = RING_FROM + (RING_TO - RING_FROM) * easeOut((t / RING_MS).coerceIn(0f, 1f))

    /** The ring's opacity at [t] ms: [RING_ALPHA] fading to nothing by [RING_MS]. */
    fun ringAlpha(t: Float): Float = if (t <= 0f || t >= RING_MS) 0f else RING_ALPHA * (1f - t / RING_MS).let { it * it }

    // The stars' spring: an underdamped spring's step response, settled (within 2%) at STAR_SETTLE_MS.
    private val omega = 4.0 / (STAR_DAMPING * STAR_SETTLE_MS / 1000.0)
    private val omegaD = omega * sqrt(1.0 - STAR_DAMPING * STAR_DAMPING)

    /** How far out the stars are at [t] ms as a share of their reach: 0 to past 1 and back toward 1. */
    fun starOut(t: Float): Float {
        if (t <= 0f) return 0f
        val s = t / 1000.0
        val e = exp(-STAR_DAMPING * omega * s)
        return (1.0 - e * (cos(omegaD * s) + STAR_DAMPING / sqrt(1.0 - STAR_DAMPING * STAR_DAMPING) * sin(omegaD * s))).toFloat()
    }

    /** A star's opacity at [t] ms. */
    fun starAlpha(t: Float): Float = when {
        t <= 0f || t >= TOTAL_MS -> 0f
        t < 40f -> t / 40f
        t < STAR_FADE_FROM_MS -> 1f
        else -> 1f - (t - STAR_FADE_FROM_MS) / (TOTAL_MS - STAR_FADE_FROM_MS)
    }

    /** A star's size at [t] ms as a share of its own: grows in, then shrinks a little as it flies. */
    fun starScale(t: Float): Float = when {
        t <= 0f -> 0f
        t < 100f -> 0.4f + 0.6f * easeOut(t / 100f)
        else -> 1f - 0.35f * ((t - 100f) / (TOTAL_MS - 100f)).coerceIn(0f, 1f)
    }

    /** Star [i]'s direction, degrees clockwise from straight up: evenly round, nudged by [seed] so no two pops match. */
    fun starAngle(i: Int, seed: Int): Float {
        val nudge = (Math.floorMod(seed * 31 + i * 17, 21) - 10) * 1.5f   // -15 .. 15 degrees
        return i * 360f / STARS + nudge
    }

    // ---- CONFETTI -------------------------------------------------------------------------------------------------------

    /** One paper bit's draw of the dice for pop [seed]: four numbers in 0..1, the same every time for the same bit and pop. */
    fun bitDice(i: Int, seed: Int): FloatArray {
        var x = (seed.toLong() shl 20) xor (i.toLong() * GOLDEN)
        return FloatArray(4) { k -> x = mix(x + k); (x ushr 40).toFloat() / (1L shl 24).toFloat() }
    }

    /** Bit [i]'s place at [t] ms: x a share of the step's width from its middle, y a share of its height above its bottom edge. */
    fun bitAt(t: Float, i: Int, seed: Int): Pair<Float, Float> {
        val d = bitDice(i, seed)
        val x0 = (d[0] * 2f - 1f) * BIT_SPREAD
        val drift = (d[1] * 2f - 1f) * BIT_DRIFT
        val rise = BIT_RISE_MIN + (BIT_RISE_MAX - BIT_RISE_MIN) * d[2]
        val peak = BIT_PEAK_MIN_MS + (BIT_PEAK_MAX_MS - BIT_PEAK_MIN_MS) * d[3]
        val u = t.coerceIn(0f, TOTAL_MS.toFloat())
        // Thrown up and pulled down by the same gravity: the top at its peak time, back at the edge at twice it.
        val y = rise * (1f - ((u - peak) / peak).let { it * it })
        return (x0 + drift * u / TOTAL_MS) to y.coerceAtLeast(0f)
    }

    /** Bit [i]'s turn at [t] ms, degrees: a slight spin, one way or the other, from its own start. */
    fun bitSpin(t: Float, i: Int, seed: Int): Float = (bitDice(i, seed)[1] * 2f - 1f) * BIT_SPIN_MAX_DEG * t / TOTAL_MS + i * 37f

    /** Bit [i]'s width at [t] ms as a share of its own: paper flipping over as it flies. */
    fun bitFlip(t: Float, i: Int, seed: Int): Float =
        0.25f + 0.75f * abs(cos(PI * (BIT_FLIP_PER_S * 2.0 * t / 1000.0 + bitDice(i, seed)[0]))).toFloat()

    /** The bits' opacity at [t] ms. */
    fun bitAlpha(t: Float): Float = if (t <= 0f) 0f else fade(t)

    // ---- RAINBOW --------------------------------------------------------------------------------------------------------

    /** How far band [b] (0 the outside) has drawn at [t] ms, 0 .. 1 of the sweep. */
    fun bandDrawn(t: Float, b: Int): Float = easeInOut(((t - b * BAND_LAG_MS) / ARC_DRAW_MS).coerceIn(0f, 1f))

    /** The tip's angle at [t] ms (the outside band's), degrees clockwise from 3 o'clock. */
    fun tipDeg(t: Float): Float = ARC_FROM_DEG + ARC_SWEEP_DEG * bandDrawn(t, 0)

    /** The check at the arc's end: its scale and how much of it is drawn. */
    fun arcCheckScale(t: Float): Float = springIn(t, ARC_CHECK_FROM_MS, ARC_CHECK_IN_MS)
    fun arcCheck(t: Float): Float = drawOn(t, ARC_CHECK_FROM_MS + 30, ARC_CHECK_DRAW_MS)

    /** The trailing sparkles' opacity at [t] ms: with the tip while it moves, fading once it stops. */
    fun sparkleAlpha(t: Float): Float = when {
        t <= 20f || t >= TOTAL_MS -> 0f
        t < ARC_DRAW_MS -> 1f
        else -> (1f - (t - ARC_DRAW_MS) / (TOTAL_MS - 80f - ARC_DRAW_MS)).coerceIn(0f, 1f)
    }

    /** A trailing sparkle's twinkle at [t] ms (its size as a share of its own), each [k] out of step. */
    fun sparkleScale(t: Float, k: Int): Float = 0.6f + 0.4f * abs(sin(PI * (t / 90.0 + k * 0.33))).toFloat()

    // ---- PAW ------------------------------------------------------------------------------------------------------------

    /** The paw's width and height at [t] ms as shares of its size: coming down from big, then the squash ringing out. */
    fun pawScale(t: Float): Pair<Float, Float> {
        if (t <= 0f || t >= TOTAL_MS) return 0f to 0f
        if (t < PAW_STAMP_MS) {
            val s = PAW_FROM + (1f - PAW_FROM) * easeIn(t / PAW_STAMP_MS)      // speeding up as it comes down
            return s to s
        }
        val d = pawRing(t - PAW_STAMP_MS)
        return (1f + PAW_SQUASH_X * d) to (1f - PAW_SQUASH_Y * d)
    }

    /** The landing's ring: 1 at touchdown, bouncing past 0 and dying away. */
    fun pawRing(u: Float): Float = if (u < 0f) 0f else (exp(-u / PAW_RING_DECAY_MS) * cos(2.0 * PI * u / PAW_BOUNCE_MS)).toFloat()

    /** The paw's opacity at [t] ms: faint to solid on the way down, then as everything fades. */
    fun pawAlpha(t: Float): Float = if (t <= 0f) 0f else minOf((t / 70f).coerceAtMost(1f), fade(t))

    /** How much of the check inside the paw is drawn. */
    fun pawCheck(t: Float): Float = drawOn(t, PAW_CHECK_FROM_MS, PAW_CHECK_MS)

    /** The ripple's scale and opacity at [t] ms, from the landing on. */
    fun rippleScale(t: Float): Float =
        PAW_RIPPLE_FROM + (PAW_RIPPLE_TO - PAW_RIPPLE_FROM) * easeOut(((t - PAW_STAMP_MS) / PAW_RIPPLE_MS).coerceIn(0f, 1f))
    fun rippleAlpha(t: Float): Float {
        val u = t - PAW_STAMP_MS
        return if (u <= 0f || u >= PAW_RIPPLE_MS) 0f else 0.85f * (1f - u / PAW_RIPPLE_MS).let { it * it }
    }

    // ---- Animations off -------------------------------------------------------------------------------------------------

    /** The check's opacity at [t] ms, shown at once and faded out over [CALM_MS] (every variant). */
    fun calmAlpha(t: Float): Float = if (t < 0f || t >= CALM_MS) 0f else 1f - t / CALM_MS

    private const val GOLDEN = -0x61c8864680b583ebL
    /** SplitMix64's finaliser: neighbouring seeds give unrelated numbers. */
    internal fun mix(z0: Long): Long {
        var z = z0 + GOLDEN
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    private fun easeOut(x: Float): Float { val k = 1f - x.coerceIn(0f, 1f); return 1f - k * k * k }
    private fun easeIn(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k }
    private fun easeInOut(x: Float): Float {
        val k = x.coerceIn(0f, 1f)
        return if (k < 0.5f) 4f * k * k * k else 1f - (-2f * k + 2f).let { it * it * it } / 2f
    }
}
