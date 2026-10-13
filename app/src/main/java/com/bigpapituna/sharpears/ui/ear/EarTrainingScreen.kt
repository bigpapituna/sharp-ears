package com.bigpapituna.sharpears.ui.ear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.EarStore
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.engine.ear.Workout
import com.bigpapituna.sharpears.engine.ear.WorkoutPlan
import com.bigpapituna.sharpears.engine.ear.newTrainer
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Stat

/** Where the hub opens: straight into a game (or the workout), for debugging and for deep links from the course. */
data class EarStart(val kind: EarKind? = null, val level: Int = 0, val workout: Boolean = false)

private sealed interface Mode {
    data object Hub : Mode
    data class Game(val kind: EarKind, val level: Int, val session: Int) : Mode
    data class Daily(val plan: WorkoutPlan) : Mode
}

/**
 * Ear training: the hub (daily workout, then the five games with their levels and best scores), the game screens and
 * the workout. [sound] says whether the app may play sound (Prefs.sound); [onReport] gets every [EarEvent] for XP and
 * goals; [onBack] leaves to wherever the hub was opened from. See ui/ear/INTEGRATION.md.
 */
@Composable
fun EarTrainingScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, micDefault: Boolean,
    onBack: () -> Unit, onReport: (EarEvent) -> Unit = {},
    sound: () -> Boolean = { true }, start: EarStart? = null,
) {
    val context = LocalContext.current
    val store = remember { EarStore(context) }
    val clicks = remember { Clicks(context) }
    DisposableEffect(Unit) { onDispose { clicks.release() } }
    val soundNow by rememberUpdatedState(sound)
    val reportNow by rememberUpdatedState(onReport)
    val env = remember(sampler, midi, mic, voice, micDefault) {
        EarEnv(sampler, midi, mic, voice, micDefault, clicks, { soundNow() }, { reportNow(it) })
    }
    var mode by remember {
        mutableStateOf<Mode>(when {
            start?.workout == true -> Mode.Daily(Workout.plan(store, EarStore.today()))
            start?.kind != null -> Mode.Game(start.kind, start.level.coerceIn(0, 2), 0)
            else -> Mode.Hub
        })
    }
    val now = mode
    BackHandler { voice.stop(); if (now is Mode.Hub) onBack() else mode = Mode.Hub }
    when (now) {
        Mode.Hub -> Hub(store, onBack, onGame = { k, l -> mode = Mode.Game(k, l, 0) }, onWorkout = { mode = Mode.Daily(Workout.plan(store, EarStore.today())) })
        is Mode.Game -> androidx.compose.runtime.key(now) {
            GameRun(now.kind, now.level, env, store, onExit = { voice.stop(); mode = Mode.Hub },
                onAgain = { voice.stop(); mode = now.copy(session = now.session + 1) })
        }
        is Mode.Daily -> WorkoutRun(now.plan, env, store, onExit = { voice.stop(); mode = Mode.Hub })
    }
}

/** One game: the screen, then the celebration with the score, best streak and "New best". */
@Composable
private fun GameRun(kind: EarKind, level: Int, env: EarEnv, store: EarStore, onExit: () -> Unit, onAgain: () -> Unit) {
    val trainer = remember { newTrainer(kind, level) }
    val started = remember { android.os.SystemClock.elapsedRealtime() }
    var newBest by remember { mutableStateOf<Boolean?>(null) }
    val done = newBest
    if (done == null) {
        TrainerHost(trainer, env, null, onExit) {
            val best = store.saveBest(kind, level, trainer.score)
            store.played(kind)
            env.report(EarEvent.GameComplete(kind, level, trainer.score, trainer.rounds, trainer.bestStreak, best,
                ((android.os.SystemClock.elapsedRealtime() - started) / 1000).toInt()))
            env.voice.speak("${trainer.score} out of ${trainer.rounds}." + if (best) " A new best." else "")
            newBest = best
        }
        return
    }
    val ok = trainer.score * 2 >= trainer.rounds
    Celebration(
        when { done -> "New best!"; ok -> "Nice playing!"; else -> "Good effort!" },
        "${kind.title}, ${kind.levels[level].lowerFirst()}.",
        listOf(Stat("${trainer.score}/${trainer.rounds}", "Score"), Stat("${trainer.bestStreak}", "Best streak")),
        primary = "Play again", onPrimary = onAgain, secondary = "Ear training", onSecondary = onExit,
        pose = when { done -> Pose.LEVEL_UP; ok -> Pose.GREAT_JOB; else -> Pose.MISTAKE },
        sampler = env.sampler.takeIf { env.sound() && ok },
    )
}

@Composable
private fun Hub(store: EarStore, onBack: () -> Unit, onGame: (EarKind, Int) -> Unit, onWorkout: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val v = store.version.intValue
    val today = EarStore.today()
    // Each game opens on the first level not yet mastered, the one the workout would pick.
    val levels = remember { mutableStateMapOf<EarKind, Int>() }
    val plan = remember(v) { Workout.plan(store, today) }
    val doneToday = remember(v) { store.workoutDoneToday(today) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                Text("Ear training", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
            }
            // The one raised panel: today's workout.
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.surfaceContainerHigh, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Daily workout", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                PupSays(if (doneToday) Pose.RESTING else Pose.GET_READY,
                    if (doneToday) "Done for today. Go again if you like." else "Three games, about five minutes, picked for you.")
                Text(plan.items.joinToString(", ") { it.kind.title }, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                val streak = store.workoutStreak(today)
                val last = store.lastWorkout()
                if (streak > 0 || last != null) Text(
                    listOfNotNull(if (streak > 0) "$streak ${if (streak == 1) "day" else "days"} in a row" else null, last?.let { "Last score $it" }).joinToString(". ") + ".",
                    style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.primary)
                ChunkyButton(onClick = onWorkout, modifier = Modifier.fillMaxWidth()) { Text(if (doneToday) "Do it again" else "Start workout") }
            }
            EarKind.entries.forEach { kind ->
                val level = levels[kind] ?: Workout.levelFor(store, kind)
                Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(kind.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(kind.howToFor(level), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kind.levels.forEachIndexed { i, name ->
                            val mastered = store.best(kind, i) >= Workout.MASTERED
                            FilterChip(level == i, { levels[kind] = i }, label = { Text("${i + 1}: $name") },
                                leadingIcon = if (mastered) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = "Mastered", modifier = Modifier.size(18.dp)) }) else null)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val best = store.best(kind, level)
                        Text(if (best < 0) "Not played yet" else "Best $best of 10", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                            color = if (best >= 0) c.primary else c.onSurfaceVariant, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { onGame(kind, level) }) { Text("Play") }
                    }
                }
            }
        }
    }
}
