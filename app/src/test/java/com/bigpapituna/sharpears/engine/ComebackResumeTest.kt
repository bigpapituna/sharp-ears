package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.Comeback.Resume
import com.bigpapituna.sharpears.engine.nav.ContinueWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 2.14, the comeback opens a concrete step: the lesson left part way at its step, the chart or song played last, else the
 * next lesson; never a list (Marc landed on the Songs list). After a week away one line says what became of the streak.
 */
class ComebackResumeTest {
    private val exists: (ContinueWith) -> Boolean = { it.id != "gone" }

    @Test fun theLessonLeftPartWayComesFirst() {
        assertEquals(Resume.LessonStep("key-g", 2),
            Comeback.resume("key-g" to 2, ContinueWith.PopSong("wagon-wheel"), "basics-1", done = { false }, exists = exists))
        // Finished since, or gone from the course: what was played last.
        assertEquals(Resume.PopChart("wagon-wheel"),
            Comeback.resume("key-g" to 2, ContinueWith.PopSong("wagon-wheel"), "basics-1", done = { it == "key-g" }, exists = exists))
        assertEquals(Resume.Song("twinkle"), Comeback.resume("gone" to 1, ContinueWith.Song("twinkle"), "basics-1", done = { false }, exists = exists))
    }

    @Test fun thenWhatWasPlayedLastThenTheNextLessonNeverAList() {
        assertEquals(Resume.PopChart("wagon-wheel"), Comeback.resume(null, ContinueWith.PopSong("wagon-wheel"), "x", { false }, exists))
        assertEquals(Resume.Song("canon"), Comeback.resume(null, ContinueWith.Song("canon"), "x", { false }, exists))
        assertEquals(Resume.LessonStep("rests", 0), Comeback.resume(null, ContinueWith.Lesson("rests"), "x", { false }, exists))
        // Marc: nothing clean, nothing left open: the next lesson, from its first step.
        assertEquals(Resume.NextLesson("intermediate-1"), Comeback.resume(null, null, "intermediate-1", { false }, exists))
        assertEquals(Resume.NextLesson("n"), Comeback.resume(null, ContinueWith.Lesson("rests"), "n", { it == "rests" }, exists))
        assertEquals(Resume.NextLesson("n"), Comeback.resume(null, ContinueWith.Song("gone"), "n", { false }, exists))
        assertNull(Comeback.resume(null, null, null, { false }, exists))
    }

    @Test fun theLeftStepIsKeptAsOneLine() {
        assertEquals("key-g" to 3, Comeback.parseLeft(Comeback.encodeLeft("key-g", 3)))
        assertEquals("playground-2" to 0, Comeback.parseLeft("playground-2:x"))
        assertNull(Comeback.parseLeft(null)); assertNull(Comeback.parseLeft("nocolon"))
    }

    @Test fun eachAudienceHasItsWords() {
        assertEquals("Welcome back.", Comeback.BIG_KID_TITLE)
        assertEquals("Pick up where you left off.", Comeback.BIG_KID_LINE)
        assertEquals("Three easy minutes to warm up. Pick up “The scale in the left hand” where you left off.",
            Comeback.grownUpLine(Resume.LessonStep("x", 2), "The scale in the left hand"))
        assertEquals("Three easy minutes to warm up. Play “Wagon Wheel” with the band.", Comeback.grownUpLine(Resume.PopChart("w"), "Wagon Wheel"))
        assertEquals(Comeback.grownUpLine("Twinkle", false), Comeback.grownUpLine(Resume.Song("t"), "Twinkle"))
        assertEquals("Three easy minutes to warm up. Start “Rests”.", Comeback.grownUpLine(Resume.NextLesson("r"), "Rests"))
        // The little kids keep the pup's own words.
        assertEquals("You're back!", Comeback.KID_TITLE)
    }

    @Test fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "9th", "11th", "12th", "13th", "21st", "22nd", "23rd", "30th", "31st"),
            listOf(1, 2, 3, 4, 9, 11, 12, 13, 21, 22, 23, 30, 31).map(Comeback::ordinal))
    }

    @Test fun afterAWeekAwayTheStreakIsNamedOnce() {
        val today = LocalDate.of(2026, 10, 26)
        // Twelve days in a row up to the 7th of October, then nothing: the freeze earned on day seven kept the 8th, the 9th broke it.
        val run = (0L until 12L).map { LocalDate.of(2026, 10, 7).minusDays(it) }.toSet()
        assertEquals("Your 12-day streak ended on the 9th. A new one starts today.", Comeback.streakLine(run, today))
        // A one-day streak is no loss to name.
        assertEquals("A new streak starts today.", Comeback.streakLine(setOf(today.minusDays(9)), today))
        // Six days since the last practice says nothing; a week does.
        assertNull(Comeback.streakLine(setOf(today.minusDays(7), today.minusDays(6)), today))
        val back = setOf(today.minusDays(8), today.minusDays(7))
        // The day after the last one is yesterday's rest day while it is open; the one after it breaks the run.
        assertEquals("Your 2-day streak ended on the ${Comeback.ordinal(today.minusDays(5).dayOfMonth)}. A new one starts today.",
            Comeback.streakLine(back, today))
        assertNull("a first day", Comeback.streakLine(emptySet(), today))
        // Marc: 25 and 26 October, back on 2 November (a week away).
        assertEquals("Your 2-day streak ended on the 28th. A new one starts today.",
            Comeback.streakLine(setOf(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)), LocalDate.of(2026, 11, 2)))
    }
}
