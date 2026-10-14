package com.bigpapituna.sharpears.ui

import android.util.Log
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.feedbackLine
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.theory.SceneBar
import com.bigpapituna.sharpears.ui.theory.SceneControls
import com.bigpapituna.sharpears.ui.theory.ScenePreview
import com.bigpapituna.sharpears.ui.theory.SceneStage
import com.bigpapituna.sharpears.ui.theory.pose
import com.bigpapituna.sharpears.ui.theory.rememberScenePlayback

/** Everything a lesson needs from the activity. */
class LessonDeps(val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val prefs: Prefs, val ready: Boolean)

/**
 * One lesson, a step at a time, under a row of progress dots, with Back and Next. A reading step is a short
 * explainer (2.0.1): the pup says each scene's caption (aloud too) over its animated picture on the stage, the
 * scenes playing one after another; Pause and Watch again work as in Theory, and Next moves through the scenes and
 * then on. An exercise step shows the pup with its instruction (read aloud) over the embedded player and completes
 * itself when its run ends (a Perform step at its pass mark); then the feedback line is spoken and the next step
 * opens. The lesson is done when its last step completes. [inject] (debug) feeds the mic for the step opened first.
 */
@Composable
fun LessonScreen(
    lesson: CourseLesson, startStep: Int, deps: LessonDeps, inject: String?,
    /** Debug: MIDI input played into the first step (see MidiDevices.debugPlay). */
    midiScript: String? = null,
    /** Debug (--ei scene n): the scene a reading step opened first starts on. */
    startScene: Int = 0,
    onStep: (Int) -> Unit, onSong: (() -> Unit)?, onDone: () -> Unit, onNext: (() -> Unit)?, onBack: () -> Unit,
) {
    val (level, _) = ALL.first { it.second == lesson }
    var index by remember { mutableIntStateOf(startStep.coerceIn(0, lesson.steps.lastIndex)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }              // bumps to restart the step's player
    var advancedByItself by remember { mutableStateOf(false) }
    var reaction by remember { mutableStateOf<Pose?>(null) }     // the pup's answer to the last try, for a few seconds
    var fails by remember { mutableIntStateOf(0) }               // tries of this step that did not pass
    LaunchedEffect(reaction) { if (reaction != null) { kotlinx.coroutines.delay(4000); reaction = null } }
    // The bar after a try (the pup's verdict and one button), and what the finish celebration counts.
    var bar by remember { mutableStateOf<Triple<Cheer, String, String?>?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var cheers by remember { mutableIntStateOf(lesson.id.hashCode()) }
    val openedAt = remember { System.currentTimeMillis() }
    // XP: each step passed is reported here, the lesson itself by onDone (MainActivity); the celebration shows the sum.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtOpen = remember { progress?.xp ?: 0 }
    var xpAtEnd by remember { mutableIntStateOf(0) }
    var tookSecs by remember { mutableStateOf(0L) }
    val step = lesson.steps[index]
    val last = index == lesson.steps.lastIndex
    val voice = deps.voice
    val context = androidx.compose.ui.platform.LocalContext.current
    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release() } }
    // A reading step plays its scenes; the pup says each caption as it comes (queued behind a feedback line after
    // an automatic advance).
    val reading = step.kind == StepKind.READ && step.scenes.isNotEmpty() && !finished
    val scenes = if (reading) key(index) {
        rememberScenePlayback(step.scenes, deps.sampler, clicks, voice, start = if (index == startStep) startScene else 0,
            tag = "lesson ${lesson.id} step ${index + 1}", interruptFirst = !advancedByItself)
    } else null

    // Read each exercise step out as it opens. After an automatic advance the feedback line is already queued, so no interrupt.
    LaunchedEffect(index) {
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1}/${lesson.steps.size} ${step.kind}")
        onStep(index)
        if (step.scenes.isEmpty()) voice.speak(step.text.replace("\n\n", " "), interrupt = !advancedByItself)
        advancedByItself = false
    }

    fun go(to: Int) { feedback = null; bar = null; finished = false; index = to; fails = 0 }
    fun complete() {
        progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.LessonStepPassed)
        if (last) {
            finished = true
            tookSecs = (System.currentTimeMillis() - openedAt) / 1000
            onDone()
            xpAtEnd = progress?.xp ?: 0
            Log.d(TAG, "lesson ${lesson.id} done")
            voice.speak("Lesson complete.")
        } else { advancedByItself = true; feedback = null; index++ }
    }
    fun exerciseEnded(s: Step, report: Report) {
        val passed = s.passed(report)
        // A Listen step has nothing to judge; Play and Perform steps get one line naming the main mistake.
        val line = if (s.kind == StepKind.LISTEN) null
            else if (s.kind.needsMidi) (report.extraScore ?: 0).let { score ->
                val what = if (s.kind == StepKind.PEDAL) "Pedal changes in time" else "Notes at the right loudness"
                val text = "$what: $score percent. " + if (passed) (if (last) "Passed." else "Passed. On to the next step.") else "You need ${s.exercise?.pass ?: 80}. Play it again."
                com.bigpapituna.sharpears.engine.Feedback(text, text)
            }
            else if (s.kind == StepKind.RAMP) com.bigpapituna.sharpears.engine.Feedback("Full speed. " + if (last) "Lesson done." else "On to the next step.",
                "Full speed. " + if (last) "Lesson done." else "On to the next step.")
            else feedbackLine(report, perform = s.kind == StepKind.PERFORM, pass = s.exercise?.pass ?: 80, next = !last, flats = s.exercise?.flats == true)
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1} ended passed=$passed feedback=\"${line?.text}\"")
        feedback = line?.text
        line?.let { voice.speak(it.spoken) }
        if (s.kind != StepKind.LISTEN) reaction = if (passed) Pose.GREAT_JOB else Pose.MISTAKE
        if (s.kind == StepKind.LISTEN) { if (passed) complete(); return }
        if (!passed) { fails++; retries++; bar = Triple(Cheer.MISS, "Not quite", line?.text); return }
        // Passing a step that took a few tries earns the high-five.
        val highFive = fails >= 2
        if (highFive) { voice.speak(com.bigpapituna.sharpears.engine.HIGH_FIVE); Log.d(TAG, "pup: high five after $fails tries") }
        fails = 0
        // The last step goes straight to the celebration; any other waits on Continue.
        if (last) complete() else bar = Triple(Cheer.GOOD, if (highFive) "High five!" else cheerTitle(cheers++),
            if (highFive) "You stuck with it. " + (line?.text ?: "") else line?.text)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { voice.stop(); onBack() }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to lessons") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("${level.title}, lesson ${ALL.indexOfFirst { it.second == lesson } + 1}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.semantics { heading() })
                }
            }
            StepDots(lesson.steps.size, index, finished, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
            if (finished) {
                // The celebration covers the screen (below).
            } else if (step.kind.needsMidi && deps.midi.connected.value == null) {
                // No keyboard: the pup says why over a keyboard being plugged in; the step starts once one is, or is skipped.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PupSays(Pose.LEARNING, if (deps.prefs.micDefault) "This step needs a MIDI keyboard. The microphone hears which notes you play, not the pedal or how hard you strike."
                        else "This step needs a MIDI keyboard connected by USB: only MIDI reports the pedal and how hard each key is struck.",
                        Modifier.padding(horizontal = 4.dp), size = 64.dp, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 300.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        ScenePreview(MIDI_NEEDED, Modifier.fillMaxSize().padding(6.dp), loop = true,
                            description = "A digital piano plugged into the phone by a USB cable, sending the pedal and how hard each key is struck")
                    }
                    Text("Plug one in by USB and this step starts by itself.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedButton(onClick = { Log.d(TAG, "lesson ${lesson.id} step ${index + 1} skipped (needs MIDI)"); complete() }) { Text("Skip (needs MIDI)") }
                }
            } else if (scenes != null) {
                // The explainer: the scene bar, the pup with the scene's caption, the stage.
                Column(Modifier.weight(1f).semantics { contentDescription = step.text }) {
                    if (step.scenes.size > 1) SceneBar(step.scenes.size, scenes.index, scenes::fraction)
                    PupSays(scenes.scene.mood.pose(), scenes.scene.caption, Modifier.padding(top = 12.dp, bottom = 12.dp).heightIn(min = 72.dp), size = 72.dp)
                    SceneStage(scenes, Modifier.weight(1f), roomier = true)
                    if (last && onSong != null) ChunkyButton(onClick = { voice.stop(); onSong() }, Modifier.padding(top = 12.dp)) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp)); Text("Open the song")
                    }
                }
            } else if (step.exercise == null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The coach explains: his first paragraph in his bubble, the rest as plain notes under it.
                    val paras = step.text.split("\n\n")
                    PupSays(Pose.LEARNING, paras.first(), Modifier.padding(top = 4.dp), size = 96.dp)
                    paras.drop(1).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    if (last && onSong != null) ChunkyButton(onClick = { voice.stop(); onSong() }) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp)); Text("Open the song")
                    }
                }
            } else {
                // The pup gives the instruction (his face answers each try for a few seconds).
                PupSays(reaction ?: if (step.kind == StepKind.LISTEN) Pose.LISTENING else Pose.PRACTICING, step.text,
                    Modifier.padding(horizontal = 4.dp).padding(bottom = 10.dp), size = 56.dp, style = MaterialTheme.typography.bodyLarge)
                val ex = step.exercise
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    // The stage is always dark, like the full player: the one raised panel on this screen.
                    SharpEarsTheme(dark = true) {
                        key(index, run) {
                            PlayerScreen(
                                ex.toSong("${lesson.id}-${index + 1}", lesson.title), null, deps.sampler, deps.midi, deps.mic, voice, deps.prefs, deps.ready,
                                0.0, false,
                                PlayerStart(step.mode, ex.practice, mic = step.kind != StepKind.LISTEN && !step.kind.needsMidi && deps.prefs.micDefault && deps.mic.hasPermission(),
                                    midiScript = midiScript?.takeIf { index == startStep && run == 0 },
                                    inject = inject?.takeIf { index == startStep && run == 0 && step.kind != StepKind.LISTEN },
                                    beatLines = ex.beatLines),
                                onBack = onBack,
                                onFinished = { exerciseEnded(step, it) },
                                embedded = Embedded(stateLabel(step), labels = step.labels, ramp = step.kind == StepKind.RAMP,
                                    special = step.kind.takeIf { it.needsMidi }),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (finished && onNext != null) OutlinedButton(onClick = onBack) { Text("All lessons") }
            // Back steps through the scenes of a reading step first, then to the step before.
            if (!finished && (index > 0 || (scenes?.index ?: 0) > 0)) OutlinedButton(onClick = {
                val sc = scenes
                if (sc != null && sc.index > 0) sc.go(sc.index - 1) else { go(index - 1); run++ }
            }) { Text("Back") }
            if (scenes != null) SceneControls(scenes, voice, "lesson ${lesson.id}:")
            Spacer(Modifier.weight(1f))
            when {
                finished -> onNext?.let { ChunkyButton(onClick = it) { Text("Next lesson") } } ?: ChunkyButton(onClick = onBack) { Text("Done") }
                scenes != null && !scenes.last -> ChunkyButton(onClick = { scenes.go(scenes.index + 1) }) { Text("Next") }
                last && step.exercise == null -> ChunkyButton(onClick = { complete() }) { Text("Finish") }
                last -> {}                                            // an exercise as the last step finishes the lesson itself
                else -> ChunkyButton(onClick = { go(index + 1); run++ }) { Text("Next") }
            }
        }
    }
    bar?.let { (cheer, title, line) ->
        CheerBar(cheer, title, line, if (cheer == Cheer.GOOD) "Continue" else "Try again", Modifier.align(Alignment.BottomCenter), onButton = {
            bar = null
            if (cheer == Cheer.GOOD) complete() else { feedback = null; run++ }
        })
    }
    if (finished) Celebration(
        "Lesson complete!", lesson.summary,
        listOf(Stat("${lesson.steps.size}", "Steps"), Stat("%d:%02d".format(tookSecs / 60, tookSecs % 60), "Time"),
            Stat("$retries", if (retries == 1) "Retry" else "Retries")),
        primary = if (onNext != null) "Next lesson" else "Done", onPrimary = { voice.stop(); (onNext ?: onBack)() },
        secondary = if (onNext != null) "All lessons" else null, onSecondary = { voice.stop(); onBack() },
        sampler = deps.sampler.takeIf { deps.prefs.sound },
        xpGained = progress?.let { (xpAtEnd - xpAtOpen).takeIf { it > 0 } },
        xpBar = progress?.let { com.bigpapituna.sharpears.ui.profile.xpBarFor(xpAtOpen, xpAtEnd) },
    )
    }
}

/** What the MIDI-needed state shows: a keyboard plugged in, sending the pedal and how hard each key is struck. */
private val MIDI_NEEDED = com.bigpapituna.sharpears.engine.theory.MidiVisual(listOf(
    com.bigpapituna.sharpears.engine.theory.MidiSignal(null, 0, "Pedal down"),
    com.bigpapituna.sharpears.engine.theory.MidiSignal(60, 40, "Soft, 40"),
    com.bigpapituna.sharpears.engine.theory.MidiSignal(60, 112, "Loud, 112")))

/** The step's fixed hand and mode, shown in the embedded player's bar. */
private fun stateLabel(step: Step): String {
    val ex = step.exercise ?: return ""
    val hand = when (ex.practice) { Practice.BOTH -> "Both hands"; Practice.LEFT -> "Left hand"; Practice.RIGHT -> "Right hand" }
    return when (step.kind) {
        StepKind.LISTEN -> "Listen"
        StepKind.PLAY -> "$hand, the notes wait"
        StepKind.FROM_MEMORY -> "$hand, from memory"
        StepKind.PERFORM -> "$hand, in time, pass at ${ex.pass}%"
        StepKind.RAMP -> "$hand, up to ${ex.bpm.toInt()} bpm"
        StepKind.PEDAL -> "Pedal with each chord"
        StepKind.DYNAMICS -> "Soft, then loud"
        StepKind.READ -> ""
    }
}

/** One dot per step: done steps filled, the current one a short accent bar. */
@Composable
private fun StepDots(count: Int, current: Int, finished: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = if (finished) "All $count steps done" else "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val now = i == current && !finished
            Box(Modifier.height(8.dp).width(if (now) 22.dp else 8.dp).clip(CircleShape).background(
                when {
                    now -> MaterialTheme.colorScheme.primary
                    i < current || finished -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.outlineVariant
                }))
        }
    }
}
