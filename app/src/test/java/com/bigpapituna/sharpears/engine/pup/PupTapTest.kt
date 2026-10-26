package com.bigpapituna.sharpears.engine.pup

import com.bigpapituna.sharpears.engine.SpokenCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14, tap the pup to play it again: the rules without Android. */
class PupTapTest {
    @Test fun aKidHearsOneOfHisRecordedCheersAndAGrownUpHearsNothing() {
        assertEquals("Woof woof! Amazing!", PupTap.line(kid = true))
        assertNull(PupTap.line(kid = false))
        assertTrue("the voice has it", PupTap.isRecorded(PupTap.LINE))
        assertTrue("it is in the voice catalog", PupTap.LINE in SpokenCatalog.kidLines())
    }

    @Test fun theLineIsSaidTheFirstTapOnly() {
        assertTrue(PupTap.speaks(alreadySpoke = false, line = PupTap.LINE))
        assertFalse(PupTap.speaks(alreadySpoke = true, line = PupTap.LINE))
        assertFalse("no line, no speech", PupTap.speaks(alreadySpoke = false, line = null))
        // Unlimited replays: the tap count never decides anything.
        var spoke = false; var said = 0
        repeat(50) { if (PupTap.speaks(spoke, PupTap.LINE)) { spoke = true; said++ } }
        assertEquals(1, said)
    }

    @Test fun theSparkleIsTinyAndShort() {
        assertTrue(PupTap.SPARKLES in 3..8)
        assertTrue(PupTap.SPARKLE_MS in 300..800)
        assertEquals("the reduced-motion fade is 150 ms", 150, PupMotion.CALM_MS)
    }
}
