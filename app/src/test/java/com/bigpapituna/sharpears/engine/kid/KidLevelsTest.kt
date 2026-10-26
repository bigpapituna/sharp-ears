package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.family.Audience
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.14 for the kids: "Skip the garden" for a reader who breezes through it, the grown-ups' pop packs for little readers,
 * and the stars a kid can chase (a replay straight to the first graded step, a skip that is never "You did it!").
 */
class KidLevelsTest {
    private val garden = PLAYGROUND_PAGES.first { it.id == "garden" }.lessonIds

    @Test fun skipTheGardenAfterThreeStonesAtThreeStars() {
        fun offered(played: List<String>, stars: Int = 3, level: Level = Level.NEVER, reader: Boolean = true,
                    audience: Audience = Audience.LITTLE_KID, declined: Boolean = false) =
            GardenSkip.offered(audience, reader, level, garden, { it in played }, { if (it in played) stars else 0 }, declined)
        val three = garden.take(3)
        assertTrue(offered(three))
        assertFalse("two is not three", offered(garden.take(2)))
        assertFalse("two stars on one", GardenSkip.offered(Audience.LITTLE_KID, true, Level.NEVER, garden, { it in three },
            { if (it == three[1]) 2 else 3 }, false))
        // In a row: the last three played count, an earlier slip does not.
        val four = garden.take(4)
        assertTrue(GardenSkip.offered(Audience.LITTLE_KID, true, Level.NEVER, garden, { it in four }, { if (it == four[0]) 1 else 3 }, false))
        assertFalse("a pre-reader is not asked", offered(three, reader = false))
        assertFalse("placed past the garden already", offered(three, level = Level.A_LITTLE))
        assertFalse("big kids walk the main course", offered(three, audience = Audience.BIG_KID))
        assertFalse("not now is for good", offered(three, declined = true))
        assertFalse("the garden is walked", offered(garden))
        // Accepting is the "A few" placement: the pond next, the garden's other stones passed by.
        val plan = Placement.planFor(GardenSkip.LEVEL_AFTER, ProfileKind.KID, reader = true)
        assertEquals("pond-1", plan.startLessonId)
        val r = Placement.reconcile(plan, { it in three }, emptySet())
        assertEquals(garden - three.toSet(), r.skip)
        assertTrue(r.reopen.isEmpty())
    }

    @Test fun littleReadersGetThePopPacksAfterTheNurserySongs() {
        assertEquals(listOf("Pop", "Greatest hits", "Rock"), KidPop.CHIPS.map { KidPop.title(it) })
        assertTrue(KidPop.shows(Audience.LITTLE_KID, reader = true))
        assertFalse(KidPop.shows(Audience.LITTLE_KID, reader = false))
        assertFalse("a big kid has the whole list on Songs", KidPop.shows(Audience.BIG_KID, reader = true))
        for (chip in KidPop.CHIPS) {
            val list = KidPop.songs(POP_SONGS, chip)
            assertTrue("$chip has songs", list.size >= 10)
            assertTrue(list.all { s -> s.styles.any { it in chip.styles } })
            assertTrue(list.none { it.id in KidPop.NOT_FOR_LITTLE_KIDS })
            assertEquals(list.sortedWith(PopFind.easiest), list)
        }
        assertTrue(KidPop.NOT_FOR_LITTLE_KIDS.all { id -> POP_SONGS.any { it.id == id } })
        // Any other chip (or none) is the kids' style, as the chord list was before 2.14.
        assertTrue(KidPop.songs(POP_SONGS, null).all { PopSong.Style.KIDS in it.styles })
        assertEquals("Songs by chords", KidPop.title(null))
    }

    @Test fun replaysGoStraightToTheFirstGradedStep() {
        for (l in COURSE.flatMap { it.lessons }) {
            val first = LessonStars.firstGraded(l.steps)
            if (l.steps.none(LessonStars::graded)) { assertEquals(0, first); continue }
            val step = l.steps[first]
            assertTrue(l.id, step.exercise != null && step.kind != StepKind.LISTEN)
            assertTrue("nothing graded before it in ${l.id}", l.steps.take(first).none(LessonStars::graded))
            assertEquals(LessonStars.SKIPPED, LessonStars.ofSkip(step))
        }
        // The garden's second lesson starts on a reading page; its replay does not.
        val two = COURSE.flatMap { it.lessons }.first { it.id == "playground-2" }
        assertEquals(StepKind.READ, two.steps[0].kind)
        assertTrue(LessonStars.firstGraded(two.steps) > 0)
    }

    @Test fun tryForThreeAndTheReplayChip() {
        val l = COURSE.flatMap { it.lessons }.first { it.id == "playground-2" }
        assertTrue(LessonStars.tryForThree(1, l.steps)); assertTrue(LessonStars.tryForThree(2, l.steps))
        assertFalse(LessonStars.tryForThree(3, l.steps)); assertFalse(LessonStars.tryForThree(0, l.steps))
        assertFalse("nothing to chase", LessonStars.tryForThree(1, l.steps.filter { !LessonStars.graded(it) }))
        fun stone(state: StoneState, stars: Int, skipped: Boolean = false) = Stone("x", "X", state, stars, skipped)
        assertTrue(LessonStars.replayChip(stone(StoneState.DONE, 1)))
        assertTrue(LessonStars.replayChip(stone(StoneState.DONE, 2)))
        assertFalse(LessonStars.replayChip(stone(StoneState.DONE, 3)))
        assertFalse(LessonStars.replayChip(stone(StoneState.OPEN, 0, skipped = true)))
        assertFalse(LessonStars.replayChip(stone(StoneState.NEXT, 0)))
    }

    @Test fun aSkippedGradedStepIsNeverYouDidIt() {
        assertEquals(LessonStars.Finish.CELEBRATE, LessonStars.finish(skippedGraded = 0))
        assertEquals(LessonStars.Finish.NEUTRAL, LessonStars.finish(skippedGraded = 1))
        assertEquals("On to the next.", LessonStars.NEUTRAL_TITLE)
        assertEquals("Skip this one?", LessonStars.SKIP_TITLE)
        assertEquals("It stays at one star.", LessonStars.SKIP_LINE)
        for (l in listOf(LessonStars.NEUTRAL_TITLE, LessonStars.NEUTRAL_LINE, LessonStars.SKIP_TITLE, LessonStars.SKIP_LINE,
                LessonStars.SKIP_YES, LessonStars.SKIP_NO, LessonStars.TRY_FOR_THREE, LessonStars.REPLAY,
                GardenSkip.TITLE, GardenSkip.LINE, GardenSkip.SKIP, GardenSkip.LATER)) {
            assertFalse(l, Regex("[–—]|did it|woof", RegexOption.IGNORE_CASE).containsMatchIn(l))
            assertTrue(l, l.first().isUpperCase())
        }
    }
}
