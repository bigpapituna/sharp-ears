package com.bigpapituna.sharpears.ui.play

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.PlayedNote
import com.bigpapituna.sharpears.engine.RunRecording
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.SharpEarsTheme

/**
 * Debug only: the Play corner's screens before the integrator wires them in. MainActivity opens it with
 * `--es screen play [--es demo jam|pupsays|clap|duet|quest|share]`; without a demo it lists them. Raw frames come from
 * the existing tuner tap ([frameTap]) until MicInput.rawFrames exists.
 */
@Composable
fun PlayDemo(start: String?, mic: MicInput, midi: MidiDevices, sampler: Sampler, voice: Voice, sound: () -> Boolean, onClose: () -> Unit) {
    var demo by remember { mutableStateOf(start) }
    val context = LocalContext.current
    val clicks = remember { Clicks(context) }
    DisposableEffect(Unit) { onDispose { clicks.release() } }
    val back = { if (demo != null && start == null) demo = null else onClose() }
    BackHandler { back() }
    Log.d(TAG, "play demo: ${demo ?: "menu"}")
    SharpEarsTheme {
        when (demo) {
            "jam" -> JamScreen(mic, midi, mic.frameTap(), onExit = back)
            "pupsays" -> PupSaysScreen(mic, midi, sampler, voice, sound, onExit = back,
                onFinished = { r -> Log.d(TAG, "play demo: pup says results $r") })
            "clap" -> ClapAlongScreen(mic, mic.frameTap(), clicks, voice, sound, onExit = back)
            "duet" -> DuetPicker(onStart = { d -> Log.d(TAG, "play demo: duet $d"); back() }, onCancel = back)
            "quest", "share" -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ExitPill(back)
                QuestCard(plan = null, onTask = { t -> Log.d(TAG, "play demo: quest task ${t.kind} opens ${t.opens}") })
                PupSays(Pose.GREAT_JOB, "A pretend run of Ode to Joy, to try the share sheet.")
                ShareConcertButton(DEMO_RUN, "Ode to Joy", stars = 2)
            }
            else -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExitPill(onClose)
                Text("Play corner", style = MaterialTheme.typography.headlineSmall)
                listOf("jam" to "Jam", "pupsays" to "Pup says", "clap" to "Clap along", "duet" to "Duet picker", "quest" to "Quest card and Send to someone")
                    .forEach { (id, label) -> OutlinedButton(onClick = { demo = id }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
            }
        }
    }
}

/** The first phrase of Ode to Joy as a run (500 ms a beat), for trying Send to someone without playing. */
private val DEMO_RUN: RunRecording = run {
    val notes = listOf(64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62)
    val beats = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.5, 0.5, 2.0)
    var at = 0L
    RunRecording("demo-ode", notes.indices.map { i ->
        val len = (beats[i] * 500).toLong()
        PlayedNote(notes[i], at, len - 40, 70 + (i % 4) * 8).also { at += len }
    })
}
