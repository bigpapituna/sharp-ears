package com.bigpapituna.sharpears.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HitLightsTest {
    @Test fun comboCountsRightNotesAndBreaksOnAWrongOne() {
        val l = HitLights()
        repeat(6) { l.hit(60, Color.Red, now = 1_000L + it) }
        assertEquals(6, l.combo)
        l.wrong(now = 2_000L, breaksCombo = false)       // Perform: a wrong key alone does not break it
        assertEquals(6, l.combo)
        l.wrong(now = 3_000L)
        assertEquals(0, l.combo)
    }

    @Test fun streakFollowsAnOutsideCount() {
        val l = HitLights()
        l.streak(7, now = 1L)
        assertEquals(7, l.combo)
        l.streak(0, now = 2L)
        assertEquals(0, l.combo)
        l.hit(60, Color.Red, now = 3L); l.resetCombo()
        assertEquals(0, l.combo)
    }

    @Test fun everythingSettlesAfterItsLifetime() {
        val l = HitLights()
        val t0 = 1_000_000_000L
        repeat(6) { l.hit(60, Color.Red, grade = HitLights.PERFECT, now = t0) }
        l.wrong(now = t0)                                  // shatters the shown combo
        assertTrue(l.prune(t0 + 100_000_000L))
        assertFalse(l.prune(t0 + 2_000_000_000L))
        assertTrue(l.items.isEmpty())
    }

    @Test fun aTouchGlowsWithoutTouchingTheCombo() {
        val l = HitLights()
        val t0 = 1_000_000_000L
        repeat(5) { l.hit(60, Color.Red, now = t0) }
        l.touch(64, Color.Blue, now = t0)
        assertEquals(5, l.combo)
        assertEquals(HitLights.Kind.TOUCH, l.items.last().kind)
        assertTrue(l.prune(t0 + (HitLights.TOUCH_MS * 1_000_000 / 2).toLong()))
        l.prune(t0 + (HitLights.TOUCH_MS * 1_000_000).toLong() + 1)
        assertTrue(l.items.none { it.kind == HitLights.Kind.TOUCH })
    }

    @Test fun manySparksStayInTheirPool() {
        val l = HitLights()
        repeat(200) { l.hit(60 + it % 12, Color.Red, now = 1_000L + it) }   // far past the pool size, no growth
        assertTrue(l.items.size <= 16)
    }
}
