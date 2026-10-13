package com.bigpapituna.sharpears.ui.ear

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.EarStore
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.ear.EarTrainer
import com.bigpapituna.sharpears.engine.ear.RhythmTapGame
import com.bigpapituna.sharpears.engine.ear.Workout
import com.bigpapituna.sharpears.engine.ear.WorkoutGame
import com.bigpapituna.sharpears.engine.ear.WorkoutPlan
import com.bigpapituna.sharpears.engine.ear.WorkoutResult
import com.bigpapituna.sharpears.engine.ear.newTrainer
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Stat

/** Runs whichever screen fits the game: the tap pad for rhythm, keys and buttons for the rest. */
@Composable
internal fun TrainerHost(trainer: EarTrainer, env: EarEnv, label: String?, onExit: () -> Unit, onFinished: () -> Unit) {
    if (trainer is RhythmTapGame) RhythmPlay(trainer, env, label, onExit, onFinished) else KeysPlay(trainer, env, label, onExit, onFinished)
}

private fun clock(seconds: Int) = "${seconds / 60}:${"%02d".format(seconds % 60)}"

/**
 * The daily workout: the games of [plan] back to back, each started from a short "next up" screen, and one
 * Celebration at the end with the total. The result goes to [store] (and the workout event to the app) once.
 */
@Composable
internal fun WorkoutRun(plan: WorkoutPlan, env: EarEnv, store: EarStore, onExit: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var upNext by remember { mutableStateOf(true) }
    val results = remember { mutableStateListOf<WorkoutGame>() }
    val started = remember { SystemClock.elapsedRealtime() }
    var seconds by remember { mutableIntStateOf(0) }
    // Integration: XP at the start, and the celebration waits one frame for the report so it can show the XP earned.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtStart = remember { progress?.xp ?: 0 }
    var reported by remember { mutableStateOf(false) }

    when {
        index >= plan.items.size -> {
            val total = WorkoutResult(results.toList())
            LaunchedEffect(Unit) {
                seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
                store.recordWorkout(total.score, total.rounds)
                env.report(EarEvent.Workout(results.size, total.score, total.rounds, total.bestStreak, seconds))
                reported = true
            }
            if (!reported) return
            val good = total.score * 2 >= total.rounds
            @Suppress("UNUSED_VARIABLE") val saved = store.version.intValue          // read so the streak line updates once today is saved
            Celebration(
                Workout.headline(total.score, total.rounds),
                "${results.size} games done" + (store.workoutStreak().takeIf { it > 1 }?.let { ", $it days in a row" } ?: "") + ".",
                listOf(Stat("${total.score}/${total.rounds}", "Score"), Stat("${total.bestStreak}", "Best streak"),
                    Stat(clock(seconds), "Time")),
                primary = "Done", onPrimary = onExit,
                pose = if (total.score * 10 >= total.rounds * 8) Pose.LEVEL_UP else if (good) Pose.GREAT_JOB else Pose.MISTAKE,
                sampler = env.sampler.takeIf { env.sound() && good },
                xpGained = progress?.let { (it.xp - xpAtStart).takeIf { g -> g > 0 } },
                xpBar = progress?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(xpAtStart, it.xp) },
            )
        }
        upNext -> UpNext(plan, index, results, onStart = { upNext = false }, onExit = onExit)
        else -> {
            val item = plan.items[index]
            val trainer = remember(index) { newTrainer(item.kind, item.level, rounds = item.rounds) }
            TrainerHost(trainer, env, "Game ${index + 1} of ${plan.items.size}", onExit) {
                results += WorkoutGame(item.kind, item.level, trainer.score, trainer.rounds, trainer.bestStreak)
                store.played(item.kind)
                index++; upNext = true
            }
        }
    }
}

/** Between games: who is next, the list with the scores so far, and one button. */
@Composable
private fun UpNext(plan: WorkoutPlan, index: Int, results: List<WorkoutGame>, onStart: () -> Unit, onExit: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val item = plan.items[index]
    val line = if (index == 0) "Three quick games, about five minutes. First up: ${item.kind.title.lowerFirst()}."
        else results.last().let { "${if (it.score * 2 >= it.rounds) "Nice" else "Done"}, ${it.score} of ${it.rounds}. Next up: ${item.kind.title.lowerFirst()}." }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Text("Daily workout", style = MaterialTheme.typography.titleLarge, color = c.primary)
        PupSays(if (index == 0) Pose.GET_READY else Pose.GREAT_JOB, line, Modifier.widthIn(max = 520.dp))
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().background(c.surfaceContainerHigh, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.items.forEachIndexed { i, it ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.kind.title, style = MaterialTheme.typography.titleMedium, color = if (i == index) c.primary else c.onSurface)
                        Text("Level ${it.level + 1}: ${it.kind.levels[it.level].lowerFirst()}, ${it.rounds} rounds", style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant)
                    }
                    results.getOrNull(i)?.let { r -> Text("${r.score}/${r.rounds}", style = Numeric.merge(MaterialTheme.typography.titleMedium), color = c.onSurface) }
                }
            }
        }
        Spacer(Modifier.padding(2.dp))
        ChunkyButton(onClick = onStart, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) { Text(if (index == 0) "Start" else "Next game") }
        TextButton(onClick = onExit) { Text("Leave workout") }
    }
}
