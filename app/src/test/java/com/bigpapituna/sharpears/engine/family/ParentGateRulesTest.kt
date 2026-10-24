package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ParentGateRulesTest {
    @Test fun sumsUseTwoToNine() {
        val r = Random(7)
        val seen = mutableSetOf<Int>()
        repeat(500) {
            val s = ParentGateRules.nextSum(r)
            assertTrue(s.a in 2..9 && s.b in 2..9)
            seen += s.a
        }
        assertEquals((2..9).toSet(), seen)
    }

    @Test fun aWrongAnswerBringsADifferentSum() {
        val r = Random(1)
        var s = ParentGateRules.nextSum(r)
        repeat(200) { val n = ParentGateRules.nextSum(r, s); assertNotEquals(s, n); s = n }
    }

    @Test fun answersAreCheckedAsTyped() {
        val s = ParentGateRules.Sum(7, 5)
        assertEquals("7 plus 5", s.spoken)
        assertTrue(s.isRight("12"))
        assertFalse(s.isRight("13"))
        assertFalse(s.isRight(""))
        assertFalse(s.isRight("012"))
        assertTrue(ParentGateRules.Sum(2, 2).isRight("4"))
        assertFalse(ParentGateRules.Sum(2, 2).isRight("04"))
    }

    @Test fun theNumberPadTypesTwoDigitsAndDeletes() {
        var t = ""
        for (k in "125") t = ParentGateRules.type(t, k)
        assertEquals("12", t)
        assertEquals("1", ParentGateRules.type(t, '<'))
        assertEquals("", ParentGateRules.type("", '<'))
        assertEquals("1", ParentGateRules.type("1", 'x'))
    }

    @Test fun theGateStaysOpenTenMinutes() {
        val at = 1_000_000L
        assertFalse(ParentGateRules.isOpen(0, at))
        assertTrue(ParentGateRules.isOpen(at, at))
        assertTrue(ParentGateRules.isOpen(at, at + 9 * 60_000))
        assertFalse(ParentGateRules.isOpen(at, at + 10 * 60_000))
        assertFalse(ParentGateRules.isOpen(at, at - 1))                     // a clock turned back does not open it
    }

    /** QA 2.8.3: three wrong sums in a row make the gate wait 30 s, then 60 s, 120 s, doubling, never more than ten minutes. */
    @Test fun threeWrongAnswersInARowMakeTheGateWait() {
        var l = ParentGateRules.Lockout()
        var now = 1_000_000L
        assertFalse(l.locked(now))
        l = l.wrongAt(now); l = l.wrongAt(now)
        assertFalse("two wrong answers are fine", l.locked(now))
        l = l.wrongAt(now)
        assertTrue(l.locked(now))
        assertEquals(30, l.secondsLeft(now))
        assertEquals("Try again in 30 seconds.", ParentGateRules.waitLine(l.secondsLeft(now)))
        assertEquals("rounded up", 30, l.secondsLeft(now + 1))
        assertEquals(1, l.secondsLeft(now + 29_500))
        assertEquals("Try again in 1 second.", ParentGateRules.waitLine(1))
        assertEquals("an answer while it waits does not count", l, l.wrongAt(now + 10_000))
        assertFalse(l.locked(now + 30_000))
        assertEquals(0, l.secondsLeft(now + 30_000))
        // Each next wait doubles, up to ten minutes.
        val waits = mutableListOf<Long>()
        repeat(7) {
            now = l.until
            repeat(3) { l = l.wrongAt(now) }
            waits += l.until - now
        }
        assertEquals(listOf(60_000L, 120_000L, 240_000L, 480_000L, 600_000L, 600_000L, 600_000L), waits)
        assertEquals(listOf(30_000L, 60_000L, 120_000L), (0..2).map(ParentGateRules::waitMs))
        assertEquals(ParentGateRules.MAX_WAIT_MS, ParentGateRules.waitMs(40))
    }

    @Test fun theWaitsStartOverADayLaterAndSurviveTheAppClosing() {
        var l = ParentGateRules.Lockout()
        repeat(6) { l = l.wrongAt(l.until.coerceAtLeast(1L)) }
        assertEquals(2, l.waits)
        assertEquals(l, ParentGateRules.Lockout.decode(l.encode()))
        val dayLater = l.until + ParentGateRules.WAITS_FORGOTTEN_MS
        repeat(3) { l = l.wrongAt(dayLater) }
        assertEquals("the first wait again", 30_000L, l.until - dayLater)
        for (junk in listOf(null, "", "a,b,c", "1,2", "-1,0,5", "1,2,3,4")) assertEquals(junk.toString(), ParentGateRules.Lockout(), ParentGateRules.Lockout.decode(junk))
        // A clock turned back cannot make a wait longer than ten minutes.
        val l2 = ParentGateRules.Lockout(0, 1, 5_000_000L)
        assertFalse(l2.locked(5_000_000L - ParentGateRules.MAX_WAIT_MS - 1))
    }

    /** QA 2.8.3: the launch picker asks the gate for a grown-up and for Add someone once a kid is in the family; never for a kid. */
    @Test fun thePickerAsksForGrownUpsOnlyWithAKidAround() {
        val roy = Profile.owner()
        val mum = Profile.adult("Ana", 2, id = "p2")
        val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
        assertFalse("grown-ups only: nothing to guard", ParentGateRules.pickerAsks(listOf(roy, mum), roy))
        assertFalse(ParentGateRules.pickerAsks(listOf(roy, mum), null))
        val family = listOf(roy, mia, mum)
        assertTrue(ParentGateRules.pickerAsks(family, roy))
        assertTrue(ParentGateRules.pickerAsks(family, mum))
        assertTrue("Add someone", ParentGateRules.pickerAsks(family, null))
        assertFalse("a kid's own tile", ParentGateRules.pickerAsks(family, mia))
    }

    @Test fun theHoldTakesThreeSeconds() {
        assertEquals(0f, ParentGateRules.holdProgress(-5), 0f)
        assertEquals(0.5f, ParentGateRules.holdProgress(1_500), 0.001f)
        assertEquals(1f, ParentGateRules.holdProgress(4_000), 0f)
    }
}
