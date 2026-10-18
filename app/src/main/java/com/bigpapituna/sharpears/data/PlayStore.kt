package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
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

    fun quest(kid: Boolean, plan: SessionPlan?, day: LocalDate = LocalDate.now()): Quest = book.quest(day, kid, plan)

    fun recordQuest(event: QuestEvent, kid: Boolean, day: LocalDate = LocalDate.now()): List<QuestTask> =
        book.recordQuest(event, day, kid).also { done ->
            if (done.isNotEmpty()) Log.d(TAG, "play: quest $profileId ${done.joinToString { it.kind.name }} done ($event)")
        }

    fun pupSaysBest(day: LocalDate = LocalDate.now()): Int = book.pupSaysBest(day)

    fun savePupSays(best: Int, day: LocalDate = LocalDate.now()): PupSaysResult =
        book.savePupSays(profileId, best, day).also { Log.d(TAG, "play: pup says $profileId best $best, today ${it.best}") }

    companion object {
        /** The file's name in ProgressFiles terms (ask the integrator to add it to ProgressFiles.NAMES). */
        const val FILE = "play"
    }
}
