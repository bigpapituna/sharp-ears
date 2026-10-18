package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.kid.LessonStars

/**
 * The kid home's memory for one profile (2.2), in its own preference file ("kid" for the owner, "kid.<id>" for everyone
 * else, [ProgressFiles.fileFor]; "kid" is in [ProgressFiles.NAMES], so backup, import and deleting a profile cover it):
 * each lesson's best stars ("stars:<lesson id>", 1 to 3, the stone shows them), the pages whose finale has played
 * ("finale:<page id>") and whether the pup has said the big kids' road line ("roadSaid"). Rules in engine/kid.
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
        if (best != before) { p.edit().putInt(STARS + lessonId, best).apply(); version.intValue++ }
        log("kid: $lessonId $stars stars, best $best" + if (best > before && before > 0) " (up from $before)" else "")
        return best
    }

    /** The pages whose finale has played. */
    fun finales(): Set<String> = p.all.keys.filter { it.startsWith(FINALE) && p.getBoolean(it, false) }.map { it.removePrefix(FINALE) }.toSet()

    /** [pageId]'s finale played: it does not play again, and the path walks on past the page. */
    fun finalePlayed(pageId: String) {
        if (pageId in finales()) return
        p.edit().putBoolean(FINALE + pageId, true).apply()
        version.intValue++
        log("kid: finale of $pageId played")
    }

    /** The pup said "You're ready for the big kids' road!" (once, after the last page's finale). */
    var roadSaid: Boolean
        get() = p.getBoolean(ROAD_SAID, false)
        set(v) { p.edit().putBoolean(ROAD_SAID, v).apply(); version.intValue++ }

    /** Debug routes only: forget the finales and stars of [lessonIds] (or everything), to walk a page again. */
    fun debugForget(lessonIds: Collection<String>? = null, pageIds: Collection<String>? = null) {
        val e = p.edit()
        if (lessonIds == null && pageIds == null) e.clear()
        else {
            lessonIds?.forEach { e.remove(STARS + it) }
            pageIds?.forEach { e.remove(FINALE + it) }
            if (pageIds != null) e.remove(ROAD_SAID)
        }
        e.apply()
        version.intValue++
        log("kid: debug forget ${lessonIds?.size ?: "all"} lessons, pages ${pageIds ?: "all"}")
    }

    companion object {
        /** The file name; in [ProgressFiles.NAMES]. */
        const val FILE = "kid"
        private const val STARS = "stars:"
        private const val FINALE = "finale:"
        private const val ROAD_SAID = "roadSaid"
    }
}
