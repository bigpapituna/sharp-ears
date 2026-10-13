package com.bigpapituna.sharpears.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.ear.Answering
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.ear.EarProgress
import com.bigpapituna.sharpears.engine.ear.PROGRESSION_CATALOG
import com.bigpapituna.sharpears.engine.ear.ProgressionGame
import com.bigpapituna.sharpears.engine.ear.Workout
import com.bigpapituna.sharpears.engine.ear.WorkoutResult
import com.bigpapituna.sharpears.engine.ear.WorkoutGame
import com.bigpapituna.sharpears.engine.ear.chordsOf
import com.bigpapituna.sharpears.engine.ear.progressionSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionWorkoutTest {
    @Test fun theCatalogSpellsTheChordsOfEachProgression() {
        fun names(id: Int, key: Int) = chordsOf(PROGRESSION_CATALOG[id], key).map { it.name }
        assertEquals(listOf("C", "F", "G", "C"), names(0, 0))
        assertEquals(listOf("G", "D", "Em", "C"), names(1, 7))
        assertEquals(listOf("Dm", "G", "C"), names(2, 0))
        assertEquals(listOf("Am", "Dm", "E", "Am"), names(6, 9))
        assertEquals(listOf("Am", "G", "F", "E"), names(7, 9))
        assertEquals(PROGRESSION_CATALOG.indices.toList(), PROGRESSION_CATALOG.map { it.id })
        assertEquals(PROGRESSION_CATALOG.size, PROGRESSION_CATALOG.map { it.label }.toSet().size)
        assertEquals(6, PROGRESSION_CATALOG.count { !it.minor })
    }

    @Test fun everyProgressionPlaysAsFourBeatsOfBlockChordsWithABass() {
        for (p in PROGRESSION_CATALOG) for (key in listOf(0, 5, 11)) {
            val steps = progressionSteps(p, key)
            assertEquals(p.steps.size, steps.size)
            // The same length whether it has three chords (the last held) or four, so the count gives nothing away.
            assertEquals(4, p.steps.sumOf { it.beats })
            steps.forEachIndexed { i, s ->
                assertEquals(4, s.notes.size)
                assertTrue(s.notes.first() <= s.notes[1] && s.notes.first() in 41..52)
                assertEquals(chordsOf(p, key)[i].root % 12, s.notes.first() % 12)
            }
        }
    }

    @Test fun levelsChooseTheirProgressionsAndKeys() {
        for ((level, expectKeys) in listOf(0 to 1, 1 to 4, 2 to 8)) {
            val g = ProgressionGame(level, seed = 5, rounds = 200)
            val ids = mutableSetOf<Int>(); val keys = mutableSetOf<Int>()
            while (!g.finished) {
                ids += g.target.id; keys += g.key
                assertEquals(Answering.CHOICES, g.answering)
                g.skip()
            }
            when (level) {
                0 -> assertEquals(setOf(0, 1, 2), ids)
                1 -> assertEquals((0..5).toSet(), ids)
                else -> assertEquals((0..9).toSet(), ids)
            }
            assertTrue("level $level keys ${keys.size}", keys.size >= expectKeys)
        }
        assertEquals(setOf(0), (1L..20L).map { ProgressionGame(0, it).key }.toSet())
    }

    @Test fun choicesHoldTheTargetAndWrongAnswersOfTheSameMode() {
        for (level in 0..2) {
            val g = ProgressionGame(level, seed = 8, rounds = 200)
            while (!g.finished) {
                val c = g.choices()
                assertEquals(if (level == 0) 3 else 4, c.size)
                assertTrue(g.target.id in c.map { it.id })
                assertEquals(c.size, c.map { it.id }.toSet().size)
                assertTrue(c.all { PROGRESSION_CATALOG[it.id].minor == g.target.minor })
                assertTrue(c.all { it.sub != null })
                g.skip()
            }
        }
    }

    @Test fun answeringSaysWhatItWas() {
        val g = ProgressionGame(1, seed = 3)
        val t = g.target
        val miss = g.choices().first { it.id != t.id }
        val wrong = g.choose(miss.id)
        assertEquals(ChordGame.Outcome.DONE, wrong.outcome); assertFalse(wrong.clean)
        assertTrue(wrong.line!!.text, wrong.line!!.text.startsWith("That was ${t.label} (${t.nickname}). ${t.blurb}"))
        assertTrue(wrong.line!!.text.contains("You picked ${PROGRESSION_CATALOG[miss.id].label}"))
        assertTrue(wrong.line!!.text.contains(chordsOf(t, g.key).first().name))
        assertEquals(0, g.score)
        g.next()
        val ok = g.choose(g.target.id)
        assertTrue(ok.clean); assertTrue(ok.line!!.text.startsWith("Yes: "))
        assertEquals(1, g.score)
        assertEquals(ChordGame.Outcome.IGNORED, g.choose(0).outcome)
    }

    // The workout --------------------------------------------------------------------------------------------

    private class Fake(val best: Map<Pair<EarKind, Int>, Int> = emptyMap(), val last: Map<EarKind, Long> = emptyMap()) : EarProgress {
        override fun best(kind: EarKind, level: Int) = best[kind to level] ?: -1
        override fun lastPlayedDay(kind: EarKind) = last[kind] ?: -1L
    }

    @Test fun theWorkoutClimbsToTheFirstLevelNotYetMastered() {
        assertEquals(0, Workout.levelFor(Fake(), EarKind.PITCH))
        assertEquals(0, Workout.levelFor(Fake(mapOf((EarKind.PITCH to 0) to 6)), EarKind.PITCH))
        assertEquals(1, Workout.levelFor(Fake(mapOf((EarKind.PITCH to 0) to 7)), EarKind.PITCH))
        assertEquals(2, Workout.levelFor(Fake(mapOf((EarKind.PITCH to 0) to 9, (EarKind.PITCH to 1) to 8)), EarKind.PITCH))
        assertEquals(2, Workout.levelFor(Fake(mapOf((EarKind.PITCH to 0) to 10, (EarKind.PITCH to 1) to 10, (EarKind.PITCH to 2) to 10)), EarKind.PITCH))
        // A level skipped (never played) stops the climb.
        assertEquals(0, Workout.levelFor(Fake(mapOf((EarKind.PITCH to 1) to 10)), EarKind.PITCH))
    }

    @Test fun theWorkoutIsThreeDifferentGamesInAboutFiveMinutes() {
        for (day in 20000L..20040L) {
            val plan = Workout.plan(Fake(), day)
            assertEquals(3, plan.items.size)
            assertEquals(3, plan.items.map { it.kind }.toSet().size)
            assertTrue("seconds ${plan.estimatedSeconds}", plan.estimatedSeconds in 200..360)
            assertTrue(plan.items.all { it.rounds in 4..8 })
        }
    }

    @Test fun theWorkoutPrefersGamesNotPlayedLatelyAndIsTheSameAllDay() {
        val last = mapOf(EarKind.RHYTHM to 100L, EarKind.MELODY to 99L, EarKind.PITCH to 50L, EarKind.DEGREE to 98L, EarKind.PROGRESSION to 20L)
        val plan = Workout.plan(Fake(last = last), 101)
        assertEquals(setOf(EarKind.PROGRESSION, EarKind.PITCH, EarKind.DEGREE), plan.items.map { it.kind }.toSet())
        assertEquals(plan, Workout.plan(Fake(last = last), 101))
        // Never played games come first.
        val fresh = Workout.plan(Fake(last = mapOf(EarKind.RHYTHM to 100L, EarKind.MELODY to 100L, EarKind.PITCH to 100L)), 101)
        assertEquals(setOf(EarKind.DEGREE, EarKind.PROGRESSION), fresh.items.map { it.kind }.toSet().intersect(setOf(EarKind.DEGREE, EarKind.PROGRESSION)))
        val lv = Workout.plan(Fake(mapOf((EarKind.DEGREE to 0) to 8, (EarKind.PROGRESSION to 0) to 3)), 7)
        lv.items.filter { it.kind == EarKind.DEGREE }.forEach { assertEquals(1, it.level) }
        lv.items.filter { it.kind == EarKind.PROGRESSION }.forEach { assertEquals(0, it.level) }
    }

    @Test fun workoutStreakCountsDaysInARow() {
        assertEquals(0, Workout.streak(emptySet(), 10))
        assertEquals(3, Workout.streak(setOf(8L, 9L, 10L), 10))
        assertEquals(2, Workout.streak(setOf(8L, 9L), 10))                  // today's is still to do
        assertEquals(0, Workout.streak(setOf(5L, 6L), 10))
        assertEquals(1, Workout.streak(setOf(3L, 10L), 10))
    }

    @Test fun workoutResultsAddUp() {
        val r = WorkoutResult(listOf(WorkoutGame(EarKind.PITCH, 0, 6, 8, 4), WorkoutGame(EarKind.RHYTHM, 1, 4, 7, 2), WorkoutGame(EarKind.MELODY, 0, 5, 5, 5)))
        assertEquals(15, r.score); assertEquals(20, r.rounds); assertEquals(5, r.bestStreak)
        assertEquals("Great workout!", Workout.headline(16, 20))
        assertEquals("Workout done!", Workout.headline(10, 20))
        assertEquals("Good effort!", Workout.headline(4, 20))
    }
}
