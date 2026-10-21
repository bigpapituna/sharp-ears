package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileFiles
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.family.Roster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everyone on the phone (docs/KIDS-PLAN.md section 2), in its own SharedPreferences file "profiles": the roster as one
 * JSON string ("roster", engine/family/Roster.kt holds the rules) and when the parent gate was last passed ("gateAt").
 *
 * [current] is never null: a phone that never had profiles gets the owner's ("Roy", id "owner"), who keeps the
 * unsuffixed progress files. [currentFlow] and [rosterFlow] change on every write so the UI follows a switch. Deleting
 * a profile deletes its progress files; the UI asks twice first, the store just deletes.
 */
class Profiles(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the "profiles:" lines go (logcat; JVM tests pass their own). */
    private val log: (String) -> Unit = { Log.d(TAG, it) },
) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences(ProfileFiles.ROSTER, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    private val currentState = MutableStateFlow(state.value.current)

    /** Everyone, the owner first, and who is playing. */
    val rosterFlow: StateFlow<Roster> = state.asStateFlow()
    /** The profile playing now; a new value on a switch and when the current profile is edited. */
    val currentFlow: StateFlow<Profile> = currentState.asStateFlow()

    val roster: Roster get() = state.value
    val all: List<Profile> get() = state.value.profiles
    val current: Profile get() = currentState.value
    val isFull: Boolean get() = state.value.isFull

    private fun load(): Roster {
        val text = p.getString(KEY_ROSTER, null)
        val r = Roster.decode(text, clock())
        if (text == null) p.edit().putString(KEY_ROSTER, r.encode()).apply()      // first start of 2.1: the owner is saved
        return r
    }

    private fun save(r: Roster, why: String) {
        if (r == state.value) return
        p.edit().putString(KEY_ROSTER, r.encode()).apply()
        log("profiles: $why; ${r.profiles.size} on the phone, current ${r.currentId} (${r.current.kind})")
        state.value = r
        currentState.value = r.current                 // last: a switch reopens the app as the new profile right away
    }

    /**
     * Adds [profile] (made with [Profile.kid] or [Profile.adult]; its id is assigned here), and switches to it when
     * [switch]. Returns the stored profile, or null when there are already [Roster.MAX] or the name is blank.
     */
    fun create(profile: Profile, switch: Boolean = true): Profile? {
        val r = state.value.add(profile.copy(createdAt = profile.createdAt.takeIf { it > 0 } ?: clock()), switch) ?: return null
        val added = r.profiles.last()
        save(r, "added ${added.id} (${added.kind}, reader=${added.reader})")
        return added
    }

    fun rename(id: String, name: String) = save(state.value.rename(id, name), "renamed $id")

    /** Changes anything but the id (avatar, reader, hint speed, colour notes, mic offset). */
    fun update(id: String, change: (Profile) -> Profile) = save(state.value.update(id, change), "updated $id")

    /** Grown-up mode for a kid (or back): the kind's defaults for goal, hints, colour notes and mic offset. */
    fun setKind(id: String, kind: ProfileKind) = save(state.value.setKind(id, kind), "$id is now $kind")

    /** Makes [id] the current profile; unknown ids are ignored. */
    fun switchTo(id: String) = save(state.value.switchTo(id), "switched to $id")

    /**
     * Deletes [id], its progress files and its event log. The owner cannot be deleted; deleting the current profile switches to the
     * first remaining one (the owner). Returns false when nothing was deleted.
     */
    fun delete(id: String): Boolean {
        val r = state.value.delete(id)
        if (r == state.value) return false
        save(r, "deleted $id")
        ProfileFiles.filesOf(id, ProgressFiles.NAMES).forEach { app.deleteSharedPreferences(it) }
        Briefs.forget(app, id)                          // and the brief's event log (files/events/<id>.log)
        return true
    }

    /** When the parent gate was last passed (epoch ms, 0 never). Kept here so it survives the app being closed. */
    var gatePassedAt: Long
        get() = p.getLong(KEY_GATE, 0L)
        set(v) = p.edit().putLong(KEY_GATE, v).apply()

    companion object {
        const val KEY_ROSTER = "roster"
        const val KEY_GATE = "gateAt"
    }
}
