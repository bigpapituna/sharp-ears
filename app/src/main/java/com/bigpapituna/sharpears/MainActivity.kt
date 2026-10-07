package com.bigpapituna.sharpears

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.RightHandColor

const val TAG = "SharpEars"

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sampler = Sampler(this)
        // ponytail: P1/P2 show the first bundled song and a keyboard on one screen; the library and player screens replace this
        val repo = SongRepository(assets)
        val song = repo.load(repo.index.first().id)
        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }}")
        val summary = "${song.title} · ${song.notes.size} notes · ${song.durationMs / 60_000}:" +
            "%02d".format(song.durationMs / 1000 % 60)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val pressed = remember { mutableStateMapOf<Int, Color>() }
                    Column(Modifier.safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sharp Ears", style = MaterialTheme.typography.headlineMedium)
                        Text(summary, style = MaterialTheme.typography.bodyLarge)
                        Text("Piano samples: VSCO 2 Community Edition upright piano by Simon Dalzell (Ivy Audio) and Versilian Studios, CC0.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Keyboard(
                            lowMidi = 48, highMidi = 84,   // C3–C6
                            lit = pressed,
                            onNoteOn = { midi, vel ->
                                Log.d(TAG, "NoteOn ${noteName(midi)} midi=$midi vel=$vel src=touch")
                                pressed[midi] = RightHandColor
                                sampler.noteOn(midi, vel)
                            },
                            onNoteOff = { midi ->
                                Log.d(TAG, "NoteOff ${noteName(midi)} midi=$midi src=touch")
                                pressed.remove(midi)
                                sampler.noteOff(midi)
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        sampler.release()
        super.onDestroy()
    }
}
