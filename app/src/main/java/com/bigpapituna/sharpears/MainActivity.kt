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
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.progressionSong
import com.bigpapituna.sharpears.ui.ChordsScreen
import com.bigpapituna.sharpears.ui.ChordsTab
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.formatTime

const val TAG = "SharpEars"

private sealed interface Screen {
    data object Home : Screen
    data class Player(val song: Song, val start: PlayerStart) : Screen
    data object MicSetup : Screen
    data class Chords(val tab: ChordsTab = ChordsTab.DICTIONARY, val inject: String? = null) : Screen
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
        //   [--es mode wait] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,60+64+67@600]
        //   --es song prog:<index>:<key 0-11>   (a chord progression, e.g. prog:0:7 = I–V–vi–IV in G)
        //   --es screen chords [--es tab practice|progressions] [--es inject ...]
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
        val first: Screen = when {
            debuggable && intent.getStringExtra("screen") == "chords" -> Screen.Chords(
                when (intent.getStringExtra("tab")) { "practice" -> ChordsTab.PRACTICE; "progressions" -> ChordsTab.PROGRESSIONS; else -> ChordsTab.DICTIONARY },
                debugStart.inject,
            )
            openSong == null -> Screen.Home
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, debugStart)
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) -> Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), debugStart) }
            else -> Screen.Player(elise, debugStart)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(first) }
                    when (val s = screen) {
                        is Screen.Player -> PlayerScreen(s.song, sampler, midi, mic, samplerReady.value,
                            if (s.song.id == openSong) seekMs else 0.0, dump, s.start,
                            onBack = { screen = if (s.song.id.startsWith("prog-")) Screen.Chords(ChordsTab.PROGRESSIONS) else Screen.Home })
                        Screen.MicSetup -> MicSetupScreen(mic, midi, debuggable, onBack = { screen = Screen.Home })
                        is Screen.Chords -> ChordsScreen(sampler, midi, mic, s.tab, s.inject,
                            onPlayProgression = { p, key -> screen = Screen.Player(progressionSong(p, key), PlayerStart(Mode.WAIT)) },
                            onBack = { screen = Screen.Home })
                        Screen.Home -> Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sharp Ears", style = MaterialTheme.typography.headlineMedium)
                            Text("${elise.title} · ${elise.notes.size} notes · ${formatTime(elise.durationMs.toDouble())}",
                                style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { screen = Screen.Player(elise, PlayerStart()) }) { Text("Play") }
                            FilledTonalButton(onClick = { screen = Screen.Player(micCheck, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true)) }) {
                                Text("Mic check: C major scale")
                            }
                            FilledTonalButton(onClick = { screen = Screen.Chords() }) { Text("Chords") }
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
