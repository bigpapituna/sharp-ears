package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.world.Crown
import com.bigpapituna.sharpears.engine.world.NextDrop
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.PupSaysResult
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.engine.world.Sticker
import com.bigpapituna.sharpears.engine.world.Stickers
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.engine.world.World
import com.bigpapituna.sharpears.engine.world.WorldState
import java.time.LocalDate

/**
 * One person's world (stickers, the egg, the treat jar, the record shelf, the pup's outfit, Pup says bests), in its
 * own preference file: "world" for the owner, "world.<id>" for everyone else ([com.bigpapituna.sharpears.data.ProgressFiles.fileFor]),
 * as one JSON string ([WorldState.encode]) so a backup carries it as is. The rules are in engine/world; this keeps
 * the state, saves it, and gives screens a [version] to recompose on.
 *
 * Notes into the jar arrive one per strike; they are saved every [SAVE_EVERY] notes (and on [flush]), not per note.
 */
class WorldStore(context: Context, val profileId: String, private val log: (String) -> Unit = { Log.d(TAG, it) }) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)
    private val stateValue = mutableStateOf(load())
    private var unsavedNotes = 0

    /** Bumps on every change, so screens that read the store recompose. */
    val version = mutableIntStateOf(0)

    val state: WorldState get() = stateValue.value

    private fun load(): WorldState {
        val text = p.getString(KEY, null)
        val s = WorldState.decode(text, seed = java.util.Random().nextLong())
        if (text == null) p.edit().putString(KEY, s.encode()).apply()      // the seed is fixed from the first open
        return s
    }

    private fun set(s: WorldState, save: Boolean = true) {
        stateValue.value = s
        version.intValue++
        if (save) { p.edit().putString(KEY, s.encode()).apply(); unsavedNotes = 0 }
    }

    /**
     * A course lesson finished: a sticker (a Playground lesson passes its [place], see [Place.ofPlaygroundLesson]),
     * the egg hatching or laid. Show the returned [Reward] (ui/world DropReveal).
     */
    fun lessonComplete(lessonId: String, place: Place? = null): Reward {
        val o = World.lessonComplete(state, place)
        set(o.state)
        log("world: lesson $lessonId -> " + describe(o.reward) + ", album ${o.state.unique}/30, lessons ${o.state.lessons}")
        return o.reward
    }

    /** A song played to its end; [accuracy] for a Perform run (0..100), null for Wait or Listen. */
    fun songComplete(songId: String, accuracy: Int?): Reward {
        val o = World.songComplete(state, songId, accuracy)
        set(o.state)
        log("world: song $songId ${accuracy?.let { "$it%" } ?: "played through"} -> " + describe(o.reward) +
            (o.reward.record?.let { ", record ${it.before?.label ?: "none"} to ${it.after.label}" } ?: ""))
        return o.reward
    }

    /** What the next lesson (of [place]) gives, for the "one more" button. */
    fun preview(place: Place? = null): NextDrop = World.preview(state, place)

    /** [n] notes played: into the treat jar. */
    fun addNotes(n: Int = 1) {
        val before = state.jar
        val s = World.addNotes(state, n)
        if (s === state) return
        unsavedNotes += n
        val full = s.jar >= com.bigpapituna.sharpears.engine.world.TreatJar.CAPACITY && before < s.jar
        set(s, save = unsavedNotes >= SAVE_EVERY || full)
        if (full) log("world: treat jar full")
    }

    /** Tips the full jar for the pup; false when it is not full yet. */
    fun tip(): Boolean {
        val s = World.tip(state) ?: return false
        set(s)
        log("world: jar tipped, treats ${s.treats}")
        return true
    }

    /** A Pup says game ended (play team): the longest pattern copied. */
    fun pupSays(result: PupSaysResult) {
        if (result.profileId != profileId) return
        val before = state.pupSays[result.day] ?: 0
        set(World.pupSays(state, result.best, result.day))
        if (result.best > before) {
            // When the day's best was reached: the crown's tie-break. Days the world forgot are dropped with it.
            val e = p.edit().putLong(KEY_SAYS_AT + result.day, result.atMillis)
            p.all.keys.filter { it.startsWith(KEY_SAYS_AT) && runCatching { LocalDate.parse(it.removePrefix(KEY_SAYS_AT)) !in state.pupSays }.getOrDefault(true) }
                .forEach(e::remove)
            e.apply()
        }
        log("world: pup says best ${result.best} on ${result.day}")
    }

    /** Puts [item] on the pup or takes it off (only when unlocked). */
    fun wear(item: WearItem) {
        val s = World.wear(state, item)
        if (s == state) return
        set(s)
        log("world: outfit ${s.outfit.items.joinToString { it.id }.ifEmpty { "none" }}")
    }

    /** Best Perform runs from before the world (Prefs bests per song id), so the shelf starts with them. Once per open is fine. */
    fun mergeRecords(bests: Map<String, Int>) {
        val s = World.mergeRecords(state, bests)
        if (s != state) { set(s); log("world: shelf has ${s.records.size} songs") }
    }

    /** Saves notes not saved yet (call when the player closes). */
    fun flush() { if (unsavedNotes > 0) set(state) }

    /** Debug screens and tests only: replaces the whole world (the debug route seeds a demo world this way). */
    fun debugSet(s: WorldState) { set(s); log("world: debug state, ${s.unique} stickers") }

    private fun describe(r: Reward) = r.drops.joinToString { "${it.sticker.id} ${it.rarity.label.lowercase()} ${it.source.name.lowercase()}${if (it.isNew) " new" else " double"}" } +
        (if (r.eggLaid) ", egg laid" else "") + (if (r.houseUp) ", house ${r.houseAfter}" else "") +
        (if (r.unlocked.isNotEmpty()) ", unlocked ${r.unlocked.joinToString { it.id }}" else "")

    companion object {
        /** The file name; the integrator adds it to ProgressFiles.NAMES so backup, import and profile deletion cover it. */
        const val FILE = "world"
        private const val KEY = "state"
        const val SAVE_EVERY = 5

        /** The drawable of [sticker] (res/drawable-nodpi/sticker_<place>_<n>.webp). */
        fun stickerRes(sticker: Sticker): Int = RES[Stickers.ALL.indexOf(sticker)]

        /** For the family wall: the drawable of the sticker [profileId] got last, or null before the first. */
        fun latestSticker(context: Context, profileId: String): Int? {
            val text = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE).getString(KEY, null)
            return WorldState.decode(text, 0L).latest?.let(Stickers::byId)?.let(::stickerRes)
        }

        /** Who wears the crown of the day among [profileIds]: the longest Pup says pattern today (ties: whoever got there first). */
        fun crownOfTheDay(context: Context, profileIds: List<String>, today: LocalDate = LocalDate.now()): String? =
            Crown.of(profileIds.mapNotNull { id ->
                val prefs = context.getSharedPreferences(ProgressFiles.fileFor(FILE, id), Context.MODE_PRIVATE)
                val best = WorldState.decode(prefs.getString(KEY, null), 0L).pupSays[today] ?: return@mapNotNull null
                // The day's best has no time of its own; the profile that saved last is treated as later.
                PupSaysResult(id, best, today, atMillis = prefs.getLong(KEY_SAYS_AT + today, Long.MAX_VALUE))
            }, today)

        private const val KEY_SAYS_AT = "saysAt:"

        private val RES = intArrayOf(
            R.drawable.sticker_garden_1, R.drawable.sticker_garden_2, R.drawable.sticker_garden_3, R.drawable.sticker_garden_4,
            R.drawable.sticker_garden_5, R.drawable.sticker_garden_6, R.drawable.sticker_garden_7, R.drawable.sticker_garden_8,
            R.drawable.sticker_garden_9, R.drawable.sticker_garden_10,
            R.drawable.sticker_pond_1, R.drawable.sticker_pond_2, R.drawable.sticker_pond_3, R.drawable.sticker_pond_4,
            R.drawable.sticker_pond_5, R.drawable.sticker_pond_6, R.drawable.sticker_pond_7, R.drawable.sticker_pond_8,
            R.drawable.sticker_pond_9, R.drawable.sticker_pond_10,
            R.drawable.sticker_concert_1, R.drawable.sticker_concert_2, R.drawable.sticker_concert_3, R.drawable.sticker_concert_4,
            R.drawable.sticker_concert_5, R.drawable.sticker_concert_6, R.drawable.sticker_concert_7, R.drawable.sticker_concert_8,
            R.drawable.sticker_concert_9, R.drawable.sticker_concert_10,
        )
    }
}
