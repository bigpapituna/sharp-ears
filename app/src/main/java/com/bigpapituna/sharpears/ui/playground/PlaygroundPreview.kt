package com.bigpapituna.sharpears.ui.playground

import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.feedbackLine
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Cheer
import com.bigpapituna.sharpears.ui.CheerBar
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Embedded
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.theory.SceneBar
import com.bigpapituna.sharpears.ui.theory.SceneControls
import com.bigpapituna.sharpears.ui.theory.SceneStage
import com.bigpapituna.sharpears.ui.theory.pose
import com.bigpapituna.sharpears.ui.theory.rememberScenePlayback

/**
 * Debug (--es screen playground [--es lesson playground-n] [--ei step n] [--ei scene n] [--es inject ...] [--es profile id]):
 * opens a Playground lesson over the app, in [PlaygroundLessonPreview], until the integrator wires the stages into
 * LessonScreen. Called from MainActivity's debug route; Back closes it.
 */
fun showPlaygroundDebug(activity: ComponentActivity, intent: Intent, deps: LessonDeps, profile: Profile) {
    val lesson = PLAYGROUND.lessons.firstOrNull { it.id == intent.getStringExtra("lesson") } ?: PLAYGROUND.lessons.first()
    val step = intent.getIntExtra("step", 0)
    val scene = intent.getIntExtra("scene", 0)
    val inject = intent.getStringExtra("inject")
    Log.d(TAG, "playground: debug ${lesson.id} step ${step + 1} scene ${scene + 1} as ${profile.id}" + (inject?.let { " inject $it" } ?: ""))
    // After the app's own setContent, which would otherwise replace this view.
    activity.window.decorView.post {
        val view = ComposeView(activity)
        view.setContent {
            CompositionLocalProvider(LocalProfile provides profile) {
                SharpEarsTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PlaygroundLessonPreview(lesson, step, scene, deps, inject) { (view.parent as? ViewGroup)?.removeView(view) }
                    }
                }
            }
        }
        activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}

/**
 * A Playground lesson the way LessonScreen will show it once the stages are wired in (the reference for the
 * integrator): the steps under their dots, a reading step's scenes with the pup saying each caption, an exercise step
 * with the pup's instruction over either its Playground stage ([hasPlaygroundStage]) or the embedded player, and the
 * cheer bar after each try. Back steps back; Next skips ahead (it is a debug screen).
 */
@Composable
internal fun PlaygroundLessonPreview(lesson: CourseLesson, startStep: Int, startScene: Int, deps: LessonDeps, inject: String?, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(startStep.coerceIn(0, lesson.steps.lastIndex)) }
    var run by remember { mutableIntStateOf(0) }
    var bar by remember { mutableStateOf<Triple<Cheer, String, String?>?>(null) }
    val step = lesson.steps[index]
    val voice = deps.voice
    val context = LocalContext.current
    val clicks = remember { runCatching { Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release(); voice.stop() } }
    val reading = step.kind == StepKind.READ && step.scenes.isNotEmpty()
    val scenes = if (reading) key(index) {
        rememberScenePlayback(step.scenes, deps.sampler, clicks, voice, start = if (index == startStep) startScene else 0, tag = "playground ${lesson.id} step ${index + 1}")
    } else null
    LaunchedEffect(index, run) {
        Log.d(TAG, "playground: lesson ${lesson.id} step ${index + 1}/${lesson.steps.size} ${step.kind}" + (step.game?.let { " $it" } ?: ""))
        if (step.scenes.isEmpty()) voice.speak(step.text, interrupt = true)
    }
    fun go(to: Int) { bar = null; index = to.coerceIn(0, lesson.steps.lastIndex); run++ }
    fun ended(r: Report) {
        val passed = step.passed(r)
        val line = if (step.kind == StepKind.LISTEN) null else feedbackLine(r, perform = step.kind == StepKind.PERFORM, pass = step.exercise?.pass ?: 80,
            next = index < lesson.steps.lastIndex).text
        Log.d(TAG, "playground: lesson ${lesson.id} step ${index + 1} ended passed=$passed \"$line\"")
        if (step.kind == StepKind.LISTEN) { if (index < lesson.steps.lastIndex) go(index + 1); return }
        bar = if (passed) Triple(Cheer.GOOD, "Nice work!", line) else Triple(Cheer.MISS, "Not quite", line)
    }
    BackHandler { voice.stop(); onClose() }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { voice.stop(); onClose() }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to lessons") }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("${PLAYGROUND.title}, lesson ${PLAYGROUND.lessons.indexOf(lesson) + 1}", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(lesson.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.semantics { heading() })
                    }
                }
                Dots(lesson.steps.size, index, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
                if (scenes != null) {
                    Column(Modifier.weight(1f)) {
                        if (step.scenes.size > 1) SceneBar(step.scenes.size, scenes.index, scenes::fraction)
                        PupSays(scenes.scene.mood.pose(), scenes.scene.caption, Modifier.padding(top = 12.dp, bottom = 12.dp).heightIn(min = 72.dp), size = 72.dp)
                        SceneStage(scenes, Modifier.weight(1f), roomier = true)
                    }
                } else {
                    val ex = step.exercise!!
                    PupSays(if (step.kind == StepKind.LISTEN) Pose.LISTENING else Pose.PRACTICING, step.text,
                        Modifier.padding(horizontal = 4.dp).padding(bottom = 10.dp), size = 56.dp, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        key(index, run) {
                            val first = index == startStep && run == 0
                            if (hasPlaygroundStage(step)) PlaygroundStage(step, lesson.id, deps, inject?.takeIf { first }, ::ended,
                                Modifier.fillMaxWidth().heightIn(max = 560.dp).fillMaxHeight())
                            else SharpEarsTheme(dark = true) {
                                PlayerScreen(ex.toSong("${lesson.id}-${index + 1}", lesson.title), null, deps.sampler, deps.midi, deps.mic, voice, deps.prefs, deps.ready,
                                    0.0, false,
                                    PlayerStart(step.mode, ex.practice, mic = step.kind != StepKind.LISTEN && deps.prefs.micDefault && deps.mic.hasPermission(),
                                        inject = inject?.takeIf { first && step.kind != StepKind.LISTEN }, beatLines = ex.beatLines),
                                    onBack = onClose, onFinished = ::ended,
                                    embedded = Embedded(if (step.kind == StepKind.LISTEN) "Listen" else "The notes wait", labels = step.labels),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium))
                            }
                        }
                    }
                }
            }
            Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0 || (scenes?.index ?: 0) > 0) OutlinedButton(onClick = {
                    val sc = scenes
                    if (sc != null && sc.index > 0) sc.go(sc.index - 1) else go(index - 1)
                }) { Text("Back") }
                if (scenes != null) SceneControls(scenes, voice, "playground ${lesson.id}:")
                Spacer(Modifier.weight(1f))
                when {
                    scenes != null && !scenes.last -> ChunkyButton(onClick = { scenes.go(scenes.index + 1) }) { Text("Next") }
                    index < lesson.steps.lastIndex -> ChunkyButton(onClick = { go(index + 1) }) { Text("Next") }
                    else -> ChunkyButton(onClick = { voice.stop(); onClose() }) { Text("Finish") }
                }
            }
        }
        bar?.let { (cheer, title, line) ->
            CheerBar(cheer, title, line, if (cheer == Cheer.GOOD) "Continue" else "Try again", Modifier.align(Alignment.BottomCenter), onButton = {
                bar = null
                if (cheer == Cheer.GOOD) { if (index < lesson.steps.lastIndex) go(index + 1) else onClose() } else run++
            })
        }
    }
}

/** One dot per step, the current one a short bar (as LessonScreen's). */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(Modifier.height(8.dp).width(if (i == current) 22.dp else 8.dp).clip(CircleShape).background(when {
                i == current -> MaterialTheme.colorScheme.primary
                i < current -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.outlineVariant
            }))
        }
    }
}
