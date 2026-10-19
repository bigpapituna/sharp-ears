package com.bigpapituna.sharpears.ui.ear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.bigpapituna.sharpears.ui.Pup
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
    val profileId = com.bigpapituna.sharpears.ui.family.LocalProfile.current.id
    val store = remember(profileId) { EarStore(context, profileId) }
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
    // Integration: the XP this game earned (rounds and the game, reported through env.report) on the celebration.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtStart = remember { progress?.xp ?: 0 }
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
        newBest = done,
        xpGained = progress?.let { (it.xp - xpAtStart).takeIf { g -> g > 0 } },
        xpBar = progress?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(xpAtStart, it.xp) },
    )
}

/**
 * The hub: today's workout as the hero panel (the pup, the streak, the three games as little tiles, one big
 * button), then the five games as a grid of cards. A card opens its sheet (how to play, level, Play); its round
 * button plays the level it shows straight away.
 */
@Composable
private fun Hub(store: EarStore, onBack: () -> Unit, onGame: (EarKind, Int) -> Unit, onWorkout: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val v = store.version.intValue
    val today = EarStore.today()
    // Each game opens on the first level not yet mastered, the one the workout would pick, until another is chosen.
    val levels = remember { mutableStateMapOf<EarKind, Int>() }
    var sheet by remember { mutableStateOf<EarKind?>(null) }
    val plan = remember(v) { Workout.plan(store, today) }
    val doneToday = remember(v) { store.workoutDoneToday(today) }
    val streak = remember(v) { store.workoutStreak(today) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                Text("Ear training", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
            }
            WorkoutHero(plan, doneToday, streak, store.lastWorkout(), onWorkout, Modifier.padding(top = 8.dp))
            Text("Games", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp).semantics { heading() })
            CardGrid(EarKind.entries.size) { i, m ->
                val kind = EarKind.entries[i]
                val level = levels[kind] ?: Workout.levelFor(store, kind)
                GameCard(kind.title, level, kind.levels.indices.map { store.best(kind, it) >= STAR_AT }, store.best(kind, level),
                    onOpen = { sheet = kind }, onPlay = { onGame(kind, level) }, modifier = m) { EarArt(kind) }
            }
        }
    }
    sheet?.let { kind ->
        val level = levels[kind] ?: Workout.levelFor(store, kind)
        GameSheet(kind.title, kind::howToFor, kind.levels, kind.levels.indices.map { store.best(kind, it) }, level,
            onPlay = { l -> levels[kind] = l; sheet = null; onGame(kind, l) }, onDismiss = { sheet = null }) { EarArt(kind, big = true) }
    }
}

/** The daily workout: the one raised panel, in the pup's colours, with the streak and the three games of the plan. */
@Composable
private fun WorkoutHero(plan: WorkoutPlan, doneToday: Boolean, streak: Int, last: String?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val minutes = plan.minutes
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.primaryContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(if (doneToday) Pose.RESTING else Pose.GET_READY, size = 92.dp, onTap = {})
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily workout", style = MaterialTheme.typography.headlineSmall, color = c.onPrimaryContainer,
                    modifier = Modifier.semantics { heading() })
                Text(if (doneToday) "Done for today. Fancy another round?" else "Three games, about $minutes minutes, picked for you.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer)
                if (last != null && doneToday) Text("Today's score $last", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.primary)
            }
            if (streak > 0) StreakBadge(streak)
        }
        Text(if (doneToday) "Next workout" else "Today", style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer.copy(alpha = 0.75f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.items.forEach { item ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLowest.copy(alpha = 0.7f)).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        EarArt(item.kind)
                    }
                    Text(item.kind.title, style = MaterialTheme.typography.labelLarge, color = c.onSurface, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
                    Text("${item.rounds} rounds", style = Numeric.merge(MaterialTheme.typography.labelSmall), color = c.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
        ChunkyButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(if (doneToday) "Do it again" else "Start workout") }
    }
}

/** Days in a row with a workout: a big number in a ring, or a nudge to start one. */
@Composable
private fun StreakBadge(streak: Int) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(if (streak > 0) c.primary else c.surfaceContainerLowest.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center) {
            Text("$streak", style = Numeric.merge(MaterialTheme.typography.headlineSmall).copy(fontWeight = FontWeight.SemiBold),
                color = if (streak > 0) c.onPrimary else c.onSurfaceVariant)
        }
        Text(if (streak == 1) "day" else "days", style = MaterialTheme.typography.labelSmall, color = c.onPrimaryContainer)
    }
}
