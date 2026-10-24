package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.play.PlayBook
import com.bigpapituna.sharpears.engine.play.PlayStorage
import com.bigpapituna.sharpears.engine.play.PupSaysResult
import com.bigpapituna.sharpears.engine.play.Quest
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.play.QuestTask
import java.time.LocalDate

/**
 * The Play corner's per-profile file ("play", "play.p3" for others): today's quest and the day's best in Pup says.
 * The rules live in engine/play/PlayBook.kt. The integrator adds "play" to ProgressFiles.NAMES so backup, import and
 * profile deletion cover it.
 */
class PlayStore(context: Context, val profileId: String) {
    private val prefs = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)
    private val book = PlayBook(object : PlayStorage {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    })

    /** Today's quest; [game] (2.7) is the arcade's pick for a kid's game task, made the first time the day's quest is. */
    fun quest(kid: Boolean, plan: SessionPlan?, day: LocalDate = AppClock.today(), game: Pair<String, String>? = null): Quest = book.quest(day, kid, plan, game)

    fun recordQuest(event: QuestEvent, kid: Boolean, day: LocalDate = AppClock.today()): List<QuestTask> =
        book.recordQuest(event, day, kid).also { done ->
            if (done.isNotEmpty()) Log.d(TAG, "play: quest $profileId ${done.joinToString { it.kind.name }} done ($event)")
            done.forEach { t -> Briefs.logFor(profileId) { com.bigpapituna.sharpears.engine.brief.BriefEvent.QuestStar(it, t.kind.name) } }
        }

    fun pupSaysBest(day: LocalDate = AppClock.today()): Int = book.pupSaysBest(day)

    fun savePupSays(best: Int, day: LocalDate = AppClock.today()): PupSaysResult =
        book.savePupSays(profileId, best, day).also { Log.d(TAG, "play: pup says $profileId best $best, today ${it.best}") }

    /** 2.9: the last-played day of the return the comeback card was shown for (engine/Comeback.due), once per return. */
    var comebackGreeted: LocalDate?
        get() = prefs.getString(KEY_COMEBACK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) { prefs.edit { if (v == null) remove(KEY_COMEBACK) else putString(KEY_COMEBACK, v.toString()) } }

    /** 2.9: the latest rest day already counted for the tester dashboard's rest_day_used (once per rest day). */
    var restDayCounted: LocalDate?
        get() = prefs.getString(KEY_REST, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) { prefs.edit { if (v == null) remove(KEY_REST) else putString(KEY_REST, v.toString()) } }

    companion object {
        private const val KEY_COMEBACK = "comebackGreeted"
        private const val KEY_REST = "restDayCounted"
        /** The file's name in ProgressFiles terms (ask the integrator to add it to ProgressFiles.NAMES). */
        const val FILE = "play"
    }
}
