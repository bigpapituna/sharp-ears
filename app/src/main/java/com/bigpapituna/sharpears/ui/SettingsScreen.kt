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
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.KeyLabels
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta

/** Settings tab: plain sections separated by space, no cards. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(prefs: Prefs, songs: List<SongMeta>, versionName: String,
                   /** The reminder was switched or its time changed: set (or cancel) the alarm. */
                   onReminder: () -> Unit = {},
                   onMicSetup: () -> Unit,
                   onPrivacy: () -> Unit, onLicences: () -> Unit) {
    var pickTime by remember { mutableStateOf(false) }
    // The reminder time as the phone shows times (6:30 PM, or 18:30).
    val timeLabel = java.time.LocalTime.of(prefs.reminderMinutes / 60, prefs.reminderMinutes % 60)
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)).replace(' ', ' ')
    val context = androidx.compose.ui.platform.LocalContext.current
    // Progress export and import (Storage Access Framework: the player picks where the file goes).
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importing by remember { mutableStateOf<Map<String, Map<String, Any>>?>(null) }
    var progressMessage by remember { mutableStateOf<String?>(null) }
    val exportTo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progressMessage = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bigpapituna.sharpears.data.ProgressFiles.export(context, uri) } }
                .fold({ "Progress saved." }, { "Could not save: ${it.message}" })
        }
    }
    val importFrom = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bigpapituna.sharpears.data.ProgressFiles.read(context, uri) } }
                .onSuccess { importing = it }
                .onFailure { progressMessage = it.message ?: "Could not read the file." ; android.util.Log.d(com.bigpapituna.sharpears.TAG, "progress: import refused: ${it.message}") }
        }
    }
    // Android 13 and up ask before an app may post notifications: asked when the reminder is switched on.
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "reminder: notification permission granted=$granted")
        prefs.reminderOn = granted
        onReminder()
    }
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
            Toggle("Feedback light", "A flash rises from the line on a right note, a soft glow runs along it on a wrong one. Off: keys flash instead.",
                prefs.feedbackLight && v >= 0) { prefs.feedbackLight = it }
            Toggle("Beat lines", "A faint line across the notes on every beat. Rhythm lessons always show them.",
                prefs.beatLines && v >= 0) { prefs.beatLines = it }

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

            Section("Reminder")
            Toggle("Daily reminder", "A notification at $timeLabel each day saying what is waiting.",
                prefs.reminderOn && v >= 0) { on ->
                if (on && !com.bigpapituna.sharpears.reminder.Reminder.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33)
                    askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else { prefs.reminderOn = on; onReminder() }
            }
            TextButton(onClick = { pickTime = true }, modifier = Modifier.semantics {
                contentDescription = "Reminder time, $timeLabel, tap to change"
            }) { Text("Time: $timeLabel") }

            Section("Progress")
            Text("Save everything the app remembers (progress, chord mastery, microphone setup, game bests) to a file, or bring it back from one.",
                style = MaterialTheme.typography.bodyMedium, color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { exportTo.launch("sharp-ears-progress-${java.time.LocalDate.now()}.json") }) { Text("Export progress") }
                TextButton(onClick = { importFrom.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Import progress") }
            }
            progressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Text("Reset clears resume positions, played marks, best scores and practice time. Chord lessons keep their progress.",
                style = MaterialTheme.typography.bodyMedium, color = dim, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { confirmReset = true }) { Text("Reset song progress", color = MaterialTheme.colorScheme.error) }

            Section("About")
            Text("Sharp Ears $versionName, a personal piano trainer.", style = MaterialTheme.typography.bodyMedium)
            Text("Piano samples by Simon Dalzell (Ivy Audio) and Versilian Studios. Tutorial videos belong to their channels on YouTube.",
                style = MaterialTheme.typography.bodySmall, color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPrivacy) { Text("Privacy") }
                TextButton(onClick = onLicences) { Text("Licences and credits (${songs.size} songs)") }
            }
        }
    }
    if (pickTime) {
        val state = androidx.compose.material3.rememberTimePickerState(prefs.reminderMinutes / 60, prefs.reminderMinutes % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Reminder time") },
            // The AM/PM selector takes the accent, not Material's default pink: one accent on every screen.
            text = { androidx.compose.material3.TimePicker(state, colors = androidx.compose.material3.TimePickerDefaults.colors(
                periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer)) },
            confirmButton = { TextButton(onClick = { prefs.reminderMinutes = state.hour * 60 + state.minute; pickTime = false; onReminder() }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
    importing?.let { files ->
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text("Replace your progress?") },
            text = { Text("The file holds ${files.values.sumOf { it.size }} saved settings. They replace everything the app remembers now, and this cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    com.bigpapituna.sharpears.data.ProgressFiles.replace(context, files)
                    importing = null
                    // Start again so every screen reads the imported values.
                    (context as? android.app.Activity)?.recreate()
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { importing = null }) { Text("Cancel") } },
        )
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset song progress?") },
        text = { Text("Resume positions, played marks, best scores and practice time will be cleared.") },
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
