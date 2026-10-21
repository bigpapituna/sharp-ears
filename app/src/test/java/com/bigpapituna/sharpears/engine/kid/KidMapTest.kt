package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.playground.BIG_KID_LESSONS
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.Place as PagePlace
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.world.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kid's walk across the Playground pages and on to the big kids' road: the next stone, the stones' states, the
 * finale trigger per page and where the path goes after it. A made-up course with three pages (the pond and the hall
 * as the pages agent will add them, one still short of twelve) checks that nothing counts on one page or on twelve.
 */
class KidMapTest {
    private fun lessons(prefix: String, n: Int) = List(n) { CourseLesson("$prefix-${it + 1}", "$prefix ${it + 1}", "", emptyList()) }
    private val garden = CourseLevel(PLAYGROUND_ID, "Pup's Playground", "", lessons("g", 12))
    private val pond = CourseLevel("playground-pond", "The pond", "", lessons("p", 12))
    private val hall = CourseLevel("playground-hall", "The concert hall", "", lessons("h", 5))      // still being written
    private val basics = CourseLevel("basics", "Basics", "", lessons("b", 4))
    private val beginner = CourseLevel("beginner", "Beginner", "", lessons("n", 3))
    // The pages agent may put its levels anywhere in the course; the pages' order is what counts.
    private val course = listOf(garden, basics, beginner, hall, pond)
    private val pages = listOf(
        PlaygroundPage("garden", garden.id, PagePlace.GARDEN, "The garden", garden.lessons.map { it.id }),
        PlaygroundPage("pond", pond.id, PagePlace.POND, "The pond", pond.lessons.map { it.id }),
        PlaygroundPage("hall", hall.id, PagePlace.CONCERT_HALL, "The concert hall", hall.lessons.map { it.id }),
    )
    private val kid = CoursePath(PLAYGROUND_ID, course = course, pages = pages)
    private val grownUp = CoursePath("basics", course = course, pages = pages)

    private fun ids(vararg prefixes: Pair<String, Int>) = prefixes.flatMap { (p, n) -> (1..n).map { "$p-$it" } }.toSet()

    @Test fun aKidWalksEveryPageInPageOrderThenTheRoad() {
        assertEquals(listOf(PLAYGROUND_ID, "playground-pond", "playground-hall", "basics", "beginner"), kid.levels.map { it.id })
        assertEquals("g-1", KidMap.next(kid, { false }, emptySet(), pages)?.id)
        assertEquals("p-1", KidMap.next(kid, { it in ids("g" to 12) }, emptySet(), pages)?.id)
        assertEquals("b-1", KidMap.next(kid, { it in ids("g" to 12, "p" to 12, "h" to 5) }, emptySet(), pages)?.id)
    }

    @Test fun aGrownUpHasEveryPageAsAnExtraAtTheEnd() {
        assertEquals(listOf("basics", "beginner", PLAYGROUND_ID, "playground-pond", "playground-hall"), grownUp.levels.map { it.id })
        assertTrue(grownUp.levels.drop(2).all(grownUp::isExtra))
        assertEquals("b-1", grownUp.next { false }?.id)
        assertNull("never sent into a page", grownUp.next { it.startsWith("b") || it.startsWith("n") })
    }

    @Test fun aPageWhoseFinalePlayedIsWalkedPastItsGapsStayOpen() {
        val done = ids("g" to 12) - "g-4"
        assertEquals("the gap first, before the finale", "g-4", KidMap.next(kid, { it in done }, emptySet(), pages)?.id)
        val m = KidMap.build(kid, { it in done }, { 2 }, setOf("garden"), pages)
        assertEquals("p-1", m.next)
        assertEquals(1, m.nextPage)
        assertEquals(StoneState.OPEN, m.stone("g-4")?.state)
        assertEquals(StoneState.LATER, m.stone("p-2")?.state)
        assertFalse(m.roadOpen)
    }

    @Test fun afterTheLastFinaleBasicsFirstLessonIsTheNextStone() {
        val done = ids("g" to 12, "p" to 12, "h" to 5)
        val m = KidMap.build(kid, { it in done }, { 3 }, setOf("garden", "pond", "hall"), pages)
        assertTrue(m.roadOpen)
        assertEquals("b-1", m.next)
        assertEquals(-1, m.nextPage)
        assertEquals("the road grows a unit at a time", listOf("basics"), m.road.map { it.levelId })
        assertEquals(StoneState.NEXT, m.road.first().stones.first().state)
        // Every page walked, its stars on its stones.
        assertTrue(m.pages.all { it.walked })
        assertEquals(3, m.stone("h-5")?.stars)
        // Graduated with gaps: the hall's last finale played, two of its lessons skipped, Basics is still next.
        val gaps = done - "h-2" - "h-3"
        assertEquals("b-1", KidMap.build(kid, { it in gaps }, { 1 }, setOf("garden", "pond", "hall"), pages).next)
        // Further along the road, the road shows Basics and Beginner.
        val further = KidMap.build(kid, { it in done + ids("b" to 4) }, { 1 }, setOf("garden", "pond", "hall"), pages)
        assertEquals("n-1", further.next)
        assertEquals(listOf("basics", "beginner"), further.road.map { it.levelId })
    }

    @Test fun stonesAreDoneNextLeftBehindOrAhead() {
        val done = setOf("g-1", "g-2", "g-5")
        val m = KidMap.build(kid, { it in done }, { if (it == "g-2") 3 else 1 }, emptySet(), pages)
        val g = m.pages.first().stones.associate { it.lessonId to it }
        assertEquals(StoneState.DONE, g.getValue("g-1").state)
        assertEquals(1, g.getValue("g-1").stars)
        assertEquals(3, g.getValue("g-2").stars)
        assertEquals(StoneState.NEXT, g.getValue("g-3").state)
        assertEquals(StoneState.LATER, g.getValue("g-4").state)
        assertEquals("done ahead of the pup still shows done", StoneState.DONE, g.getValue("g-5").state)
        assertEquals(0, g.getValue("g-4").stars)
        assertEquals(0, m.nextPage)
        assertTrue(m.road.isEmpty())
    }

    @Test fun aBigKidSkipsTheGardensOtherGamesButWalksAPageTheShortListDoesNotName() {
        // The garden under its real lesson ids, which the short list names.
        val g = CourseLevel(PLAYGROUND_ID, "Pup's Playground", "", (1..12).map { n -> CourseLesson("playground-$n", "$n", "", emptyList()) })
        val bigPages = listOf(PlaygroundPage("garden", g.id, PagePlace.GARDEN, "The garden", g.lessons.map { it.id })) + pages.drop(1)
        val big = CoursePath(PLAYGROUND_ID, bigKid = true, course = listOf(g, basics, beginner, hall, pond), pages = bigPages)
        assertEquals("playground-2", KidMap.next(big, { false }, emptySet(), bigPages)?.id)
        val m = KidMap.build(big, { it in setOf("playground-2", "playground-3") }, { 2 }, emptySet(), bigPages)
        assertEquals("playground-6", m.next)
        assertEquals("skipped behind the pup, still open", StoneState.OPEN, m.stone("playground-4")?.state)
        assertEquals(StoneState.OPEN, m.stone("playground-1")?.state)
        // The short list done: the pond is next, walked whole (the list names none of it).
        val picked = BIG_KID_LESSONS.toSet()
        assertEquals("p-1", KidMap.next(big, { it in picked }, emptySet(), bigPages)?.id)
        assertEquals("p-2", KidMap.next(big, { it in picked + "p-1" }, emptySet(), bigPages)?.id)
    }

    @Test fun aShortPageDrawsTheStonesItHas() {
        val m = KidMap.build(kid, { false }, { 0 }, emptySet(), pages)
        assertEquals(listOf(12, 12, 5), m.pages.map { it.stones.size })
        assertEquals(listOf(Place.GARDEN, Place.POND, Place.CONCERT), m.pages.map { it.place })
        // A page naming a lesson the course does not have yet is drawn without it, not a crash.
        val ahead = pages + PlaygroundPage("later", "playground-later", PagePlace.POND, "Later", listOf("x-1"))
        assertEquals(0, KidMap.build(kid, { false }, { 0 }, emptySet(), ahead).pages.last().stones.size)
    }

    @Test fun theFinalePlaysOnEachPagesLastLessonOnce() {
        assertEquals("garden", Finale.trigger("g-12", emptySet(), pages)?.id)
        assertEquals("pond", Finale.trigger("p-12", setOf("garden"), pages)?.id)
        assertEquals("a page as long as it is", "hall", Finale.trigger("h-5", emptySet(), pages)?.id)
        assertNull("not twice", Finale.trigger("g-12", setOf("garden"), pages))
        assertNull("not on another lesson", Finale.trigger("g-11", emptySet(), pages))
        assertNull("not outside the Playground", Finale.trigger("b-4", emptySet(), pages))
        // A page with no lessons yet has no last lesson: nothing triggers, nothing throws.
        val empty = PlaygroundPage("empty", "x", PagePlace.POND, "Empty", emptyList())
        assertEquals("garden", Finale.trigger("g-12", emptySet(), listOf(empty) + pages)?.id)
        assertEquals(Finale.After.Road, Finale.after(pages[2], pages + empty))
    }

    /**
     * Audit 2026-10-20: a page's finale only played as the kid left its last lesson, from memory; with the app closed or
     * killed in between it was lost for good. A finale owed (the last lesson done, the finale not played) plays on the
     * kid home, once, first page first.
     */
    @Test fun aFinaleOwedPlaysWhenTheHomeOpens() {
        val done = setOf("g-12", "p-3")
        assertEquals("garden", Finale.owed(done::contains, emptySet(), pages)?.id)
        assertNull("played already", Finale.owed(done::contains, setOf("garden"), pages))
        assertNull("the last lesson not done", Finale.owed(setOf("g-11")::contains, emptySet(), pages))
        assertEquals("first page first", "garden", Finale.owed(setOf("g-12", "p-12")::contains, emptySet(), pages)?.id)
        assertEquals("pond", Finale.owed(setOf("g-12", "p-12")::contains, setOf("garden"), pages)?.id)
        val empty = PlaygroundPage("empty", "x", PagePlace.POND, "Empty", emptyList())
        assertNull("a page with no lessons owes nothing", Finale.owed({ true }, setOf("garden", "pond", "hall"), listOf(empty) + pages))
    }

    @Test fun afterAFinaleThePathGoesToTheNextPageOrTheRoad() {
        assertEquals(Finale.After.NextPage(pages[1]), Finale.after(pages[0], pages))
        assertEquals(Finale.After.NextPage(pages[2]), Finale.after(pages[1], pages))
        assertEquals(Finale.After.Road, Finale.after(pages[2], pages))
        assertEquals("one page only: the garden leads to the road", Finale.After.Road, Finale.after(pages[0], pages.take(1)))
        assertEquals("Next stop, the pond!", KidLines.onward(Finale.after(pages[0], pages)))
        assertEquals(KidLines.ROAD, KidLines.onward(Finale.After.Road))
    }

    /** The real pages, however many there are: each page's finale leads to the next page, the last one to Basics. */
    @Test fun theRealPagesLeadOnToBasics() {
        val path = CoursePath(PLAYGROUND_ID)
        var walked = emptySet<String>()
        var finales = emptySet<String>()
        PLAYGROUND_PAGES.forEachIndexed { i, page ->
            assertEquals(page.id, Finale.trigger(page.lastLessonId, finales)?.id)
            walked = walked + page.lessonIds
            finales = finales + page.id
            val m = KidMap.build(path, { it in walked }, { 2 }, finales)
            val nextPage = PLAYGROUND_PAGES.getOrNull(i + 1)
            if (nextPage != null) {
                assertEquals(Finale.After.NextPage(nextPage), Finale.after(page))
                assertEquals(nextPage.lessonIds.first(), m.next)
                assertEquals(i + 1, m.nextPage)
                assertFalse(m.roadOpen)
            } else {
                assertEquals(Finale.After.Road, Finale.after(page))
                assertEquals(COURSE.first { it.id == "basics" }.lessons.first().id, m.next)
                assertTrue(m.roadOpen)
            }
        }
    }

    @Test fun theKidHomesLinesAreInTheVoiceCatalogWithoutNames() {
        val kid = com.bigpapituna.sharpears.engine.SpokenCatalog.kidLines()
        assertTrue(kid.containsAll(KidLines.fixed().map(com.bigpapituna.sharpears.engine.SpokenCatalog::normalize)))
        assertTrue("You finished the garden!" in KidLines.fixed())
        assertTrue(KidLines.fixed().none { "Mia" in it })
        assertEquals("Three stars to find today!", KidLines.signpost(0))
        assertEquals("All three stars found today!", KidLines.signpost(5))
    }

    /**
     * 2.3: every place a page can be has its finale lines in the catalog, whichever pages this build has (the treehouse and
     * the night sky land from another branch), so they are recorded once and in the voice whatever the pages' order.
     */
    @Test fun everyPlacesFinaleLinesAreListed() {
        val fixed = KidLines.fixed()
        for (title in listOf("the workshop", "the treehouse", "the night sky", "the fair", "the pond", "the concert hall")) {
            assertTrue(title, "You finished $title!" in fixed)
            assertTrue(title, "Next stop, $title!" in fixed)
        }
        assertEquals(PagePlace.entries.size, PagePlace.entries.map { KidPages.worldPlace(it) }.toSet().size)
    }

    @Test fun pagesMapToTheirScenesAndDropPlaces() {
        assertEquals(Place.GARDEN, KidPages.worldPlace(PagePlace.GARDEN))
        assertEquals(Place.POND, KidPages.worldPlace(PagePlace.POND))
        assertEquals(Place.CONCERT, KidPages.worldPlace(PagePlace.CONCERT_HALL))
        assertEquals(Place.WORKSHOP, KidPages.worldPlace(PagePlace.WORKSHOP))
        assertEquals(Place.TREEHOUSE, KidPages.worldPlace(PagePlace.TREEHOUSE))
        assertEquals(Place.NIGHT_SKY, KidPages.worldPlace(PagePlace.NIGHT_SKY))
        assertEquals(Place.FAIR, KidPages.worldPlace(PagePlace.FAIR))
        assertEquals(Place.POND, KidPages.dropPlace("p-3", pages))
        assertNull(KidPages.dropPlace("b-1", pages))
        assertEquals(1 to 2, KidPages.locate("p-3", pages))
        assertEquals(1 to 0, KidPages.locate(13, pages))
        assertEquals(2 to 4, KidPages.locate(29, pages))
        assertNull(KidPages.locate(30, pages))
        assertNull(KidPages.locate(0, pages))
        assertEquals("Mia finished the concert hall!", KidLines.finished("Mia", pages[2]))
        assertEquals("You finished the garden!", KidLines.finished(" ", pages[0]))
    }

    /** The merged pages (2.2): every lesson drops its own page's stickers, by its page, never by its number. */
    @Test fun theRealPagesDropTheirOwnPlacesStickers() {
        val want = mapOf("garden" to Place.GARDEN, "pond" to Place.POND, "hall" to Place.CONCERT, "workshop" to Place.WORKSHOP, "fair" to Place.FAIR,
            "treehouse" to Place.TREEHOUSE, "nightsky" to Place.NIGHT_SKY)
        PLAYGROUND_PAGES.forEach { page ->
            page.lessonIds.forEach { id ->
                assertEquals(id, want.getValue(page.id), KidPages.dropPlace(id))
                assertEquals(id, page, com.bigpapituna.sharpears.engine.playground.pageOf(id))
            }
        }
        assertEquals(Place.POND, KidPages.dropPlace("pond-1"))
        assertEquals(Place.CONCERT, KidPages.dropPlace("hall-12"))
        assertEquals(Place.WORKSHOP, KidPages.dropPlace("workshop-7"))
        assertEquals(Place.FAIR, KidPages.dropPlace("fair-4"))
        assertNull("outside the Playground the album picks", KidPages.dropPlace("sit"))
    }

    /**
     * The real map walked lesson by lesson, the way the kid home does it (the next stone, its finale on a page's last
     * lesson): a pre-reader walks every page whole; a reader the garden's short list, then the rest whole. Either way the
     * map's next stone is the course path's next lesson, and the last page's finale (2.3: the fair's) opens the road.
     */
    @Test fun theRealMapFollowsTheCoursePathForReadersAndPreReaders() {
        for (reader in listOf(false, true)) {
            val path = CoursePath(PLAYGROUND_ID, bigKid = reader)
            val done = mutableSetOf<String>(); val finales = mutableSetOf<String>(); val walk = mutableListOf<String>()
            while (true) {
                val m = KidMap.build(path, { it in done }, { 2 }, finales)
                val next = m.next!!
                assertEquals(path.next { it in done }?.id, next)
                if (KidPages.pageOf(next) == null) { assertTrue(m.roadOpen); assertEquals("sit", next); break }
                assertFalse(m.roadOpen)
                assertEquals(StoneState.NEXT, m.stone(next)?.state)
                walk += next; done += next
                Finale.trigger(next, finales)?.let { finales += it.id }
            }
            // 2.3: every page after the garden walked whole (the workshop, the fair: the short list names none of them).
            val garden = if (reader) BIG_KID_LESSONS else PLAYGROUND_PAGES[0].lessonIds
            assertEquals("reader=$reader", garden + PLAYGROUND_PAGES.drop(1).flatMap { it.lessonIds }, walk)
            assertEquals(PLAYGROUND_PAGES.map { it.id }.toSet(), finales)
            assertEquals(PLAYGROUND_PAGES.map { it.lessonIds.size }, KidMap.build(path, { false }, { 0 }, emptySet()).pages.map { it.stones.size })
            assertEquals("the fair is a short page", 4, KidMap.build(path, { false }, { 0 }, emptySet()).pages.last().stones.size)
        }
    }

    // ---- 2.4: a grown-up's pace from the parent brief (engine/kid/Pace.kt) ----

    @Test fun aPinnedLessonIsThePupsStoneEvenWalkedAndTheStonesUpToTheWalkStayOpen() {
        val done = ids("g" to 6)
        val m = KidMap.build(kid, { it in done }, { 2 }, emptySet(), pages, Pace(pin = "g-3"))
        assertEquals("g-3", m.next)
        assertEquals(StoneState.NEXT, m.stone("g-3")?.state)
        assertEquals("its stars still show", 2, m.stone("g-3")?.stars)
        assertEquals("the walk's next is still open to play", StoneState.OPEN, m.stone("g-7")?.state)
        assertEquals(StoneState.LATER, m.stone("g-8")?.state)
        // A pin ahead of the walk opens the stones between.
        val ahead = KidMap.build(kid, { it in done }, { 2 }, emptySet(), pages, Pace(pin = "p-2"))
        assertEquals("p-2", ahead.next)
        assertEquals(1, ahead.nextPage)
        assertEquals(StoneState.OPEN, ahead.stone("p-1")?.state)
        assertEquals(StoneState.LATER, ahead.stone("p-3")?.state)
        // No pace: the map as before.
        assertEquals(KidMap.build(kid, { it in done }, { 2 }, emptySet(), pages), KidMap.build(kid, { it in done }, { 2 }, emptySet(), pages, Pace.NONE))
    }

    @Test fun aPageOpenedEarlyIsWalkedPastItsGapsStayOpen() {
        val done = ids("g" to 5)
        assertEquals("g-6", KidMap.next(kid, { it in done }, emptySet(), pages)?.id)
        val m = KidMap.build(kid, { it in done }, { 3 }, emptySet(), pages, Pace(early = setOf("garden")))
        assertEquals("p-1", m.next)
        assertEquals(StoneState.OPEN, m.stone("g-6")?.state)
        assertEquals(StoneState.OPEN, m.stone("g-12")?.state)
        assertEquals("the garden's finale is still to come", "garden", Finale.trigger("g-12", emptySet(), pages)?.id)
    }

    @Test fun focusHidesTheLaterPagesAndTheRoadAndKeepsThePupOnTheFocusPage() {
        val done = ids("g" to 12, "p" to 3)
        val m = KidMap.build(kid, { it in done }, { 2 }, setOf("garden"), pages, Pace(focus = "pond"))
        assertEquals(listOf("garden", "pond"), m.pages.map { it.page.id })
        assertFalse(m.roadOpen)
        assertEquals("p-4", m.next)
        // Walked past the focus page (a page opened early): the pup comes back to its first lesson not done.
        val past = KidMap.build(kid, { it in done }, { 2 }, setOf("garden"), pages, Pace(focus = "garden"))
        assertEquals(listOf("garden"), past.pages.map { it.page.id })
        assertEquals("every garden stone walked: the one with the fewest stars", "g-4",
            KidMap.next(kid, { it in done }, setOf("garden"), pages, Pace(focus = "garden"), stars = { if (it == "g-4") 1 else 3 })?.id)
        assertEquals(StoneState.NEXT, KidMap.build(kid, { it in done }, { if (it == "g-4") 1 else 3 }, setOf("garden"), pages, Pace(focus = "garden")).stone("g-4")?.state)
        // A pin on a page focus hides is left out until focus is lifted.
        assertEquals("p-4", KidMap.next(kid, { it in done }, setOf("garden"), pages, Pace(pin = "h-1", focus = "pond"))?.id)
    }

    @Test fun aPinOnTheRoadKeepsTheRoadOpenToItsUnit() {
        val done = ids("g" to 12, "p" to 12, "h" to 5)
        val m = KidMap.build(kid, { it in done }, { 3 }, setOf("garden", "pond", "hall"), pages, Pace(pin = "n-2"))
        assertTrue(m.roadOpen)
        assertEquals("n-2", m.next)
        assertEquals(listOf("basics", "beginner"), m.road.map { it.levelId })
        assertEquals(StoneState.OPEN, m.stone("b-1")?.state)
    }
}
