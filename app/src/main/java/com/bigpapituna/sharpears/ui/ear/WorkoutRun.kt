package com.bigpapituna.sharpears.ui.ear

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.bigpapituna.sharpears.R
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
    val started = remember { com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() }
    var seconds by remember { mutableIntStateOf(0) }
    // Integration: XP at the start, and the celebration waits one frame for the report so it can show the XP earned.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtStart = remember { progress?.xp ?: 0 }
    var reported by remember { mutableStateOf(false) }

    when {
        index >= plan.items.size -> {
            val total = WorkoutResult(results.toList())
            LaunchedEffect(Unit) {
                seconds = ((com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - started) / 1000).toInt()
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
    val line = if (index == 0) "Three quick games, about ${plan.minutes} minutes. First up: ${item.kind.title.lowerFirst()}."
        else results.last().let { "${if (it.score * 2 >= it.rounds) "Nice" else "Done"}, ${it.score} of ${it.rounds}. Next up: ${item.kind.title.lowerFirst()}." }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Text("Daily workout", style = MaterialTheme.typography.titleLarge, color = c.primary)
        PupSays(if (index == 0) Pose.GET_READY else Pose.GREAT_JOB, line, Modifier.widthIn(max = 560.dp))
        // The three games as tiles: the ones done ticked with their score, the next one raised.
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom) {
            plan.items.forEachIndexed { i, it ->
                val done = results.getOrNull(i)
                val now = i == index
                val lift by animateDpAsState(if (now) 10.dp else 0.dp, label = "lift")
                Column(Modifier.weight(1f).fillMaxHeight().offset { androidx.compose.ui.unit.IntOffset(0, -lift.roundToPx()) }.clip(RoundedCornerShape(18.dp))
                    .background(if (now) c.primaryContainer else c.surfaceContainerHigh)
                    .then(if (now) Modifier.border(2.dp, c.primary, RoundedCornerShape(18.dp)) else Modifier)
                    .graphicsLayer { alpha = if (done == null && !now) 0.7f else 1f }
                    .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHighest),
                        contentAlignment = Alignment.Center) {
                        EarArt(it.kind)
                        if (done != null) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(c.tertiary),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", tint = c.surface, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(it.kind.title, style = MaterialTheme.typography.titleSmall, color = if (now) c.onPrimaryContainer else c.onSurface, maxLines = 2)
                    Text(done?.let { r -> "${r.score}/${r.rounds}" } ?: "Level ${it.level + 1}, ${it.rounds} rounds",
                        style = Numeric.merge(if (done != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall),
                        color = if (done != null) c.tertiary else c.onSurfaceVariant)
                }
            }
        }
        ChunkyButton(onClick = onStart, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) { Text(if (index == 0) "Start" else "Next game") }
        TextButton(onClick = onExit) { Text("Leave workout") }
    }
}
