package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Section

val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0)
private val LoopColor = Color(0xFF7CE38B)

fun formatTime(ms: Double): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }
private fun speedLabel(s: Double) = "${s}x".replace(".0x", "x")

/** Everything the bar shows; the player owns the state. */
data class BarState(
    val playing: Boolean,
    val ready: Boolean,
    val positionMs: Double,
    val durationMs: Long,
    val speed: Double,
    val mode: Mode,
    val practice: Practice,
    val loopStart: Double?,
    val loopEnd: Double?,
    val sections: List<Section>,
    val midiDevice: String? = null,
    val micOn: Boolean = false,
    /** Read only while drawing the level dot, so level updates do not recompose the player. */
    val micLevelDb: () -> Float = { -120f },
    val title: String = "",
    /** null: the song has no tutorial video. */
    val videoOn: Boolean? = null,
    /** Short live status, e.g. the Perform streak. */
    val status: String = "",
)

class BarActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSpeed: (Double) -> Unit,
    val onMode: (Mode) -> Unit,
    val onPractice: (Practice) -> Unit,
    val onSeek: (Double) -> Unit,
    val onLoopA: () -> Unit,
    val onLoopB: () -> Unit,
    val onLoopClear: () -> Unit,
    val onMic: () -> Unit = {},
    val onVideo: () -> Unit = {},
)

@Composable
fun TransportBar(s: BarState, a: BarActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = a.onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹") }
            Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Button(onClick = a.onPlayPause, enabled = s.ready, modifier = Modifier.width(100.dp)) {
                Text(if (!s.ready) "Loading" else if (s.playing) "Pause" else "Play")
            }
            Segments(listOf("Listen" to Mode.LISTEN, "Wait" to Mode.WAIT, "Perform" to Mode.PERFORM), s.mode, "Mode", a.onMode)
            Segments(listOf("Both" to Practice.BOTH, "L" to Practice.LEFT, "R" to Practice.RIGHT), s.practice, "Hands", a.onPractice,
                spoken = mapOf("L" to "Left hand", "R" to "Right hand", "Both" to "Both hands"))
            // One chip cycles the speed, which keeps the row short enough for the cover screen.
            FilterChip(s.speed != 1.0, { a.onSpeed(SPEEDS[(SPEEDS.indexOf(s.speed) + 1) % SPEEDS.size]) },
                label = { Text(speedLabel(s.speed)) },
                modifier = Modifier.semantics { contentDescription = "Speed ${speedLabel(s.speed)}, tap to change" })
        }
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatTime(s.positionMs), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp)) {
                    if (s.durationMs <= 0) return@Canvas
                    fun x(ms: Double) = (size.width * ms / s.durationMs).toFloat()
                    s.sections.forEach { drawLine(Color.Gray, Offset(x(it.startMs.toDouble()), 4f), Offset(x(it.startMs.toDouble()), 14f), 3f) }
                    s.loopStart?.let { drawLine(LoopColor, Offset(x(it), 0f), Offset(x(it), size.height), 4f) }
                    s.loopEnd?.let { drawLine(LoopColor, Offset(x(it), 0f), Offset(x(it), size.height), 4f) }
                }
                Slider(
                    value = (s.positionMs / s.durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                    onValueChange = { a.onSeek(it.toDouble() * s.durationMs) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Song position" },
                )
            }
            Text(formatTime(s.durationMs.toDouble()), style = MaterialTheme.typography.labelLarge)
            Chip("A", s.loopStart != null, a.onLoopA, "Loop start")
            Chip("B", s.loopEnd != null, a.onLoopB, "Loop end")
            if (s.loopStart != null || s.loopEnd != null) TextButton(onClick = a.onLoopClear) { Text("Clear") }
            Chip(if (s.micOn) "Mic on" else "Mic", s.micOn, a.onMic, "Microphone")
            if (s.micOn) {
                // Level dot: brighter when louder (-70 dB .. -10 dB).
                Canvas(Modifier.size(12.dp)) { drawCircle(LoopColor.copy(alpha = ((s.micLevelDb() + 70f) / 60f).coerceIn(0.1f, 1f))) }
            }
            s.videoOn?.let { on -> Chip("Video", on, a.onVideo, "Tutorial video") }
            Text(
                s.status.ifEmpty {
                    when { s.micOn && s.midiDevice != null -> "MIDI = judge"; s.midiDevice != null -> "MIDI"; s.micOn -> ""; else -> "Touch" }
                },
                style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = if (s.status.isNotEmpty()) MaterialTheme.colorScheme.tertiary
                        else if (s.midiDevice != null) LoopColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> Segments(options: List<Pair<String, T>>, selected: T, name: String, onPick: (T) -> Unit,
                         spoken: Map<String, String> = emptyMap()) {
    SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = name }) {
        options.forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = value == selected, onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size), icon = {},
                modifier = Modifier.semantics { spoken[label]?.let { contentDescription = it } },
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, spoken: String) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) },
        modifier = Modifier.semantics { contentDescription = spoken; stateDescription = if (selected) "on" else "off" })
