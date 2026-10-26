package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.kid.LessonStars
import com.bigpapituna.sharpears.engine.kid.Pace
import com.bigpapituna.sharpears.engine.kid.Pin
import com.bigpapituna.sharpears.engine.kid.Shelf
import com.bigpapituna.sharpears.engine.kid.ShelfSong

/**
 * The kid home's memory for one profile (2.2), in its own preference file ("kid" for the owner, "kid.<id>" for everyone
 * else, [ProgressFiles.fileFor]; "kid" is in [ProgressFiles.NAMES], so backup, import and deleting a profile cover it):
 * each lesson's best stars ("stars:<lesson id>", 1 to 3, the stone shows them), the pages whose finale has played
 * ("finale:<page id>") and whether the pup has said the big kids' road line ("roadSaid"). Rules in engine/kid.
 *
 * 2.4, what a grown-up set from the parent brief (engine/kid/Pace.kt; kept for grown-ups too, whose Learn path reads the
 * pin): the lesson pinned as the next stone ("pin", "pinUntil"), focus mode ("focus"), the pages opened early ("early")
 * and the shelf of songs assigned with a note ("shelf", JSON).
 */
class KidStore(context: Context, val profileId: String, private val log: (String) -> Unit = { Log.d(TAG, it) }) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)

    /** Bumps on every change, so the map recomposes. */
    val version = mutableIntStateOf(0)

    /** The best stars [lessonId] ever got, 0 before its first finish. */
    fun stars(lessonId: String): Int = p.getInt(STARS + lessonId, 0)

    /** Every lesson's best stars. */
    fun allStars(): Map<String, Int> = p.all.mapNotNull { (k, v) -> if (k.startsWith(STARS) && v is Int) k.removePrefix(STARS) to v else null }.toMap()

    /** A finished run of [lessonId] earned [stars]: the stone keeps the best ([LessonStars.best]). Returns the best now. */
    fun recordStars(lessonId: String, stars: Int): Int {
        val before = stars(lessonId)
        val best = LessonStars.best(before, stars)
        if (best != before) { p.edit { putInt(STARS + lessonId, best) }; version.intValue++ }
        log("kid: $lessonId $stars stars, best $best" + if (best > before && before > 0) " (up from $before)" else "")
        return best
    }

    /** The pages whose finale has played. */
    fun finales(): Set<String> = p.all.keys.filter { it.startsWith(FINALE) && p.getBoolean(it, false) }.map { it.removePrefix(FINALE) }.toSet()

    /** [pageId]'s finale played: it does not play again, and the path walks on past the page. */
    fun finalePlayed(pageId: String) {
        if (pageId in finales()) return
        p.edit { putBoolean(FINALE + pageId, true) }
        version.intValue++
        log("kid: finale of $pageId played")
        Briefs.logFor(profileId) { com.bigpapituna.sharpears.engine.brief.BriefEvent.FinalePlayed(it, pageId) }
    }

    /** The pup said "You're ready for the big kids' road!" (once, after the last page's finale). */
    var roadSaid: Boolean
        get() = p.getBoolean(ROAD_SAID, false)
        set(v) { p.edit { putBoolean(ROAD_SAID, v) }; version.intValue++ }

    /** 2.14: "Skip the garden" was answered (taken, or "Not now"): it is never offered again (engine/kid/KidLevels.kt GardenSkip). */
    var gardenSkipAsked: Boolean
        get() = p.getBoolean(GARDEN_SKIP, false)
        set(v) { p.edit { putBoolean(GARDEN_SKIP, v) }; version.intValue++ }

    /** The lesson pinned as the next stone and until when (Set as next), or null. */
    var pin: Pin?
        get() = p.getString(PIN, null)?.let { Pin(it, p.getLong(PIN_UNTIL, 0L)) }
        set(v) { p.edit { if (v == null) { remove(PIN); remove(PIN_UNTIL) } else { putString(PIN, v.lessonId); putLong(PIN_UNTIL, v.untilMs) } }; version.intValue++
            log("kid: pin ${v?.let { "${it.lessonId} until ${it.untilMs}" } ?: "cleared"}") }

    /** Focus mode: the pages after this one are hidden for now (null: off). */
    var focus: String?
        get() = p.getString(FOCUS, null)
        set(v) { p.edit { if (v == null) remove(FOCUS) else putString(FOCUS, v) }; version.intValue++; log("kid: focus ${v ?: "off"}") }

    /** Pages a grown-up opened the next page after, early. */
    var early: Set<String>
        get() = p.getStringSet(EARLY, null)?.toSet().orEmpty()
        set(v) { p.edit { if (v.isEmpty()) remove(EARLY) else putStringSet(EARLY, v.toSet()) }; version.intValue++; log("kid: pages opened early $v") }

    /** Songs a grown-up put on the shelf, newest first (Teacher mode). */
    var shelf: List<ShelfSong>
        get() = Shelf.decode(p.getString(SHELF, null))
        set(v) { p.edit { if (v.isEmpty()) remove(SHELF) else putString(SHELF, Shelf.encode(v)) }; version.intValue++; log("kid: shelf ${v.map { it.songId }}") }

    /** The pace the map and the next lesson follow [now]: a pin past its day is left out. */
    fun pace(now: Long = System.currentTimeMillis()): Pace = Pace(pin?.takeIf { it.activeAt(now) }?.lessonId, focus, early)

    /** A fresh start (the parent brief): [lessonIds] lose their stars; finales, stickers and the rest stay. */
    fun forgetStars(lessonIds: Collection<String>) {
        p.edit { lessonIds.forEach { remove(STARS + it) } }
        version.intValue++
        log("kid: stars forgotten for ${lessonIds.size} lessons (fresh start)")
    }

    /** Undoing a fresh start: these best stars back as they were. */
    fun restoreStars(stars: Map<String, Int>) {
        p.edit { stars.forEach { (id, n) -> if (n > 0) putInt(STARS + id, n.coerceIn(1, 3)) } }
        version.intValue++
        log("kid: stars restored for ${stars.size} lessons")
    }

    /** [lessonId] was finished: a pin on it has done its job. */
    fun lessonPlayed(lessonId: String) { if (pin?.lessonId == lessonId) pin = null }

    /** Debug routes only: forget the finales and stars of [lessonIds] (or everything), to walk a page again. */
    fun debugForget(lessonIds: Collection<String>? = null, pageIds: Collection<String>? = null) {
        p.edit {
            if (lessonIds == null && pageIds == null) clear()
            else {
                lessonIds?.forEach { remove(STARS + it) }
                pageIds?.forEach { remove(FINALE + it) }
                if (pageIds != null) remove(ROAD_SAID)
            }
        }
        version.intValue++
        log("kid: debug forget ${lessonIds?.size ?: "all"} lessons, pages ${pageIds ?: "all"}")
    }

    companion object {
        /** The file name; in [ProgressFiles.NAMES]. */
        const val FILE = "kid"
        private const val STARS = "stars:"
        private const val FINALE = "finale:"
        private const val ROAD_SAID = "roadSaid"
        private const val GARDEN_SKIP = "gardenSkipAsked"
        private const val PIN = "pin"
        private const val PIN_UNTIL = "pinUntil"
        private const val FOCUS = "focus"
        private const val EARLY = "early"
        private const val SHELF = "shelf"
    }
}
