package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeTest {
    @Test fun beatsAndAccentsIn44() {
        val m = Metronome(120.0, 4 to 4)
        assertEquals(500.0, m.beatMs, 1e-9)
        assertEquals(2000.0, m.barMs, 1e-9)
        assertEquals(listOf(true, false, false, false, true), m.clicks(0.0, 2001.0))
        assertEquals(emptyList<Boolean>(), m.clicks(1.0, 499.0))
    }

    @Test fun framesNeverDoubleOrDropAClick() {
        val m = Metronome(97.0, 4 to 4)
        var t = -m.barMs
        var count = 0
        while (t < 10_000) { val next = t + 16.7; count += m.clicks(t, next).size; t = next }
        // Beats from the count-in's first beat up to 10 s: one click each.
        assertEquals(Math.ceil((10_000 + m.barMs) / m.beatMs - 1e-9).toInt(), count)
    }

    @Test fun countInIsOneBarWithItsOwnAccent() {
        val m = Metronome(90.0, 3 to 4)
        val countIn = m.clicks(-m.barMs, 0.0)
        assertEquals(listOf(true, false, false), countIn)
        assertEquals(0, m.beatInBar(-m.barMs))
        assertEquals(2, m.beatInBar(-1.0))
    }

    @Test fun eighthNoteMetersCountEighths() {
        val m = Metronome(60.0, 6 to 8)
        assertEquals(500.0, m.beatMs, 1e-9)
        assertEquals(6, m.clicks(0.0, 3000.0).size)
    }

    @Test fun clickIsShortAndTheAccentStandsOut() {
        val a = clickSamples(true); val b = clickSamples(false)
        assertEquals(48_000 * 35 / 1000, a.size)
        assertTrue(a.maxOf { abs(it) } > b.maxOf { abs(it) })
        assertTrue(a.all { abs(it) <= 1f })
        assertTrue("decays to near silence", abs(a.last()) < 0.02f)
    }
}
