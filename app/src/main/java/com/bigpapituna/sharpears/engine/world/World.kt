package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.MiniJson
import java.time.LocalDate

/**
 * Everything one person's world remembers: the profile's random [seed] (drops are a pure function of it and the
 * [history]), the stickers owned, lessons finished (an egg every [World.EGG_EVERY]), the egg waiting to hatch, the
 * treat jar, the record shelf (best Perform accuracy per song), the pup's outfit, Pup says bests of recent days
 * (for the crown of the day) and the latest sticker (for the family wall).
 *
 * 2.5: the practice [ledger] and the [wardrobe] it opened ([fresh]: opened but not shown yet, the next finish screen
 * reveals them; [practiceDay]: when the last practice item opened, one a day), the stones' own stickers ([levels],
 * lesson ids), every sticker in the order it was earned ([earned], keys; the tray lists the unplaced ones newest first),
 * where the kid stuck them ([placements]), and [homesLine]: stickers placed by the app before 2.5 were moved to the
 * tray, and the pup says so once.
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
    val ledger: Ledger = Ledger(),
    val wardrobe: Set<WearItem> = emptySet(),
    val fresh: List<WearItem> = emptyList(),
    val practiceDay: LocalDate? = null,
    val levels: Set<String> = emptySet(),
    val earned: List<String> = emptyList(),
    val placements: Placements = Placements(),
    val homesLine: Boolean = false,
    /** 2.9: Tomorrow's surprise (engine/world/Surprise.kt). */
    val gift: Gift = Gift(),
) {
    val album: Album get() = Album.of(history.owned)
    val unique: Int get() = history.unique
    val house: Int get() = House.stage(unique)
    /** Items open to wear, in catalogue order. */
    val unlocked: List<WearItem> get() = WearItem.entries.filter { it in wardrobe }
    /** Stickers not stuck anywhere yet, newest first. */
    val tray: List<String> get() = Placements.tray(earned, placements)
    /** How close the next practice item is (null when the track is done). */
    val nextOutfit: NextOutfit? get() = Wardrobe.next(ledger, wardrobe)

    fun encode(): String = MiniJson.write(linkedMapOf(
        "v" to VERSION,
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
        "ledger" to linkedMapOf(
            "seconds" to ledger.seconds,
            "credited" to ledger.credited.entries.sortedBy { it.key }.associate { it.key.toString() to it.value },
            "days" to ledger.days,
            "lastDay" to ledger.lastDay?.toString(),
            "streak" to ledger.streak,
            "best" to ledger.best,
            "threeStars" to ledger.threeStars.sorted(),
            "finales" to ledger.finales.sorted(),
            "songs" to ledger.songs,
            "songSparkles" to ledger.songSparkles,
            "songDay" to ledger.songDay?.toString(),
            "songsThatDay" to ledger.songsThatDay,
            "floor" to ledger.floor?.toString(),
            "bonus" to ledger.bonus,
        ),
        "wardrobe" to WearItem.entries.filter { it in wardrobe }.map { it.id },
        "fresh" to fresh.map { it.id },
        "practiceDay" to practiceDay?.toString(),
        "levels" to levels.sorted(),
        "earned" to earned,
        "placed" to placements.at.toSortedMap(),
        "homesLine" to homesLine,
        "gift" to linkedMapOf("on" to gift.wrappedOn?.toString(), "opened" to gift.opened),
    ))

    companion object {
        /** 1: 2.1 to 2.4 (stickers placed by the app, items by sticker count). 2: 2.5, the wardrobe and decorate mode. */
        const val VERSION = 2

        /**
         * Reads [text] back; anything missing or unreadable falls back to a fresh world with [seed] (an unknown sticker
         * or item id from a newer version is dropped, not fatal). A version 1 world is moved on: what its stickers had
         * opened stays open, its stickers go to the tray (oldest first in catalogue order) and the pup's line is due.
         */
        fun decode(text: String?, seed: Long): WorldState {
            if (text.isNullOrBlank()) return WorldState(seed)
            val m = runCatching { MiniJson.read(text) as? Map<*, *> }.getOrNull() ?: return WorldState(seed)
            fun int(k: String, map: Map<*, *> = m) = (map[k] as? Number)?.toInt() ?: 0
            fun ints(k: String, map: Map<*, *> = m) = (map[k] as? Map<*, *>)?.mapNotNull { (key, v) -> (v as? Number)?.let { key.toString() to it.toInt() } }?.toMap().orEmpty()
            fun strings(k: String, map: Map<*, *> = m) = (map[k] as? List<*>).orEmpty().mapNotNull { it as? String }
            fun date(v: Any?) = (v as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val owned = ints("owned").filter { Stickers.byId(it.key) != null && it.value > 0 }
            val old = int("v") < 2
            val l = m["ledger"] as? Map<*, *> ?: emptyMap<String, Any>()
            val ledger = Ledger(
                seconds = (l["seconds"] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
                credited = ints("credited", l).mapNotNull { (d, s) -> date(d)?.let { it to s } }.toMap(),
                days = int("days", l).coerceAtLeast(0),
                lastDay = date(l["lastDay"]),
                streak = int("streak", l).coerceAtLeast(0),
                best = int("best", l).coerceAtLeast(0),
                threeStars = strings("threeStars", l).toSet(),
                finales = strings("finales", l).toSet(),
                songs = int("songs", l).coerceAtLeast(0),
                songSparkles = int("songSparkles", l).coerceAtLeast(0),
                songDay = date(l["songDay"]),
                songsThatDay = int("songsThatDay", l).coerceAtLeast(0),
                floor = date(l["floor"]),
                bonus = int("bonus", l).coerceAtLeast(0),
            )
            val levels = strings("levels").filter { LevelStickers.byLesson(it) != null }.toSet()
            val keys = owned.keys + levels.map { LevelSticker.PREFIX + it }
            val wardrobe = if (old) Wardrobe.legacy(owned.size) else strings("wardrobe").mapNotNull(WearItem::byId).toSet()
            val earned = if (old) Stickers.ALL.map { it.id }.filter { it in owned }
                else strings("earned").filter { it in keys }.distinct().let { e -> e + keys.filter { it !in e }.sorted() }
            val placements = if (old) Placements() else Placements((m["placed"] as? Map<*, *>).orEmpty()
                .mapNotNull { (k, x) -> (x as? String)?.let { k.toString() to it } }.toMap()).valid(keys)
            return WorldState(
                seed = (m["seed"] as? Number)?.toLong() ?: seed,
                history = DropHistory(owned = owned, sinceRare = int("sinceRare").coerceAtLeast(0), rolls = int("rolls").coerceAtLeast(0)),
                lessons = int("lessons").coerceAtLeast(0),
                egg = m["egg"] == true,
                jar = int("jar").coerceIn(0, TreatJar.CAPACITY),
                treats = int("treats").coerceAtLeast(0),
                records = ints("records").mapValues { it.value.coerceIn(0, 100) },
                outfit = Outfit((m["outfit"] as? List<*>).orEmpty().mapNotNull { WearItem.byId(it?.toString()) }
                    .filter { it in wardrobe }.groupBy { it.slot }.map { it.value.first() }.toSet()),
                pupSays = ints("pupSays").mapNotNull { (d, n) -> date(d)?.let { it to n } }.toMap(),
                latest = (m["latest"] as? String)?.takeIf { Stickers.byId(it) != null },
                ledger = ledger,
                wardrobe = wardrobe,
                fresh = strings("fresh").mapNotNull(WearItem::byId).filter { it in wardrobe }.distinct(),
                practiceDay = date(m["practiceDay"]),
                levels = levels,
                earned = earned,
                placements = placements,
                homesLine = if (old) owned.isNotEmpty() else m["homesLine"] == true,
                gift = (m["gift"] as? Map<*, *>)?.let { g -> date(g["on"])?.let { Gift(it, g["opened"] != false) } } ?: Gift(),
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
    /** 2.5: items to wear that opened since the last finish screen, announced now. */
    val unlocked: List<WearItem> = emptyList(),
    val record: RecordUp? = null,
    /** 2.5: the stone's own sticker, earned on its first finish. */
    val level: LevelSticker? = null,
) {
    val houseUp: Boolean get() = houseAfter > houseBefore
    /** The rarest drop (the one the jingle and the pup react to). */
    val best: Drop? get() = drops.maxByOrNull { it.rarity.ordinal }
    val isEmpty: Boolean get() = drops.isEmpty() && !eggLaid && unlocked.isEmpty() && record == null && !houseUp && level == null
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

/** 2.9: a surprise opened: what [kind] it held, the world after, and the [reward] to show (a drop, or things to wear). */
data class GiftOpened(val kind: GiftKind, val state: WorldState, val reward: Reward)

/** The world's rules: events in, a new state (and a reward) out. Pure; data/WorldStore.kt keeps the state. */
object World {
    /** An egg is laid on every this-many finished lessons. */
    const val EGG_EVERY = 3
    /** Pup says days kept. */
    const val KEEP_DAYS = 14

    /**
     * A lesson finished: a sticker (from [place] for a Playground lesson, else the album's current place), the waiting
     * egg hatches one rarity up, and every [EGG_EVERY]th lesson lays a new egg. 2.5: the first finish of a stone with
     * its own sticker ([lessonId], [LevelStickers]) earns it into the tray, and items the ledger opened since the last
     * finish screen are announced ([Reward.unlocked]).
     */
    fun lessonComplete(s: WorldState, place: Place? = null, lessonId: String? = null): Outcome {
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
        val level = LevelStickers.byLesson(lessonId)?.takeIf { it.lessonId !in s.levels }
        val after = s.copy(history = h, lessons = lessons, egg = lay, levels = level?.let { s.levels + it.lessonId } ?: s.levels,
            earned = s.earned + listOfNotNull(level?.key))
        return finish(s, after, drops, eggLaid = lay, eggHatched = s.egg, level = level)
    }

    /**
     * A song played to its end: a sticker (everyone who finishes gets one), and for a Perform run ([accuracy] not null)
     * the record shelf keeps the best and says when its medal moved up. 2.5: the ledger counts the song ([today]).
     */
    fun songComplete(s: WorldState, songId: String, accuracy: Int?, today: LocalDate = LocalDate.now()): Outcome {
        val drop = Drops.roll(s.seed, s.history, DropSource.SONG)
        val before = s.records[songId]
        val up = accuracy?.let { Records.up(songId, before, it) }
        val records = if (accuracy == null) s.records else s.records + (songId to Records.best(before, accuracy))
        val counted = unlock(s.copy(ledger = s.ledger.song(today)), today)
        return finish(s, counted.copy(history = s.history.after(drop), records = records), listOf(drop), record = up)
    }

    /**
     * A round of an arcade game ended (2.7, engine/arcade): it counts like a song played to its end in the ledger (two
     * sparkles, within the five songs a day), with no sticker drop (a round is a minute; a drop each would flood the
     * album). [sticker] is the game's own sticker (LevelStickers' arcade page) on its first three stars: earned into the
     * tray like a stone's. Items the ledger opens wait for the next finish screen, as a song's do.
     */
    fun gameRound(s: WorldState, sticker: String?, today: LocalDate = LocalDate.now()): Outcome {
        val counted = unlock(s.copy(ledger = s.ledger.song(today)), today)
        val level = LevelStickers.byLesson(sticker)?.takeIf { it.lessonId !in s.levels }
        val after = counted.copy(levels = level?.let { counted.levels + it.lessonId } ?: counted.levels, earned = counted.earned + listOfNotNull(level?.key))
        return finish(s, after, emptyList(), level = level)
    }

    private fun finish(before: WorldState, after: WorldState, drops: List<Drop>, eggLaid: Boolean = false, eggHatched: Boolean = false,
                       record: RecordUp? = null, level: LevelSticker? = null): Outcome {
        val newKeys = drops.filter { it.isNew }.map { it.sticker.key }.distinct().filter { it !in after.earned }
        val state = after.copy(latest = drops.lastOrNull()?.sticker?.id ?: after.latest, earned = after.earned + newKeys, fresh = emptyList())
        return Outcome(state, Reward(drops, eggLaid, eggHatched, before.house, state.house, after.fresh, record, level))
    }

    /**
     * The ledger synced from the stores (Ledger.sync: [practice] seconds a day, [threeStars] lessons, [walked] pages) on
     * [today], and whatever it opens: milestones at once, the next practice item if its sparkles are in and none opened
     * today. New items wait in [WorldState.fresh] for the next finish screen.
     */
    fun syncLedger(s: WorldState, practice: Map<LocalDate, Int>, threeStars: Set<String>, walked: Set<String>, today: LocalDate = LocalDate.now()): WorldState =
        unlock(s.copy(ledger = s.ledger.sync(practice, threeStars, walked)), today)

    private fun unlock(s: WorldState, today: LocalDate): WorldState {
        var st = s
        // A practice item a day at most; milestones all at once.
        val due = Wardrobe.due(st.ledger, st.wardrobe, today, st.practiceDay)
        if (due.isEmpty()) return st
        val practice = due.any { it.earn is Earn.Practice }
        st = st.copy(wardrobe = st.wardrobe + due, fresh = st.fresh + due.filter { it !in st.fresh },
            practiceDay = if (practice) today else st.practiceDay)
        return st
    }

    /**
     * 2.9, Tomorrow's surprise: wrapped on [today] when the day's goal is [goalMet] and the rules allow it (Surprise.canWrap);
     * otherwise the world as it was.
     */
    fun wrapGift(s: WorldState, today: LocalDate, goalMet: Boolean): WorldState =
        Surprise.wrap(s.gift, today, goalMet).let { g -> if (g == s.gift) s else s.copy(gift = g) }

    /**
     * 2.9: the waiting gift opened on [today] (null when it is not ready: Surprise.ready). Inside, as Surprise.roll says: a
     * sticker for the album (a drop, the album's current place), a look from the practice track the kid has not got (opened
     * at once and announced in the reward, as a finish screen's "Keep it on?"), or [Surprise.SPARKLES] sparkles for the
     * ledger (whatever they open is announced too).
     */
    fun openGift(s: WorldState, today: LocalDate): GiftOpened? {
        val on = s.gift.wrappedOn ?: return null
        if (!Surprise.ready(s.gift, today)) return null
        val opened = s.copy(gift = s.gift.copy(opened = true))
        val outfit = Surprise.outfit(s.seed, on, s.wardrobe)
        val kind = Surprise.roll(s.seed, on).let { if (it == GiftKind.OUTFIT && outfit == null) GiftKind.SPARKLES else it }
        val o = when (kind) {
            GiftKind.STICKER -> {
                val drop = Drops.roll(s.seed, s.history, DropSource.GIFT)
                finish(s, opened.copy(history = s.history.after(drop)), listOf(drop))
            }
            GiftKind.OUTFIT -> finish(s, opened.copy(wardrobe = s.wardrobe + outfit!!, fresh = s.fresh + outfit.filter { it !in s.fresh }), emptyList())
            GiftKind.SPARKLES -> finish(s, unlock(opened.copy(ledger = opened.ledger.copy(bonus = opened.ledger.bonus + Surprise.SPARKLES)), today), emptyList())
        }
        return GiftOpened(kind, o.state, o.reward)
    }

    /** The items waiting to be shown, now shown (a finish screen that is not a lesson's or a song's: the finale). */
    fun announce(s: WorldState): Pair<WorldState, List<WearItem>> = s.copy(fresh = emptyList()) to s.fresh

    /**
     * Level stickers for stones finished before 2.5 (or on another phone): every [done] lesson with a sticker of its
     * own that is not earned yet goes into the tray, quietly.
     */
    fun earnLevels(s: WorldState, done: (String) -> Boolean): WorldState {
        val add = LevelStickers.ALL.filter { it.lessonId !in s.levels && done(it.lessonId) }
        return if (add.isEmpty()) s else s.copy(levels = s.levels + add.map { it.lessonId }, earned = s.earned + add.map { it.key })
    }

    /** [key] stuck in [slotId] (a sticker the person owns; a slot in a place they have reached is the caller's rule). */
    fun place(s: WorldState, key: String, slotId: String): WorldState =
        if (key !in s.earned) s else s.copy(placements = s.placements.put(key, slotId), homesLine = false)

    /** [key] back in the tray. */
    fun unplace(s: WorldState, key: String): WorldState = s.copy(placements = s.placements.remove(key))

    /** The pup said the stickers want new homes (once, after the move from 2.4). */
    fun homesSaid(s: WorldState): WorldState = s.copy(homesLine = false)
    /** What finishing the next lesson (of [place], when it has one) would give, the same roll [lessonComplete] makes. */
    fun preview(s: WorldState, place: Place? = null): NextDrop {
        val drop = Drops.roll(s.seed, s.history, DropSource.LESSON, place)
        val hatch = if (s.egg) Drops.roll(s.seed, s.history.after(drop), DropSource.EGG, place, bump = 1).sticker else null
        return NextDrop(drop.sticker, Drops.floorDue(s.history), s.egg, hatch, eggLays = (s.lessons + 1) % EGG_EVERY == 0)
    }

    /** What playing a song to its end would give (songs never hatch or lay eggs), the roll [songComplete] makes. */
    fun previewSong(s: WorldState): NextDrop =
        NextDrop(Drops.roll(s.seed, s.history, DropSource.SONG).sticker, Drops.floorDue(s.history), eggHatches = false, hatch = null, eggLays = false)

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
        if (item !in s.wardrobe) s else s.copy(outfit = s.outfit.toggle(item))

    /** The pup in [outfit] (items not open are left off). */
    fun dress(s: WorldState, outfit: Outfit): WorldState = s.copy(outfit = Outfit(outfit.items.filter { it in s.wardrobe }.toSet()))

    /** Best Perform runs from before the world existed (Prefs), so the shelf starts full; the higher best wins. */
    fun mergeRecords(s: WorldState, bests: Map<String, Int>): WorldState =
        s.copy(records = (s.records.keys + bests.keys).associateWith { maxOf(s.records[it] ?: -1, bests[it] ?: -1).coerceIn(0, 100) })
}
