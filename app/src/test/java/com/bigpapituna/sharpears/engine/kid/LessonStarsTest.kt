package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Report
import org.junit.Assert.assertEquals
import org.junit.Test

/** Stars on every stone: a run's stars by the recap rules, a lesson's from its steps, and the stone keeping its best. */
class LessonStarsTest {
    private fun waitRun(wrong: Int) = Report(emptyList(), 0, wrong, null)
    private fun perform(good: Int, miss: Int, bias: Int = 20) = Report(emptyList(), 0, 0, Recap(good, 0, miss, bias, good))
    private fun scored(score: Int) = Report(emptyList(), 0, 0, null, extraScore = score)

    @Test fun aRunEarnsWhatItsRecapWould() {
        assertEquals(3, LessonStars.ofRun(waitRun(0)))
        assertEquals(2, LessonStars.ofRun(waitRun(3)))
        assertEquals(1, LessonStars.ofRun(waitRun(4)))
        assertEquals(3, LessonStars.ofRun(perform(good = 19, miss = 1)))
        assertEquals("in time but loose", 2, LessonStars.ofRun(perform(good = 19, miss = 1, bias = 120)))
        assertEquals(2, LessonStars.ofRun(perform(good = 6, miss = 4)))
        assertEquals(1, LessonStars.ofRun(perform(good = 5, miss = 5)))
        // The drum: claps on the beat as a share, on the Perform lines.
        assertEquals(3, LessonStars.ofRun(scored(95)))
        assertEquals(2, LessonStars.ofRun(scored(60)))
        assertEquals(1, LessonStars.ofRun(scored(10)))
    }

    @Test fun aLessonIsTheMeanOfItsStepsAHalfRoundedUp() {
        assertEquals(3, LessonStars.ofLesson(listOf(3, 3, 3)))
        assertEquals(3, LessonStars.ofLesson(listOf(3, 2)))
        assertEquals(2, LessonStars.ofLesson(listOf(3, 1)))
        assertEquals(1, LessonStars.ofLesson(listOf(1, 1, 2)))
        assertEquals(2, LessonStars.ofLesson(listOf(LessonStars.LET_GO, 3, 2)))
        assertEquals("nothing graded: finished is three", 3, LessonStars.ofLesson(emptyList()))
        assertEquals("out-of-range steps are clamped", 3, LessonStars.ofLesson(listOf(7, 3)))
    }

    @Test fun theStoneKeepsItsBest() {
        assertEquals(2, LessonStars.best(null, 2))
        assertEquals(2, LessonStars.best(0, 2))
        assertEquals("a replay with fewer takes nothing away", 3, LessonStars.best(3, 1))
        assertEquals("a replay with more adds", 3, LessonStars.best(1, 3))
        assertEquals("never zero after a finish", 1, LessonStars.best(0, 0))
        assertEquals(3, LessonStars.best(2, 9))
    }

    @Test fun aPagesCertificateStars() {
        val stars = mapOf("a" to 3, "b" to 2, "c" to 0)
        assertEquals(3, LessonStars.ofPage(listOf("a", "b", "c")) { stars[it] ?: 0 })
        assertEquals(1, LessonStars.ofPage(listOf("c")) { stars[it] ?: 0 })
    }
}
