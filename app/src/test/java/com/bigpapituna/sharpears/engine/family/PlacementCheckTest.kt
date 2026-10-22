package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.family.PlacementCheck.Result
import com.bigpapituna.sharpears.engine.family.PlacementCheck.Task
import com.bigpapituna.sharpears.engine.parseTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementCheckTest {
    @Test fun tasksPerLevelAreTwoForALittleThreeForSomeAndALotNoneForNever() {
        assertTrue(PlacementCheck.tasksFor(Level.NEVER).isEmpty()); assertFalse(PlacementCheck.offered(Level.NEVER))
        assertEquals(listOf(Task.FIND_C, Task.FIVE_NOTES), PlacementCheck.tasksFor(Level.A_LITTLE))
        assertEquals(listOf(Task.FIND_C, Task.FIVE_NOTES, Task.TWINKLE), PlacementCheck.tasksFor(Level.SOME))
        assertEquals(PlacementCheck.tasksFor(Level.SOME), PlacementCheck.tasksFor(Level.A_LOT))
    }

    @Test fun theExercisesAreWhatTheBriefAsksFor() {
        assertEquals(listOf(60, 62, 64, 65, 67), parseTokens(PlacementCheck.FIVE_NOTES_EXERCISE.right).flatMap { it.keys })
        assertEquals(listOf(60, 60, 67, 67, 69, 69, 67), parseTokens(PlacementCheck.TWINKLE_EXERCISE.right).flatMap { it.keys })
        assertEquals(70.0, PlacementCheck.TWINKLE_EXERCISE.bpm, 0.0)
        assertTrue("the band's bass is under it", PlacementCheck.TWINKLE_EXERCISE.left.isNotBlank())
        assertTrue(parseTokens(PlacementCheck.FIND_C_EXERCISE.right).single().anyOf)
    }

    @Test fun scoreCountsPlayedThingsOnly() {
        assertEquals(2, PlacementCheck.score(listOf(Result.PLAYED, Result.LET_GO, Result.PLAYED)))
        assertEquals(0, PlacementCheck.score(listOf(Result.SKIPPED, Result.LET_GO)))
    }

    @Test fun theSuggestionMatrixForGrownUps() {
        // (said, score) -> start level. Three things asked for some and a lot, two for a little.
        val m = listOf(
            Triple(Level.A_LITTLE, 2, Level.A_LITTLE), Triple(Level.A_LITTLE, 1, Level.NEVER), Triple(Level.A_LITTLE, 0, Level.NEVER),
            Triple(Level.SOME, 3, Level.SOME), Triple(Level.SOME, 2, Level.A_LITTLE), Triple(Level.SOME, 1, Level.NEVER), Triple(Level.SOME, 0, Level.NEVER),
            Triple(Level.A_LOT, 3, Level.A_LOT), Triple(Level.A_LOT, 2, Level.A_LITTLE), Triple(Level.A_LOT, 1, Level.NEVER), Triple(Level.A_LOT, 0, Level.NEVER),
        )
        for ((said, score, want) in m) assertEquals("$said with $score", want, PlacementCheck.suggest(said, score).level)
    }

    @Test fun theCheckNeverRaisesAnAnswerAndASkippedCheckKeepsIt() {
        for (said in Level.entries) {
            val asked = PlacementCheck.tasksFor(said).size
            for (score in 0..asked) assertTrue(PlacementCheck.suggest(said, score).level.ordinal <= said.ordinal)
            val skipped = PlacementCheck.suggest(said, null)
            assertEquals(said, skipped.level); assertTrue(skipped.agrees); assertNull(skipped.score)
        }
        assertEquals(Level.NEVER, PlacementCheck.suggest(Level.NEVER, 3).level)
    }

    @Test fun theGentlerLineNamesTheStartAndWhatStaysOpen() {
        assertEquals("Let's begin at Beginner; Basics stays open.", PlacementCheck.suggest(Level.SOME, 2).line)
        assertEquals("Let's begin at Basics.", PlacementCheck.suggest(Level.A_LITTLE, 0).line)
        assertTrue(PlacementCheck.suggest(Level.SOME, 3).line.startsWith("Lovely playing."))
        val words = listOf("wrong", "fail", "pass", "score", "/", "mark")
        for (said in Level.entries) for (score in listOf(null) + (0..3)) {
            val line = PlacementCheck.suggest(said, score).line.lowercase()
            assertTrue("no mark is ever worded: $line", words.none { it in line })
        }
    }

    @Test fun kidsAreWordedByPages() {
        val s = PlacementCheck.suggest(Level.SOME, 2, ProfileKind.KID, asked = 3)
        assertEquals(Level.A_LITTLE, s.level)
        assertEquals("Let's begin at the pond; the garden stays open.", s.line)
        assertEquals(Level.SOME, PlacementCheck.suggest(Level.A_LOT, null, ProfileKind.KID).level)
    }
}
