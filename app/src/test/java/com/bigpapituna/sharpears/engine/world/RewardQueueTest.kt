package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14, the calmer finish: one reward line and a queue behind a chip, and a kid's first lesson giving the starter wearable. */
class RewardQueueTest {
    private val common = Drop(Stickers.ALL.first { it.rarity == Rarity.COMMON }, DropSource.LESSON, isNew = true)
    private val rare = Drop(Stickers.ALL.first { it.rarity == Rarity.RARE }, DropSource.LESSON, isNew = true)
    private val stone = LevelStickers.ALL.first()

    @Test fun theQueueHoldsEveryRevealInTheOrderPeopleMeetThem() {
        val reward = Reward(listOf(common, rare), eggLaid = true, houseBefore = 1, houseAfter = 2, unlocked = listOf(WearItem.BOW_TIE), level = stone)
        val items = RewardQueue.of(reward, LevelUp(2, 3))
        assertEquals(listOf("Trail", "Sticker", "Sticker", "Wear", "EggLaid", "House", "Level"), items.map { it::class.simpleName })
        // Nothing to show: no queue, no chip.
        assertTrue(RewardQueue.of(Reward(emptyList())).isEmpty())
        assertNull(RewardQueue.chip(emptyList()))
        // The chip counts what the sheet holds.
        assertEquals("See rewards (7)", RewardQueue.chip(items))
        assertEquals("See rewards (1)", RewardQueue.chip(listOf(RewardItem.EggLaid)))
        // Only a level-up: still one thing to see.
        assertEquals(listOf(RewardItem.Level(LevelUp(1, 2))), RewardQueue.of(null, LevelUp(1, 2)))
    }

    @Test fun aGrownUpGetsOneRewardLineNeverTwo() {
        assertEquals("A sticker for your trail.", RewardQueue.line(RewardQueue.of(Reward(listOf(common))), 40, 280))
        assertEquals("A sticker for your trail.", RewardQueue.line(RewardQueue.of(Reward(emptyList(), level = stone)), 40, 280))
        assertEquals("A rare sticker for your trail.", RewardQueue.line(RewardQueue.of(Reward(listOf(common, rare))), 40, 280))
        assertEquals("Something new for the pup to wear.", RewardQueue.line(RewardQueue.of(Reward(emptyList(), unlocked = listOf(WearItem.BOW_TIE))), 40, 280))
        assertEquals("A mystery egg for your trail.", RewardQueue.line(RewardQueue.of(Reward(emptyList(), eggLaid = true)), 40, 280))
        // Nothing to collect: the XP, and how far the next level is (at 163 XP it is level 2, and level 3 is 120 away).
        assertEquals(2, Progression.levelFor(163)); assertEquals(120, Progression.xpToNext(163))
        assertEquals("+40 XP, level 3 in 120.", RewardQueue.line(emptyList(), 40, 163))
        assertNull(RewardQueue.line(emptyList(), null, 163)); assertNull(RewardQueue.line(emptyList(), 0, 163))
        assertTrue(RewardQueue.xpLine(40, 10_000_000)!!.endsWith("the top level."))
        // Copy rules: sentence case, no dashes, every line ends with a stop.
        for (l in listOf("A sticker for your trail.", "A rare sticker for your trail.", "Something new for the pup to wear.", "A mystery egg for your trail.",
            RewardQueue.line(emptyList(), 40, 163)!!))
            assertTrue(l, l.endsWith(".") && "—" !in l && "–" !in l && l.first().let { it.isUpperCase() || it == '+' })
    }

    @Test fun theSheetReadsOneRewardAtATime() {
        assertEquals("Reward", RewardQueue.heading(0, 1))
        assertEquals("Rewards, 2 of 3", RewardQueue.heading(1, 3))
        assertEquals("Next reward", RewardQueue.button(0, 3)); assertEquals("Next reward", RewardQueue.button(1, 3)); assertEquals("Done", RewardQueue.button(2, 3))
        assertEquals("Done", RewardQueue.button(0, 1))
    }

    @Test fun aKidsFirstLessonGivesTheStarterWearableAndSticksTheFirstStickerOnTheMap() {
        val s = WorldState(seed = 4L)
        val o = World.lessonComplete(s, Place.GARDEN, "playground-1", starter = true)
        val r = o.reward
        assertTrue(r.starter)
        assertEquals(listOf(Starter.ITEM), r.unlocked)
        assertTrue(Starter.ITEM in o.state.wardrobe)
        // The stone's own sticker is the one stuck on the map, in a real slot of the garden.
        val slot = r.placedAt
        assertNotNull(slot)
        assertEquals(Place.GARDEN, slot!!.place)
        val key = LevelStickers.byLesson("playground-1")!!.key
        assertEquals(key, o.state.placements.at[slot.id])
        assertFalse("it is out of the tray", key in o.state.tray)
        // The finish queue shows it: the trail sticker (already stuck), the drop, the wearable (kept without asking).
        val items = RewardQueue.of(r, null, starter = r.starter, placedAt = r.placedAt)
        assertEquals(listOf("Trail", "Sticker", "Wear"), items.map { it::class.simpleName })
        assertTrue((items.last() as RewardItem.Wear).starter)
        assertEquals(slot, (items.first() as RewardItem.Trail).placedAt)
        // The state round-trips with the placement and the open item.
        val back = WorldState.decode(o.state.encode(), 0L)
        assertEquals(o.state.placements, back.placements)
        assertTrue(Starter.ITEM in back.wardrobe)
    }

    @Test fun theStarterIsOnlyForAKidsVeryFirstLesson() {
        val s = WorldState(seed = 4L)
        // A grown-up's lesson: nothing.
        val grown = World.lessonComplete(s, null, "sit", starter = false)
        assertFalse(grown.reward.starter); assertTrue(grown.reward.unlocked.isEmpty()); assertTrue(grown.state.placements.at.isEmpty())
        // The second lesson: nothing new to wear and no sticker stuck for them.
        val first = World.lessonComplete(s, Place.GARDEN, "playground-1", starter = true)
        val second = World.lessonComplete(first.state, Place.GARDEN, "playground-2", starter = true)
        assertFalse(second.reward.starter); assertTrue(second.reward.unlocked.isEmpty()); assertEquals(first.state.placements, second.state.placements)
        // A kid whose wardrobe already holds something is not given a second starter.
        val owned = World.lessonComplete(s.copy(wardrobe = setOf(WearItem.BOW_TIE)), Place.GARDEN, "playground-1", starter = true)
        assertFalse(owned.reward.starter)
        // The practice track keeps its order: the bandana is in the set at 22, which later gives only the cowboy hat.
        assertEquals(22, (WearItem.BANDANA.earn as Earn.Practice).n)
        assertEquals(1, (Wardrobe.nextPractice(setOf(WearItem.BANDANA))!!.first().earn as Earn.Practice).n)
    }

    @Test fun theStarterIsFreeAndKidCopyKeepsItsRules() {
        assertEquals(WearItem.BANDANA, Starter.ITEM)
        for (l in listOf(Starter.PLACED_LINE, Starter.WEARING_LINE, Wardrobe.MORE_SOON, Wardrobe.MORE_SOON_LINE)) {
            assertTrue(l, l.first().isUpperCase() && l.last() in ".!?" && "—" !in l && "–" !in l && l.none { it.isDigit() })
        }
        assertEquals("Something new to wear!", Wardrobe.revealLine(listOf(WearItem.BANDANA), ask = false))
        assertEquals("Something new to wear! Keep it on?", Wardrobe.revealLine(listOf(WearItem.BANDANA)))
    }
}
