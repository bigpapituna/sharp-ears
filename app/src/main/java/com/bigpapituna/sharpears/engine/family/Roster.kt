package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.MiniJson

/**
 * Everyone on the phone and who is playing now: the rules of data/Profiles.kt without Android, so they are tested on
 * the JVM. Immutable; every change returns a new roster (or the same one when the change is not allowed).
 *
 * Rules: at most [MAX] profiles; never empty (an empty roster is the owner alone); the owner ([Profile.OWNER_ID]) cannot
 * be deleted and stays a grown-up; deleting the current profile makes the first remaining one current.
 */
data class Roster(val profiles: List<Profile>, val currentId: String) {
    init { require(profiles.isNotEmpty()) { "a roster always has someone" } }

    val current: Profile get() = profiles.firstOrNull { it.id == currentId } ?: profiles.first()
    val isFull: Boolean get() = profiles.size >= MAX
    /** The picker shows on launch only when there is someone to pick between. */
    val needsPicker: Boolean get() = profiles.size >= 2

    operator fun get(id: String): Profile? = profiles.firstOrNull { it.id == id }

    /** The next free id: "p1", "p2", ... one above the highest used, so a deleted profile's id is not handed out again soon. */
    fun nextId(): String = "p" + ((profiles.mapNotNull { p -> p.id.takeIf { it.startsWith("p") }?.drop(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1)

    /**
     * Adds [profile] with a fresh id (its own id is ignored), current when [switch]. Returns null when the roster is
     * full or the name is blank. The new profile is the last one in [profiles].
     */
    fun add(profile: Profile, switch: Boolean = true): Roster? {
        if (isFull) return null
        val name = Profile.cleanName(profile.name).takeIf { it.isNotEmpty() } ?: return null
        val added = profile.copy(id = nextId(), name = name)
        return Roster(profiles + added, if (switch) added.id else currentId)
    }

    fun rename(id: String, name: String): Roster {
        val clean = Profile.cleanName(name).takeIf { it.isNotEmpty() } ?: return this
        return update(id) { it.copy(name = clean) }
    }

    /** Replaces the profile with [id] by [change] applied to it (the id never changes). The owner stays a grown-up. */
    fun update(id: String, change: (Profile) -> Profile): Roster =
        copy(profiles = profiles.map { p ->
            if (p.id != id) p else change(p).copy(id = p.id).let { if (p.isOwner && it.isKid) it.withKind(ProfileKind.ADULT) else it }
        })

    /** Switches a kid to grown-up mode (or back): the kind's defaults for goal, hints, colour notes and mic offset. */
    fun setKind(id: String, kind: ProfileKind): Roster = update(id) { it.withKind(kind) }

    /** Removes [id]; the owner and unknown ids are refused (the same roster comes back). */
    fun delete(id: String): Roster {
        if (id == Profile.OWNER_ID || this[id] == null) return this
        val left = profiles.filter { it.id != id }
        return Roster(left, if (currentId == id) left.first().id else currentId)
    }

    fun switchTo(id: String): Roster = if (this[id] == null) this else copy(currentId = id)

    fun encode(): String = MiniJson.write(linkedMapOf("current" to currentId, "profiles" to profiles.map { it.toMap() }))

    companion object {
        const val MAX = 8

        /** The roster of a phone that has never had profiles: the owner alone. */
        fun fresh(now: Long = 0L) = Roster(listOf(Profile.owner(now)), Profile.OWNER_ID)

        /**
         * Reads what [encode] wrote. Nothing (or nothing readable) gives [fresh]. Profiles without an id and repeated ids
         * are dropped, at most [MAX] are kept, the owner is always there (first), and an unknown current id falls back to the first.
         */
        fun decode(text: String?, now: Long = 0L): Roster {
            val root = text?.let { runCatching { MiniJson.read(it) as? Map<*, *> }.getOrNull() } ?: return fresh(now)
            val read = (root["profiles"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(Profile::fromMap) }.distinctBy { it.id }
            val owner = read.firstOrNull { it.isOwner }?.withKind(ProfileKind.ADULT) ?: Profile.owner(now)
            val list = listOf(owner) + read.filter { !it.isOwner }.take(MAX - 1)      // the owner first, always
            val current = (root["current"] as? String)?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
            return Roster(list, current)
        }
    }
}
