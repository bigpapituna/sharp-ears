package com.bigpapituna.sharpears.ui.ear

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.ear.Cue
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.ear.Grade
import com.bigpapituna.sharpears.engine.ear.RhythmPattern
import com.bigpapituna.sharpears.engine.ear.RhythmTapGame
import com.bigpapituna.sharpears.ui.Cheer
import com.bigpapituna.sharpears.ui.CheerBar
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.cheerTitle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How long the phone takes to turn a scheduled click into sound. Taps are judged against the click schedule, so
 * a player who taps with what they hear lands this much late; the grader takes it off.
 */
// ponytail: one guess for every phone (SoundPool is the low latency path, 30 to 60 ms), a tap-along calibration screen would measure it
private const val OUTPUT_LATENCY_MS = 40L

/** Taps this early before the first beat after the count-in still count (a player tapping a little ahead of the first note). */
private const val EARLY_TAP_MS = 250L
/** Taps this late after the pattern's end still count. */
private const val TAIL_MS = 350L

private enum class RPhase { LISTEN, COUNT, TAP, REVIEW }

/** One tap on the pad, [ms] from the start of the pattern, and how it looked when it landed. */
private class TapMark(val ms: Long, val live: Grade)

/**
 * Rhythm tap. The app plays the pattern with the beat clicking (Listen), counts one bar in, and the player taps the
 * pattern on the pad (the whole lower half; a MIDI key also counts) while the beat keeps clicking. The grid shows the
 * pattern, a cursor sweeping over it, and the taps as marks coloured by how close they were. Then the cheer bar
 * says what was off. [onFinished] is called once, after the last round's Continue.
 */
@Composable
internal fun RhythmPlay(trainer: RhythmTapGame, env: EarEnv, label: String?, onExit: () -> Unit, onFinished: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(RPhase.LISTEN) }
    var replay by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }                    // 1..beats during the count-in
    val cursor = remember { mutableFloatStateOf(-1f) }                // in ticks, -1 = hidden
    var padDown by remember { mutableStateOf(false) }
    val taps = remember { mutableStateListOf<TapMark>() }
    var tapStart by remember { mutableLongStateOf(0L) }               // uptime of the pattern's first beat in the tap phase
    var review by remember { mutableStateOf<ChordGame.Answer?>(null) }
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val seen = version
    val round = trainer.round
    val finished = trainer.finished
    val pattern: RhythmPattern = trainer.pattern

    suspend fun voiceDone() { delay(250); while (env.voice.speaking.value) delay(50) }

    fun fire(c: Cue) {
        if (!env.sound()) return
        if (c.click) env.clicks.play(c.accent)
        c.note?.let { n ->
            env.sampler.noteOn(n, 85)
            scope.launch { delay(c.holdMs.toLong()); env.sampler.noteOff(n) }
        }
    }

    /**
     * Runs [cues] against the clock: each at [startAt] (uptime ms) plus its time, with the cursor sweeping when
     * [sweep] is on, until [durationMs] has passed. Cancelling (a new round, Hear again) stops it where it is.
     */
    suspend fun run(cues: List<Cue>, startAt: Long, durationMs: Long, sweep: Boolean, onCue: (Cue) -> Unit = {}) = coroutineScope {
        val frames = launch {
            while (true) {
                androidx.compose.runtime.withFrameNanos { }
                cursor.floatValue = if (sweep) ((SystemClock.uptimeMillis() - startAt) / pattern.tickMs).toFloat() else -1f
            }
        }
        try {
            for (c in cues) {
                val wait = startAt + c.atMs - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)
                fire(c); onCue(c)
            }
            val rest = startAt + durationMs - SystemClock.uptimeMillis()
            if (rest > 0) delay(rest)
        } finally { frames.cancel() }
    }

    // One round: Listen, one bar of count-in, then the player's turn. A new round or Hear again restarts it.
    LaunchedEffect(round, replay, finished) {
        if (finished) return@LaunchedEffect
        taps.clear(); review = null; count = 0; cursor.floatValue = -1f
        phase = RPhase.LISTEN
        if (round == 0 && replay == 0) { env.voice.speak(trainer.prompt().spoken); voiceDone() }
        Log.d(TAG, "ear: rhythm level ${trainer.level} round ${round + 1}: ${trainer.describe()}")
        delay(350)
        run(trainer.demo(), SystemClock.uptimeMillis() + 50, pattern.totalMs + 250, sweep = true)
        phase = RPhase.COUNT
        cursor.floatValue = -1f
        val countStart = SystemClock.uptimeMillis() + 300
        tapStart = countStart + pattern.countInMs
        var beat = 0
        run(trainer.countIn(), countStart, pattern.countInMs, sweep = false) { count = ++beat }
        phase = RPhase.TAP
        count = 0
        run(trainer.tapPulse(), tapStart, pattern.totalMs + TAIL_MS, sweep = true)
        // Time is up: grade the taps.
        val a = trainer.grade(taps.map { it.ms }, OUTPUT_LATENCY_MS)
        Log.d(TAG, "ear: rhythm graded ${taps.size} taps: ${a.line?.text} score=${trainer.score}")
        review = a
        phase = RPhase.REVIEW
        cursor.floatValue = -1f
        a.line?.let { env.voice.speak(it.spoken) }
        env.report(EarEvent.Round(trainer.kind, trainer.level, a.clean))
        version++
    }

    // A tap from the pad or a MIDI key. Times are uptime milliseconds.
    fun tap(now: Long) {
        val rel = now - tapStart
        val inWindow = (phase == RPhase.TAP && rel <= pattern.totalMs + TAIL_MS) || (phase == RPhase.COUNT && rel >= -EARLY_TAP_MS)
        if (env.sound()) {
            env.sampler.noteOn(RhythmPattern.DEMO_NOTE, 100)
            scope.launch { delay(160); env.sampler.noteOff(RhythmPattern.DEMO_NOTE) }
        }
        if (!inWindow) return
        taps += TapMark(rel, trainer.liveGrade(rel - OUTPUT_LATENCY_MS))
    }
    val tapNow by rememberUpdatedState(::tap)
    LaunchedEffect(env.midi) { env.midi.events.collect { e -> if (e is NoteEvent.On) tapNow(SystemClock.uptimeMillis()) } }
    DisposableEffect(Unit) { onDispose { env.voice.stop(); env.sampler.noteOff(RhythmPattern.DEMO_NOTE) } }

    val pose = when {
        phase == RPhase.REVIEW -> if (review?.clean == true) Pose.GREAT_JOB else Pose.MISTAKE
        phase == RPhase.LISTEN -> Pose.LISTENING
        trainer.streak >= 3 -> Pose.STREAK
        else -> Pose.GET_READY
    }
    val c = MaterialTheme.colorScheme
    val rev = review
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameHeader(trainer, label, pose, if (finished) "" else trainer.prompt().text, onExit)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${pattern.meter.label}, ${pattern.bpm} beats per minute", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { replay++ }, enabled = phase != RPhase.REVIEW) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp)); Text("Hear again")
                }
                TextButton(onClick = {
                    env.voice.stop(); review = null; trainer.skip(); version++; replay = 0
                    if (trainer.finished) onFinished()
                }, enabled = phase != RPhase.REVIEW) { Text("Skip") }
            }
            BeatGrid(pattern, cursor, taps, trainer.result, phase, Modifier.fillMaxWidth().height(132.dp))
            // What to do now, in words and, during the count-in, in big numbers.
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                val status = when (phase) {
                    RPhase.LISTEN -> "Listen"
                    RPhase.COUNT -> if (count > 0) "$count" else "Get ready"
                    RPhase.TAP -> "Your turn: tap it"
                    RPhase.REVIEW -> ""
                }
                Text(status, style = if (phase == RPhase.COUNT && count > 0) Numeric.merge(MaterialTheme.typography.displaySmall) else MaterialTheme.typography.titleLarge,
                    color = if (phase == RPhase.TAP) c.primary else c.onSurface, textAlign = TextAlign.Center)
            }
            TapPad(padDown, phase == RPhase.TAP || phase == RPhase.COUNT, onDown = { padDown = it }, onTap = { tapNow(it) },
                modifier = Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(if (rev != null) 220.dp else 4.dp))
        }
        if (rev != null) CheerBar(
            if (rev.clean) Cheer.GOOD else Cheer.MISS,
            if (rev.clean) cheerTitle(round + trainer.score) else missTitle(round + trainer.score),
            // The engine line can open with "Not quite:"; the title already says it.
            rev.line?.text?.let { t -> if (rev.clean) t else t.removePrefix("Not quite: ").replaceFirstChar { it.uppercase() } },
            if (round + 1 >= trainer.rounds) "See results" else "Continue",
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            env.voice.stop(); review = null; replay = 0
            trainer.next(); version++
            if (trainer.finished) onFinished()
        }
    }
}

/** The big pad: it takes the first touch of every finger, the instant it lands, and says so by lighting up. */
@Composable
private fun TapPad(down: Boolean, active: Boolean, onDown: (Boolean) -> Unit, onTap: (Long) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val face by animateColorAsState(when { down && active -> c.primary.copy(alpha = 0.45f); active -> c.primaryContainer; else -> c.surfaceContainerHigh }, label = "pad")
    val onDownNow by rememberUpdatedState(onDown)
    val onTapNow by rememberUpdatedState(onTap)
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(face).border(2.dp, if (active) c.primary.copy(alpha = 0.5f) else c.outlineVariant, RoundedCornerShape(28.dp))
        .semantics { contentDescription = "Tap pad" }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    e.changes.forEach { ch -> if (ch.pressed && !ch.previousPressed) onTapNow(ch.uptimeMillis) }
                    onDownNow(e.changes.any { it.pressed })
                }
            }
        }, contentAlignment = Alignment.Center) {
        Text(if (active) "Tap here" else "Wait for your turn", style = MaterialTheme.typography.titleMedium,
            color = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
    }
}

/**
 * The pattern as a grid: a dot per beat (the current one swells), the notes as bars on the top lane (rests as hollow
 * marks), the cursor sweeping, and the player's taps on the lane below, each coloured by its grade. After grading the
 * marks take their final colours, and notes that were missed are outlined.
 */
@Composable
private fun BeatGrid(pattern: RhythmPattern, cursor: FloatState, taps: List<TapMark>, result: com.bigpapituna.sharpears.engine.ear.RhythmResult?, phase: RPhase, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val good = c.tertiary; val ok = c.primary; val bad = c.error
    val lane = c.surfaceContainerHigh; val ink = c.onSurface; val dim = c.onSurfaceVariant
    Canvas(modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow)) {
        val pad = 14.dp.toPx()
        val cw = (size.width - 2 * pad) / pattern.totalTicks
        fun x(tick: Float) = pad + tick * cw
        val cur = cursor.floatValue
        val beatY = 16.dp.toPx(); val noteTop = 36.dp.toPx(); val noteH = 34.dp.toPx(); val tapY = noteTop + noteH + 28.dp.toPx()
        // Lanes.
        drawRoundRect(lane, Offset(pad - 6.dp.toPx(), noteTop - 6.dp.toPx()), Size(size.width - 2 * pad + 12.dp.toPx(), noteH + 12.dp.toPx()), CornerRadius(12.dp.toPx()))
        // Beat dots, the one under the cursor larger and filled.
        val beats = pattern.bars * pattern.meter.beats
        val currentBeat = if (cur >= 0) (cur / pattern.meter.ticksPerBeat).toInt() else -1
        for (b in 0 until beats) {
            val bx = x(b * pattern.meter.ticksPerBeat.toFloat())
            val on = b == currentBeat
            drawCircle(if (on) ok else dim.copy(alpha = 0.5f), if (on) 6.dp.toPx() else 3.5.dp.toPx(), Offset(bx, beatY))
            if (b % pattern.meter.beats == 0 && b > 0) drawLine(dim.copy(alpha = 0.5f), Offset(bx - 4.dp.toPx(), noteTop - 6.dp.toPx()), Offset(bx - 4.dp.toPx(), noteTop + noteH + 6.dp.toPx()), 2.dp.toPx())
        }
        // Notes and rests.
        var noteIndex = 0
        pattern.slots.forEach { s ->
            val left = x(s.startTick.toFloat()) + 2.dp.toPx()
            if (s.rest) {
                drawRoundRect(dim.copy(alpha = 0.6f), Offset(left, noteTop + noteH / 2 - 1.dp.toPx()), Size(minOf(14.dp.toPx(), s.ticks * cw - 4.dp.toPx()), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            } else {
                val width = s.ticks * cw - 4.dp.toPx()
                val sounding = cur >= s.startTick && cur < s.startTick + s.ticks && (phase == RPhase.LISTEN || phase == RPhase.TAP)
                val missed = result?.notes?.getOrNull(noteIndex)?.grade == Grade.MISSED
                val fill = if (sounding) ok else ink.copy(alpha = 0.78f)
                drawRoundRect(fill, Offset(left, noteTop), Size(width, noteH), CornerRadius(10.dp.toPx()))
                if (missed) drawRoundRect(bad, Offset(left, noteTop), Size(width, noteH), CornerRadius(10.dp.toPx()), Stroke(3.dp.toPx()))
                noteIndex++
            }
        }
        // The cursor.
        if (cur >= 0 && cur <= pattern.totalTicks + 1) {
            drawLine(ok, Offset(x(cur), noteTop - 12.dp.toPx()), Offset(x(cur), tapY + 22.dp.toPx()), 3.dp.toPx())
        }
        // The player's taps.
        drawLine(dim.copy(alpha = 0.35f), Offset(pad, tapY), Offset(size.width - pad, tapY), 1.dp.toPx())
        taps.forEach { t ->
            val tick = (t.ms - OUTPUT_LATENCY_MS) / pattern.tickMs.toFloat()
            val tx = x(tick.coerceIn(-0.4f, pattern.totalTicks + 0.4f))
            val grade = result?.let { r ->
                r.notes.firstOrNull { it.tapMs == t.ms - OUTPUT_LATENCY_MS }?.grade ?: Grade.OFF
            } ?: t.live
            drawCircle(when (grade) { Grade.GOOD -> good; Grade.OK -> ok; else -> bad }, 8.dp.toPx(), Offset(tx, tapY))
        }
    }
}
