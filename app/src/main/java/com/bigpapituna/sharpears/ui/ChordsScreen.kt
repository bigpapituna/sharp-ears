package com.bigpapituna.sharpears.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordDrill
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.PROGRESSION_KEYS
import com.bigpapituna.sharpears.engine.Progression
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.rootName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Good = Color(0xFF5BE37D)
private val Bad = Color(0xFFFF4D5E)
private const val LOW = 48   // C3
private const val HIGH = 84  // C6

enum class ChordsTab { DICTIONARY, PRACTICE, PROGRESSIONS }

@Composable
fun ChordsScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput,
    startTab: ChordsTab = ChordsTab.DICTIONARY, inject: String? = null,
    onPlayProgression: (Progression, Int) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(startTab) }
    Column(Modifier.safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Chords", style = MaterialTheme.typography.headlineSmall)
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            Tab(tab == ChordsTab.DICTIONARY, { tab = ChordsTab.DICTIONARY }, text = { Text("Dictionary") })
            Tab(tab == ChordsTab.PRACTICE, { tab = ChordsTab.PRACTICE }, text = { Text("Practice") })
            Tab(tab == ChordsTab.PROGRESSIONS, { tab = ChordsTab.PROGRESSIONS }, text = { Text("Progressions") })
        }
        when (tab) {
            ChordsTab.DICTIONARY -> Dictionary(sampler)
            ChordsTab.PRACTICE -> Practice(sampler, midi, mic, inject)
            ChordsTab.PROGRESSIONS -> Progressions(onPlayProgression)
        }
    }
}

@Composable
private fun Dictionary(sampler: Sampler) {
    var root by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf(ChordType.MAJOR) }
    var inversion by remember { mutableIntStateOf(0) }
    val chord = Chord(root, type)
    if (inversion >= chord.inversionCount) inversion = 0
    val notes = chord.voicing(inversion)
    val scope = rememberCoroutineScope()
    val touched = remember { mutableStateMapOf<Int, Color>() }

    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { r -> FilterChip(root == r, { root = r }, label = { Text(rootName(r)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChordType.entries.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t.label) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until chord.inversionCount).forEach { i ->
                FilterChip(inversion == i, { inversion = i }, label = { Text(if (i == 0) "Root position" else "Inversion $i") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(chord.name, style = MaterialTheme.typography.displaySmall)
            Text(notes.joinToString("  ") { noteName(it) }, style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                Log.d(TAG, "chords: play ${chord.name} inv=$inversion $notes")
                scope.launch { notes.forEach { sampler.noteOn(it, 90) }; delay(1500); notes.forEach(sampler::noteOff) }
            }) { Text("Play") }
        }
        Text("Numbers on the keys are right-hand fingers: 1 thumb … 5 little finger.", style = MaterialTheme.typography.bodySmall)
        Keyboard(
            LOW, HIGH,
            lit = notes.associateWith { RightHandColor } + touched,
            onNoteOn = { m, v -> touched[m] = Good; sampler.noteOn(m, v) },
            onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            labels = notes.zip(chord.fingering(inversion)).associate { (n, f) -> n to f.toString() },
        )
    }
}

/** Chord families offered for practice. */
private val FAMILIES = listOf(
    "Major" to listOf(ChordType.MAJOR), "Minor" to listOf(ChordType.MINOR),
    "Sevenths" to listOf(ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7), "Sus & dim" to listOf(ChordType.SUS2, ChordType.SUS4, ChordType.DIM),
)

@Composable
private fun Practice(sampler: Sampler, midi: MidiDevices, mic: MicInput, inject: String?) {
    var families by remember { mutableStateOf(setOf("Major", "Minor")) }
    val pool = remember(families) { (0..11).flatMap { r -> FAMILIES.filter { it.first in families }.flatMap { it.second }.map { Chord(r, it) } } }
    // Injected (debug) runs use a fixed seed so the chord order is known in advance.
    val drill = remember(pool) { ChordDrill(pool.ifEmpty { listOf(Chord(0, ChordType.MAJOR)) }, seed = if (inject != null) 1 else System.nanoTime()) }
    var version by remember { mutableIntStateOf(0) }
    var showNotes by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(inject != null) }
    var flash by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) micOn = true }

    val current = remember(version, drill) { drill.current }
    val voicing = current.voicing(0)
    LaunchedEffect(current) { Log.d(TAG, "chords: practice now ${current.name} ${voicing.map { noteName(it) }}") }
    fun strike(m: Int, src: String) {
        val before = drill.current
        val r = drill.input(m)
        Log.d(TAG, "chords: practice ${before.name} heard ${noteName(m)} src=$src -> $r correct=${drill.correct} streak=${drill.streak}")
        when (r) {
            ChordDrill.Result.DONE -> scope.launch { flash = "✓ ${before.name}" to true; delay(700); flash = null }
            ChordDrill.Result.MISS -> scope.launch { flash = "${noteName(m)} is not in ${before.name}" to false; delay(700); flash = null }
            ChordDrill.Result.PART -> {}
        }
        version++
    }

    LaunchedEffect(midi, drill) {
        midi.events.collect { e -> if (e is NoteEvent.On) { touched[e.midi] = RightHandColor; strike(e.midi, "midi") } else touched.remove(e.midi) }
    }
    val visible = onScreen()
    DisposableEffect(micOn, visible) {
        if (micOn && visible) { if (inject != null) mic.startInjection(inject) else if (!mic.start()) micOn = false }
        onDispose { mic.stop() }
    }
    LaunchedEffect(version, micOn) {
        // The mic listens for this chord's notes around middle C that are still missing.
        val missing = voicing.filter { it % 12 !in drill.heard }.toSet()
        mic.matcher.expected = if (micOn) missing else emptySet()
        mic.matcher.group = voicing.toSet()
    }
    LaunchedEffect(mic, drill) {
        mic.events.collect { e -> if (micOn && e is MicEvent.Strike) strike(e.midi, "mic") }
    }

    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FAMILIES.forEach { (name, _) ->
                FilterChip(name in families, { families = if (name in families && families.size > 1) families - name else families + name }, label = { Text(name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Play:", style = MaterialTheme.typography.titleMedium)
            Text(current.name, style = MaterialTheme.typography.displayMedium)
            flash?.let { (msg, ok) -> Text(msg, color = if (ok) Good else Bad, style = MaterialTheme.typography.titleLarge) }
        }
        Text("Correct ${drill.correct} · Streak ${drill.streak}", style = MaterialTheme.typography.titleMedium)
        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Show notes  "); Switch(showNotes, { showNotes = it }) }
            FilterChip(micOn, {
                if (micOn) micOn = false else if (mic.hasPermission()) micOn = true else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }, label = { Text(if (micOn) "Mic on" else "Mic") })
            FilledTonalButton(onClick = { drill.next(); version++ }) { Text("Skip") }
        }
        Text("Any octave, any order. Notes heard so far turn green.", style = MaterialTheme.typography.bodySmall)
        val lit = buildMap {
            if (showNotes) voicing.forEach { put(it, RightHandColor.copy(alpha = 0.5f)) }
            voicing.filter { it % 12 in drill.heard }.forEach { put(it, Good) }
            putAll(touched)
        }
        Keyboard(
            LOW, HIGH, lit = lit,
            onNoteOn = { m, v -> touched[m] = Good; if (!micOn || mic.headphonesOn()) sampler.noteOn(m, v); strike(m, "touch") },
            onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().height(220.dp).then(if (flash?.second == false) Modifier.border(4.dp, Bad) else Modifier),
            labels = if (showNotes) voicing.zip(current.fingering(0)).associate { (n, f) -> n to f.toString() } else emptyMap(),
        )
    }
}

@Composable
private fun Progressions(onPlay: (Progression, Int) -> Unit) {
    var key by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Key", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PROGRESSION_KEYS.forEach { k -> FilterChip(key == k, { key = k }, label = { Text(rootName(k)) }) }
        }
        Text("Each opens in the player in wait mode: right hand plays the chord, left hand the root.", style = MaterialTheme.typography.bodySmall)
        PROGRESSIONS.forEach { p ->
            val chords = p.degrees.map { Chord((key + it.semitones) % 12, it.type).name }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(chords.distinct().joinToString(" · ").let { if (p.degrees.size > 4) "$it  (${p.degrees.size} bars)" else chords.joinToString(" – ") },
                        style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { onPlay(p, key) }) { Text("Practice") }
            }
        }
    }
}
