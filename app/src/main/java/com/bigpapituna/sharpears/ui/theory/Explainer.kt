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
import androidx.compose.runtime.Stable
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
import com.bigpapituna.sharpears.engine.theory.Scene
import com.bigpapituna.sharpears.engine.theory.TheoryLesson
import com.bigpapituna.sharpears.engine.theory.TheoryUnit
import com.bigpapituna.sharpears.engine.theory.Visual
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.CircleVisual
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.pup.Pose
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
internal class SceneSound(private val sampler: Sampler?, private val clicks: Clicks?) {
    private val offAt = HashMap<Int, Int>()

    fun play(c: Cue) {
        c.click?.let { clicks?.play(it == Click.ACCENT) }
        c.notes.forEach { sampler?.noteOn(it, c.velocity); offAt[it] = c.atMs + c.holdMs }
    }

    fun release(t: Int) {
        val due = offAt.filterValues { it <= t }.keys
        due.forEach { sampler?.noteOff(it); offAt.remove(it) }
    }

    val ringing get() = offAt.isNotEmpty()

    fun stopAll() { offAt.keys.forEach { sampler?.noteOff(it) }; offAt.clear() }
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
 * A list of scenes being played: which one is showing, whether it is playing or paused, a counter bumped to watch
 * it again, and its clock (ms into the scene's animation). [rememberScenePlayback] drives it.
 */
@Stable
internal class ScenePlayback(val scenes: List<Scene>, start: Int) {
    var index by mutableIntStateOf(start.coerceIn(0, scenes.lastIndex))
        private set
    var playing by mutableStateOf(true)
    var run by mutableIntStateOf(0)
        private set
    var forward by mutableStateOf(true)
        private set
    val clock = mutableIntStateOf(0)
    val scene get() = scenes[index]
    val last get() = index == scenes.lastIndex
    /** How far the current scene has played, 0 to 1 (read while drawing). */
    fun fraction() = (clock.intValue.toFloat() / scene.visual.durationMs).coerceIn(0f, 1f)
    fun go(to: Int) { forward = to >= index; index = to.coerceIn(0, scenes.lastIndex); playing = true }
    fun replay() { playing = true; run++ }
}

/**
 * Plays [scenes] while composed: each scene's caption said once as it opens (spoken the way Theory reads it), its
 * sounds through [sampler] and [clicks] in time with the clock, then on to the next scene by itself once the
 * animation and the caption are over (the last one stays). Pause holds the clock, the sound and the voice.
 * [interruptFirst] false lets the first caption queue behind a line already being said. [tag] marks the log lines.
 */
@Composable
internal fun rememberScenePlayback(
    scenes: List<Scene>, sampler: Sampler, clicks: Clicks?, voice: Voice, start: Int = 0, tag: String,
    interruptFirst: Boolean = true, onScene: (Int) -> Unit = {},
): ScenePlayback {
    val pb = remember(scenes) { ScenePlayback(scenes, start) }
    val sound = remember { SceneSound(sampler, clicks) }
    var said by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { sound.stopAll(); voice.stop() } }
    // A new scene (or Watch again): back to its start, the caption said once.
    LaunchedEffect(pb.index, pb.run) {
        pb.clock.intValue = 0
        sound.stopAll()
        onScene(pb.index)
        Log.d(TAG, "$tag scene ${pb.index + 1}/${scenes.size} ${pb.scene.visual::class.simpleName}")
        voice.speak(com.bigpapituna.sharpears.engine.theory.spokenTheory(pb.scene.say), interrupt = said > 0 || interruptFirst)
        said++
    }
    // Play the scene while not paused; once it is over and the caption said, move on by itself.
    LaunchedEffect(pb.index, pb.run, pb.playing) {
        if (!pb.playing) { sound.stopAll(); return@LaunchedEffect }
        val scene = pb.scene
        val shownAt = System.currentTimeMillis()
        runClock(pb.clock, scene.visual.durationMs, scene.visual.cues, sound)
        if (pb.last) return@LaunchedEffect
        withTimeoutOrNull(15_000) { snapshotFlow { voice.speaking.value }.first { !it } }
        // With the voice off, leave time to read the caption.
        val readMs = if (voice.enabled) 0L else 1500L + scene.caption.split(' ').size * 260L
        delay(maxOf(1100L, readMs - (System.currentTimeMillis() - shownAt)))
        Log.d(TAG, "$tag autoplay to scene ${pb.index + 2}")
        pb.go(pb.index + 1)
    }
    return pb
}

/**
 * The stage: the one raised panel, as tall as the tallest of the scenes needs (a share of its width, so the panel
 * does not jump between scenes), centred in the room it is given. Each scene slides in from the side it comes from.
 * With the phone's animations off each scene is drawn finished.
 */
@Composable
internal fun SceneStage(pb: ScenePlayback, modifier: Modifier = Modifier, roomier: Boolean = false) {
    val still = reducedMotion()
    val enter = remember { Animatable(1f) }
    LaunchedEffect(pb.index) { if (!still) { enter.snapTo(0f); enter.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) } }
    // A lesson's stage ([roomier]) takes a little more height and sits under the pup, its spare room below.
    val ratio = remember(pb.scenes) { pb.scenes.maxOf { stageRatio(it.visual) } * if (roomier) 1.2f else 1f }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = if (roomier) Alignment.TopCenter else Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(max = maxWidth * ratio).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            androidx.compose.runtime.key(pb.index) {
                val visual = pb.scene.visual
                SceneCanvas(visual, time = { if (still) visual.durationMs else pb.clock.intValue }, description = pb.scene.caption,
                    modifier = Modifier.fillMaxSize().padding(6.dp).graphicsLayer {
                        alpha = enter.value
                        translationX = (1 - enter.value) * 48.dp.toPx() * if (pb.forward) 1 else -1
                    })
            }
        }
    }
}

/** Pause or play, and watch this scene again: the player's two round controls. */
@Composable
internal fun SceneControls(pb: ScenePlayback, voice: Voice, tag: String) {
    FilledTonalIconButton(onClick = {
        pb.playing = !pb.playing
        if (!pb.playing) voice.stop()
        Log.d(TAG, "$tag ${if (pb.playing) "play" else "pause"} at ${pb.clock.intValue} ms")
    }, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(if (pb.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow), contentDescription = if (pb.playing) "Pause" else "Play")
    }
    IconButton(onClick = { pb.replay() }, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_repeat), contentDescription = "Watch this again")
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
    val pb = rememberScenePlayback(lesson.scenes, sampler, clicks, voice, startScene, tag = "theory: ${lesson.id}", onScene = onScene)
    val scene = pb.scene
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close lesson") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Unit ${unit.number}, ${unit.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.semantics { heading() })
                }
            }
            SceneBar(lesson.scenes.size, pb.index, pb::fraction)
            PupSays(scene.mood.pose(), scene.caption, Modifier.padding(top = 14.dp, bottom = 12.dp).heightIn(min = 80.dp), size = 76.dp)
            // On a tall phone a full-height panel left the keyboard scenes floating in empty space: the stage is only
            // as tall as the lesson's tallest scene needs.
            SceneStage(pb, Modifier.weight(1f))
        }
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pb.go(pb.index - 1) }, enabled = pb.index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            SceneControls(pb, voice, "theory:")
            Spacer(Modifier.weight(1f))
            ChunkyButton(onClick = { if (pb.last) { voice.stop(); onQuiz() } else pb.go(pb.index + 1) }) {
                Text(if (pb.last) "Start quiz" else "Next")
            }
        }
    }
}

/** Stage height as a share of its width that each kind of scene needs (keys are wide, the circle is round). */
internal fun stageRatio(v: Visual): Float = when (v) {
    is KeysVisual -> 0.8f
    is StaffVisual -> if (v.grand) 1.0f else 0.85f
    is CircleVisual, is ChordVisual -> 1.05f
    is com.bigpapituna.sharpears.engine.theory.PhraseVisual -> if (v.hasRight && v.hasLeft) 1.1f else 0.95f
    is com.bigpapituna.sharpears.engine.theory.PedalVisual -> if (v.foot) 0.95f else 1.05f
    is com.bigpapituna.sharpears.engine.theory.LaneVisual -> 1.0f
    is com.bigpapituna.sharpears.engine.theory.SongMapVisual -> 0.62f
    is com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual, is com.bigpapituna.sharpears.engine.theory.HandShapeVisual -> 0.85f
    is com.bigpapituna.sharpears.engine.theory.PostureVisual -> 0.86f
    else -> 0.9f
}

/** One segment per scene: done ones full, the current one filling as it plays (read while drawing). */
@Composable
internal fun SceneBar(count: Int, current: Int, fraction: () -> Float) {
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

/**
 * One visual playing on its own, outside an explainer (a state screen, a Chords item): once with its sounds through
 * [sampler] (null: silent), then with [loop] again every few seconds, silently. Bump [replay] to hear it again.
 * With the phone's animations off it is drawn finished.
 */
@Composable
internal fun ScenePreview(visual: Visual, modifier: Modifier = Modifier, sampler: Sampler? = null, loop: Boolean = false, replay: Int = 0, description: String = "") {
    val still = reducedMotion()
    val clock = remember { mutableIntStateOf(0) }
    val sound = remember(sampler) { SceneSound(sampler, null) }
    DisposableEffect(sound) { onDispose { sound.stopAll() } }
    LaunchedEffect(visual, replay) {
        var pass = 0
        do {
            clock.intValue = 0
            runClock(clock, visual.durationMs, if (pass == 0) visual.cues else emptyList(), sound)
            pass++
            if (loop) delay(1800)
        } while (loop)
    }
    SceneCanvas(visual, time = { if (still) visual.durationMs else clock.intValue }, modifier = modifier, description = description)
}
