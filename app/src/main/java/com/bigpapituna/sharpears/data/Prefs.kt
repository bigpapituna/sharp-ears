package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.TAG

/** What the library shows per song. [bestAccuracy] is a Perform-mode score, 0..100, or -1 before the first run. */
data class SongProgress(val lastPositionMs: Long = 0, val completed: Boolean = false, val bestAccuracy: Int = -1,
                        /** The worst bar of the last Wait or Perform run, or null when it was clean. */
                        val troubleBar: Int? = null)

enum class KeyLabels { OFF, C_ONLY, ALL }

/**
 * Progress and settings in one SharedPreferences file (PLAN 5.9). Keys are per song id; no migrations in v1.
 * [version] bumps on every write so screens that read progress recompose.
 */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)

    fun progress(id: String) = SongProgress(p.getLong("pos:$id", 0), p.getBoolean("done:$id", false), p.getInt("best:$id", -1),
        troubleBars(id).firstOrNull())

    /** The hardest bars of the song's last Wait or Perform run, worst first ("trouble:<id>" = "12,13,27"). */
    fun troubleBars(id: String): List<Int> = com.bigpapituna.sharpears.engine.TroubleSpots.decode(p.getString("trouble:$id", null))
    fun saveTroubleBars(id: String, spots: List<com.bigpapituna.sharpears.engine.TroubleSpot>) =
        edit { if (spots.isEmpty()) remove("trouble:$id") else putString("trouble:$id", com.bigpapituna.sharpears.engine.TroubleSpots.encode(spots)) }

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

    /** The chosen text-to-speech voice (engine id), or null for the best available. */
    var voiceName: String?
        get() = p.getString("voiceName", null)
        set(v) = edit { putString("voiceName", v) }

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

    /** Transpose per song, in semitones (-6..6). */
    fun transpose(id: String) = p.getInt("transpose:$id", 0)
    fun saveTranspose(id: String, semitones: Int) = edit { if (semitones == 0) remove("transpose:$id") else putInt("transpose:$id", semitones) }

    /** The daily practice reminder: on or off, and its time in minutes after midnight (default 18:30). */
    var reminderOn: Boolean
        get() = p.getBoolean("reminderOn", false)
        set(v) = edit { putBoolean("reminderOn", v) }
    var reminderMinutes: Int
        get() = p.getInt("reminderMinutes", 18 * 60 + 30)
        set(v) = edit { putInt("reminderMinutes", v) }

    /** Lines across the falling notes at each beat. Off by default; rhythm lessons show them anyway. */
    var beatLines: Boolean
        get() = p.getBoolean("beatLines", false)
        set(v) = edit { putBoolean("beatLines", v) }

    /** Hit-line light for right and wrong notes; off brings back green key flashes and a red keyboard border. */
    var feedbackLight: Boolean
        get() = p.getBoolean("feedbackLight", true)
        set(v) = edit { putBoolean("feedbackLight", v) }

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
    /** Second anchor: video second at which the song's last note sounds. */
    fun lastAnchor(songId: String, videoId: String): Float? = p.getFloat("anchor2:$songId:$videoId", -1f).takeIf { it >= 0 }
    fun saveLastAnchor(songId: String, videoId: String, sec: Float) = edit { putFloat("anchor2:$songId:$videoId", sec) }

    /** Practice time: seconds the transport played, per day ("practice:yyyy-MM-dd"); days past 60 are dropped. */
    fun addPractice(day: java.time.LocalDate, seconds: Int) {
        if (seconds <= 0) return
        val old = com.bigpapituna.sharpears.engine.PracticeStats.stale(practice().keys, day)
        edit {
            putInt("practice:$day", p.getInt("practice:$day", 0) + seconds)
            old.forEach { remove("practice:$it") }
        }
    }
    fun practice(): Map<java.time.LocalDate, Int> = p.all.mapNotNull { (k, v) ->
        if (!k.startsWith("practice:") || v !is Int) null
        else runCatching { java.time.LocalDate.parse(k.removePrefix("practice:")) to v }.getOrNull()
    }.toMap()

    /** Which hands a song has been played through with (wait or Perform runs that reached the end). */
    fun handsDone(id: String): Set<com.bigpapituna.sharpears.engine.Practice> =
        p.getString("hands:$id", "").orEmpty().split(',').mapNotNull { n -> com.bigpapituna.sharpears.engine.Practice.entries.firstOrNull { it.name == n } }.toSet()
    fun addHandDone(id: String, hand: com.bigpapituna.sharpears.engine.Practice) {
        val now = handsDone(id) + hand
        edit { putString("hands:$id", now.joinToString(",") { it.name }) }
    }
    /** The hands-apart tip that was dismissed for a song ("right", "left" or "together"), so it stays away. */
    fun handsTipDismissed(id: String) = p.getString("handsTip:$id", null)
    fun dismissHandsTip(id: String, stage: String) = edit { putString("handsTip:$id", stage) }

    /** The Auto speed of a song ("auto:<id>" = "72,2026-10-07": percent and the day of the last nudge). */
    fun autoSpeed(id: String) = com.bigpapituna.sharpears.engine.AutoSpeed.decode(p.getString("auto:$id", null))
    fun saveAutoSpeed(id: String, a: com.bigpapituna.sharpears.engine.AutoSpeed) = edit { putString("auto:$id", a.encode()) }

    /** Sight-reading: the best run per level (fewest points: seconds plus two per wrong note). */
    fun sightBest(level: Int) = com.bigpapituna.sharpears.engine.SightScore.decode(p.getString("sight:$level", null))
    /** Returns true when [s] beats the saved best. */
    fun saveSightBest(level: Int, s: com.bigpapituna.sharpears.engine.SightScore): Boolean {
        val old = sightBest(level)
        if (old != null && old.points <= s.points) return false
        edit { putString("sight:$level", s.encode()) }
        return true
    }

    /** "Practice today" finished on [day]: the summary row, or null. */
    fun dailySummary(day: java.time.LocalDate): String? = p.getString("daily:$day", null)
    fun saveDailySummary(day: java.time.LocalDate, summary: String) = edit {
        p.all.keys.filter { it.startsWith("daily:") }.forEach(::remove)
        putString("daily:$day", summary)
    }

    fun resetProgress() {
        val keys = p.all.keys.filter { it.startsWith("pos:") || it.startsWith("done:") || it.startsWith("best:") || it.startsWith("trouble:") || it.startsWith("auto:") || it.startsWith("hands:") || it.startsWith("handsTip:") || it == "lastSong" || it.startsWith("practice:") }
        edit { keys.forEach(::remove) }
    }

    fun logRestored() = Log.d(TAG, "prefs restored: " + p.all.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" })

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version.intValue++
    }
}
