package com.bigpapituna.sharpears.engine.brief.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The weekly quota per profile and the cache of written replies, through the small store the integrator wires. */
class BudgetTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val wednesday = LocalDate.of(2026, 10, 21)
    private val store = MemoryBudgetStore()
    private val budget = Budget(store, callsPerWeek = 40, zone = zone)

    @Test fun fortyCallsAWeekPerProfileThenNone() {
        val now = at(wednesday)
        repeat(40) { assertTrue(budget.take("mia", now)) }
        assertFalse(budget.take("mia", now))
        assertEquals(40, budget.used("mia", now))
        assertEquals(0, budget.left("mia", now))
        assertTrue("another profile has its own", budget.take("leo", now))
        budget.giveBack("mia", now)
        assertEquals(39, budget.used("mia", now))
        assertTrue(budget.take("mia", now))
    }

    @Test fun theWeekRunsMondayToSundayInThePhonesZone() {
        repeat(5) { budget.take("mia", at(wednesday)) }
        assertEquals(5, budget.used("mia", at(LocalDate.of(2026, 10, 25), 23)))       // Sunday night: same week
        assertEquals(0, budget.used("mia", at(LocalDate.of(2026, 10, 26), 0)))        // Monday: a new week
        assertTrue(budget.take("mia", at(LocalDate.of(2026, 10, 26))))
        assertEquals(1, budget.used("mia", at(LocalDate.of(2026, 10, 26))))
        assertEquals("one key per profile, not one per week", setOf(Budget.callsKey("mia")), store.keys())
    }

    @Test fun giveBackNeverGoesBelowZeroAndAConfiguredQuotaHolds() {
        val now = at(wednesday)
        budget.giveBack("mia", now)
        assertEquals(0, budget.used("mia", now))
        val small = Budget(MemoryBudgetStore(), callsPerWeek = 2, zone = zone)
        assertTrue(small.take("mia", now)); assertTrue(small.take("mia", now)); assertFalse(small.take("mia", now))
    }

    @Test fun theCountSurvivesANewBudgetOnTheSameStore() {
        val now = at(wednesday)
        repeat(3) { budget.take("mia", now) }
        assertEquals(3, Budget(store, zone = zone).used("mia", now))
        store.put(Budget.callsKey("mia"), "garbage")
        assertEquals("a damaged count reads as none", 0, budget.used("mia", now))
    }

    @Test fun theCacheHoldsOneReplyPerProfileAndSlotForItsHash() {
        val h1 = Budget.hash("v1", "model", "WEEK", "findings one")
        val h2 = Budget.hash("v1", "model", "WEEK", "findings two")
        assertNotEquals(h1, h2)
        assertEquals(64, h1.length)
        assertNotEquals("parts are separated", Budget.hash("ab", "c"), Budget.hash("a", "bc"))
        budget.keep("mia", "WEEK", h1, 1_000, "{\"headline\":\"line one\nline two\"}")
        assertEquals(Budget.Cached(1_000, "{\"headline\":\"line one\nline two\"}"), budget.cached("mia", "WEEK", h1))
        assertNull("other findings", budget.cached("mia", "WEEK", h2))
        assertNull("another slot", budget.cached("mia", "DAY", h1))
        assertNull("another profile", budget.cached("leo", "WEEK", h1))
        budget.keep("mia", "WEEK", h2, 2_000, "second")
        assertNull("new findings replace the old", budget.cached("mia", "WEEK", h1))
        budget.forget("mia", "WEEK")
        assertNull(budget.cached("mia", "WEEK", h2))
    }

    @Test fun forgettingAProfileDropsItsCountAndEveryCachedReply() {
        val now = at(wednesday)
        budget.take("mia", now)
        Budget.SLOTS.forEach { budget.keep("mia", it, "h", 1, "x") }
        budget.keep("leo", "WEEK", "h", 1, "x")
        budget.forgetProfile("mia")
        assertEquals(setOf(Budget.cacheKey("leo", "WEEK")), store.keys())
    }
}
