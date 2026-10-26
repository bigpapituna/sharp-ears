package com.bigpapituna.sharpears.ui.chords

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.chords.KnownChords
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.nav.ChordWords
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * "I know these chords" (2.14, engine/chords/KnownChords.kt): one screen of the chords the songs by chords use, the six first
 * chords first, each a pill to tap. The ones already known (Leitner box 3 or more) start picked. Under them the count of
 * songs those chords play changes as they are picked. [onSave] gets the chords picked (the caller writes them at the known
 * box); [onLater] puts the offer away. A full-screen dialog over wherever it was asked from (Today, the empty lists).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnownChordsSheet(songs: List<PopSong>, known: Set<Chord>, onSave: (Set<Chord>) -> Unit, onLater: () -> Unit) {
    val choices = remember(songs) { KnownChords.choices(songs) }
    val picked = remember { mutableStateListOf<Chord>().apply { addAll(choices.filter { it in known }) } }
    val playable = remember(picked.toList(), known) { PopFind.playableCount(songs, known + picked) }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        com.bigpapituna.sharpears.ui.DialogBarsFollowTheme()
        BackHandler(onBack = onLater)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(KnownChords.TITLE, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    PupSays(Pose.LEARNING, KnownChords.LINE, size = 72.dp, style = MaterialTheme.typography.bodyLarge)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        choices.forEach { c -> ChordPick(c, c in picked) { on -> if (on) picked += c else picked -= c } }
                    }
                    Text(KnownChords.countLine(if (picked.isEmpty() && known.isEmpty()) 0 else playable), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLater) { Text("Not now") }
                    Box(Modifier.weight(1f))
                    ChunkyButton(onClick = { onSave(picked.toSet()) }, enabled = picked.isNotEmpty()) { Text(KnownChords.SAVE) }
                }
            }
        }
    }
}

/** One chord as a big pill: its name, and its name in words for TalkBack ("A minor"). */
@Composable
private fun ChordPick(chord: Chord, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.heightIn(min = 56.dp).widthIn(min = 76.dp).clip(CircleShape)
        .background(if (on) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .border(if (on) 2.dp else 1.dp, if (on) c.primary else c.outline, CircleShape)
        .toggleable(on, role = Role.Checkbox, onValueChange = onChange)
        .semantics { this.contentDescription = ChordWords.name(chord) }
        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(chord.name, style = MaterialTheme.typography.titleLarge, color = if (on) c.onPrimaryContainer else c.onSurface, textAlign = TextAlign.Center)
    }
}
