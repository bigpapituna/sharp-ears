package com.bigpapituna.sharpears.progress

import com.bigpapituna.sharpears.engine.progress.Achievement
import com.bigpapituna.sharpears.engine.progress.AchievementCategory
import com.bigpapituna.sharpears.engine.progress.Achievements
import com.bigpapituna.sharpears.engine.progress.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    /** One achievement: a snapshot that just earns it and one that just misses. A plain empty snapshot is the "below" for yes or no ones. */
    private class Case(val id: String, val at: Snapshot, val below: Snapshot = Snapshot())

    private val rootsEleven = (0..10).toSet()
    private val rootsAll = (0..11).toSet()
    private val cases = listOf(
        // Practice
        Case("first-note", Snapshot(totalSeconds = 1)),
        Case("minutes-100", Snapshot(totalSeconds = 6000), Snapshot(totalSeconds = 5999)),
        Case("minutes-500", Snapshot(totalSeconds = 30_000), Snapshot(totalSeconds = 29_999)),
        Case("minutes-1000", Snapshot(totalSeconds = 60_000), Snapshot(totalSeconds = 59_999)),
        Case("marathon", Snapshot(bestDaySeconds = 1800), Snapshot(bestDaySeconds = 1799)),
        Case("night-owl", Snapshot(nightOwl = true)),
        Case("early-bird", Snapshot(earlyBird = true)),
        Case("comeback", Snapshot(comeback = true)),
        Case("goal-1", Snapshot(daysGoalMet = 1)),
        Case("goal-10", Snapshot(daysGoalMet = 10), Snapshot(daysGoalMet = 9)),
        Case("level-10", Snapshot(level = 10), Snapshot(level = 9)),
        Case("level-20", Snapshot(level = 20), Snapshot(level = 19)),
        // Streaks (the longest streak counts, so a broken streak keeps its trophy)
        Case("streak-3", Snapshot(longestStreak = 3), Snapshot(longestStreak = 2)),
        Case("streak-7", Snapshot(longestStreak = 7), Snapshot(longestStreak = 6)),
        Case("streak-14", Snapshot(longestStreak = 14), Snapshot(longestStreak = 13)),
        Case("streak-30", Snapshot(longestStreak = 30), Snapshot(longestStreak = 29)),
        Case("streak-100", Snapshot(longestStreak = 100), Snapshot(longestStreak = 99)),
        Case("freeze-used", Snapshot(freezesUsed = 1)),
        Case("week-goal", Snapshot(weeksGoalMet = 1)),
        Case("week-goal-4", Snapshot(weeksGoalMet = 4), Snapshot(weeksGoalMet = 3)),
        // Songs
        Case("song-1", Snapshot(songsCompleted = 1)),
        Case("song-10", Snapshot(songsCompleted = 10), Snapshot(songsCompleted = 9)),
        Case("song-25", Snapshot(songsCompleted = 25), Snapshot(songsCompleted = 24)),
        Case("perform-1", Snapshot(performRuns = 1)),
        Case("perform-80", Snapshot(bestPerform = 80), Snapshot(bestPerform = 79)),
        Case("perform-95", Snapshot(bestPerform = 95), Snapshot(bestPerform = 94)),
        Case("polished-5", Snapshot(songsPolished = 5), Snapshot(songsPolished = 4)),
        Case("records-10", Snapshot(newBests = 10), Snapshot(newBests = 9)),
        // Lessons
        Case("lesson-1", Snapshot(lessonsDone = 1)),
        Case("lessons-10", Snapshot(lessonsDone = 10), Snapshot(lessonsDone = 9)),
        Case("lessons-25", Snapshot(lessonsDone = 25), Snapshot(lessonsDone = 24)),
        Case("basics-done", Snapshot(unitsComplete = setOf("basics")), Snapshot(unitsComplete = setOf("beginner"))),
        Case("beginner-done", Snapshot(unitsComplete = setOf("beginner")), Snapshot(unitsComplete = setOf("basics"))),
        Case("intermediate-done", Snapshot(unitsComplete = setOf("intermediate")), Snapshot(unitsComplete = setOf("basics"))),
        Case("course-done", Snapshot(lessonsDone = 70, lessonsTotal = 70), Snapshot(lessonsDone = 69, lessonsTotal = 70)),
        // Chords
        Case("chord-1", Snapshot(chordRounds = 1)),
        Case("game-1", Snapshot(gamesCompleted = 1)),
        Case("game-10", Snapshot(gamesCompleted = 10), Snapshot(gamesCompleted = 9)),
        Case("game-perfect", Snapshot(perfectGames = 1)),
        Case("every-key", Snapshot(rootsPlayed = rootsAll), Snapshot(rootsPlayed = rootsEleven)),
        // Ear
        Case("ear-1", Snapshot(earRounds = 1)),
        Case("ear-50", Snapshot(earRounds = 50), Snapshot(earRounds = 49)),
        Case("ear-250", Snapshot(earRounds = 250), Snapshot(earRounds = 249)),
        Case("ear-100-right", Snapshot(earCorrect = 100), Snapshot(earCorrect = 99)),
        // Theory
        Case("theory-1", Snapshot(theoryLessons = 1)),
        Case("theory-10", Snapshot(theoryLessons = 10), Snapshot(theoryLessons = 9)),
        Case("theory-unit-1", Snapshot(theoryUnits = 1)),
        Case("theory-unit-6", Snapshot(theoryUnits = 6), Snapshot(theoryUnits = 5)),
        // Explorer
        Case("sight-1", Snapshot(sightReadRuns = 1)),
        Case("mic-on", Snapshot(featuresUsed = setOf("mic")), Snapshot(featuresUsed = setOf("import"))),
        Case("import-midi", Snapshot(featuresUsed = setOf("import")), Snapshot(featuresUsed = setOf("mic"))),
        Case("daily-1", Snapshot(dailySessions = 1)),
        Case("daily-10", Snapshot(dailySessions = 10), Snapshot(dailySessions = 9)),
        Case("explorer", Snapshot(featuresUsed = setOf("a", "b", "c", "d", "e", "f")), Snapshot(featuresUsed = setOf("a", "b", "c", "d", "e"))),
    )

    private fun ach(id: String): Achievement = Achievements.byId(id) ?: error("no achievement $id")

    @Test fun thereAreEnoughAndIdsAreUnique() {
        assertTrue(Achievements.ALL.size >= 30)
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.title }.toSet().size)
    }

    @Test fun everyCategoryHasAchievements() {
        AchievementCategory.entries.forEach { c ->
            assertTrue("$c is empty", Achievements.ALL.any { it.category == c })
        }
        assertEquals(AchievementCategory.entries.toSet(), Achievements.byCategory().keys)
    }

    @Test fun everyAchievementHasACase() {
        assertEquals(Achievements.ALL.map { it.id }.toSet(), cases.map { it.id }.toSet())
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
    }

    @Test fun everyConditionFiresWhenMet() {
        cases.forEach { c -> assertTrue("${c.id} should unlock", ach(c.id).unlocked(c.at)) }
    }

    @Test fun everyConditionStaysLockedWhenNotMet() {
        cases.forEach { c -> assertFalse("${c.id} should stay locked", ach(c.id).unlocked(c.below)) }
    }

    @Test fun anEmptySnapshotUnlocksNothing() {
        assertTrue(Achievements.newlyUnlocked(Snapshot(), emptySet()).isEmpty())
    }

    @Test fun newlyUnlockedSkipsWhatYouAlreadyHave() {
        val s = Snapshot(totalSeconds = 6000, songsCompleted = 1)
        val first = Achievements.newlyUnlocked(s, emptySet()).map { it.id }
        assertEquals(listOf("first-note", "minutes-100", "song-1"), first)
        val second = Achievements.newlyUnlocked(s, setOf("first-note", "song-1")).map { it.id }
        assertEquals(listOf("minutes-100"), second)
        assertTrue(Achievements.newlyUnlocked(s, first.toSet()).isEmpty())
    }

    @Test fun fractionShowsProgressTowardTheTarget() {
        val a = ach("minutes-100")
        assertEquals(0f, a.fraction(Snapshot()), 0f)
        assertEquals(0.5f, a.fraction(Snapshot(totalSeconds = 3000)), 0.001f)
        assertEquals(1f, a.fraction(Snapshot(totalSeconds = 60_000)), 0f)
    }

    @Test fun everyKeyIgnoresValuesOutsideTheOctave() {
        assertFalse(ach("every-key").unlocked(Snapshot(rootsPlayed = rootsEleven + 12 + 13)))
    }

    @Test fun courseDoneNeedsACourse() {
        assertFalse(ach("course-done").unlocked(Snapshot(lessonsDone = 0, lessonsTotal = 0)))
    }

    @Test fun timeOfDayWindows() {
        assertTrue(Achievements.isNightOwlHour(22)); assertTrue(Achievements.isNightOwlHour(23)); assertFalse(Achievements.isNightOwlHour(21))
        assertTrue(Achievements.isEarlyBirdHour(6)); assertTrue(Achievements.isEarlyBirdHour(0)); assertFalse(Achievements.isEarlyBirdHour(7))
    }

    @Test fun iconsAndCopyAreFilledIn() {
        Achievements.ALL.forEach {
            assertTrue("${it.id} icon", it.icon.name.isNotBlank())
            assertTrue("${it.id} hint", it.hint.isNotBlank())
            assertTrue("${it.id} desc", it.description.isNotBlank())
            // House style: no em or en dashes in copy.
            listOf(it.title, it.description, it.hint).forEach { s -> assertFalse("${it.id}: $s", s.contains('—') || s.contains('–')) }
        }
    }
}
