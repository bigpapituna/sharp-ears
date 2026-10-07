package com.bigpapituna.sharpears

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand

const val TAG = "SharpEars"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: P1 loads the first bundled song at launch to prove the pipeline; the library screen replaces this in P8
        val repo = SongRepository(assets)
        val song = repo.load(repo.index.first().id)
        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }}")
        val summary = "${song.title}\n${song.notes.size} notes · ${song.durationMs / 60_000}:" +
            "%02d".format(song.durationMs / 1000 % 60)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Sharp Ears\n\n$summary", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
