package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.bigpapituna.sharpears.data.Recordings
import com.bigpapituna.sharpears.data.StuckShare
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.bigpapituna.sharpears.R
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup


/** Fixture labels for the recording session; the file names say what was played. */
private val FIXTURES = listOf(
    "scale-right" to "Right-hand scale",
    "scale-left" to "Left-hand scale",
    "chords" to "Chords",
    "elise" to "Für Elise opening",
    "silence" to "30 s of silence",
)

@Composable
fun MicSetupScreen(mic: MicInput, midi: MidiDevices, debuggable: Boolean, onMicCheck: () -> Unit, onBack: () -> Unit,
                   /** Wraps changes to the report token: the parent gate (ParentGateController.guard), even for grown-ups. */
                   guardToken: (() -> Unit) -> Unit = { it() },
                   /** Debug (--es screen micsetup --es inject ...): the room check hears this instead of the microphone. */
                   inject: String? = null) {
    BackHandler(onBack = onBack)
    var granted by remember { mutableStateOf(mic.hasPermission()) }
    // Refused twice, Android stops asking: "Allow microphone" then did nothing at all. Refused, the way to the phone's
    // settings shows; coming back from there checks again.
    var refused by remember { mutableStateOf(false) }
    // 2.15: through the microphone card; its Not now leaves this screen as it is, with the card to read again below.
    val ask = rememberMicAsk(mic) { granted = it; refused = !it && !mic.micNotNow }
    var howItWorks by remember { mutableStateOf(false) }
    if (howItWorks) MicCardInfo(mic) { howItWorks = false }
    var micAddress by remember { mutableStateOf(mic.micAddress) }
    var heard by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var sensitivity by remember { mutableFloatStateOf(mic.matcher.config.sensitivityDb.toFloat()) }
    var wrongDb by remember { mutableFloatStateOf(mic.matcher.config.wrongOnsetDb.toFloat()) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    // What the player does now: off after the noisy-room offer stopped the painting for this session; on again clears that.
    var wrongFlash by remember { mutableStateOf(mic.paintsWrongKeys) }

    // Listen while this screen is open; the check note is middle C. MIDI, if connected, is recorded as truth.
    val visible = onScreen()
    LaunchedEffect(visible) { if (visible && !granted) granted = mic.hasPermission() }
    DisposableEffect(granted, micAddress, visible) {
        val on = granted && visible
        if (on) {
            mic.micAddress = micAddress
            mic.matcher.expected = setOf(60); mic.matcher.group = setOf(60)
            mic.start(autoRecord = false)
            midi.onTruth = mic::onMidiTruth
        }
        // Only what this effect set up: a leaving screen's last dispose used to stop the mic check's mic and clear its notes.
        onDispose { if (on) { mic.stopRecording(); mic.stop(); midi.onTruth = null; mic.matcher.expected = emptySet() } }
    }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            when (e) {
                is MicEvent.Strike -> { if (e.midi == 60) heard++; last = "Heard ${noteName(e.midi)} (rise ${"%.0f".format(java.util.Locale.ROOT, e.riseDb)} dB)" to true; Log.d(TAG, "micsetup heard ${noteName(e.midi)} rise=${e.riseDb}") }
                is MicEvent.Wrong -> { last = "Heard a different note" to false; Log.d(TAG, "micsetup wrong") }
            }
        }
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Text("Microphone setup", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        if (!granted) {
            // Honest for this build: debug builds keep recordings by default (Keep the last four recordings, below once allowed).
            // 2.15: as the card says it, from what this phone does now (the keep switch can be off on a test build).
            PupSays(Pose.LISTENING, "Sharp Ears listens to your piano through the microphone. " + when {
                mic.keepRecordings -> "This test build keeps recent sessions on the phone, and you can turn that off here."
                mic.canRecord -> "Keeping recordings is off on this test build."
                else -> "Nothing is recorded or sent anywhere." },
                size = 88.dp, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { ask.ask() }) { Text("Allow microphone") }
            if (refused) MicRefusedNote()
            TextButton(onClick = { howItWorks = true }) { Text(com.bigpapituna.sharpears.engine.MicTrust.TITLE) }
            return@Column
        }

        Text("Microphone", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mics = mic.builtInMics()
            mics.forEach { (address, label) ->
                // None picked yet: start() takes the first built-in mic the phone lists, so that chip is the one selected
                // ("Bottom" showed selected whatever was used, and no chip at all where no mic is called "bottom").
                FilterChip(selected = (micAddress ?: mics.firstOrNull()?.first) == address, onClick = { micAddress = address }, label = { Text(label) })
            }
        }
        Text(mic.source.value ?: "Starting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Level", style = MaterialTheme.typography.titleMedium)
        val level = mic.level.floatValue
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { ((level + 90f) / 90f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
            Text("%.0f dB".format(java.util.Locale.ROOT, level), modifier = Modifier.width(64.dp))
        }

        Text("Check: play middle C (C4) a few times", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(Pose.LISTENING, size = 64.dp)
            Text(last?.first ?: "Listening", color = when (last?.second) { true -> Good; false -> Bad; null -> MaterialTheme.colorScheme.onSurface })
        }
        Text("Heard C4 ${com.bigpapituna.sharpears.engine.times(heard)}", style = MaterialTheme.typography.bodySmall)

        FilledTonalButton(onClick = onMicCheck) { Text("Try it: C major scale in wait mode") }

        RoomCheckSection(mic, inject)
        TextButton(onClick = { howItWorks = true }) { Text(com.bigpapituna.sharpears.engine.MicTrust.TITLE) }

        Text("Sensitivity: %.0f dB".format(java.util.Locale.ROOT, sensitivity), style = MaterialTheme.typography.titleMedium)
        Text("Lower hears softer notes; raise it if room noise triggers notes.", style = MaterialTheme.typography.bodySmall)
        Slider(sensitivity, { sensitivity = it }, valueRange = -80f..-40f, onValueChangeFinished = {
            mic.saveConfig(mic.matcher.config.copy(sensitivityDb = sensitivity.toDouble())); Log.d(TAG, "mic sensitivity=$sensitivity"); com.bigpapituna.sharpears.data.Briefs.setting("sensitivityDb", sensitivity.toInt())
        }, modifier = Modifier.semantics { contentDescription = "Sensitivity" })     // TalkBack said only "slider" and a percent
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show wrong keys heard by the mic", style = MaterialTheme.typography.titleMedium)
                Text("A key you hit lights yellow next to the right one and red further away. Only clear piano strikes count, so talking should not set it off.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = wrongFlash, onCheckedChange = { wrongFlash = it; mic.wrongFlash = it; if (it) mic.wrongPaintOff = false; Log.d(TAG, "mic wrongFlash=$it"); com.bigpapituna.sharpears.data.Briefs.setting("wrongFlash", it) },
                modifier = Modifier.semantics { contentDescription = "Show wrong keys heard by the mic" })
        }
        if (wrongFlash) {
            Text("Wrong-key trigger: %.0f dB jump".format(java.util.Locale.ROOT, wrongDb), style = MaterialTheme.typography.titleMedium)
            Text("Raise it if right notes light up as wrong.", style = MaterialTheme.typography.bodySmall)
            Slider(wrongDb, { wrongDb = it }, valueRange = 3f..15f, onValueChangeFinished = {
                mic.saveConfig(mic.matcher.config.copy(wrongOnsetDb = wrongDb.toDouble())); Log.d(TAG, "mic wrongOnsetDb=$wrongDb")
            }, modifier = Modifier.semantics { contentDescription = "Wrong-key trigger" })
        }

        if (mic.canRecord) {
            // This private (debug) build can keep recent mic sessions to tune note detection; the user sees and controls it.
            var keep by remember { mutableStateOf(mic.keepRecordings) }
            var count by remember { mutableIntStateOf(mic.recordings().size) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Recordings.KEEP_SWITCH, style = MaterialTheme.typography.titleMedium)
                    Text("Saves your last four mic sessions in the app's own storage on this phone, so note detection can be tuned from your playing, " +
                        "plus a short clip whenever wait mode is stuck on the same notes for 5 seconds (up to 250 MB, oldest deleted first). " +
                        "They leave the phone only if you share them or switch on Send them for review. Talking in the room is recorded too.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = keep, onCheckedChange = { keep = it; mic.keepRecordings = it; Log.d(TAG, "mic keepRecordings=$it") },
                    modifier = Modifier.semantics { contentDescription = Recordings.KEEP_SWITCH })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (count == 0) "No recordings saved." else "$count recording${if (count == 1) "" else "s"} saved.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (count > 0) TextButton(onClick = { mic.deleteRecordings(); count = mic.recordings().size }) {
                    Text("Delete recordings", color = MaterialTheme.colorScheme.error)
                }
            }
            var stuck by remember { mutableIntStateOf(mic.stalls().size) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            // Zipping for Share stuck moments: files packed so far out of the total, or null when idle.
            var packing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
            var shareError by remember { mutableStateOf<String?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (stuck == 0) "No stuck moments saved." else "$stuck stuck moment${if (stuck == 1) "" else "s"} saved for review.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (stuck > 0) TextButton(enabled = packing == null, onClick = {
                    val files = mic.stallFiles()
                    shareError = null
                    packing = 0 to files.size
                    scope.launch {
                        val zip = runCatching {
                            withContext(Dispatchers.IO) {
                                StuckShare.zip(context, files) { done -> scope.launch { packing = done to files.size } }
                            }
                        }
                        packing = null
                        zip.onSuccess { Log.d(TAG, "mic: sharing ${it.name} (${it.length() / 1024} KB)"); StuckShare.share(context, it) }
                            .onFailure { Log.d(TAG, "mic: share zip failed (${it.message})"); com.bigpapituna.sharpears.analytics.Tester.error("recordings share"); shareError = "Could not pack them: ${it.message}" }
                    }
                }, modifier = Modifier.semantics { contentDescription = "Share stuck moments" }) { Text("Share") }
                if (stuck > 0) TextButton(enabled = packing == null, onClick = { mic.deleteStalls(); stuck = mic.stalls().size }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            if (stuck > 0) {
                // What a share would hold: every file in the folder (clips, reports, flags, crash reports).
                val files = remember(stuck) { mic.stallFiles() }
                val newest = remember(stuck) { mic.stalls().lastOrNull()?.lastModified() }
                val mb = files.sumOf { it.length() } / 1_048_576.0
                Text(buildString {
                    if (newest != null) append("Newest ").append(SimpleDateFormat("MMM d, yyyy, HH:mm", LocalLocale.current.platformLocale).format(Date(newest))).append(". ")
                    append(if (mb < 0.1) "Less than 0.1 MB" else "%.1f MB".format(java.util.Locale.ROOT, mb)).append(" in ${files.size} file${if (files.size == 1) "" else "s"}.")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            packing?.let { (done, total) ->
                Text("Packing $done of $total files", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            shareError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Bad) }
            // Sending them on: stuck moments, flags, crashes and the weekly summary become issues in the private repo.
            val rep = mic.reporter
            var hasToken by remember { mutableStateOf(rep.hasToken) }
            var send by remember { mutableStateOf(rep.enabled) }
            var editToken by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Send them for review", style = MaterialTheme.typography.titleMedium)
                    Text(if (!hasToken) "Not set up on this phone yet. Tap Set up and paste a GitHub token."
                        else "On Wi-Fi, each goes to your private GitHub repo with its clip, where fixes are prepared. ${rep.pending()} waiting.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = send, enabled = hasToken, onCheckedChange = { send = it; rep.enabled = it; if (it) rep.flush() },
                    modifier = Modifier.semantics { contentDescription = "Send stuck moments for review" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { guardToken { editToken = true } }) { Text(if (hasToken) "Set up (change)" else "Set up") }
                if (hasToken) TextButton(onClick = { guardToken { rep.clearToken(); hasToken = false; send = rep.enabled } }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
            if (editToken) {
                // The typed value lives only in this dialog and then in the app's private preferences; never logged or shown.
                var token by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { editToken = false },
                    title = { Text("GitHub token") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("A fine-grained GitHub token for your sharp-ears repo with Contents and Issues read and write. " +
                                "It stays in this app's private storage.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(token, { token = it }, label = { Text("GitHub token") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(enabled = token.isNotBlank(), onClick = {
                            rep.setToken(token); token = ""; editToken = false
                            hasToken = rep.hasToken; send = rep.enabled; rep.flush()
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { token = ""; editToken = false }) { Text("Cancel") } },
                )
            }
        }

        if (debuggable) {
            Text("Record test fixtures (debug)", style = MaterialTheme.typography.titleMedium)
            Text(
                midi.connected.value?.let { "MIDI $it is recorded alongside as the truth." }
                    ?: "No MIDI keyboard: recordings will have no truth.",
                style = MaterialTheme.typography.bodySmall,
            )
            val recording = mic.recording.value
            if (recording != null) {
                Text("Recording “$recording”. Play, then tap Stop.", color = Bad)
                Button(onClick = { mic.stopRecording(); savedNote = "Saved $recording" }) { Text("Stop") }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FIXTURES.forEach { (id, label) ->
                        FilledTonalButton(onClick = {
                            val name = "$id-${(micAddress ?: "bottom")}"
                            savedNote = if (mic.startRecording(name) != null) null else "Mic is not running"
                        }) { Text(label) }
                    }
                }
            }
            savedNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** What the mic buttons say once the microphone was refused (Android may have stopped asking). */
const val MIC_REFUSED_LINE = "The microphone is off for Sharp Ears. Allow it in the phone's settings, under Permissions."

/**
 * Shown once the microphone was refused. After two refusals Android stops asking, so the only way left is the app's
 * page in the phone's settings (Permissions, Microphone); this opens it.
 */
@Composable
fun MicRefusedNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("The microphone is off for Sharp Ears. To turn it on, allow it in the phone's settings, under Permissions.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)))
        }) { Text("Open the phone's settings") }
    }
}

/**
 * 2.15, the room check: ten seconds to play C, E and G, judged by the could-not-hear rule (engine/hints/MicHearing.kt
 * RoomCheck): "Heard clearly", "Heard, a bit noisy" or "Couldn't hear: try closer". It listens with strict keys, as the
 * stages do, then gives the screen's middle C check back. [inject] (debug) plays instead of the microphone.
 */
@Composable
private fun RoomCheckSection(mic: MicInput, inject: String?) {
    var check by remember { mutableStateOf<com.bigpapituna.sharpears.engine.hints.RoomCheck?>(null) }
    var verdict by remember { mutableStateOf<com.bigpapituna.sharpears.engine.hints.RoomCheck.Verdict?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var heard by remember { mutableStateOf(emptySet<Int>()) }
    val running = check != null
    Text("Room check", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(com.bigpapituna.sharpears.engine.hints.RoomCheck.PROMPT, style = MaterialTheme.typography.bodySmall)
    LaunchedEffect(running) {
        val c = check ?: return@LaunchedEffect
        val keys = com.bigpapituna.sharpears.engine.hints.RoomCheck.KEYS
        mic.matcher.strictKeys = true
        mic.matcher.expected = keys; mic.matcher.groups = keys.map { setOf(it) }
        if (inject != null) mic.startInjection(inject)
        Log.d(TAG, "room check: started${if (inject != null) " (injected $inject)" else ""}")
        try {
            while (true) {
                val now = android.os.SystemClock.uptimeMillis()
                if (c.done(now)) break
                c.level(now, mic.level.floatValue.toDouble())
                progress = c.progress(now).toFloat()
                heard = c.heard.toSet()
                kotlinx.coroutines.delay(100)
            }
            val v = c.verdict()
            verdict = v
            Log.d(TAG, "room check: ${v.name} heard=${c.heard.map { noteName(it) }} noisy=${c.noisy}${c.noisyBy?.let { " ($it)" } ?: ""}")
        } finally {
            mic.matcher.strictKeys = false
            mic.matcher.expected = setOf(60); mic.matcher.group = setOf(60)
            if (inject != null) { mic.stop(); mic.start(autoRecord = false) }
            check = null
        }
    }
    LaunchedEffect(mic) {
        mic.events.collect { e -> if (e is MicEvent.Strike) check?.note(e.midi) }
    }
    LaunchedEffect(mic) {
        mic.noise.collect { val c = check ?: return@collect; c.unpitched(android.os.SystemClock.uptimeMillis()); Log.d(TAG, "room check: a sound with no note in it") }
    }
    if (running) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Room check, listening" })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (k in com.bigpapituna.sharpears.engine.hints.RoomCheck.KEYS) {
                val got = k in heard
                Text(noteName(k).filter { it.isLetter() }, style = MaterialTheme.typography.titleLarge,
                    color = if (got) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "${noteName(k)} ${if (got) "heard" else "not heard yet"}" })
            }
        }
    } else {
        verdict?.let { v ->
            Text(v.title, style = MaterialTheme.typography.titleMedium,
                color = if (v == com.bigpapituna.sharpears.engine.hints.RoomCheck.Verdict.CLEAR) Good else MaterialTheme.colorScheme.primary)
            Text(v.line, style = MaterialTheme.typography.bodyMedium)
        }
        FilledTonalButton(onClick = {
            verdict = null; heard = emptySet(); progress = 0f
            check = com.bigpapituna.sharpears.engine.hints.RoomCheck(android.os.SystemClock.uptimeMillis())
        }) { Text(if (verdict == null) "Start the room check" else "Check again") }
    }
}
