package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.14 depth: the MIDI timing offset, its calibration and the way Perform applies it. */
class MidiOffsetTest {
    private val clicks = MidiOffset.clickTimes(1000)

    @Test fun clicksFollowTheCountIn() {
        assertEquals(MidiOffset.CLICKS, clicks.size)
        assertEquals(1000 + 4 * 750L, clicks.first())
        assertEquals(750L, clicks[1] - clicks[0])
        // The whole calibration, count-in and clicks, fits in ten seconds.
        assertEquals(true, clicks.last() - 1000 + MidiOffset.CLICK_MS < 10_000)
    }

    @Test fun theMedianOfTapMinusClick() {
        val taps = clicks.mapIndexed { i, c -> c + listOf(38, 42, 40, 45, 35, 41, 39, 44)[i] }
        assertEquals(40, MidiOffset.calibrate(clicks, taps))
        // Early taps give an early offset; a stray tap a beat away and one wild tap change nothing.
        val early = clicks.map { it - 22 } + listOf(clicks[2] + 375)
        assertEquals(-20, MidiOffset.calibrate(clicks, early))
        val wild = clicks.mapIndexed { i, c -> c + if (i == 5) 230 else 30 }
        assertEquals(30, MidiOffset.calibrate(clicks, wild))
    }

    @Test fun tooFewTapsSayNothingAndTheResultIsHeld() {
        assertNull(MidiOffset.calibrate(clicks, clicks.take(4)))
        assertNull(MidiOffset.calibrate(clicks, emptyList()))
        assertEquals(MidiOffset.MAX, MidiOffset.calibrate(clicks, clicks.map { it + 200 }))
        assertEquals(MidiOffset.MIN, MidiOffset.clamp(-400))
        assertEquals("40 ms late", MidiOffset.label(40))
        assertEquals("25 ms early", MidiOffset.label(-25))
        assertEquals("0 ms", MidiOffset.label(0))
    }

    @Test fun aLateKeyboardIsGradedOnTimeInPerform() {
        // A key that reaches the app 100 ms after it was played: graded at its note's time with the offset, OK without.
        val notes = listOf(Note(60, 1000, 400, Hand.RIGHT, 80), Note(62, 2000, 400, Hand.RIGHT, 80))
        val song = Song("m", "m", 60.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 3000)
        fun run(offset: Int): Recap {
            val t = Transport(song).apply { mode = Mode.PERFORM; play() }
            while (t.positionMs < 1170) t.tick(10.0)
            t.noteInput(60, MidiOffset.latencyMs(offset))
            while (t.positionMs < 2170) t.tick(10.0)
            t.noteInput(62, MidiOffset.latencyMs(offset))
            while (t.playing) t.tick(10.0)
            return t.score!!.recap()
        }
        assertEquals(Recap(0, 2, 0, 170, 2), run(0))
        assertEquals(Recap(2, 0, 0, 70, 2), run(100))
        // Negative: a keyboard whose notes come early is moved later.
        assertEquals(Recap(0, 2, 0, 270, 2), run(-100))
    }
}
