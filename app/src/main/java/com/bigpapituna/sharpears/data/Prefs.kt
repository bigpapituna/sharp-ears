package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.TAG

/** What the library shows per song. [bestAccuracy] is a Perform-mode score, 0..100, or -1 before the first run. */
data class SongProgress(val lastPositionMs: Long = 0, val completed: Boolean = false, val bestAccuracy: Int = -1)

enum class KeyLabels { OFF, C_ONLY, ALL }

/**
 * Progress and settings in one SharedPreferences file (PLAN 5.9). Keys are per song id; no migrations in v1.
 * [version] bumps on every write so screens that read progress recompose.
 */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)

    fun progress(id: String) = SongProgress(p.getLong("pos:$id", 0), p.getBoolean("done:$id", false), p.getInt("best:$id", -1))

    fun savePosition(id: String, ms: Long) = edit { putLong("pos:$id", ms) }
    fun markCompleted(id: String) = edit { putBoolean("done:$id", true); putLong("pos:$id", 0) }
    /** Returns true when [accuracy] beats the saved best. */
    fun saveScore(id: String, accuracy: Int): Boolean {
        if (accuracy <= progress(id).bestAccuracy) return false
        edit { putInt("best:$id", accuracy) }
        return true
    }

    var keyLabels: KeyLabels
        get() = KeyLabels.entries.getOrElse(p.getInt("keyLabels", KeyLabels.C_ONLY.ordinal)) { KeyLabels.C_ONLY }
        set(v) = edit { putInt("keyLabels", v.ordinal) }
    /** App sound for touch and listen mode. The mic still mutes it unless headphones are on. */
    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = edit { putBoolean("sound", v) }
    /** The mic was on the last time a song was played; songs open with it on again. */
    var micDefault: Boolean
        get() = p.getBoolean("micDefault", false)
        set(v) = edit { putBoolean("micDefault", v) }

    /** Spoken guidance: lesson steps and feedback read aloud. */
    var voice: Boolean
        get() = p.getBoolean("voice", true)
        set(v) = edit { putBoolean("voice", v) }

    /** Note names inside the falling notes. */
    var noteNames: Boolean
        get() = p.getBoolean("noteNames", true)
        set(v) = edit { putBoolean("noteNames", v) }
    /** Suggested finger numbers on the falling notes and the waiting key. */
    var fingerNumbers: Boolean
        get() = p.getBoolean("fingers", true)
        set(v) = edit { putBoolean("fingers", v) }

    /** Learn course: lessons finished (exercise played through, or marked done). */
    fun lessonDone(id: String) = p.getBoolean("lesson:$id", false)
    fun markLessonDone(id: String) = edit { putBoolean("lesson:$id", true) }

    /** Chord games: best score (out of 10) per game and level, or -1 before the first full round. */
    fun gameBest(game: String, level: Int) = p.getInt("game:$game:$level", -1)
    /** Returns true when [score] beats the saved best. */
    fun saveGameBest(game: String, level: Int, score: Int): Boolean {
        if (score <= gameBest(game, level)) return false
        edit { putInt("game:$game:$level", score) }
        return true
    }

    /** The song opened most recently; the library offers to continue it. */
    var lastSongId: String?
        get() = p.getString("lastSong", null)
        set(v) = edit { putString("lastSong", v) }

    /** Tutorial alignment: video second at which the song's first note sounds, per song and video. */
    fun anchor(songId: String, videoId: String): Float? = p.getFloat("anchor:$songId:$videoId", -1f).takeIf { it >= 0 }
    fun saveAnchor(songId: String, videoId: String, sec: Float) = edit { putFloat("anchor:$songId:$videoId", sec) }

    fun resetProgress() {
        val keys = p.all.keys.filter { it.startsWith("pos:") || it.startsWith("done:") || it.startsWith("best:") || it == "lastSong" }
        edit { keys.forEach(::remove) }
    }

    fun logRestored() = Log.d(TAG, "prefs restored: " + p.all.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" })

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version.intValue++
    }
}
