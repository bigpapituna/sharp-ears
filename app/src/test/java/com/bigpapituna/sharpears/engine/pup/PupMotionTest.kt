package com.bigpapituna.sharpears.engine.pup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.10: the living pup's table (engine/pup/PupMotion.kt), walked a millisecond at a time. */
class PupMotionTest {
    private val m = PupMotion

    /** Every frame of a curve from 0 to [ms], one per millisecond. */
    private fun frames(ms: Int, f: (Float) -> PupFrame) = (0..ms).map { f(it.toFloat()) }

    @Test fun everyBeatIsOverInUnderASecondAndAHalf() {
        for (t in m.TIMINGS) assertTrue("${t.name} ${t.ms} ms", t.ms in 1 until 1500)
        for (mood in PupMood.entries.filter { it.beat }) assertTrue("$mood", m.beatMs(mood) in 1 until 1500)
        for (mood in PupMood.entries.filter { !it.beat }) assertEquals("$mood holds", 0, m.beatMs(mood))
    }

    @Test fun theIdleLoopIsBetweenTwoAndAHalfAndFourSeconds() {
        assertTrue(m.BREATH_MS in 2500..4000)
        // A breath: 1.0 at the start, 1.02 at the top, a sine back down.
        assertEquals(0f, m.breath(0.0), 1e-6f)
        assertEquals(m.BREATH_SCALE, m.breath(0.5), 1e-6f)
        assertEquals(m.breath(0.25), m.breath(0.75), 1e-6f)
        assertEquals(0.02f, m.BREATH_SCALE, 1e-6f)
        // Blinks every 3 to 6 s, flicks every 8 to 15 s, for any draw of the dice.
        for (r in listOf(0.0, 0.3, 0.999)) {
            assertTrue(m.blinkGap(r) in 3000L..6000L)
            assertTrue(m.flickGap(r) in 8000L..15000L)
        }
        // Asleep he breathes slower than awake.
        assertTrue(m.SLEEP_BREATH_MS > m.BREATH_MS)
    }

    @Test fun noScaleGoesBeyondOnePointTwoFive() {
        for (s in m.SCALES) assertTrue("$s", s in m.MIN_SCALE..m.MAX_SCALE)
        val all = frames(m.HAPPY_MS) { m.happy(it) } + frames(m.POP_BEAT_MS) { m.pop(it) } + frames(m.CHEER_WAG_MS) { m.cheerWag(it) } + frames(m.TILT_MS) { m.tilt(it) } + frames(m.WAG_MS) { m.wag(it) } +
            (0..4000).map { m.cheer(it / 1000.0) }
        for (f in all) {
            assertTrue("$f", f.scaleX <= m.MAX_SCALE && f.scaleY <= m.MAX_SCALE)
            assertTrue("$f", f.scaleX >= m.MIN_SCALE && f.scaleY >= m.MIN_SCALE)
        }
        // The worst stack the screen can make (a pop on a stretch on a breath) is clamped to the limit.
        val worst = all.maxOf { maxOf(it.scaleX, it.scaleY) } * m.SWAP_POP * (1f + m.SLEEP_SCALE) * (1f + m.BREATH_SCALE)
        assertTrue(worst > 1f)
        assertTrue(m.clamp(worst) <= 1.25f)
        assertEquals(1.25f, m.clamp(9f), 0f)
        assertEquals(m.MIN_SCALE, m.clamp(0.1f), 0f)
        for (t in 0..m.SWAP_POP_MS) assertTrue(m.swapPop(t.toFloat()) in 1f..m.SWAP_POP)
    }

    @Test fun theJumpSquashesStretchesHangsAndLandsWithTwoBounces() {
        val f = frames(m.HAPPY_MS) { m.happy(it) }
        assertEquals(PupFrame.REST, f.first())
        assertEquals(PupFrame.REST, f.last())
        // Squashed before take-off, stretched on the way up.
        val squash = m.happy(m.SQUASH_MS - 1f)
        assertTrue(squash.scaleX > 1.08f && squash.scaleY < 0.92f && squash.lift == 0f)
        val stretch = f.maxBy { it.scaleY }
        assertEquals(m.STRETCH_Y, stretch.scaleY, 0.005f)
        assertTrue(stretch.scaleX < 0.95f && stretch.lift > 0f)
        // The top is the jump's height, held through the hang.
        val top = m.SQUASH_MS + m.RISE_MS
        assertEquals(m.JUMP, f.maxOf { it.lift }, 1e-4f)
        for (t in top until top + m.HANG_MS) assertEquals(m.JUMP, f[t].lift, 1e-4f)
        // Never under the ground.
        assertTrue(f.all { it.lift >= 0f })
        // The landing: the peaks of the lift after touchdown that anyone would see.
        val land = top + m.HANG_MS + m.FALL_MS
        val after = f.drop(land).map { it.lift / m.JUMP }
        val peaks = after.indices.filter { i -> i in 1 until after.size - 1 && after[i] > after[i - 1] && after[i] >= after[i + 1] }.map { after[it] }
        assertEquals("bounces ${peaks}", 2, peaks.count { it > m.SEEN })
        assertEquals(m.LAND_FIRST, peaks.first(), 0.01f)
        assertTrue(peaks[1] < peaks[0] * 0.4f)
        // Each touchdown squashes him, the first most.
        assertEquals(1f - m.LAND_SQUASH, f[land].scaleY, 0.002f)
        assertTrue(m.bounce(3) < m.SEEN && m.bounce(2) > m.SEEN)
    }

    @Test fun theTiltLeansTwelveDegreesAndHolds() {
        val f = frames(m.TILT_MS) { m.tilt(it) }
        assertEquals(m.TILT_DEG, f.maxOf { it.rotation }, 1e-4f)
        assertEquals(12f, m.TILT_DEG, 0f)
        assertTrue(m.TILT_HOLD_MS >= 300)
        for (t in m.TILT_IN_MS until m.TILT_IN_MS + m.TILT_HOLD_MS) assertEquals(m.TILT_DEG, f[t].rotation, 1e-4f)
        assertEquals(-m.TILT_DEG, m.tilt(m.TILT_IN_MS + 10f, side = -1f).rotation, 1e-4f)
        assertEquals(PupFrame.REST, f.last())
        // A tilt never lifts, squashes or shrinks him: it is a question, not a slump.
        assertTrue(f.all { it.lift == 0f && it.scaleX == 1f && it.scaleY == 1f })
    }

    @Test fun theWagIsThreeWobblesOfFourDegrees() {
        val f = frames(m.WAG_MS) { m.wag(it) }
        assertTrue(f.all { kotlin.math.abs(it.rotation) <= m.WAG_DEG + 1e-4f })
        val crossings = (1 until f.size - 1).count { i -> f[i].rotation > 0f && f[i + 1].rotation <= 0f }
        assertEquals(m.WAG_CYCLES, crossings)
        assertTrue(f.maxOf { it.rotation } > 3.5f)
    }

    @Test fun theCheerLandsOnEveryBeatAndSwaysBothWays() {
        for (b in 0..7) {
            val on = m.cheer(b.toDouble())
            assertEquals("beat $b on the ground", 0f, on.lift, 1e-4f)
            assertTrue(on.scaleY < 1f)                                           // a little squash on the beat
            assertEquals(m.CHEER_BOB, m.cheer(b + 0.5).lift, 1e-4f)                // up between beats
        }
        assertTrue(m.cheer(0.5).rotation > 0f && m.cheer(1.5).rotation < 0f)
        // Folded into a range where a hop per beat reads as one.
        assertEquals(120, m.cheerBpm(120))
        assertEquals(90, m.cheerBpm(180))
        assertEquals(80, m.cheerBpm(40))
        assertEquals(m.CHEER_BPM, m.cheerBpm(0))
        for (b in listOf(20, 45, 61, 151, 300, 999)) assertTrue("$b", m.cheerBpm(b) in m.CHEER_MIN_BPM..m.CHEER_MAX_BPM)
    }

    @Test fun theBlinkSquintAndFlickComeBackToRest() {
        assertEquals(0f, m.squint(0f), 0f)
        assertEquals(1f, m.squint(m.SQUINT_MS / 2f), 1e-4f)
        assertEquals(0f, m.squint(m.SQUINT_MS.toFloat()), 0f)
        assertEquals(1f, m.blinkTwin(m.BLINK_FADE_MS + 1f), 0f)
        assertEquals(0f, m.blinkTwin((m.BLINK_FADE_MS * 2 + m.BLINK_SHUT_MS).toFloat()), 0f)
        val flick = (0..m.FLICK_MS).map { m.flick(it.toFloat()) }
        assertEquals(m.FLICK_DEG, flick.max(), 0.01f)
        assertEquals(0f, flick.last(), 1e-4f)
        assertTrue(flick.min() < 0f)                                                 // one small swing past
        assertTrue(m.FLICK_DEG == 3f && m.SQUINT_Y == 0.92f && m.HEAD_SHARE == 0.45f)
    }

    /** The layered poses: the eyes shut and open again, the tail wags three times, no part turns past what the cut fills. */
    @Test fun theLayeredPartsBlinkWagAndStayInsideTheirLimits() {
        assertEquals(1f, m.eyesBlink(0f), 0f)
        assertEquals(m.EYES_SHUT, (0..m.BLINK_EYES_MS).minOf { m.eyesBlink(it.toFloat()) }, 1e-4f)
        assertEquals(1f, m.eyesBlink(m.BLINK_EYES_MS.toFloat()), 0f)
        assertTrue(m.BLINK_EYES_MS < 1500 && m.EYES_SHUT in 0.05f..0.3f)
        val wag = (0..m.WAG_MS).map { m.tailWag(it.toFloat()) }
        assertEquals(m.WAG_CYCLES, (1 until wag.size - 1).count { i -> wag[i] > 0f && wag[i + 1] <= 0f })
        assertTrue(wag.all { kotlin.math.abs(it) <= m.TAIL_WAG_DEG + 1e-4f })
        for (deg in listOf(m.EAR_FLICK_DEG, m.TAIL_FLICK_DEG, m.TAIL_WAG_DEG, m.TILT_DEG, m.LISTEN_DEG)) assertTrue("$deg", deg <= m.PART_MAX_DEG)
        assertEquals(1f, (0..m.FLICK_MS).maxOf { m.flickShare(it.toFloat()) }, 0.01f)
        assertTrue(m.WAG_BODY_SHARE in 0f..1f)
    }

    @Test fun beatsHaveAnOrderAndHeldMoodsDoNotPlay() {
        assertTrue(m.priority(PupMood.HAPPY) > m.priority(PupMood.POP))
        assertTrue(m.priority(PupMood.POP) > m.priority(PupMood.TILT))
        assertEquals(m.priority(PupMood.POP), m.priority(PupMood.CHEER_WAG))
        assertTrue(m.priority(PupMood.TILT) > m.priority(PupMood.WAG))
        assertEquals(setOf(PupMood.HAPPY, PupMood.POP, PupMood.CHEER_WAG, PupMood.TILT, PupMood.WAG), PupMood.entries.filter { it.beat }.toSet())
        for (mood in PupMood.entries.filter { !it.beat }) assertEquals(PupFrame.REST, m.beat(mood, 100f))
        assertFalse(m.beat(PupMood.HAPPY, 300f) == PupFrame.REST)
        assertFalse(m.beat(PupMood.POP, 150f) == PupFrame.REST)
        assertFalse(m.beat(PupMood.CHEER_WAG, 130f) == PupFrame.REST)
    }

    /** 2.11, the step pop's cheer wag (CONFETTI and RAINBOW): two bobs swaying one way then the other, about half a second, from rest to rest. */
    @Test fun theCheerWagIsTwoBobsAndASwayFromRestToRest() {
        assertTrue(m.CHEER_WAG_MS in 1 until 700)
        assertEquals(m.CHEER_WAG_MS, m.beatMs(PupMood.CHEER_WAG))
        val f = frames(m.CHEER_WAG_MS) { m.cheerWag(it) }
        assertEquals(PupFrame.REST, f.first())
        assertEquals(PupFrame.REST, f.last())
        assertTrue(f.all { it.lift >= 0f && it.lift <= m.CHEER_WAG_LIFT + 1e-6f })
        // Up on each bob, down between them.
        assertEquals(m.CHEER_WAG_LIFT, m.cheerWag(m.CHEER_WAG_BOB_MS / 2f).lift, 1e-4f)
        assertEquals(0f, m.cheerWag(m.CHEER_WAG_BOB_MS.toFloat()).lift, 1e-4f)
        // One way on the first bob, the other on the second, never past the cheer's sway by much.
        assertTrue(m.cheerWag(m.CHEER_WAG_BOB_MS / 2f).rotation > 3f && m.cheerWag(m.CHEER_WAG_BOB_MS * 1.5f).rotation < -3f)
        assertTrue(f.all { kotlin.math.abs(it.rotation) <= m.CHEER_WAG_DEG })
        // Nothing jumps at either end: the first and last tenths stay close to rest.
        assertTrue((f.take(5) + f.takeLast(5)).all { kotlin.math.abs(it.scaleY - 1f) < 0.01f && kotlin.math.abs(it.rotation) < 0.6f })
    }

    /**
     * 2.11, the step pop's mini hop: over in under 700 ms (inside the 600 ms burst's time, near enough), a small HAPPY: a
     * squash, a stretch up to [PupMotion.POP_JUMP] (half the jump's height or less), back on the ground, a squash ringing out.
     */
    @Test fun thePopIsALittleHopUnderSevenHundredMilliseconds() {
        assertTrue(m.POP_BEAT_MS in 1 until 700)
        assertEquals(m.POP_BEAT_MS, m.beatMs(PupMood.POP))
        assertTrue(m.TIMINGS.any { it.name == "pop" && it.ms == m.POP_BEAT_MS })
        val f = frames(m.POP_BEAT_MS) { m.pop(it) }
        assertEquals(PupFrame.REST, f.first())
        assertEquals(PupFrame.REST, f.last())
        assertEquals(m.POP_JUMP, f.maxOf { it.lift }, 1e-3f)
        assertTrue(m.POP_JUMP <= m.JUMP / 2f)
        assertTrue(f.all { it.lift >= 0f && it.rotation == 0f })
        // Squashed before it leaves the ground, stretched on the way up, squashed again as it lands.
        assertTrue(m.pop(m.POP_SQUASH_MS - 1f).let { it.scaleY < 0.95f && it.lift == 0f })
        assertEquals(m.POP_STRETCH_Y, f.maxOf { it.scaleY }, 0.005f)
        val land = m.POP_SQUASH_MS + m.POP_RISE_MS + m.POP_FALL_MS
        assertEquals(1f - m.POP_LAND_SQUASH, f[land].scaleY, 0.002f)
        assertTrue(f.drop(land).all { it.lift == 0f })
        // It rings out to nothing: the last tenth is within a hair of rest, so it hands back without a jolt.
        assertTrue(f.takeLast(m.POP_BEAT_MS / 10).all { kotlin.math.abs(it.scaleY - 1f) < 0.003f })
    }
}
