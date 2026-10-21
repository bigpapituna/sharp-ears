package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    private fun recap(good: Int, miss: Int = 0, bias: Int = 0) = Recap(good, 0, miss, bias, good)

    @Test fun notesAreSpokenAsWords() {
        assertEquals("F sharp", spokenNote(66))
        assertEquals("B flat", spokenNote(70))
        assertEquals("C four", spokenNote(60))
        assertEquals("C five", spokenNote(72))
        assertEquals("E flat", spokenPitch(3))
        // TalkBack's key names: the sharp as a word, every octave said (it read "F#4" as "F number 4").
        assertEquals("F sharp 4", spokenKey(66))
        assertEquals("C4", spokenKey(60))
        assertEquals("A sharp 2", spokenKey(46))
    }

    /** Audit: Play at a song's end (after a seek) passed no group, yet counted as a clean run with a sticker and XP. */
    @Test fun aRunThatReachedNothingIsEmpty() {
        assertTrue(Report(emptyList(), 0, 0, null, groups = 0).empty)
        assertTrue("a Perform run with no note graded", Report(emptyList(), 0, 0, Recap(0, 0, 0, 0, 0)).empty)
        assertFalse(Report(emptyList(), 0, 0, null, groups = 1).empty)
        assertFalse("a Perform run that missed everything still played", Report(emptyList(), 0, 0, recap(0, miss = 3)).empty)
    }

    @Test fun cleanRun() {
        assertEquals("Clean. On to the next step.", feedbackLine(Report(emptyList(), 0, 0, null), perform = false).text)
        assertEquals("Clean.", feedbackLine(Report(emptyList(), 0, 0, null), perform = false, next = false).text)
    }

    @Test fun namesTheNoteMissedMost() {
        val r = Report(listOf(66 to 3, 60 to 1), retries = 3, wrongKeys = 4, recap = null)
        assertEquals("You missed F sharp three times. Try that bar slowly.", feedbackLine(r, perform = false).text)
    }

    @Test fun oneOffSlips() {
        assertEquals("Nearly clean: one slip, on B flat. On to the next step.",
            feedbackLine(Report(listOf(70 to 1), 1, 1, null), perform = false).text)
        assertEquals("Nearly clean: 2 notes needed a second try. On to the next step.",
            feedbackLine(Report(listOf(62 to 1, 64 to 1), 2, 2, null), perform = false).text)
    }

    @Test fun performTimingIsSpokenInMilliseconds() {
        val f = feedbackLine(Report(emptyList(), 0, 0, recap(10, bias = -60)), perform = true)
        assertEquals("A little rushed: you were 60 ms early on average.", f.text)
        assertEquals("A little rushed: you were 60 milliseconds early on average.", f.spoken)
        assertEquals("A little late: you were 75 ms behind on average.",
            feedbackLine(Report(emptyList(), 0, 0, recap(10, bias = 75)), perform = true).text)
    }

    @Test fun performBelowThePassMark() {
        assertEquals("70 percent, and you need 80. You missed C four twice. Play it again.",
            feedbackLine(Report(listOf(60 to 2), 2, 2, recap(7, miss = 3)), perform = true, pass = 80).text)
        assertEquals("0 percent, and you need 80. No notes were heard in time. Play it again.",
            feedbackLine(Report(emptyList(), 0, 0, recap(0, miss = 9)), perform = true).text)
    }

    @Test fun cleanPerformAndAPassWithMisses() {
        assertEquals("Clean. On to the next step.", feedbackLine(Report(emptyList(), 0, 0, recap(9)), perform = true).text)
        assertEquals("90 percent. Passed. On to the next step.", feedbackLine(Report(emptyList(), 0, 0, recap(9, miss = 1)), perform = true).text)
    }

    @Test fun transportChargesWrongKeysToTheNearestExpectedNote() {
        val notes = parseLine("C4:1 F#4:1 G4:1", Hand.RIGHT, 500.0)
        val t = Transport(Song("t", "t", 120.0, 4 to 4, notes, groupOnsets(notes), emptyList(), 2000)).apply { mode = Mode.WAIT }
        t.play()
        t.tick(10.0)
        assertEquals(InputResult.GROUP_DONE, t.noteInput(60))
        t.tick(600.0)
        assertEquals(InputResult.WRONG, t.noteInput(65))      // F for F#
        assertEquals(InputResult.WRONG, t.noteInput(65))
        assertEquals(InputResult.GROUP_DONE, t.noteInput(66))
        val r = t.report()
        assertEquals(listOf(66 to 2), r.missed)
        assertEquals(1, r.retries)
        assertEquals(2, r.wrongKeys)
    }
}
