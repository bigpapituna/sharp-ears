package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.isBlack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePreviewTest {
    @Test fun fiveFingerPreviewIsTheRightHandShape() {
        val p = warmupPreview(fiveFinger(0))
        assertEquals(listOf(60, 62, 64, 65, 67, 65, 64, 62, 60), p.steps.map { it.right.single() })
        assertTrue(p.steps.all { it.left.isEmpty() })                              // the parallel left hand is left out
        assertTrue(p.low <= 60 && p.high >= 67 && !isBlack(p.low) && !isBlack(p.high))
        assertEquals(10.0, p.totalBeats, 1e-9)                                     // eight beats and the long last note
    }

    @Test fun contraryMotionShowsBothHands() {
        val p = warmupPreview(contraryScale(0))
        assertEquals(60, p.steps[0].right.single())
        assertEquals(60, p.steps[0].left.single())
        assertEquals(59, p.steps[1].left.single())
        assertTrue(p.low <= 48 + 2 && p.high >= 69)
    }

    @Test fun aHeldChordIsOneStepWithThreeKeys() {
        val p = warmupPreview(handIndependence(0, leftHolds = true))
        assertEquals(3, p.steps[0].left.size)
        assertTrue(p.steps.drop(1).take(3).all { it.left.isEmpty() && it.right.size == 1 })
    }

    @Test fun everyWarmupHasAPreviewOnAtLeastSixWhiteKeys() {
        for (root in 0..11) for (w in warmupCatalog(root)) {
            val p = warmupPreview(w)
            assertTrue(w.id, p.steps.isNotEmpty() && p.steps.size <= 12)
            assertTrue(w.id, (p.low..p.high).count { !isBlack(it) } >= 6)
            assertTrue(w.id, p.steps.all { s -> (s.right + s.left).all { it in p.low..p.high } && s.beats > 0 })
        }
    }

    @Test fun routineIntroNamesTheKindsInOrder() {
        val items = listOf(fiveFinger(0), hanon(HANON_PATTERNS[0]), contraryScale(0)).map { RoutineItem(it, 1, 60) }
        assertEquals("Ten minutes: a five-finger pattern, then a Hanon style run, then contrary motion.", routineIntro(10, items))
        val trills = listOf(trill(0, 1, 2), trill(0, 2, 3), repeatedNotes(0), scaleWarmup(0), fiveFinger(0)).map { RoutineItem(it, 1, 60) }
        assertEquals("Five minutes: trills, then repeated notes, the scale hands together and a five-finger pattern.", routineIntro(5, trills))
        for (m in listOf(5, 10, 15)) for (level in 1..3) {
            val line = routineIntro(m, buildRoutine(m, level))
            assertTrue(line, line.length < 160)
            assertFalse(line, line.contains(Char(0x2014)))
        }
    }
}
