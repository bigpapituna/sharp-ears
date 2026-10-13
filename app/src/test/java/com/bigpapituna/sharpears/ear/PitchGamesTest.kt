package com.bigpapituna.sharpears.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.ear.Answering
import com.bigpapituna.sharpears.engine.ear.PerfectPitchGame
import com.bigpapituna.sharpears.engine.ear.ScaleDegreeGame
import com.bigpapituna.sharpears.engine.ear.pitchLabel
import com.bigpapituna.sharpears.engine.ear.solfege
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchGamesTest {
    @Test fun perfectPitchLevelsSetTheNotesAndTheButtons() {
        val white = setOf(0, 2, 4, 5, 7, 9, 11)
        val l0 = PerfectPitchGame(0, seed = 1, rounds = 100)
        val l1 = PerfectPitchGame(1, seed = 1, rounds = 100)
        val l2 = PerfectPitchGame(2, seed = 1, rounds = 100)
        assertEquals(7, l0.choices().size); assertEquals(12, l1.choices().size); assertEquals(12, l2.choices().size)
        val seen = listOf(l0, l1, l2).map { g -> val s = mutableSetOf<Int>(); while (!g.finished) { s += g.target; g.skip() }; s }
        assertTrue(seen[0].all { it % 12 in white && it in 60..71 })
        assertTrue(seen[1].any { it % 12 !in white } && seen[1].all { it in 60..71 })
        assertTrue(seen[2].any { it > 71 } && seen[2].all { it in 60..83 })
        assertEquals(Answering.KEYS_AND_CHOICES, l0.answering)
    }

    @Test fun perfectPitchNeverRepeatsTheNoteNameTwiceInARow() {
        val g = PerfectPitchGame(2, seed = 4, rounds = 200)
        var prev = -1
        while (!g.finished) { assertNotEquals(prev, g.target % 12); prev = g.target % 12; g.skip() }
    }

    @Test fun perfectPitchChoiceRightAndWrong() {
        val g = PerfectPitchGame(1, seed = 2)
        val pc = g.target % 12
        assertEquals(pc, g.answerId())
        assertEquals(listOf(g.target), g.toPlay().single().notes)
        val ok = g.choose(pc)
        assertEquals(ChordGame.Outcome.DONE, ok.outcome); assertTrue(ok.clean)
        assertEquals(1, g.score)
        assertEquals(ChordGame.Outcome.IGNORED, g.choose(pc).outcome)
        g.next()
        val miss = g.choose((g.target % 12 + 1) % 12)
        assertFalse(miss.clean)
        assertTrue(miss.line!!.text, miss.line!!.text.startsWith("That was ${pitchLabel(g.target)}, not "))
        assertTrue("teaches where the note lives", miss.line!!.text.length > 30)
        assertEquals(0, g.streak)
        assertEquals(Lit.FOUND, g.lit()[g.target])
    }

    @Test fun perfectPitchKeysAcceptAnyOctaveAndShowTheNoteAfterTwoWrong() {
        val g = PerfectPitchGame(0, seed = 6)
        val other = g.pool.first { it % 12 != g.target % 12 }
        assertEquals(ChordGame.Outcome.WRONG, g.input(other).outcome)
        assertTrue(g.lit().isEmpty())
        val second = g.input(other)
        assertTrue(second.line!!.text.endsWith("Here is the note."))
        assertEquals(Lit.SHOWN, g.lit()[g.target])
        val done = g.input(g.target + 12)                                    // an octave up still counts
        assertEquals(ChordGame.Outcome.DONE, done.outcome)
        assertFalse(done.clean)
        assertTrue(done.line!!.text.contains(pitchLabel(g.target)))
        // The mic listens for the note in every octave of the keyboard, one group per key.
        val h = PerfectPitchGame(1, seed = 3)
        assertEquals(setOf(h.target % 12), h.micNotes().map { it % 12 }.toSet())
        assertTrue(h.micNotes().size >= 3)
        assertTrue(h.micGroups().all { it.size == 1 })
    }

    @Test fun scaleDegreeLevelsAndKeys() {
        val l0 = ScaleDegreeGame(0, seed = 1, rounds = 100)
        val ds = mutableSetOf<Int>(); val keys = mutableSetOf<Pair<Int, Boolean>>()
        while (!l0.finished) { ds += l0.degree; keys += l0.tonicPc to l0.minor; l0.skip() }
        assertEquals(setOf(0, 1, 2, 3, 4), ds); assertEquals(setOf(0 to false), keys)
        assertEquals(5, ScaleDegreeGame(0, 1).choices().size)
        val l1 = ScaleDegreeGame(1, seed = 1, rounds = 100)
        val ds1 = mutableSetOf<Int>(); val keys1 = mutableSetOf<Pair<Int, Boolean>>()
        while (!l1.finished) { ds1 += l1.degree; keys1 += l1.tonicPc to l1.minor; l1.skip() }
        assertEquals((0..6).toSet(), ds1); assertTrue(keys1.size > 3 && keys1.none { it.second })
        val l2 = ScaleDegreeGame(2, seed = 1, rounds = 100)
        val keys2 = mutableSetOf<Pair<Int, Boolean>>()
        while (!l2.finished) { keys2 += l2.tonicPc to l2.minor; l2.skip() }
        assertTrue(keys2.any { it.second } && keys2.any { !it.second })
    }

    @Test fun scaleDegreeTargetsAreTheRightStepOfTheKey() {
        for (level in 0..2) {
            val g = ScaleDegreeGame(level, seed = 9, rounds = 100)
            while (!g.finished) {
                val steps = if (g.minor) listOf(0, 2, 3, 5, 7, 8, 10) else listOf(0, 2, 4, 5, 7, 9, 11)
                assertEquals((g.tonicPc + steps[g.degree]) % 12, g.target % 12)
                assertTrue(g.target in 48..84)
                assertEquals(g.degree, g.answerId())
                g.skip()
            }
        }
    }

    @Test fun theKeyIsSetByAChordThenTheScale() {
        val g = ScaleDegreeGame(1, seed = 3)
        val intro = g.intro()
        assertEquals(3, intro.first().notes.size)                            // the tonic triad
        assertEquals(g.tonicPc, intro.first().notes.first() % 12)
        assertEquals(8, intro.drop(1).size)                                  // the scale up to the octave
        assertEquals(intro.first().notes.first(), intro[1].notes.single())
        assertEquals(intro.first().notes.first() + 12, intro.last().notes.single())
        assertEquals(listOf(g.target), g.toPlay().single().notes)
        assertEquals(Lit.GIVEN, g.lit()[intro.first().notes.first()])      // the tonic is lit at level 2
    }

    @Test fun scaleDegreeChoicesAndExplanations() {
        val g = ScaleDegreeGame(2, seed = 11, rounds = 50)
        while (!g.minor) g.skip()
        assertEquals(listOf("do", "re", "me", "fa", "sol", "le", "te"), g.choices().map { it.label })
        assertEquals((1..7).map { "$it" }, g.choices().map { it.sub })
        assertTrue(g.lit().keys.none { it == g.tonicPc })                    // no tonic hint at level 3
        val wrong = (g.degree + 1) % 7
        val miss = g.choose(wrong)
        assertFalse(miss.clean)
        assertTrue(miss.line!!.text, miss.line!!.text.contains("That was ${solfege(g.degree, true)}, step ${g.degree + 1} of ${g.keyLabel}"))
        assertTrue(miss.line!!.text.contains("not ${solfege(wrong, true)}"))
        g.next()
        val ok = g.choose(g.degree)
        assertTrue(ok.clean)
        assertTrue(ok.line!!.text.startsWith("Yes: ${solfege(g.degree, g.minor)}, step ${g.degree + 1} of "))
    }

    @Test fun scaleDegreeKeysAreGradedByPitchClass() {
        val g = ScaleDegreeGame(0, seed = 5)
        val notInKey = (0..11).first { (it - g.tonicPc + 12) % 12 !in listOf(0, 2, 4, 5, 7, 9, 11) }
        val a = g.input(60 + notInKey)
        assertEquals(ChordGame.Outcome.WRONG, a.outcome)
        assertTrue(a.line!!.text, a.line!!.text.contains("is not in C major"))
        val other = (g.degree + 1) % 5
        val b = g.input(60 + listOf(0, 2, 4, 5, 7)[other])
        assertEquals(ChordGame.Outcome.WRONG, b.outcome)
        assertTrue(b.line!!.text.startsWith("That was ${solfege(other, false)}"))
        assertEquals(Lit.SHOWN, g.lit()[g.target])                           // two wrong: shown
        val done = g.input(g.target - 12)
        assertEquals(ChordGame.Outcome.DONE, done.outcome); assertFalse(done.clean)
    }
}
