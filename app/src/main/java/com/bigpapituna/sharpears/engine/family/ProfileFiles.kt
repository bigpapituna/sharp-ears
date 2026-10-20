package com.bigpapituna.sharpears.engine.family

/**
 * Which preference file holds what for whom. A profile is a suffix on the file names: the owner keeps the plain
 * names ("progress"), so everything from before 2.1 is the owner's; everyone else gets "progress.p3". Files in
 * [SHARED] (the microphone setup) belong to the phone, not to a person.
 */
object ProfileFiles {
    /** Files every profile shares. */
    val SHARED = setOf("mic")
    /** The roster itself (data/Profiles.kt), carried by a backup next to the progress files. */
    const val ROSTER = "profiles"

    /** The preference file for [name] (one of ProgressFiles.NAMES) and [profileId]. */
    fun fileFor(name: String, profileId: String): String =
        if (profileId == Profile.OWNER_ID || name in SHARED) name else "$name.$profileId"

    /** Every file of [profileId] among [names] (the shared ones excluded): what deleting the profile removes. */
    fun filesOf(profileId: String, names: List<String>): List<String> =
        names.filter { it !in SHARED }.map { fileFor(it, profileId) }

    /** What a backup carries: the shared files, each profile's own files, and the roster. */
    fun backupFiles(names: List<String>, profileIds: List<String>): List<String> =
        (names.filter { it in SHARED } + profileIds.flatMap { filesOf(it, names) } + ROSTER).distinct()

    /**
     * The profile a file name belongs to ("progress.p3" to "p3", "progress" to the owner), or null when it is not one
     * of ours (a shared file, the roster, or anything a backup should not write).
     */
    fun profileOf(file: String, names: List<String>): String? {
        if (file in SHARED || file == ROSTER) return null
        if (file in names) return Profile.OWNER_ID
        val name = file.substringBefore('.', "")
        val id = file.substringAfter('.', "")
        return id.takeIf { name in names && name !in SHARED && ID.matches(it) && it != Profile.OWNER_ID }
    }

    /** True for the files an import may write: [backupFiles] of any profile. */
    fun restorable(file: String, names: List<String>): Boolean =
        file == ROSTER || (file in SHARED && file in names) || profileOf(file, names) != null

    /** What an import does: [write] these files from the backup, after clearing [clear] (which includes them). */
    data class ImportPlan(val write: Set<String>, val clear: List<String>, val allProfiles: Boolean)

    /**
     * The import of a backup holding [backup] files onto a phone with [currentIds] profiles. A backup with the roster
     * (2.1 on) replaces every profile: all current files are cleared and the backup's are written. One without it
     * (2.0) is the owner's alone: only the plain files it holds are cleared and written (2.0 wrote every file it had), so
     * other profiles and the owner's files from after 2.0 (the world, the kid's stars) stay as they are. Files that
     * are not ours are never written.
     */
    fun importPlan(backup: Set<String>, currentIds: List<String>, names: List<String>): ImportPlan {
        val all = ROSTER in backup
        val write = backup.filter { if (all) restorable(it, names) else it in names }.toSet()
        val clear = if (all) (backupFiles(names, currentIds) + write).distinct() else names.filter { it in backup }
        return ImportPlan(write, clear, all)
    }

    private val ID = Regex("[A-Za-z0-9_-]{1,32}")
}
