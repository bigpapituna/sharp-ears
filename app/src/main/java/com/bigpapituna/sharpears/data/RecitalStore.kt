package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.playground.Recital

/**
 * The three songs a kid picked for their recital (2.2, the concert hall's "Pick your three"; the recital plays them).
 * Kept in the profile's "play" file (PlayStore's, already in ProgressFiles.NAMES, so backup and profile deletion cover
 * it) under its own key. The rules are engine/playground Recital.
 */
class RecitalStore(context: Context, val profileId: String) {
    private val prefs = context.getSharedPreferences(ProgressFiles.fileFor(PlayStore.FILE, profileId), Context.MODE_PRIVATE)

    /** The picked songs' ids, in order; empty when none were picked yet. */
    fun load(): List<String> = Recital.parse(prefs.getString(KEY, null))

    fun save(ids: List<String>) {
        prefs.edit { putString(KEY, Recital.format(ids)) }
        Log.d(TAG, "playground: recital for $profileId is ${ids.joinToString()}")
    }

    private companion object { const val KEY = "recital" }
}
