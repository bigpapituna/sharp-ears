package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordGamesTest {
    @Test fun sameSeedSameRounds() {
        val a = ChordGame(GameKind.HEAR, 1, seed = 7); val b = ChordGame(GameKind.HEAR, 1, seed = 7)
        repeat(10) {
            assertEquals(a.target, b.target)
            a.skip(); b.skip()
        }
        assertTrue(a.finished)
    }

    @Test fun buildStartsWithTheRootAndFinishesWithThirdAndFifthInAnyOrder() {
        val g = ChordGame(GameKind.BUILD, 1, seed = 3)
        val c = g.target
        assertEquals(setOf(c.root % 12), g.found.toSet())
        assertEquals("Build ${chordTitle(c)}", g.prompt().text)
        val fifth = 60 + (c.root + c.type.intervals[2]) % 12
        val third = 48 + (c.root + c.type.intervals[1]) % 12          // any octave
        assertEquals(ChordGame.Outcome.FOUND, g.input(fifth).outcome)
        assertEquals(ChordGame.Outcome.IGNORED, g.input(fifth + 12).outcome)
        val done = g.input(third)
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertTrue(done.clean)
        assertEquals(1, g.score); assertEquals(1, g.streak)
    }

    @Test fun buildNamesTheWrongNoteAndHintsAfterTwo() {
        val g = ChordGame(GameKind.BUILD, 0, seed = 1)
        // Force a known chord: walk rounds until D minor comes up.
        while (g.target != Chord(2, ChordType.MINOR)) g.skip().also { if (g.finished) error("no D minor in the C pool draw") }
        val first = g.input(63)                                        // E flat
        assertEquals(ChordGame.Outcome.WRONG, first.outcome)
        assertEquals("Eb is not in D minor.", first.line!!.text)
        assertEquals("E flat is not in D minor.", first.line!!.spoken)
        assertEquals("Eb is not in D minor. The third is F.", g.input(63).line!!.text)
        g.input(65); val done = g.input(69)
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertFalse("a wrong note costs the point", done.clean)
        assertEquals(0, g.score)
    }

    @Test fun hearShowsTheNotesAfterTwoWrongs() {
        val g = ChordGame(GameKind.HEAR, 0, seed = 5)
        val wrong = (0..11).first { it !in g.target.pitchClasses } + 60
        g.input(wrong); assertFalse(g.notesShown)
        g.input(wrong); assertTrue(g.notesShown)
        g.target.pitchClasses.forEach { g.input(it + 72) }
        assertTrue(g.roundOver)
        assertEquals(0, g.score)
    }

    @Test fun majorOrMinorTakesAnyChordOfTheRightKind() {
        val g = ChordGame(GameKind.MAJOR_MINOR, 0, seed = 11)
        val t = g.target
        // Answer with the right quality on a different root: F# (6) plus intervals.
        val answer = Chord(6, t.type).voicing(0, 60)
        var last: ChordGame.Answer? = null
        answer.forEachIndexed { i, m -> last = g.input(m, nowMs = i * 100L) }
        assertEquals(ChordGame.Outcome.DONE, last!!.outcome)
        assertTrue(last!!.clean)
        val expected = if (t.type == ChordType.MINOR) "Yes. That was ${chordTitle(t)}: the middle note is a half step lower than in ${chordTitle(Chord(t.root, ChordType.MAJOR))}."
            else "Yes. That was ${chordTitle(t)}: the middle note is a half step higher than in ${chordTitle(Chord(t.root, ChordType.MINOR))}."
        assertEquals(expected, last!!.line!!.text)
    }

    @Test fun majorOrMinorWrongAnswerAndExplanation() {
        val g = ChordGame(GameKind.MAJOR_MINOR, 0, seed = 2)
        while (g.target.type != ChordType.MINOR) g.skip()
        val e = g.target
        val wrong = Chord(0, ChordType.MAJOR).voicing(0, 60)
        var last: ChordGame.Answer? = null
        wrong.forEach { last = g.input(it, 0) }
        assertFalse(last!!.clean)
        assertEquals("That was ${chordTitle(e)}: the middle note is a half step lower than in ${chordTitle(Chord(e.root, ChordType.MAJOR))}.", last!!.line!!.text)
    }

    @Test fun majorOrMinorIgnoresStaleKeysAndRejectsOtherShapes() {
        val g = ChordGame(GameKind.MAJOR_MINOR, 0, seed = 4)
        g.input(60, 0); g.input(62, 100)
        assertEquals(ChordGame.Outcome.WRONG, g.input(63, 200).outcome)   // C, D, Eb is no triad
        g.input(61, 0); g.input(66, 5000)                                 // C# went stale
        assertEquals(ChordGame.Outcome.FOUND, g.input(69, 5100).outcome)
    }

    @Test fun sevenLevelAsksSevenOrMajorSeven() {
        val g = ChordGame(GameKind.MAJOR_MINOR, 1, seed = 9)
        assertTrue(g.target.type == ChordType.DOM7 || g.target.type == ChordType.MAJ7)
        assertEquals("Seven, or major seven?", g.prompt().spoken)
        g.target.voicing(0, 48).forEach { g.input(it, 0) }
        assertEquals(1, g.score)
    }

    /** Audit: Hear it served C augmented after A flat augmented, the same three keys, as a new round. */
    @Test fun aRoundNeverAsksForTheSameKeysAgain() {
        for (kind in listOf(GameKind.BUILD, GameKind.HEAR, GameKind.MAJOR_MINOR)) for (level in kind.levels.indices) for (seed in 1L..200L) {
            val g = ChordGame(kind, level, seed = seed)
            var prev = g.target
            repeat(9) {
                g.skip()
                assertTrue("$kind $level seed $seed: ${chordTitle(prev)} then ${chordTitle(g.target)}", g.target.pitchClasses != prev.pitchClasses)
                prev = g.target
            }
        }
    }

    @Test fun aRoundOfTenEnds() {
        val g = ChordGame(GameKind.BUILD, 2, seed = 1)
        repeat(10) { g.target.pitchClasses.forEach { pc -> g.input(60 + pc) }; g.next() }
        assertTrue(g.finished)
        assertEquals(10, g.score); assertEquals(10, g.bestStreak)
        assertEquals(ChordGame.Outcome.IGNORED, g.input(60).outcome)
    }
}
