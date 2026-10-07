package com.bigpapituna.sharpears

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.MIC_CHECK_ID
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.micCheckExercise
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.formatTime

const val TAG = "SharpEars"

private sealed interface Screen {
    data object Home : Screen
    data class Player(val song: Song, val start: PlayerStart) : Screen
    data object MicSetup : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private lateinit var mic: MicInput
    private val samplerReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        val repo = SongRepository(assets)
        // ponytail: one bundled song plus the mic check and a three-screen switch; the library and navigation arrive in P8
        val elise = repo.load(repo.index.first().id)
        val micCheck = micCheckExercise()
        Log.d(TAG, "song ${elise.id}: ${elise.notes.size} notes, ${elise.groups.size} groups, ${elise.durationMs} ms, " +
            "right=${elise.notes.count { it.hand == Hand.RIGHT }} left=${elise.notes.count { it.hand == Hand.LEFT }}")

        // Debug builds accept extras to open the player directly:
        //   --es song fur-elise|mic-check [--el seek <ms>] [--ez dump true]
        //   [--es mode wait] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,76@600]
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { debuggable }
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)
        val debugStart = PlayerStart(
            mode = if (intent.getStringExtra("mode") == "wait") Mode.WAIT else Mode.LISTEN,
            practice = when (intent.getStringExtra("hands")) { "left" -> Practice.LEFT; "right" -> Practice.RIGHT; else -> Practice.BOTH },
            mic = intent.getBooleanExtra("mic", false),
            inject = intent.getStringExtra("inject")?.takeIf { debuggable },
        )
        val first: Screen = when (openSong) {
            null -> Screen.Home
            MIC_CHECK_ID -> Screen.Player(micCheck, debugStart)
            else -> Screen.Player(elise, debugStart)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(first) }
                    when (val s = screen) {
                        is Screen.Player -> PlayerScreen(s.song, sampler, midi, mic, samplerReady.value,
                            if (s.song.id == openSong) seekMs else 0.0, dump, s.start, onBack = { screen = Screen.Home })
                        Screen.MicSetup -> MicSetupScreen(mic, midi, debuggable, onBack = { screen = Screen.Home })
                        Screen.Home -> Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sharp Ears", style = MaterialTheme.typography.headlineMedium)
                            Text("${elise.title} · ${elise.notes.size} notes · ${formatTime(elise.durationMs.toDouble())}",
                                style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { screen = Screen.Player(elise, PlayerStart()) }) { Text("Play") }
                            FilledTonalButton(onClick = { screen = Screen.Player(micCheck, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true)) }) {
                                Text("Mic check: C major scale")
                            }
                            FilledTonalButton(onClick = { screen = Screen.MicSetup }) { Text("Microphone setup") }
                            Text("Piano samples: VSCO 2 Community Edition upright piano by Simon Dalzell (Ivy Audio) and Versilian Studios, CC0. " +
                                "Für Elise MIDI: Mutopia Project, public domain.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mic.stop()
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
