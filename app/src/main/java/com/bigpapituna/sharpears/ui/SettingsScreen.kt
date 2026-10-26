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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.bigpapituna.sharpears.engine.nav.Simple

/**
 * Settings: plain sections separated by space, no cards. Since 2.7 under six headers, in this order: Family (everyone on the
 * phone, and the progress file that carries them all), Playing (keys, falling notes, sound, voice, wrong keys, the reminder),
 * Briefs and email, Microphone, About, then Testing when this build has it. Every switch keeps its preference and its tests.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SettingsScreen(prefs: Prefs, songs: List<SongMeta>, versionName: String,
                   /** The speaking voice, for the voice picker (null in previews). */
                   voice: com.bigpapituna.sharpears.audio.Voice? = null,
                   /** The microphone, for Playing's "Show wrong keys" (the same switch as Microphone setup's); null hides it. */
                   mic: com.bigpapituna.sharpears.audio.MicInput? = null,
                   /** The reminder was switched or its time changed: set (or cancel) the alarm. */
                   onReminder: () -> Unit = {},
                   onMicSetup: () -> Unit,
                   onPrivacy: () -> Unit, onLicences: () -> Unit,
                   /** Settings is a pushed screen (from the gear on You): a back arrow beside the heading. */
                   onBack: (() -> Unit)? = null,
                   /** After Prefs.resetProgress(): the other stores that keep scores (ear training, practice tools). */
                   onResetProgress: () -> Unit = {},
                   /** The Family section (ui/family/FamilySettings.kt), first on the screen; null hides it. */
                   family: (@Composable () -> Unit)? = null,
                   /** The daily reminder is the phone owner's (one alarm per phone); other profiles do not see it. */
                   showReminder: Boolean = true,
                   /**
                    * The page's scroll and its "Show all settings", kept by the caller so they outlive a visit to Privacy,
                    * Licences or Mic setup (2.8.4, QA D14: the screen is left there and came back at the top).
                    */
                   scroll: androidx.compose.foundation.ScrollState = rememberScrollState(),
                   showAllState: androidx.compose.runtime.MutableState<Boolean>? = null,
                   /** 2.14: the MIDI keyboard's timing offset (ui/MidiTiming.kt), under Microphone; null hides it. */
                   midiTiming: (@Composable () -> Unit)? = null) {
    var pickTime by remember { mutableStateOf(false) }
    // The reminder time as the phone shows times (6:30 PM, or 18:30): its 12 or 24-hour setting, as the picker uses.
    val timeLabel = android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
        .format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, prefs.reminderMinutes / 60); set(java.util.Calendar.MINUTE, prefs.reminderMinutes % 60) }.time).replace(' ', ' ')
    val context = androidx.compose.ui.platform.LocalContext.current
    // Progress export and import (Storage Access Framework: the player picks where the file goes).
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importing by remember { mutableStateOf<com.bigpapituna.sharpears.engine.ProgressBackup.Backup?>(null) }
    var progressMessage by remember { mutableStateOf<String?>(null) }
    val exportTo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            progressMessage = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bigpapituna.sharpears.data.ProgressFiles.export(context, uri) } }
                .fold({ "Progress saved." }, { com.bigpapituna.sharpears.analytics.Tester.error("progress export"); "Could not save: ${it.message}" })
        }
    }
    val importFrom = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.bigpapituna.sharpears.data.ProgressFiles.read(context, uri) } }
                .onSuccess { importing = it }
                .onFailure { com.bigpapituna.sharpears.analytics.Tester.error("progress import"); progressMessage = it.message ?: "Could not read the file." ; android.util.Log.d(com.bigpapituna.sharpears.TAG, "progress: import refused: ${it.message}") }
        }
    }
    // Android 13 and up ask before an app may post notifications: asked when the reminder is switched on.
    // Refused (or refused twice, when Android stops asking and the switch did nothing at all): say where to allow them.
    var notifyRefused by remember { mutableStateOf(false) }
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "reminder: notification permission granted=$granted")
        prefs.reminderOn = granted
        notifyRefused = !granted
        onReminder()
    }
    // Re-read after each write so the controls show the saved values.
    val v = prefs.version.intValue
    var confirmReset by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    // The layouts (2.8, engine/nav/Simple.kt): Simple shows the essentials, Advanced adds keys, falling notes, wrong keys and
    // briefs, Full everything; the rest waits behind "Show all settings".
    val layout = LocalLayout.current
    val ownShowAll = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showAll by (showAllState ?: ownShowAll)
    fun shows(part: Simple.SettingsPart) = Simple.settingsShows(part, Simple.shown(layout.layout, showAll))
    Column(Modifier.fillMaxWidth().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { IconButton(onClick = it) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") } }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = if (onBack != null) 4.dp else 0.dp).semantics { heading() })
            }

            // Family: everyone on the phone, and the progress file that carries them all (export, import; reset is yours).
            Section("Family")
            family?.invoke()
            if (shows(Simple.SettingsPart.PROGRESS)) {
            if (family != null) Sub("Progress")
            Text("Save everything the app remembers for everyone on this phone (progress, chord mastery, microphone setup, game bests) " +
                "to a file, or bring it back from one.",
                style = MaterialTheme.typography.bodyMedium, color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { exportTo.launch("sharp-ears-progress-${java.time.LocalDate.now()}.json") }) { Text("Export progress") }
                TextButton(onClick = { importFrom.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Import progress") }
            }
            progressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Text("Reset clears resume positions, played marks, best scores, practice time, ear training and theory scores and the practice tools' records. " +
                "Learn and chord lessons, stickers, XP and achievements are kept.",
                style = MaterialTheme.typography.bodyMedium, color = dim, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { confirmReset = true }) { Text("Reset progress", color = MaterialTheme.colorScheme.error) }
            }

            // Playing: how the keys, the falling notes, the sound and the voice behave, wrong keys, and the reminder.
            Section("Playing")
            val profiles = com.bigpapituna.sharpears.ui.family.LocalProfiles.current
            val me = com.bigpapituna.sharpears.ui.family.LocalProfile.current
            // The layout (2.8; 2.7's Simple mode switch): a grown-up's own three-way choice (kept per profile); a kid never has it.
            if (!me.isKid && shows(Simple.SettingsPart.LAYOUT)) {
                Text(Simple.SETTING_TITLE, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(Simple.SETTING_LINE, style = MaterialTheme.typography.bodySmall, color = dim)
                LayoutPicker(Modifier.padding(top = 4.dp, bottom = 4.dp).widthIn(max = 420.dp).fillMaxWidth())
            }
            if (shows(Simple.SettingsPart.KEYS)) {
            Sub("Keys")
            Text("Key labels: none, every C with its octave, or every white key.", style = MaterialTheme.typography.bodyMedium, color = dim)
            SingleChoiceSegmentedButtonRow(Modifier.padding(top = 4.dp)) {
                val opts = listOf(KeyLabels.OFF to "Off", KeyLabels.C_ONLY to "C only", KeyLabels.ALL to "All")
                opts.forEachIndexed { i, (k, label) ->
                    SegmentedButton(prefs.keyLabels == k && v >= 0, { prefs.keyLabels = k; com.bigpapituna.sharpears.data.Briefs.setting("keyLabels", k) }, SegmentedButtonDefaults.itemShape(i, opts.size), icon = {}) { Text(label) }
                }
            }
            }

            if (shows(Simple.SettingsPart.FALLING_NOTES)) {
            Sub("Falling notes")
            // Colour notes belong to the profile (2.1): on for kids from the start, a choice for grown-ups.
            if (profiles != null) Toggle("Colour notes", "Each note and key in its own colour: C red, D orange, E yellow, F green, G teal, A blue, B purple, " +
                "as in many beginners' books.", me.colourNotes) { on -> profiles.update(me.id) { it.copy(colourNotes = on) } }
            Toggle("Note names", "The letter inside each note, with the octave on every C.", prefs.noteNames && v >= 0) { prefs.noteNames = it; com.bigpapituna.sharpears.data.Briefs.setting("noteNames", it) }
            Toggle("Finger numbers", "A suggested finger for each note, 1 for the thumb to 5 for the little finger.",
                prefs.fingerNumbers && v >= 0) { prefs.fingerNumbers = it; com.bigpapituna.sharpears.data.Briefs.setting("fingerNumbers", it) }
            Toggle("Feedback light", "A flash rises from the line on a right note, a soft glow runs along it on a wrong one. Off: keys flash instead.",
                prefs.feedbackLight && v >= 0) { prefs.feedbackLight = it; com.bigpapituna.sharpears.data.Briefs.setting("feedbackLight", it) }
            Toggle("Beat lines", "A faint line across the notes on every beat. Rhythm lessons always show them.",
                prefs.beatLines && v >= 0) { prefs.beatLines = it; com.bigpapituna.sharpears.data.Briefs.setting("beatLines", it) }
            Toggle("Ghost hand", "A hand over the keys shows which finger plays next.",
                prefs.ghostHand && v >= 0) { prefs.ghostHand = it; com.bigpapituna.sharpears.data.Briefs.setting("ghostHand", it) }
            }
            // Wrong keys heard by the mic: the same switch as Microphone setup's (the phone's mic settings, every profile).
            if (mic != null && shows(Simple.SettingsPart.WRONG_KEYS)) {
                var wrongFlash by remember { mutableStateOf(mic.paintsWrongKeys) }
                Toggle("Show wrong keys", "A key the mic hears you hit lights yellow next to the right one and red further away. Touch and MIDI keys always show.",
                    wrongFlash) { wrongFlash = it; mic.wrongFlash = it; if (it) mic.wrongPaintOff = false; android.util.Log.d(com.bigpapituna.sharpears.TAG, "mic wrongFlash=$it"); com.bigpapituna.sharpears.data.Briefs.setting("wrongFlash", it) }
            }

            Sub("Sound")
            Toggle("App sound", "Touch keys and the parts the app plays for you. The microphone mutes it unless headphones are on.",
                prefs.sound && v >= 0) { prefs.sound = it; com.bigpapituna.sharpears.ui.world.WorldSounds.on = it; com.bigpapituna.sharpears.data.Briefs.setting("sound", it) }
            Toggle("Vibration on touch keys", "A light tap for a right note, a double tap for a wrong one, when you play on the screen.",
                prefs.haptics && v >= 0) { prefs.haptics = it; com.bigpapituna.sharpears.data.Briefs.setting("haptics", it) }

            Sub("Voice")
            Toggle("Spoken guidance", "The app reads lesson steps and feedback aloud. It waits while the microphone listens, unless headphones are on.",
                prefs.voice && v >= 0) { prefs.voice = it; if (!it) voice?.stop(); com.bigpapituna.sharpears.data.Briefs.setting("voice", it) }
            if (prefs.voice) Toggle("Natural voice",
                "A lifelike voice (Kokoro) that runs on this phone, nothing sent anywhere." +
                    if (voice?.naturalModelInstalled == false) " Lines that name notes and times use the phone's voice until the voice model is installed." else "",
                prefs.naturalVoice && v >= 0) { prefs.naturalVoice = it; com.bigpapituna.sharpears.data.Briefs.setting("naturalVoice", it) }
            if (voice != null && prefs.voice && !prefs.naturalVoice) VoicePicker(voice, v)

            // 2.13: the cheer pops (ui/pup/CheerPop.kt), per profile; every layout shows them. Often for everyone by default.
            if (shows(Simple.SettingsPart.ENCOURAGEMENT)) {
                Sub("Encouragement")
                Text(ENCOURAGEMENT_LINE, style = MaterialTheme.typography.bodySmall, color = dim)
                SingleChoiceSegmentedButtonRow(Modifier.padding(top = 4.dp)) {
                    val opts = com.bigpapituna.sharpears.engine.lessons.CheerFrequency.entries
                    opts.forEachIndexed { i, f ->
                        SegmentedButton(prefs.cheerFrequency == f && v >= 0, { prefs.cheerFrequency = f; com.bigpapituna.sharpears.data.Briefs.setting("cheer", f.wire)
                            android.util.Log.d(com.bigpapituna.sharpears.TAG, "cheer: frequency ${f.wire}") },
                            SegmentedButtonDefaults.itemShape(i, opts.size), icon = {}) { Text(f.title) }
                    }
                }
                if (prefs.cheerFrequency != com.bigpapituna.sharpears.engine.lessons.CheerFrequency.OFF && v >= 0)
                    Toggle("Say it aloud", "The pup says the words too, never while the microphone waits for your note.",
                        com.bigpapituna.sharpears.engine.lessons.CheerRules.aloud(me.kidSkin, prefs.cheerAloud)) {
                        prefs.cheerAloud = it; com.bigpapituna.sharpears.data.Briefs.setting("cheerAloud", it)
                    }
            }

            if (showReminder) Sub("Reminder")
            if (showReminder) Toggle("Daily reminder", "A notification at $timeLabel each day saying what is waiting.",
                prefs.reminderOn && v >= 0) { on ->
                if (on && !com.bigpapituna.sharpears.reminder.Reminder.canNotify(context))
                    askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else { prefs.reminderOn = on; onReminder(); com.bigpapituna.sharpears.data.Briefs.setting("reminderOn", on) }
            }
            if (showReminder) TextButton(onClick = { pickTime = true }, modifier = Modifier.semantics {
                contentDescription = "Reminder time, $timeLabel, tap to change"
            }) { Text("Time: $timeLabel") }
            // Checked again on coming back from the phone's settings.
            val shown = onScreen()
            val blocked = remember(shown, v, notifyRefused) { !com.bigpapituna.sharpears.reminder.Reminder.canNotify(context) }
            if (showReminder && blocked && (notifyRefused || prefs.reminderOn)) {
                Text("Notifications are off for Sharp Ears, so no reminder can show. Allow them in the phone's settings.",
                    style = MaterialTheme.typography.bodySmall, color = dim)
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Open notification settings") }
            }

            // Briefs and email (2.8): the one place for the brief by email (the family's address, each child's Daily and Weekly),
            // then who writes the parent brief (BriefAiSettings, at the bottom of BriefsAndEmail). Simple shows it too.
            if (family != null && shows(Simple.SettingsPart.BRIEFS)) {
                Section("Briefs and email")
                Text("Each child's brief is under Family: tap Change beside the child, then Read the brief. Your own week is on You, under My week.",
                    style = MaterialTheme.typography.bodyMedium, color = dim)
                val kids = profiles?.rosterFlow?.collectAsState()?.value?.profiles.orEmpty().filter { it.isKid }
                com.bigpapituna.sharpears.ui.brief.email.BriefsAndEmail(kids)
            }

            Section("Microphone")
            Text("Choose the mic, test middle C, and set the sensitivity.", style = MaterialTheme.typography.bodyMedium, color = dim)
            TextButton(onClick = onMicSetup, modifier = Modifier.padding(start = 0.dp)) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp)); Text("Microphone setup")
            }
            // 2.10.1 (security review finding 5): a build that keeps recordings says so here, with its switch in plain view
            // (the same preference as Microphone setup's; on by default in the debug build, never on in a release).
            if (mic != null && mic.canRecord) {
                var keep by remember { mutableStateOf(mic.keepRecordings) }
                Toggle(com.bigpapituna.sharpears.data.Recordings.KEEP_SWITCH, com.bigpapituna.sharpears.data.Recordings.KEEP_LINE, keep) {
                    keep = it; mic.keepRecordings = it; android.util.Log.d(com.bigpapituna.sharpears.TAG, "mic keepRecordings=$it (Settings)")
                }
            }
            midiTiming?.let { Sub("MIDI keyboard"); it() }

            if (shows(Simple.SettingsPart.ABOUT)) {
            Section("About")
            Text("Sharp Ears $versionName, a personal piano trainer.", style = MaterialTheme.typography.bodyMedium)
            Text("Piano samples by Simon Dalzell (Ivy Audio) and Versilian Studios. Tutorial videos belong to their channels on YouTube.",
                style = MaterialTheme.typography.bodySmall, color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPrivacy) { Text("Privacy") }
                TextButton(onClick = onLicences) { Text("Licences and credits (${songs.size} songs)") }
            }
            }

            // Testing, last (analytics/Tester.kt): "Help Roy test" and the diary, when this build has them; each change behind the gate.
            if (family != null && shows(Simple.SettingsPart.TESTING) && (com.bigpapituna.sharpears.analytics.Tester.rowShown(context) || com.bigpapituna.sharpears.analytics.Tester.diaryBuilt)) {
                Section("Testing")
                val gate = com.bigpapituna.sharpears.ui.family.LocalParentGate.current
                com.bigpapituna.sharpears.analytics.TesterSettings(titled = false, guard = { gate.guard(it) })
            }
            // A layout that cuts Settings: the rest, once, and the three layouts to pick from.
            LayoutMore(Simple.Place.SETTINGS, showAll, { showAll = true }, label = Simple.SHOW_ALL_SETTINGS, edge = 0.dp)
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
    importing?.let { backup ->
        val files = backup.files
        AlertDialog(
            onDismissRequest = { importing = null },
            title = { Text("Replace your progress?") },
            text = { Text("The file holds ${files.values.sumOf { it.size }} saved settings. They replace everything the app remembers now" +
                (if ("profiles" in files) ", for everyone on this phone," else " for the owner's profile (an older file: stickers and the pup's world stay),") + " and this cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    com.bigpapituna.sharpears.data.ProgressFiles.replace(context, backup)
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
        title = { Text("Reset progress?") },
        // XP and achievements stay on purpose: they record what was done, and a reset should not take that away.
        text = { Text("Resume positions, played marks, best scores, practice time, ear training and theory scores and the practice tools' records " +
            "will be cleared. Your lessons, stickers, XP, level and achievements are kept.") },
        confirmButton = { TextButton(onClick = { prefs.resetProgress(); onResetProgress(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

/**
 * The voice list: the phone's offline English voices, best first. Tapping one switches to it and says a short
 * sample line, so the choice is made by ear.
 */
@Composable
private fun VoicePicker(voice: com.bigpapituna.sharpears.audio.Voice, version: Int) {
    androidx.compose.runtime.LaunchedEffect(Unit) { voice.warmUp() }
    val ready = voice.engineReady.value
    var expanded by remember { mutableStateOf(false) }
    val options = remember(ready, version) { voice.voices() }
    val current = remember(ready, version) { voice.currentVoice() }
    val currentLabel = options.firstOrNull { it.name == current }?.label ?: "Starting"
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(expanded, role = Role.Button, onValueChange = { expanded = it }),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Voice", style = MaterialTheme.typography.titleSmall)
            Text(if (!ready) "Starting the speech engine." else "$currentLabel. Tap to hear and choose another.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (expanded) "Hide" else "Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (expanded && ready) {
        if (options.isEmpty()) Text("No offline English voices on this phone. Install them in the phone's text-to-speech settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { o ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(o.name == current, role = Role.RadioButton,
                    onValueChange = { voice.choose(o.name) }).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.RadioButton(selected = o.name == current, onClick = null)
                Text(o.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One of the six groups (2.7): Family, Playing, Briefs and email, Microphone, About, Testing. */
@Composable
private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        modifier = Modifier.padding(top = 32.dp, bottom = 2.dp).semantics { heading() })

/** A part of a group: Keys, Falling notes, Sound, Voice and Reminder under Playing; Progress under Family. */
@Composable
private fun Sub(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp).semantics { heading() })

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

/** Settings > Playing > Encouragement (2.13): what the cheer pops are and what the three choices do. */
internal const val ENCOURAGEMENT_LINE = "Now and then the pup pops up while you play with a few kind words. Less waits longer between them. " +
    "Off keeps only the check when you pass a step."
