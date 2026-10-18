package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.ANY_OF_LESSONS
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID
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
        assertEquals(PLAYGROUND_ID, kid.levels.first().id)
        assertEquals("basics", kid.levels[1].id)
        assertEquals("basics", adult.levels.first().id)
        assertEquals("the playground comes last for a grown-up", PLAYGROUND_ID, adult.levels.last().id)
        assertEquals(COURSE.size, adult.levels.size)
        assertTrue(adult.isExtra(adult.levels.last()))
        assertFalse(kid.isExtra(kid.levels.first()))
    }

    @Test fun aGrownUpIsNeverSentToThePlayground() {
        assertEquals("sit", adult.next { false }?.id)
        // Every main lesson done: nothing to suggest, even with the Playground untouched.
        val main = adult.counted.map { it.id }.toSet()
        assertNull(adult.next { it in main })
        assertTrue(adult.counted.none { it.id.startsWith("playground-") })
        // The last lesson of the course has no "Next lesson" into the Playground.
        assertNull(adult.after(adult.counted.last()))
        // Inside the Playground (opened by hand), Next stays in it.
        val p1 = COURSE.first { it.id == PLAYGROUND_ID }.lessons.first()
        assertEquals("playground-2", adult.after(p1)?.id)
        assertNull(adult.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons.last()))
    }

    @Test fun aKidWalksThePlaygroundThenBasics() {
        assertEquals("playground-1", kid.next { false }?.id)
        val pg = COURSE.first { it.id == PLAYGROUND_ID }.lessons
        assertEquals("sit", kid.after(pg.last())?.id)
        assertEquals("sit", kid.next { it.startsWith("playground-") }?.id)
        assertEquals(COURSE.sumOf { it.lessons.size }, kid.counted.size)
    }

    @Test fun aBigKidIsPointedAtTheShortList() {
        assertEquals("playground-2", bigKid.next { false }?.id)
        assertEquals("playground-3", bigKid.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons[1])?.id)
        assertEquals("playground-6", bigKid.after(COURSE.first { it.id == PLAYGROUND_ID }.lessons[2])?.id)
        assertEquals("sit", bigKid.next { it in setOf("playground-2", "playground-3", "playground-6", "playground-7", "playground-12") }?.id)
    }

    @Test fun anyOfLessonsAreTheRangeGames() {
        assertEquals(setOf("playground-1", "playground-2", "playground-3"), ANY_OF_LESSONS)
    }

    @Test fun grownUpModeIsOfferedOnceAfterBeginner() {
        val beginner = COURSE.first { it.id == CoursePath.GROWN_UP_AFTER }.lessons.map { it.id }.toSet()
        assertTrue(CoursePath.offerGrownUp(kid = true, offered = false, done = { it in beginner }))
        assertFalse("not before Beginner is finished", CoursePath.offerGrownUp(kid = true, offered = false, done = { it in beginner.drop(1) }))
        assertFalse("once only", CoursePath.offerGrownUp(kid = true, offered = true, done = { it in beginner }))
        assertFalse("grown-ups are grown-ups already", CoursePath.offerGrownUp(kid = false, offered = false, done = { it in beginner }))
    }
}
