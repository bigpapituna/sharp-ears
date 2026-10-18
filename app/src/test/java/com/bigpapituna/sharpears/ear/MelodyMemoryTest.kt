package com.bigpapituna.sharpears.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.ear.MAJOR_SCALE
import com.bigpapituna.sharpears.engine.ear.MINOR_SCALE
import com.bigpapituna.sharpears.engine.ear.MelodyMemoryGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyMemoryTest {
    private fun playRight(g: MelodyMemoryGame): ChordGame.Answer {
        var last: ChordGame.Answer? = null
        g.melody.forEach { last = g.input(it) }
        return last!!
    }

    @Test fun theMelodyGrowsByOneNoteEveryRoundAndKeepsItsStart() {
        for (level in 0..2) {
            val g = MelodyMemoryGame(level, seed = 21)
            val start = g.startLength
            var before: List<Int>? = null
            repeat(g.rounds) { r ->
                val m = g.melody
                assertEquals(start + r, m.size)
                before?.let { assertEquals(it, m.take(it.size)) }
                before = m
                g.skip()
            }
            assertTrue(g.finished)
            assertEquals(if (level == 0) 2 else 3, start)
        }
    }

    @Test fun notesStayInTheKeyAndLeapsInTheLevelsLimit() {
        for (level in 0..2) for (seed in 1L..40L) {
            val g = MelodyMemoryGame(level, seed)
            val scale = if (g.minor) MINOR_SCALE else MAJOR_SCALE
            g.full.forEach {
                assertTrue("level $level seed $seed: $it", it in g.pool)
                assertTrue(((it - g.tonicPc) % 12 + 12) % 12 in scale)
                assertTrue(it in 48..84)
            }
            assertTrue("leap ${g.biggestLeap()} at level $level", g.biggestLeap() <= g.maxLeap && g.biggestLeap() >= 1)
        }
    }

    @Test fun levelsRaiseTheRangeAndLeaps() {
        val sizes = (0..2).map { MelodyMemoryGame(it, seed = 1).pool.size }
        assertEquals(5, sizes[0])
        assertTrue(sizes[1] > sizes[0] && sizes[2] > sizes[1])
        assertTrue(MelodyMemoryGame(0, 1).maxLeap < MelodyMemoryGame(1, 1).maxLeap)
        assertTrue(MelodyMemoryGame(1, 1).maxLeap < MelodyMemoryGame(2, 1).maxLeap)
        assertEquals(setOf(0), (1L..30L).map { MelodyMemoryGame(0, it).tonicPc }.toSet())
        assertTrue((1L..60L).any { MelodyMemoryGame(2, it).minor })
        assertFalse((1L..60L).any { MelodyMemoryGame(1, it).minor })
        // Levels 0 and 1 start on the tonic.
        assertTrue((1L..20L).all { MelodyMemoryGame(1, it).let { g -> g.full[0] == g.pool[0] } })
    }

    @Test fun playingItRightScoresAndShowsTheNotesGreen() {
        val g = MelodyMemoryGame(0, seed = 3)
        assertEquals(Lit.GIVEN, g.lit()[g.melody[0]])                        // level 0 shows where to start
        assertEquals(ChordGame.Outcome.FOUND, g.input(g.melody[0]).outcome)
        assertEquals(Lit.FOUND, g.lit()[g.melody[0]])
        assertEquals(setOf(g.melody[1]), g.micNotes())
        val done = g.input(g.melody[1])
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertTrue(done.clean)
        assertTrue(done.line!!.text.startsWith("Yes, first time"))
        assertEquals(1, g.score)
        g.next()
        assertEquals(3, g.melody.size)
    }

    @Test fun aWrongNoteRestartsTheTryAndTwoFailuresShowTheNotes() {
        val g = MelodyMemoryGame(1, seed = 5)
        val m = g.melody
        g.input(m[0])
        val wrongNote = g.pool.first { it != m[1] }
        val w1 = g.input(wrongNote)
        assertEquals(ChordGame.Outcome.WRONG, w1.outcome)
        assertEquals(0, g.progress)
        assertFalse(g.notesShown)
        assertTrue(g.replayOnWrong)
        // A second failed try: the notes are shown.
        val w2 = g.input(g.pool.first { it != m[0] })
        assertEquals(ChordGame.Outcome.WRONG, w2.outcome)
        assertTrue(g.notesShown)
        assertTrue(w2.line!!.text.contains("Here are the notes"))
        assertEquals(Lit.SHOWN, g.lit()[m[0]])
        val done = playRight(g)
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertFalse(done.clean)
        assertEquals(0, g.score)
        assertEquals(0, g.streak)
    }

    @Test fun oneFailedTryStillEarnsThePoint() {
        val g = MelodyMemoryGame(0, seed = 8)
        g.input(g.pool.first { it != g.melody[0] })
        val done = playRight(g)
        assertTrue(done.clean)
        assertEquals(1, g.score)
        assertTrue(done.line!!.text.startsWith("Yes:"))
    }

    @Test fun aWholePerfectGameScoresEveryRound() {
        for (level in 0..2) {
            val g = MelodyMemoryGame(level, seed = 2)
            while (!g.finished) { playRight(g); g.next() }
            assertEquals(10, g.score); assertEquals(10, g.bestStreak)
        }
    }

    @Test fun promptNamesTheLengthAndKey() {
        val g = MelodyMemoryGame(0, seed = 1)
        assertEquals("Play it back: 2 notes in C major", g.prompt().text)
        assertTrue(g.toPlay().size == 2 && g.toPlay().all { it.notes.size == 1 })
    }

    /** The melody as drawn before the walk moved into MelodyWalk (2.1), step for step: every seeded game is unchanged. */
    @Test fun theWalkDrawsTheSameMelodiesAsBefore() {
        for (level in 0..2) for (seed in 1L..60L) {
            val g = MelodyMemoryGame(level, seed)
            val rnd = java.util.Random(seed)
            // Replays the constructor's draws: the key, then the old walk.
            val keys = when (level) { 0 -> 1; 1 -> 4; else -> 8 }
            rnd.nextInt(keys)
            val steps = when (level) { 0 -> listOf(-2, -1, -1, -1, 1, 1, 1, 2); 1 -> listOf(-3, -2, -1, -1, -1, 1, 1, 1, 2, 3); else -> listOf(-4, -3, -2, -2, -1, -1, -1, 1, 1, 1, 2, 2, 3, 4) }
            val pool = g.pool
            var i = if (level < 2) 0 else rnd.nextInt(pool.size)
            val old = buildList {
                add(pool[i])
                repeat(g.rounds + g.startLength - 2) {
                    var next: Int
                    do {
                        val s = steps[rnd.nextInt(steps.size)]
                        next = i + s
                        if (next !in pool.indices) next = i - s
                    } while (next !in pool.indices || next == i)
                    i = next
                    add(pool[i])
                }
            }
            assertEquals("level $level seed $seed", old, g.full)
        }
    }

    @Test fun aWalkNeverRepeatsANoteAndStaysInThePool() {
        val pool = listOf(60, 62, 64, 65, 67)
        val walk = com.bigpapituna.sharpears.engine.ear.MelodyWalk(pool, com.bigpapituna.sharpears.engine.ear.MelodyWalk.stepsFor(0), java.util.Random(4))
        val notes = List(200) { walk.next() }
        assertEquals(60, notes[0])
        assertTrue(notes.all { it in pool })
        assertTrue(notes.zipWithNext().none { (a, b) -> a == b })
        assertTrue(notes.zipWithNext().all { (a, b) -> kotlin.math.abs(pool.indexOf(a) - pool.indexOf(b)) <= 2 })
    }
}
