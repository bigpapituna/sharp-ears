package com.bigpapituna.sharpears.ui.playground

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.playground.ClapCounter
import com.bigpapituna.sharpears.engine.playground.drumSamples
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The pup's drum: a low tom ([drumSamples]) synthesized once into the cache and played through SoundPool. */
internal class DrumSound(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val sound = pool.load(File(context.cacheDir, "pup-drum.wav").also { Wav.write(it, drumSamples(), 48_000) }.path, 1)
    fun play(accent: Boolean) { pool.play(sound, if (accent) 1f else 0.8f, if (accent) 1f else 0.8f, 1, 0, 1f) }
    fun release() = pool.release()
}

private enum class ClapPhase { READY, COUNT_IN, PLAYING, DONE, NOTHING_HEARD }

/** A clap is on the beat this close to it. */
private const val ON_BEAT_MS = 220.0
/** The clap counter confirms a clap this long after it began (its tail has to be heard first): the wait at the end. */
private const val CONFIRM_MS = 110.0

/**
 * Lesson 7, "The steady drum": the pup drums; the child claps along. A bar of drum beats counts in (1, 2, 3, 4), then
 * the exercise's beats at its tempo, each one a paw print that fills when a clap lands within a fifth of a second of
 * it. Claps are heard by the clap counter on the raw microphone (the drum, the voice and the piano never count), or a
 * tap on the big round pad. Nothing heard at all: the pup asks to clap again, or to skip. Never a fail: the report's
 * groups are the claps on the beat (its extra score the share of beats with one).
 */
@Composable
internal fun ClapStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val beats = parseTokens(ex.right).count { !it.rest }.coerceAtLeast(1)
    val beatMs = 60_000.0 / ex.bpm
    val context = LocalContext.current
    val drum = remember { runCatching { DrumSound(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { drum?.release() } }
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val ended by rememberUpdatedState(onEnded)

    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(ClapPhase.READY) }
    var startNanos by remember { mutableStateOf(0L) }
    var beatNow by remember { mutableIntStateOf(-1) }          // -4..-1 count-in, 0.. the beats
    val credited = remember { mutableStateListOf<Int>() }      // beats with a clap on them
    var claps by remember { mutableIntStateOf(0) }
    val bursts = remember { mutableStateListOf<Pair<Long, Boolean>>() }   // (when, on the beat)
    val beatPulse = remember { Animatable(0f) }

    // When each clap began, on the sample clock: the moment the first chunk of sound arrived, plus the clap's sample
    // index. (Arrival time minus the counter's delay would wobble with the audio thread.)
    val heard = remember(attempt) { MutableSharedFlow<Long>(extraBufferCapacity = 32) }
    val firstSample = remember(attempt) { java.util.concurrent.atomic.AtomicLong(0L) }
    val counter = remember(attempt) {
        ClapCounter().also { c -> c.listener = { clap -> heard.tryEmit(firstSample.get() + clap.atSample * 1_000_000_000L / c.sampleRate) } }
    }
    val listening = rememberListening(deps.mic, deps.voice, phase == ClapPhase.COUNT_IN || phase == ClapPhase.PLAYING,
        inject?.takeIf { attempt == 0 }, raw = { s, n ->
            if (firstSample.get() == 0L) firstSample.set(System.nanoTime() - n * 1_000_000_000L / counter.sampleRate)
            counter.process(s, n)
        })

    /** A clap at [nanos] (already moved back by the counter's delay for a heard one). */
    fun clap(nanos: Long, heardIt: Boolean) {
        if (phase != ClapPhase.COUNT_IN && phase != ClapPhase.PLAYING) return
        claps++
        val ms = (nanos - startNanos) / 1e6 - 4 * beatMs                  // from the first counted beat
        val beat = Math.round(ms / beatMs).toInt()
        val on = beat in 0 until beats && abs(ms - beat * beatMs) <= ON_BEAT_MS
        if (on && beat !in credited) credited += beat
        bursts += System.nanoTime() to on
        Log.d(TAG, "playground: clap (${if (heardIt) "heard" else "tap"}) at ${"%.0f".format(ms)} ms, " + if (on) "on beat ${beat + 1}" else "between beats")
    }
    LaunchedEffect(heard) { heard.collect { t -> clap(t, heardIt = true) } }

    // Start once the voice has read the instruction (a separate trigger, so the voice speaking later cannot cut the drum
    // short); then the count-in and the beats, then the verdict.
    val speaking = deps.voice.speaking.value
    var go by remember { mutableIntStateOf(-1) }
    LaunchedEffect(attempt, speaking, deps.ready) {
        if (go == attempt || phase != ClapPhase.READY || speaking || !deps.ready) return@LaunchedEffect
        delay(700)
        go = attempt
    }
    LaunchedEffect(go) {
        if (go < 0) return@LaunchedEffect
        credited.clear(); claps = 0; bursts.clear()
        startNanos = withFrameNanos { it }
        phase = ClapPhase.COUNT_IN
        // Debug audio is a recording made for the beat: its first sample is the first count-in beat.
        if (inject != null && go == 0) {
            val waitUntil = System.nanoTime() + 1_500_000_000L
            while (firstSample.get() == 0L && System.nanoTime() < waitUntil) withFrameNanos { }
            if (firstSample.get() != 0L) startNanos = firstSample.get()
        }
        Log.d(TAG, "playground: drum at ${ex.bpm.toInt()} for $beats beats (try ${attempt + 1})")
        for (k in -4 until beats) {
            val due = startNanos + ((k + 4) * beatMs * 1e6).toLong()
            while (withFrameNanos { it } < due) { }
            beatNow = k
            if (k == 0) phase = ClapPhase.PLAYING
            drum?.play(accent = Math.floorMod(k, 4) == 0)
            if (!still) scope.launch { beatPulse.snapTo(1f); beatPulse.animateTo(0f, tween((beatMs * 0.8).toInt())) }
        }
        delay((beatMs + CONFIRM_MS + 150).toLong())                       // the last clap's tail
        val heardAny = claps > 0
        phase = if (heardAny) ClapPhase.DONE else ClapPhase.NOTHING_HEARD
        Log.d(TAG, "playground: drum done, ${credited.size} of $beats beats with a clap, $claps claps")
        if (heardAny) {
            delay(1400)
            ended(Report(emptyList(), 0, 0, null, groups = credited.size, extraScore = credited.size * 100 / beats))
        }
    }

    // A clock for the bursts' fading (frame time is System.nanoTime on Android).
    val frameNow by produceState(System.nanoTime()) { while (true) withFrameNanos { value = it } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pupSize = (maxHeight * 0.42f).coerceIn(110.dp, 220.dp)
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BeatDots(if (phase == ClapPhase.COUNT_IN || phase == ClapPhase.PLAYING) Math.floorMod(beatNow, 4) else -1)
                StageLine(when (phase) {
                    ClapPhase.READY -> "Get ready to clap!"
                    ClapPhase.COUNT_IN -> listOf("One", "Two", "Three", "Four")[Math.floorMod(beatNow, 4)]
                    ClapPhase.PLAYING -> if (bursts.lastOrNull()?.second == true) "On the boom!" else "Clap, clap!"
                    ClapPhase.DONE -> if (credited.size * 2 >= beats) "What a drummer you are!" else "I heard you! Now right on the boom."
                    ClapPhase.NOTHING_HEARD -> "I could not hear the claps."
                })
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { bursts.forEach { (t, on) -> clapBurst(frameNow, t, on, still) } }
                PaintedPup(when (phase) {
                    ClapPhase.DONE -> Painted.CLAPPING
                    ClapPhase.NOTHING_HEARD -> Painted.HEAD_TILT
                    ClapPhase.READY -> Painted.LISTENING
                    else -> Painted.DRUMMING
                }, size = pupSize, squash = { beatPulse.value })
            }
            PawRow(beats, credited.toSet(), if (phase == ClapPhase.PLAYING) beatNow else -1, Modifier.padding(vertical = 8.dp))
            if (phase == ClapPhase.NOTHING_HEARD) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { ended(Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0)) }) { Text("Skip") }
                    ChunkyButton(onClick = { attempt++; phase = ClapPhase.READY }) { Text("Clap again") }
                }
            } else {
                // The pad: a tap is a clap, for a phone without its microphone or a child who likes tapping.
                Box(Modifier.size(84.dp).background(Accent.copy(alpha = if (phase == ClapPhase.PLAYING || phase == ClapPhase.COUNT_IN) 0.9f else 0.35f), CircleShape)
                    .clickable(MutableInteractionSource(), indication = null, role = Role.Button) { clap(System.nanoTime(), heardIt = false) }
                    .semantics { contentDescription = if (listening) "Clap here, or clap your hands" else "Tap to clap" },
                    contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(44.dp)) { hands(Ink) }
                }
            }
        }
        if (phase == ClapPhase.DONE && credited.isNotEmpty()) Confetti(attempt, Modifier.fillMaxSize())
    }
}

/** Four dots, one per beat of the bar, the current one lit. */
@Composable
private fun BeatDots(current: Int) {
    Canvas(Modifier.size(width = 88.dp, height = 18.dp).semantics { contentDescription = if (current >= 0) "Beat ${current + 1}" else "" }) {
        for (i in 0 until 4) {
            val c = Offset(size.height / 2 + i * 23.dp.toPx(), size.height / 2)
            if (i == current) drawCircle(Accent, size.height / 2, c) else drawCircle(InkDim.copy(alpha = 0.45f), size.height / 4, c)
        }
    }
}

/** One paw print per beat: filled when a clap landed on it; the beat now has a ring. */
@Composable
private fun PawRow(beats: Int, credited: Set<Int>, now: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = "${credited.size} of $beats beats with a clap" }) {
        val step = minOf(size.width / beats, 40.dp.toPx())
        val x0 = (size.width - step * beats) / 2 + step / 2
        for (b in 0 until beats) {
            val c = Offset(x0 + b * step, size.height / 2)
            paw(c, step * 0.36f, if (b in credited) PupGold else InkDim.copy(alpha = 0.3f))
            if (b == now) drawCircle(Ink.copy(alpha = 0.7f), step * 0.46f, c, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A paw print: a pad and four toes. */
private fun DrawScope.paw(c: Offset, r: Float, color: Color) {
    drawOval(color, Offset(c.x - r * 0.62f, c.y - r * 0.1f), androidx.compose.ui.geometry.Size(r * 1.24f, r * 0.95f))
    for ((dx, dy) in listOf(-0.62f to -0.42f, -0.22f to -0.78f, 0.22f to -0.78f, 0.62f to -0.42f)) drawCircle(color, r * 0.24f, Offset(c.x + dx * r, c.y + dy * r))
}

/** Two hands meeting: the pad's picture. Each a palm with four fingers and a thumb, tilted toward the other, with a
 * spark of three rays where they meet. */
private fun DrawScope.hands(color: Color) {
    val w = size.width; val h = size.height
    val ink = Color(0xFF6B3A1F)
    for (side in listOf(-1f, 1f)) {
        rotate(side * 16f, Offset(w / 2 + side * w * 0.1f, h * 0.95f)) {
            val left = w / 2 + (if (side < 0) -w * 0.3f else w * 0.02f)
            val pw = w * 0.28f
            // Palm, then four fingers along its top, then the thumb on the outer side.
            drawRoundRect(color, Offset(left, h * 0.42f), Size(pw, h * 0.5f), CornerRadius(pw * 0.35f))
            for (f in 0 until 4) {
                val fw = pw / 4.4f
                val fx = left + pw * 0.06f + f * (pw * 0.88f / 4)
                val len = h * (if (f == 1 || f == 2) 0.36f else 0.3f)
                drawRoundRect(color, Offset(fx, h * 0.5f - len), Size(fw, len + h * 0.08f), CornerRadius(fw / 2))
            }
            val tx = if (side < 0) left - pw * 0.18f else left + pw * 0.88f
            drawRoundRect(color, Offset(tx, h * 0.55f), Size(pw * 0.3f, h * 0.2f), CornerRadius(pw * 0.15f))
            drawLine(ink.copy(alpha = 0.35f), Offset(left + pw * 0.2f, h * 0.7f), Offset(left + pw * 0.8f, h * 0.7f), 1.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    for (k in 0 until 3) {
        val a = -Math.PI / 2 + (k - 1) * 0.7
        val c = Offset(w / 2, h * 0.14f)
        drawLine(color, c + Offset((w * 0.08f * cos(a)).toFloat(), (w * 0.08f * sin(a)).toFloat()),
            c + Offset((w * 0.18f * cos(a)).toFloat(), (w * 0.18f * sin(a)).toFloat()), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** A clap's burst: short rays spreading from above the pup, gold on the beat, pale between beats; gone in 0.6 s. */
private fun DrawScope.clapBurst(now: Long, at: Long, onBeat: Boolean, still: Boolean) {
    val age = (now - at) / 1e9f
    if (age > 0.6f) return
    val p = if (still) 1f else age / 0.6f
    val c = Offset(size.width / 2, size.height * 0.18f)
    val color = (if (onBeat) PupGold else Ink).copy(alpha = 1f - p)
    for (k in 0 until 8) {
        val a = k * Math.PI / 4
        val r0 = 18.dp.toPx() + 30.dp.toPx() * p; val r1 = r0 + 12.dp.toPx()
        drawLine(color, c + Offset((r0 * cos(a)).toFloat(), (r0 * sin(a)).toFloat()), c + Offset((r1 * cos(a)).toFloat(), (r1 * sin(a)).toFloat()),
            3.dp.toPx(), cap = StrokeCap.Round)
    }
}
