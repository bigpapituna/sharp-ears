package com.bigpapituna.sharpears.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ProgressBackup
import com.bigpapituna.sharpears.engine.family.ProfileFiles
import com.bigpapituna.sharpears.engine.family.Roster

/**
 * Export and import of everything the app remembers: song progress and settings ("progress"), chord mastery
 * ("mastery") and the microphone setup ("mic"), as one JSON file the player picks a place for. Since 2.0 also XP,
 * achievements and goals ("profile"), ear training ("ear"), the practice tools ("practice") and theory ("theory").
 *
 * Since 2.1 every file but "mic" is per profile: the owner keeps the plain names, everyone else has
 * "<name>.<profileId>" ([fileFor]). A backup carries every profile's files and the roster ("profiles"). 2.1 adds the
 * pup's world ("world": stickers, the jar, the outfit), the play corner ("play": today's quest, Pup says bests) and the
 * kid skin's bedtime dates ("skin"). 2.2 adds the kid home ("kid": each lesson's best stars, the finales played).
 *
 * The parent brief's event logs are files, not preferences (files/events/<profileId>.log, data/EventLog.kt): the backup
 * carries them beside the files ("events", engine/ProgressBackup.kt) and an import puts them back ([replace]); deleting a
 * profile deletes its log (Profiles.delete). [LOGS] names them for the coverage test.
 */
object ProgressFiles {
    val NAMES = listOf("progress", "mastery", "mic", "profile", "ear", "practice", "theory", "world", "play", "skin", "kid")

    /** Per-profile data kept as files under the app's files folder rather than preferences: backed up and deleted too. */
    val LOGS = listOf(EventLog.DIR)

    /** The preference file [name] (one of [NAMES]) opens for [profileId]: "progress" for the owner, "progress.p3" otherwise. */
    fun fileFor(name: String, profileId: String): String = ProfileFiles.fileFor(name, profileId)

    private fun prefs(context: Context, file: String) = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private fun roster(context: Context) = Roster.decode(prefs(context, ProfileFiles.ROSTER).getString(Profiles.KEY_ROSTER, null))

    /** Every file a backup of this phone holds, in the order they are written. */
    fun backupFiles(context: Context): List<String> = ProfileFiles.backupFiles(NAMES, roster(context).profiles.map { it.id })

    fun export(context: Context, uri: Uri) {
        val files = backupFiles(context).associateWith { f ->
            prefs(context, f).all.let { if (f == ProfileFiles.ROSTER) it - Profiles.KEY_GATE else it }   // the gate stays on this phone
        }
        val events = Briefs.exportLogs(context, roster(context).profiles.map { it.id })
        val text = ProgressBackup.encode(files, events)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Could not open the file.")
        Log.d(TAG, "progress: exported ${text.length} bytes, " + files.entries.joinToString { "${it.key}=${it.value.size}" } +
            ", events " + events.entries.joinToString { "${it.key}=${it.value.size}" })
    }

    /** Reads a file picked by the player; throws [ProgressBackup.BadBackup] when it is not a progress file. */
    fun read(context: Context, uri: Uri): ProgressBackup.Backup {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw ProgressBackup.BadBackup("Could not open the file.")
        return ProgressBackup.decodeBackup(text)
    }

    /**
     * Replaces the settings files with [files] (files missing from it are cleared). A backup from 2.1 on carries the
     * roster and replaces every profile; an older one (no roster) is the owner's alone, replaces only the files it holds
     * and leaves the other profiles be.
     * Anything that is not one of our files is ignored. Recreate the activity afterwards so every store reads it.
     * The event logs follow the same rule (EventLog.restore): a backup with the roster replaces them all.
     */
    fun replace(context: Context, backup: ProgressBackup.Backup) {
        val files = backup.files
        val plan = ProfileFiles.importPlan(files.keys, roster(context).profiles.map { it.id }, NAMES)
        for (file in plan.clear) {
            val e = prefs(context, file).edit().clear()
            if (file == ProfileFiles.ROSTER) prefs(context, file).getLong(Profiles.KEY_GATE, 0L).let { e.putLong(Profiles.KEY_GATE, it) }
            files[file]?.takeIf { file in plan.write }?.forEach { (k, v) ->
                if (file == ProfileFiles.ROSTER && k == Profiles.KEY_GATE) return@forEach
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
        // Files of profiles the backup does not know any more are gone with them.
        if (plan.allProfiles) {
            val kept = roster(context).profiles.map { it.id }.toSet()
            plan.clear.filter { f -> ProfileFiles.profileOf(f, NAMES)?.let { it !in kept } == true }.forEach { context.deleteSharedPreferences(it) }
        }
        Briefs.restoreLogs(context, backup.events, roster(context).profiles.map { it.id }, plan.allProfiles)
        Log.d(TAG, "progress: imported " + (if (plan.allProfiles) "all profiles: " else "the owner's files: ") +
            plan.write.sorted().joinToString { "$it=${files[it]?.size ?: 0}" } +
            if (plan.allProfiles) ", events " + (backup.events?.entries?.joinToString { "${it.key}=${it.value.size}" } ?: "none (an older backup: logs cleared)") else "")
    }
}
