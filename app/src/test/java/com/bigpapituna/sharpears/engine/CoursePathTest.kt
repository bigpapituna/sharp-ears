package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.ANY_OF_LESSONS
import com.bigpapituna.sharpears.engine.playground.HALL
import com.bigpapituna.sharpears.engine.playground.HALL_ID
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID
import com.bigpapituna.sharpears.engine.playground.POND
import com.bigpapituna.sharpears.engine.playground.POND_ID
import com.bigpapituna.sharpears.engine.playground.isPlaygroundLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePathTest {
    private val adult = CoursePath(Profile.firstUnitId(ProfileKind.ADULT))
    private val kid = CoursePath(Profile.firstUnitId(ProfileKind.KID))
    private val bigKid = CoursePath(Profile.firstUnitId(ProfileKind.KID), bigKid = true)

    @Test fun kidsStartInThePlaygroundGrownUpsAtBasics() {
        // 2.2: a kid walks the Playground's pages (garden, pond, concert hall; 2.3 the workshop, the treehouse, the night
        // sky and the fair), then Basics.
        val pages = com.bigpapituna.sharpears.engine.playground.PLAYGROUND_LEVELS.map { it.id }
        assertEquals(listOf(PLAYGROUND_ID, POND_ID, HALL_ID), pages.take(3))
        assertEquals(pages + "basics", kid.levels.take(pages.size + 1).map { it.id })
        assertEquals("basics", adult.levels.first().id)
        assertEquals("the playground's pages come last for a grown-up", pages, adult.levels.takeLast(pages.size).map { it.id })
        assertEquals(COURSE.size, adult.levels.size)
        assertTrue(adult.levels.takeLast(pages.size).all(adult::isExtra))
        assertEquals("only the pages are extras", pages.size, adult.levels.count(adult::isExtra))
        assertTrue(kid.levels.none(kid::isExtra))
    }

    @Test fun aGrownUpIsNeverSentToThePlayground() {
        assertEquals("sit", adult.next { false }?.id)
        // Every main lesson done: nothing to suggest, even with the Playground untouched.
        val main = adult.counted.map { it.id }.toSet()
        assertNull(adult.next { it in main })
        assertTrue(adult.counted.none { isPlaygroundLesson(it.id) })
        // The pond and the concert hall are extras too: opened by hand, Next stays on their page.
        assertEquals("pond-2", adult.after(POND.lessons.first())?.id)
        assertNull(adult.after(HALL.lessons.last()))
        // The last lesson of the course has no "Next lesson" into the Playground.
        assertNull(adult.after(adult.counted.last()))
        // Inside the Playground (opened by hand), Next stays in it.
        val p1 = COURSE.first { it.id == PLAYGROUND_ID }.lessons.first()
        assertEquals("playground-2", adult.after(p1)?.id)
        assertNull(adult.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons.last()))
    }

    @Test fun aKidWalksThePlaygroundPagesThenBasics() {
        assertEquals("playground-1", kid.next { false }?.id)
        val pg = COURSE.first { it.id == PLAYGROUND_ID }.lessons
        assertEquals("the garden's last game leads to the pond", "pond-1", kid.after(pg.last())?.id)
        assertEquals("hall-1", kid.after(POND.lessons.last())?.id)
        // 2.3: the recital leads on to the workshop, the workshop to the treehouse, the treehouse to the night sky, the night
        // sky to the fair, and the fair's last game to the big kids' road.
        assertEquals("the recital leads to the workshop", "workshop-1", kid.after(HALL.lessons.last())?.id)
        assertEquals("the workshop's concert leads to the treehouse", "treehouse-1", kid.after(com.bigpapituna.sharpears.engine.playground.WORKSHOP.lessons.last())?.id)
        assertEquals("the treehouse's concert leads to the night sky", "night-1", kid.after(com.bigpapituna.sharpears.engine.playground.TREEHOUSE.lessons.last())?.id)
        assertEquals("the night sky's concert leads to the fair", "fair-1", kid.after(com.bigpapituna.sharpears.engine.playground.NIGHT_SKY.lessons.last())?.id)
        assertEquals("the fair leads to the big kids' road", "sit", kid.after(com.bigpapituna.sharpears.engine.playground.FAIR.lessons.last())?.id)
        assertEquals("pond-1", kid.next { it.startsWith("playground-") }?.id)
        assertEquals("sit", kid.next { isPlaygroundLesson(it) }?.id)
        assertEquals(COURSE.sumOf { it.lessons.size }, kid.counted.size)
    }

    @Test fun aBigKidIsPointedAtTheShortList() {
        assertEquals("playground-2", bigKid.next { false }?.id)
        assertEquals("playground-3", bigKid.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons[1])?.id)
        assertEquals("playground-6", bigKid.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons[2])?.id)
        // A reader skips most of the garden, then walks the pond and the concert hall whole (letters, songs with words).
        val short = setOf("playground-2", "playground-3", "playground-6", "playground-7", "playground-12")
        assertEquals("pond-1", bigKid.next { it in short }?.id)
        assertEquals("pond-1", bigKid.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons.last())?.id)
        assertEquals("workshop-1", bigKid.next { it in short || it.startsWith("pond-") || it.startsWith("hall-") }?.id)
        assertEquals("sit", bigKid.next { it in short || (isPlaygroundLesson(it) && !it.startsWith("playground-")) }?.id)
    }

    @Test fun anyOfLessonsAreTheRangeGames() {
        // 2.3: the night sky's higher or lower (any key above or below the pup's) and its black-key jam.
        assertEquals(setOf("playground-1", "playground-2", "playground-3", "hall-1", "night-5", "night-11"), ANY_OF_LESSONS)
    }

    @Test fun grownUpModeIsOfferedOnceAfterBeginner() {
        val beginner = COURSE.first { it.id == CoursePath.GROWN_UP_AFTER }.lessons.map { it.id }.toSet()
        assertTrue(CoursePath.offerGrownUp(kid = true, offered = false, done = { it in beginner }))
        assertFalse("not before Beginner is finished", CoursePath.offerGrownUp(kid = true, offered = false, done = { it in beginner.drop(1) }))
        assertFalse("once only", CoursePath.offerGrownUp(kid = true, offered = true, done = { it in beginner }))
        assertFalse("grown-ups are grown-ups already", CoursePath.offerGrownUp(kid = false, offered = false, done = { it in beginner }))
    }

    /** Audit: "Some years" said "We'll start you in Intermediate", then Learn, Practice today and the quest all said "sit". */
    @Test fun theOnboardingPlacementIsWhereSuggestionsStart() {
        for (e in com.bigpapituna.sharpears.engine.progress.Experience.entries) {
            val placed = CoursePath(Profile.firstUnitId(ProfileKind.ADULT), startAt = { e.startLessonId })
            assertEquals(e.label, e.startLessonId, placed.next { false }?.id)
        }
        val placed = CoursePath(Profile.firstUnitId(ProfileKind.ADULT), startAt = { "scale-left" })
        val main = placed.counted.map { it.id }
        val from = main.indexOf("scale-left")
        assertEquals(main[from + 1], placed.next { it == "scale-left" }?.id)
        // Everything from the placement on done: the lessons before it come round, from the first.
        assertEquals("sit", placed.next { it in main.drop(from) }?.id)
        assertNull(placed.next { it in main })
        // A lesson after it never pulls the suggestion back to the start.
        assertEquals("scale-left", placed.next { it == "sit" }?.id)
    }
}
