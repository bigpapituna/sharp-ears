package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
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
        assertEquals(listOf("basics", "beginner", "intermediate", "advanced"), COURSE.map { it.id })
        for (lesson in COURSE.flatMap { it.lessons }) {
            assertTrue("${lesson.id} has text", lesson.body.isNotEmpty() && lesson.body.none { '—' in it || '–' in it })
            assertTrue("${lesson.id} has something to play", lesson.exercise != null || lesson.songId != null)
            val ex = lesson.exercise ?: continue
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

    @Test fun fivePositionLessonsUseOneFingerPerKey() {
        val right = COURSE.flatMap { it.lessons }.first { it.id == "fingers-right" }.exercise!!.toSong("x", "x")
        val f = Fingering.of(right)
        assertEquals(listOf(1, 2, 3, 4, 5, 4, 3, 2, 1), right.notes.map { f[it] })
        val left = COURSE.flatMap { it.lessons }.first { it.id == "fingers-left" }.exercise!!.toSong("x", "x")
        val g = Fingering.of(left)
        assertEquals(listOf(5, 4, 3, 2, 1, 2, 3, 4, 5), left.notes.map { g[it] })
    }
}
