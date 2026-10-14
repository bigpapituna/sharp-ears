package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Voice
import kotlinx.coroutines.delay

/**
 * Between two parts of "Practice today": one line, said by the voice too ("Next: chords"), then the part opens
 * by itself. Skip passes over that part; End stops the session.
 */
@Composable
fun Interstitial(line: String, voice: Voice, onGo: () -> Unit, onSkip: () -> Unit, onEnd: () -> Unit) {
    LaunchedEffect(line) {
        Log.d(TAG, "daily: $line")
        voice.speak(line, interrupt = true)
        delay(400)
        while (voice.speaking.value) delay(50)
        delay(900)
        onGo()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The pup announces the next part in his bubble.
            PupSays(Pose.GET_READY, line, Modifier.semantics(mergeDescendants = true) { heading() }, size = 120.dp,
                style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSkip) { Text("Skip") }
                TextButton(onClick = onEnd) { Text("End for today") }
            }
        }
    }
}
