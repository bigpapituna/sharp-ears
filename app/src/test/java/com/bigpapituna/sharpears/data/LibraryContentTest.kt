package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.SmfParser
import com.bigpapituna.sharpears.engine.parseChord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** The bundled content: songs parse with both hands, a broken import is refused with a message, lesson packs load. */
class LibraryContentTest {
    private companion object {
        val TO_CURATE = setOf("scarborough-fair", "auld-lang-syne", "silent-night", "jingle-bells", "saints", "oh-susanna",
            "brahms-lullaby", "frere-jacques")
    }
    private val assets = File("src/main/assets")
    private val index = File(assets, "songs/index.json").readText()

    // org.json is not available on the JVM, so the index is read with patterns; enough for a content check.
    private val entries = Regex("\"id\": \"([^\"]+)\",\\s*\"file\": \"([^\"]+)\"").findAll(index).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test fun everyBundledSongParsesWithBothHandsAndHasATutorial() {
        val bundled = entries.filter { !it.second.startsWith("user/") }
        assertEquals("23 bundled songs", 23, bundled.size)
        for ((id, file) in bundled) {
            val rule = if (id == "prelude-c") HandRule.Tracks(right = 2, left = 1) else HandRule.Auto
            val song = SmfParser.parse(File(assets, "songs/$file").readBytes(), id, id, rule)
            val right = song.notes.count { it.hand == Hand.RIGHT }; val left = song.notes.count { it.hand == Hand.LEFT }
            println("%-18s %4d notes (R %d, L %d) %5.1f s".format(id, song.notes.size, right, left, song.durationMs / 1000.0))
            assertTrue("$id has both hands", right > 0 && left > 0)
        }
        // Every entry (Alicia included) names at least one video with a check date, except the 2026-10-08 batch,
        // whose videos are still to be curated (no internet when they were added): those carry an empty list.
        val starts = Regex("\"id\": \"([^\"]+)\",\\s*\"file\"").findAll(index).map { it.range.first to it.groupValues[1] }.toList()
        starts.forEachIndexed { i, (at, id) ->
            val block = index.substring(at, starts.getOrNull(i + 1)?.first ?: index.length)
            if (id in TO_CURATE) assertTrue("$id: empty video list", block.contains("\"videos\": []"))
            else assertTrue("video for $id", block.contains("\"checkedOn\": \"2026-"))
        }
        assertTrue(entries.any { it.second == "user/alicia.mid" })
    }

    @Test fun easyAndFullVersionsNameEachOtherAndShareVideos() {
        // The app's own JSON reader (engine/MiniJson) works here, where org.json does not.
        @Suppress("UNCHECKED_CAST")
        val songs = (com.bigpapituna.sharpears.engine.MiniJson.read(index) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>
        val byId = songs.associateBy { it["id"] as String }
        val easy = songs.filter { (it["id"] as String).endsWith("-easy") }
        assertEquals(listOf("minuet-g-easy", "greensleeves-easy", "fur-elise-easy"), easy.map { it["id"] })
        for (e in easy) {
            val full = byId.getValue(e["pairWith"] as String)
            assertEquals(e["id"], full["pairWith"])
            assertEquals("easy", e["difficulty"])
            assertEquals(full["videos"], e["videos"])
            assertTrue((e["title"] as String).endsWith(" (easy)"))
        }
    }

    @Test fun aTruncatedMidiFileIsRefusedWithAMessage() {
        val good = File(assets, "songs/fur-elise.mid").readBytes()
        val meta = SongMeta("x", "user/x.mid", "x", "", "imported", HandRule.Auto, "", "")
        SongRepository.validate(good, meta)        // the whole file is fine
        for (cut in listOf(10, 30, good.size / 2)) {
            try { SongRepository.validate(good.copyOf(cut), meta); fail("cut at $cut accepted") }
            catch (e: ImportException) { assertTrue(e.message!!.startsWith("This is not a MIDI file")) }
        }
        try { SongRepository.validate("hello".toByteArray(), meta); fail("text accepted") } catch (_: ImportException) {}
    }

    @Test fun lessonPacksNameRealChordsAndProgressions() {
        val packs = File(assets, "lessons").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(packs.size >= 6)
        for (p in packs) {
            val text = p.readText()
            Regex("\"chord\": \"([^\"]+)\"").findAll(text).forEach { assertNotNull("${p.name}: ${it.groupValues[1]}", parseChord(it.groupValues[1])) }
            Regex("\"progression\": (\\d+)").findAll(text).forEach { assertTrue(p.name, it.groupValues[1].toInt() in PROGRESSIONS.indices) }
            Regex("\"requires\": \\[([^]]*)]").find(text)!!.groupValues[1].split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
                .forEach { req -> assertTrue("${p.name} requires $req", packs.any { it.nameWithoutExtension == req }) }
        }
    }
}
