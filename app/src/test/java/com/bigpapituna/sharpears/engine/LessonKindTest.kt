package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icon kind of each course lesson on the Learn path. */
class LessonKindTest {
    private val all = COURSE.flatMap { it.lessons }
    private fun byId(id: String) = all.first { it.id == id }

    @Test fun songLessonsAreSongs() {
        all.filter { it.songId != null }.forEach { assertEquals(it.id, LessonKind.SONG, it.kind) }
        assertEquals(LessonKind.SONG, byId("rep-mary").kind)
    }

    @Test fun gradedRunsArePerform() {
        assertEquals(LessonKind.PERFORM, byId("rhythm-quarters").kind)
        assertEquals(LessonKind.PERFORM, byId("pedal").kind)
        assertEquals(LessonKind.PERFORM, byId("dynamics").kind)
    }

    @Test fun playAlongIsPlay() {
        assertEquals(LessonKind.PLAY, byId("lh-waltz").kind)
    }

    @Test fun readAndListenOnly() {
        val read = CourseLesson("r", "R", "s", listOf(Step(StepKind.READ, "x")))
        assertEquals(LessonKind.READ, read.kind)
        val ex = Exercise(right = "C4:1", bpm = 60.0)
        assertEquals(LessonKind.LISTEN, read.copy(steps = read.steps + Step(StepKind.LISTEN, "x", ex)).kind)
    }

    @Test fun everyStepHasAnIconKind() {
        assertEquals(LessonKind.PLAY, Step(StepKind.FROM_MEMORY, "x").lessonKind)
        assertEquals(LessonKind.PERFORM, Step(StepKind.RAMP, "x").lessonKind)
        // The path shows more than one kind of stop.
        assertTrue(all.map { it.kind }.toSet().size >= 3)
    }
}
