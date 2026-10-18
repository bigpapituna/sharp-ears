package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.MiniJson
import java.time.LocalDate

/**
 * Everything one person's world remembers: the profile's random [seed] (drops are a pure function of it and the
 * [history]), the stickers owned, lessons finished (an egg every [World.EGG_EVERY]), the egg waiting to hatch, the
 * treat jar, the record shelf (best Perform accuracy per song), the pup's outfit, Pup says bests of recent days
 * (for the crown of the day) and the latest sticker (for the family wall).
 */
data class WorldState(
    val seed: Long,
    val history: DropHistory = DropHistory(),
    /** Lessons finished since the world began (replays count: a replay is practice too). */
    val lessons: Int = 0,
    /** An egg is waiting; it hatches when the next lesson is finished. */
    val egg: Boolean = false,
    /** Notes in the treat jar, 0..[TreatJar.CAPACITY]. */
    val jar: Int = 0,
    /** Times the jar was tipped for the pup. */
    val treats: Int = 0,
    /** Best Perform accuracy per song id. */
    val records: Map<String, Int> = emptyMap(),
    val outfit: Outfit = Outfit.NONE,
    /** The longest Pup says pattern per day (the last [World.KEEP_DAYS] days). */
    val pupSays: Map<LocalDate, Int> = emptyMap(),
    /** The sticker given last. */
    val latest: String? = null,
) {
    val album: Album get() = Album.of(history.owned)
    val unique: Int get() = history.unique
    val house: Int get() = House.stage(unique)
    val unlocked: List<WearItem> get() = Dress.unlocked(unique)

    fun encode(): String = MiniJson.write(linkedMapOf(
        "v" to 1,
        "seed" to seed,
        "owned" to history.owned.filterValues { it > 0 }.toSortedMap(),
        "sinceRare" to history.sinceRare,
        "rolls" to history.rolls,
        "lessons" to lessons,
        "egg" to egg,
        "jar" to jar,
        "treats" to treats,
        "records" to records.toSortedMap(),
        "outfit" to outfit.items.map { it.id }.sorted(),
        "pupSays" to pupSays.entries.sortedBy { it.key }.associate { it.key.toString() to it.value },
        "latest" to latest,
    ))

    companion object {
        /**
         * Reads [text] back; anything missing or unreadable falls back to a fresh world with [seed] (an unknown sticker
         * or item id from a newer version is dropped, not fatal).
         */
        fun decode(text: String?, seed: Long): WorldState {
            if (text.isNullOrBlank()) return WorldState(seed)
            val m = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull() ?: return WorldState(seed)
            fun int(k: String) = (m[k] as? Number)?.toInt() ?: 0
            fun ints(k: String) = (m[k] as? Map<*, *>)?.mapNotNull { (key, v) -> (v as? Number)?.let { key.toString() to it.toInt() } }?.toMap().orEmpty()
            return WorldState(
                seed = (m["seed"] as? Number)?.toLong() ?: seed,
                history = DropHistory(
                    owned = ints("owned").filter { Stickers.byId(it.key) != null && it.value > 0 },
                    sinceRare = int("sinceRare").coerceAtLeast(0),
                    rolls = int("rolls").coerceAtLeast(0),
                ),
                lessons = int("lessons").coerceAtLeast(0),
                egg = m["egg"] == true,
                jar = int("jar").coerceIn(0, TreatJar.CAPACITY),
                treats = int("treats").coerceAtLeast(0),
                records = ints("records").mapValues { it.value.coerceIn(0, 100) },
                outfit = Outfit((m["outfit"] as? List<*>).orEmpty().mapNotNull { WearItem.byId(it?.toString()) }
                    .groupBy { it.slot }.map { it.value.first() }.toSet()),
                pupSays = ints("pupSays").mapNotNull { (d, v) -> runCatching { LocalDate.parse(d) to v }.getOrNull() }.toMap(),
                latest = (m["latest"] as? String)?.takeIf { Stickers.byId(it) != null },
            )
        }
    }
}

/**
 * What a finished lesson or song gave: [drops] in reveal order (the lesson's or song's sticker first, then the
 * egg's), whether an egg was laid or hatched, the dog house before and after, dress-up items unlocked and a record
 * that moved up.
 */
data class Reward(
    val drops: List<Drop>,
    val eggLaid: Boolean = false,
    val eggHatched: Boolean = false,
    val houseBefore: Int = 1,
    val houseAfter: Int = 1,
    val unlocked: List<WearItem> = emptyList(),
    val record: RecordUp? = null,
) {
    val houseUp: Boolean get() = houseAfter > houseBefore
    /** The rarest drop (the one the jingle and the pup react to). */
    val best: Drop? get() = drops.maxByOrNull { it.rarity.ordinal }
    val isEmpty: Boolean get() = drops.isEmpty() && !eggLaid && unlocked.isEmpty() && record == null && !houseUp
}

/**
 * The "one more" preview: what finishing the next lesson gives if nothing else happens first. [sticker] is the
 * lesson's own drop (shown as a silhouette, the surprise kept), [rareDue] when the floor makes it at least rare,
 * [eggHatches] with [hatch] when the waiting egg opens, [eggLays] when a new egg is laid.
 */
data class NextDrop(val sticker: Sticker, val rareDue: Boolean, val eggHatches: Boolean, val hatch: Sticker?, val eggLays: Boolean) {
    val rarity: Rarity get() = sticker.rarity
}

/** A state and what getting there gave. */
data class Outcome(val state: WorldState, val reward: Reward)

/** The world's rules: events in, a new state (and a reward) out. Pure; data/WorldStore.kt keeps the state. */
object World {
    /** An egg is laid on every this-many finished lessons. */
    const val EGG_EVERY = 3
    /** Pup says days kept. */
    const val KEEP_DAYS = 14

    /**
     * A lesson finished: a sticker (from [place] for a Playground lesson, else the album's current place), the waiting
     * egg hatches one rarity up, and every [EGG_EVERY]th lesson lays a new egg.
     */
    fun lessonComplete(s: WorldState, place: Place? = null): Outcome {
        val drop = Drops.roll(s.seed, s.history, DropSource.LESSON, place)
        var h = s.history.after(drop)
        val drops = mutableListOf(drop)
        if (s.egg) {
            val hatched = Drops.roll(s.seed, h, DropSource.EGG, place, bump = 1)
            h = h.after(hatched)
            drops += hatched
        }
        val lessons = s.lessons + 1
        val lay = lessons % EGG_EVERY == 0
        return finish(s, s.copy(history = h, lessons = lessons, egg = lay), drops, eggLaid = lay, eggHatched = s.egg)
    }

    /**
     * A song played to its end: a sticker (everyone who finishes gets one), and for a Perform run ([accuracy] not null)
     * the record shelf keeps the best and says when its medal moved up.
     */
    fun songComplete(s: WorldState, songId: String, accuracy: Int?): Outcome {
        val drop = Drops.roll(s.seed, s.history, DropSource.SONG)
        val before = s.records[songId]
        val up = accuracy?.let { Records.up(songId, before, it) }
        val records = if (accuracy == null) s.records else s.records + (songId to Records.best(before, accuracy))
        return finish(s, s.copy(history = s.history.after(drop), records = records), listOf(drop), record = up)
    }

    private fun finish(before: WorldState, after: WorldState, drops: List<Drop>, eggLaid: Boolean = false, eggHatched: Boolean = false,
                       record: RecordUp? = null): Outcome {
        val state = after.copy(latest = drops.lastOrNull()?.sticker?.id ?: after.latest)
        return Outcome(state, Reward(drops, eggLaid, eggHatched, before.house, state.house, Dress.newlyUnlocked(before.unique, state.unique), record))
    }

    /** What finishing the next lesson (of [place], when it has one) would give, the same roll [lessonComplete] makes. */
    fun preview(s: WorldState, place: Place? = null): NextDrop {
        val drop = Drops.roll(s.seed, s.history, DropSource.LESSON, place)
        val hatch = if (s.egg) Drops.roll(s.seed, s.history.after(drop), DropSource.EGG, place, bump = 1).sticker else null
        return NextDrop(drop.sticker, Drops.floorDue(s.history), s.egg, hatch, eggLays = (s.lessons + 1) % EGG_EVERY == 0)
    }

    /** [n] notes played: into the jar. */
    fun addNotes(s: WorldState, n: Int): WorldState = if (n <= 0 || TreatJar.full(s.jar)) s else s.copy(jar = TreatJar.add(s.jar, n))

    /** Tips a full jar for the pup (a treat fed, the jar empty again); null when it is not full yet. */
    fun tip(s: WorldState): WorldState? = if (!TreatJar.full(s.jar)) null else s.copy(jar = 0, treats = s.treats + 1)

    /** A Pup says game ended with a longest pattern of [best] on [day]: kept as that day's best; old days drop off. */
    fun pupSays(s: WorldState, best: Int, day: LocalDate): WorldState {
        val days = s.pupSays + (day to maxOf(best, s.pupSays[day] ?: 0))
        return s.copy(pupSays = days.filterKeys { !it.isBefore(day.minusDays(KEEP_DAYS.toLong() - 1)) })
    }

    /** Puts [item] on or takes it off, if it is unlocked. */
    fun wear(s: WorldState, item: WearItem): WorldState =
        if (s.unique < item.unlockAt) s else s.copy(outfit = s.outfit.toggle(item))

    /** Best Perform runs from before the world existed (Prefs), so the shelf starts full; the higher best wins. */
    fun mergeRecords(s: WorldState, bests: Map<String, Int>): WorldState =
        s.copy(records = (s.records.keys + bests.keys).associateWith { maxOf(s.records[it] ?: -1, bests[it] ?: -1).coerceIn(0, 100) })
}
