package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.MySongs
import com.bigpapituna.sharpears.engine.SmfParser
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.family.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A curated tutorial video (PLAN 6.3), checked embeddable through YouTube's oEmbed endpoint on [checkedOn]. */
data class Video(val id: String, val title: String, val channel: String, val checkedOn: String)

data class SongMeta(
    val id: String,
    /** Asset path under songs/, "user/<name>.mid" for a file imported into the app's storage, or "songs-import/<id>.mid" for My songs. */
    val file: String,
    val title: String,
    val composer: String,
    val difficulty: String,
    val hands: HandRule,
    val license: String,
    val attribution: String,
    val videos: List<Video> = emptyList(),
    /** The other arrangement of the same piece ("fur-elise" and "fur-elise-easy" name each other). */
    val pairWith: String? = null,
    /** My songs: the left hand is kept as well as the tune (the title dialog's Both hands). */
    val bothHands: Boolean = false,
) {
    /** This one is the easy arrangement of its pair. */
    val easy get() = id.endsWith("-easy")
    val user get() = file.startsWith("user/")
    /** A song the learner added from a file (My songs, 2.8.1), played as its easy version (engine/MySongs.kt). */
    val mine get() = file.startsWith("${SongRepository.MINE_DIR}/")
}

class ImportException(message: String) : Exception(message)

/**
 * Bundled songs (assets/songs/index.json) plus imported ones (files/user/index.json) plus My songs (2.8.1). The bundled
 * index may name a user file (Alicia): it shows in the library as "import to unlock" until that file exists.
 *
 * My songs: the bytes of the file the learner picked, copied to files/songs-import/<id>.mid (the picker's URI is never
 * kept), and the list in the phone-wide preference file [MINE_FILE] (in ProgressFiles.NAMES and ProfileFiles.SHARED):
 * each song's title and hands, and a copy of its bytes, so the progress export carries the songs themselves and an import
 * on another phone writes their files back ([mine]). The song is made easy when it loads (engine/MySongs.kt).
 */
class SongRepository(private val context: Context) {
    private val userDir = File(context.filesDir, "user").apply { mkdirs() }
    private val userIndex = File(userDir, "index.json")
    private val mineDir = File(context.filesDir, "songs-import")
    private val minePrefs get() = context.getSharedPreferences(ProgressFiles.fileFor(MINE_FILE, Profile.OWNER_ID), Context.MODE_PRIVATE)

    val bundled: List<SongMeta> by lazy {
        parseIndex(context.assets.open("songs/index.json").bufferedReader().use { it.readText() })
    }

    /** A damaged index (a write cut short by a full disk or a killed process) reads as no imports, not a crash on every launch. */
    fun imported(): List<SongMeta> = if (!userIndex.exists()) emptyList() else runCatching { parseIndex(userIndex.readText()) }
        .getOrElse { android.util.Log.d(com.bigpapituna.sharpears.TAG, "imported songs: index unreadable, ignored ($it)"); emptyList() }

    fun all() = (bundled + imported().filter { u -> bundled.none { it.id == u.id } }).let { b -> b + mine().filter { m -> b.none { it.id == m.id } } }

    fun available(meta: SongMeta) = !(meta.user || meta.mine) || File(context.filesDir, meta.file).exists()

    fun load(meta: SongMeta): Song {
        if (meta.mine) return MySongs.song(songBytes(File(context.filesDir, meta.file)), meta.id, meta.title, meta.bothHands)
        val bytes = if (meta.user) songBytes(File(context.filesDir, meta.file))
                    else context.assets.open("songs/${meta.file}").use { it.readBytes() }
        return SmfParser.parse(bytes, meta.id, meta.title, meta.hands)
    }

    /** A stored song file's bytes. One bigger than any song (an older import, a file back from a backup) is refused unread. */
    private fun songBytes(f: File): ByteArray {
        if (f.length() > SmfParser.MAX_BYTES) throw MySongs.Refused(MySongs.Problem.TOO_BIG)
        return f.readBytes()
    }

    /** A picked file's bytes, at most [SmfParser.MAX_BYTES] and a little more (to tell a bigger file); null when it cannot be opened. */
    private fun readAtMost(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() <= SmfParser.MAX_BYTES) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            out.toByteArray()
        }
    }.getOrNull()

    // ---- My songs (2.8.1) ----

    /** My songs, newest first. A song whose file is missing (a progress import from another phone) gets it back from the list's copy. */
    fun mine(): List<SongMeta> {
        val p = minePrefs
        val list = runCatching { parseMine(p.getString(KEY_MINE, null)) }
            .getOrElse { android.util.Log.d(com.bigpapituna.sharpears.TAG, "my songs: list unreadable, ignored ($it)"); emptyList() }
        for (m in list) {
            val f = File(context.filesDir, m.file)
            if (f.exists()) continue
            val data = p.getString(KEY_BYTES + m.id, null) ?: continue
            runCatching {
                val bytes = java.util.Base64.getDecoder().decode(data)
                check(bytes.size <= MySongs.MAX_BYTES) { "${bytes.size} bytes, bigger than any song" }
                mineDir.mkdirs(); f.writeBytes(bytes)
            }
                .onSuccess { android.util.Log.d(com.bigpapituna.sharpears.TAG, "my songs: ${m.id} file written back from the list") }
                .onFailure { android.util.Log.d(com.bigpapituna.sharpears.TAG, "my songs: ${m.id} not written back ($it)") }
        }
        return list
    }

    /**
     * Reads a picked file for the title dialog: at most [MySongs.MAX_BYTES] (and one byte more, to tell a bigger file),
     * never keeping the URI. Throws [MySongs.Refused] with the kind line. Returns the bytes and the file's name.
     */
    fun readPicked(uri: Uri): Pair<ByteArray, String?> {
        val bytes = readAtMost(uri) ?: throw MySongs.Refused(MySongs.Problem.UNREADABLE)
        if (bytes.size > MySongs.MAX_BYTES) throw MySongs.Refused(MySongs.Problem.TOO_BIG)
        return bytes to runCatching { displayName(uri) }.getOrNull()
    }

    /**
     * Adds [bytes] to My songs as [title] ([suggested]: the title the file itself suggested, which names its id). The same
     * file again replaces its entry and keeps its id, so its stars stay. The list first (with its copy of the bytes), then
     * files/songs-import/<id>.mid.
     */
    fun addMine(bytes: ByteArray, suggested: String, title: String, bothHands: Boolean): SongMeta {
        val id = MySongs.idFor(suggested, bytes)
        val meta = mineMeta(id, title.trim().take(MySongs.TITLE_MAX).ifBlank { suggested }, bothHands)
        val list = listOf(meta) + mine().filter { it.id != id }
        minePrefs.edit(commit = true) { putString(KEY_MINE, mineJson(list)); putString(KEY_BYTES + id, java.util.Base64.getEncoder().encodeToString(bytes)) }
        mineDir.mkdirs()
        File(context.filesDir, meta.file).writeBytes(bytes)
        return meta
    }

    /**
     * Copies a picked MIDI file into files/user/ after checking that it parses. [into] is the index entry it
     * fills (Alicia); without one, a new entry is made from the file name with automatic hands (two tracks
     * split by track, one track split at middle C). Like My songs, a file over [SmfParser.MAX_BYTES] is refused unread.
     */
    fun import(uri: Uri, into: SongMeta? = null): SongMeta {
        val bytes = readAtMost(uri) ?: throw ImportException("Could not open the file.")
        if (bytes.size > SmfParser.MAX_BYTES) throw ImportException(MySongs.Problem.TOO_BIG.line)
        val name = displayName(uri)?.substringBeforeLast('.') ?: "Imported song"
        val meta = into ?: SongMeta(
            id = userSongId(name),
            file = "", title = name, composer = "", difficulty = "imported", hands = HandRule.Auto,
            license = "personal use, not bundled", attribution = "Imported from your device",
        ).let { it.copy(file = "user/${it.id}.mid") }
        validate(bytes, meta)
        File(context.filesDir, meta.file).writeBytes(bytes)
        if (into == null) writeUserIndex(imported().filter { it.id != meta.id } + meta)
        return meta
    }

    /** A new title for an imported song or one of My songs; its id, file and scores stay. */
    fun rename(meta: SongMeta, title: String) {
        if (meta.mine) {
            val list = mine().map { if (it.id == meta.id) it.copy(title = title.trim().take(MySongs.TITLE_MAX)) else it }
            minePrefs.edit { putString(KEY_MINE, mineJson(list)) }
            return
        }
        if (imported().any { it.id == meta.id }) writeUserIndex(imported().map { if (it.id == meta.id) it.copy(title = title) else it })
    }

    /** Removes an imported song's file and entry (My songs: its copy in the list too). Its scores stay, for when it comes back. */
    fun delete(meta: SongMeta) {
        if (meta.mine) {
            // The list first (with its copy of the bytes), then the file: mine() would write a listed song's missing file back.
            val list = mine().filter { it.id != meta.id }
            minePrefs.edit(commit = true) { putString(KEY_MINE, mineJson(list)); remove(KEY_BYTES + meta.id) }
            File(context.filesDir, meta.file).delete()
            return
        }
        File(context.filesDir, meta.file).delete()
        if (imported().any { it.id == meta.id }) writeUserIndex(imported().filter { it.id != meta.id })
    }

    /** Whole or not at all: written beside the index, then renamed over it. */
    private fun writeUserIndex(list: List<SongMeta>) {
        val tmp = File(userDir, "index.json.tmp")
        tmp.writeText(JSONObject().put("songs", JSONArray(list.map(::toJson))).toString(2))
        if (!tmp.renameTo(userIndex)) { tmp.delete(); throw java.io.IOException("Could not save the song list.") }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {
        /** My songs' folder under files/, and its list's preference file (phone-wide; ProgressFiles.NAMES and ProfileFiles.SHARED). */
        const val MINE_DIR = "songs-import"
        const val MINE_FILE = "mysongs"
        private const val KEY_MINE = "songs"
        private const val KEY_BYTES = "mid:"

        fun mineMeta(id: String, title: String, bothHands: Boolean) = SongMeta(
            id = id, file = "$MINE_DIR/$id.mid", title = title, composer = "", difficulty = "mine", hands = HandRule.Auto,
            license = "personal use, not bundled", attribution = "Added from your device", bothHands = bothHands,
        )

        /** My songs' list as stored: [{"id", "title", "both"}], newest first (engine/MiniJson, so it is tested on the JVM). */
        fun mineJson(list: List<SongMeta>): String =
            MiniJson.write(list.map { linkedMapOf("id" to it.id, "title" to it.title, "both" to it.bothHands) })

        /** The stored list back; an entry without an id or title, or whose id could name another path, is skipped. */
        fun parseMine(json: String?): List<SongMeta> {
            if (json.isNullOrEmpty()) return emptyList()
            return (MiniJson.read(json) as? List<*>).orEmpty().mapNotNull { e ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? String)?.takeIf { MINE_ID.matches(it) } ?: return@mapNotNull null
                mineMeta(id, m["title"] as? String ?: return@mapNotNull null, m["both"] == true)
            }
        }

        private val MINE_ID = Regex("[a-z0-9-]{1,48}")

        /**
         * The id of a song imported from a file called [name]: "user-" and its letters and digits. A name with none of
         * them (all Chinese, Arabic, emoji) gets a code from the name, so two such songs never replace each other.
         */
        fun userSongId(name: String): String = "user-" + name.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-")
            .trim('-').ifEmpty { "song-" + Integer.toHexString(name.hashCode()) }

        /** Throws [ImportException] with a readable message when [bytes] is not a usable MIDI file. */
        fun validate(bytes: ByteArray, meta: SongMeta) {
            val song = try { SmfParser.parse(bytes, meta.id, meta.title, meta.hands) }
                catch (e: Exception) { throw ImportException("This is not a MIDI file the app can read (${e.message ?: e.javaClass.simpleName}).") }
            if (song.notes.isEmpty()) throw ImportException("The MIDI file has no notes.")
        }

        fun parseIndex(json: String): List<SongMeta> {
            val songs = JSONObject(json).getJSONArray("songs")
            return List(songs.length()) { i ->
                val s = songs.getJSONObject(i)
                val v = s.optJSONArray("videos") ?: JSONArray()
                SongMeta(
                    id = s.getString("id"), file = s.getString("file"), title = s.getString("title"),
                    composer = s.optString("composer"), difficulty = s.optString("difficulty", "medium"),
                    hands = handRule(s), license = s.optString("license"), attribution = s.optString("attribution"),
                    videos = List(v.length()) { j -> v.getJSONObject(j).let { Video(it.getString("id"), it.optString("title"), it.optString("channel"), it.optString("checkedOn")) } },
                    pairWith = s.optString("pairWith").takeIf { it.isNotEmpty() },
                )
            }
        }

        /** "hands": "auto" | "middleC" | {"right": track, "left": track} */
        private fun handRule(s: JSONObject): HandRule = when (val h = s.opt("hands")) {
            is JSONObject -> HandRule.Tracks(right = h.getInt("right"), left = h.getInt("left"))
            "middleC" -> HandRule.MiddleCSplit
            else -> HandRule.Auto
        }

        private fun toJson(m: SongMeta) = JSONObject().put("id", m.id).put("file", m.file).put("title", m.title)
            .put("composer", m.composer).put("difficulty", m.difficulty).put("hands", "auto")
            .put("license", m.license).put("attribution", m.attribution)
    }
}
