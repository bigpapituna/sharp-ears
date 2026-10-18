package com.bigpapituna.sharpears.engine.skin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** A wrong key is yellow within two semitones of an expected note, red further away (FAMILY.md). */
class WrongKeyTest {
    @Test fun distanceClassification() {
        assertEquals(WrongTone.NEAR, WrongKey(61, 1).tone)
        assertEquals(WrongTone.NEAR, WrongKey(62, 2).tone)
        assertEquals(WrongTone.FAR, WrongKey(63, 3).tone)
        assertEquals(WrongTone.FAR, WrongKey(72, 12).tone)        // the right letter an octave off is still far
        assertEquals(WrongTone.NEAR, WrongKey(60, 0).tone)
        assertEquals(WrongTone.NEAR, WrongKey.tone(-2))           // a signed distance from the detector reads the same
        assertEquals(WrongTone.FAR, WrongKey.tone(-3))
    }

    @Test fun nearestExpectedNoteSetsTheDistance() {
        assertEquals(WrongKey(62, 2), WrongKey.of(62, listOf(60)))
        assertEquals(WrongKey(66, 1), WrongKey.of(66, listOf(60, 64, 67)))          // F sharp next to G
        assertEquals(WrongTone.FAR, WrongKey.of(71, listOf(60, 64))!!.tone)
        assertEquals(WrongKey(59, 1), WrongKey.of(59, setOf(60, 72)))
        assertNull(WrongKey.of(60, emptyList()))
    }

    @Test fun flashLastsAboutSixHundredMsWithAShortShake() {
        assertEquals(0f, WrongFlashCurve.glow(-1.0))
        assertTrue(WrongFlashCurve.glow(0.0) > 0.5f)
        assertEquals(1f, WrongFlashCurve.glow(150.0))
        assertTrue(WrongFlashCurve.glow(500.0) in 0.01f..0.3f)
        assertEquals(0f, WrongFlashCurve.glow(600.0))
        assertTrue(WrongFlashCurve.alive(599.0)); assertFalse(WrongFlashCurve.alive(600.0))
        // The shake moves, rings down, and is over well before the glow.
        val early = (0..80).maxOf { abs(WrongFlashCurve.shake(it.toDouble())) }
        val late = (250..319).maxOf { abs(WrongFlashCurve.shake(it.toDouble())) }
        assertTrue(early > 0.5f); assertTrue(late < early * 0.25f)
        assertEquals(0f, WrongFlashCurve.shake(400.0))
    }

    @Test fun reducedMotionHoldsStillAndSteady() {
        for (ms in listOf(0.0, 100.0, 300.0, 590.0)) {
            assertEquals(0f, WrongFlashCurve.shake(ms, still = true))
            assertEquals(1f, WrongFlashCurve.glow(ms, still = true))
        }
        assertEquals(0f, WrongFlashCurve.glow(600.0, still = true))
    }
}
