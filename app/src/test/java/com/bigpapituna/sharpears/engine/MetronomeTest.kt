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

    /**
     * Security review finding 4 (2.8.3): whatever tempo and time signature a song carries, a beat is 25 ms to 12 s and
     * one call clicks at most MAX_CLICKS. A denominator of Int.MIN_VALUE made beatMs negative and the click loop endless.
     */
    @Test fun anyTempoAndTimeSignatureGiveABoundedBeat() {
        val signatures = listOf(4 to Int.MIN_VALUE, 4 to 0, 4 to -4, 4 to 3, 4 to 1_048_576, -56 to 4, 0 to 4, 1_000 to 4, 1 to 32, 32 to 1)
        val tempos = listOf(0.0, -60.0, 1e-9, 1e12, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 120.0)
        for (sig in signatures) for (bpm in tempos) {
            val m = Metronome(bpm, sig)
            assertTrue("$bpm $sig: ${m.beatMs}", m.beatMs in 25.0..12_000.0)
            assertTrue("$bpm $sig", m.beatsPerBar in 1..32)
            assertTrue("$bpm $sig", m.clicks(0.0, 16.7).size <= 1)
            assertTrue("$bpm $sig", m.clicks(-1e18, 1e18).isEmpty())          // millions of beats in one call: none, not a burst
            assertTrue("$bpm $sig", m.clicks(1e15, 1e15 + 10.0).isEmpty())    // a time no song reaches
            assertTrue("$bpm $sig", m.clicks(0.0, Double.NaN).isEmpty() && m.clicks(Double.NEGATIVE_INFINITY, 0.0).isEmpty())
            assertTrue("$bpm $sig", m.beatInBar(1_000.0) in 0 until m.beatsPerBar)
        }
        // A signature music does not have counts as 4/4.
        val odd = Metronome(120.0, 3 to Int.MIN_VALUE)
        assertEquals(4, odd.beatsPerBar)
        assertEquals(500.0, odd.beatMs, 1e-9)
        assertEquals(25.0, Metronome(300.0, 1 to 32).beatMs, 1e-9)
        assertEquals(12_000.0, Metronome(20.0, 1 to 1).beatMs, 1e-9)
        assertEquals(Metronome.MAX_CLICKS, Metronome(300.0, 1 to 32).clicks(0.0, Metronome.MAX_CLICKS * 25.0).size)
        // Past 2.3e20 ms the beat number passed Long.MAX_VALUE, wrapped and the loop spun for ever.
        assertTrue(Metronome(20.0, 1 to 1).clicks(3e20, 3e20 + 65_536.0).isEmpty())
    }

    @Test fun clickIsShortAndTheAccentStandsOut() {
        val a = clickSamples(true); val b = clickSamples(false)
        assertEquals(48_000 * 35 / 1000, a.size)
        assertTrue(a.maxOf { abs(it) } > b.maxOf { abs(it) })
        assertTrue(a.all { abs(it) <= 1f })
        assertTrue("decays to near silence", abs(a.last()) < 0.02f)
    }
}
