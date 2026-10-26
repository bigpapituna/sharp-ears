package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Insights
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.TODAY
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.history
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14, the family today card on a grown-up's Today (Priya's P8): one row per child, the same numbers as the child's brief. */
class FamilyTodayTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")

    @Test fun aRowSaysTodaysMinutesLessonsAndStarsAsTheBriefDoes() {
        val events = history {
            session(TODAY.minusDays(1), 18, 10)
            session(TODAY, 17, 22, practiceMin = 14) { done("playground-1", stars = 3); done("playground-2", stars = 2) }
        }
        val c = FamilyToday.of(mia.id, events, TODAY, NOW, ZONE)
        assertEquals(ChildToday("p1", minutes = 22, practiceMinutes = 14, lessons = 2, stars = 5, paw = TodayPaw.PLAYED, streak = 2), c)
        assertEquals("14 min playing of 22 min on screen. 2 lessons, 5 stars.", FamilyToday.line(c))
        assertEquals("2 days in a row", FamilyToday.pawWords(c))
        val day = Insights.compute(events, mia, Period.DAY, NOW, zone = ZONE).stats.totals
        assertEquals(day.minutes, c.minutes); assertEquals(day.practiceMinutes, c.practiceMinutes); assertEquals(day.lessonsFinished, c.lessons)
    }

    @Test fun nothingYetTodayAndARestDay() {
        // Played the day before yesterday, rested yesterday: today is still open, so yesterday reads as the rest day.
        val rested = FamilyToday.of(mia.id, history { session(TODAY.minusDays(2), 18, 10) }, TODAY, NOW, ZONE)
        assertFalse(rested.played)
        assertEquals(TodayPaw.REST, rested.paw)
        assertEquals("Not at the piano yet today.", FamilyToday.line(rested))
        assertEquals("Yesterday was a rest day", FamilyToday.pawWords(rested))
        val none = FamilyToday.of(mia.id, emptyList(), TODAY, NOW, ZONE)
        assertEquals(TodayPaw.NONE, none.paw)
        assertEquals("No playing yet today", FamilyToday.pawWords(none))
    }

    @Test fun oneLessonOneStarAndScreenTimeWithNoPractice() {
        val c = FamilyToday.of(mia.id, history { session(TODAY, 9, 5, practiceMin = 0) { done("playground-1", stars = 1) } }, TODAY, NOW, ZONE)
        assertEquals("0 min playing of 5 min on screen. 1 lesson, 1 star.", FamilyToday.line(c))
        assertEquals("no practice: no paw", TodayPaw.NONE, c.paw)
        assertTrue(c.played)
    }

    @Test fun lessonsFromOtherDaysAndTheFutureAreNotToday() {
        val events = listOf(BriefEvent.LessonDone(NOW + 60_000, "playground-1", 3)) +
            history { session(TODAY.minusDays(1), 18, 10) { done("playground-2") } }
        assertEquals(0, FamilyToday.of(mia.id, events, TODAY, NOW, ZONE).lessons)
    }
}
