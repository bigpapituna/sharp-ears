package com.bigpapituna.sharpears.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

/** How often a scale, arpeggio or warm-up has been practised, and the fastest tempo it was opened at. */
data class PracticeStat(val times: Int, val bestBpm: Int)

/**
 * The Practice tab's own preferences (SharedPreferences file "practice"): the settings each tool was last used
 * with, and what has been practised. Stat ids: "scale:<TYPE>:<root>" and "arp:<TYPE>:<root>" (root 0 = C, per key
 * and type, hands and octaves pooled) and "warmup:<id>".
 */
class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("practice", Context.MODE_PRIVATE)

    /** Bumped whenever a stat changes, so screens that read stats recompose. */
    val version = mutableIntStateOf(0)

    fun int(key: String, default: Int) = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
    fun string(key: String, default: String) = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) = prefs.edit { putString(key, value) }
    fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun putBool(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    /** The enum constant stored under [key], or [default] when missing or from an older version. */
    inline fun <reified E : Enum<E>> enum(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == string(key, default.name) } ?: default

    fun putEnum(key: String, value: Enum<*>) = putString(key, value.name)

    // ---- what has been practised ----

    fun stat(id: String): PracticeStat {
        val parts = prefs.getString("stat:$id", null)?.split(",")
        return PracticeStat(parts?.getOrNull(0)?.toIntOrNull() ?: 0, parts?.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** One more session of [id] at [bpm] (0 for none): counts the time, keeps the best tempo. */
    fun record(id: String, bpm: Int = 0) {
        val old = stat(id)
        prefs.edit { putString("stat:$id", "${old.times + 1},${maxOf(old.bestBpm, bpm)}") }
        version.intValue++
    }

    fun recordScale(type: String, root: Int, bpm: Int) = record("scale:$type:$root", bpm)
    fun recordArpeggio(type: String, root: Int, bpm: Int) = record("arp:$type:$root", bpm)
    fun recordWarmup(id: String, bpm: Int) = record("warmup:$id", bpm)

    /** Every recorded id with its stat, for a summary on the You tab. */
    fun allStats(): Map<String, PracticeStat> =
        prefs.all.keys.filter { it.startsWith("stat:") }.associate { it.removePrefix("stat:") to stat(it.removePrefix("stat:")) }

    /** Different scales and arpeggios practised at least once. */
    fun distinctKeysPractised() = allStats().keys.count { it.startsWith("scale:") || it.startsWith("arp:") }

    fun clear() { prefs.edit { clear() }; version.intValue++ }
}
