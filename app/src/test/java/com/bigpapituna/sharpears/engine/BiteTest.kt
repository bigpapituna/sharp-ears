package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1: Exercise.phraseEnd marks where the first phrase ends; the kid skin plays only that much ([bite]). */
class BiteTest {
    @Test fun phraseEndCutsTheKidBite() {
        val buns = BUNS_X.bite()
        assertEquals("E4:1 D4:1 C4:2 E4:1 D4:1 C4:2", buns.right)
        assertEquals("C3:4 C3:4", buns.left)
        assertNull(buns.phraseEnd)
        assertEquals("Mary had a little lamb", "E4:1 D4:1 C4:1 D4:1 E4:1 E4:1 E4:2", MARY_X.bite().right)
        assertEquals("C3:4 C3:4", MARY_X.bite().left)
        // A note that runs past the cut ends there; later notes, rests and fingers go.
        val ex = Exercise(right = "C4:3 D4:3 r:1 E4:1", left = "C3:2 r:2 G2:4", fingers = "1 2 3", phraseEnd = 4.0)
        assertEquals("C4:3 D4:1", ex.bite().right)
        assertEquals("C3:2 r:2", ex.bite().left)
        assertEquals("1 2", ex.bite().fingers)
        val plain = Exercise(right = "C4:1")
        assertSame("no phrase end: already a bite", plain, plain.bite())
        // Alternatives survive the cut.
        assertEquals("C#4|D#4:1", Exercise(right = "C#4|D#4:1 F#4|G#4:1", phraseEnd = 1.0).bite().right)
        // Every phrase end in the course sits inside its exercise and leaves a shorter, playable bite.
        val cut = COURSE.flatMap { it.lessons }.flatMap { it.steps }.mapNotNull { it.exercise }.filter { it.phraseEnd != null }.distinct()
        assertTrue("phrase ends on Mary, Hot Cross Buns, Twinkle, the C position and C-G lessons: ${cut.size}", cut.size >= 7)
        for (e in cut) {
            val total = parseTokens(e.right).sumOf { it.beats }
            assertTrue("${e.right}: cut at ${e.phraseEnd} of $total", e.phraseEnd!! > 0 && e.phraseEnd!! < total)
            val bite = e.bite().toSong("b", "b")
            assertTrue(bite.notes.isNotEmpty() && bite.durationMs < e.toSong("w", "w").durationMs)
            assertTrue("whole bars", e.phraseEnd!! % e.beats == 0.0 || e.phraseEnd!! < e.beats * 2)
        }
    }
}
