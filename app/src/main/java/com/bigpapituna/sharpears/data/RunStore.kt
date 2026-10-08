package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.RunRecording
import java.io.File

/** The last run of each song, as played: files/runs/<songId>.json (one file per song, overwritten). */
class RunStore(context: Context) {
    private val dir = File(context.filesDir, "runs").apply { mkdirs() }
    private fun file(songId: String) = File(dir, songId.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun save(run: RunRecording) {
        runCatching { file(run.songId).writeText(run.toJson()) }
            .onSuccess { Log.d(TAG, "run saved ${run.songId}: ${run.notes.size} notes") }
            .onFailure { Log.d(TAG, "run save failed ${run.songId}: $it") }
    }

    fun load(songId: String): RunRecording? = file(songId).takeIf { it.exists() }?.let { f ->
        runCatching { RunRecording.fromJson(f.readText()) }.getOrNull()
    }
}
