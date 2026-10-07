package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Section

val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0)
private val LoopColor = Color(0xFF7CE38B)

fun formatTime(ms: Double): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

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
)

@Composable
fun TransportBar(s: BarState, a: BarActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = a.onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back") }
            FilledTonalButton(onClick = a.onPlayPause, enabled = s.ready, modifier = Modifier.width(104.dp)) {
                Text(if (!s.ready) "Loading" else if (s.playing) "Pause" else "Play")
            }
            Chip("Listen", s.mode == Mode.LISTEN) { a.onMode(Mode.LISTEN) }
            Chip("Wait", s.mode == Mode.WAIT) { a.onMode(Mode.WAIT) }
            VerticalDivider(Modifier.height(24.dp))
            Chip("Both", s.practice == Practice.BOTH) { a.onPractice(Practice.BOTH) }
            Chip("Left", s.practice == Practice.LEFT) { a.onPractice(Practice.LEFT) }
            Chip("Right", s.practice == Practice.RIGHT) { a.onPractice(Practice.RIGHT) }
            VerticalDivider(Modifier.height(24.dp))
            SPEEDS.forEach { sp -> Chip("${sp}x".replace(".0x", "x"), s.speed == sp) { a.onSpeed(sp) } }
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
            Chip("A", s.loopStart != null, a.onLoopA)
            Chip("B", s.loopEnd != null, a.onLoopB)
            if (s.loopStart != null || s.loopEnd != null) TextButton(onClick = a.onLoopClear) { Text("Clear loop") }
            Text(s.midiDevice?.let { "MIDI: $it" } ?: "Touch", style = MaterialTheme.typography.labelMedium,
                color = if (s.midiDevice != null) LoopColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
