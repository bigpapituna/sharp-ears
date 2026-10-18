package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.SmfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The kid's song list: only songs played by colour (easy, the app's own, white keys only), in the library's order. */
class KidSongsTest {
    private fun song(id: String, keys: List<Int>, difficulty: String = "easy", imported: Boolean = false, available: Boolean = true) =
        SongFacts(id, id, difficulty, imported, available, keys)
    private val cde = listOf(60, 62, 64, 65, 67)

    @Test fun onlyEasyOwnWhiteKeySongsOnThePhone() {
        assertTrue(KidSongs.isKidSong(song("twinkle", cde + 48 + 72)))
        assertFalse("a black key is two colours", KidSongs.isKidSong(song("minuet", cde + 66)))
        assertFalse(KidSongs.isKidSong(song("moonlight", cde, difficulty = "medium")))
        assertFalse(KidSongs.isKidSong(song("alicia", cde, difficulty = "imported", imported = true)))
        assertFalse("an import is never a kid song, even easy", KidSongs.isKidSong(song("mine", cde, imported = true)))
        assertFalse("not on the phone", KidSongs.isKidSong(song("gone", cde, available = false)))
        assertFalse("no notes read", KidSongs.isKidSong(song("empty", emptyList())))
        val order = listOf(song("b", cde), song("x", cde + 61), song("a", cde), song("buns", listOf(64, 62, 60, 64)))
        assertEquals("the simplest first, then the library's order", listOf("buns", "b", "a"), KidSongs.pick(order).map { it.id })
    }

    @Test fun theOpeningIsTheMelodysFirstNotesTopOfEachChord() {
        val melody = listOf(0L to 64, 0L to 60, 500L to 62, 1000L to 60, 1500L to 62, 2000L to 64, 2500L to 64, 3000L to 64)
        assertEquals(listOf(64, 62, 60, 62, 64, 64), KidSongs.opening(melody))
        assertEquals(listOf(64, 62), KidSongs.opening(melody.shuffled(java.util.Random(3)), n = 2))
        assertTrue(KidSongs.opening(emptyList()).isEmpty())
    }

    /** The real library: the nursery tunes are in, the songs with sharps and the harder pieces are out. */
    @Test fun theLibrarysColourSongs() {
        val assets = File("src/main/assets")
        val index = File(assets, "songs/index.json").readText()
        val entries = Regex("\"id\": \"([^\"]+)\",\\s*\"file\": \"([^\"]+)\",\\s*\"title\": \"([^\"]+)\",\\s*\"composer\": \"[^\"]*\",\\s*\"difficulty\": \"([^\"]+)\"")
            .findAll(index).map { it.groupValues }.toList()
        val facts = entries.map { (_, id, file, title, difficulty) ->
            val imported = file.startsWith("user/")
            val keys = if (imported) emptyList() else {
                val rule = if (id == "prelude-c") HandRule.Tracks(right = 2, left = 1) else HandRule.Auto
                SmfParser.parse(File(assets, "songs/$file").readBytes(), id, title, rule).notes.map { it.midi }
            }
            SongFacts(id, title, difficulty, imported, !imported, keys)
        }
        val kid = KidSongs.pick(facts).map { it.id }
        println("kid songs (${kid.size} of ${facts.size}): $kid")
        assertTrue(kid.containsAll(listOf("twinkle", "mary-had-a-little-lamb", "hot-cross-buns", "ode-to-joy")))
        assertTrue("nothing medium, hard or imported", facts.filter { it.id in kid }.all { it.difficulty == "easy" && !it.imported })
        assertTrue("enough to pick from", kid.size >= 8)
        // Every one opens on notes that each have one colour.
        val twinkle = SmfParser.parse(File(assets, "songs/twinkle.mid").readBytes(), "twinkle", "Twinkle", HandRule.Auto)
        val opening = KidSongs.opening(twinkle.notes.filter { it.hand == Hand.RIGHT }.map { it.startMs to it.midi })
        assertEquals(6, opening.size)
        assertEquals("Twinkle opens on a repeated note", opening[0], opening[1])
    }
}
