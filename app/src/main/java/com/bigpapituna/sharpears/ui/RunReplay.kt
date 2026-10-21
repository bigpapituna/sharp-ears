package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.RunRecording
import com.bigpapituna.sharpears.engine.Song

/** Colour of the player's own notes on the replay lane: bright, thin, over the song's dimmed blocks. */
val PlayedInk = Color(0xFFF6F2EA)

/**
 * "Hear my run": the player's notes played back through the sampler at the run's speed, over the song's notes
 * (dimmed) on the lane, so early and late notes show as thin bright blocks off their marks. Play and stop only.
 */
@Composable
fun RunReplay(song: Song, run: RunRecording, low: Int, high: Int, sampler: Sampler, sound: Boolean, onClose: () -> Unit) {
    val played = remember(run) { run.asNotes() }
    val startMs = remember(run) { (played.firstOrNull()?.startMs ?: 0L) - 1500.0 }
    val endMs = remember(run) { (played.maxOfOrNull { it.startMs + it.durationMs } ?: 0L) + 800.0 }
    var pos by remember { mutableDoubleStateOf(startMs) }
    var playing by remember { mutableStateOf(false) }
    val sounding = remember { mutableSetOf<Note>() }
    var lit by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }

    fun silence() { sounding.forEach { sampler.noteOff(it.midi) }; sounding.clear(); lit = emptyMap() }
    DisposableEffect(Unit) { onDispose { silence() } }

    LaunchedEffect(playing) {
        if (!playing) { silence(); return@LaunchedEffect }
        if (pos >= endMs) pos = startMs
        Log.d(TAG, "replay ${run.songId}: ${played.size} notes from ${pos.toLong()} ms at speed ${run.speed}")
        var last = withFrameNanos { it }
        // Frames stop off screen: the first one back leaves the time away out (it played every skipped note at once).
        var awayLast = com.bigpapituna.sharpears.engine.ForegroundClock.app.awayMs()
        var next = played.indexOfFirst { it.startMs >= pos }.let { if (it < 0) played.size else it }
        while (playing && pos < endMs) {
            withFrameNanos { now ->
                val away = com.bigpapituna.sharpears.engine.ForegroundClock.app.awayMs().let { a -> (a - awayLast).also { awayLast = a } }
                val to = pos + ((now - last) / 1_000_000.0 - away).coerceAtLeast(0.0) * run.speed
                last = now
                var changed = false
                sounding.removeAll { n -> (n.startMs + n.durationMs <= to).also { if (it) { sampler.noteOff(n.midi); changed = true } } }
                while (next < played.size && played[next].startMs < to) {
                    val n = played[next++]
                    sounding.removeAll { it.midi == n.midi }
                    if (sound) sampler.noteOn(n.midi, n.velocity.coerceIn(30, 127))
                    sounding += n; changed = true
                }
                if (changed) lit = sounding.associate { it.midi to PlayedInk }
                pos = to
            }
        }
        if (pos >= endMs) { Log.d(TAG, "replay ${run.songId}: end"); playing = false }
    }

    Column(Modifier.fillMaxSize().background(LaneBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to the song") }
            Column(Modifier.weight(1f)) {
                Text("Your run", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Your notes are the thin bright ones; the song's are dimmed behind them.", style = MaterialTheme.typography.bodySmall,
                    color = InkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatTime(maxOf(pos, 0.0)), style = Numeric.merge(MaterialTheme.typography.labelLarge), color = Ink)
            FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = if (playing) "Stop" else "Play my run")
            }
        }
        NoteLane(song, pos, low, high, onScrubStart = {}, onScrub = {}, onScrubEnd = {}, modifier = Modifier.fillMaxWidth().weight(1f),
            dimmed = { true }, showFingers = false, played = played)
        Keyboard(low, high, lit = lit, onNoteOn = { _, _ -> }, onNoteOff = {}, modifier = Modifier.fillMaxWidth().weight(0.45f))
    }
}
