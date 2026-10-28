package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import java.io.File

/**
 * Where a debug build keeps what the microphone recorded (security review 2026-10-22, finding 5): the app's internal
 * storage, which no other app can read. files/recordings/fixtures holds the last four practice sessions (and Microphone
 * setup's test fixtures), files/recordings/stalls the stuck moments, flags, crash reports and weekly summaries the
 * [Reporter] sends. Neither is in Android's backup (res/xml/backup_rules.xml, data_extraction_rules.xml). Sharing goes
 * through a zip in cache/share (StuckShare), so the FileProvider never serves these folders.
 *
 * Up to 2.10.0 both lived in external app storage (Android/data/<package>/files/fixtures and /stalls), which apps with the
 * storage permission can read on Android 9 and 10. [migrate] moves them in once.
 */
object Recordings {
    const val DIR = "recordings"
    const val FIXTURES = "fixtures"
    const val STALLS = "stalls"

    /**
     * What the privacy page and Settings > Microphone say on a build that can keep recordings (Roy keeps them on by default
     * during the beta, to tune the listening).
     */
    const val KEEP_LINE = "This test build keeps the last four practice recordings on this phone, in the app's own storage, to tune the " +
        "listening. They never leave the phone unless you share them. Turn this off in Settings, Sound."
    /** The switch, in Settings > Microphone and in Microphone setup. */
    const val KEEP_SWITCH = "Keep the last four recordings"

    fun fixtures(context: Context): File = File(File(context.applicationContext.filesDir, DIR), FIXTURES)
    fun stalls(context: Context): File = File(File(context.applicationContext.filesDir, DIR), STALLS)

    private const val MOVED = "recordingsMoved"

    /**
     * Once per install: the files of the old external folders move into [fixtures] and [stalls] (each folder removed when
     * empty). Marked done only when every file moved, so a kill or a full disk tries again at the next start. Off the main
     * thread: the stalls can hold 250 MB (Reporter runs it on its own thread before anything it sends).
     */
    fun migrate(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("mic", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MOVED, false)) return
        val external = runCatching { app.getExternalFilesDir(null) }.getOrNull()
        val moved = move(external?.let { File(it, FIXTURES) }, fixtures(app)) + move(external?.let { File(it, STALLS) }, stalls(app))
        prefs.edit { putBoolean(MOVED, true) }
        Log.d(TAG, "recordings: $moved file(s) moved to the app's own storage")
    }

    /**
     * Moves every file of [from] into [to] (made when needed), keeping each one's name and time: a rename when both are on the
     * same disk, else a copy under a temporary name, renamed into place, then the original deleted. A file [to] already has
     * stays; the old copy goes. [from] is removed once empty. Returns how many moved. Plain java.io, tested on the JVM.
     */
    fun move(from: File?, to: File): Int {
        val files = from?.listFiles()?.filter { it.isFile }.orEmpty()
        var n = 0
        if (files.isNotEmpty()) to.mkdirs()
        for (f in files) {
            val dest = File(to, f.name)
            if (dest.exists()) { f.delete(); continue }
            val time = f.lastModified()
            if (!f.renameTo(dest)) {
                val part = File(to, f.name + ".part")
                f.copyTo(part, overwrite = true)
                if (!part.renameTo(dest)) { part.delete(); throw java.io.IOException("could not move ${f.name}") }
                f.delete()
            }
            dest.setLastModified(time)
            n++
        }
        if (from != null && from.isDirectory && from.listFiles().isNullOrEmpty()) from.delete()
        return n
    }
}
