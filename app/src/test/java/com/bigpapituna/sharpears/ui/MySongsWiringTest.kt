package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.nav.NavGraph
import com.bigpapituna.sharpears.engine.nav.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * My songs (2.8.1) wired end to end, read from the sources (composables and Android code): the Songs tab's section and its
 * Add button open the picker (MIDI, any file as the fallback, the URI never kept), the title dialog adds the song, and the
 * song is an ordinary library song from then on: the player, progress and the brief's titles all come through
 * SongRepository.all(), which holds My songs.
 */
class MySongsWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText().replace("\r\n", "\n")

    @Test fun theAddButtonPicksAFileAndTheTitleDialogAddsIt() {
        val activity = src("MainActivity.kt")
        assertTrue(Regex("""onAdd = \{ pickMySong\.launch\(arrayOf\("audio/midi"[^)]*"\*/\*"\)\) }""").containsMatchIn(activity))
        assertTrue("val pickMySong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())" in activity)
        assertTrue("repo.readPicked(uri)" in activity && "MySongs.scan(bytes, name)" in activity)
        assertTrue("MySongDialog(d, onAdd = { title, both ->" in activity)
        assertTrue("repo.addMine(d.bytes, d.scan.title, title, both)" in activity)
        assertTrue("a refused file says why", "mySongRefused = e.problem.line" in activity)
        // The picked file is copied, never kept by its URI.
        for (f in listOf(activity, src("data/SongRepository.kt"))) assertFalse("takePersistableUriPermission" in f)
    }

    @Test fun theSongsTabShowsMySongsWithItsMenu() {
        val library = src("ui/LibraryScreen.kt")
        assertTrue("MySongs.Words.SECTION" in library && "MySongs.Words.ADD" in library)
        assertTrue("val mine = myShelf(entries)" in library && "entries.filter { it.meta.mine } + entries.filter { it.meta.user && it.available }" in library)
        // Rename and Delete from the overflow or a long press; Delete asks once.
        assertTrue("combinedClickable(onLongClick = if (ownMenu) ({ menu = true }) else null" in library)
        assertTrue("|| m.mine" in library && "MySongs.Words.DELETE_LINE" in library)
        assertEquals(1, Regex("""title = \{ Text\("Delete \$\{m.title\}\?"\) }""").findAll(library).count())
        val activity = src("MainActivity.kt")
        assertTrue("onRename = { m, title -> repo.rename(m, title)" in activity)
        assertTrue(Regex("""onDelete = \{ m ->\s*repo\.delete\(m\)""").containsMatchIn(activity))
    }

    /** A song of mine is a library song: the player opens it from Songs, and every title lookup (the brief's too) finds it. */
    @Test fun mySongsPlayAndAreTitledLikeEveryOtherSong() {
        assertTrue("Player" in NavGraph.OPENS.getValue(NavGraph.tab(NavTab.SONGS)))
        val repo = src("data/SongRepository.kt")
        assertTrue("b + mine()" in repo)
        assertTrue("if (meta.mine) return MySongs.song(" in repo)
        assertTrue("SongRepository(context).all().associate { it.id to it.title }" in src("ui/brief/BriefEnv.kt"))
        assertTrue("SongRepository(app).all().associate { it.id to it.title }" in src("data/BriefAi.kt"))
    }

    /** The stored list round-trips, and an entry whose id could name another path is dropped. */
    @Test fun theListRoundTripsAndRefusesPaths() {
        val a = SongRepository.mineMeta("billie-jean-3fa2c1", "Billie Jean", bothHands = true)
        val b = SongRepository.mineMeta("song-00aa11", "A \"quoted\" title", bothHands = false)
        assertTrue(a.mine && !a.user)
        assertEquals("songs-import/billie-jean-3fa2c1.mid", a.file)
        assertEquals(HandRule.Auto, a.hands)
        assertEquals(listOf(a, b), SongRepository.parseMine(SongRepository.mineJson(listOf(a, b))))
        val bad = """[{"id":"../../shared_prefs/profiles","title":"x"},{"id":"ok-123456","title":"Fine"},{"title":"no id"}]"""
        assertEquals(listOf("ok-123456"), SongRepository.parseMine(bad).map { it.id })
        assertEquals(emptyList<Any>(), SongRepository.parseMine(null))
    }
}
