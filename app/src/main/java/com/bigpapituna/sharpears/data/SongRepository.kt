package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.SmfParser
import com.bigpapituna.sharpears.engine.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A curated tutorial video (PLAN 6.3), checked embeddable through YouTube's oEmbed endpoint on [checkedOn]. */
data class Video(val id: String, val title: String, val channel: String, val checkedOn: String)

data class SongMeta(
    val id: String,
    /** Asset path under songs/, or "user/<name>.mid" for a file imported into the app's storage. */
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
) {
    /** This one is the easy arrangement of its pair. */
    val easy get() = id.endsWith("-easy")
    val user get() = file.startsWith("user/")
}

class ImportException(message: String) : Exception(message)

/**
 * Bundled songs (assets/songs/index.json) plus imported ones (files/user/index.json). The bundled index may
 * name a user file (Alicia): it shows in the library as "import to unlock" until that file exists.
 */
class SongRepository(private val context: Context) {
    private val userDir = File(context.filesDir, "user").apply { mkdirs() }
    private val userIndex = File(userDir, "index.json")

    val bundled: List<SongMeta> by lazy {
        parseIndex(context.assets.open("songs/index.json").bufferedReader().use { it.readText() })
    }

    fun imported(): List<SongMeta> = if (userIndex.exists()) parseIndex(userIndex.readText()) else emptyList()

    fun all() = bundled + imported().filter { u -> bundled.none { it.id == u.id } }

    fun available(meta: SongMeta) = !meta.user || File(context.filesDir, meta.file).exists()

    fun load(meta: SongMeta): Song {
        val bytes = if (meta.user) File(context.filesDir, meta.file).readBytes()
                    else context.assets.open("songs/${meta.file}").use { it.readBytes() }
        return SmfParser.parse(bytes, meta.id, meta.title, meta.hands)
    }

    /**
     * Copies a picked MIDI file into files/user/ after checking that it parses. [into] is the index entry it
     * fills (Alicia); without one, a new entry is made from the file name with automatic hands (two tracks
     * split by track, one track split at middle C).
     */
    fun import(uri: Uri, into: SongMeta? = null): SongMeta {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ImportException("Could not open the file.")
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

    fun delete(meta: SongMeta) {
        File(context.filesDir, meta.file).delete()
        if (imported().any { it.id == meta.id }) writeUserIndex(imported().filter { it.id != meta.id })
    }

    private fun writeUserIndex(list: List<SongMeta>) =
        userIndex.writeText(JSONObject().put("songs", JSONArray(list.map(::toJson))).toString(2))

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    companion object {
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
