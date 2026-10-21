package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.Trail
import com.bigpapituna.sharpears.ui.world.SCENE_RATIO
import com.bigpapituna.sharpears.ui.world.WorldAnchors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import com.bigpapituna.sharpears.engine.playground.Place as PagePlace

/** The world's anchors follow the pages: page index to scene, lesson index on the page to stop, twelve stops a scene. */
class MapAnchorsTest {
    private fun page(id: String, place: PagePlace, n: Int) = PlaygroundPage(id, "lv-$id", place, id, List(n) { "$id-${it + 1}" })
    private val pages = listOf(page("garden", PagePlace.GARDEN, 12), page("pond", PagePlace.POND, 12), page("hall", PagePlace.CONCERT_HALL, 7))

    @Test fun everySceneHasTwelveStopsInThreeWindingRows() {
        for (place in Place.entries) {
            val stops = Trail.stops(place)
            assertEquals(12, stops.size)
            assertTrue("inside the scene", stops.all { it.x in 0.05f..0.95f && it.y in 0.3f..0.97f })
            val rows = stops.chunked(4)
            assertEquals(3, rows.size)
            // Left to right, back right to left, then left to right again.
            assertTrue(rows[0].zipWithNext().all { (a, b) -> b.x > a.x })
            assertTrue(rows[1].zipWithNext().all { (a, b) -> b.x < a.x })
            assertTrue(rows[2].zipWithNext().all { (a, b) -> b.x > a.x })
            assertTrue("rows go down the scene", rows.zipWithNext().all { (a, b) -> b.minOf { it.y } > a.maxOf { it.y } })
            // Stones never crowd each other: at least a tenth of the width apart (heights converted to widths).
            val close = stops.indices.flatMap { i -> (i + 1 until stops.size).map { j ->
                hypot(stops[i].x - stops[j].x, (stops[i].y - stops[j].y) / SCENE_RATIO) } }.min()
            assertTrue("$place stones $close apart", close > 0.1f)
            // The trail's line runs through every stop, entering on the left and leaving over an edge.
            val path = Trail.path(place)
            assertTrue(path.containsAll(stops))
            assertTrue(path.first().x < 0f && (path.last().x > 1f || path.last().x < 0f))
        }
    }

    @Test fun aShortPageTakesTheFirstStopsALongOneDoesNotLeaveTheScene() {
        val twelve = Trail.stops(Place.POND)
        assertEquals(twelve.take(5), Trail.stops(Place.POND, 5))
        assertTrue(Trail.stops(Place.GARDEN, 0).isEmpty())
        val long = Trail.stops(Place.GARDEN, 20)
        assertEquals(20, long.size)
        assertTrue(long.all { it.y in 0.3f..0.97f })
        assertTrue("five rows, still going down", long.chunked(4).zipWithNext().all { (a, b) -> b.minOf { it.y } > a.maxOf { it.y } })
    }

    /** 2.3: the fair is a page of four: one row across its scene, its gate where that row leaves it. */
    @Test fun aShortPageIsOneRowWithItsGateAtItsEnd() {
        val four = Trail.stops(Place.FAIR, 4)
        assertEquals(Trail.stops(Place.FAIR).take(4), four)
        assertTrue("one row, left to right", four.zipWithNext().all { (a, b) -> b.x > a.x } && four.maxOf { it.y } - four.minOf { it.y } < 0.03f)
        val path = Trail.path(Place.FAIR, 4)
        assertTrue("through every stone, in from the left, out on the right", path.containsAll(four) && path.first().x < 0f && path.last().x > 1f)
        val gate = WorldAnchors.gate(Place.FAIR, 4)
        assertTrue("the gate stands where the row leaves the scene", gate.x > four.last().x && kotlin.math.abs(gate.y - four.first().y) < 0.05f)
        assertEquals(four[3].x, WorldAnchors.stop(Place.FAIR, 3, 4)!!.x)
        // A page of twelve still ends at the bottom row.
        assertTrue(WorldAnchors.gate(Place.FAIR).y > gate.y + 0.2f)
    }

    @Test fun lessonsMapToTheirPagesSceneAndStop() {
        val a = WorldAnchors.lesson(1, pages)!!
        assertEquals(Place.GARDEN, a.place)
        assertEquals(Trail.stops(Place.GARDEN)[0].x, a.x)
        val p3 = WorldAnchors.lesson("pond-3", pages)!!
        assertEquals(Place.POND, p3.place)
        assertEquals(Trail.stops(Place.POND)[2].y, p3.y)
        assertEquals(WorldAnchors.lesson(15, pages), p3)
        val h7 = WorldAnchors.lesson("hall-7", pages)!!
        assertEquals(Place.CONCERT, h7.place)
        assertEquals(Trail.stops(Place.CONCERT)[6].x, h7.x)
        assertNull("past the last lesson", WorldAnchors.lesson(32, pages))
        assertNull(WorldAnchors.lesson("basics-1", pages))
        assertNull(WorldAnchors.lesson(0, pages))
        // The gate stands at the trail's end in the last scene, inside it.
        val g = WorldAnchors.gate(Place.CONCERT)
        assertTrue(g.x in 0.05f..0.97f && g.y in 0.3f..0.97f)
    }

    @Test fun theRealPagesAnchorWithoutCrashing() {
        val total = PLAYGROUND_PAGES.sumOf { it.lessonIds.size }
        (1..total).forEach { assertNotNull("lesson $it", WorldAnchors.lesson(it)) }
        PLAYGROUND_PAGES.forEach { p -> p.lessonIds.forEach { assertEquals(KidPages.worldPlace(p), WorldAnchors.lesson(it)!!.place) } }
        assertNull(WorldAnchors.lesson(total + 1))
    }

    // 2.2's stickersSitClearOfTheStones became engine/world/DecorTest (2.5: the kid places stickers in named slots).
}
