package com.bigpapituna.sharpears.data

import android.content.res.AssetManager
import com.bigpapituna.sharpears.engine.HandRule
import com.bigpapituna.sharpears.engine.SmfParser
import com.bigpapituna.sharpears.engine.Song
import org.json.JSONObject

data class SongMeta(
    val id: String,
    val file: String,
    val title: String,
    val composer: String,
    val difficulty: String,
    val hands: HandRule,
    val license: String,
    val attribution: String,
)

/** Bundled songs: assets/songs/index.json plus one .mid per entry. */
class SongRepository(private val assets: AssetManager) {

    val index: List<SongMeta> by lazy {
        val songs = JSONObject(assets.open("songs/index.json").bufferedReader().use { it.readText() }).getJSONArray("songs")
        List(songs.length()) { i ->
            val s = songs.getJSONObject(i)
            SongMeta(
                id = s.getString("id"),
                file = s.getString("file"),
                title = s.getString("title"),
                composer = s.optString("composer"),
                difficulty = s.optString("difficulty", "medium"),
                hands = handRule(s),
                license = s.optString("license"),
                attribution = s.optString("attribution"),
            )
        }
    }

    fun load(id: String): Song {
        val meta = index.first { it.id == id }
        val bytes = assets.open("songs/${meta.file}").use { it.readBytes() }
        return SmfParser.parse(bytes, meta.id, meta.title, meta.hands)
    }

    /** "hands": "auto" | "middleC" | {"right": track, "left": track} */
    private fun handRule(s: JSONObject): HandRule = when (val h = s.opt("hands")) {
        is JSONObject -> HandRule.Tracks(right = h.getInt("right"), left = h.getInt("left"))
        "middleC" -> HandRule.MiddleCSplit
        else -> HandRule.Auto
    }
}
