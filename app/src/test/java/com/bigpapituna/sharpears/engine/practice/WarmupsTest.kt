package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.parseLine
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupsTest {
    @Test fun everyWarmupInEveryKeyParsesAndFits() {
        for (root in 0..11) for (w in warmupCatalog(root)) {
            val what = w.id
            assertTrue(what, w.level in 1..3)
            assertTrue(what, w.bpmMin in 30 until w.bpmMax && w.bpmMax <= 140)
            assertTrue(what, w.title.isNotBlank() && w.description.isNotBlank())
            val ex = w.at(w.defaultBpm)
            val ms = 60_000 / ex.bpm
            val right = parseLine(ex.right, Hand.RIGHT, ms)
            val left = parseLine(ex.left, Hand.LEFT, ms)
            assertTrue(what, right.isNotEmpty() && left.isNotEmpty())
            assertTrue(what, (right + left).all { it.midi in 36..96 })
            assertTrue(what, w.defaultBpm in w.bpmMin..w.bpmMax)
            // The fingering, when given, is one number per right-hand note and the song builds.
            if (ex.fingers.isNotEmpty()) assertEquals(what, right.size, ex.fingers.split(" ").size)
            assertTrue(what, ex.toSong(w.id, w.title).notes.size == right.size + left.size)
            assertTrue(what, w.seconds() in 5.0..120.0)
        }
    }

    @Test fun contraryMotionOnlyInTheKeysThatShareFingering() {
        assertTrue(warmupCatalog(0).any { it.id.startsWith("contrary") })
        assertTrue(warmupCatalog(1).none { it.id.startsWith("contrary") })
        val w = contraryScale(0)
        val r = parseLine(w.exercise.right, Hand.RIGHT, 500.0).map { it.midi }
        val l = parseLine(w.exercise.left, Hand.LEFT, 500.0).map { it.midi }
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60), r)
        assertEquals(listOf(60, 59, 57, 55, 53, 52, 50, 48, 50, 52, 53, 55, 57, 59, 60), l)
    }

    @Test fun hanonOneIsTheBookPattern() {
        val w = hanon(HANON_PATTERNS[0], 0)
        val r = parseLine(w.exercise.right, Hand.RIGHT, 1000.0).map { it.midi }
        // C E F G A G F E, D F G A B A G F, then E G A B C ...
        assertEquals(listOf(60, 64, 65, 67, 69, 67, 65, 64, 62, 65, 67, 69, 71, 69, 67, 65, 64, 67, 69, 71, 72), r.take(21))
        assertEquals("1 2 3 4 5 4 3 2 1 2 3 4 5 4 3 2", w.exercise.fingers.split(" ").take(16).joinToString(" "))
        // Both hands in parallel octaves.
        val l = parseLine(w.exercise.left, Hand.LEFT, 1000.0).map { it.midi }
        assertEquals(r.map { it - 12 }, l)
        assertEquals(60, r.last())
    }

    @Test fun hanonPatternsAreFiveFingerShapes() {
        for (p in HANON_PATTERNS) {
            assertEquals(8, p.degrees.size)
            assertEquals(0, p.degrees.first())
            assertTrue(p.degrees.max() <= 5)
            assertEquals(5, p.degrees.distinct().size)
        }
        assertEquals(5, HANON_PATTERNS.size)
    }

    @Test fun keysAreTransposed() {
        val g = fiveFinger(7)
        assertTrue(g.exercise.right.startsWith("G3:1 A3:1 B3:1 C4:1 D4:1 C4:1"))
        assertTrue(fiveFinger(10).exercise.flats)
        assertTrue(fiveFinger(10).exercise.right.startsWith("Bb3:1"))
        val m = parseLine(fiveFinger(0, minor = true).exercise.right, Hand.RIGHT, 1000.0).map { it.midi }
        assertEquals(listOf(60, 62, 63, 65, 67, 65, 63, 62, 60), m)
    }

    @Test fun handIndependenceHoldsOneHandAndWalksTheOther() {
        val w = handIndependence(0, leftHolds = false)
        val r = parseLine(w.exercise.right, Hand.RIGHT, 1000.0)
        val l = parseLine(w.exercise.left, Hand.LEFT, 1000.0)
        assertEquals(12, r.size)                                             // four triads
        assertTrue(r.all { it.durationMs > 3000 })
        assertEquals(16, l.size)                                             // four notes a bar
        assertTrue(l.all { it.durationMs < 1000 })
        val v = handIndependence(0, leftHolds = true)
        assertEquals(16, parseLine(v.exercise.right, Hand.RIGHT, 1000.0).size)
        assertEquals(12, parseLine(v.exercise.left, Hand.LEFT, 1000.0).size)
    }

    @Test fun trillsAndRepeatedNotes() {
        val t = trill(0, 2, 3)
        val r = parseLine(t.exercise.right, Hand.RIGHT, 1000.0).map { it.midi }
        assertEquals(listOf(62, 64), r.take(2))
        assertEquals(17, r.size)
        val rep = parseLine(repeatedNotes(0).exercise.right, Hand.RIGHT, 1000.0).map { it.midi }
        assertEquals(listOf(60, 60, 60, 60, 62), rep.take(5))
        assertEquals(36, rep.size)
    }

    @Test fun routineFillsTheTimeAndGrowsWithLevel() {
        for (minutes in listOf(5, 10, 15)) for (level in 1..3) for (seed in 0..3) {
            val items = buildRoutine(minutes, level, seed)
            val what = "$minutes min level $level seed $seed"
            assertTrue(what, items.isNotEmpty())
            assertTrue(what, items.all { it.warmup.level <= level && it.reps >= 1 })
            assertEquals(what, items.map { it.warmup.id }.toSet().size, items.size)
            val sec = routineSeconds(items)
            assertTrue("$what: $sec s", sec in minutes * 60 * 0.6..minutes * 60 * 1.5)
        }
        assertTrue(buildRoutine(15, 3).size > buildRoutine(5, 3).size)
        assertTrue(buildRoutine(15, 1).all { it.warmup.level == 1 })
        assertEquals(buildRoutine(10, 2, 1).map { it.warmup.id }, buildRoutine(10, 2, 1).map { it.warmup.id })
    }
}
