package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.theory.TheoryProgress
import com.bigpapituna.sharpears.engine.theory.TheoryUnit
import com.bigpapituna.sharpears.engine.theory.percent
import com.bigpapituna.sharpears.engine.theory.theoryLesson

/** What finishing a quiz changed: its score, whether it beat the best, and the unit it completed (first time only). */
data class TheoryFinish(val percent: Int, val newBest: Boolean, val unitCompleted: TheoryUnit?)

/**
 * The Theory Academy's progress, in its own SharedPreferences file "theory": per lesson whether it is done
 * (`done:<id>`), the best quiz score in percent (`best:<id>`) and seconds spent (`secs:<id>`). [version] bumps on
 * every change so screens reading it recompose. Per profile ("theory.<id>", [ProgressFiles.fileFor]).
 */
class TheoryStore(context: Context, profileId: String) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor("theory", profileId), Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)

    fun done(lessonId: String) = p.getBoolean("done:$lessonId", false)
    fun best(lessonId: String): Int? = p.getInt("best:$lessonId", -1).takeIf { it >= 0 }
    fun seconds(lessonId: String) = p.getInt("secs:$lessonId", 0)
    fun unitSeconds(u: TheoryUnit) = u.lessons.sumOf { seconds(it.id) }
    fun progress() = TheoryProgress(::done, ::best)

    /** A lesson's quiz ended with [correct] of [total] right on the first try, [seconds] after the lesson opened. */
    fun finish(lessonId: String, correct: Int, total: Int, seconds: Int): TheoryFinish {
        val unit = theoryLesson(lessonId)?.first ?: error("no theory lesson $lessonId")
        val wasDone = progress().unitDone(unit)
        val pct = percent(correct, total)
        val old = best(lessonId)
        val newBest = old == null || pct > old
        p.edit {
            putBoolean("done:$lessonId", true)
            if (newBest) putInt("best:$lessonId", pct)
            putInt("secs:$lessonId", seconds(lessonId) + seconds)
        }
        version.intValue++
        val completed = unit.takeIf { !wasDone && progress().unitDone(it) }
        Log.d(TAG, "theory: $lessonId quiz $correct/$total ($pct%) in ${seconds}s" + (completed?.let { ", unit ${it.id} complete" } ?: ""))
        return TheoryFinish(pct, newBest, completed)
    }

    /** Clears all theory progress. */
    fun reset() { p.edit { clear() }; version.intValue++; Log.d(TAG, "theory: progress reset") }
}

/** The current profile's theory store, one per composition (the preferences file is shared anyway). */
@Composable
fun rememberTheoryStore(): TheoryStore {
    val ctx = LocalContext.current.applicationContext
    val id = com.bigpapituna.sharpears.ui.family.LocalProfile.current.id
    return remember(ctx, id) { TheoryStore(ctx, id) }
}
