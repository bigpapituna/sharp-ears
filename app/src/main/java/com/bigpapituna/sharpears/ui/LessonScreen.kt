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
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong

/** Everything a lesson needs from the activity. */
class LessonDeps(val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val prefs: Prefs, val ready: Boolean)

/**
 * One lesson, a step at a time: the step's text at the top (and read aloud), its exercise in the embedded
 * player, a row of progress dots, Back and Next. An exercise step completes itself when its run ends (a Perform
 * step at its pass mark); then the feedback line and the next step's text are spoken and the next step opens.
 * The lesson is done when its last step completes. [inject] (debug) feeds the mic for the step opened first.
 */
@Composable
fun LessonScreen(
    lesson: CourseLesson, startStep: Int, deps: LessonDeps, inject: String?,
    onStep: (Int) -> Unit, onSong: (() -> Unit)?, onDone: () -> Unit, onNext: (() -> Unit)?, onBack: () -> Unit,
) {
    val (level, _) = ALL.first { it.second == lesson }
    var index by remember { mutableIntStateOf(startStep.coerceIn(0, lesson.steps.lastIndex)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }              // bumps to restart the step's player
    var advancedByItself by remember { mutableStateOf(false) }
    val step = lesson.steps[index]
    val last = index == lesson.steps.lastIndex
    val voice = deps.voice

    // Read each step out as it opens. After an automatic advance the feedback line is already queued, so no interrupt.
    LaunchedEffect(index) {
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1}/${lesson.steps.size} ${step.kind}")
        onStep(index)
        voice.speak(step.text.replace("\n\n", " "), interrupt = !advancedByItself)
        advancedByItself = false
    }

    fun go(to: Int) { feedback = null; finished = false; index = to }
    fun complete() {
        if (last) {
            finished = true
            onDone()
            Log.d(TAG, "lesson ${lesson.id} done")
            voice.speak("Lesson complete.")
        } else { advancedByItself = true; index++ }
    }
    fun exerciseEnded(s: Step, recap: Recap?) {
        val passed = s.passed(recap)
        val line = stepFeedback(s, recap, passed, last)
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1} ended passed=$passed recap=$recap feedback=\"$line\"")
        feedback = line.ifEmpty { null }
        if (line.isNotEmpty()) voice.speak(line)
        if (passed) complete()
    }

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
            StepDots(lesson.steps.size, index, finished, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 14.dp))
            feedback?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp))
            }
            if (finished) {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lesson complete", style = MaterialTheme.typography.headlineMedium)
                    Text(lesson.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (step.exercise == null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    step.text.split("\n\n").forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    if (last && onSong != null) Button(onClick = { voice.stop(); onSong() }) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp)); Text("Open the song")
                    }
                }
            } else {
                Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))
                val ex = step.exercise
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    // The stage is always dark, like the full player: the one raised panel on this screen.
                    SharpEarsTheme(dark = true) {
                        key(index, run) {
                            PlayerScreen(
                                ex.toSong("${lesson.id}-${index + 1}", lesson.title), null, deps.sampler, deps.midi, deps.mic, voice, deps.prefs, deps.ready,
                                0.0, false,
                                PlayerStart(step.mode, ex.practice, mic = step.kind != StepKind.LISTEN && deps.prefs.micDefault && deps.mic.hasPermission(),
                                    inject = inject?.takeIf { index == startStep && run == 0 && step.kind != StepKind.LISTEN }),
                                onBack = onBack,
                                onFinished = { exerciseEnded(step, it) },
                                embedded = Embedded(stateLabel(step), labels = step.labels),
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
            if (index > 0 && !finished) OutlinedButton(onClick = { go(index - 1); run++ }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            when {
                finished -> onNext?.let { Button(onClick = it) { Text("Next lesson") } } ?: Button(onClick = onBack) { Text("Done") }
                last && step.exercise == null -> Button(onClick = { complete() }) { Text("Finish") }
                last -> {}                                            // an exercise as the last step finishes the lesson itself
                else -> Button(onClick = { go(index + 1); run++ }) { Text("Next") }
            }
        }
    }
}

/** The step's fixed hand and mode, shown in the embedded player's bar. */
private fun stateLabel(step: Step): String {
    val ex = step.exercise ?: return ""
    val hand = when (ex.practice) { Practice.BOTH -> "Both hands"; Practice.LEFT -> "Left hand"; Practice.RIGHT -> "Right hand" }
    return when (step.kind) {
        StepKind.LISTEN -> "Listen"
        StepKind.PLAY -> "$hand, the notes wait"
        StepKind.FROM_MEMORY -> "$hand, from memory"
        StepKind.PERFORM -> "$hand, in time, pass at ${ex.pass}%"
        StepKind.READ -> ""
    }
}

// ponytail: placeholder sentences; the mistake-naming report replaces them
private fun stepFeedback(step: Step, recap: Recap?, passed: Boolean, last: Boolean): String {
    val then = if (last) "" else " On to the next step."
    return when (step.kind) {
        StepKind.READ, StepKind.LISTEN -> ""
        StepKind.PERFORM -> if (passed) "${recap?.accuracy} percent.$then" else "${recap?.accuracy ?: 0} percent, and you need ${step.exercise?.pass}. Play it again."
        else -> "Well played.$then"
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
