package com.bigpapituna.sharpears.ui.family

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressFiles
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.PlacementCheck
import com.bigpapituna.sharpears.engine.family.PlacementCheck.Result
import com.bigpapituna.sharpears.engine.family.PlacementCheck.Task
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Embedded
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.LessonDrop
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.playground.CopyStage
import com.bigpapituna.sharpears.ui.playground.KeyHuntStage
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import com.bigpapituna.sharpears.ui.skin.LocalColourNotes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The preference file the check plays against (the player writes practice time and the like; the check is nobody's practice). */
private const val SCRATCH_ID = "placement-check"

/**
 * "Let's hear you play" (2.6, Roy's decision): the short listen after a grown-up (or a parent, for a kid) said how much piano
 * has been played. The pup shows each thing first, the microphone listens with strict keys, every first key gets the stages'
 * glow, ring and hop in colour, and nothing is ever marked: no score, no pass or fail. Two things for [Level.A_LITTLE], three
 * for the rest (never for [Level.NEVER]); ninety seconds at most, "Skip" always in view. A task let go after three tries is
 * simply gone by ("Good try, let's keep going"). At the end the pup says where [who] will begin and that the earlier levels
 * stay open, over the first lesson's rewards ([award]: stars, the first sticker, sparkles toward an outfit).
 *
 * [onDone] gets the level to begin at and whether the check was played through. Skipping or quitting halfway gives
 * [said] back, silently, and no reward. The keys can be touched on screen as well, so a phone with no microphone allowed
 * still plays the check. [inject] is the debug microphone injection for the task opened first; [startTask] (debug) opens at that task.
 */
@Composable
fun PlacementCheckScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, ready: Boolean,
    who: Profile, said: Level,
    award: () -> Reward?,
    onDone: (begin: Level, played: Boolean) -> Unit,
    /** The label of the last button: where the pup takes them ("Start learning", "Show me the songs", "To the map"). */
    finishLabel: String = "Start learning",
    inject: String? = null, startTask: Int = 0,
) {
    val tasks = remember(said, who.kind) { PlacementCheck.tasksFor(if (who.isKid) said.forKid else said) }
    if (tasks.isEmpty()) {
        LaunchedEffect(Unit) { onDone(said, false) }
        return
    }
    val context = LocalContext.current
    val scratch = remember { Prefs(context, SCRATCH_ID) }
    DisposableEffect(Unit) { onDispose { context.deleteSharedPreferences(ProgressFiles.fileFor("progress", SCRATCH_ID)) } }
    val deps = remember(scratch, ready) { LessonDeps(sampler, midi, mic, voice, scratch, ready) }
    val quit by rememberUpdatedState { Log.d(TAG, "placement: check left, keeping ${said.name}"); voice.stop(); onDone(said, false) }
    BackHandler { quit() }

    // The microphone is asked for once, up front; allowed or not, the check plays (the keys can be touched).
    var settled by remember { mutableStateOf(mic.hasPermission() || inject != null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Log.d(TAG, "placement: microphone ${if (ok) "allowed" else "not allowed"}"); settled = true
    }
    LaunchedEffect(Unit) { if (!settled) askMic.launch(android.Manifest.permission.RECORD_AUDIO) }

    var index by remember { mutableIntStateOf(startTask.coerceIn(0, tasks.lastIndex)) }
    val results = remember { mutableStateListOf<Result>() }
    var rewardShown by remember { mutableStateOf<Reward?>(null) }
    var finished by remember { mutableStateOf(false) }
    val suggestion = remember(finished) {
        if (!finished) null else PlacementCheck.suggest(said, PlacementCheck.score(results.toList()), who.kind, tasks.size)
    }
    val scope = rememberCoroutineScope()
    fun ended(task: Task, r: Result) {
        Log.d(TAG, "placement: ${task.name.lowercase()} ${r.name.lowercase()}")
        results += r
        scope.launch {
            delay(1_100)
            if (index >= tasks.lastIndex) { rewardShown = award(); finished = true } else index++
        }
    }

    CompositionLocalProvider(LocalProfile provides who, LocalProgress provides null, LocalColourNotes provides true) {
        if (finished && suggestion != null) {
            Log.d(TAG, "placement: began at ${suggestion.level} (said ${suggestion.said}, ${suggestion.score} of ${tasks.size})")
            Celebration(
                title = "Lovely playing!", line = suggestion.line, stats = emptyList(),
                primary = finishLabel, onPrimary = { onDone(suggestion.level, true) },
                sampler = sampler.takeIf { scratch.sound },
                reward = rewardShown?.let { rw -> { LessonDrop(rw, stars = 3, sampler = sampler.takeIf { scratch.sound }, say = { voice.speak(it) }) } },
            )
            return@CompositionLocalProvider
        }
        Column(Modifier.fillMaxSize().paper().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = quit) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Let's hear you play", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Dots(tasks.size, index)
                    // Always in view: nobody has to do this.
                    OutlinedButton(onClick = quit, modifier = Modifier.padding(start = 12.dp)) { Text("Skip") }
                }
                val task = tasks[index]
                PupSays(Pose.PRACTICING, task.ask, Modifier.padding(top = 6.dp, bottom = 10.dp), size = 56.dp, style = MaterialTheme.typography.bodyLarge)
                SharpEarsTheme(dark = true) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Stage)) {
                        if (settled) key(index) {
                            val first = inject?.takeIf { index == startTask }
                            when (task) {
                                Task.FIND_C -> KeyHuntStage(Step(StepKind.PLAY, task.ask, PlacementCheck.FIND_C_EXERCISE), "placement-find-c", deps, first, { ended(task, resultOf(it)) })
                                Task.FIVE_NOTES -> CopyStage(StageGame.Copy(Place.TREEHOUSE), Step(StepKind.PLAY, task.ask, PlacementCheck.FIVE_NOTES_EXERCISE), "placement-five", deps, first, { ended(task, resultOf(it)) })
                                Task.TWINKLE -> BandTask(deps, first) { ended(task, it) }
                            }
                        }
                    }
                }
                Box(Modifier.height(12.dp))
            }
        }
    }
}

/** A stage's report as the check keeps it: a round let go after three tries is gone by, anything else was played. */
private fun resultOf(r: Report): Result = if (r.letGoRounds > 0) Result.LET_GO else Result.PLAYED

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.semantics { contentDescription = "Part ${current + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(Modifier.height(8.dp).width(if (i == current) 22.dp else 8.dp).clip(CircleShape).background(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

/**
 * Twinkle's first phrase with the band at 70 bpm, right hand: the pup plays it first ("Let me show you", the embedded player in
 * Listen), then it runs in time (Perform) with the band under it. Strict keys, as the stages. A run that did not come together
 * goes round again ("Once more, with the band!"); after three the pup lets it go ("Good try, let's keep going"). The
 * accuracy decides whether it counts as played; it is never shown.
 */
@Composable
private fun BandTask(deps: LessonDeps, inject: String?, onEnded: (Result) -> Unit) {
    val ex = PlacementCheck.TWINKLE_EXERCISE
    var shown by remember { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    var say by remember { mutableStateOf<String?>(null) }
    val ended by rememberUpdatedState(onEnded)
    DisposableEffect(Unit) {
        deps.mic.matcher.strictKeys = true
        onDispose { deps.mic.matcher.strictKeys = false }
    }
    Column(Modifier.fillMaxSize()) {
        say?.let { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = androidx.compose.ui.graphics.Color(0xFFF3EEE9), style = MaterialTheme.typography.bodyLarge) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!shown) {
                LaunchedEffect(Unit) { deps.voice.speak(PlaygroundLines.LET_ME_SHOW) }
                PlayerScreen(ex.toSong("placement-band-demo", "Twinkle, Twinkle"), null, deps.sampler, deps.midi, deps.mic, deps.voice, deps.prefs, deps.ready,
                    0.0, false, PlayerStart(Mode.LISTEN, Practice.BOTH, mic = false), onBack = {},
                    onFinished = { deps.voice.speak(PlaygroundLines.YOUR_TURN); shown = true },
                    embedded = Embedded("Listen first"), modifier = Modifier.fillMaxSize())
            } else key(tries) {
                PlayerScreen(ex.toSong("placement-band-$tries", "Twinkle, Twinkle"), null, deps.sampler, deps.midi, deps.mic, deps.voice, deps.prefs, deps.ready,
                    0.0, false, PlayerStart(Mode.PERFORM, Practice.RIGHT, mic = deps.mic.hasPermission() || inject != null, inject = inject?.takeIf { tries == 0 }),
                    onBack = {},
                    onFinished = { r ->
                        val accuracy = r.recap?.accuracy ?: 0
                        Log.d(TAG, "placement: band run ${tries + 1}: ${accuracy}% (counts at ${ex.pass}%)")
                        when {
                            accuracy >= ex.pass -> ended(Result.PLAYED)
                            tries + 1 >= PlacementCheck.TRIES -> { deps.voice.speak(PlaygroundLines.LET_GO); ended(Result.LET_GO) }
                            else -> { say = "Once more, with the band!"; tries++ }
                        }
                    },
                    embedded = Embedded("With the band"), modifier = Modifier.fillMaxSize())
            }
        }
    }
}
