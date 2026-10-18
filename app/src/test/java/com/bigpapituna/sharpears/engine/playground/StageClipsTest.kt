package com.bigpapituna.sharpears.engine.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the stages and the Jam keep audio for tuning (2.1.1). */
class StageClipsTest {
    @Test fun aStageStuckWithSoundsArrivingIsKeptOncePerStretch() {
        val w = StageClipWatch()
        w.reset(0, listOf("C#4", "D#4"))
        w.sound(1_000, "named E4: white_key"); w.sound(3_000, "onset")
        assertNull("only two sounds", w.check(9_000, "lesson=playground-2 stage=key_hunt round=1/3", "kid"))
        w.sound(5_000, "pitch 64.1")
        assertNull("not 8 s yet", w.check(7_999, "x", "kid"))
        val clip = w.check(8_000, "lesson=playground-2 stage=key_hunt round=1/3", "kid")
        assertNotNull(clip)
        assertEquals(0, clip!!.fromMs)
        val text = clip.report
        assertTrue(text.startsWith("# sharp ears stuck moment\nprofile=kid\nlesson=playground-2 stage=key_hunt round=1/3\n"))
        assertTrue("expected=C#4,D#4" in text)
        assertTrue("named E4" in text)
        assertNull("once per stretch", w.check(20_000, "x", "kid"))
        // A key recognised starts a new stretch.
        w.reset(21_000)
        repeat(3) { w.sound(22_000L + it * 1_000, "onset") }
        assertNotNull(w.check(29_000, "x", "adult"))
    }

    @Test fun aQuietKidIsNotADetectionProblem() {
        val w = StageClipWatch()
        w.reset(0, listOf("C4"))
        assertNull(w.check(60_000, "x", "kid"))
    }

    @Test fun theJamKeepsOneClipASession() {
        val j = JamClipWatch()
        for (k in 0 until 9) j.sound(1_000L + k * 500)
        assertFalse(j.check(40_000))
        j.sound(6_000)
        assertFalse("the first sound is not 20 s old", j.check(20_999))
        assertTrue(j.check(21_000))
        assertFalse("one a session", j.check(60_000))
        assertTrue(j.report(21_000, "screen=jam", "kid").startsWith("# sharp ears jam clip\nprofile=kid\nscreen=jam\n"))
        // Closing with enough sounds saves what there is.
        val k = JamClipWatch()
        repeat(10) { k.sound(1_000L + it * 100) }
        assertFalse(k.check(5_000))
        assertTrue(k.check(5_000, closing = true))
        val few = JamClipWatch()
        repeat(9) { few.sound(it * 100L) }
        assertFalse(few.check(99_000, closing = true))
    }
}
