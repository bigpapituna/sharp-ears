package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SightReadingTest {
    private val major = listOf(0, 2, 4, 5, 7, 9, 11)

    @Test fun everyGeneratedPieceIsPlayable() {
        for (level in 1..2) for (seed in 0L until 300L) {
            val p = sightPiece(level, seed)
            val song = p.exercise.toSong("s", "s")
            val right = song.notes.filter { it.hand == Hand.RIGHT }
            val left = song.notes.filter { it.hand == Hand.LEFT }
            val scale = major.map { (it + p.key) % 12 }.toSet()
            val tag = "level $level seed $seed ${p.exercise.right} | ${p.exercise.left}"
            // In key, in range, four whole bars, ending on the tonic.
            assertTrue(tag, song.notes.all { it.midi % 12 in scale })
            assertTrue(tag, right.all { it.midi in 53..81 })
            assertEquals(tag, 16.0, p.exercise.right.split(" ").sumOf { it.substringAfter(':').toDouble() }, 1e-9)
            assertEquals(tag, p.key, right.last().midi % 12)
            // Mostly stepwise: never more than a fifth between neighbours.
            assertTrue(tag, right.zipWithNext().all { (a, b) -> abs(a.midi - b.midi) <= 7 })
            // Level 1 has no eighths and no left hand; level 2 has the bar's root in the bass.
            if (level == 1) {
                assertTrue(tag, left.isEmpty() && p.key == 0)
                assertTrue(tag, p.exercise.right.split(" ").none { it.endsWith(":0.5") })
            } else {
                assertEquals(tag, 4, left.size)
                assertTrue(tag, left.all { it.midi in 36..59 })
            }
            // A fingering exists for every note.
            val f = Fingering.of(song)
            assertTrue(tag, song.notes.all { f[it] in 1..5 })
        }
    }

    @Test fun seededAndVaried() {
        assertEquals(sightPiece(2, 42), sightPiece(2, 42))
        assertNotEquals(sightPiece(1, 1).exercise.right, sightPiece(1, 2).exercise.right)
        assertTrue((0L until 30L).map { sightPiece(2, it).key }.toSet() == setOf(0, 7, 5))      // C, G and F major
        assertTrue((0L until 30L).any { sightPiece(2, it).exercise.right.contains(":0.5") })
        assertTrue(sightPiece(2, (0L until 50L).first { sightPiece(2, it).key == 5 }).exercise.toSong("f", "f").preferFlats)
    }

    @Test fun scoreIsTimePlusTwoSecondsPerWrongNote() {
        val s = SightScore.of(41_600.0, 2)
        assertEquals(42, s.seconds); assertEquals(46, s.points)
        assertEquals("0:42, 2 wrong", s.label)
        assertEquals(s, SightScore.decode(s.encode()))
        assertEquals("1:05, no wrong notes", SightScore(65, 0).label)
    }
}
