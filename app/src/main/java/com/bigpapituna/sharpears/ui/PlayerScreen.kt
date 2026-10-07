package com.bigpapituna.sharpears.ui

import androidx.activity.compose.LocalActivity
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.rememberCoroutineScope
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.LaneGeometry
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.fitRange
import com.bigpapituna.sharpears.engine.noteName

private val CorrectColor = Color(0xFF5BE37D)
private val WrongColor = Color(0xFFFF4D5E)

/**
 * Landscape player: transport bar, falling-notes lane, keyboard.
 * Listen mode plays the song; wait mode holds at each note group until the keys are played on the keyboard.
 * [startMs] and [debugDump] support adb checks: open frozen at a time and log every visible note's rectangle.
 */
@Composable
fun PlayerScreen(song: Song, sampler: Sampler, ready: Boolean, startMs: Double, debugDump: Boolean, onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val range = remember(song) { fitRange(song.notes) }
    val transport = remember(song) { Transport(song, playable = range) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var mode by remember { mutableStateOf(Mode.LISTEN) }
    var practice by remember { mutableStateOf(Practice.BOTH) }
    var loopStart by remember { mutableStateOf<Double?>(null) }
    var loopEnd by remember { mutableStateOf<Double?>(null) }
    var version by remember { mutableIntStateOf(0) }              // bumps when sounding/expected/waiting change
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val flashes = remember { mutableStateMapOf<Int, Color>() }    // short green flash on a correct key
    var wrongFlash by remember { mutableIntStateOf(0) }           // bumps on each wrong note
    val scope = rememberCoroutineScope()

    fun apply(events: List<NoteEvent>) {
        events.forEach { e -> when (e) { is NoteEvent.On -> sampler.noteOn(e.midi, e.velocity); is NoteEvent.Off -> sampler.noteOff(e.midi) } }
        version++
    }
    fun seek(ms: Double) { apply(transport.seek(ms)); position = transport.positionMs }
    fun pause() { apply(transport.pause()); playing = false; Log.d(TAG, "transport pause at ${transport.positionMs.toLong()} ms") }
    fun play() {
        transport.play(); playing = true
        Log.d(TAG, "transport play from ${transport.positionMs.toLong()} ms speed=${transport.speed} mode=${transport.mode} hands=${transport.practice}")
    }
    fun setLoop(a: Double?, b: Double?) {
        loopStart = a; loopEnd = b
        transport.loop = if (a != null && b != null && b > a) a..b else null
        Log.d(TAG, "loop ${transport.loop?.let { "${it.start.toLong()}..${it.endInclusive.toLong()} ms" } ?: "off (A=${a?.toLong()} B=${b?.toLong()})"}")
    }
    fun keyStruck(midi: Int) {
        val result = transport.noteInput(midi)
        Log.d(TAG, "input ${noteName(midi)} -> $result at ${transport.positionMs.toLong()} ms")
        when (result) {
            InputResult.WRONG -> { wrongFlash++; Log.d(TAG, "redFlash") }
            InputResult.CORRECT, InputResult.GROUP_DONE -> scope.launch { flashes[midi] = CorrectColor; delay(200); flashes.remove(midi) }
            InputResult.IGNORED -> {}
        }
        version++
    }

    LaunchedEffect(song) { if (startMs > 0) seek(startMs); Log.d(TAG, "player open ${song.id} range=${noteName(range.first)}..${noteName(range.last)}") }
    DisposableEffect(song) { onDispose { apply(transport.pause()) } }
    BackHandler(onBack = onBack)

    // Frame loop: tick the transport with the real frame delta while playing.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        var wasWaiting = transport.waiting
        while (transport.playing) {
            withFrameNanos { now ->
                val before = transport.positionMs
                val events = transport.tick((now - last) / 1_000_000.0)
                if (events.isNotEmpty()) apply(events)
                if (transport.positionMs < before) Log.d(TAG, "loop wrap ${before.toLong()} -> ${transport.positionMs.toLong()} ms")
                last = now
                position = transport.positionMs
                if (transport.waiting != wasWaiting) {
                    wasWaiting = transport.waiting
                    version++
                    if (wasWaiting) Log.d(TAG, "wait at ${transport.positionMs.toLong()} ms expect=${transport.expected.map { noteName(it.midi) }}")
                }
            }
        }
        playing = false
        Log.d(TAG, "transport stopped at ${transport.positionMs.toLong()} ms")
    }

    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var laneBox by remember { mutableStateOf<Rect?>(null) }
    val landscape = LocalWindowInfo.current.containerSize.let { it.width > it.height }

    Column(Modifier.fillMaxSize().background(LaneBackground).safeDrawingPadding()) {
        TransportBar(
            BarState(playing, ready, position, song.durationMs, speed, mode, practice, loopStart, loopEnd, song.sections),
            BarActions(
                onBack = onBack,
                onPlayPause = { if (playing) pause() else play() },
                onSpeed = { speed = it; transport.speed = it; Log.d(TAG, "transport speed=$it") },
                onMode = { m -> apply(transport.pause()); playing = false; transport.mode = m; mode = m; Log.d(TAG, "mode=$m") },
                onPractice = { practice = it; transport.practice = it; version++; Log.d(TAG, "hands=$it") },
                onSeek = { seek(it) },
                onLoopA = { setLoop(transport.positionMs, loopEnd) },
                onLoopB = { setLoop(loopStart, transport.positionMs) },
                onLoopClear = { setLoop(null, null) },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        NoteLane(
            song = song, positionMs = position, lowMidi = range.first, highMidi = range.last,
            dimmed = { mode == Mode.WAIT && practice != Practice.BOTH && !transport.required(it) },
            onScrubStart = { wasPlayingBeforeScrub = playing; if (playing) pause() },
            onScrub = { seek(transport.positionMs + it) },
            onScrubEnd = { Log.d(TAG, "scrub to ${transport.positionMs.toLong()} ms"); if (wasPlayingBeforeScrub) play() },
            modifier = Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { c ->
                if (debugDump) laneBox = Rect(c.positionInWindow(), c.size.toSize())
            },
        )
        // Debug: once the frozen screen settles, log each visible note's rectangle in window pixels for adb checks.
        LaunchedEffect(laneBox, position, playing) {
            val box = laneBox ?: return@LaunchedEffect
            if (playing) return@LaunchedEffect
            delay(400)
            val lane = LaneGeometry(box.height)
            val keys = KeyboardLayout(range.first, range.last, box.width, box.height)
            Log.d(TAG, "laneDump pos=${position.toLong()} top=${box.top.toInt()} height=${box.height.toInt()} pxPerMs=${lane.pxPerMs}")
            lane.visible(song, position, song.notes.maxOf { it.durationMs }).filter { it.midi in range }.forEach { n ->
                val k = keys.rect(n.midi)
                Log.d(TAG, "laneNote ${noteName(n.midi)} hand=${n.hand} x=${(box.left + k.centerX).toInt()} " +
                    "top=${(box.top + lane.topY(n, position)).toInt()} bottom=${(box.top + lane.bottomY(n, position)).toInt()}")
            }
        }
        // Keys the song wants pulse in their hand color while wait mode holds.
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "pulse")
        val expected = remember(version) { transport.expected }
        val sounding = remember(version) { transport.sounding.toList() }
        val lit: Map<Int, Color> = buildMap {
            sounding.forEach { put(it.midi, handColor(it.hand)) }
            expected.forEach { put(it.midi, handColor(it.hand).copy(alpha = pulse)) }
            putAll(touched)
            putAll(flashes)
        }
        var wrongVisible by remember { mutableStateOf(false) }
        LaunchedEffect(wrongFlash) { if (wrongFlash > 0) { wrongVisible = true; delay(150); wrongVisible = false } }
        Keyboard(
            lowMidi = range.first, highMidi = range.last, lit = lit,
            onNoteOn = { m, v ->
                Log.d(TAG, "NoteOn ${noteName(m)} midi=$m vel=$v src=touch")
                touched[m] = RightHandColor; sampler.noteOn(m, v); keyStruck(m)
            },
            onNoteOff = { m -> Log.d(TAG, "NoteOff ${noteName(m)} midi=$m src=touch"); touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().weight(if (landscape) 0.45f else 0.3f)
                .then(if (wrongVisible) Modifier.border(4.dp, WrongColor) else Modifier),
        )
    }
}

private fun handColor(h: Hand) = if (h == Hand.RIGHT) RightHandColor else LeftHandColor
