package com.bigpapituna.sharpears.ui.theory

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.theory.Click
import com.bigpapituna.sharpears.engine.theory.Cue
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.TheoryLesson
import com.bigpapituna.sharpears.engine.theory.TheoryUnit
import com.bigpapituna.sharpears.engine.theory.Visual
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.CircleVisual
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun Mood.pose() = when (this) {
    Mood.EXPLAIN -> Pose.LEARNING; Mood.POINT -> Pose.GET_READY; Mood.LISTEN -> Pose.LISTENING
    Mood.PLAY -> Pose.PRACTICING; Mood.CHEER -> Pose.GREAT_JOB; Mood.THINK -> Pose.WELCOME
}

/**
 * Plays a scene's cues: notes through the [sampler] (each released after its hold) and clicks through [clicks].
 * A note struck again cancels the older release, so a repeated key is never cut short.
 */
internal class SceneSound(private val sampler: Sampler, private val clicks: Clicks?) {
    private val offAt = HashMap<Int, Int>()

    fun play(c: Cue) {
        c.click?.let { clicks?.play(it == Click.ACCENT) }
        c.notes.forEach { sampler.noteOn(it, c.velocity); offAt[it] = c.atMs + c.holdMs }
    }

    fun release(t: Int) {
        val due = offAt.filterValues { it <= t }.keys
        due.forEach { sampler.noteOff(it); offAt.remove(it) }
    }

    val ringing get() = offAt.isNotEmpty()

    fun stopAll() { offAt.keys.forEach(sampler::noteOff); offAt.clear() }
}

/**
 * Runs [cues] from [clock]'s current time while it is composed: advances the clock every frame, plays each cue as
 * the clock passes it, releases notes, and returns once the animation ([durationMs]) and every note are over.
 */
internal suspend fun runClock(clock: MutableIntState, durationMs: Int, cues: List<Cue>, sound: SceneSound) {
    val from = clock.intValue
    var next = cues.indexOfFirst { if (from == 0) it.atMs >= 0 else it.atMs > from }.let { if (it < 0) cues.size else it }
    var last = withFrameMillis { it }
    while (clock.intValue < durationMs || sound.ringing) {
        withFrameMillis { now ->
            val t = clock.intValue + (now - last).toInt().coerceIn(0, 100)
            last = now
            clock.intValue = t
            while (next < cues.size && cues[next].atMs <= t) sound.play(cues[next++])
            sound.release(t)
        }
    }
}

/**
 * The animated explainer: the pup says each scene's caption (spoken too, with the voice on) over its animation on
 * the stage, the one raised panel. Scenes play themselves one after another; Pause holds the animation, sound and
 * voice; Back, Watch again and Next move by hand. A segmented bar shows the scenes, the current one filling as it
 * plays. With the phone's animations off each scene is drawn finished, its sounds still played in time.
 */
@Composable
internal fun Explainer(
    unit: TheoryUnit, lesson: TheoryLesson, sampler: Sampler, clicks: Clicks?, voice: Voice, startScene: Int,
    onScene: (Int) -> Unit, onQuiz: () -> Unit, onClose: () -> Unit,
) {
    val still = reducedMotion()
    var index by remember { mutableIntStateOf(startScene.coerceIn(0, lesson.scenes.lastIndex)) }
    var playing by remember { mutableStateOf(true) }
    var run by remember { mutableIntStateOf(0) }            // bumps to watch the scene again
    var forward by remember { mutableStateOf(true) }
    val clock = remember { mutableIntStateOf(0) }
    val sound = remember { SceneSound(sampler, clicks) }
    val scene = lesson.scenes[index]
    val visual = scene.visual
    val last = index == lesson.scenes.lastIndex
    DisposableEffect(Unit) { onDispose { sound.stopAll(); voice.stop() } }

    fun go(to: Int) { forward = to >= index; index = to; playing = true }

    // A new scene (or Watch again): back to its start, the caption said once.
    LaunchedEffect(index, run) {
        clock.intValue = 0
        sound.stopAll()
        onScene(index)
        Log.d(TAG, "theory: ${lesson.id} scene ${index + 1}/${lesson.scenes.size} ${visual::class.simpleName}")
        voice.speak(scene.say, interrupt = true)
    }
    // Play the scene while not paused; once it is over and the caption said, move on by itself.
    LaunchedEffect(index, run, playing) {
        if (!playing) { sound.stopAll(); return@LaunchedEffect }
        val shownAt = System.currentTimeMillis()
        runClock(clock, visual.durationMs, visual.cues, sound)
        if (last) return@LaunchedEffect
        withTimeoutOrNull(15_000) { snapshotFlow { voice.speaking.value }.first { !it } }
        // With the voice off, leave time to read the caption.
        val readMs = if (voice.enabled) 0L else 1500L + scene.caption.split(' ').size * 260L
        delay(maxOf(1100L, readMs - (System.currentTimeMillis() - shownAt)))
        Log.d(TAG, "theory: ${lesson.id} autoplay to scene ${index + 2}")
        go(index + 1)
    }
    // The stage slides in from the side we are heading.
    val enter = remember { Animatable(1f) }
    LaunchedEffect(index) { if (!still) { enter.snapTo(0f); enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) } }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close lesson") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Unit ${unit.number}, ${unit.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.semantics { heading() })
                }
            }
            SceneBar(lesson.scenes.size, index) { (clock.intValue.toFloat() / visual.durationMs).coerceIn(0f, 1f) }
            PupSays(scene.mood.pose(), scene.caption, Modifier.padding(top = 14.dp, bottom = 12.dp).heightIn(min = 80.dp), size = 76.dp)
            // The stage is only as tall as the lesson's tallest scene needs (a share of its width), centred in the room
            // left over: on a tall phone a full-height panel left the keyboard scenes floating in empty space.
            // One height per lesson, so the panel does not jump between scenes.
            val ratio = remember(lesson) { lesson.scenes.maxOf { stageRatio(it.visual) } }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().heightIn(max = maxWidth * ratio).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    androidx.compose.runtime.key(index) {
                        SceneCanvas(visual, time = { if (still) visual.durationMs else clock.intValue }, description = scene.caption,
                            modifier = Modifier.fillMaxSize().padding(6.dp).graphicsLayer {
                                alpha = enter.value
                                translationX = (1 - enter.value) * 48.dp.toPx() * if (forward) 1 else -1
                            })
                    }
                }
            }
        }
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { go(index - 1) }, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            FilledTonalIconButton(onClick = {
                playing = !playing
                if (!playing) voice.stop()
                Log.d(TAG, "theory: ${if (playing) "play" else "pause"} at ${clock.intValue} ms")
            }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow), contentDescription = if (playing) "Pause" else "Play")
            }
            IconButton(onClick = { playing = true; run++ }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = "Watch this again")
            }
            Spacer(Modifier.weight(1f))
            ChunkyButton(onClick = { if (last) { sound.stopAll(); voice.stop(); onQuiz() } else go(index + 1) }) {
                Text(if (last) "Start quiz" else "Next")
            }
        }
    }
}

/** Stage height as a share of its width that each kind of scene needs (keys are wide, the circle is round). */
private fun stageRatio(v: Visual): Float = when (v) {
    is KeysVisual -> 0.8f
    is StaffVisual -> if (v.grand) 1.0f else 0.85f
    is CircleVisual, is ChordVisual -> 1.05f
    else -> 0.9f
}

/** One segment per scene: done ones full, the current one filling as it plays (read while drawing). */
@Composable
private fun SceneBar(count: Int, current: Int, fraction: () -> Float) {
    val c = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 4.dp).semantics { contentDescription = "Scene ${current + 1} of $count" }) {
        val gap = 5.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        val r = CornerRadius(size.height / 2)
        for (i in 0 until count) {
            val x = i * (w + gap)
            drawRoundRect(c.outlineVariant, Offset(x, 0f), Size(w, size.height), r)
            val f = when { i < current -> 1f; i == current -> fraction(); else -> 0f }
            if (f > 0f) drawRoundRect(c.primary, Offset(x, 0f), Size(maxOf(w * f, size.height), size.height), r)
        }
    }
}

/** A finished-looking still of a visual, for quiz pictures and previews. */
@Composable
internal fun StillScene(visual: com.bigpapituna.sharpears.engine.theory.Visual, modifier: Modifier = Modifier, description: String = "") =
    SceneCanvas(visual, time = { visual.durationMs }, modifier = modifier, description = description)
