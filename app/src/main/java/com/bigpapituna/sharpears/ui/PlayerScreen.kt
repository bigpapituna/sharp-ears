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

/**
 * Landscape player: transport bar, falling-notes lane, keyboard. Listen mode only (P3).
 * [startMs] and [debugDump] support adb checks: open frozen at a time and log every visible note's rectangle.
 */
@Composable
fun PlayerScreen(song: Song, sampler: Sampler, ready: Boolean, startMs: Double, debugDump: Boolean, onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val transport = remember(song) { Transport(song) }
    var position by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var soundingVersion by remember { mutableIntStateOf(0) }      // bumps when transport.sounding changes
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val range = remember(song) { fitRange(song.notes) }

    fun apply(events: List<NoteEvent>) {
        if (events.isEmpty()) return
        events.forEach { e -> when (e) { is NoteEvent.On -> sampler.noteOn(e.midi, e.velocity); is NoteEvent.Off -> sampler.noteOff(e.midi) } }
        soundingVersion++
    }
    fun seek(ms: Double) { apply(transport.seek(ms)); position = transport.positionMs }
    fun pause() { apply(transport.pause()); playing = false; Log.d(TAG, "transport pause at ${transport.positionMs.toLong()} ms") }
    fun play() { transport.play(); playing = true; Log.d(TAG, "transport play from ${transport.positionMs.toLong()} ms speed=${transport.speed}") }

    LaunchedEffect(song) { if (startMs > 0) seek(startMs); Log.d(TAG, "player open ${song.id} range=${noteName(range.first)}..${noteName(range.last)}") }
    DisposableEffect(song) { onDispose { apply(transport.pause()) } }
    BackHandler(onBack = onBack)

    // Frame loop: tick the transport with the real frame delta while playing.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (transport.playing) {
            withFrameNanos { now ->
                apply(transport.tick((now - last) / 1_000_000.0))
                last = now
                position = transport.positionMs
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
            playing = playing, ready = ready, positionMs = position, durationMs = song.durationMs, speed = speed,
            sections = song.sections,
            onBack = onBack,
            onPlayPause = { if (playing) pause() else play() },
            onSpeed = { speed = it; transport.speed = it; Log.d(TAG, "transport speed=$it") },
            onSeek = { seek(it) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        NoteLane(
            song = song, positionMs = position, lowMidi = range.first, highMidi = range.last,
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
        val lit: Map<Int, Color> = remember(soundingVersion, touched.toMap()) {
            transport.sounding.associate { it.midi to (if (it.hand == Hand.RIGHT) RightHandColor else LeftHandColor) } + touched
        }
        Keyboard(
            lowMidi = range.first, highMidi = range.last, lit = lit,
            onNoteOn = { m, v -> Log.d(TAG, "NoteOn ${noteName(m)} midi=$m vel=$v src=touch"); touched[m] = RightHandColor; sampler.noteOn(m, v) },
            onNoteOff = { m -> Log.d(TAG, "NoteOff ${noteName(m)} midi=$m src=touch"); touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().weight(if (landscape) 0.45f else 0.3f),
        )
    }
}
