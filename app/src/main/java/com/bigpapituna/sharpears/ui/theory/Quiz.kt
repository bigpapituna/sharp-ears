package com.bigpapituna.sharpears.ui.theory

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.theory.Choice
import com.bigpapituna.sharpears.engine.theory.Listen
import com.bigpapituna.sharpears.engine.theory.PlayIt
import com.bigpapituna.sharpears.engine.theory.Question
import com.bigpapituna.sharpears.engine.theory.TheoryLesson
import com.bigpapituna.sharpears.engine.theory.TheoryUnit
import com.bigpapituna.sharpears.engine.theory.keyLabel
import com.bigpapituna.sharpears.ui.Bad
import com.bigpapituna.sharpears.ui.Cheer
import com.bigpapituna.sharpears.ui.CheerBar
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.cheerTitle
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A lesson's quiz, one question at a time: tap an answer, play a key, or listen and choose. Each answer gets the
 * cheer bar with a one-line explanation. A question missed is asked again at the end (it counts as missed), so the
 * quiz always ends on right answers. [onDone] gets the first-try score.
 */
@Composable
internal fun Quiz(unit: TheoryUnit, lesson: TheoryLesson, sampler: Sampler, voice: Voice, startAt: Int, onDone: (correct: Int, total: Int) -> Unit, onClose: () -> Unit) {
    val qs = lesson.quiz
    var order by remember { mutableStateOf(qs.indices.toList().drop(startAt.coerceIn(0, qs.lastIndex))) }
    var at by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }          // an option index, or the key played
    var right by remember { mutableStateOf<Boolean?>(null) }
    var missed by remember { mutableStateOf(setOf<Int>()) }
    var cheers by remember { mutableIntStateOf(lesson.id.hashCode()) }
    val qi = order[at]
    val q = qs[qi]
    DisposableEffect(Unit) { onDispose { voice.stop() } }
    LaunchedEffect(at) {
        Log.d(TAG, "theory: ${lesson.id} question ${qi + 1}/${qs.size} ${q::class.simpleName}")
        voice.speak(com.bigpapituna.sharpears.engine.theory.spokenNumerals(q.prompt), interrupt = true)
    }

    fun answer(choice: Int, ok: Boolean) {
        if (right != null) return
        picked = choice; right = ok
        if (!ok && qi !in missed) { missed = missed + qi; order = order + qi }
        Log.d(TAG, "theory: ${lesson.id} q${qi + 1} answered $choice ${if (ok) "right" else "wrong"}")
        voice.speak(if (ok) cheerTitle(cheers) else com.bigpapituna.sharpears.engine.theory.spokenNumerals(q.explain), interrupt = true)
    }
    fun next() {
        if (at + 1 < order.size) { at++; picked = null; right = null; cheers++ }
        else { voice.stop(); onDone(qs.size - missed.size, qs.size) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave the quiz") }
                    QuizBar((at + if (right == true) 1 else 0).toFloat() / order.size, Modifier.weight(1f).padding(horizontal = 6.dp))
                    Text("${minOf(at + 1, order.size)}/${order.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                }
                key(at) {
                    when (q) {
                        is Choice -> ChoiceBody(q, picked, right) { answer(it, it == q.answer) }
                        is Listen -> ListenBody(q, sampler, voice, picked, right) { answer(it, it == q.answer) }
                        is PlayIt -> PlayBody(q, sampler, picked, right) { answer(it, q.accepts(it)) }
                    }
                }
            }
        }
        right?.let { ok ->
            val line = if (ok) q.explain else (answerText(q)?.let { "The answer: $it. " } ?: "") + q.explain
            CheerBar(if (ok) Cheer.GOOD else Cheer.MISS, if (ok) cheerTitle(cheers) else "Not quite", line,
                if (at + 1 < order.size) "Continue" else "Finish", Modifier.align(Alignment.BottomCenter), onButton = ::next)
        }
    }
}

private fun answerText(q: Question) = when (q) {
    is Choice -> q.options[q.answer]
    is Listen -> q.options[q.answer]
    is PlayIt -> null
}

@Composable
private fun QuizBar(fraction: Float, modifier: Modifier) {
    val f by animateFloatAsState(fraction, tween(if (reducedMotion()) 0 else 450), label = "quiz")
    Box(modifier.height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

/** The prompt in the pup's bubble, and the picture (if any) in the raised panel under it. */
@Composable
private fun Prompt(q: Question, pose: Pose, pictureHeight: Int = 190) {
    PupSays(pose, q.prompt, Modifier.padding(top = 10.dp, bottom = 14.dp), size = 72.dp)
    val show = (q as? Choice)?.show ?: (q as? PlayIt)?.show
    if (show != null) {
        Box(Modifier.fillMaxWidth().height(pictureHeight.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            StillScene(show, Modifier.fillMaxSize().padding(6.dp), description = "Picture for the question")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChoiceBody(q: Choice, picked: Int?, right: Boolean?, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 220.dp)) {
        Prompt(q, Pose.LEARNING)
        Options(q.options, q.answer, picked, right, onPick)
    }
}

/** Answer pills: the right one fills with the accent once answered; a wrong pick is crossed out. */
@Composable
private fun Options(options: List<String>, answer: Int, picked: Int?, right: Boolean?, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, o ->
            val done = right != null
            val isAnswer = done && i == answer
            val wrongPick = done && i == picked && i != answer
            val shape = RoundedCornerShape(28.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
                .background(when { isAnswer -> c.primaryContainer; wrongPick -> c.surfaceContainerHighest; else -> Color.Transparent })
                .border(2.dp, when { isAnswer -> c.primary; wrongPick -> c.outline; else -> c.outlineVariant }, shape)
                .clickable(enabled = !done, role = Role.Button) { onPick(i) }
                .semantics { if (isAnswer) stateDescription = "Right answer" else if (wrongPick) stateDescription = "Your answer, wrong" }
                .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(o, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f),
                    color = when { isAnswer -> c.onPrimaryContainer; wrongPick -> c.onSurfaceVariant; else -> c.onSurface })
                if (isAnswer) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.primary)
                if (wrongPick) Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = c.onSurfaceVariant)
            }
        }
    }
}

/** Listen and choose: the clips play by themselves once the question is read, and again on the big button. */
@Composable
private fun ListenBody(q: Listen, sampler: Sampler, voice: Voice, picked: Int?, right: Boolean?, onPick: (Int) -> Unit) {
    var playing by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    fun play() {
        job?.cancel()
        job = scope.launch {
            try {
                q.clips.forEachIndexed { i, clip ->
                    playing = i
                    clip.forEach { sampler.noteOn(it, 88) }
                    delay(if (q.clips.size == 1) 1700L else q.gapMs.toLong())
                    clip.forEach(sampler::noteOff)
                    delay(if (q.clips.size > 4) 40L else 180L)
                }
            } finally { q.clips.flatten().forEach(sampler::noteOff); playing = null }
        }
    }
    LaunchedEffect(Unit) {
        delay(300)
        withTimeoutOrNull(6000) { snapshotFlow { voice.speaking.value }.first { !it } }
        delay(250)
        play()
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 220.dp)) {
        Prompt(q, Pose.LISTENING)
        // The stage: a big play button, and one light per sound as it plays.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val c = MaterialTheme.colorScheme
            val pulse by animateFloatAsState(if (playing != null) 1.08f else 1f, tween(200), label = "play")
            Box(Modifier.size(84.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(c.primary)
                .clickable(role = Role.Button) { play() }.semantics { contentDescription = "Play the sounds again" }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(44.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (q.clips.size > 4) 8.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                q.clips.indices.forEach { i ->
                    val on = playing == i
                    if (q.clips.size > 4) Box(Modifier.size(if (on) 14.dp else 10.dp).clip(CircleShape).background(if (on) c.primary else c.outlineVariant))
                    else Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) c.primary else c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = if (on) c.onPrimary else c.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Options(q.options, q.answer, picked, right) { job?.cancel(); onPick(it) }
    }
}

/** Play it: the keyboard answers the first key pressed; a miss shows the right key pulsing. */
@Composable
private fun PlayBody(q: PlayIt, sampler: Sampler, picked: Int?, right: Boolean?, onKey: (Int) -> Unit) {
    var held by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Prompt(q, Pose.PRACTICING, pictureHeight = 170)
        Text(if (right == null) "Tap the key on the keyboard." else "Keep playing if you like.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
        val target = (q.low..q.high).firstOrNull { q.accepts(it) } ?: q.target
        val lit = buildMap {
            held?.let { put(it, com.bigpapituna.sharpears.ui.RightHandColor) }
            if (right != null) picked?.let { put(it, if (right) Good else Bad) }
            if (right == false) put(target, Good)
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 240.dp).height(220.dp).clip(RoundedCornerShape(20.dp))
            .background(com.bigpapituna.sharpears.ui.Stage).padding(8.dp)) {
            Keyboard(q.low, q.high, lit,
                onNoteOn = { m, v -> sampler.noteOn(m, v); held = m; if (right == null) onKey(m) },
                onNoteOff = { m -> sampler.noteOff(m); if (held == m) held = null },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                pulsing = if (right == false) mapOf(target to Good) else emptyMap(),
                labels = if (right != null) mapOf(target to keyLabel(target, q.flats)) else emptyMap())
        }
    }
}
