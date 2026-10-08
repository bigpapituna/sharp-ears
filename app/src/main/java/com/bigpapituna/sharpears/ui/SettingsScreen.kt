package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.KeyLabels
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta

/** Settings tab: plain sections separated by space, no cards. */
@Composable
fun SettingsScreen(prefs: Prefs, songs: List<SongMeta>, versionName: String, onMicSetup: () -> Unit) {
    // Re-read after each write so the controls show the saved values.
    val v = prefs.version.intValue
    var confirmReset by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

            Section("Keyboard")
            Text("Key labels: none, every C with its octave, or every white key.", style = MaterialTheme.typography.bodyMedium, color = dim)
            SingleChoiceSegmentedButtonRow(Modifier.padding(top = 4.dp)) {
                val opts = listOf(KeyLabels.OFF to "Off", KeyLabels.C_ONLY to "C only", KeyLabels.ALL to "All")
                opts.forEachIndexed { i, (k, label) ->
                    SegmentedButton(prefs.keyLabels == k && v >= 0, { prefs.keyLabels = k }, SegmentedButtonDefaults.itemShape(i, opts.size), icon = {}) { Text(label) }
                }
            }

            Section("Falling notes")
            Toggle("Note names", "The letter inside each note, with the octave on every C.", prefs.noteNames && v >= 0) { prefs.noteNames = it }
            Toggle("Finger numbers", "A suggested finger for each note, 1 for the thumb to 5 for the little finger.",
                prefs.fingerNumbers && v >= 0) { prefs.fingerNumbers = it }

            Section("Sound")
            Toggle("App sound", "Touch keys and the parts the app plays for you. The microphone mutes it unless headphones are on.",
                prefs.sound && v >= 0) { prefs.sound = it }

            Section("Voice")
            Toggle("Spoken guidance", "The app reads lesson steps and feedback aloud. It waits while the microphone listens, unless headphones are on.",
                prefs.voice && v >= 0) { prefs.voice = it }

            Section("Microphone")
            Text("Choose the mic, test middle C, and set the sensitivity.", style = MaterialTheme.typography.bodyMedium, color = dim)
            TextButton(onClick = onMicSetup, modifier = Modifier.padding(start = 0.dp)) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp)); Text("Microphone setup")
            }

            Section("Progress")
            Text("Clears resume positions, played marks and best scores. Chord lessons keep their progress.",
                style = MaterialTheme.typography.bodyMedium, color = dim)
            TextButton(onClick = { confirmReset = true }) { Text("Reset song progress", color = MaterialTheme.colorScheme.error) }

            Section("About")
            Text("Sharp Ears $versionName, a personal piano trainer.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(4.dp))
            listOf(
                "Piano samples: VSCO 2 Community Edition upright piano by Simon Dalzell (Ivy Audio) and Versilian Studios, CC0.",
                "Type: Geist and Geist Mono by Vercel, SIL Open Font License. Icons: Material Symbols by Google, Apache 2.0.",
                "Tutorial videos play in YouTube's own embedded player and belong to their channels.",
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = dim) }
            Spacer(Modifier.size(8.dp))
            Text("Songs", style = MaterialTheme.typography.titleSmall)
            songs.forEach { s ->
                Text("${s.title} (${s.composer.ifEmpty { "unknown" }}): ${s.license}. ${s.attribution}.".replace("..", "."),
                    style = MaterialTheme.typography.bodySmall, color = dim)
            }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset song progress?") },
        text = { Text("Resume positions, played marks and best scores will be cleared.") },
        confirmButton = { TextButton(onClick = { prefs.resetProgress(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        modifier = Modifier.padding(top = 28.dp, bottom = 2.dp).semantics { heading() })

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles, so the touch target is the row, and TalkBack reads it as one switch.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}
