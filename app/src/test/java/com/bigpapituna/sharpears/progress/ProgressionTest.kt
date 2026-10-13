package com.bigpapituna.sharpears.progress

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.progress.Experience
import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.engine.progress.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProgressionTest {
    @Test fun levelThresholdsGrow() {
        assertEquals(0, Progression.xpToReach(1))
        assertEquals(100, Progression.xpToReach(2))
        assertEquals(283, Progression.xpToReach(3))
        assertEquals(2700, Progression.xpToReach(10))
        // Each level asks for more than the one before it.
        val gaps = (1..60).map { Progression.xpToReach(it + 1) - Progression.xpToReach(it) }
        assertTrue(gaps.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun levelForIsTheInverseAtTheEdges() {
        for (level in 1..Progression.MAX_LEVEL) {
            val start = Progression.xpToReach(level)
            assertEquals("start of $level", level, Progression.levelFor(start))
            if (level > 1) assertEquals("just before $level", level - 1, Progression.levelFor(start - 1))
        }
        assertEquals(1, Progression.levelFor(0))
        assertEquals(1, Progression.levelFor(-5))
        assertEquals(Progression.MAX_LEVEL, Progression.levelFor(Int.MAX_VALUE / 2))
    }

    @Test fun fractionAndRemaining() {
        assertEquals(0f, Progression.fractionToNext(0), 0f)
        assertEquals(0.5f, Progression.fractionToNext(50), 0.001f)
        assertEquals(50, Progression.xpToNext(50))
        assertEquals(0f, Progression.fractionToNext(100), 0f)           // exactly at level 2
        assertEquals(183, Progression.xpToNext(100))
        assertEquals(50, Progression.xpIntoLevel(150))
        assertEquals(1f, Progression.fractionToNext(Progression.xpToReach(Progression.MAX_LEVEL)), 0f)
        assertEquals(0, Progression.xpToNext(Progression.xpToReach(Progression.MAX_LEVEL)))
    }

    @Test fun levelUpOnlyWhenALevelIsCrossed() {
        assertNull(Progression.levelUp(0, 99))
        assertEquals(LevelUp(1, 2), Progression.levelUp(0, 100))
        assertEquals(LevelUp(1, 3), Progression.levelUp(50, 300))      // a big gain skips levels
        assertNull(Progression.levelUp(100, 0))
    }

    @Test fun titlesFollowTheRanks() {
        assertEquals("Newcomer", Progression.title(1))
        assertEquals("Tune Finder", Progression.title(5))
        assertEquals("Steady Hands", Progression.title(10))
        assertEquals("Chord Builder", Progression.title(15))
        assertEquals("Sight Reader", Progression.title(20))
        assertEquals("Sight Reader", Progression.title(21))             // between ranks: the one below
        assertEquals("Performer", Progression.title(30))
        assertEquals("Virtuoso", Progression.title(40))
        assertEquals("Virtuoso", Progression.title(49))
        assertEquals("Newcomer", Progression.title(0))
        assertTrue((1..Progression.MAX_LEVEL).all { Progression.title(it).isNotBlank() })
    }

    @Test fun xpRules() {
        val now = LocalDateTime.of(2026, 10, 13, 9, 0)
        assertEquals(10, Xp.PRACTICE_MINUTE)
        assertEquals(15, Progression.xpFor(ProgressEvent.LessonStepPassed))
        assertEquals(100, Progression.xpFor(ProgressEvent.LessonComplete("sit")))
        assertEquals(0, Progression.xpFor(ProgressEvent.LessonComplete("sit"), firstTime = false))
        assertEquals(50, Progression.xpFor(ProgressEvent.SongRun("a", null, newBest = false, firstCompletion = false)))
        assertEquals(100, Progression.xpFor(ProgressEvent.SongRun("a", 90, newBest = true, firstCompletion = false)))
        assertEquals(5, Progression.xpFor(ProgressEvent.ChordRound(0)))
        assertEquals(40, Progression.xpFor(ProgressEvent.GameComplete("build", 0, 8)))
        assertEquals(60, Progression.xpFor(ProgressEvent.TheoryLesson("t1")))
        assertEquals(0, Progression.xpFor(ProgressEvent.TheoryLesson("t1"), firstTime = false))
        assertEquals(200, Progression.xpFor(ProgressEvent.TheoryUnit("u1")))
        assertEquals(5, Progression.xpFor(ProgressEvent.EarRound()))
        assertEquals(0, Progression.xpFor(ProgressEvent.Practice(now, 600)))     // practice is paid by minute crossings
        assertEquals(0, Progression.xpFor(ProgressEvent.FeatureUsed("mic")))
        assertEquals(75, Xp.DAILY_GOAL_MET)
    }

    @Test fun practicePaysPerWholeMinuteWithoutLosingRemainders() {
        assertEquals(0, Progression.practiceXp(0, 59))
        assertEquals(10, Progression.practiceXp(0, 60))
        assertEquals(10, Progression.practiceXp(59, 61))                 // crossing a minute mark pays
        assertEquals(30, Progression.practiceXp(30, 200))
        assertEquals(0, Progression.practiceXp(100, 100))
        // Fifteen-second flushes add up to the same as one long session.
        var total = 0L; var xp = 0
        repeat(40) { val next = total + 15; xp += Progression.practiceXp(total, next); total = next }
        assertEquals(Progression.practiceXp(0, 600), xp)
        assertEquals(100, xp)
    }

    @Test fun onboardingStartsAtLessonsThatExist() {
        val ids = COURSE.flatMap { l -> l.lessons.map { l.id to it.id } }
        Experience.entries.forEach { e ->
            assertNotNull("${e.name} start lesson", ids.firstOrNull { it.second == e.startLessonId })
            assertEquals("${e.name} unit", e.startUnit, ids.first { it.second == e.startLessonId }.first)
        }
        assertEquals(Experience.A_LITTLE, Experience.fromOrdinal(1))
        assertNull(Experience.fromOrdinal(7))
    }
}
