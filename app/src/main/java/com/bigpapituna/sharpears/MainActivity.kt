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
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.formatTime

const val TAG = "SharpEars"

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private val samplerReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        val repo = SongRepository(assets)
        // ponytail: one bundled song and a two-screen switch; the library screen and navigation arrive in P8
        val song = repo.load(repo.index.first().id)
        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }}")

        // Debug builds accept: --es song <id> [--el seek <ms>] [--ez dump true] to open the player frozen at a time.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { debuggable }
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    var inPlayer by remember { mutableStateOf(openSong != null) }
                    if (inPlayer) {
                        PlayerScreen(song, sampler, midi, samplerReady.value, seekMs, dump, onBack = { inPlayer = false })
                    } else {
                        Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sharp Ears", style = MaterialTheme.typography.headlineMedium)
                            Text("${song.title} · ${song.notes.size} notes · ${formatTime(song.durationMs.toDouble())}",
                                style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { inPlayer = true }) { Text("Play") }
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
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
