package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Section

val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0)

fun formatTime(ms: Double): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun TransportBar(
    playing: Boolean,
    ready: Boolean,
    positionMs: Double,
    durationMs: Long,
    speed: Double,
    sections: List<Section>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSpeed: (Double) -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back") }
        FilledTonalButton(onClick = onPlayPause, enabled = ready, modifier = Modifier.width(112.dp)) {
            Text(if (!ready) "Loading" else if (playing) "Pause" else "Play")
        }
        SPEEDS.forEach { s ->
            FilterChip(selected = speed == s, onClick = { onSpeed(s) }, label = { Text("${s}x".replace(".0x", "x")) })
        }
        Text(formatTime(positionMs), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
            if (sections.isNotEmpty() && durationMs > 0) {
                Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                    sections.forEach { s ->
                        val x = size.width * s.startMs / durationMs
                        drawLine(androidx.compose.ui.graphics.Color.Gray, Offset(x, 6f), Offset(x, 18f), strokeWidth = 3f)
                    }
                }
            }
            Slider(
                value = (positionMs / durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                onValueChange = { onSeek(it.toDouble() * durationMs) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Song position" },
            )
        }
        Text(formatTime(durationMs.toDouble()), style = MaterialTheme.typography.labelLarge)
    }
}
