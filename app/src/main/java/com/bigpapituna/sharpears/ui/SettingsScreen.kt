package com.bigpapituna.sharpears.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.KeyLabels
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta

@Composable
fun SettingsScreen(prefs: Prefs, songs: List<SongMeta>, versionName: String, onMicSetup: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // Re-read after each write so the controls show the saved values.
    val v = prefs.version.intValue
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹") }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        Section("Keyboard") {
            Text("Key labels", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(KeyLabels.OFF to "Off", KeyLabels.C_ONLY to "C keys", KeyLabels.ALL to "All white keys").forEach { (k, label) ->
                    FilterChip(prefs.keyLabels == k && v >= 0, { prefs.keyLabels = k }, label = { Text(label) })
                }
            }
        }
        Section("Sound") {
            Toggle("App sound", "Touch keys and the parts the app plays for you. The microphone still mutes it unless headphones are on.",
                prefs.sound && v >= 0) { prefs.sound = it }
        }
        Section("Microphone") {
            Text("Choose the mic, test middle C, and set the sensitivity.", style = MaterialTheme.typography.bodyMedium, color = InkDim)
            FilledTonalButton(onClick = onMicSetup) { Text("Microphone setup") }
        }
        Section("Progress") {
            Text("Clears resume positions, played marks, and best Perform scores. Chord mastery is kept.",
                style = MaterialTheme.typography.bodyMedium, color = InkDim)
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset song progress") }
        }
        Section("About") {
            Text("Sharp Ears $versionName, a personal piano trainer.", style = MaterialTheme.typography.bodyMedium)
            Text("Piano samples: VSCO 2 Community Edition upright piano by Simon Dalzell (Ivy Audio) and Versilian Studios, CC0.",
                style = MaterialTheme.typography.bodySmall, color = InkDim)
            Text("Tutorial videos play in YouTube's official embedded player and belong to their channels.",
                style = MaterialTheme.typography.bodySmall, color = InkDim)
            songs.forEach { s ->
                Text("${s.title} (${s.composer.ifEmpty { "unknown" }}): ${s.license}. ${s.attribution}",
                    style = MaterialTheme.typography.bodySmall, color = InkDim)
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles, so the touch target is the row, and TalkBack reads it as one switch.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
        Switch(checked, onCheckedChange = null)
    }
}
