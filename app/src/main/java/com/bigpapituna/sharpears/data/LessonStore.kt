package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Curriculum
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
import com.bigpapituna.sharpears.engine.Mastery
import com.bigpapituna.sharpears.engine.after
import com.bigpapituna.sharpears.engine.decodeMastery
import com.bigpapituna.sharpears.engine.parseChord
import org.json.JSONObject

/**
 * Lesson packs from assets/lessons plus the song-derived packs, and mastery per item in SharedPreferences
 * (key = item id, value = "box,dueAt"). The current practice session lives here so it survives a trip
 * to the player for a progression item. Mastery is per profile ("mastery.<id>", [ProgressFiles.fileFor]).
 */
class LessonStore(private val context: Context, profileId: String) {
    private val prefs = context.getSharedPreferences(ProgressFiles.fileFor("mastery", profileId), Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    /** Pack descriptions by id, shown on the Lessons tab. */
    val about = mutableMapOf<String, String>()

    val authored: List<LessonPack> by lazy {
        context.assets.list("lessons").orEmpty().filter { it.endsWith(".json") }.sorted().map { f ->
            val o = JSONObject(context.assets.open("lessons/$f").bufferedReader().use { it.readText() })
            val items = o.getJSONArray("items")
            about[o.getString("id")] = o.optString("about")
            LessonPack(
                o.getString("id"), o.getString("title"), o.getInt("level"),
                o.optJSONArray("requires")?.let { r -> List(r.length()) { r.getString(it) } }.orEmpty(),
                List(items.length()) { i ->
                    val it = items.getJSONObject(i)
                    when (it.getString("type")) {
                        "chord" -> LessonItem.ChordItem(parseChord(it.getString("chord")) ?: error("$f: unknown chord ${it.getString("chord")}"))
                        "progression" -> LessonItem.ProgressionItem(it.getInt("progression"), it.getInt("key"))
                        else -> error("$f: unknown item type")
                    }
                },
            )
        }
    }

    /** Song-derived packs, filled in once the library's songs have been labeled (off the main thread). */
    val songPacks = mutableStateOf<List<LessonPack>>(emptyList())
    /** Chord changes per library song id, from the same bar labels; the player shows them on the falling notes. */
    val songChords = mutableStateOf<Map<String, com.bigpapituna.sharpears.engine.ChordTrack>>(emptyMap())

    fun curriculum() = Curriculum(authored + songPacks.value)

    fun mastery(): Map<String, Mastery> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let(::decodeMastery)?.let { k to it }
    }.toMap()

    fun record(item: LessonItem, correct: Boolean, now: Long = System.currentTimeMillis()) {
        val m = (mastery()[item.id] ?: Mastery()).after(correct, now)
        prefs.edit { putString(item.id, "${m.box},${m.dueAt}") }
        Log.d(TAG, "lessons: ${item.id} correct=$correct -> box ${m.box}, due in ${(m.dueAt - now) / 3_600_000} h")
        version.intValue++
    }

    /**
     * Debug (`--es known C,F,G,Am` with `--es screen popsongs`): those chords as played cleanly [box] times running, due in
     * four days, so songs by chords has songs "you can play" and "one new chord away" to show.
     */
    fun debugKnow(names: List<String>, box: Int, now: Long = System.currentTimeMillis()) {
        prefs.edit { names.forEach { putString("chord:$it", "$box,${now + 4 * 86_400_000L}") } }
        Log.d(TAG, "lessons: debug known ${names.joinToString()} at box $box")
        version.intValue++
    }

    // The running session: a fixed queue and how far through it the player is.
    var session by mutableStateOf<List<LessonItem>>(emptyList())
    var sessionIndex by mutableIntStateOf(0)
    var sessionCorrect by mutableIntStateOf(0)

    fun startSession(items: List<LessonItem>) { session = items; sessionIndex = 0; sessionCorrect = 0; Log.d(TAG, "lessons: session ${items.map { it.id }}") }
    fun answer(correct: Boolean) {
        val item = session.getOrNull(sessionIndex) ?: return
        record(item, correct)
        if (correct) sessionCorrect++
        sessionIndex++
    }
    fun endSession() { session = emptyList(); sessionIndex = 0 }
}

