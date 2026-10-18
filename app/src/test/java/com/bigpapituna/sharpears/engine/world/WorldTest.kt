package com.bigpapituna.sharpears.engine.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorldTest {
    private val day = LocalDate.of(2026, 10, 18)

    @Test fun eggEveryThirdLessonHatchesOnTheNext() {
        var s = WorldState(seed = 3L)
        val laid = mutableListOf<Int>()
        val hatched = mutableListOf<Int>()
        for (lesson in 1..10) {
            val o = World.lessonComplete(s)
            if (o.reward.eggLaid) laid += lesson
            if (o.reward.eggHatched) {
                hatched += lesson
                assertEquals(2, o.reward.drops.size)
                val egg = o.reward.drops[1]
                assertEquals(DropSource.EGG, egg.source)
                assertTrue("an egg hatches one rarity up: ${egg.rarity}", egg.rarity.special)
            } else assertEquals(1, o.reward.drops.size)
            assertEquals(lesson % 3 == 0, o.state.egg)
            s = o.state
        }
        assertEquals(listOf(3, 6, 9), laid)
        assertEquals(listOf(4, 7, 10), hatched)
        assertEquals(10, s.lessons)
        assertEquals(13, s.history.rolls)
    }

    @Test fun anEggHatchesOneRarityUpFromItsOwnRoll() {
        val h = DropHistory(rolls = 5)
        val plain = Drops.rarity(8L, h)
        assertEquals(plain.up(), Drops.rarity(8L, h, bump = 1))
        assertEquals(Rarity.SHINY, Rarity.SHINY.up())
        assertEquals(Rarity.RARE, Rarity.COMMON.up())
    }

    @Test fun songsDropEveryFinishAndKeepTheBestRecord() {
        var s = WorldState(seed = 1L)
        var o = World.songComplete(s, "twinkle", 72)
        assertEquals(1, o.reward.drops.size)
        assertEquals(DropSource.SONG, o.reward.drops[0].source)
        assertEquals(RecordUp("twinkle", null, Medal.BRONZE), o.reward.record)
        s = o.state
        o = World.songComplete(s, "twinkle", 65)                // worse: no change
        assertNull(o.reward.record); assertEquals(72, o.state.records["twinkle"])
        s = o.state
        o = World.songComplete(s, "twinkle", 96)
        assertEquals(RecordUp("twinkle", Medal.BRONZE, Medal.GOLD), o.reward.record)
        assertEquals(96, o.state.records["twinkle"])
        o = World.songComplete(o.state, "ode", null)            // a wait run: a sticker, no record
        assertNull(o.reward.record); assertFalse("ode" in o.state.records)
        assertEquals(4, o.state.history.rolls)
        // Songs never lay eggs.
        assertFalse(o.state.egg); assertEquals(0, o.state.lessons)
    }

    @Test fun previewIsWhatTheNextLessonGives() {
        var s = WorldState(seed = 12L)
        repeat(12) {
            val p = World.preview(s)
            val o = World.lessonComplete(s)
            assertEquals(p.sticker, o.reward.drops[0].sticker)
            assertEquals(p.eggHatches, o.reward.eggHatched)
            assertEquals(p.eggLays, o.reward.eggLaid)
            if (p.eggHatches) assertEquals(p.hatch, o.reward.drops[1].sticker)
            if (p.rareDue) assertTrue(o.reward.drops[0].rarity.special)
            s = o.state
        }
        val pond = World.preview(s, Place.POND)
        assertEquals(Place.POND, pond.sticker.place)
    }

    @Test fun rewardSaysWhenTheHouseGrowsAndItemsUnlock() {
        var s = WorldState(seed = 4L)
        var houseUps = 0
        val unlocked = mutableListOf<WearItem>()
        repeat(200) {
            val o = World.lessonComplete(s)
            if (o.reward.houseUp) houseUps++
            unlocked += o.reward.unlocked
            assertEquals(House.stage(o.state.unique), o.reward.houseAfter)
            assertEquals(o.reward.drops.last().sticker.id, o.state.latest)
            s = o.state
        }
        assertEquals(2, houseUps)
        assertEquals(WearItem.entries.toList(), unlocked)
        assertEquals(3, s.house)
    }

    @Test fun albumShowsGapsPerPlace() {
        val owned = mapOf("pond-1" to 1, "pond-2" to 3, "pond-3" to 1, "pond-4" to 1, "pond-5" to 1, "pond-6" to 1, "pond-9" to 1, "garden-1" to 1)
        val a = Album.of(owned)
        val pond = a.page(Place.POND)
        assertEquals("7 of 10 in the pond", pond.line)
        assertEquals(listOf("pond-7", "pond-8", "pond-10"), pond.gaps.map { it.id })
        assertEquals(2, pond.slots[1].doubles)
        assertEquals("1 of 10 in the garden", a.page(Place.GARDEN).line)
        assertEquals("0 of 10 in the concert hall", a.page(Place.CONCERT).line)
        assertEquals("8 of 30 stickers", a.line)
        assertFalse(a.complete)
        assertTrue(Album.of(Stickers.ALL.associate { it.id to 1 }).complete)
    }

    @Test fun houseStages() {
        assertEquals(1, House.stage(0)); assertEquals(1, House.stage(4))
        assertEquals(2, House.stage(5)); assertEquals(2, House.stage(14))
        assertEquals(3, House.stage(15)); assertEquals(3, House.stage(30))
        assertEquals(5, House.toNext(0)); assertEquals(1, House.toNext(14)); assertNull(House.toNext(15))
    }

    @Test fun sceneryPlacesWhatWasCollectedAndSeatsTheAnimals() {
        val owned = mapOf("garden-1" to 1, "garden-3" to 2, "pond-6" to 1, "pond-1" to 1, "concert-6" to 1, "garden-6" to 0)
        assertEquals(listOf("garden-1", "garden-3"), Scenery.items(Place.GARDEN, owned).map { it.id })
        assertEquals(listOf("concert-6"), Scenery.items(Place.CONCERT, owned).map { it.id })
        assertEquals(listOf("garden-3", "pond-1", "pond-6"), Scenery.audience(owned).map { it.id })
        assertEquals(9, Scenery.SEATS)
    }

    @Test fun recordThresholds() {
        assertNull(Medal.of(null)); assertNull(Medal.of(59))
        assertEquals(Medal.BRONZE, Medal.of(60)); assertEquals(Medal.BRONZE, Medal.of(79))
        assertEquals(Medal.SILVER, Medal.of(80)); assertEquals(Medal.SILVER, Medal.of(94))
        assertEquals(Medal.GOLD, Medal.of(95)); assertEquals(Medal.GOLD, Medal.of(100))
        assertNull(Records.up("a", 70, 75))
        assertNull(Records.up("a", null, 40))
        assertEquals(RecordUp("a", Medal.SILVER, Medal.GOLD), Records.up("a", 85, 97))
        val bests = mapOf("a" to 61, "b" to 99, "c" to 50, "d" to 85, "e" to 96)
        assertEquals(listOf("b", "e", "d", "a"), Records.canPlay(bests))
        assertEquals(mapOf(Medal.BRONZE to 1, Medal.SILVER to 1, Medal.GOLD to 2), Records.counts(bests))
    }

    @Test fun mergeRecordsKeepsTheHigherBest() {
        val s = WorldState(seed = 1L, records = mapOf("a" to 90, "b" to 50))
        assertEquals(mapOf("a" to 90, "b" to 70, "c" to 100), World.mergeRecords(s, mapOf("a" to 80, "b" to 70, "c" to 100)).records)
    }

    @Test fun jarMaths() {
        assertEquals(200, TreatJar.CAPACITY)
        var s = WorldState(seed = 1L)
        s = World.addNotes(s, 150)
        assertEquals(0.75f, TreatJar.fraction(s.jar), 1e-6f)
        assertEquals(15, TreatJar.biscuits(s.jar))
        assertEquals(50, TreatJar.toFull(s.jar))
        assertNull(World.tip(s))                                  // not full: nothing to tip
        s = World.addNotes(s, 80)
        assertEquals(200, s.jar)                                  // capped
        assertTrue(TreatJar.full(s.jar))
        assertEquals(s, World.addNotes(s, 5))                     // a full jar stays as it is
        assertEquals(s, World.addNotes(World.addNotes(s, 0), -3))
        val tipped = World.tip(s)!!
        assertEquals(0, tipped.jar)
        assertEquals(1, tipped.treats)
        assertEquals(TreatJar.BISCUITS, TreatJar.biscuits(200))
    }

    @Test fun crownOfTheDay() {
        val r = listOf(
            PupSaysResult("owner", 5, day, atMillis = 100),
            PupSaysResult("p1", 7, day, atMillis = 300),
            PupSaysResult("p2", 7, day, atMillis = 200),           // same best, there first: keeps the crown
            PupSaysResult("p3", 12, day.minusDays(1), atMillis = 50),
        )
        assertEquals("p2", Crown.of(r, day))
        assertEquals("p3", Crown.of(r, day.minusDays(1)))
        assertNull(Crown.of(r, day.plusDays(1)))
        assertNull(Crown.of(listOf(PupSaysResult("owner", 0, day)), day))
    }

    @Test fun pupSaysKeepsTheDaysBestAndForgetsOldDays() {
        var s = WorldState(seed = 1L)
        s = World.pupSays(s, 4, day.minusDays(20))
        s = World.pupSays(s, 6, day)
        s = World.pupSays(s, 3, day)
        assertEquals(mapOf(day to 6), s.pupSays)
    }

    @Test fun dressUnlocksAndSlots() {
        assertTrue(Dress.unlocked(0).isEmpty())
        assertEquals(listOf(WearItem.BOW_TIE), Dress.unlocked(1))
        assertEquals(listOf(WearItem.SUNGLASSES, WearItem.PARTY_HAT), Dress.newlyUnlocked(2, 6))
        assertEquals(WearItem.SUNGLASSES to 2, Dress.next(1))
        assertNull(Dress.next(30))
        // One per slot: a second hat replaces the first; the bow tie stays.
        var o = Outfit().toggle(WearItem.PARTY_HAT).toggle(WearItem.BOW_TIE).toggle(WearItem.TOP_HAT)
        assertEquals(setOf(WearItem.TOP_HAT, WearItem.BOW_TIE), o.items)
        o = o.toggle(WearItem.TOP_HAT)
        assertEquals(setOf(WearItem.BOW_TIE), o.items)
        // Locked items cannot be worn.
        val s = WorldState(seed = 1L, history = DropHistory(owned = mapOf("garden-1" to 1, "garden-2" to 1)))
        assertEquals(Outfit.NONE, World.wear(s, WearItem.SUNGLASSES).outfit)
        assertEquals(setOf(WearItem.BOW_TIE), World.wear(s, WearItem.BOW_TIE).outfit.items)
        assertEquals(setOf(WearItem.BOW_TIE), Dress.allowed(Outfit(setOf(WearItem.BOW_TIE, WearItem.TOP_HAT)), 2).items)
    }

    @Test fun serialisationRoundTrip() {
        val s = WorldState(
            seed = -8_123_456_789_012_345L,
            history = DropHistory(owned = mapOf("garden-1" to 2, "pond-3" to 1, "concert-10" to 4), sinceRare = 2, rolls = 7),
            lessons = 5, egg = true, jar = 133, treats = 4,
            records = mapOf("twinkle" to 88, "ode" to 100),
            outfit = Outfit(setOf(WearItem.SUNGLASSES, WearItem.SCARF)),
            pupSays = mapOf(day to 6, day.minusDays(1) to 3),
            latest = "pond-3",
        )
        assertEquals(s, WorldState.decode(s.encode(), seed = 0L))
        assertEquals(WorldState(seed = 9L), WorldState.decode(null, seed = 9L))
        assertEquals(WorldState(seed = 9L), WorldState.decode("not json", seed = 9L))
        // Ids from a newer version are dropped, not fatal; numbers out of range are clamped.
        val odd = WorldState.decode("""{"seed":5,"owned":{"moon-1":2,"garden-2":1},"outfit":["cape","bowtie"],"jar":999,"latest":"moon-1"}""", 0L)
        assertEquals(mapOf("garden-2" to 1), odd.history.owned)
        assertEquals(setOf(WearItem.BOW_TIE), odd.outfit.items)
        assertEquals(TreatJar.CAPACITY, odd.jar)
        assertNull(odd.latest)
        assertEquals(5L, odd.seed)
    }
}
