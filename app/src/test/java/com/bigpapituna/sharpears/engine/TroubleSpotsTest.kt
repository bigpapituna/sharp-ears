package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TroubleSpotsTest {
    // 120 bpm in 4/4: a bar is 2000 ms. One note per beat for 8 bars.
    private val notes = (0 until 32).map { Note(60 + it % 5, it * 500L, 450, Hand.RIGHT, 80) }
    private val song = Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 16_000)

    @Test fun waitModeRanksLongHoldsAndWrongKeys() {
        val log = TroubleLog()
        log.waited[2500] = 5200.0          // bar 2: 4 s past a normal hold
        log.waited[3000] = 900.0           // bar 2: a normal reach, free
        log.waited[12_000] = 2700.0        // bar 7: 1.5 s
        repeat(2) { log.wrongAt += 8000.0 } // bar 5: two wrong keys = 3 s
        log.wrongAt += 12_500.0            // bar 7: one more = 3 s in all
        val spots = TroubleSpots.forWait(song, log)
        assertEquals(listOf(2, 5, 7), spots.map { it.bar })          // 4 s, then 3 s (earlier bar wins the tie)
        assertEquals(2000.0, spots[0].startMs, 0.0)
        assertEquals(4000.0, spots[0].endMs, 0.0)
        assertEquals("Hardest bars: 2, 5, 7", TroubleSpots.line(spots))
    }

    @Test fun performRanksMisses() {
        val log = TroubleLog()
        log.missedAt += listOf(6000L, 6500L, 7000L, 500L, 14_000L, 14_500L)
        assertEquals(listOf(4, 8, 1), TroubleSpots.forPerform(song, log).map { it.bar })
        assertEquals(listOf(4), TroubleSpots.forPerform(song, log, count = 1).map { it.bar })
    }

    @Test fun aCleanRunHasNoTroubleSpots() {
        val log = TroubleLog().apply { waited[0] = 800.0; waited[500] = 1100.0 }
        assertTrue(TroubleSpots.forWait(song, log).isEmpty())
        assertNull(TroubleSpots.line(emptyList()))
    }

    @Test fun theTransportLogsHoldsWrongKeysAndMisses() {
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        repeat(200) { t.tick(16.0) }                                   // held 3.2 s at the first note (bar 1)
        t.noteInput(70); t.noteInput(60)                              // one wrong, then right
        assertEquals(1, t.troubles.wrongAt.size)
        assertTrue(t.troubles.waited[0L]!! > 3000.0)
        assertEquals(listOf(1), TroubleSpots.forWait(song, t.troubles).map { it.bar })

        val p = Transport(song).apply { mode = Mode.PERFORM; play() }
        repeat(400) { p.tick(16.0) }                                   // 6.4 s, nothing played: 13 misses so far
        assertEquals(p.score!!.recap().miss, p.troubles.missedAt.size)
        assertEquals(listOf(1, 2, 3), TroubleSpots.forPerform(song, p.troubles).map { it.bar })
    }

    @Test fun storedAsBarNumbers() {
        val spots = listOf(TroubleSpots.spot(song, 12), TroubleSpots.spot(song, 3))
        assertEquals("12,3", TroubleSpots.encode(spots))
        assertEquals(listOf(12, 3), TroubleSpots.decode("12,3"))
        assertEquals(emptyList<Int>(), TroubleSpots.decode(null))
    }

    /** Audit: a stored bar (stale after a shorter file was imported, or from a backup) looped outside the song. */
    @Test fun aStoredBarOutsideTheSongLoopsInsideIt() {
        assertEquals(TroubleSpot(12, 14_000.0, 16_000.0, 0.0), TroubleSpots.spot(song, 12))
        assertEquals(TroubleSpot(0, 0.0, 2000.0, 0.0), TroubleSpots.spot(song, 0))
        assertEquals(TroubleSpot(-3, 0.0, 2000.0, 0.0), TroubleSpots.spot(song, -3))
        val short = song.copy(durationMs = 15_000)
        assertEquals(TroubleSpot(99, 14_000.0, 15_000.0, 0.0), TroubleSpots.spot(short, 99))
        for (bar in TroubleSpots.decode("0,-1,3,40")) TroubleSpots.spot(song, bar).let { assertTrue("$it", it.startMs >= 0 && it.startMs < it.endMs && it.endMs <= song.durationMs) }
    }
}
