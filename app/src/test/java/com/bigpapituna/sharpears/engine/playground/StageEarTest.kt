package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a stage turns the mic's reports into one sound per moment (StageEar), and what each round makes of it (the judges). */
class StageEarTest {
    @Test fun aStrikeIsDecidedAtOnceAndASecondReportOfTheSameSoundIsNothing() {
        val ear = StageEar()
        val s = ear.strike(61, -30.0, 1_000).single()
        assertTrue(s.final); assertEquals(HeardBy.MATCHER, s.by); assertEquals(61, s.midi)
        assertTrue("a chord's second key", ear.strike(63, -32.0, 1_080).isEmpty())
        assertTrue("its onset, named", ear.wrong(64, -35.0, 1_150).isEmpty())
        assertTrue("its onset as noise", ear.noise(1_200).isEmpty())
        assertEquals("a new sound", 1, ear.strike(61, -30.0, 1_400).size)
    }

    @Test fun anythingElseIsShownAtOnceAndDecidedLater() {
        val ear = StageEar()
        val shown = ear.noise(2_000).single()
        assertFalse(shown.final); assertEquals(HeardBy.ONSET, shown.by)
        assertTrue(ear.poll(2_100).isEmpty())
        val decided = ear.poll(2_300).single()
        assertTrue(decided.final); assertEquals(HeardBy.ONSET, decided.by)
        assertTrue(ear.poll(2_600).isEmpty())
    }

    @Test fun aLateStrikeReplacesTheOnsetItBelongsTo() {
        // A bass note's strike can come after its onset was already reported as noise.
        val ear = StageEar()
        ear.noise(3_000)
        val s = ear.strike(36, -28.0, 3_090).single()
        assertTrue(s.final); assertEquals(36, s.midi)
        assertTrue("the onset is gone", ear.poll(3_400).isEmpty())
    }

    @Test fun theNamedKeyBeatsThePitchGuessAndAGuessNeedsToBeClear() {
        val ear = StageEar()
        assertTrue("a muddy pitch is not a guess", ear.pitch(70.2, 0.5, 4_000).isEmpty())
        val p = ear.pitch(70.2, 0.95, 4_000).single()
        assertEquals(HeardBy.PITCH, p.by); assertEquals(70, p.near)
        val n = ear.wrong(70, -31.0, 4_100).single()
        assertEquals(HeardBy.NAMED, n.by)
        val d = ear.poll(4_500).single()
        assertEquals(HeardBy.NAMED, d.by); assertEquals(70, d.midi); assertTrue(d.final)
        // A plain onset gains the tracker's guess.
        ear.noise(5_000)
        assertEquals(HeardBy.PITCH, ear.pitch(40.1, 0.97, 5_050).single().by)
        assertEquals(40, ear.poll(5_400).single().near)
    }

    @Test fun highAndLow() {
        fun s(by: HeardBy, midi: Int? = null, guess: Double? = null) = StageSound(0, by, midi, guess, final = true)
        assertEquals(Answer.Found(84), HighLowJudge.judge(s(HeardBy.MATCHER, 84), wantHigh = true))
        assertEquals(Answer.Found(74), HighLowJudge.judge(s(HeardBy.NAMED, 74), wantHigh = true))
        assertEquals(Answer.Miss(MissWhy.OTHER_SIDE, 48), HighLowJudge.judge(s(HeardBy.MATCHER, 48), wantHigh = true))
        assertEquals(Answer.Miss(MissWhy.MIDDLE, 60), HighLowJudge.judge(s(HeardBy.NAMED, 60), wantHigh = false))
        assertEquals(Answer.Found(43), HighLowJudge.judge(s(HeardBy.PITCH, guess = 43.2), wantHigh = false))
        assertEquals("a guess near the middle is no side", Answer.Heard, HighLowJudge.judge(s(HeardBy.PITCH, guess = 61.4), wantHigh = true))
        assertEquals(Answer.Heard, HighLowJudge.judge(s(HeardBy.ONSET), wantHigh = true))
    }

    @Test fun theHunts() {
        fun s(by: HeardBy, midi: Int?) = StageSound(0, by, midi, final = true)
        assertEquals(Answer.Found(61), HuntJudge.judge(s(HeardBy.MATCHER, 61), Hunt.DOG_HOUSE))
        assertEquals("a dog house an octave down", Answer.Found(63), HuntJudge.judge(s(HeardBy.NAMED, 51), Hunt.DOG_HOUSE))
        assertEquals(Answer.Miss(MissWhy.WHITE_KEY, 62), HuntJudge.judge(s(HeardBy.NAMED, 62), Hunt.DOG_HOUSE))
        assertEquals(Answer.Miss(MissWhy.OTHER_GROUP, 66), HuntJudge.judge(s(HeardBy.NAMED, 66), Hunt.DOG_HOUSE))
        assertEquals(Answer.Found(70), HuntJudge.judge(s(HeardBy.NAMED, 58), Hunt.FENCE))
        assertEquals(Answer.Miss(MissWhy.NOT_C, 62), HuntJudge.judge(s(HeardBy.NAMED, 62), Hunt.C))
        assertEquals("the pitch tracker never finds", Answer.Heard, HuntJudge.judge(s(HeardBy.PITCH, 61), Hunt.DOG_HOUSE))
        assertEquals(Answer.Heard, HuntJudge.judge(s(HeardBy.ONSET, null), Hunt.FENCE))
    }

    @Test fun theStageKeysFitTheCapAndTheExercises() {
        for (keys in listOf(StageKeys.HIGH_LOW, StageKeys.DOG_HOUSES, StageKeys.FENCES, StageKeys.CS, StageKeys.SOFT_LOUD))
            assertTrue("$keys", keys.size <= com.bigpapituna.sharpears.engine.MIC_ANY_OF_LIMIT)
        assertEquals(setOf(61, 63, 73, 75), StageKeys.DOG_HOUSES)
        assertEquals(setOf(66, 68, 70, 78, 80, 82), StageKeys.FENCES)
        assertEquals(setOf(36, 48, 60, 72, 84), StageKeys.CS)
        assertTrue(StageKeys.HIGH.all { it > 60 } && StageKeys.LOW.all { it < 60 })
        // The hunts' exercises ask for exactly the keys the stage listens for.
        val lesson2 = PLAYGROUND.lessons[1]
        for (step in lesson2.steps.filter { it.game == Game.KEY_HUNT }) {
            val song = step.exercise!!.toSong("t", "t")
            for (g in song.groups) {
                val keys = g.notes.map { it.midi }.toSet()
                assertEquals(StageKeys.forHunt(Hunt.of(keys)!!), keys)
            }
        }
    }
}
