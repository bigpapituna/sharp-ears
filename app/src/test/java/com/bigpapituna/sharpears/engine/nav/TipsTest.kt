package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.nav.Tips.Tip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.15: the one-time tips. A tip needs a grown-up (never `isKid`, so never a big kid either), has not been seen, comes with no
 * other tip this session, and points at something that is within reach and still unused.
 */
class TipsTest {
    private val none = emptySet<String>()

    @Test fun theCopyIsTheBriefsAndKeepsTheRules() {
        assertEquals("Tap a bar to see which notes were early or late.", Tip.TIMING.line)
        assertEquals("Loop the bars you missed.", Tip.LOOP.line)
        assertEquals("Try a sight-reading drill at your level.", Tip.SIGHT.line)
        Tip.entries.forEach { t ->
            assertTrue(t.line, t.line.first().isUpperCase() && t.line.endsWith(".") && !Regex("[—–·>!]| - ").containsMatchIn(t.line))
        }
        assertEquals("Open", Tip.SIGHT.action); assertNull(Tip.TIMING.action); assertNull(Tip.LOOP.action)
    }

    @Test fun theTimingTipComesAfterThePerformRunWhoseStripCanBeTapped() {
        assertEquals(Tip.TIMING, Tips.afterSong(false, none, false, timingBars = true, hardRecaps = 0, hasHardBars = false))
        // A wait-mode recap has no strip: nothing to tap.
        assertNull(Tips.afterSong(false, none, false, timingBars = false, hardRecaps = 0, hasHardBars = false))
        assertNull(Tips.afterSong(false, setOf("timing"), false, timingBars = true, hardRecaps = 0, hasHardBars = false))
    }

    @Test fun theLoopTipWaitsForTheThirdRecapWithHardBars() {
        for (n in 0..2) assertNull("recap $n", Tips.afterSong(false, none, false, timingBars = false, hardRecaps = n, hasHardBars = true))
        assertEquals(Tip.LOOP, Tips.afterSong(false, none, false, timingBars = false, hardRecaps = 3, hasHardBars = true))
        // Later, if the session limit held it back; not when this recap has no hard bars, and never after Loop was used.
        assertEquals(Tip.LOOP, Tips.afterSong(false, none, false, timingBars = false, hardRecaps = 5, hasHardBars = true))
        assertNull(Tips.afterSong(false, none, false, timingBars = false, hardRecaps = 3, hasHardBars = false))
        assertNull(Tips.afterSong(false, setOf("loop"), false, timingBars = false, hardRecaps = 3, hasHardBars = true))
    }

    @Test fun theTimingTipGoesFirstWhenBothAreDue() {
        assertEquals(Tip.TIMING, Tips.afterSong(false, none, false, timingBars = true, hardRecaps = 3, hasHardBars = true))
        assertEquals(Tip.LOOP, Tips.afterSong(false, setOf("timing"), false, timingBars = true, hardRecaps = 3, hasHardBars = true))
    }

    @Test fun theSightTipIsForPlayersAtSomeOrALotWhoOpenItAndHaveNotTriedIt() {
        fun tip(level: Level, open: Boolean = true, tried: Boolean = false, seen: Set<String> = none) = Tips.inPractice(false, level, open, tried, seen, false)
        assertEquals(Tip.SIGHT, tip(Level.SOME)); assertEquals(Tip.SIGHT, tip(Level.A_LOT))
        assertNull(tip(Level.NEVER)); assertNull(tip(Level.A_LITTLE))
        assertNull("the staff levels are closed", tip(Level.SOME, open = false))
        assertNull("already tried a drill", tip(Level.SOME, tried = true))
        assertNull("already shown", tip(Level.SOME, seen = setOf("sight")))
    }

    @Test fun theSightTipOpensTheLevelTodaysChallengeStartsAt() {
        assertEquals(4, Tips.sightLevelFor(Level.SOME))
        assertEquals(6, Tips.sightLevelFor(Level.A_LOT))
    }

    @Test fun neverForAKidAndNeverTwoInASession() {
        assertNull(Tips.afterSong(true, none, false, timingBars = true, hardRecaps = 9, hasHardBars = true))
        assertNull(Tips.inPractice(true, Level.A_LOT, true, false, none, false))
        assertNull(Tips.afterSong(false, none, true, timingBars = true, hardRecaps = 9, hasHardBars = true))
        assertNull(Tips.inPractice(false, Level.A_LOT, true, false, none, true))
    }

    @Test fun theSessionLimitIsOneAndResets() {
        TipSession.reset(); assertFalse(TipSession.shown)
        TipSession.shown = true; assertTrue(TipSession.shown)
        TipSession.reset(); assertFalse(TipSession.shown)
    }

    @Test fun seenTipsRoundTrip() {
        assertEquals(setOf("loop", "timing"), Tips.parse(Tips.encode(setOf("timing", "loop"))))
        assertTrue(Tips.parse(null).isEmpty()); assertTrue(Tips.parse("").isEmpty())
    }
}
