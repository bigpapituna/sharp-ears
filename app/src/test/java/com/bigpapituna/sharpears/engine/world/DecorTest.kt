package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.5: the scenes' named slots, where the kid sticks stickers, and the tray. */
class DecorTest {
    /** A phone's scene (360 dp less the margins) and the Fold's (capped at 720 dp). */
    private val widths = listOf(328f, 720f)

    @Test fun everySceneHasTwelveToFourteenNamedSlotsInsideIt() {
        assertEquals("ids are unique", Decor.SLOTS.size, Decor.SLOTS.map { it.id }.toSet().size)
        for (place in Place.entries) {
            val slots = Decor.of(place)
            assertTrue("$place has ${slots.size}", slots.size in 12..14)
            assertEquals("names are unique in $place", slots.size, slots.map { it.name }.toSet().size)
            slots.forEach { s ->
                val b = Decor.box(s)
                assertTrue("${s.id} inside the scene: $b", b.left >= 0f && b.right <= 1f && b.top >= 0f && b.bottom <= 1f)
                assertTrue(s.name, s.name.first().isLowerCase() && s.name.none { it.isDigit() })
                // The smallest sit in the gaps of the bottom row: 25 dp on a phone, 54 on the Fold.
                assertTrue("${s.id} big enough to see a sticker", s.size >= 0.075f)
            }
            // Two slots never overlap, so two stickers never sit on each other.
            for (i in slots.indices) for (j in i + 1 until slots.size)
                assertFalse("${slots[i].id} and ${slots[j].id} overlap", Decor.box(slots[i]).overlaps(Decor.box(slots[j])))
        }
    }

    @Test fun slotsKeepClearOfStonesStarsAndTheGateOnAPhoneAndTheFold() {
        val last = PLAYGROUND_PAGES.last()
        for (page in PLAYGROUND_PAGES) {
            val place = KidPages.worldPlace(page)
            for (w in widths) {
                val blocked = Decor.blocked(place, page.lessonIds.size, w, gate = page == last)
                Decor.of(place).forEach { s ->
                    blocked.forEachIndexed { k, b -> assertFalse("${s.id} at $w dp covers ${what(k, blocked.size, page == last)}: $b", Decor.box(s).overlaps(b)) }
                }
            }
        }
        // A whole page of twelve stones too, for every scene (a scene drawn with its full trail, as WorldDemo shows it).
        for (place in Place.entries) for (w in widths) {
            val blocked = Decor.blocked(place, Trail.STOPS, w, gate = false)
            if (place == Place.FAIR) continue       // the fair's page is four stones: its lower lawn is free for stickers
            Decor.of(place).forEach { s -> blocked.forEach { b -> assertFalse("${s.id} at $w dp", Decor.box(s).overlaps(b)) } }
        }
    }

    private fun what(k: Int, n: Int, gate: Boolean) = if (gate && k == n - 1) "the gate" else if (k % 2 == 0) "stone ${k / 2 + 1}" else "the stars of stone ${k / 2 + 1}"

    /** The scenes' own things stay visible: the treat jar and the egg's nest, the stage's pup, the audience's seats. */
    @Test fun slotsKeepClearOfTheScenesOwnThings() {
        val h = 1f / Decor.SCENE_HEIGHT
        val jar = SceneBox(0.555f - 0.055f, 0.47f - 0.07f * h, 0.555f + 0.055f, 0.47f + 0.07f * h)
        val egg = SceneBox(0.74f - 0.05f, 0.435f - 0.06f * h, 0.74f + 0.05f, 0.435f + 0.065f * h)
        Decor.of(Place.GARDEN).forEach { s -> assertFalse("${s.id} on the jar", Decor.box(s).overlaps(jar)); assertFalse("${s.id} on the egg", Decor.box(s).overlaps(egg)) }
        val stagePup = SceneBox(0.5f - 0.12f, 0.38f - 0.13f * h, 0.5f + 0.12f, 0.38f + 0.13f * h)
        val seats = SceneBox(0f, 0.81f, 1f, 1f)
        Decor.of(Place.CONCERT).forEach { s -> assertFalse("${s.id} on the stage's pup", Decor.box(s).overlaps(stagePup)); assertFalse("${s.id} on the seats", Decor.box(s).overlaps(seats)) }
    }

    @Test fun puttingMovingSwappingAndBackToTheTray() {
        var p = Placements()
        p = p.put("garden-1", "garden:sky")
        assertEquals("garden:sky", p.slotOf("garden-1"))
        // A move: the sticker leaves its old slot.
        p = p.put("garden-1", "pond:water")
        assertNull(p.at["garden:sky"]); assertEquals("garden-1", p.at["pond:water"])
        // Onto a taken slot from the tray: the one there goes back to the tray.
        p = p.put("lv:playground-1", "pond:water")
        assertEquals("lv:playground-1", p.at["pond:water"]); assertNull(p.slotOf("garden-1"))
        // Onto a taken slot from another slot: they swap.
        p = p.put("garden-1", "garden:sky").put("garden-1", "pond:water")
        assertEquals("garden-1", p.at["pond:water"]); assertEquals("lv:playground-1", p.at["garden:sky"])
        p = p.remove("garden-1")
        assertNull(p.slotOf("garden-1"))
        assertEquals("a slot that does not exist", p, p.put("garden-2", "moon:crater"))
        assertEquals(listOf(Place.GARDEN), p.on(Place.GARDEN).map { it.first.place })
        // The tray: everything earned and not stuck, newest first.
        assertEquals(listOf("garden-3", "garden-1"), Placements.tray(listOf("garden-1", "lv:playground-1", "garden-3"), p))
        // Stickers a person does not own, and slots that do not exist, are dropped.
        assertEquals(mapOf("garden:sky" to "lv:playground-1"), Placements(p.at + ("moon:crater" to "garden-1") + ("pond:sky" to "pond-4")).valid(setOf("lv:playground-1", "garden-1")).at)
    }

    @Test fun placementsLastThroughLessonsAndFinales() {
        var s = WorldState(seed = 5L)
        s = World.lessonComplete(s, Place.GARDEN, "playground-1").state
        assertEquals("the level sticker and the drop are in the tray", 2, s.tray.size)
        assertTrue("lv:playground-1" in s.tray)
        s = World.place(s, "lv:playground-1", "garden:roof")
        assertFalse("lv:playground-1" in s.tray)
        // A replay earns no second level sticker; the placement stays through more lessons and a finale's page.
        for (id in listOf("playground-1", "playground-2", "playground-12")) s = World.lessonComplete(s, Place.GARDEN, id).state
        assertEquals("garden:roof", s.placements.slotOf("lv:playground-1"))
        assertEquals(3, s.levels.size)
        assertEquals(s, WorldState.decode(s.encode(), 0L))
        // A sticker not owned cannot be placed.
        assertEquals(s, World.place(s, "lv:fair-4", "garden:sky"))
    }

    @Test fun levelStickersOnePerStonePageByPage() {
        val playground = PLAYGROUND_PAGES.flatMap { it.lessonIds }
        assertEquals(playground, LevelStickers.ALL.filter { it.page != LevelStickers.ROAD }.map { it.lessonId })
        assertEquals(PLAYGROUND_PAGES.map { it.id } + LevelStickers.ROAD, LevelStickers.PAGES)
        assertEquals(6, LevelStickers.of(LevelStickers.ROAD).size)
        val basics = com.bigpapituna.sharpears.engine.COURSE.first { it.id == "basics" }.lessons.map { it.id }
        assertEquals(basics, LevelStickers.of(LevelStickers.ROAD).map { it.lessonId })
        assertEquals("names are unique", LevelStickers.ALL.size, LevelStickers.ALL.map { it.name }.toSet().size)
        val banned = Regex("[0-9—–·#&/%]| - |\\b(wrong|mistakes?|fail\\w*)\\b", RegexOption.IGNORE_CASE)
        LevelStickers.ALL.forEach { l ->
            assertFalse(l.line, banned.containsMatchIn(l.line))
            assertTrue(l.line, l.line.first().isUpperCase() && l.line.last() in ".!?" && l.line.length <= 40)
            assertEquals(l, Decal.byKey(l.key))
        }
        // Stones finished before 2.5 earn theirs on the first open, into the tray.
        val s = World.earnLevels(WorldState(seed = 1L), done = { it in setOf("playground-1", "pond-2", "sit") })
        assertEquals(setOf("playground-1", "pond-2", "sit"), s.levels)
        assertEquals(3, s.tray.size)
    }
}
