package com.bigpapituna.sharpears.engine.brief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunTallyTest {
    @Test fun wrongKeysAreNearOrFarByTheNoteWanted() {
        val t = RunTally()
        t.wrong(62, 60)      // two away: a slip
        t.wrong(61, 60)
        t.wrong(65, 60)      // a fourth: far
        t.wrong(70, null)    // nothing wanted: far, no pair
        assertEquals(2, t.near); assertEquals(2, t.far); assertEquals(4, t.wrongKeys)
        assertEquals(listOf(BriefEvent.WrongKey(5, 60, 61, true, 1, "pond-1"), BriefEvent.WrongKey(5, 60, 62, true, 1, "pond-1"),
            BriefEvent.WrongKey(5, 60, 65, false, 1, "pond-1")), t.wrongKeyEvents(5, lessonId = "pond-1"))
    }

    @Test fun onlyTheMostFrequentPairsAreWritten() {
        val t = RunTally()
        repeat(4) { t.wrong(64, 62) }
        for (k in 66..72) t.wrong(k, 67)
        val events = t.wrongKeyEvents(9, songId = "ode")
        assertEquals(RunTally.TOP_PAIRS, events.size)
        assertEquals(BriefEvent.WrongKey(9, 62, 64, true, 4, songId = "ode"), events.first())
    }

    @Test fun eachStuckWaitCountsItsDeepestHint() {
        val t = RunTally()
        t.hint(HintDepth.GLOW); t.hint(HintDepth.POINT); t.waitEnded()     // pointed, then played
        t.waitEnded()                                                     // a wait with no hint
        t.hint(HintDepth.GLOW); t.hint(HintDepth.POINT); t.hint(HintDepth.PLAY); t.waitEnded()
        t.hint(HintDepth.GLOW); t.waitEnded()
        assertEquals(listOf(2, 3, 1), t.hintsUsed)
        repeat(100) { t.hint(1); t.waitEnded() }
        assertEquals(RunTally.MAX_HINTS, t.hintsUsed.size)
    }

    @Test fun triesAddUpAndAClearStartsOver() {
        val a = RunTally().apply { wrong(62, 60); hint(3); waitEnded(); octave(); letGo() }
        val b = RunTally().apply { wrong(62, 60); wrong(50, 60); octave() }
        val sum = a.plus(b)
        assertEquals(2, sum.near); assertEquals(1, sum.far); assertEquals(2, sum.octaves); assertEquals(1, sum.letGos)
        assertEquals(listOf(3), sum.hintsUsed)
        assertEquals(2, sum.wrongKeyEvents(0).first { it.playedMidi == 62 }.count)
        assertEquals(BriefEvent.OctaveHint(0, "x", null, 2), sum.octaveEvent(0, "x"))
        val copy = a.copy(); a.clear()
        assertEquals(0, a.wrongKeys); assertNull(a.octaveEvent(0)); assertEquals(1, copy.wrongKeys)
    }
}
