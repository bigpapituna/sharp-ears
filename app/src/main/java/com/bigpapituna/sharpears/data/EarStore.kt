package com.bigpapituna.sharpears.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.ear.EarProgress
import com.bigpapituna.sharpears.engine.ear.Workout
import java.time.LocalDate

/**
 * Ear training results in their own SharedPreferences file "ear": best score per game and level
 * (best:<game>:<level>, clean rounds of 10), when each game was last played (last:<game>, epoch day), how often
 * (plays:<game>), and the daily workout (workoutDays: the last 60 days with one, as comma separated epoch days;
 * workoutCount; workoutScore: "score/rounds" of the latest one). [version] bumps on every write so screens that read
 * it recompose.
 */
class EarStore(context: Context) : EarProgress {
    private val p = context.getSharedPreferences("ear", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)

    override fun best(kind: EarKind, level: Int) = p.getInt("best:${kind.id}:$level", -1)

    /** Saves [score] when it beats the best; true only for a real new best (above zero, above the old best). */
    fun saveBest(kind: EarKind, level: Int, score: Int): Boolean {
        val old = best(kind, level)
        if (score <= old) return false
        edit { putInt("best:${kind.id}:$level", score) }
        return score > 0
    }

    override fun lastPlayedDay(kind: EarKind) = p.getLong("last:${kind.id}", -1L)
    fun plays(kind: EarKind) = p.getInt("plays:${kind.id}", 0)

    /** A full game of [kind] was played today (in the hub or in the workout). */
    fun played(kind: EarKind, day: Long = today()) = edit {
        putLong("last:${kind.id}", day)
        putInt("plays:${kind.id}", plays(kind) + 1)
    }

    fun workoutDays(): Set<Long> = p.getString("workoutDays", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
    fun workoutsDone() = p.getInt("workoutCount", 0)
    fun workoutDoneToday(day: Long = today()) = day in workoutDays()
    fun workoutStreak(day: Long = today()) = Workout.streak(workoutDays(), day)
    /** "13/20" for the latest workout, or null before the first. */
    fun lastWorkout(): String? = p.getString("workoutScore", null)

    fun recordWorkout(score: Int, rounds: Int, day: Long = today()) = edit {
        val days = (workoutDays() + day).filter { it > day - 60 }.sorted()
        putString("workoutDays", days.joinToString(","))
        putInt("workoutCount", workoutsDone() + 1)
        putString("workoutScore", "$score/$rounds")
    }

    /** Forgets every score and workout (for a "reset progress" in Settings). */
    fun reset() = edit { clear() }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version.intValue++
    }

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}
