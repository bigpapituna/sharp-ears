package com.bigpapituna.sharpears.progress

import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.engine.progress.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.14 depth: the level names run as far as a daily intermediate gets in a month, so every level-up of that month names
 * something new (Sam: "Level 1, Newcomer" on You, a name that told him nothing).
 */
class LevelLadderTest {
    /**
     * A day of an intermediate who plays every day: twenty minutes, the goal met, a lesson of four steps, two song runs with
     * a new best, six chord rounds and Practice today.
     */
    private val day = 20 * Xp.PRACTICE_MINUTE + Xp.DAILY_GOAL_MET + Xp.LESSON_COMPLETE + 4 * Xp.LESSON_STEP +
        2 * Xp.SONG_RUN + Xp.SONG_NEW_BEST + 6 * Xp.CHORD_ROUND + Xp.DAILY_SESSION

    @Test fun everyLevelOfAMonthHasItsOwnName() {
        val month = Progression.levelFor(30 * day)
        assertTrue("a month reaches level $month", month >= 30)
        // A margin past the month, for a player placed further up (branch levels-ages seeds level and XP).
        val names = (1..month + 5).map { Progression.title(it) }
        assertEquals(names.toString(), names.size, names.distinct().size)
    }

    @Test fun onlyTheFirstLevelIsANewcomer() {
        assertEquals("Newcomer", Progression.title(1))
        (2..Progression.MAX_LEVEL).forEach { assertNotEquals("Newcomer", Progression.title(it)) }
    }
}
