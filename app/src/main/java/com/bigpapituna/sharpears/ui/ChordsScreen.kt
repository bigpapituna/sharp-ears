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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.LessonStore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.widthIn
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.LessonPack
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

private const val LOW = 48   // C3
private const val HIGH = 84  // C6

enum class ChordsTab { LESSONS, DICTIONARY, PRACTICE, PROGRESSIONS }

@Composable
fun ChordsScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput,
    lessons: LessonStore, micDefault: Boolean,
    startTab: ChordsTab = ChordsTab.LESSONS, inject: String? = null,
    onPlayProgression: (Progression, Int) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(startTab) }
    Column(Modifier.safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹") }
            Text("Chords", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
            Tab(tab == ChordsTab.LESSONS, { tab = ChordsTab.LESSONS }, text = { Text("Lessons") })
            Tab(tab == ChordsTab.DICTIONARY, { tab = ChordsTab.DICTIONARY }, text = { Text("Dictionary") })
            Tab(tab == ChordsTab.PRACTICE, { tab = ChordsTab.PRACTICE }, text = { Text("Practice") })
            Tab(tab == ChordsTab.PROGRESSIONS, { tab = ChordsTab.PROGRESSIONS }, text = { Text("Progressions") })
        }
        when (tab) {
            ChordsTab.LESSONS -> Lessons(lessons, sampler, midi, mic, micDefault, inject, onPlayProgression)
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
    ChordTrainer(drill, sampler, midi, mic, micStart = inject != null, inject = inject,
        header = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FAMILIES.forEach { (name, _) ->
                    FilterChip(name in families, { families = if (name in families && families.size > 1) families - name else families + name }, label = { Text(name) })
                }
            }
        },
        skip = { drill.next() })
}

/**
 * One chord at a time from [drill], played by touch, MIDI or the mic. [onDone] gets each finished chord and
 * whether it was played without a wrong note; [skip] runs on the Skip button.
 */
@Composable
private fun ChordTrainer(
    drill: ChordDrill, sampler: Sampler, midi: MidiDevices, mic: MicInput, micStart: Boolean, inject: String?,
    header: @Composable () -> Unit, skip: () -> Unit, onDone: (Chord, Boolean) -> Unit = { _, _ -> },
) {
    var version by remember { mutableIntStateOf(0) }
    var showNotes by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(micStart && mic.hasPermission() || inject != null) }
    var flash by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) micOn = true }

    val current = remember(version, drill) { drill.current }
    val voicing = current.voicing(0)
    // The mic hears the chord where it is shown and an octave lower (left-hand range). Touch and MIDI take any octave.
    val micNotes = remember(current) { (voicing + voicing.map { it - 12 }).sorted() }
    // Strikes that began before this chord appeared (a ringing re-strike of the previous chord) do not count.
    var chordStart by remember { mutableLongStateOf(0L) }
    LaunchedEffect(current) {
        chordStart = mic.matcher.samplesProcessed
        Log.d(TAG, "chords: practice now ${current.name} ${voicing.map { noteName(it) }}")
    }
    fun strike(m: Int, src: String) {
        val before = drill.current
        val r = drill.input(m)
        Log.d(TAG, "chords: practice ${before.name} heard ${noteName(m)} src=$src -> $r correct=${drill.correct} streak=${drill.streak}")
        when (r) {
            ChordDrill.Result.DONE -> { onDone(before, drill.lastClean); scope.launch { flash = "✓ ${before.name}" to true; delay(700); flash = null } }
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
        if (micOn && visible) {
            if (inject != null) mic.startInjection(inject) else if (!mic.start()) micOn = false
            chordStart = 0L                                   // the matcher's sample clock restarts with the mic
        }
        onDispose { mic.stop() }
    }
    LaunchedEffect(version, micOn, current) {
        // Listen for the chord's still-missing pitch classes, in both octaves.
        mic.matcher.expected = if (micOn) micNotes.filter { it % 12 !in drill.heard }.toSet() else emptySet()
        // Each octave is its own chord for the matcher; one big group would bury every upper note in its octave below.
        mic.matcher.groups = listOf(voicing.toSet(), voicing.map { it - 12 }.toSet())
    }
    LaunchedEffect(mic, drill) {
        mic.events.collect { e ->
            if (!micOn || e !is MicEvent.Strike) return@collect
            val onset = e.sample - ((e.delayMs ?: 0.0) * MicInput.RATE / 1000).toLong()
            if (onset < chordStart) { Log.d(TAG, "chords: ignored ${noteName(e.midi)} (struck before ${drill.current.name} appeared)"); return@collect }
            strike(e.midi, "mic" + (e.inferredFrom?.let { " inferred from ${noteName(it)}" } ?: ""))
        }
    }

    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        header()
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
            FilledTonalButton(onClick = { skip(); version++ }) { Text("Skip") }
        }
        Text(
            if (micOn) "Mic: play it between ${noteName(micNotes.first())} and ${noteName(micNotes.last())}, any order. Notes heard turn green."
            else "Any octave, any order. Notes heard so far turn green.",
            style = MaterialTheme.typography.bodySmall,
        )
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

@Composable
private fun Lessons(
    store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, inject: String?,
    onPlayProgression: (Progression, Int) -> Unit,
) {
    val v = store.version.intValue
    if (store.session.isNotEmpty()) { Session(store, sampler, midi, mic, micDefault, inject, onPlayProgression); return }
    val now = remember(v) { System.currentTimeMillis() }
    val songPacks = store.songPacks.value
    val cur = remember(v, songPacks) { store.curriculum() }
    val mastery = remember(v) { store.mastery() }
    val today = remember(cur, mastery) { cur.queue(mastery, now) }
    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth().widthIn(max = 760.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val due = today.count { mastery[it.id]?.seen == true }
                Text(if (today.isEmpty()) "All caught up for today" else "Today: ${today.size} to practice",
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (today.isEmpty()) "Come back tomorrow, or practice any pack below."
                     else "$due due for review, ${today.size - due} new. Items you get right come back less often.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (today.isNotEmpty()) Button(onClick = { store.startSession(today) }) { Text("Start practice") }
            }
        }
        (cur.packs.filter { it.level < 6 } to cur.packs.filter { it.level >= 6 }).let { (authored, fromSongs) ->
            authored.sortedBy { it.level }.forEach { PackCard(it, store, cur.unlocked(it, mastery), cur, mastery, now) }
            Text("From your songs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            if (fromSongs.isEmpty()) Text("Finding the chords in your songs…", style = MaterialTheme.typography.bodySmall, color = InkDim)
            fromSongs.forEach { PackCard(it, store, true, cur, mastery, now) }
        }
    }
}

@Composable
private fun PackCard(pack: LessonPack, store: LessonStore, unlocked: Boolean, cur: com.bigpapituna.sharpears.engine.Curriculum,
                     mastery: Map<String, com.bigpapituna.sharpears.engine.Mastery>, now: Long) {
    val c = cur.counts(pack, mastery, now)
    Card(Modifier.fillMaxWidth().widthIn(max = 760.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pack.title, style = MaterialTheme.typography.titleMedium)
                store.about[pack.id]?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkDim) }
                Text(
                    if (!unlocked) "Unlocks when ${pack.requires.mapNotNull { r -> cur.packs.firstOrNull { it.id == r }?.title }.joinToString(" and ")} are well practiced"
                    else "${pack.items.size} items · ${c.due} due · ${c.new} new · ${c.mastered} mastered" +
                        if (pack.level >= 6) " · " + pack.items.joinToString(" ") { (it as? LessonItem.ChordItem)?.chord?.name ?: "" } else "",
                    style = MaterialTheme.typography.labelMedium, color = if (unlocked) MaterialTheme.colorScheme.onSurface else InkDim,
                )
            }
            if (unlocked) FilledTonalButton(onClick = { store.startSession(cur.queue(mastery, now, newLimit = 8, only = pack).ifEmpty { pack.items.take(8) }) }) { Text("Practice") }
        }
    }
}

@Composable
private fun Session(
    store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, inject: String?,
    onPlayProgression: (Progression, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val item = store.session.getOrNull(store.sessionIndex)
    val n = store.session.size
    if (item == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Session done", style = MaterialTheme.typography.headlineSmall)
            Text("${store.sessionCorrect} of $n right first time. The ones you missed come back tomorrow.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = store::endSession) { Text("Back to lessons") }
        }
        return
    }
    val progress = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lesson ${store.sessionIndex + 1} of $n", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = store::endSession) { Text("End session") }
        }
    }
    when (item) {
        is LessonItem.ChordItem -> {
            val drill = remember(store.sessionIndex) { ChordDrill(listOf(item.chord), seed = 1) }
            var answered by remember(store.sessionIndex) { mutableStateOf(false) }
            ChordTrainer(drill, sampler, midi, mic, micStart = micDefault, inject = inject, header = progress,
                skip = { store.answer(false) },
                onDone = { _, clean ->
                    if (!answered) { answered = true; scope.launch { delay(700); store.answer(clean) } }
                })
        }
        is LessonItem.ProgressionItem -> Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            progress()
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            Text("Play it through in wait mode: right hand the chords, left hand the roots. Then come back and say how it went.",
                style = MaterialTheme.typography.bodyMedium, color = InkDim)
            Button(onClick = { onPlayProgression(PROGRESSIONS[item.progression], item.key) }) { Text("Open in the player") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { store.answer(true) }) { Text("Got it") }
                OutlinedButton(onClick = { store.answer(false) }) { Text("Needs work") }
            }
        }
    }
}