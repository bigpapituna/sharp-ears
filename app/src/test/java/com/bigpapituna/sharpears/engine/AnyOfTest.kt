package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 2.1: any-of groups (alternatives, not chords) in the notation, the song and the transport. */
class AnyOfTest {
    private fun song(right: String, left: String = "", bpm: Double = 60.0) = Exercise(right = right, left = left, bpm = bpm).toSong("t", "t")

    /** Ticks until wait mode holds at a group. */
    private fun Transport.toWait() { var n = 0; while (!waiting && playing && n++ < 10_000) tick(10.0) }

    @Test fun alternativesAndRangesParseWithTheDemonstrationKeyFirst() {
        val t = parseTokens("C#4|D#4:2 r:1 C6|C#4..E4:2 C4+E4:1")
        assertEquals(listOf(61, 63), t[0].keys); assertTrue(t[0].anyOf)
        assertTrue(t[1].rest)
        assertEquals("the demonstration key first, then the range without it", listOf(84, 61, 62, 63, 64), t[2].keys)
        assertEquals(3.0, t[2].startBeat, 0.0)
        assertFalse("a chord is not alternatives", t[3].anyOf)
        assertEquals(listOf(60, 64), t[3].keys)
        assertEquals("parseLine gives every key", 2 + 5 + 2, parseLine("C#4|D#4:2 r:1 C6|C#4..E4:2 C4+E4:1", Hand.RIGHT, 500.0).size)
        for (bad in listOf("C4+E4|G4:1", "E4..C4:1", "C8..D8:1")) {
            try { parseTokens(bad); fail("$bad should not parse") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun eachTokenOfAlternativesIsItsOwnGroup() {
        val s = song("C4:1 C#4|D#4:1 E4+G4:1", left = "C3:1")
        assertEquals(3, s.groups.size)
        assertEquals(listOf(false, true, false), s.groups.map { it.anyOf })
        assertEquals("C3 and C4 still strike together", listOf(48, 60), s.groups[0].notes.map { it.midi })
        assertEquals(listOf(61, 63), s.groups[1].notes.map { it.midi })
        assertEquals("every alternative is a note of the song", 6, s.notes.size)
        try { song("C#4|D#4:1", left = "C3:1"); fail("alternatives cannot share their moment with a note") } catch (_: IllegalArgumentException) {}
    }

    @Test fun aChordStillNeedsEveryNote() {
        val t = Transport(song("C4+E4+G4:2 D4:2")).apply { mode = Mode.WAIT; play() }
        t.toWait()
        assertEquals(InputResult.CORRECT, t.noteInput(60))
        assertEquals(InputResult.CORRECT, t.noteInput(64))
        assertTrue("still waiting for G", t.waiting)
        assertEquals(InputResult.GROUP_DONE, t.noteInput(67))
        // And existing songs build exactly as before: no group is any-of.
        assertTrue(COURSE.filter { !com.bigpapituna.sharpears.engine.playground.isPlaygroundLevel(it.id) }.flatMap { it.lessons }.flatMap { it.steps }.mapNotNull { it.exercise }
            .all { ex -> ex.toSong("x", "x").groups.none { it.anyOf } })
    }

    @Test fun anyOneKeyOfAnAnyOfGroupAdvances() {
        val s = song("C#4|D#4:2 F#4|G#4|A#4:2")
        for (first in listOf(61, 63)) {
            val t = Transport(s).apply { mode = Mode.WAIT; play() }
            t.toWait()
            assertEquals(setOf(61, 63), t.expected.map { it.midi }.toSet())
            assertEquals(InputResult.GROUP_DONE, t.noteInput(first))
            assertFalse(t.waiting)
            t.toWait()
            assertEquals(InputResult.GROUP_DONE, t.noteInput(70))
        }
    }

    @Test fun aKeyOutsideTheSetIsASlipThatNamesNoNote() {
        val t = Transport(song("C#4|D#4:2")).apply { mode = Mode.WAIT; play() }
        t.toWait()
        assertEquals(InputResult.WRONG, t.noteInput(62))
        assertTrue("does not advance", t.waiting)
        val r = t.report()
        assertEquals(1, r.wrongKeys)
        assertTrue("no single key of the set was the one missed", r.missed.isEmpty())
        assertEquals(InputResult.GROUP_DONE, t.noteInput(61))
    }

    @Test fun theMicListensForTheSetEachKeyOnItsOwn() {
        val small = Transport(song("C#4|D#4|C#5|D#5:2")).apply { mode = Mode.WAIT; play() }
        small.toWait()
        assertEquals(setOf(61, 63, 73, 75), small.listenFor)
        assertEquals(listOf(setOf(61), setOf(63), setOf(73), setOf(75)), small.listenGroups)
        assertEquals("a hint plays the demonstration key only", listOf(61), small.hintKeys)
        // A chord stays one group for the matcher.
        val chord = Transport(song("C4+E4+G4:2")).apply { mode = Mode.WAIT; play() }
        chord.toWait()
        assertEquals(listOf(setOf(60, 64, 67)), chord.listenGroups)
        assertEquals(listOf(60, 64, 67), chord.hintKeys)
    }

    @Test fun aBigRangeIsListenedForThroughTwelveSpreadKeys() {
        val t = Transport(song("C6|C#4..C8:2")).apply { mode = Mode.WAIT; play() }
        t.toWait()
        assertEquals("every key is still accepted", 48, t.expected.size)
        val mic = t.listenFor
        assertEquals(MIC_ANY_OF_LIMIT, mic.size)
        assertEquals("the demonstration key first", 84, mic.first())
        assertTrue("white keys, spread from one end to the other", mic.drop(1).none(::isBlack) && 62 in mic && 108 in mic)
        assertEquals(InputResult.GROUP_DONE, t.noteInput(97))
        assertEquals(setOf(1, 2, 3), micKeys(listOf(1, 2, 3)))
    }

    @Test fun alternativesOffTheKeyboardOnScreenStillCount() {
        // The phone shows two octaves; a real piano is wider, and the mic hears all of it.
        val t = Transport(song("C6|C#4..C8:2"), playable = 48..72).apply { mode = Mode.WAIT; play() }
        t.toWait()
        assertEquals(InputResult.GROUP_DONE, t.noteInput(100))
    }

    @Test fun onlyTheDemonstrationKeySoundsWhenNobodyPlays() {
        val s = song("C6|C#4..C8:2 G2|A0..B3:2")
        val t = Transport(s).apply { mode = Mode.LISTEN; play() }
        val on = mutableListOf<Int>()
        while (t.playing) t.tick(16.0).filterIsInstance<NoteEvent.On>().forEach { on += it.midi }
        assertEquals(listOf(84, 43), on)
        // Practising one hand, the other hand's any-of group sounds its demonstration key as accompaniment.
        val r = Transport(song("C4:2 r:2", left = "r:2 G2|A0..B3:2")).apply { mode = Mode.WAIT; practice = Practice.RIGHT; play() }
        val heard = mutableListOf<Int>()
        while (r.playing) { r.tick(16.0).filterIsInstance<NoteEvent.On>().forEach { heard += it.midi }; if (r.waiting) r.noteInput(60) }
        assertEquals(listOf(43), heard)
    }

    @Test fun performGradesAnAnyOfGroupOnAnyOneKey() {
        val s = song("C#4|D#4:1 C#4|D#4:1 F#4|G#4|A#4:1 F#4|G#4|A#4:1")
        val t = Transport(s).apply { mode = Mode.PERFORM; play() }
        val keys = listOf(63, 61, 70, 66)
        s.groups.forEachIndexed { i, g ->
            while (t.positionMs < g.startMs) t.tick(minOf(5.0, g.startMs - t.positionMs))
            assertEquals(InputResult.CORRECT, t.noteInput(keys[i]))
        }
        while (t.playing) t.tick(16.0)
        assertEquals(Recap(4, 0, 0, 0, 4), t.score!!.recap())
    }

    @Test fun transposingKeepsAlternativesApart() {
        val s = song("C#4|D#4:2 C4+E4:2")
        val up = s.transposed(2)
        assertEquals(listOf(true, false), up.groups.map { it.anyOf })
        assertEquals(listOf(63, 65), up.groups[0].notes.map { it.midi })
        val t = Transport(up).apply { mode = Mode.WAIT; play() }
        t.toWait()
        assertEquals(InputResult.GROUP_DONE, t.noteInput(65))
        assertNull("listen mode has no next group", Transport(up).apply { mode = Mode.LISTEN }.nextGroup)
    }
}
