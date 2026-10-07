package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardLayoutTest {
    // C3..C6: 22 white keys, 15 black keys, 2200 px wide so each white key is 100 px.
    private val l = KeyboardLayout(48, 84, 2200f, 400f)

    @Test fun counts() {
        assertEquals(22, l.whiteKeys.size)
        assertEquals(15, l.blackKeys.size)
        assertEquals(100f, l.whiteWidth, 0.001f)
    }

    @Test fun hitTest() {
        assertEquals(48, l.keyAt(10f, 390f))               // left edge of C3
        assertEquals(49, l.keyAt(100f, 100f))              // C#3 straddles the C3/D3 boundary, upper part
        assertEquals(48, l.keyAt(80f, 300f))               // below the black key: back on C3
        assertEquals(52, l.keyAt(250f, 100f))              // E3: no black key between E and F
        assertEquals(84, l.keyAt(2199f, 10f))              // last key C6
        assertNull(l.keyAt(2200f, 10f))                    // off the right edge
        for (k in l.whiteKeys + l.blackKeys) assertEquals(noteName(k.midi), k.midi, l.keyAt(k.centerX, k.touchY))
    }

    @Test fun samplePicksNearestWithinTwoSemitones() {
        val available = (21..105 step 4).toList() + 108
        assertEquals(57 to 1f, sampleFor(57, available))
        val (s, rate) = sampleFor(59, available)           // B3: two above A3 (57) or two below C#4 (61), either is fine
        assert(s == 57 || s == 61)
        assertEquals(Math.pow(2.0, (59 - s) / 12.0).toFloat(), rate, 1e-6f)
        for (m in 21..108) assert(kotlin.math.abs(sampleFor(m, available).first - m) <= 2) { "midi $m" }
    }

    @Test fun names() {
        assertEquals("C4", noteName(60))
        assertEquals("A0", noteName(21))
        assertEquals("C#3", noteName(49))
    }
}
