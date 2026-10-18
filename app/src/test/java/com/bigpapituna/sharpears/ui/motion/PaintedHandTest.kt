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

    /**
     * 2.2.1 QA: PRESS_n is the frame with finger n down, in both hands. The left hand's frames are mirrored, so its finger
     * numbers run the other way across the keys: its thumb sits on the high side, its little finger on the low side.
     */
    @Test fun everyPressDropsItsOwnFingerInBothHandsAndTheLeftThumbSitsHigh() {
        for (right in listOf(true, false)) {
            for (f in 1..5) {
                val drops = (1..5).map { g -> handTip(right, HandPose.press(f), g, g, 0f, 0f, w).y - handTip(right, HandPose.REST, g, g, 0f, 0f, w).y }
                assertEquals("press $f right=$right drops finger ${drops.indexOf(drops.max()) + 1}", f, drops.indexOf(drops.max()) + 1)
                assertTrue("press $f right=$right: finger $f goes down", drops[f - 1] > w * 0.15f)
                (1..5).filter { it != f }.forEach { g -> assertEquals("press $f right=$right moves finger $g", 0f, drops[g - 1], w * 0.05f) }
            }
            val thumb = handTip(right, HandPose.REST, 1, 3, 0f, 0f, w).x
            val little = handTip(right, HandPose.REST, 5, 3, 0f, 0f, w).x
            assertTrue("right=$right: the thumb on the ${if (right) "low" else "high"} side", if (right) thumb < little else thumb > little)
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
