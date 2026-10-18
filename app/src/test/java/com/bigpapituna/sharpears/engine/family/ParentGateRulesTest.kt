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

    @Test fun theHoldTakesThreeSeconds() {
        assertEquals(0f, ParentGateRules.holdProgress(-5), 0f)
        assertEquals(0.5f, ParentGateRules.holdProgress(1_500), 0.001f)
        assertEquals(1f, ParentGateRules.holdProgress(4_000), 0f)
    }
}
