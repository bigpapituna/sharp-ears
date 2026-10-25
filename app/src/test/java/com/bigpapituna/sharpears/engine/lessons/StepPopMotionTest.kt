package com.bigpapituna.sharpears.engine.lessons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.11, the step pop's burst (engine/lessons/StepPopMotion.kt), walked a millisecond at a time. */
class StepPopMotionTest {
    private val m = StepPopMotion
    private val ms = 0..m.TOTAL_MS

    @Test fun theBurstIsAboutSixHundredMillisecondsAndGoneAfter() {
        assertEquals(600, m.TOTAL_MS)
        val end = m.TOTAL_MS.toFloat()
        assertEquals(0f, m.discAlpha(end), 0f); assertEquals(0f, m.ringAlpha(end), 0f); assertEquals(0f, m.starAlpha(end), 0f)
        assertEquals(0f, m.discScale(0f), 0f); assertEquals(0f, m.starAlpha(0f), 0f)
        // Something shows from the first frames on.
        assertTrue(m.discAlpha(16f) > 0.9f && m.ringAlpha(16f) > 0.8f && m.starAlpha(16f) > 0.3f)
    }

    @Test fun theRingSpreadsFromPointSixToOnePointTwoAndFades() {
        assertEquals(0.6f, m.ringScale(0f), 1e-6f)
        assertEquals(1.2f, m.ringScale(m.RING_MS.toFloat()), 1e-6f)
        assertEquals(1.2f, m.ringScale(m.TOTAL_MS.toFloat()), 1e-6f)
        val scales = ms.map { m.ringScale(it.toFloat()) }
        assertTrue(scales.zipWithNext().all { (a, b) -> b >= a })
        val alphas = ms.drop(1).map { m.ringAlpha(it.toFloat()) }
        assertTrue(alphas.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(m.RING_MS <= m.TOTAL_MS)
    }

    @Test fun theCheckDrawsOnWhileTheDiscSpringsIn() {
        assertEquals(0f, m.check(0f), 0f)
        assertEquals(1f, m.check((m.CHECK_FROM_MS + m.CHECK_MS).toFloat()), 0f)
        assertTrue(ms.map { m.check(it.toFloat()) }.zipWithNext().all { (a, b) -> b >= a })
        // The disc overshoots a little and settles at its size before it fades.
        val disc = ms.map { m.discScale(it.toFloat()) }
        assertEquals(m.DISC_OVERSHOOT, disc.max(), 0.01f)
        assertEquals(1f, m.discScale((m.DISC_IN_MS + 20).toFloat()), 1e-6f)
        assertTrue(m.DISC_OVERSHOOT <= 1.25f)
        // The check is whole well before the fade starts.
        assertTrue(m.CHECK_FROM_MS + m.CHECK_MS < m.FADE_FROM_MS)
    }

    @Test fun theStarsFlyOutOnASpringAndFade() {
        assertTrue(m.STARS in 6..8)
        val out = ms.map { m.starOut(it.toFloat()) }
        assertEquals(0f, out.first(), 0f)
        // Past the mark (a spring's overshoot, about a fifth) and back toward it.
        assertTrue(out.max() in 1.1f..1.3f)
        assertEquals(1f, m.starOut(m.STAR_SETTLE_MS.toFloat()), 0.03f)
        assertEquals(1f, out.last(), 0.03f)
        // Their directions go all the way round, never two together, and differ between pops.
        for (seed in listOf(0, 1, 77)) {
            val a = (0 until m.STARS).map { m.starAngle(it, seed) }
            assertTrue(a.zipWithNext().all { (x, y) -> y - x > 360f / m.STARS - 31f })
        }
        assertTrue((0 until m.STARS).map { m.starAngle(it, 1) } != (0 until m.STARS).map { m.starAngle(it, 2) })
        assertTrue(ms.all { m.starScale(it.toFloat()) <= 1f })
    }

    /** Every picture is gone by 600 ms and shows something from its first frames. */
    @Test fun everyPictureIsOverInSixHundredMilliseconds() {
        val end = m.TOTAL_MS.toFloat()
        for (t in listOf(end, end + 100f)) {
            assertEquals(0f, m.bitAlpha(t), 0f); assertEquals(0f, m.fade(t), 0f); assertEquals(0f, m.sparkleAlpha(t), 0f)
            assertEquals(0f, m.pawAlpha(t), 0f); assertEquals(0f, m.rippleAlpha(t), 0f); assertEquals(0f, m.arcCheckScale(t), 0f)
        }
        assertTrue(m.bitAlpha(16f) > 0.9f && m.fade(16f) == 1f && m.pawAlpha(16f) > 0.2f && m.bandDrawn(16f, 0) > 0f)
    }

    /** CONFETTI: up from the bottom edge, a top, back down with gravity; a slight spin; inside the step. */
    @Test fun theConfettiPopsUpFromTheBottomEdgeAndFallsBack() {
        for (seed in listOf(1, 2, 99)) for (i in 0 until m.BITS) {
            val (x0, y0) = m.bitAt(0f, i, seed)
            assertEquals(0f, y0, 1e-6f)                                            // on the bottom edge
            val ys = (0..m.TOTAL_MS).map { m.bitAt(it.toFloat(), i, seed).second }
            val top = ys.indices.maxBy { ys[it] }
            assertTrue(top in m.BIT_PEAK_MIN_MS..m.BIT_PEAK_MAX_MS)
            assertTrue(ys[top] in m.BIT_RISE_MIN..m.BIT_RISE_MAX)
            assertTrue(ys.last() < ys[top] * 0.2f)                                 // back down by the end
            // Rising slows to the top: gravity.
            assertTrue(ys[top / 4] - ys[0] > ys[top] - ys[top * 3 / 4])
            assertTrue(kotlin.math.abs(x0) <= m.BIT_SPREAD)
            assertTrue((0..m.TOTAL_MS).all { kotlin.math.abs(m.bitAt(it.toFloat(), i, seed).first) <= m.BIT_SPREAD + m.BIT_DRIFT })
            val turned = m.bitSpin(m.TOTAL_MS.toFloat(), i, seed) - m.bitSpin(0f, i, seed)
            assertTrue(kotlin.math.abs(turned) <= m.BIT_SPIN_MAX_DEG)
            assertTrue((0..m.TOTAL_MS).all { m.bitFlip(it.toFloat(), i, seed) in 0.25f..1f })
        }
        // The same pop draws the same bits; another pop draws others.
        assertTrue(m.bitDice(3, 7).contentEquals(m.bitDice(3, 7)) && !m.bitDice(3, 7).contentEquals(m.bitDice(3, 8)))
    }

    /** RAINBOW: drawn on from left to right, the outside band first, the check popping at the end, the sparkles fading after. */
    @Test fun theRainbowDrawsOnLeftToRightAndTheCheckPopsAtItsEnd() {
        assertEquals(m.ARC_FROM_DEG, m.tipDeg(0f), 0f)
        assertEquals(m.ARC_FROM_DEG + m.ARC_SWEEP_DEG, m.tipDeg(m.ARC_DRAW_MS.toFloat()), 1e-3f)
        assertTrue((0..m.TOTAL_MS).map { m.tipDeg(it.toFloat()) }.zipWithNext().all { (a, b) -> b >= a })
        for (b in 1 until m.BANDS) assertTrue(m.bandDrawn(150f, b) < m.bandDrawn(150f, b - 1))
        for (b in 0 until m.BANDS) assertEquals(1f, m.bandDrawn((m.ARC_DRAW_MS + m.BAND_LAG_MS * b).toFloat(), b), 1e-6f)
        // Left (180 degrees is nine on a clock) over the top (270) to the right (360 is three), even about the top.
        assertTrue(m.ARC_FROM_DEG in 180f..200f && m.ARC_FROM_DEG + m.ARC_SWEEP_DEG in 340f..360f)
        assertEquals(270f, m.ARC_FROM_DEG + m.ARC_SWEEP_DEG / 2, 1e-3f)
        assertEquals(0f, m.arcCheckScale(m.ARC_CHECK_FROM_MS.toFloat()), 0f)
        assertTrue(m.ARC_CHECK_FROM_MS >= m.ARC_DRAW_MS - 60 && m.arcCheckScale(m.ARC_CHECK_FROM_MS + 100f) > 1f)
        assertEquals(1f, m.arcCheck((m.ARC_CHECK_FROM_MS + 30 + m.ARC_CHECK_DRAW_MS).toFloat()), 0f)
        assertTrue(m.ARC_CHECK_FROM_MS + 30 + m.ARC_CHECK_DRAW_MS < m.TOTAL_MS - 100)
        assertEquals(1f, m.sparkleAlpha(200f), 0f)
        assertTrue(m.sparkleAlpha(450f) < 0.6f && m.SPARKLE_LAG_DEG.size in 2..3)
    }

    /** PAW: down from big to its size by the stamp, a squash and a bounce ringing out, the check inside, the ripple after landing. */
    @Test fun thePawStampsSquashesBouncesAndRipples() {
        assertEquals(m.PAW_FROM, m.pawScale(1f).first, 0.01f)
        val landed = m.pawScale(m.PAW_STAMP_MS.toFloat())
        assertEquals(1f + m.PAW_SQUASH_X, landed.first, 1e-4f)                  // squashed: wider and flatter
        assertEquals(1f - m.PAW_SQUASH_Y, landed.second, 1e-4f)
        val ys = (m.PAW_STAMP_MS until m.TOTAL_MS).map { m.pawScale(it.toFloat()).second }
        val overs = ys.indices.filter { i -> i in 1 until ys.size - 1 && ys[i] > ys[i - 1] && ys[i] >= ys[i + 1] && ys[i] > 1.005f }
        assertTrue("a bounce up past its size", overs.isNotEmpty())
        assertEquals(1f, m.pawScale(m.FADE_FROM_MS.toFloat()).second, 0.02f)    // settled before it fades
        for (t in 0 until m.TOTAL_MS) m.pawScale(t.toFloat()).let { (x, y) -> assertTrue(x <= m.PAW_FROM && y <= m.PAW_FROM) }
        assertEquals(0f, m.rippleAlpha(m.PAW_STAMP_MS.toFloat()), 0f)
        assertTrue(m.rippleAlpha(m.PAW_STAMP_MS + 50f) > 0.5f)
        assertEquals(m.PAW_RIPPLE_TO, m.rippleScale(m.PAW_STAMP_MS + m.PAW_RIPPLE_MS.toFloat()), 1e-6f)
        assertEquals(0f, m.pawCheck(m.PAW_STAMP_MS.toFloat()), 0f)
        assertEquals(1f, m.pawCheck((m.PAW_CHECK_FROM_MS + m.PAW_CHECK_MS).toFloat()), 0f)
    }

    /** Animations off: one 150 ms fade of the check, and nothing else moves. */
    @Test fun animationsOffIsOneShortFade() {
        assertEquals(150, m.CALM_MS)
        assertEquals(1f, m.calmAlpha(0f), 0f)
        assertEquals(0f, m.calmAlpha(150f), 0f)
        assertTrue((0..150).map { m.calmAlpha(it.toFloat()) }.zipWithNext().all { (a, b) -> b <= a })
    }
}
