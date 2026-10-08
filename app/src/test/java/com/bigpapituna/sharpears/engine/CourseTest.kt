package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test fun noteNames() {
        assertEquals(60, noteNumber("C4")); assertEquals(54, noteNumber("F#3")); assertEquals(46, noteNumber("Bb2")); assertEquals(21, noteNumber("A0"))
    }

    @Test fun lineTimingRestsAndChords() {
        val notes = parseLine("C4:1 r:1 C4+E4+G4:2", Hand.RIGHT, 500.0)
        assertEquals(listOf(60, 60, 64, 67), notes.map { it.midi })
        assertEquals(listOf(0L, 1000L, 1000L, 1000L), notes.map { it.startMs })
    }

    @Test fun everyLessonIsUsable() {
        val ids = COURSE.flatMap { it.lessons }.map { it.id }
        assertEquals("unique lesson ids", ids.size, ids.toSet().size)
        assertEquals(27, ids.size)
        assertEquals(listOf("basics", "beginner", "intermediate", "advanced"), COURSE.map { it.id })
        for (lesson in COURSE.flatMap { it.lessons }) {
            assertTrue("${lesson.id} has 3 to 5 steps", lesson.steps.size in 3..5)
            assertEquals("${lesson.id} starts by reading", StepKind.READ, lesson.steps.first().kind)
            assertTrue("${lesson.id} has something to play", lesson.steps.any { it.exercise != null })
            if (lesson.songId != null) assertEquals("${lesson.id} ends by opening its song", StepKind.READ, lesson.steps.last().kind)
            for (step in lesson.steps) {
                assertTrue("${lesson.id} step text", step.text.isNotBlank() && step.text.none { it in "—–·" })
                assertEquals("${lesson.id}: only reading steps have no exercise", step.kind == StepKind.READ, step.exercise == null)
                val ex = step.exercise ?: continue
                val song = ex.toSong(lesson.id, lesson.title)
                assertTrue("${lesson.id} has notes", song.notes.isNotEmpty())
                // The practiced hand has notes, and every note gets a suggested finger.
                when (ex.practice) {
                    Practice.LEFT -> assertTrue(lesson.id, song.notes.any { it.hand == Hand.LEFT })
                    Practice.RIGHT -> assertTrue(lesson.id, song.notes.any { it.hand == Hand.RIGHT })
                    Practice.BOTH -> {}
                }
                val f = Fingering.of(song)
                assertTrue("${lesson.id} fingering", song.notes.all { f[it] in 1..5 })
            }
        }
    }

    @Test fun stepKindsSetTheModeAndLabels() {
        val ex = Exercise(right = "C4:1")
        assertEquals(Mode.LISTEN, Step(StepKind.LISTEN, "x", ex).mode)
        assertEquals(Mode.WAIT, Step(StepKind.PLAY, "x", ex).mode)
        assertEquals(Mode.WAIT, Step(StepKind.FROM_MEMORY, "x", ex).mode)
        assertEquals(Mode.PERFORM, Step(StepKind.PERFORM, "x", ex).mode)
        assertTrue(Step(StepKind.PLAY, "x", ex).labels)
        assertFalse(Step(StepKind.FROM_MEMORY, "x", ex).labels)
    }

    @Test fun performStepsPassAtTheirMark() {
        val step = Step(StepKind.PERFORM, "x", Exercise(right = "C4:1", pass = 80))
        assertTrue(step.passed(Recap(good = 8, ok = 0, miss = 2, biasMs = 0, longestStreak = 8)))
        assertFalse(step.passed(Recap(good = 7, ok = 0, miss = 3, biasMs = 0, longestStreak = 7)))
        assertFalse("a Perform step needs a run", step.passed(null))
        assertTrue(Step(StepKind.PLAY, "x", Exercise(right = "C4:1")).passed(null))
    }

    @Test fun fivePositionLessonsUseOneFingerPerKey() {
        fun ex(id: String) = COURSE.flatMap { it.lessons }.first { it.id == id }.steps.first { it.kind == StepKind.PLAY }.exercise!!
        val right = ex("fingers-right").toSong("x", "x")
        val f = Fingering.of(right)
        assertEquals(listOf(1, 2, 3, 4, 5, 4, 3, 2, 1), right.notes.map { f[it] })
        val left = ex("fingers-left").toSong("x", "x")
        val g = Fingering.of(left)
        assertEquals(listOf(5, 4, 3, 2, 1, 2, 3, 4, 5), left.notes.map { g[it] })
    }
}
