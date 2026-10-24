package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.SightScore
import com.bigpapituna.sharpears.engine.sightPiece
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * Sight-reading: four bars never seen before, played once in wait mode with note names and fingers hidden.
 * Scored on time to the end plus wrong notes; the best per level is kept.
 */
@Composable
fun SightReadScreen(deps: LessonDeps, startLevel: Int = 1, inject: String? = null, onBack: () -> Unit) {
    var level by remember { mutableIntStateOf(startLevel.coerceIn(1, 2)) }
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    // Injected (debug) runs use a fixed seed so the notes are known in advance.
    var seed by remember { mutableLongStateOf(if (inject != null) 1L else System.nanoTime()) }
    val piece = remember(level, seed) { sightPiece(level, seed).also { Log.d(TAG, "sight: level $level ${it.keyName}: ${it.exercise.right} | ${it.exercise.left}") } }
    var result by remember(level, seed) { mutableStateOf<Pair<SightScore, Boolean>?>(null) }
    val prefs = deps.prefs
    val best = remember(level, prefs.version.intValue) { prefs.sightBest(level) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { deps.voice.stop(); onBack() }) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to lessons") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Sight-read", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(best?.let { "Best: ${it.label}" } ?: "No best yet at this level", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(start = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(level == 1, { level = 1 }, label = { Text("Level 1") })
                FilterChip(level == 2, { level = 2 }, label = { Text("Level 2") })
            }
            // The pup says what this level reads, over the piece.
            PupSays(Pose.GET_READY,
                if (level == 1) "C major, right hand, quarter and half notes."
                else "${piece.keyName}, eighth notes too, and a bass note in the left hand.",
                Modifier.padding(horizontal = 4.dp, vertical = 10.dp), size = 56.dp, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                SharpEarsTheme(dark = true) {
                    key(level, seed) {
                        PlayerScreen(piece.exercise.toSong("sight-$level-$seed", "Sight-read"), null, deps.sampler, deps.midi, deps.mic, deps.voice, prefs,
                            deps.ready, 0.0, false,
                            PlayerStart(Mode.WAIT, piece.exercise.practice, mic = deps.prefs.micDefault && deps.mic.hasPermission(), inject = inject),
                            onBack = onBack,
                            onFinished = { r ->
                                val s = SightScore.of(r.realMs, r.wrongKeys)
                                val newBest = prefs.saveSightBest(level, s)
                                progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.SightRead(level))
                                Log.d(TAG, "sight: level $level done in ${s.seconds} s, ${s.wrong} wrong, points ${s.points}, newBest=$newBest")
                                deps.voice.speak((if (newBest) "A new best: " else "Done: ") + "${s.seconds} seconds" +
                                    if (s.wrong == 0) ", no wrong notes." else ", ${s.wrong} wrong.")
                                result = s to newBest
                            },
                            embedded = Embedded("Names hidden, the notes wait", labels = false, autoStart = false),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium),
                        )
                    }
                }
            }
        }
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            ChunkyButton(onClick = { seed = System.nanoTime() }) { Text("New piece") }
        }
    }
    // The pup's verdict on the piece, and the way on to the next one.
    result?.let { (sc, newBest) ->
        val good = newBest || sc.wrong <= 2
        CheerBar(if (good) Cheer.GOOD else Cheer.MISS,
            when { newBest -> "New best!"; good -> cheerTitle(seed.toInt()); else -> "Keep reading" },
            // The title already says "New best!" (it read "New best! New best: 0:20, no wrong notes.").
            (if (newBest) "" else "Done: ") + sc.label + ".", "New piece", Modifier.align(Alignment.BottomCenter)) { seed = System.nanoTime() }
    }
    }
}
