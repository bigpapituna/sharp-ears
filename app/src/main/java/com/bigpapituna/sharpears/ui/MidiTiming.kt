package com.bigpapituna.sharpears.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.engine.MidiOffset
import com.bigpapituna.sharpears.engine.NoteEvent
import kotlinx.coroutines.delay

/**
 * Settings > Microphone > MIDI keyboard (2.14): the MIDI timing offset, −100 to +100 ms in steps of 5, kept for the phone
 * ([MidiDevices.offsetMs]), with "Tap along to measure": eight clicks after four to count in, a key on each, and the median
 * of how late the taps came is offered as the offset. Perform's scoring alone uses it. [debugScript]: a pretend keyboard played
 * from the calibration's start (debug builds, `--es midi`).
 */
@Composable
fun MidiTimingSetting(midi: MidiDevices, debugScript: String? = null) {
    val v = midi.offsetVersion.value
    var shown by remember(v) { mutableIntStateOf(midi.offsetMs) }
    var calibrating by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MIDI timing offset", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(MidiOffset.label(shown), style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary)
        }
        Text("How late this phone hears your MIDI keyboard. Perform allows for it when it grades your timing; nothing else changes. " +
            "If Perform says you're late when you're not, move it up.", style = MaterialTheme.typography.bodySmall, color = dim)
        Slider(value = shown.toFloat(), onValueChange = { shown = MidiOffset.clamp((it / MidiOffset.STEP).let(Math::round) * MidiOffset.STEP) },
            onValueChangeFinished = { midi.offsetMs = shown },
            // Snapped to 5 ms as it moves; no step marks (forty dots on the track read as noise).
            valueRange = MidiOffset.MIN.toFloat()..MidiOffset.MAX.toFloat(),
            modifier = Modifier.semantics { contentDescription = "MIDI timing offset"; stateDescription = MidiOffset.label(shown) })
        Row {
            TextButton(onClick = { calibrating = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text("Tap along to measure") }
            if (shown != MidiOffset.DEFAULT) TextButton(onClick = { shown = MidiOffset.DEFAULT; midi.offsetMs = MidiOffset.DEFAULT }) { Text("Back to 0") }
        }
    }
    if (calibrating) MidiCalibration(midi, debugScript, onDone = { ms -> if (ms != null) { midi.offsetMs = ms; shown = ms }; calibrating = false })
}

private enum class CalPhase { READY, RUNNING, RESULT }

/**
 * The calibration dialog: ten seconds, "tap along to 8 clicks". Clicks come every [MidiOffset.CLICK_MS] (four to count in,
 * then eight); every MIDI key pressed is timed against them ([MidiOffset.calibrate]). Save keeps the offset found.
 */
@Composable
private fun MidiCalibration(midi: MidiDevices, debugScript: String?, onDone: (Int?) -> Unit) {
    val context = LocalContext.current
    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release() } }
    var phase by remember { mutableStateOf(CalPhase.READY) }
    var beat by remember { mutableIntStateOf(-1) }                 // the click sounding now: 0..3 count-in, 4..11 the eight
    val taps = remember { mutableStateListOf<Long>() }
    var times by remember { mutableStateOf(emptyList<Long>()) }
    var found by remember { mutableStateOf<Int?>(null) }
    val connected = midi.connected.value
    LaunchedEffect(phase) {
        if (phase != CalPhase.RUNNING) return@LaunchedEffect
        taps.clear()
        val start = SystemClock.uptimeMillis() + 600
        val all = (0 until MidiOffset.COUNT_IN + MidiOffset.CLICKS).map { start + it * MidiOffset.CLICK_MS }
        times = MidiOffset.clickTimes(start)
        debugScript?.let { midi.debugPlay(it) }
        Log.d(TAG, "midi calibration: clicks at +600 ms, every ${MidiOffset.CLICK_MS} ms, ${MidiOffset.COUNT_IN} to count in")
        all.forEachIndexed { i, t ->
            delay((t - SystemClock.uptimeMillis()).coerceAtLeast(0))
            beat = i
            clicks?.play(i == 0 || i == MidiOffset.COUNT_IN)
        }
        delay(MidiOffset.MATCH_MS + 150)
        found = MidiOffset.calibrate(times, taps.toList())
        Log.d(TAG, "midi calibration: ${taps.size} taps, " + taps.joinToString { "${it - (times.minByOrNull { c -> kotlin.math.abs(c - it) } ?: it)}" } + " ms -> offset $found")
        phase = CalPhase.RESULT
    }
    LaunchedEffect(midi) {
        midi.events.collect { e -> if (e is NoteEvent.On && phase == CalPhase.RUNNING) taps += SystemClock.uptimeMillis() }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Tap along to 8 clicks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    connected == null && debugScript == null -> "Connect a MIDI keyboard first. Then play any key with each click, after four clicks to count you in."
                    phase == CalPhase.RESULT -> found?.let { if (it == 0) "Your keyboard is right on time. No offset needed." else "Your keyboard is heard ${MidiOffset.label(it)}. Save it, and Perform allows for it." }
                        ?: "Only ${taps.size} taps came with a click. Try again, one key with each click."
                    else -> "Play any key on your keyboard with each click, after four clicks to count you in. About ten seconds."
                }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                // Twelve dots: four to count in (dimmer), eight to tap on; the one sounding lit.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until MidiOffset.COUNT_IN + MidiOffset.CLICKS) {
                        val counted = i >= MidiOffset.COUNT_IN
                        Box(Modifier.size(if (counted) 14.dp else 10.dp).background(when {
                            i == beat && phase == CalPhase.RUNNING -> MaterialTheme.colorScheme.primary
                            i < beat || phase == CalPhase.RESULT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }, CircleShape))
                    }
                }
                if (phase == CalPhase.RUNNING) Text("${taps.size} taps", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        },
        confirmButton = {
            when (phase) {
                CalPhase.READY -> TextButton(onClick = { phase = CalPhase.RUNNING }, enabled = connected != null || debugScript != null) { Text("Start") }
                CalPhase.RUNNING -> {}
                CalPhase.RESULT -> if (found != null) TextButton(onClick = { onDone(found) }) { Text("Save") }
                    else TextButton(onClick = { phase = CalPhase.RUNNING }) { Text("Try again") }
            }
        },
        dismissButton = {
            Row {
                if (phase == CalPhase.RESULT && found != null) TextButton(onClick = { phase = CalPhase.RUNNING }) { Text("Again") }
                TextButton(onClick = { onDone(null) }) { Text("Cancel") }
            }
        },
    )
}
