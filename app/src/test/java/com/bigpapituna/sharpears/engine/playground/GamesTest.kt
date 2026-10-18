package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small rules the Playground's stages draw by. */
class GamesTest {
    private fun keys(s: String) = s.split(' ').map(::noteNumber)

    @Test fun huntsAreNamedByTheirKeys() {
        assertEquals(Hunt.DOG_HOUSE, Hunt.of(parseTokens("$DOG_HOUSES:1").single().keys))
        assertEquals(Hunt.FENCE, Hunt.of(parseTokens("$FENCES:1").single().keys))
        assertEquals(Hunt.C, Hunt.of(parseTokens("$ANY_C:1").single().keys))
        assertNull(Hunt.of(keys("C4 D4")))
        assertNull("one key of a fence is not a fence", Hunt.of(keys("F#4")))
    }

    @Test fun placesOnTheKeyboardShown() {
        assertEquals(listOf(keys("C#3 D#3"), keys("C#4 D#4")), huntPlaces(Hunt.DOG_HOUSE, noteNumber("C3"), noteNumber("B4")))
        assertEquals(listOf(keys("F#3 G#3 A#3"), keys("F#4 G#4 A#4"), keys("F#5 G#5 A#5")), huntPlaces(Hunt.FENCE, noteNumber("C3"), noteNumber("B5")))
        assertEquals(keys("C3 C4 C5").map { listOf(it) }, huntPlaces(Hunt.C, noteNumber("C3"), noteNumber("C5")))
        assertEquals("a group cut off by the edge is not shown", emptyList<List<Int>>(), huntPlaces(Hunt.FENCE, noteNumber("C4"), noteNumber("G4")))
        assertEquals(keys("F#5 G#5 A#5"), huntPlaceOf(Hunt.FENCE, noteNumber("G#5")))
        assertNull(huntPlaceOf(Hunt.FENCE, noteNumber("C#5")))
    }

    @Test fun theFlyingPupShowsTheSideStraightAway() {
        assertEquals(0f, flightHeight(21.0), 0f)
        assertTrue("every low key stays near the grass", (21..59).all { flightHeight(it.toDouble()) <= 0.18f })
        assertTrue("every high key is up in the sky", (61..108).all { flightHeight(it.toDouble()) >= 0.45f })
        assertTrue("middle C in between", flightHeight(60.0) in 0.2f..0.4f)
        assertTrue("higher keys fly higher", (61..95).all { flightHeight(it + 1.0) > flightHeight(it.toDouble()) })
        assertEquals(1f, flightHeight(108.0), 0f)
        val updown = PLAYGROUND.lessons[0].steps.last { it.game == Game.HIGH_LOW }.exercise!!.toSong("p", "p")
        assertEquals(listOf(true, false, true, false, true, false), updown.groups.map(::wantsHigh))
    }
}
