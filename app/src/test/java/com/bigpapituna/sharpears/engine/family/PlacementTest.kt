package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.kid.KidMap
import com.bigpapituna.sharpears.engine.kid.StoneState
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.isPlaygroundLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {
    private fun ids(unit: String) = COURSE.first { it.id == unit }.lessons.map { it.id }
    private fun page(id: String) = PLAYGROUND_PAGES.first { it.id == id }.lessonIds

    @Test fun grownUpsNeverStartAtBasicsWithNothingSkipped() {
        val p = Placement.startFor(Level.NEVER, ProfileKind.ADULT)
        assertEquals("basics", p.startUnitId); assertEquals(ids("basics").first(), p.startLessonId)
        assertTrue(p.skipLessonIds.isEmpty()); assertTrue(p.isFullCourse)
        assertEquals(HomeFirst.COURSE, p.homeFirst)
    }

    @Test fun aLittleStartsAtBeginnerWithBasicsSkipped() {
        val p = Placement.startFor(Level.A_LITTLE, ProfileKind.ADULT)
        assertEquals("beginner", p.startUnitId); assertEquals(ids("beginner").first(), p.startLessonId)
        assertEquals(ids("basics"), p.skipLessonIds)
        assertEquals(listOf("basics"), p.skipUnitIds)
        assertEquals("Begin at Beginner. Basics stays open.", p.line)
    }

    @Test fun someStartsAtIntermediateWithBasicsBeginnerAndRhythmSkipped() {
        val p = Placement.startFor(Level.SOME, ProfileKind.ADULT)
        assertEquals("intermediate", p.startUnitId)
        assertEquals(ids("basics") + ids("beginner") + ids("rhythm"), p.skipLessonIds)
        assertEquals("Begin at Intermediate. Basics, Beginner and Rhythm stay open.", p.line)
        assertEquals(HomeFirst.COURSE, p.homeFirst)
    }

    @Test fun aLotStartsAtKeysLearnOpensThereAndSongsComeFirst() {
        val p = Placement.startFor(Level.A_LOT, ProfileKind.ADULT)
        assertEquals("keys", p.startUnitId); assertEquals("keys", p.learnOpensAt)
        assertEquals(ids("basics") + ids("beginner") + ids("rhythm") + ids("intermediate"), p.skipLessonIds)
        assertEquals(HomeFirst.SONGS, p.homeFirst)
        assertTrue(Level.entries.filter { it != Level.A_LOT }.all { Placement.startFor(it, ProfileKind.ADULT).homeFirst == HomeFirst.COURSE })
    }

    @Test fun aGrownUpsPlanNeverSkipsThePlaygroundExtrasOrTheStartAndLater() {
        for (l in Level.entries) {
            val p = Placement.startFor(l, ProfileKind.ADULT)
            assertTrue(p.skipLessonIds.none(::isPlaygroundLesson))
            assertFalse(p.startLessonId in p.skipLessonIds)
            assertEquals(p.skipLessonIds.distinct(), p.skipLessonIds)
        }
    }

    @Test fun kidsFirstTimeStartsInTheGardenWithNothingSkipped() {
        val p = Placement.startFor(Level.NEVER, ProfileKind.KID, reader = false)
        assertEquals("playground", p.startUnitId); assertEquals("playground-1", p.startLessonId)
        assertTrue(p.skipLessonIds.isEmpty())
    }

    @Test fun kidsWhoHavePlayedABitStartAtThePondWithTheGardenSkipped() {
        val p = Placement.startFor(Level.A_LITTLE, ProfileKind.KID, reader = false)
        assertEquals("playground-pond", p.startUnitId); assertEquals("pond-1", p.startLessonId)
        assertEquals(page("garden"), p.skipLessonIds)
        assertTrue(p.comeBackLessonIds.isEmpty())
        assertEquals("Begin at the pond. The garden stays open.", p.line)
    }

    @Test fun kidsWhoHaveHadLessonsStartAtTheWorkshopAndTheHallsConcertStaysAsAComeBackStone() {
        val p = Placement.startFor(Level.SOME, ProfileKind.KID, reader = false)
        assertEquals("workshop-1", p.startLessonId)
        val hall = page("hall")
        assertEquals(listOf(hall.last()), p.comeBackLessonIds)
        assertEquals(page("garden") + page("pond") + hall.dropLast(1), p.skipLessonIds)
        assertFalse(hall.last() in p.skipLessonIds)
        // Only the sentence's first word is capitalised ("The pond" read "The garden, The pond and ..." before 2.6.0).
        assertEquals("Begin at the workshop. The garden, the pond and the concert hall stay open.", p.line)
        assertEquals(listOf("playground", "playground-pond"), p.skipUnitIds)
    }

    @Test fun aKidNeverGetsAGrownUpLevelAndAReaderStartsOnTheShortList() {
        assertEquals(Placement.startFor(Level.SOME, ProfileKind.KID), Placement.startFor(Level.A_LOT, ProfileKind.KID))
        assertEquals(listOf(Level.NEVER, Level.A_LITTLE, Level.SOME), Level.kid)
        assertEquals("playground-2", Placement.startFor(Level.NEVER, ProfileKind.KID, reader = true).startLessonId)
        assertEquals("pond-1", Placement.startFor(Level.A_LITTLE, ProfileKind.KID, reader = true).startLessonId)
    }

    @Test fun theLinesAreTheHonestOnes() {
        assertEquals("Never: I'm starting from the very first key", Level.NEVER.adultText)
        assertEquals("A little: I know some keys and can play a simple tune", Level.A_LITTLE.adultText)
        assertEquals("Some: I play with both hands and read a bit", Level.SOME.adultText)
        assertEquals("A lot: I'm here for songs and technique", Level.A_LOT.adultText)
        assertEquals(listOf("First time at a piano", "Has played a bit", "Has had lessons"), Level.kid.map { it.kidLine })
        assertEquals(Level.NEVER, Level.parse(null)); assertEquals(Level.NEVER, Level.parse("nonsense")); assertEquals(Level.SOME, Level.parse("SOME"))
    }

    @Test fun reconcileSkipsOnlyWhatIsNotDoneAndReopensWhatThePlanNoLongerSkips() {
        val plan = Placement.startFor(Level.SOME, ProfileKind.ADULT)
        val played = setOf(ids("basics")[0])                          // really played, not skipped
        val r = Placement.reconcile(plan, { it in played }, emptySet())
        assertFalse(played.first() in r.skip)
        assertEquals(plan.skipLessonIds.size - 1, r.skip.size)
        assertTrue(r.reopen.isEmpty())
        // A lower level later: what was skipped and is no longer is reopened; the played one is never touched.
        val lower = Placement.startFor(Level.A_LITTLE, ProfileKind.ADULT)
        val skipped = plan.skipLessonIds.toSet() - played
        val back = Placement.reconcile(lower, { it in played || it in skipped }, skipped)
        assertEquals((ids("beginner") + ids("rhythm")).sorted(), back.reopen)
        assertTrue(back.skip.isEmpty())
        // The same level again changes nothing.
        assertFalse(Placement.reconcile(plan, { it in played || it in skipped }, skipped).changes)
    }

    @Test fun afterASkipTheNextLessonIsTheStartAndTheSkippedStayOpenToTap() {
        for (l in Level.adult) {
            val plan = Placement.startFor(l, ProfileKind.ADULT)
            val skipped = plan.skipLessonIds.toSet()
            val path = CoursePath("basics", startAt = { plan.startLessonId.takeIf { l != Level.NEVER } })
            assertEquals(plan.startLessonId, path.next { it in skipped }?.id)
            // Next lesson from the last skipped one leads to the start; a skipped lesson is still on the path to open.
            plan.skipLessonIds.lastOrNull()?.let { last -> assertEquals(plan.startLessonId, path.after(path.lesson(last)!!) { it in skipped }?.id) }
            skipped.forEach { assertTrue(path.lesson(it) != null) }
        }
    }

    @Test fun aKidPlacedAtTheWorkshopWalksOnFromItAndTheRecitalWaits() {
        val plan = Placement.startFor(Level.SOME, ProfileKind.KID, reader = false)
        val skipped = plan.skipLessonIds.toSet()
        val path = CoursePath("playground", startAt = { plan.startLessonId })
        assertEquals("workshop-1", path.next { it in skipped }?.id)
        // The recital is behind the start and not done: it only comes round once everything after is done.
        val after = path.lessons.map { it.second.id }.dropWhile { it != "workshop-1" }.toSet()
        assertEquals("hall-12", path.next { it in skipped || it in after }?.id)
        // Without the start, the recital would be the next stone.
        assertEquals("hall-12", CoursePath("playground").next { it in skipped }?.id)
    }

    @Test fun theKidMapShowsSkippedStonesOpenWithNoStarsAndNeverWalkedPages() {
        val plan = Placement.startFor(Level.SOME, ProfileKind.KID, reader = false)
        val skipped = plan.skipLessonIds.toSet()
        val path = CoursePath("playground", startAt = { plan.startLessonId })
        val m = KidMap.build(path, { it in skipped }, { 3 }, emptySet(), skipped = { it in skipped })
        assertEquals("workshop-1", m.next)
        val garden = m.pages.first { it.page.id == "garden" }
        assertTrue(garden.stones.all { it.state == StoneState.OPEN && it.stars == 0 && it.skipped })
        assertFalse(garden.walked)
        assertEquals(StoneState.OPEN, m.stone("hall-12")!!.state)
        assertFalse(m.stone("hall-12")!!.skipped)
        assertEquals(StoneState.NEXT, m.stone("workshop-1")!!.state)
        assertEquals(StoneState.LATER, m.stone("treehouse-1")!!.state)
        assertFalse(m.roadOpen)
    }

    @Test fun noFinaleIsOwedWhenNothingWasPlayed() {
        // A finale looks at played lessons: a skipped page has none (the app passes Prefs.lessonPlayed).
        assertNull(com.bigpapituna.sharpears.engine.kid.Finale.owed({ false }, emptySet()))
    }

    @Test fun profilesKeepTheirLevelAndOldOnesReadAsNever() {
        val p = Profile.kid("Mia", 1, false, id = "p3").copy(level = Level.A_LITTLE)
        assertEquals(Level.A_LITTLE, Profile.decode(p.encode())!!.level)
        val old = Profile.decode("""{"id":"p2","name":"Sam","kind":"ADULT"}""")!!
        assertEquals(Level.NEVER, old.level)
        // A switch of kind starts the level over: a kid's pages mean nothing on a grown-up's units.
        assertEquals(Level.NEVER, p.withKind(ProfileKind.ADULT).level)
    }
}
