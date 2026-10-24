package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.9 in the parent brief: the comeback on the wire and as praise ("came back after four days"), the habits numbers
 * (days back this month, the longest gap) and the week's one habits line, and the rest day on the seven-day strip.
 */
class RetentionBriefTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    @Test fun aComebackRoundTripsOnTheWireAndIsPraised() {
        val e = BriefEvent.Comeback(1_792_000_000_000, 4)
        assertEquals("""{"t":1792000000000,"e":"comeback","away":4}""", e.encode())
        assertEquals(e, BriefEvent.decode(e.encode()))
        val events = history {
            session(d(14), 17, 10)
            session(d(19), 17, 10) { done("garden-2") }
        } + BriefEvent.Comeback(at(d(19), 17, 1), 4)
        val f = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE)
        assertEquals(Praise(PraiseKind.CAME_BACK, 4), f.praise.first { it.kind == PraiseKind.CAME_BACK })
        val text = TemplateWriter({ "Garden" }, seed = 3, clock = { NOW }).write(f, "Mia").let { b -> listOf(b.headline, b.praise) + b.facts + b.sections.map { it.body } }
        assertTrue(text.toString(), text.any { it.contains("four days") })
    }

    @Test fun theHabitsSayDaysBackAndTheLongestGap() {
        // Wednesday 21 October. Played the 15th, 16th, 18th and 21st (and the 2nd, earlier this month).
        val events = history {
            session(d(2), 17, 10)
            session(d(15), 17, 10); session(d(16), 17, 10); session(d(18), 17, 10); session(d(21), 9, 10)
        }
        val f = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE)
        assertEquals(5, f.stats.daysBackThisMonth)
        assertEquals(2, f.stats.longestGap)                        // the 19th and 20th
        assertEquals(4, f.stats.totals.daysPractised)
        // The 17th, one missed day between two played, is the strip's rest day.
        assertEquals(listOf("2026-10-17"), f.stats.days.filter { it.rest }.map { it.date })
        // Both survive the findings' own encoding.
        val back = Findings.decode(f.encode())!!.stats
        assertEquals(5, back.daysBackThisMonth); assertEquals(2, back.longestGap); assertTrue(back.days.single { it.date == "2026-10-17" }.rest)
        // The week's habits section says it in one line.
        val habits = TemplateWriter({ "" }, seed = 3, clock = { NOW }).write(f, "Mia").sections.first { it.title == "Habits" }.body
        assertTrue(habits, habits.contains("of the last seven days") && habits.contains("the longest gap two days"))
        assertTrue(habits, habits.contains("Played on five days this month."))
        assertFalse(habits, Regex("[—–·]| - ").containsMatchIn(habits))
    }

    @Test fun aNewPlayerHasNoGapAndTodayStillOpenIsNotOne() {
        // First played on the 19th, then the 20th; today (the 21st) not yet.
        val events = history { session(d(19), 17, 10); session(d(20), 17, 10) } + BriefEvent.SettingChanged(at(d(21), 8), "sound", "true")
        val f = Insights.compute(events, mia, Period.WEEK, NOW, zone = ZONE)
        assertEquals(0, f.stats.longestGap)
        assertEquals(2, f.stats.daysBackThisMonth)
        val habits = TemplateWriter({ "" }, seed = 3, clock = { NOW }).write(f, "Mia").sections.first { it.title == "Habits" }.body
        assertTrue(habits, Regex("no gap at all|not one missed|no day skipped").containsMatchIn(habits))
    }

    @Test fun habitsCountTheMonthUpToTheDayAndTheGapInsideTheStrip() {
        assertEquals(Habits(3, 3), Habits.of(setOf(d(1), d(17), d(21), LocalDate.of(2026, 9, 30)), d(21), d(1)))
        assertEquals(Habits(0, 0), Habits.of(emptySet(), d(21), null))
        // The last day, still unplayed, is not a gap; a run of empty days before it is.
        assertEquals(Habits(1, 5), Habits.of(setOf(d(15)), d(21), d(1)))
    }
}
