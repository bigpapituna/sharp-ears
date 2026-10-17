package com.bigpapituna.sharpears.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintedHandTest {
    private val w = 40f

    @Test fun anchorFingerLandsOnItsKeyInEveryPose() {
        for (right in listOf(true, false)) for (f in 1..5) {
            val tip = handTip(right, HandPose.REST, f, f, 300f, 120f, w)
            assertEquals(300f, tip.x, 0.01f); assertEquals(120f, tip.y, 0.01f)
            // Pressing keeps the finger over its key (it only drops toward the player).
            val down = handTip(right, HandPose.press(f), f, f, 300f, 120f, w)
            assertEquals("finger $f right=$right", 300f, down.x, w * 0.3f)
            assertTrue(down.y >= 120f - 0.5f)
        }
    }

    @Test fun restingFingersSitAboutAWhiteKeyApart() {
        for (right in listOf(true, false)) {
            val xs = (1..5).map { handTip(right, HandPose.REST, it, 3, 0f, 0f, w).x }
            val dir = if (right) 1f else -1f
            for (f in 1 until 4) assertEquals(w, dir * (xs[f + 1] - xs[f]), w * 0.15f)
            assertTrue(dir * (xs[1] - xs[0]) > 0f)
        }
    }

    @Test fun madePressesDropTheirFinger() {
        for (f in listOf(2, 4)) {
            val rest = handTip(true, HandPose.REST, f, 3, 0f, 0f, w)
            val down = handTip(true, HandPose.press(f), f, 3, 0f, 0f, w)
            assertTrue(down.y - rest.y > w * 0.3f)
        }
    }
}
