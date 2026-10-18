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
        /** The 2026-10-13 content batch (Sharp Ears 2.0): public-domain tunes for the Repertoire levels, no videos yet. */
        val NEW_SONGS = listOf("mary-had-a-little-lamb", "hot-cross-buns", "row-row-row-your-boat", "london-bridge", "yankee-doodle",
            "old-macdonald", "haydn-surprise", "au-clair-de-la-lune", "lightly-row", "we-wish-you-a-merry-christmas",
            "eine-kleine-nachtmusik", "gymnopedie-1", "hall-of-the-mountain-king", "rondo-alla-turca", "bach-toccata-opening")
        val TO_CURATE = setOf("scarborough-fair", "auld-lang-syne", "silent-night", "jingle-bells", "saints", "oh-susanna",
            "brahms-lullaby", "frere-jacques") + NEW_SONGS
    }
    private val assets = File("src/main/assets")
    private val index = File(assets, "songs/index.json").readText()

    // org.json is not available on the JVM, so the index is read with patterns; enough for a content check.
    private val entries = Regex("\"id\": \"([^\"]+)\",\\s*\"file\": \"([^\"]+)\"").findAll(index).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test fun everyBundledSongParsesWithBothHandsAndHasATutorial() {
        val bundled = entries.filter { !it.second.startsWith("user/") }
        assertEquals("23 older songs and 15 new ones", 38, bundled.size)
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

    @Test fun newSongsAreCompleteSectionedAndPublicDomain() {
        @Suppress("UNCHECKED_CAST")
        val songs = (com.bigpapituna.sharpears.engine.MiniJson.read(index) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>
        val byId = songs.associateBy { it["id"] as String }
        assertEquals("ids are unique", songs.size, byId.size)
        val difficulties = NEW_SONGS.map { byId.getValue(it)["difficulty"] }
        assertTrue("a spread of difficulty: $difficulties", setOf("easy", "medium", "hard").all { it in difficulties })
        for (id in NEW_SONGS) {
            val meta = byId.getValue(id)
            for (key in listOf("title", "composer", "license", "attribution")) {
                val v = meta[key] as String
                assertTrue("$id $key", v.isNotBlank() && v.none { it in "\u2014\u2013" })
            }
            assertEquals("$id license", "Public Domain", meta["license"])
            assertEquals("$id file", "$id.mid", meta["file"])
            assertEquals("$id: no tutorial videos yet", emptyList<Any?>(), meta["videos"])
            val song = SmfParser.parse(File(assets, "songs/$id.mid").readBytes(), id, meta["title"] as String, HandRule.Auto)
            assertTrue("$id has ${song.notes.size} notes", song.notes.size > 20)
            assertTrue("$id has both hands", song.notes.any { it.hand == Hand.RIGHT } && song.notes.any { it.hand == Hand.LEFT })
            assertTrue("$id is at least ten seconds", song.durationMs >= 10_000)
            // Sections: listed in the index (name and bar) and written into the file as markers, in the same order.
            @Suppress("UNCHECKED_CAST")
            val listed = meta["sections"] as List<Map<String, Any?>>
            assertTrue("$id sections", listed.size >= 2)
            assertEquals("$id: sections in the file", listed.map { it["name"] }, song.sections.map { it.name })
            assertEquals("$id: first section starts at the top", 0L, song.sections.first().startMs)
            assertTrue("$id: sections run forward", song.sections.zipWithNext().all { (a, b) -> a.startMs < b.startMs && a.endMs == b.startMs })
            assertTrue("$id: the last section ends with the song", song.sections.last().endMs == song.durationMs)
        }
    }

    /**
     * 2.2.1 QA: a song is spelled the same in the library and in the lessons that teach it. The pond's lesson said
     * "Twinkle, Twinkle, Little Star" (as the library says "Row, Row, Row Your Boat") and Songs said "Twinkle Twinkle
     * Little Star". A library title's "(opening)" or "(easy)" is the library's own.
     */
    @Test fun lessonsSpellTheLibrarysSongTitles() {
        @Suppress("UNCHECKED_CAST")
        val songs = (com.bigpapituna.sharpears.engine.MiniJson.read(index) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>
        fun bare(t: String) = t.replace(Regex(" \\([^)]*\\)$"), "")
        fun key(t: String) = bare(t).lowercase().filter { it.isLetterOrDigit() }
        val library = songs.map { bare(it["title"] as String) }.associateBy(::key)
        var named = 0
        for (lesson in com.bigpapituna.sharpears.engine.COURSE.flatMap { it.lessons }) {
            val title = library[key(lesson.title)] ?: continue
            named++
            assertEquals("lesson ${lesson.id}", title, lesson.title)
        }
        assertTrue("lessons named after songs: $named", named >= 10)
    }
}
