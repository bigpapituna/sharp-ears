package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The song recap's words: a headline by result, a coaching line, timing without milliseconds or dashes. */
class RunWordsTest {
    private fun recap(good: Int, ok: Int, miss: Int, bias: Int = 0) = Recap(good, ok, miss, bias, longestStreak = good)

    @Test fun headlines() {
        assertEquals("New best!", RunWords.title(12, newBest = true))
        assertEquals("Beautifully played", RunWords.title(95, false))
        assertEquals("Nicely done", RunWords.title(75, false))
        assertEquals("Getting there", RunWords.title(45, false))
        assertEquals("A first pass", RunWords.title(0, false))
        assertEquals("Clean run!", RunWords.waitTitle(0))
    }

    @Test fun timingInWords() {
        assertEquals("right on the beat", RunWords.timing(10))
        assertEquals("a hair late", RunWords.timing(50))
        assertEquals("a hair early", RunWords.timing(-50))
        assertEquals("a little behind the beat", RunWords.timing(200))
        assertEquals("a little ahead of the beat", RunWords.timing(-200))
    }

    @Test fun lowScoresGetACoachingLine() {
        assertTrue(RunWords.line(recap(0, 0, 40), hardest = 3, speed = 1.0).contains("Wait mode"))
        assertTrue(RunWords.line(recap(5, 0, 30), hardest = 3, speed = 1.0).contains("bar 3"))
        assertTrue(RunWords.line(recap(5, 0, 30), hardest = null, speed = 1.0).contains("half speed"))
        assertTrue(RunWords.line(recap(19, 0, 1), hardest = null, speed = 0.5).contains("speed up"))
    }

    @Test fun noMillisecondsNoDashes() {
        val lines = listOf(-300, -60, 0, 60, 300).flatMap { b ->
            listOf(recap(0, 0, 10, b), recap(3, 0, 7, b), recap(6, 0, 4, b), recap(8, 0, 2, b), recap(10, 0, 0, b))
                .flatMap { r -> listOf(RunWords.line(r, 2, 1.0), RunWords.line(r, null, 0.5)) }
        } + RunWords.waitLine(0, null) + RunWords.waitLine(1, 4) + RunWords.waitLine(7, null)
        lines.forEach { l ->
            assertFalse(l, l.contains(" ms") || l.contains('—') || l.contains('–') || l.contains(" - "))
        }
        assertEquals("1 wrong note on the way. Bar 4 is worth a loop.", RunWords.waitLine(1, 4))
    }
}
