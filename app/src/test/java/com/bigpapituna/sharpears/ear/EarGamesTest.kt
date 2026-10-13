package com.bigpapituna.sharpears.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.ear.Answering
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.ear.EarTrainer
import com.bigpapituna.sharpears.engine.ear.MelodyMemoryGame
import com.bigpapituna.sharpears.engine.ear.RhythmTapGame
import com.bigpapituna.sharpears.engine.ear.newTrainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every game at every level, played through the way the generic screen drives it. */
class EarGamesTest {
    private fun cheat(g: EarTrainer): ChordGame.Answer = when (g) {
        is RhythmTapGame -> g.grade(g.pattern.noteTimesMs)
        is MelodyMemoryGame -> g.melody.map { g.input(it) }.last()
        else -> if (g.choices().isNotEmpty()) g.choose(g.answerId()!!) else error("no cheat for ${g.kind}")
    }

    @Test fun everyGameAtEveryLevelProducesTenValidRounds() {
        for (kind in EarKind.entries) for (level in 0..2) for (seed in 1L..5L) {
            val g = newTrainer(kind, level, seed)
            assertEquals(kind, g.kind); assertEquals(level, g.level); assertEquals(10, g.rounds)
            assertEquals(3, kind.levels.size)
            var rounds = 0
            while (!g.finished) {
                assertTrue("$kind $level prompt", g.prompt().text.isNotBlank() && g.prompt().spoken.isNotBlank())
                if (g.answering != Answering.TAPS) assertTrue("$kind $level plays something", g.toPlay().isNotEmpty())
                if (g.answering == Answering.CHOICES || g.answering == Answering.KEYS_AND_CHOICES) {
                    assertTrue(g.choices().size >= 3)
                    assertTrue(g.answerId() in g.choices().map { it.id })
                }
                if (g.answering == Answering.KEYS || g.answering == Answering.KEYS_AND_CHOICES) {
                    assertTrue(g.micNotes().isNotEmpty()); assertTrue(g.micGroups().isNotEmpty())
                    assertTrue(g.micNotes().all { it in 36..96 })
                }
                assertTrue(g.describe().isNotBlank())
                assertEquals(rounds, g.round)
                g.skip(); rounds++
            }
            assertEquals(10, rounds)
            assertEquals(0, g.score)
        }
    }

    @Test fun anOracleScoresTenOutOfTenAndABestStreakOfTen() {
        for (kind in listOf(EarKind.RHYTHM, EarKind.MELODY, EarKind.PITCH, EarKind.DEGREE, EarKind.PROGRESSION)) for (level in 0..2) {
            val g = newTrainer(kind, level, seed = 13)
            while (!g.finished) {
                val a = cheat(g)
                assertEquals("$kind $level", ChordGame.Outcome.DONE, a.outcome)
                assertTrue(a.clean)
                assertTrue(a.line!!.text.isNotBlank())
                assertTrue(g.roundOver)
                g.next()
            }
            assertEquals(10, g.score); assertEquals(10, g.bestStreak)
        }
    }

    @Test fun roundsOnlyAdvanceAfterTheRoundIsOver() {
        val g = newTrainer(EarKind.PITCH, 0, 1)
        g.next()
        assertEquals(0, g.round)
        g.choose(g.answerId()!!)
        g.next()
        assertEquals(1, g.round)
        g.skip()
        assertEquals(2, g.round)
        assertEquals(1, g.score); assertEquals(0, g.streak); assertEquals(1, g.bestStreak)
    }

    @Test fun noGameCopyUsesDashes() {
        val bad = Regex("[\u2013\u2014]")
        for (kind in EarKind.entries) {
            assertFalse(kind.title + kind.howTo + kind.levels, bad.containsMatchIn(kind.title + kind.howTo + kind.levels.joinToString()))
            for (level in 0..2) {
                val g = newTrainer(kind, level, 2)
                while (!g.finished) {
                    val a = cheat(g)
                    assertFalse(a.line!!.text, bad.containsMatchIn(a.line!!.text + a.line!!.spoken + g.prompt().text))
                    g.next()
                }
            }
        }
    }

    @Test fun theSpokenLinesCarryNoSymbols() {
        for (kind in listOf(EarKind.PITCH, EarKind.DEGREE)) {
            val g = newTrainer(kind, 1, 4)
            val miss = g.choose((g.answerId()!! + 1) % 7)
            assertFalse(miss.line!!.spoken, miss.line!!.spoken.contains('#') || miss.line!!.spoken.contains('/'))
        }
    }
}
