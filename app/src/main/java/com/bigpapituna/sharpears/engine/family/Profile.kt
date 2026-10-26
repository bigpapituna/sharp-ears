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
 * [band] (2.14, engine/family/Ages.kt) is a kid's age band: little (the pup's world) or big (the tabs). A kid made before
 * 2.14 has none and reads as [AgeBand.LITTLE]; a grown-up's is never read. [isKid] stays true for both bands: the gate, the
 * brief, bedtime and the mic offset treat a big kid as a kid; [audience] says which world they see.
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
    /**
     * 2.14: whether [name] is a name this person gave. The phone owner starts without one ([UNSET_NAME]) and is asked in the
     * first-run flow, or once on the next launch for a phone from before 2.14; every greeting reads [greetName], so an
     * owner who gave none is greeted "Hi there", never by a name nobody typed.
     */
    val named: Boolean = true,
    val band: AgeBand = AgeBand.LITTLE,
) {
    val isKid: Boolean get() = kind == ProfileKind.KID
    val isOwner: Boolean get() = id == OWNER_ID
    /** The name to greet them by, or null when they gave none. */
    val greetName: String? get() = name.takeIf { named && it.isNotBlank() }

    /** 2.14: a little kid, a big kid or a grown-up ([Ages] routes each screen by it). */
    val audience: Audience get() = Audience.of(kind, band)
    /** The pup's world is their home (a little kid): the map, the sticker trail, the kid songs, no tabs. */
    val mapHome: Boolean get() = Ages.mapHome(audience)
    /** The kid skin in lessons, the player and the finish screens (a little kid). */
    val kidSkin: Boolean get() = Ages.kidSkin(audience)
    /** A big kid (8 to 13): the tabs in Simple, still a kid for the gate. */
    val bigKid: Boolean get() = audience == Audience.BIG_KID

    /**
     * The course unit this person starts on: the Playground for a little kid, Basics for a big kid and a grown-up. Only the
     * id; the Playground team adds the unit with this id to engine/Course.kt.
     */
    val firstUnitId: String get() = Ages.firstUnitId(audience)

    /**
     * The same person as [newKind], with that kind's defaults for goal, hints, colour notes and mic offset. The piano level
     * starts over at [Level.NEVER]: what "has had lessons" meant for a kid's pages means nothing on a grown-up's units
     * (Family > Piano level sets it again).
     */
    fun withKind(newKind: ProfileKind): Profile = if (newKind == kind) this else {
        val d = defaults(newKind, reader, band)
        copy(kind = newKind, dailyGoalMin = d.dailyGoalMin, hintSeconds = d.hintSeconds, colourNotes = d.colourNotes, micOffsetDb = d.micOffsetDb, level = Level.NEVER)
    }

    /**
     * The same kid in [newBand] (2.14, Family > the kid > Little or Big kid): the band's colour notes and hints; the piano
     * level stays (Placement re-plans it on the band's own course).
     */
    fun withBand(newBand: AgeBand): Profile = if (newBand == band) this else {
        val d = defaults(kind, reader, newBand)
        copy(band = newBand, hintSeconds = d.hintSeconds, colourNotes = d.colourNotes)
    }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id, "name" to name, "avatar" to avatar, "kind" to kind.name, "reader" to reader,
        "dailyGoalMin" to dailyGoalMin, "hintSeconds" to hintSeconds, "colourNotes" to colourNotes,
        "micOffsetDb" to micOffsetDb, "createdAt" to createdAt, "level" to level.name, "named" to named, "band" to band.name,
    )

    fun encode(): String = MiniJson.write(toMap())

    companion object {
        /** The phone owner's profile: keeps the unsuffixed preference files, so progress from before 2.1 is theirs. */
        const val OWNER_ID = "owner"
        /** What the phone owner is called until they give a name (2.14). Before it, every fresh owner was "Roy", the app's first user. */
        const val UNSET_NAME = "Grown-up"
        /** The name every owner of a phone from before 2.14 carries, whether they chose it or never were asked. */
        const val LEGACY_OWNER_NAME = "Roy"
        /** Sweatband colours to choose from. */
        const val AVATARS = 8
        /** Longest name kept (longer names are cut). */
        const val MAX_NAME = 20

        const val KID_GOAL_PRE_READER = 5
        const val KID_GOAL_READER = 8
        /** A big kid's goal (2.14): a grown-up's ten minutes. */
        const val KID_GOAL_BIG = 10
        const val ADULT_GOAL = 10
        val KID_HINTS = listOf(4, 8, 12)
        val ADULT_HINTS = listOf(6, 12, 18)
        /** Kids strike lighter: the mic is this much easier for them (plan section 6). */
        const val KID_MIC_OFFSET_DB = 3.0

        /** The colour names of the avatars, for TalkBack ("Mia, the blue pup"). */
        val AVATAR_NAMES = listOf("red", "blue", "green", "purple", "yellow", "pink", "orange", "teal")

        fun firstUnitId(kind: ProfileKind) = if (kind == ProfileKind.KID) "playground" else "basics"

        /**
         * A new kid: goal 5 minutes before they read, 8 after; hints at 4, 8 and 12 s; colour notes on; mic +3 dB. A big kid
         * (2.14): goal 10 minutes, a grown-up's hints, colour notes off (the letters on the keys read better at 8 to 13), mic +3 dB.
         */
        fun kid(name: String, avatar: Int, reader: Boolean, id: String = "", createdAt: Long = 0L, band: AgeBand = AgeBand.LITTLE): Profile =
            defaults(ProfileKind.KID, reader, band).copy(id = id, name = cleanName(name), avatar = avatar.coerceIn(0, AVATARS - 1), createdAt = createdAt)

        /** A new grown-up: goal 10 minutes, hints at 6, 12 and 18 s, colour notes off, no mic offset. */
        fun adult(name: String, avatar: Int, id: String = "", createdAt: Long = 0L): Profile =
            defaults(ProfileKind.ADULT, true).copy(id = id, name = cleanName(name), avatar = avatar.coerceIn(0, AVATARS - 1), createdAt = createdAt)

        /** The owner, made when the phone has no profiles yet: without a name until they give one (2.14). */
        fun owner(createdAt: Long = 0L) = adult(UNSET_NAME, 0, OWNER_ID, createdAt).copy(named = false)

        private fun defaults(kind: ProfileKind, reader: Boolean, band: AgeBand = AgeBand.LITTLE) = when {
            kind == ProfileKind.KID && band == AgeBand.BIG -> Profile("", "", 0, kind, reader, KID_GOAL_BIG,
                ADULT_HINTS, colourNotes = false, micOffsetDb = KID_MIC_OFFSET_DB, createdAt = 0L, band = band)
            kind == ProfileKind.KID -> Profile("", "", 0, kind, reader, if (reader) KID_GOAL_READER else KID_GOAL_PRE_READER,
                KID_HINTS, colourNotes = true, micOffsetDb = KID_MIC_OFFSET_DB, createdAt = 0L)
            else -> Profile("", "", 0, kind, true, ADULT_GOAL, ADULT_HINTS, colourNotes = false, micOffsetDb = 0.0, createdAt = 0L, band = band)
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
            // 2.14: a kid saved before the age band is a little kid (the pup's world, as they had it).
            val band = AgeBand.parse(m["band"] as? String)
            val d = defaults(kind, reader, band)
            val saved = (m["name"] as? String)?.let(::cleanName)?.takeIf { it.isNotEmpty() }
            val name = saved ?: if (id == OWNER_ID) UNSET_NAME else "Player"
            // A profile saved before 2.14 has no flag: the owner called "Roy" never chose it (the app made it), so that name is
            // asked for again, once; any other name was typed by someone.
            val named = m["named"] as? Boolean ?: (id != OWNER_ID || (saved != null && saved != LEGACY_OWNER_NAME))
            val hints = (m["hintSeconds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.takeIf { it.size == 3 } ?: d.hintSeconds
            return d.copy(
                id = id,
                reader = reader,
                name = name,
                named = named,
                avatar = ((m["avatar"] as? Number)?.toInt() ?: 0).coerceIn(0, AVATARS - 1),
                dailyGoalMin = (m["dailyGoalMin"] as? Number)?.toInt() ?: d.dailyGoalMin,
                hintSeconds = hints,
                colourNotes = m["colourNotes"] as? Boolean ?: d.colourNotes,
                micOffsetDb = (m["micOffsetDb"] as? Number)?.toDouble() ?: d.micOffsetDb,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                level = Level.parse(m["level"] as? String),
                band = band,
            )
        }

        fun decode(text: String): Profile? = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull()?.let(::fromMap)
    }
}
