package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 2.14, the top of the brief (Priya's P10): "what they can now do" and practice against screen time. */
class CanDoTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)
    private val titles = object : BriefTitles {
        override fun lesson(id: String) = mapOf("basics-3" to "Find C anywhere")[id] ?: id
        override fun song(id: String) = mapOf("twinkle" to "Twinkle, Twinkle, Little Star", "ode" to "Ode to Joy")[id] ?: id
        override fun page(id: String) = id
    }

    @Test fun lessonsAndSongsPlayedWellTheLatestFirst() {
        val f = Insights.compute(history {
            session(d(1), 18, 10) { done("playground-4", stars = 3) }                 // over four weeks ago: left out
            session(d(16), 18, 10) { done("playground-1", stars = 3) }
            session(d(18), 18, 10) { done("playground-2", stars = 1) }                // one star: not yet
            session(d(19), 18, 10) { song("twinkle", stars = 2, hands = Hands.LEFT); song("twinkle", stars = 3, hands = Hands.RIGHT) }
            session(d(20), 18, 10) { song("ode", stars = 3, mode = RunMode.LISTEN) }   // the app played it: not the child
            session(d(21), 18, 10) { done("playground-3", stars = 2) }
        }, mia, Period.DAY, NOW, zone = ZONE)
        assertEquals(listOf(CanDo(lessonId = "playground-3"), CanDo(songId = "twinkle", hands = Hands.RIGHT), CanDo(lessonId = "playground-1")), f.canDo)
        assertEquals("Can find C on the keyboard, play Twinkle, Twinkle, Little Star with the right hand and tell high keys from low ones.",
            CanDoWords.line(f.canDo, titles))
        // The ids reach the titles the AI writer may use, and survive the wire.
        assertTrue("playground-3" in f.lessonIds() && "twinkle" in f.songIds())
        assertEquals(f.canDo, Findings.decode(f.encode())!!.canDo)
    }

    @Test fun bothHandsOutrankOneAndOtherLessonsGoByTheirTitle() {
        val f = Insights.compute(history {
            session(d(20), 18, 10) { song("ode", stars = 2, hands = Hands.RIGHT); song("ode", stars = 2, hands = Hands.BOTH) }
            session(d(21), 18, 10) { done("basics-3", stars = 3) }
        }, mia, Period.WEEK, NOW, zone = ZONE)
        assertEquals("Can play the lesson “Find C anywhere” and play Ode to Joy with both hands.", CanDoWords.line(f.canDo, titles))
    }

    @Test fun nothingPlayedWellSaysNothing() {
        assertNull(CanDoWords.line(emptyList(), titles))
        val f = Insights.compute(history { session(d(21), 18, 10) { done("playground-1", stars = 1) } }, mia, Period.DAY, NOW, zone = ZONE)
        assertTrue(f.canDo.isEmpty())
        assertFalse("nothing written for older readers", f.encode().contains("canDo"))
    }

    @Test fun aLongLineLeavesOutTheOldestSkill() {
        val long = listOf(CanDo(lessonId = "pond-8"), CanDo(lessonId = "workshop-12"), CanDo(lessonId = "treehouse-7"))
        val line = CanDoWords.line(long, titles)!!
        assertTrue(line, line.length <= CanDoWords.MAX_CHARS)
        assertTrue(line, line.startsWith("Can hold a low C in the left hand"))
        assertFalse(line, "black key" in line)
    }

    /** Every Playground lesson has its own skill; none claims more than a finish shows, and the copy rules hold. */
    @Test fun everyPlaygroundLessonHasAPlainSkill() {
        val ids = PLAYGROUND_PAGES.flatMap { it.lessonIds }
        assertEquals(ids.toSet(), CanDoWords.SKILLS.keys)
        for ((id, s) in CanDoWords.SKILLS) {
            assertTrue("$id: $s", s.first().isLowerCase())
            assertFalse("$id: $s", s.endsWith("."))
            assertFalse("$id: $s", Regex("[–—·]|without looking|by heart").containsMatchIn(s))
        }
    }

    @Test fun practiceAgainstScreenTime() {
        assertEquals("14 min playing of 22 min on screen.", CanDoWords.minutesLine(14, 22))
        assertEquals("0 min playing of 5 min on screen.", CanDoWords.minutesLine(0, 5))
        assertNull(CanDoWords.minutesLine(0, 0))
        val f = Insights.compute(history { session(d(21), 18, 22, practiceMin = 14) }, mia, Period.DAY, NOW, zone = ZONE)
        assertEquals("14 min playing of 22 min on screen.", CanDoWords.minutesLine(f.stats.totals.practiceMinutes, f.stats.totals.minutes))
    }

    /** P10: "grew to seven minutes this week, from about zero" compared a whole week with the few minutes of a first day. */
    @Test fun aFirstWeekIsNotComparedWithThePartWeekBeforeIt() {
        val f = Insights.compute(history {
            session(d(14), 18, 2)                                     // the first day, in the week before this one
            for (day in 15..21) session(d(day), 18, 7)
        }, mia, Period.WEEK, NOW, zone = ZONE)
        assertNull(f.trends.firstOrNull { it.kind == TrendKind.MINUTES })
        assertNull(f.trends.firstOrNull { it.kind == TrendKind.DAYS })
    }
}
