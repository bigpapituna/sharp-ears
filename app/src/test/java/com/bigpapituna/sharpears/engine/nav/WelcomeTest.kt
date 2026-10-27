package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.nav.Welcome.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 2.9.0, the retention release: three lines at most, the rest day, the comeback and tomorrow (the kids' surprise). */
    @Test fun whatsNewIn290IsTheRetentionRelease() {
        val lines = Welcome.whatsNew("2.9.0")
        assertTrue(lines.size in 1..Welcome.MAX_LINES)
        assertEquals(lines, Welcome.WHATS_NEW.getValue("2.9.0"))
        assertTrue(lines[0].startsWith("Rest days"))
        assertTrue(lines.any { "three easy minutes" in it })
        assertTrue(lines.any { "tomorrow" in it && "surprise" in it })
    }

    /** 2.10.0, the living pup: three lines at most, about the pup coming alive (his jump, his tilt, the kids' wag, the clips). */
    @Test fun whatsNewIn2100IsTheLivingPup() {
        val lines = Welcome.whatsNew("2.10.0")
        assertTrue(lines.size in 1..Welcome.MAX_LINES)
        assertEquals(lines, Welcome.WHATS_NEW.getValue("2.10.0"))
        assertTrue(lines[0].startsWith("The pup comes alive"))
        assertTrue(lines.any { "jumps for joy" in it } && lines.any { "tilts his head" in it })
        assertTrue(lines.any { "wag" in it })
        assertTrue(lines.any { "cheers after each lesson" in it && "wave hello" in it && "dance at the finale" in it })
        assertEquals(3, lines.size)
    }

    /** 2.11.0, the step pop: one line (three at most), the check, the chime and its climb. */
    @Test fun whatsNew2110IsTheStepPop() {
        val lines = Welcome.whatsNew("2.11.0")
        assertEquals(1, lines.size)
        assertTrue(lines.size <= Welcome.MAX_LINES)
        assertTrue(lines.single().let { "pops" in it && "chime" in it && "climbs" in it })
    }

    /** 2.12.0, the pup's new clips: one line (three at most), the jump, the wave and the celebration. */
    @Test fun whatsNew2120IsThePupsNewMoves() {
        val lines = Welcome.whatsNew("2.12.0")
        assertEquals(1, lines.size)
        assertTrue(lines.size <= Welcome.MAX_LINES)
        assertEquals("The pup has new moves: a bigger jump, a proper wave, and a celebration after every lesson.", lines.single())
    }

    /** 2.13.0, the cheer pops: one line (three at most), the pup's kind words while you play and where to change how often. */
    @Test fun whatsNew2130IsTheCheerPops() {
        val lines = Welcome.whatsNew("2.13.0")
        assertEquals(1, lines.size)
        assertTrue(lines.size <= Welcome.MAX_LINES)
        assertTrue(lines.single().let { "pops up" in it && "kind words" in it && "Settings" in it })
    }

    /** 2.14.0, three lines: levels and ages, the parents' part, the pup and the timing panel. */
    @Test fun whatsNew2140IsLevelsParentsAndThePup() {
        val lines = Welcome.whatsNew("2.14.0")
        assertEquals(3, lines.size)
        assertTrue(lines.size <= Welcome.MAX_LINES)
        assertTrue(lines[0].let { "Big kids" in it && "level" in it })
        assertTrue(lines[1].let { "family card" in it && "daily time" in it && "Lock now" in it })
        assertTrue(lines[2].let { "Tap the pup" in it && "timing" in it })
        lines.forEach { assertTrue(it, it.first().isUpperCase() && it.endsWith(".") && '!' !in it) }
    }

    /** 2.15.0 merge: the Plain look has no streak counter, so its tour skips "A streak that forgives"; the rest as they are. */
    @Test fun thePlainLooksTourSkipsTheStreakCard() {
        assertEquals(Welcome.TOUR, Welcome.tour(plain = false))
        val plain = Welcome.tour(plain = true)
        assertEquals(listOf(Welcome.TourArt.PLAN, Welcome.TourArt.BAND, Welcome.TourArt.TIMING, Welcome.TourArt.LOOP), plain.map { it.art })
        assertTrue(plain.none { "streak" in it.title.lowercase() || "streak" in it.line.lowercase() || "paw" in it.line.lowercase() })
    }

    /** 2.15: the tour is the five things that matter most, one picture and one sentence each, in this order. */
    @Test fun theTourIsTheFiveThingsThatMatterMost() {
        assertEquals(listOf("One Start for your day", "Play songs by chords with a band", "See your timing, bar by bar", "Loop the bars you missed", "A streak that forgives"),
            Welcome.TOUR.map { it.title })
        assertEquals(listOf(Welcome.TourArt.PLAN, Welcome.TourArt.BAND, Welcome.TourArt.TIMING, Welcome.TourArt.LOOP, Welcome.TourArt.STREAK), Welcome.TOUR.map { it.art })
        assertTrue(Welcome.TOUR.size <= Welcome.MAX_TOUR_CARDS)
        // One sentence a card.
        Welcome.TOUR.forEach { assertEquals(it.line, 1, it.line.count { ch -> ch == '.' }) }
    }

    /** 2.15: "For grown-ups" is three cards; the brief card promises an email only on a build that can send one. */
    @Test fun theParentsTourIsThreeCardsAndTheEmailIsHonest() {
        val with = Welcome.parentsTour(emailed = true); val without = Welcome.parentsTour(emailed = false)
        assertEquals(Welcome.PARENTS_TOUR_CARDS, with.size)
        assertEquals(listOf(Welcome.TourArt.FAMILY, Welcome.TourArt.LIMIT, Welcome.TourArt.GATE), with.map { it.art })
        assertTrue("emailed" in with[0].line && "emailed" !in without[0].line)
        assertTrue(with[0].line.endsWith(".") && without[0].line.endsWith("."))
        // The four things a parent needs, in three cards.
        assertTrue("Family today" in with[0].title && "brief" in with[0].title.lowercase())
        assertTrue("closes the app" in with[1].title && "until tomorrow" in with[1].line)
        assertTrue("Lock now" in with[2].title && "Lock now" in with[2].line && "two minutes" in with[2].line)
    }

    /** "For grown-ups" comes after What's new and the tour, once, for grown-ups only. */
    @Test fun theParentsTourComesLastAndNeverToAKid() {
        assertEquals(Show.WHATS_NEW, Welcome.next(false, tourDone = false, whatsNewSeen = null, version = v, parentsTourDue = true))
        assertEquals(Show.TOUR, Welcome.next(false, tourDone = false, whatsNewSeen = v, version = v, parentsTourDue = true))
        assertEquals(Show.PARENTS, Welcome.next(false, tourDone = true, whatsNewSeen = v, version = v, parentsTourDue = true))
        assertEquals(Show.NOTHING, Welcome.next(false, tourDone = true, whatsNewSeen = v, version = v, parentsTourDue = false))
        assertEquals(Show.NOTHING, Welcome.next(true, tourDone = true, whatsNewSeen = v, version = v, parentsTourDue = true))
    }

    /** The first kid on a phone starts it; a second kid, a phone that already saw it, or a phone that had kids before 2.15 does not. */
    @Test fun onlyTheFirstKidStartsTheParentsTour() {
        assertEquals(Welcome.PARENTS_PENDING, Welcome.parentsStateAfterAdd(hadKid = false, state = null))
        assertNull(Welcome.parentsStateAfterAdd(hadKid = true, state = null))
        assertEquals(Welcome.PARENTS_DONE, Welcome.parentsStateAfterAdd(hadKid = false, state = Welcome.PARENTS_DONE))
        assertEquals(Welcome.PARENTS_PENDING, Welcome.parentsStateAfterAdd(hadKid = true, state = Welcome.PARENTS_PENDING))
        assertTrue(Welcome.parentsTourDue(Welcome.PARENTS_PENDING))
        assertFalse(Welcome.parentsTourDue(Welcome.PARENTS_DONE)); assertFalse(Welcome.parentsTourDue(null))
    }

    /** The copy rules (CLAUDE.md design system): no dashes or symbol glyphs, sentences end with a stop. */
    @Test fun theCopyKeepsTheRules() {
        val lines = (Welcome.TOUR + Welcome.parentsTour(true) + Welcome.parentsTour(false)).flatMap { listOf(it.title, it.line) } + Welcome.WHATS_NEW.values.flatten()
        for (l in lines) {
            assertFalse(l, Regex("[—–·>]| - ").containsMatchIn(l))
        }
        (Welcome.TOUR.map { it.line } + Welcome.parentsTour(true).map { it.line } + Welcome.WHATS_NEW.values.flatten()).forEach { assertTrue(it, it.endsWith(".")) }
        (Welcome.TOUR + Welcome.parentsTour(true)).forEach { assertFalse(it.title, it.title.endsWith(".")); assertFalse(it.title, it.title.first().isLowerCase()) }
    }
}
