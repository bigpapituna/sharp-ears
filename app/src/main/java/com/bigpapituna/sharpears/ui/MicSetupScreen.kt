package com.bigpapituna.sharpears.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.noteName

private val Good = Color(0xFF5BE37D)
private val Bad = Color(0xFFFF4D5E)

/** Fixture labels for the recording session; the file names say what was played. */
private val FIXTURES = listOf(
    "scale-right" to "Right-hand scale",
    "scale-left" to "Left-hand scale",
    "chords" to "Chords",
    "elise" to "Für Elise opening",
    "silence" to "30 s of silence",
)

@Composable
fun MicSetupScreen(mic: MicInput, midi: MidiDevices, debuggable: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var granted by remember { mutableStateOf(mic.hasPermission()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var micAddress by remember { mutableStateOf(mic.micAddress) }
    var heard by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var sensitivity by remember { mutableFloatStateOf(mic.matcher.config.sensitivityDb.toFloat()) }
    var wrongDb by remember { mutableFloatStateOf(mic.matcher.config.wrongOnsetDb.toFloat()) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    // Listen while this screen is open; the check note is middle C. MIDI, if connected, is recorded as truth.
    val visible = onScreen()
    DisposableEffect(granted, micAddress, visible) {
        if (granted && visible) {
            mic.micAddress = micAddress
            mic.matcher.expected = setOf(60); mic.matcher.group = setOf(60)
            mic.start()
            midi.onTruth = mic::onMidiTruth
        }
        onDispose { mic.stopRecording(); mic.stop(); midi.onTruth = null; mic.matcher.expected = emptySet() }
    }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            when (e) {
                is MicEvent.Strike -> { heard++; last = "Heard ${noteName(e.midi)} (rise ${"%.0f".format(e.riseDb)} dB)" to true; Log.d(TAG, "micsetup heard ${noteName(e.midi)} rise=${e.riseDb}") }
                is MicEvent.Wrong -> { last = "Heard a different note" to false; Log.d(TAG, "micsetup wrong") }
            }
        }
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Microphone setup", style = MaterialTheme.typography.headlineSmall)
        }
        if (!granted) {
            Text("Sharp Ears listens to your piano through the microphone. Nothing is recorded or sent anywhere.")
            Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
            return@Column
        }

        Text("Microphone", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mic.builtInMics().forEach { (address, label) ->
                FilterChip(selected = (micAddress ?: "bottom") == address, onClick = { micAddress = address }, label = { Text(label) })
            }
        }
        Text(mic.source.value ?: "starting…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Level", style = MaterialTheme.typography.titleMedium)
        val level = mic.level.floatValue
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { ((level + 90f) / 90f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
            Text("%.0f dB".format(level), modifier = Modifier.width(64.dp))
        }

        Text("Check: play middle C (C4) a few times", style = MaterialTheme.typography.titleMedium)
        Text(last?.first ?: "Listening…", color = when (last?.second) { true -> Good; false -> Bad; null -> MaterialTheme.colorScheme.onSurface })
        Text("Heard C4 $heard times", style = MaterialTheme.typography.bodySmall)

        Text("Sensitivity: %.0f dB".format(sensitivity), style = MaterialTheme.typography.titleMedium)
        Text("Lower hears softer notes; raise it if room noise triggers notes.", style = MaterialTheme.typography.bodySmall)
        Slider(sensitivity, { sensitivity = it }, valueRange = -80f..-40f, onValueChangeFinished = {
            mic.saveConfig(mic.matcher.config.copy(sensitivityDb = sensitivity.toDouble())); Log.d(TAG, "mic sensitivity=$sensitivity")
        })
        Text("Wrong-note trigger: %.0f dB jump".format(wrongDb), style = MaterialTheme.typography.titleMedium)
        Text("Raise it if correct notes flash red.", style = MaterialTheme.typography.bodySmall)
        Slider(wrongDb, { wrongDb = it }, valueRange = 3f..15f, onValueChangeFinished = {
            mic.saveConfig(mic.matcher.config.copy(wrongOnsetDb = wrongDb.toDouble())); Log.d(TAG, "mic wrongOnsetDb=$wrongDb")
        })

        if (debuggable) {
            Text("Record test fixtures (debug)", style = MaterialTheme.typography.titleMedium)
            Text(
                midi.connected.value?.let { "MIDI $it is recorded alongside as the truth." }
                    ?: "No MIDI keyboard: recordings will have no truth.",
                style = MaterialTheme.typography.bodySmall,
            )
            val recording = mic.recording.value
            if (recording != null) {
                Text("Recording “$recording”… play, then stop.", color = Bad)
                Button(onClick = { mic.stopRecording(); savedNote = "Saved $recording" }) { Text("Stop") }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXTURES.forEach { (id, label) ->
                        FilledTonalButton(onClick = {
                            val name = "$id-${(micAddress ?: "bottom")}"
                            savedNote = if (mic.startRecording(name) != null) null else "Mic is not running"
                        }) { Text(label) }
                    }
                }
            }
            savedNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
