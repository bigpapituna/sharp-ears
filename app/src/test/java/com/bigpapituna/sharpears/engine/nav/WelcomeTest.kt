package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.nav.Welcome.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7: What's new once per version and the tour once per profile, for grown-ups; kids see neither. The order a grown-up who
 * was here before 2.7 meets them in: What's new, then the tour, then nothing.
 */
class WelcomeTest {
    private val v = "2.7.0"

    @Test fun aGrownUpFromBeforeSeesWhatsNewThenTheTourThenNothing() {
        assertEquals(Show.WHATS_NEW, Welcome.next(kid = false, tourDone = false, whatsNewSeen = null, version = v))
        assertEquals(Show.WHATS_NEW, Welcome.next(kid = false, tourDone = false, whatsNewSeen = "2.6.0", version = v))
        assertEquals(Show.TOUR, Welcome.next(kid = false, tourDone = false, whatsNewSeen = v, version = v))
        assertEquals(Show.NOTHING, Welcome.next(kid = false, tourDone = true, whatsNewSeen = v, version = v))
    }

    /** A newcomer's What's new is marked seen at the first open (MainActivity): the tour follows the first-run questions. */
    @Test fun aNewcomerGetsOnlyTheTour() {
        assertEquals(Show.TOUR, Welcome.next(kid = false, tourDone = false, whatsNewSeen = v, version = v))
    }

    @Test fun kidsSeeNeither() {
        for (tour in listOf(true, false)) for (seen in listOf(null, "2.6.0", v))
            assertEquals(Show.NOTHING, Welcome.next(kid = true, tourDone = tour, whatsNewSeen = seen, version = v))
    }

    @Test fun whatsNewComesOncePerVersionAndOnlyWhenThereIsSomethingToSay() {
        val table = mapOf(v to Welcome.WHATS_NEW.getValue(v), "2.8.0" to listOf("A line"))
        assertEquals(Show.WHATS_NEW, Welcome.next(false, tourDone = true, whatsNewSeen = v, version = "2.8.0", table = table))
        assertEquals(Show.NOTHING, Welcome.next(false, tourDone = true, whatsNewSeen = "2.8.0", version = "2.8.0", table = table))
        // A version with no lines (a fix release) shows nothing, whatever was seen before.
        assertEquals(Show.NOTHING, Welcome.next(false, tourDone = true, whatsNewSeen = v, version = "2.7.1", table = table))
        assertEquals(Show.TOUR, Welcome.next(false, tourDone = false, whatsNewSeen = v, version = "2.7.1", table = table))
    }

    @Test fun theTableHasThreeLinesAtMostAndIsFilledFor270() {
        assertEquals(3, Welcome.whatsNew("2.7.0").size)
        Welcome.WHATS_NEW.values.forEach { assertTrue(it.size <= Welcome.MAX_LINES) }
        assertEquals(listOf("a", "b", "c"), Welcome.whatsNew("x", mapOf("x" to listOf("a", "b", "c", "d"))))
    }

    /** 2.8.1 says one thing: My songs. */
    @Test fun whatsNewIn281IsMySongs() {
        val lines = Welcome.whatsNew("2.8.1")
        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("My songs"))
    }

    @Test fun theTourIsThreeCardsOverTodaySongsAndLearnWithRoysTitles() {
        assertEquals(listOf("Today is your plan", "Songs is everything you can play", "Learn is the road, with chords at the top"), Welcome.TOUR.map { it.title })
        assertEquals(listOf(NavTab.TODAY, NavTab.SONGS, NavTab.LEARN), Welcome.TOUR.map { it.tab })
    }

    /** The copy rules (CLAUDE.md design system): no dashes or symbol glyphs, sentences end with a stop. */
    @Test fun theCopyKeepsTheRules() {
        val lines = Welcome.TOUR.flatMap { listOf(it.title, it.line) } + Welcome.WHATS_NEW.values.flatten()
        for (l in lines) {
            assertFalse(l, Regex("[—–·>]| - ").containsMatchIn(l))
        }
        (Welcome.TOUR.map { it.line } + Welcome.WHATS_NEW.values.flatten()).forEach { assertTrue(it, it.endsWith(".")) }
    }
}
