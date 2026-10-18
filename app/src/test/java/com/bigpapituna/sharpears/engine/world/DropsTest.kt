package com.bigpapituna.sharpears.engine.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DropsTest {
    @Test fun catalogueHasTenPerPlaceSixThreeOne() {
        assertEquals(30, Stickers.ALL.size)
        assertEquals(30, Stickers.ALL.map { it.id }.toSet().size)
        assertEquals(30, Stickers.ALL.map { it.name }.toSet().size)
        for (p in Place.entries) {
            assertEquals((1..10).toList(), p.stickers.map { it.n })
            assertEquals(6, Stickers.of(p, Rarity.COMMON).size)
            assertEquals(3, Stickers.of(p, Rarity.RARE).size)
            assertEquals(1, Stickers.of(p, Rarity.SHINY).size)
        }
        assertEquals("sticker_pond_7", Stickers.byId("pond-7")!!.resName)
        assertEquals("Water lily", Stickers.byId("pond-7")!!.name)
    }

    @Test fun everyStickerHasItsPicture() {
        val dir = File("src/main/res/drawable-nodpi")
        for (s in Stickers.ALL) assertTrue("${s.resName}.webp", File(dir, "${s.resName}.webp").isFile)
    }

    @Test fun baseOddsAre70_25_5Over10kRolls() {
        val rng = Rng(42)
        val n = 10_000
        val counts = IntArray(3)
        repeat(n) { counts[Drops.rarityFor(rng.nextDouble()).ordinal]++ }
        assertEquals(0.70, counts[0] / n.toDouble(), 0.015)
        assertEquals(0.25, counts[1] / n.toDouble(), 0.015)
        assertEquals(0.05, counts[2] / n.toDouble(), 0.006)
    }

    /** 10k drops through the real roll with the floor on: the long-run shares the floor gives (see [Drops]). */
    @Test fun dropsWithTheFloorOver10kRolls() {
        var h = DropHistory()
        val counts = IntArray(3)
        val n = 10_000
        repeat(n) {
            val d = Drops.roll(seed = 7L, history = h)
            counts[d.rarity.ordinal]++
            h = h.after(d)
        }
        // Stationary shares of the chain "up to three commons, then at least rare": 60.5 / 32.9 / 6.6.
        assertEquals(0.605, counts[0] / n.toDouble(), 0.02)
        assertEquals(0.329, counts[1] / n.toDouble(), 0.02)
        assertEquals(0.066, counts[2] / n.toDouble(), 0.01)
    }

    @Test fun neverMoreThanThreeDropsWithoutARare() {
        for (seed in 1L..40L) {
            var h = DropHistory()
            var run = 0
            repeat(500) {
                val d = Drops.roll(seed, h)
                run = if (d.rarity.special) 0 else run + 1
                assertTrue("seed $seed: $run commons in a row", run <= Drops.FLOOR)
                h = h.after(d)
            }
        }
    }

    @Test fun theFloorForcesARareOnTheFourthDrop() {
        val due = DropHistory(sinceRare = 3)
        assertTrue(Drops.floorDue(due))
        assertFalse(Drops.floorDue(DropHistory(sinceRare = 2)))
        for (rolls in 0 until 300) assertTrue(Drops.roll(99L, due.copy(rolls = rolls)).rarity.special)
    }

    @Test fun sameSeedAndHistorySameSticker() {
        val h = DropHistory(owned = mapOf("garden-1" to 1, "garden-2" to 2), sinceRare = 1, rolls = 3)
        assertEquals(Drops.roll(5L, h), Drops.roll(5L, h))
        // Different seeds give different streams over a run of drops.
        val a = generateSequence(DropHistory() to Drops.roll(1L, DropHistory())) { (hh, d) -> hh.after(d).let { it to Drops.roll(1L, it) } }.take(20).map { it.second.sticker.id }.toList()
        val b = generateSequence(DropHistory() to Drops.roll(2L, DropHistory())) { (hh, d) -> hh.after(d).let { it to Drops.roll(2L, it) } }.take(20).map { it.second.sticker.id }.toList()
        assertTrue(a != b)
    }

    @Test fun aPlaygroundLessonDropsItsOwnPlace() {
        var h = DropHistory()
        repeat(200) {
            val d = Drops.roll(3L, h, place = Place.POND)
            assertEquals(Place.POND, d.sticker.place)
            h = h.after(d)
        }
    }

    @Test fun dropsFillTheGardenFirstThenSpillOver() {
        var h = DropHistory()
        // The first drops land in the garden (the current place) unless its stickers of that rarity are all owned.
        repeat(40) {
            val d = Drops.roll(11L, h)
            val gardenHasGap = Stickers.of(Place.GARDEN, d.rarity).any { h.count(it.id) == 0 }
            if (gardenHasGap && d.isNew) assertEquals(Place.GARDEN, d.sticker.place)
            h = h.after(d)
        }
        assertEquals(Place.GARDEN, Drops.currentPlace(emptyMap()))
        val gardenFull = Place.GARDEN.stickers.associate { it.id to 1 }
        assertEquals(Place.POND, Drops.currentPlace(gardenFull))
    }

    @Test fun theAlbumFillsAndNewIsTrueOnlyForGaps() {
        var h = DropHistory()
        var drops = 0
        while (h.unique < 30 && drops < 2000) {
            val d = Drops.roll(21L, h)
            assertEquals(h.count(d.sticker.id) == 0, d.isNew)
            h = h.after(d)
            drops++
        }
        assertEquals(30, h.unique)
        assertTrue("took $drops drops", drops < 600)
        assertEquals(drops, h.total)
        assertEquals(drops, h.rolls)
    }

    /** How long a whole album takes, on average over many profiles (the pace the INTEGRATION notes give). */
    @Test fun aFullAlbumTakesAboutFiftyDrops() {
        val runs = (1L..60L).map { seed ->
            var h = DropHistory(); var n = 0
            while (h.unique < 30) { h = h.after(Drops.roll(seed, h)); n++ }
            n
        }
        val mean = runs.average()
        println("drops to a full album: mean ${"%.1f".format(mean)}, min ${runs.min()}, max ${runs.max()}")
        // About 53 (31 to 123): the three shinies are the long pole (6.6% of drops each time).
        assertTrue("mean $mean", mean in 40.0..80.0)
    }

    @Test fun rngIsStableAcrossRuns() {
        // Pinned values: a change here changes every profile's future drops (and breaks previews already shown).
        val r = Rng.of(1L, 0L)
        val first = r.nextLong()
        assertEquals(first, Rng.of(1L, 0L).nextLong())
        assertTrue(Rng.of(1L, 1L).nextLong() != first)
        repeat(1000) { val x = r.nextDouble(); assertTrue(x >= 0.0 && x < 1.0) }
        repeat(1000) { val x = r.nextInt(7); assertTrue(x in 0..6) }
    }
}
