package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.MiniJson

/** A grown-up (the full app) or a kid (the kid skin: colours, bites, stars, the gate in front of settings). */
enum class ProfileKind { ADULT, KID }

/**
 * One person on the phone (docs/KIDS-PLAN.md section 2). Progress lives in the per-profile preference files
 * ([ProfileFiles.fileFor]); this is who they are and how the app should treat them.
 *
 * [avatar] is a sweatband colour index, 0 until [AVATARS] (red, blue, green, purple, yellow, pink, orange, teal).
 * [reader] matters for kids only: readers see letters on the colour notes, pre-readers see animals.
 * [dailyGoalMin] seeds the daily goal when the profile first opens (ProgressStore.goalMinutes is the live value).
 * [hintSeconds] are the three steps of hint escalation (glow, the hand points, the app plays it).
 * [micOffsetDb] makes the microphone that much easier to trigger for this person (kids strike lighter).
 * [level] is how much piano they have played already (engine/family/Placement.kt): where the course starts for them. A
 * profile made before 2.6 has none and reads as [Level.NEVER], so nothing changes for it.
 */
data class Profile(
    val id: String,
    val name: String,
    val avatar: Int,
    val kind: ProfileKind,
    val reader: Boolean,
    val dailyGoalMin: Int,
    val hintSeconds: List<Int>,
    val colourNotes: Boolean,
    val micOffsetDb: Double,
    val createdAt: Long,
    val level: Level = Level.NEVER,
) {
    val isKid: Boolean get() = kind == ProfileKind.KID
    val isOwner: Boolean get() = id == OWNER_ID

    /**
     * The course unit this person starts on: the Playground for kids, Basics for grown-ups. Only the id; the
     * Playground team adds the unit with this id to engine/Course.kt.
     */
    val firstUnitId: String get() = firstUnitId(kind)

    /**
     * The same person as [newKind], with that kind's defaults for goal, hints, colour notes and mic offset. The piano level
     * starts over at [Level.NEVER]: what "has had lessons" meant for a kid's pages means nothing on a grown-up's units
     * (Family > Piano level sets it again).
     */
    fun withKind(newKind: ProfileKind): Profile = if (newKind == kind) this else {
        val d = defaults(newKind, reader)
        copy(kind = newKind, dailyGoalMin = d.dailyGoalMin, hintSeconds = d.hintSeconds, colourNotes = d.colourNotes, micOffsetDb = d.micOffsetDb, level = Level.NEVER)
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "name" to name, "avatar" to avatar, "kind" to kind.name, "reader" to reader,
        "dailyGoalMin" to dailyGoalMin, "hintSeconds" to hintSeconds, "colourNotes" to colourNotes,
        "micOffsetDb" to micOffsetDb, "createdAt" to createdAt, "level" to level.name,
    )

    fun encode(): String = MiniJson.write(toMap())

    companion object {
        /** The phone owner's profile: keeps the unsuffixed preference files, so progress from before 2.1 is theirs. */
        const val OWNER_ID = "owner"
        const val OWNER_NAME = "Roy"
        /** Sweatband colours to choose from. */
        const val AVATARS = 8
        /** Longest name kept (longer names are cut). */
        const val MAX_NAME = 20

        const val KID_GOAL_PRE_READER = 5
        const val KID_GOAL_READER = 8
        const val ADULT_GOAL = 10
        val KID_HINTS = listOf(4, 8, 12)
        val ADULT_HINTS = listOf(6, 12, 18)
        /** Kids strike lighter: the mic is this much easier for them (plan section 6). */
        const val KID_MIC_OFFSET_DB = 3.0

        /** The colour names of the avatars, for TalkBack ("Mia, the blue pup"). */
        val AVATAR_NAMES = listOf("red", "blue", "green", "purple", "yellow", "pink", "orange", "teal")

        fun firstUnitId(kind: ProfileKind) = if (kind == ProfileKind.KID) "playground" else "basics"

        /** A new kid: goal 5 minutes before they read, 8 after; hints at 4, 8 and 12 s; colour notes on; mic +3 dB. */
        fun kid(name: String, avatar: Int, reader: Boolean, id: String = "", createdAt: Long = 0L): Profile =
            defaults(ProfileKind.KID, reader).copy(id = id, name = cleanName(name), avatar = avatar.coerceIn(0, AVATARS - 1), createdAt = createdAt)

        /** A new grown-up: goal 10 minutes, hints at 6, 12 and 18 s, colour notes off, no mic offset. */
        fun adult(name: String, avatar: Int, id: String = "", createdAt: Long = 0L): Profile =
            defaults(ProfileKind.ADULT, true).copy(id = id, name = cleanName(name), avatar = avatar.coerceIn(0, AVATARS - 1), createdAt = createdAt)

        /** The owner, made when the phone has no profiles yet (everyone who used the app before 2.1). */
        fun owner(createdAt: Long = 0L) = adult(OWNER_NAME, 0, OWNER_ID, createdAt)

        private fun defaults(kind: ProfileKind, reader: Boolean) = when (kind) {
            ProfileKind.KID -> Profile("", "", 0, kind, reader, if (reader) KID_GOAL_READER else KID_GOAL_PRE_READER,
                KID_HINTS, colourNotes = true, micOffsetDb = KID_MIC_OFFSET_DB, createdAt = 0L)
            ProfileKind.ADULT -> Profile("", "", 0, kind, true, ADULT_GOAL, ADULT_HINTS, colourNotes = false, micOffsetDb = 0.0, createdAt = 0L)
        }

        /** Trimmed, inner runs of spaces made one, at most [MAX_NAME] characters (never half an emoji). */
        fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }.trim()

        /** What an id may look like: it ends up in file names ("progress.p3"). */
        private val ID = Regex("[A-Za-z0-9_-]{1,32}")

        /** Reads what [toMap] wrote; fields missing from an older file take the kind's defaults. Null without a usable id. */
        fun fromMap(m: Map<*, *>): Profile? {
            val id = (m["id"] as? String)?.takeIf { ID.matches(it) } ?: return null
            val kind = ProfileKind.entries.firstOrNull { it.name == m["kind"] } ?: ProfileKind.ADULT
            val reader = m["reader"] as? Boolean ?: true
            val d = defaults(kind, reader)
            val hints = (m["hintSeconds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.size == 3 } ?: d.hintSeconds
            return d.copy(
                id = id,
                reader = reader,
                name = (m["name"] as? String)?.let(::cleanName)?.takeIf { it.isNotEmpty() } ?: if (id == OWNER_ID) OWNER_NAME else "Player",
                avatar = ((m["avatar"] as? Number)?.toInt() ?: 0).coerceIn(0, AVATARS - 1),
                dailyGoalMin = (m["dailyGoalMin"] as? Number)?.toInt() ?: d.dailyGoalMin,
                hintSeconds = hints,
                colourNotes = m["colourNotes"] as? Boolean ?: d.colourNotes,
                micOffsetDb = (m["micOffsetDb"] as? Number)?.toDouble() ?: d.micOffsetDb,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                level = Level.parse(m["level"] as? String),
            )
        }

        fun decode(text: String): Profile? = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull()?.let(::fromMap)
    }
}
