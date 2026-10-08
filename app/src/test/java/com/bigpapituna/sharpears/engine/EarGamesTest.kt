package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarGamesTest {
    @Test fun intervalsStayWithinTheirLevel() {
        for (level in 0..2) {
            val g = IntervalGame(level, seed = 11, rounds = 200)
            var down = 0
            while (!g.finished) {
                val i = g.interval
                assertTrue("level $level: $i", if (level == 0) i in 1..7 else if (level == 1) i in 1..12 else kotlin.math.abs(i) in 1..12)
                assertTrue(g.second in 48..84)
                if (i < 0) down++
                g.skip()
            }
            assertEquals(level == 2, down > 0)
        }
        val a = IntervalGame(1, seed = 5); val b = IntervalGame(1, seed = 5)
        repeat(10) { assertEquals(a.second, b.second); a.skip(); b.skip() }
    }

    @Test fun intervalNamesTheWrongNoteThenShowsTheAnswer() {
        val g = IntervalGame(0, seed = 3)
        assertEquals(listOf(listOf(g.first), listOf(g.second)), g.toPlay())
        assertEquals(Lit.GIVEN, g.lit()[g.first])
        assertEquals(ChordGame.Outcome.IGNORED, g.input(g.first).outcome)                 // replaying the first note is fine
        val wrong = g.input(g.first + 12)
        assertEquals(ChordGame.Outcome.WRONG, wrong.outcome)
        assertEquals("That was an octave up.", wrong.line!!.text)
        g.input(g.first - 1)
        assertEquals(Lit.SHOWN, g.lit()[g.second])                                        // two wrong: shown
        val done = g.input(g.second)
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertFalse(done.clean)
        assertTrue(done.line!!.text.startsWith("Yes: ${withArticle(intervalName(g.interval))} up"))
        assertEquals(0, g.score)
    }

    @Test fun playItBackInOrderWrongOrderStartsOver() {
        val g = PlaybackGame(0, seed = 9)
        val m = g.melody
        assertEquals(3, m.size)
        assertTrue(m.all { it in g.position })
        assertEquals(ChordGame.Outcome.FOUND, g.input(m[0]).outcome)
        val wrong = g.input(if (m[1] == 60) 62 else 60)
        assertEquals(ChordGame.Outcome.WRONG, wrong.outcome)
        assertEquals(0, g.progress); assertEquals(1, g.attempts)
        m.dropLast(1).forEach { g.input(it) }
        val done = g.input(m.last())
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertTrue("one retry, notes never shown", done.clean)
        g.next()
        // Two failed attempts show the notes; finishing then is not clean.
        val n = g.melody
        g.input(if (n[0] == 60) 62 else 60); g.input(if (n[0] == 60) 62 else 60)
        assertTrue(g.notesShown)
        assertTrue(n.all { g.lit()[it] == Lit.SHOWN || it in n })
        n.forEach { g.input(it) }
        assertEquals(1, g.score)
    }

    @Test fun fiveNotesInTheSecondHalfAndEachLevelHasItsPosition() {
        val g = PlaybackGame(2, seed = 4)
        assertEquals(listOf(62, 64, 66, 67, 69), g.position)                               // D position: F sharp
        while (g.round < 5) g.skip()
        assertEquals(5, g.melody.size)
        assertTrue(g.melody.all { it in g.position })
        assertEquals(listOf(67, 69, 71, 72, 74), PlaybackGame(1, seed = 1).position)
        val same = PlaybackGame(0, seed = 8).melody
        assertEquals(same, PlaybackGame(0, seed = 8).melody)
        assertEquals(GameKind.PLAYBACK, newGame(GameKind.PLAYBACK, 0, 1).kind)
        assertEquals(GameKind.HEAR, newGame(GameKind.HEAR, 0, 1).kind)
    }
}
