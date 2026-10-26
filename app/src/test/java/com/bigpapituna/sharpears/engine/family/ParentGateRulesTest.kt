package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ParentGateRulesTest {
    /**
     * 2.14 (Priya's P3: an 8 year old reader passed "8 plus 9" in seconds): every question is a sum of two two-digit numbers
     * with a carry, or a times table from 4, 6, 7, 8 and 9; never a one-digit sum; both kinds come up; every answer fits the
     * two-digit pad.
     */
    @Test fun questionsAreTwoDigitSumsWithACarryOrTimesTables() {
        val r = Random(7)
        var sums = 0; var times = 0
        val factors = mutableSetOf<Int>()
        repeat(2000) {
            when (val q = ParentGateRules.nextQuestion(r)) {
                is ParentGateRules.Sum -> {
                    sums++
                    assertTrue(q.spoken, q.a >= 10 && q.b >= 10)                 // never a one-digit number
                    assertTrue(q.spoken, q.a % 10 != 0 && q.b % 10 != 0)
                    assertTrue("a carry: " + q.spoken, q.a % 10 + q.b % 10 >= 10)
                    assertTrue(q.spoken, q.answer in 10..99)
                    assertTrue(ParentGateRules.fair(q))
                }
                is ParentGateRules.Times -> {
                    times++
                    assertTrue(q.spoken, q.a in ParentGateRules.FACTORS && q.b in ParentGateRules.FACTORS)
                    assertTrue(q.spoken, q.answer in 16..81)
                    factors += q.a
                }
            }
        }
        assertTrue("both kinds, rolled: $sums sums, $times times", sums in 700..1300 && times in 700..1300)
        assertEquals(ParentGateRules.FACTORS.toSet(), factors)
        for (bad in listOf(ParentGateRules.Sum(8, 9), ParentGateRules.Sum(23, 41), ParentGateRules.Sum(20, 48), ParentGateRules.Sum(57, 68)))
            assertFalse("not asked: " + bad.spoken, ParentGateRules.fair(bad))
        assertTrue(ParentGateRules.fair(ParentGateRules.Sum(23, 48)))
    }

    @Test fun aWrongAnswerBringsADifferentQuestion() {
        val r = Random(1)
        var q = ParentGateRules.nextQuestion(r)
        repeat(300) { val n = ParentGateRules.nextQuestion(r, q); assertNotEquals(q, n); q = n }
    }

    @Test fun answersAreCheckedAsTypedAndSaidInWords() {
        val s = ParentGateRules.Sum(23, 48)
        assertEquals("23 plus 48", s.spoken)
        assertTrue(s.isRight("71"))
        assertFalse(s.isRight("72"))
        assertFalse(s.isRight(""))
        assertFalse(s.isRight("071"))
        val t = ParentGateRules.Times(6, 7)
        assertEquals("6 times 7", t.spoken)                  // words, never a symbol
        assertTrue(t.isRight("42"))
        assertFalse(t.isRight("13"))
        assertFalse(t.isRight("042"))
    }

    @Test fun theNumberPadTypesTwoDigitsAndDeletes() {
        var t = ""
        for (k in "125") t = ParentGateRules.type(t, k)
        assertEquals("12", t)
        assertEquals("1", ParentGateRules.type(t, '<'))
        assertEquals("", ParentGateRules.type("", '<'))
        assertEquals("1", ParentGateRules.type("1", 'x'))
    }

    /** 2.14 (Priya's P4): two minutes, not ten; each thing done behind it starts the two minutes again. */
    @Test fun theGateStaysOpenTwoMinutesFromTheLastUse() {
        val at = 1_000_000L
        assertFalse(ParentGateRules.isOpen(0, at))
        assertTrue(ParentGateRules.isOpen(at, at))
        assertTrue(ParentGateRules.isOpen(at, at + 119_000))
        assertFalse(ParentGateRules.isOpen(at, at + 2 * 60_000))
        assertFalse("the old ten minutes", ParentGateRules.isOpen(at, at + 9 * 60_000))
        assertFalse(ParentGateRules.isOpen(at, at - 1))                     // a clock turned back does not open it
        assertEquals(at + 120_000, ParentGateRules.closesAt(at, at + 5_000))
        assertEquals(0L, ParentGateRules.closesAt(at, at + 120_000))
        // A setting changed a minute and a half in: open for two more minutes from then.
        val used = ParentGateRules.used(at, at + 90_000)
        assertEquals(at + 90_000, used)
        assertTrue(ParentGateRules.isOpen(used, at + 200_000))
        assertFalse(ParentGateRules.isOpen(used, at + 211_000))
        assertEquals("a closed gate stays closed", at, ParentGateRules.used(at, at + 130_000))
        assertEquals(0L, ParentGateRules.used(0, at))
    }

    /** 2.14: the app away (in the background) for more than 30 seconds closes the gate; a short look elsewhere does not. */
    @Test fun theGateClosesAfterThirtySecondsAway() {
        val away = 5_000_000L
        assertFalse("never away", ParentGateRules.closesAfterAway(0, away))
        assertFalse(ParentGateRules.closesAfterAway(away, away + 30_000))
        assertTrue(ParentGateRules.closesAfterAway(away, away + 30_001))
        assertTrue("a clock turned back", ParentGateRules.closesAfterAway(away, away - 1))
    }

    /** 2.14 (Priya's P4): every switch of player and a fresh start close the gate; a fold, rotation or dark mode does not. */
    @Test fun theGateClosesOnEveryProfileSwitchButNotOnARecreate() {
        assertTrue("a fresh start", ParentGateRules.closesOnOpen(null, "owner", recreated = false))
        assertFalse("a recreate opens the same person again", ParentGateRules.closesOnOpen(null, "owner", recreated = true))
        assertTrue("to a kid", ParentGateRules.closesOnOpen("owner", "p1", recreated = false))
        assertTrue("to a grown-up", ParentGateRules.closesOnOpen("p1", "owner", recreated = false))
        assertTrue(ParentGateRules.closesOnOpen("p1", "p2", recreated = true))
        assertFalse("the same person, a new kind or reader", ParentGateRules.closesOnOpen("p1", "p1", recreated = false))
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
