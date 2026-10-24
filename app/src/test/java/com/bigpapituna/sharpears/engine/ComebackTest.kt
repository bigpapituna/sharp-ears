package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Hands
import com.bigpapituna.sharpears.engine.brief.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.9, the comeback: back after three or more full days away, a person is met with one easy thing they know, once per
 * return. A kid replays a favourite stone, a grown-up the last clean song (slower) or lesson.
 */
class ComebackTest {
    private val today = LocalDate.of(2026, 10, 28)
    private fun ago(n: Long) = today.minusDays(n)

    @Test fun threeFullDaysAwayIsAComeback() {
        // Last played four days ago: the 25th, 26th and 27th were missed.
        assertEquals(Comeback.Return(ago(4), 3), Comeback.due(setOf(ago(10), ago(4)), today, greeted = null))
        assertEquals(Comeback.Return(ago(5), 4), Comeback.due(setOf(ago(5)), today, greeted = null))
        // Two days away is not.
        assertNull(Comeback.due(setOf(ago(3)), today, greeted = null))
        assertNull(Comeback.due(setOf(ago(1)), today, greeted = null))
    }

    @Test fun theCardShowsOncePerReturn() {
        val practised = setOf(ago(6))
        assertEquals(6, Comeback.due(practised, today, greeted = null)!!.lastPlayed.until(today).days)
        assertNull(Comeback.due(practised, today, greeted = ago(6)))                    // greeted for this return
        assertEquals(ago(6), Comeback.due(practised, today, greeted = ago(20))!!.lastPlayed)  // an older return does not count
        // Played today already: the return happened, no card.
        assertNull(Comeback.due(practised + today, today, greeted = null))
        // Nobody who never played comes back.
        assertNull(Comeback.due(emptySet(), today, greeted = null))
    }

    @Test fun daysAwayCountsTheFullDaysBetween() {
        assertEquals(0, Comeback.daysAway(ago(1), today))
        assertEquals(3, Comeback.daysAway(ago(4), today))
        assertEquals("four days", Comeback.daysWords(4))
        assertEquals("one day", Comeback.daysWords(1))
        assertEquals("12 days", Comeback.daysWords(12))
    }

    @Test fun aKidReplaysTheirBestRecentStoneTheEasierOneOnATie() {
        val stars = mapOf("g-1" to 3, "g-2" to 2, "g-3" to 3, "g-4" to 1, "g-5" to 2)
        assertEquals("g-1", Comeback.kidStone(listOf("g-1", "g-2", "g-3", "g-4", "g-5"), stars::get))
        // Only the last twelve walked count: the old three-star stone is out of reach, the recent best wins.
        val walked = (1..20).map { "p-$it" }
        val many = walked.associateWith { if (it == "p-1") 3 else if (it == "p-15") 2 else 1 }
        assertEquals("p-15", Comeback.kidStone(walked, many::get))
        // No stars known (played before stars were kept): the earliest of the recent stones.
        assertEquals("p-9", Comeback.kidStone(walked, { null }))
        assertNull(Comeback.kidStone(emptyList(), stars::get))
    }

    @Test fun aGrownUpReplaysTheLastCleanSongOrLesson() {
        fun song(at: Long, id: String, stars: Int, mode: RunMode = RunMode.WAIT) = BriefEvent.SongRun(at, id, mode, Hands.RIGHT, null, stars)
        fun lesson(at: Long, id: String, stars: Int) = BriefEvent.LessonDone(at, id, stars)
        val known = setOf("twinkle", "ode", "minuet")
        val lessons = setOf("fingers-right", "c-position")
        fun pick(vararg e: BriefEvent) = Comeback.grownUpPiece(e.toList(), { it in known }, { it in lessons })
        // The latest clean one wins over a later rough run.
        assertEquals(Comeback.Piece.Song("twinkle"), pick(song(1, "ode", 2), song(2, "twinkle", 3), song(3, "minuet", 1)))
        assertEquals(Comeback.Piece.Lesson("c-position"), pick(song(1, "twinkle", 3), lesson(2, "c-position", 3), song(3, "ode", 2)))
        // Listen runs and songs that no longer exist are not replayed.
        assertEquals(Comeback.Piece.Song("ode"), pick(song(1, "ode", 3), song(2, "twinkle", 3, RunMode.LISTEN), song(3, "gone", 3)))
        // Nothing clean: the latest played.
        assertEquals(Comeback.Piece.Song("minuet"), pick(song(1, "ode", 1), song(2, "minuet", 2)))
        assertNull(pick())
        // The card's words: three easy minutes, nothing new.
        assertTrue(Comeback.grownUpLine("Twinkle", lesson = false).startsWith("Three easy minutes"))
        assertFalse(Regex("[—–·]| - ").containsMatchIn(Comeback.grownUpLine("Twinkle", lesson = false)))
        assertEquals(0.75, Comeback.GROWN_UP_SPEED, 0.0)
        assertEquals(180_000L, Comeback.GROWN_UP_MS)
    }
}
