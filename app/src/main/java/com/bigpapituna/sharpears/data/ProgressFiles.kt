package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ProgressBackup

/**
 * Export and import of everything the app remembers: song progress and settings ("progress"), chord mastery
 * ("mastery") and the microphone setup ("mic"), as one JSON file the player picks a place for. Since 2.0 also XP,
 * achievements and goals ("profile"), ear training ("ear"), the practice tools ("practice") and theory ("theory").
 */
object ProgressFiles {
    val NAMES = listOf("progress", "mastery", "mic", "profile", "ear", "practice", "theory")

    fun export(context: Context, uri: Uri) {
        val text = ProgressBackup.encode(NAMES.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all })
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Could not open the file.")
        Log.d(TAG, "progress: exported ${text.length} bytes, " + NAMES.joinToString { "$it=${context.getSharedPreferences(it, Context.MODE_PRIVATE).all.size}" })
    }

    /** Reads a file picked by the player; throws [ProgressBackup.BadBackup] when it is not a progress file. */
    fun read(context: Context, uri: Uri): Map<String, Map<String, Any>> {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw ProgressBackup.BadBackup("Could not open the file.")
        return ProgressBackup.decode(text)
    }

    /** Replaces every settings file with [files] (files missing from it are cleared). */
    fun replace(context: Context, files: Map<String, Map<String, Any>>) {
        for (name in NAMES) {
            val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            files[name]?.forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is String -> e.putString(k, v)
                    is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
                }
            }
            e.commit()
        }
        Log.d(TAG, "progress: imported " + NAMES.joinToString { "$it=${files[it]?.size ?: 0}" })
    }
}
