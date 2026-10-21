package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

/** Audit (docs/AUDIT-ENGINE.md): the transport and its scoring at their edges. */
class TransportEdgeTest {
    /** Eight quarter notes at 120 bpm (500 ms apart), then a C major chord; the song ends at 5000. */
    private val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72).mapIndexed { i, m -> Note(m, i * 500L, 450, Hand.RIGHT, 80) } +
        listOf(60, 64, 67).map { Note(it, 4000, 900, Hand.RIGHT, 80) }
    private val song = Song("e", "e", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 5000)

    private fun Transport.runTo(ms: Double) { var n = 0; while (positionMs < ms - 1e-9 && playing && n++ < 100_000) tick(16.0) }

    /**
     * The transport trusted the loop it was given: B before A jumped forward to A at B and never looped again, and a B
     * past the song's end never wrapped (the song stopped; Play then started from the top, not A). The player's own
     * buttons cannot set either today; the transport no longer depends on that.
     */
    @Test fun aLoopRunningBackwardsIsNoLoopAndOneEndingPastTheSongWrapsAtTheEnd() {
        val back = Transport(song).apply { mode = Mode.LISTEN; loop = 3000.0..1000.0; play() }
        back.runTo(2000.0)
        assertTrue("no jump ahead: ${back.positionMs}", back.positionMs in 1900.0..2100.0)
        back.runTo(6000.0)
        assertEquals(0, back.passes); assertFalse(back.playing)

        val long = Transport(song).apply { mode = Mode.LISTEN; loop = 2000.0..9000.0; play() }
        long.runTo(4990.0)
        repeat(20) { long.tick(16.0) }
        assertEquals(1, long.passes)
        assertTrue(long.playing && long.positionMs in 2000.0..2400.0)

        val fromZero = Transport(song).apply { mode = Mode.LISTEN; loop = 0.0..1000.0; play() }
        repeat(70) { fromZero.tick(16.0) }
        assertEquals(1, fromZero.passes)
        assertTrue(fromZero.positionMs < 200)

        val pastTheEnd = Transport(song).apply { mode = Mode.LISTEN; loop = 6000.0..7000.0; play() }
        pastTheEnd.runTo(6000.0)
        assertEquals(0, pastTheEnd.passes); assertFalse(pastTheEnd.playing)
    }

    /** Perform scores song time: changing the speed while it plays keeps a player on the beat at 100%. */
    @Test fun aSpeedChangeMidRunKeepsTheScore() {
        val t = Transport(song).apply { mode = Mode.PERFORM; play() }
        var next = 0
        var frames = 0
        while (t.playing) {
            if (frames++ % 40 == 0) t.speed = if (t.speed == 1.0) 0.5 else 1.0
            t.tick(16.0)
            val g = song.groups.getOrNull(next)
            if (g != null && t.positionMs >= g.startMs) { g.notes.forEach { t.noteInput(it.midi) }; next++ }
        }
        val r = t.score!!.recap()
        assertEquals(song.groups.size, r.good)
        assertEquals(100, r.accuracy)
    }

    /** Perform at its edges: everything far too early is a miss each; extra wrong keys cost nothing; no notes, no pass. */
    @Test fun performScoringAtItsEdges() {
        val early = Transport(song).apply { mode = Mode.PERFORM; play() }
        var next = 1                                                                  // the first group cannot come early: it is the start
        while (early.playing) {
            early.tick(16.0)
            val g = song.groups.getOrNull(next)
            if (g != null && early.positionMs >= g.startMs - OK_MS - 100) { assertEquals(InputResult.WRONG, early.noteInput(g.notes[0].midi)); next++ }
        }
        assertEquals(Recap(0, 0, song.groups.size, 0, 0), early.score!!.recap())

        val extra = Transport(song).apply { mode = Mode.PERFORM; play() }
        val rnd = Random(4)
        next = 0
        while (extra.playing) {
            extra.tick(16.0)
            if (rnd.nextInt(3) == 0) extra.noteInput(30 + rnd.nextInt(10))          // keys the song never has
            val g = song.groups.getOrNull(next)
            if (g != null && extra.positionMs >= g.startMs) { extra.noteInput(g.notes[0].midi); next++ }
        }
        assertEquals(100, extra.score!!.recap().accuracy)
        assertTrue(extra.report().wrongKeys > 0)

        assertEquals(0, PerformScore(emptyList(), { true }).recap().accuracy)
        assertFalse(Step(StepKind.PERFORM, "x", Exercise(right = "C4:1")).passed(PerformScore(emptyList(), { true }).recap()))
    }

    /** Wait mode: a key pressed twice is one key (a chord still needs its other notes); a key 300 ms early is too early. */
    @Test fun waitModeTakesEachKeyOnceAndIgnoresTheTooEarly() {
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        var n = 0
        while (t.nextGroup?.startMs != 4000L && n++ < 10_000) { t.tick(16.0); if (t.waiting) t.expected.forEach { t.noteInput(it.midi) } }
        while (t.positionMs < 3700 && n++ < 10_000) t.tick(16.0)
        assertEquals(InputResult.IGNORED, t.noteInput(60))                 // 300 ms before the chord: not yet, and not wrong
        while (t.positionMs < 3850 && n++ < 10_000) t.tick(16.0)
        for (k in listOf(60, 60, 64, 64)) assertEquals(InputResult.CORRECT, t.noteInput(k))   // early enough, each twice
        while (!t.waiting && n++ < 10_000) t.tick(16.0)
        assertEquals(listOf(67), t.expected.map { it.midi })
        assertEquals(InputResult.GROUP_DONE, t.noteInput(67))
        assertEquals(0, t.report().wrongKeys)
    }

    /** Transposed to the piano's edges, a song stays on its 88 keys and still plays through, every group finishable. */
    @Test fun transposingToTheEdgesStaysOnThePianoAndPlaysThrough() {
        val edge = listOf(Note(104, 0, 400, Hand.RIGHT, 80), Note(106, 0, 400, Hand.RIGHT, 80), Note(108, 0, 400, Hand.RIGHT, 80),
            Note(21, 500, 400, Hand.LEFT, 80), Note(23, 500, 400, Hand.LEFT, 80), Note(100, 1000, 400, Hand.RIGHT, 80))
        val s = Song("x", "x", 120.0, 4 to 4, edge, groupOnsets(edge), emptyList(), 1500)
        for (by in listOf(-30, -6, 6, 30)) {
            val moved = s.transposed(by)
            assertTrue("$by", moved.notes.all { it.midi in 21..108 })
            val t = Transport(moved).apply { mode = Mode.WAIT; play() }
            var done = 0; var n = 0
            while (t.playing && n++ < 10_000) {
                t.tick(16.0)
                if (t.waiting) for (k in t.expected.map { it.midi }.distinct()) if (t.noteInput(k) == InputResult.GROUP_DONE) done++
            }
            assertFalse("$by: stuck at ${t.positionMs}", t.playing)
            assertEquals("$by", moved.groups.size, done)
        }
    }

    /** Auto speed only climbs with a better run, stays 25..100, and nudges once a day. */
    @Test fun autoSpeedIsMonotoneBoundedAndNudgesOnceADay() {
        val rnd = Random(11)
        val day = LocalDate.of(2026, 10, 20)
        repeat(2000) {
            val prev = if (rnd.nextBoolean()) null else AutoSpeed(rnd.nextInt(76) + 25, if (rnd.nextBoolean()) day else day.minusDays(1))
            val eff = rnd.nextInt(90) + 20; val acc = rnd.nextInt(101)
            val a = AutoSpeed.afterRun(prev, eff, acc, day)
            assertTrue(a.percent in AutoSpeed.MIN..AutoSpeed.MAX)
            assertTrue(AutoSpeed.afterRun(prev, eff + 1, acc, day).percent >= a.percent)
            assertTrue(AutoSpeed.afterRun(prev, eff, minOf(100, acc + 10), day).percent >= a.percent)
            val again = AutoSpeed.afterRun(a, eff, acc, day)
            assertTrue("one nudge a day", again.percent <= a.percent.coerceAtLeast(eff.coerceIn(AutoSpeed.MIN, AutoSpeed.MAX)))
        }
        val song = 30_000.0
        assertTrue((1..200).map { AutoSpeed.effectivePercent(song, song * 100 / it)!! }.zipWithNext().all { (x, y) -> y >= x })
    }
}
