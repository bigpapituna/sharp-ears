package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The library tile art: the opening notes placed inside the tile, deterministic, and the facts beside it. */
class SongArtTest {
    private fun load(id: String) = SmfParser.parse(File("src/main/assets/songs/$id.mid").readBytes(), id, id)

    @Test fun twinkleOpensLowAndClimbs() {
        val art = SongArt.of(load("twinkle"))
        assertTrue(art.notes.isNotEmpty())
        assertTrue(art.notes.size <= SongArt.MAX_NOTES)
        // Every bar fits the tile.
        art.notes.forEach { n ->
            assertTrue("x ${n.x}", n.x >= 0f && n.x + n.w <= 1.0001f)
            assertTrue("y ${n.y}", n.y >= 0f && n.y < 1f)
            assertTrue("h ${n.h}", n.h > 0f && n.y + n.h <= 1.0001f)
        }
        // The first note sits on the hit line; both hands are drawn (melody and bass).
        assertEquals(0f, art.notes.minOf { it.y }, 0.0001f)
        assertTrue(art.notes.any { it.right } && art.notes.any { !it.right })
        // C C G G: the right hand's third note is further right (higher) than its first.
        val right = art.notes.filter { it.right }
        assertTrue(right[2].x > right[0].x)
    }

    @Test fun wideLeapsCloseUp() {
        // Twinkle's bass sits more than an octave under the tune; on the tile no gap is wider than two semitone steps.
        val xs = SongArt.of(load("twinkle")).notes.map { it.x }.distinct().sorted()
        val gaps = xs.zipWithNext { a, b -> b - a }
        assertTrue(gaps.max() <= 2 * gaps.min() + 0.0001f)
    }

    @Test fun sameFileSameArt() {
        assertEquals(SongArt.of(load("fur-elise")), SongArt.of(load("fur-elise")))
    }

    @Test fun lengthIsTheSongsDuration() {
        val song = load("twinkle")
        assertEquals(song.durationMs, SongArt.of(song).durationMs)
    }

    @Test fun everyBundledSongDraws() {
        File("src/main/assets/songs").listFiles { f -> f.extension == "mid" }!!.forEach { f ->
            val art = SongArt.of(SmfParser.parse(f.readBytes(), f.name, f.name))
            assertTrue(f.name, art.notes.size in 3..SongArt.MAX_NOTES)
        }
    }

    @Test fun starsAndLength() {
        assertEquals(1, SongArt.stars("easy"))
        assertEquals(2, SongArt.stars("medium"))
        assertEquals(3, SongArt.stars("hard"))
        assertEquals(0, SongArt.stars("imported"))
        assertEquals("2:10", SongArt.length(130_200))
        assertEquals("0:05", SongArt.length(4_600))
        assertEquals("0:00", SongArt.length(0))
    }
}
