package com.bigpapituna.sharpears.engine.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.9, Tomorrow's surprise: one wrapped a day once the goal is met, never two waiting, opened from the next calendar day
 * on, holding a sticker, something to wear the kid has not got (the rarest) or 25 sparkles.
 */
class SurpriseTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)

    @Test fun wrappedOnlyOnceTheGoalIsMetAndOnceADay() {
        val none = Gift()
        assertFalse(Surprise.canWrap(none, mon, goalMet = false))
        val g = Surprise.wrap(none, mon, goalMet = true)
        assertEquals(Gift(mon, opened = false), g)
        assertTrue(Surprise.wrappedToday(g, mon))
        // Not again today, and not a second one while it waits.
        assertFalse(Surprise.canWrap(g, mon, goalMet = true))
        assertFalse(Surprise.canWrap(g, tue, goalMet = true))
        assertEquals(g, Surprise.wrap(g, tue, goalMet = true))
        // Opened: tomorrow's goal wraps the next one; an opened one from today still blocks a second today.
        val opened = g.copy(opened = true)
        assertTrue(Surprise.canWrap(opened, tue, goalMet = true))
        assertFalse(Surprise.canWrap(Gift(tue, opened = true), tue, goalMet = true))
    }

    @Test fun itOpensOnlyOnALaterDay() {
        val g = Gift(mon, opened = false)
        assertFalse(Surprise.ready(g, mon))
        assertTrue(Surprise.ready(g, tue))
        assertTrue(Surprise.ready(g, wed))                     // a kid who skipped a day still finds it
        assertFalse(Surprise.ready(g.copy(opened = true), tue))
        assertFalse(Surprise.ready(Gift(), tue))
    }

    @Test fun theWorldOpensItOnceAndKeepsTheRules() {
        val w = World.wrapGift(WorldState(seed = 7), mon, goalMet = true)
        assertEquals(Gift(mon, opened = false), w.gift)
        assertNull(World.openGift(w, mon))                     // never the same day
        val o = World.openGift(w, tue)!!
        assertTrue(o.state.gift.opened)
        assertNull(World.openGift(o.state, tue))               // once
        assertNull(World.openGift(o.state, wed))
        assertEquals(w, World.wrapGift(w, mon, goalMet = true))   // never two waiting
    }

    @Test fun theOutfitIsTheRarestPrize() {
        val counts = GiftKind.entries.associateWith { 0 }.toMutableMap()
        val start = LocalDate.of(2026, 1, 1)
        for (seed in 0L until 40L) for (d in 0L until 250L) counts.merge(Surprise.roll(seed, start.plusDays(d)), 1, Int::plus)
        val total = counts.values.sum().toDouble()
        assertTrue(counts.toString(), counts.getValue(GiftKind.OUTFIT) < counts.getValue(GiftKind.STICKER))
        assertTrue(counts.toString(), counts.getValue(GiftKind.OUTFIT) < counts.getValue(GiftKind.SPARKLES))
        assertEquals(0.15, counts.getValue(GiftKind.OUTFIT) / total, 0.02)
        assertEquals(0.45, counts.getValue(GiftKind.STICKER) / total, 0.02)
        assertEquals(GiftKind.STICKER, Surprise.kindFor(0.0))
        assertEquals(GiftKind.SPARKLES, Surprise.kindFor(0.5))
        assertEquals(GiftKind.OUTFIT, Surprise.kindFor(0.99))
    }

    @Test fun eachPrizeGivesWhatItSays() {
        // Find a wrapping day for each kind with this seed.
        val seed = 11L
        fun dayFor(k: GiftKind) = (0L until 400L).map { mon.plusDays(it) }.first { Surprise.roll(seed, it) == k }
        for (kind in GiftKind.entries) {
            val on = dayFor(kind)
            val w = WorldState(seed).copy(gift = Gift(on, opened = false))
            val o = World.openGift(w, on.plusDays(1))!!
            assertEquals(kind, o.kind)
            when (kind) {
                GiftKind.STICKER -> {
                    assertEquals(1, o.reward.drops.size); assertEquals(DropSource.GIFT, o.reward.drops[0].source)
                    assertEquals(1, o.state.history.total)
                }
                GiftKind.OUTFIT -> {
                    assertTrue(o.reward.unlocked.isNotEmpty())
                    assertTrue(o.reward.unlocked.all { it in o.state.wardrobe && it.earn is Earn.Practice })
                    assertTrue(o.reward.unlocked.none { it in w.wardrobe })
                }
                GiftKind.SPARKLES -> {
                    assertEquals(Surprise.SPARKLES, o.state.ledger.bonus)
                    assertEquals(w.ledger.sparkles + Surprise.SPARKLES, o.state.ledger.sparkles)
                }
            }
        }
    }

    @Test fun anOutfitWithNothingLeftToGiveIsSparkles() {
        val seed = 11L
        val on = (0L until 400L).map { mon.plusDays(it) }.first { Surprise.roll(seed, it) == GiftKind.OUTFIT }
        val all = WearItem.entries.toSet()
        assertNull(Surprise.outfit(seed, on, all))
        val o = World.openGift(WorldState(seed, wardrobe = all).copy(gift = Gift(on, opened = false)), on.plusDays(1))!!
        assertEquals(GiftKind.SPARKLES, o.kind)
        assertNotNull(Surprise.outfit(seed, on, emptySet()))
    }

    @Test fun theGiftAndTheSparklesSurviveSaving() {
        val w = WorldState(seed = 3).copy(gift = Gift(mon, opened = false), ledger = Ledger(bonus = 50))
        val back = WorldState.decode(w.encode(), seed = 99)
        assertEquals(w.gift, back.gift)
        assertEquals(50, back.ledger.bonus)
        assertEquals(Gift(), WorldState.decode(WorldState(seed = 3).encode(), seed = 99).gift)
        // A world saved before 2.9 has no gift and no bonus.
        assertEquals(Gift(), WorldState.decode("""{"v":2,"seed":5}""", seed = 1).gift)
    }

    @Test fun theWordsKeepTheRules() {
        val lines = GiftKind.entries.map(Surprise::line) + listOf(Surprise.WRAPPED_TITLE, Surprise.WRAPPED_LINE, Surprise.READY_TITLE, Surprise.READY_LINE)
        for (l in lines) {
            assertFalse(l, Regex("[—–·0-9]| - ").containsMatchIn(l))
            assertFalse(l, Regex("(?i)hours?|minutes?|seconds?|left").containsMatchIn(l))      // no countdown
        }
    }
}
