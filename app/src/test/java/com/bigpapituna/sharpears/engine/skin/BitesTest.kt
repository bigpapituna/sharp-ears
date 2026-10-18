package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kid bites: an exercise cut at its phrase end, one scene before "More?", one run passes. */
class BitesTest {
    private val twinkle = Exercise(right = "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2",
        left = "C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2", bpm = 80.0, fingers = "1 1 5 5 5 5 5 4 4 3 3 2 2 1")

    @Test fun cutsBothHandsAtThePhraseEnd() {
        val bite = Bites.cut(twinkle, 8.0)                      // two bars of 4/4: "Twinkle twinkle little star"
        assertEquals("C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2", bite.right)
        assertEquals("C3:4 F3:2 C3:2", bite.left)
        assertEquals("1 1 5 5 5 5 5", bite.fingers)              // the book fingering follows the notes kept
        assertEquals(8.0, Bites.beats(bite.right), 1e-9)
        assertEquals(twinkle.bpm, bite.bpm, 0.0)
    }

    @Test fun aNoteRingingOverTheCutIsShortenedAndTrailingRestsGo() {
        assertEquals("C4:1 D4:1 E4:2", Bites.cutLine("C4:1 D4:1 E4:4 F4:1", 4.0))
        assertEquals("C4:0.5 D4:0.5", Bites.cutLine("C4:0.5 D4:0.5 r:1 E4:1", 2.0))
        assertEquals("C4+E4+G4:1.5", Bites.cutLine("C4+E4+G4:4", 1.5))
        assertEquals("", Bites.cutLine("r:4 C4:1", 2.0))
    }

    @Test fun nullOrPastTheEndPlaysTheWholeExercise() {
        assertSame(twinkle, Bites.cut(twinkle, null))
        assertSame(twinkle, Bites.cut(twinkle, 16.0))
        assertSame(twinkle, Bites.cut(twinkle, 99.0))
        assertSame(twinkle, Bites.cut(twinkle, 0.0))
        assertEquals(12.0, Bites.barsToBeats(4, Exercise(beats = 3)), 0.0)
    }

    @Test fun theBitePlaysAsAShorterSong() {
        val whole = twinkle.toSong("t", "t")
        val bite = Bites.cut(twinkle, 8.0).toSong("t", "t")
        assertEquals(7 + 3, bite.notes.size)
        assertTrue(bite.durationMs < whole.durationMs / 2 + 1000)
        assertTrue(bite.notes.all { it.startMs < 8 * 60_000 / 80 })
    }

    @Test fun everyCourseExerciseCutsAtBarFourAndStillParses() {
        for (lesson in COURSE.flatMap { it.lessons }) for (step in lesson.steps) {
            val ex = step.exercise ?: continue
            val end = Bites.barsToBeats(4, ex)
            val bite = Bites.cut(ex, end)
            val song = bite.toSong(lesson.id, lesson.title)
            val full = ex.toSong(lesson.id, lesson.title)
            assertTrue("${lesson.id}: ${song.notes.size} of ${full.notes.size}", song.notes.size <= full.notes.size)
            assertTrue(lesson.id, Bites.beats(bite.right) <= end + 1e-9 && Bites.beats(bite.left) <= end + 1e-9)
            // The notes kept are exactly the ones that started before the cut.
            val msPerBeat = 60_000 / ex.bpm
            assertEquals(lesson.id, full.notes.count { it.startMs < (end * msPerBeat).toLong() }, song.notes.size)
        }
    }

    @Test fun kidStepsAreCutGrownUpStepsUntouched() {
        val play = Step(StepKind.PLAY, "Your turn.", twinkle)
        assertEquals("C4:1 C4:1 G4:1 G4:1", Bites.kidStep(play, 4.0).exercise!!.right)
        assertEquals(play, Bites.kidStep(play, null))
        val read = Step(StepKind.READ, "Read")
        assertSame(read, Bites.kidStep(read, 4.0))
        val ramp = Bites.kidStep(Step(StepKind.RAMP, "Ramp", twinkle), null)
        assertEquals(StepKind.PERFORM, ramp.kind)                             // one run, no climbing
        assertEquals(twinkle.bpm * Bites.KID_RAMP_SPEED, ramp.exercise!!.bpm, 1e-9)
        val pedal = Step(StepKind.PEDAL, "Pedal", twinkle)
        assertSame(pedal, Bites.kidStep(pedal, 4.0))                          // MIDI steps keep their whole exercise
        // The phrase end is the exercise's own (Exercise.phraseEnd); none: the whole exercise.
        assertEquals(null, Bites.phraseEndBeats(twinkle))
        assertEquals(8.0, Bites.phraseEndBeats(twinkle.copy(phraseEnd = 8.0))!!, 1e-9)
        assertEquals("C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2", Bites.kidStep(Step(StepKind.PLAY, "Play", twinkle.copy(phraseEnd = 8.0))).exercise!!.right)
    }

    @Test fun oneRunPassesForAKid() {
        fun report(recap: Recap?, wrong: Int = 0) = Report(emptyList(), 0, wrong, recap)
        val perform = Step(StepKind.PERFORM, "In time", twinkle.copy(pass = 80))
        assertTrue(Bites.kidPassed(perform, report(Recap(6, 1, 3, 0, 4))))          // 70%: two stars, a kid passes
        assertFalse(perform.passed(report(Recap(6, 1, 3, 0, 4))))                   // a grown-up needs 80
        assertFalse(Bites.kidPassed(perform, report(Recap(3, 1, 6, 0, 2))))         // one star: once more
        assertFalse(Bites.kidPassed(perform, report(null)))
        val play = Step(StepKind.PLAY, "Your turn", twinkle)
        assertTrue(Bites.kidPassed(play, report(null, wrong = 9)))                  // a wait run passes at its end
        val dyn = Step(StepKind.DYNAMICS, "Soft, loud", twinkle.copy(pass = 80))
        assertFalse(Bites.kidPassed(dyn, Report(emptyList(), 0, 0, null, extraScore = 50)))
    }

    @Test fun readingStepsShowOneSceneUntilMore() {
        assertEquals(1, Bites.scenesShown(3, kid = true, more = false))
        assertEquals(3, Bites.scenesShown(3, kid = true, more = true))
        assertEquals(3, Bites.scenesShown(3, kid = false, more = false))
        assertEquals(0, Bites.scenesShown(0, kid = true, more = false))
    }
}
