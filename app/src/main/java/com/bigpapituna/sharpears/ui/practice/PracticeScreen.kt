package com.bigpapituna.sharpears.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.practice.Arpeggio
import com.bigpapituna.sharpears.engine.practice.ArpeggioType
import com.bigpapituna.sharpears.engine.practice.CORE_SCALES
import com.bigpapituna.sharpears.engine.practice.RoutineItem
import com.bigpapituna.sharpears.engine.practice.Scale
import com.bigpapituna.sharpears.engine.practice.ScaleType
import com.bigpapituna.sharpears.engine.practice.Warmup
import com.bigpapituna.sharpears.engine.practice.buildRoutine
import com.bigpapituna.sharpears.engine.practice.routineRoot
import com.bigpapituna.sharpears.engine.practice.routineSeconds
import com.bigpapituna.sharpears.engine.practice.scaleRootName
import com.bigpapituna.sharpears.engine.practice.warmupCatalog
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.RightHandColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class PracticeTab(val label: String) { SCALES("Scales"), ARPEGGIOS("Arpeggios"), WARMUPS("Warm-ups"), METRONOME("Metronome"), TUNER("Tuner") }

enum class PracticeKind { SCALE, ARPEGGIO, WARMUP }

/** Reported through `onEvent` when the player is about to open a practice item, for XP, streaks and goals. */
data class PracticeEvent(val kind: PracticeKind, val id: String, val title: String, val bpm: Int)

/**
 * The Practice tab. [onOpenExercise] opens an exercise in the player (wait mode, scored) with the given title.
 * [onEvent] says what was started; [startTab] is for a debug route.
 */
@Composable
fun PracticeScreen(
    sampler: Sampler,
    mic: MicInput,
    store: PracticeStore,
    onOpenExercise: (Exercise, String) -> Unit,
    modifier: Modifier = Modifier,
    startTab: PracticeTab = PracticeTab.SCALES,
    onEvent: (PracticeEvent) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    Column(modifier) {
        Text("Practice", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() })
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background, divider = {}) {
            PracticeTab.entries.forEach { t ->
                Tab(tab == t, { tab = t }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text(t.label) })
            }
        }
        when (tab) {
            PracticeTab.SCALES -> ScalesTab(sampler, store, onOpenExercise, onEvent)
            PracticeTab.ARPEGGIOS -> ArpeggiosTab(sampler, store, onOpenExercise, onEvent)
            PracticeTab.WARMUPS -> WarmupsTab(store, onOpenExercise, onEvent)
            PracticeTab.METRONOME -> MetronomeScreen(store)
            PracticeTab.TUNER -> TunerScreen(mic, sampler)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Scales and arpeggios share one panel: choices on top, the keyboard with fingering, Play it and Practice it.

/** What the panel shows for the current choices; built by each tab. */
private class Pattern(
    val title: (Practice) -> String,
    val notes: (Hand) -> List<Int>,
    val sequence: (Hand) -> List<Int>,
    val fingering: (Hand) -> List<Int>,
    val exercise: (Practice, Double) -> Exercise,
    val statId: String,
    val onStart: (Practice, Int) -> Unit,
)

@Composable
private fun ScalesTab(sampler: Sampler, store: PracticeStore, onOpen: (Exercise, String) -> Unit, onEvent: (PracticeEvent) -> Unit) {
    var root by remember { mutableIntStateOf(store.int("scale.root", 0).coerceIn(0, 11)) }
    var type by remember { mutableStateOf(store.enum("scale.type", ScaleType.MAJOR)) }
    var octaves by remember { mutableIntStateOf(store.int("scale.octaves", 1).coerceIn(1, 2)) }
    var hand by remember { mutableStateOf(store.enum("scale.hand", Practice.RIGHT)) }
    var bpm by remember { mutableIntStateOf(store.int("scale.bpm", 72)) }
    val scale = remember(root, type, octaves) { Scale(root, type, octaves) }
    val pattern = Pattern(
        title = scale::title, notes = scale::ascending, sequence = scale::upAndDown, fingering = scale::fingering,
        exercise = { p, b -> scale.toExercise(p, b) }, statId = "scale:${type.name}:$root",
        onStart = { p, b ->
            store.recordScale(type.name, root, b)
            onEvent(PracticeEvent(PracticeKind.SCALE, "scale:${type.name}:$root", scale.title(p), b))
        })
    PatternPanel(sampler, store, pattern, hand, { hand = it; store.putEnum("scale.hand", it) }, octaves, { octaves = it; store.putInt("scale.octaves", it) },
        bpm, { bpm = it; store.putInt("scale.bpm", it) }, onOpen, hasStandardFingering = scale.standardFingering) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { r -> FilterChip(root == r, { root = r; store.putInt("scale.root", r) }, label = { Text(scaleRootName(r, type)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (CORE_SCALES + ScaleType.entries.filter { it !in CORE_SCALES }).forEach { t ->
                FilterChip(type == t, { type = t; store.putEnum("scale.type", t) }, label = { Text(t.label) })
            }
        }
    }
}

@Composable
private fun ArpeggiosTab(sampler: Sampler, store: PracticeStore, onOpen: (Exercise, String) -> Unit, onEvent: (PracticeEvent) -> Unit) {
    var root by remember { mutableIntStateOf(store.int("arp.root", 0).coerceIn(0, 11)) }
    var type by remember { mutableStateOf(store.enum("arp.type", ArpeggioType.MAJOR)) }
    var octaves by remember { mutableIntStateOf(store.int("arp.octaves", 1).coerceIn(1, 2)) }
    var hand by remember { mutableStateOf(store.enum("arp.hand", Practice.RIGHT)) }
    var bpm by remember { mutableIntStateOf(store.int("arp.bpm", 60)) }
    val arp = remember(root, type, octaves) { Arpeggio(root, type, octaves) }
    val pattern = Pattern(
        title = arp::title, notes = arp::ascending, sequence = arp::upAndDown, fingering = arp::fingering,
        exercise = { p, b -> arp.toExercise(p, b) }, statId = "arp:${type.name}:$root",
        onStart = { p, b ->
            store.recordArpeggio(type.name, root, b)
            onEvent(PracticeEvent(PracticeKind.ARPEGGIO, "arp:${type.name}:$root", arp.title(p), b))
        })
    PatternPanel(sampler, store, pattern, hand, { hand = it; store.putEnum("arp.hand", it) }, octaves, { octaves = it; store.putInt("arp.octaves", it) },
        bpm, { bpm = it; store.putInt("arp.bpm", it) }, onOpen, hasStandardFingering = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { r -> FilterChip(root == r, { root = r; store.putInt("arp.root", r) }, label = { Text(scaleRootName(r, type.minorFamily)) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ArpeggioType.entries.forEach { t -> FilterChip(type == t, { type = t; store.putEnum("arp.type", t) }, label = { Text(t.label) }) }
        }
    }
}

@Composable
private fun PatternPanel(
    sampler: Sampler, store: PracticeStore, pattern: Pattern,
    hand: Practice, onHand: (Practice) -> Unit, octaves: Int, onOctaves: (Int) -> Unit,
    bpm: Int, onBpm: (Int) -> Unit, onOpen: (Exercise, String) -> Unit, hasStandardFingering: Boolean,
    choices: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val right = remember(pattern.statId, octaves) { pattern.notes(Hand.RIGHT) }
    val left = remember(pattern.statId, octaves) { pattern.notes(Hand.LEFT) }
    val shown = when (hand) { Practice.LEFT -> left; Practice.RIGHT -> right; Practice.BOTH -> left + right }
    val low = shown.min().let { if (isBlack(it)) it - 1 else it }
    val high = shown.max().let { if (isBlack(it)) it + 1 else it }
    val rightFingers = remember(pattern.statId, octaves) { pattern.fingering(Hand.RIGHT) }
    val leftFingers = remember(pattern.statId, octaves) { pattern.fingering(Hand.LEFT) }
    val sounding = remember { mutableStateMapOf<Int, Color>() }
    val touched = remember { mutableStateMapOf<Int, Color>() }
    var job by remember { mutableStateOf<Job?>(null) }
    @Suppress("UNUSED_VARIABLE") val unusedVersion = store.version.intValue   // stats below follow the store
    val stat = store.stat(pattern.statId)

    fun stop() { job?.cancel(); job = null }
    // A different scale, hand or range: whatever was playing stops.
    DisposableEffect(pattern.statId, hand, octaves) { onDispose { stop() } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                listOf(1, 2).forEachIndexed { i, o ->
                    SegmentedButton(octaves == o, { onOctaves(o) }, SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(if (o == 1) "1 octave" else "2 octaves") }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val hands = listOf(Practice.RIGHT to "Right", Practice.LEFT to "Left", Practice.BOTH to "Both")
            hands.forEachIndexed { i, (h, label) ->
                SegmentedButton(hand == h, { onHand(h) }, SegmentedButtonDefaults.itemShape(i, hands.size), icon = {}) { Text(label) }
            }
        }

        Text(pattern.title(hand), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        val lit = buildMap {
            if (hand != Practice.RIGHT) left.forEach { put(it, LeftHandColor.copy(alpha = if (hand == Practice.BOTH) 0.4f else 0.6f)) }
            if (hand != Practice.LEFT) right.forEach { put(it, RightHandColor.copy(alpha = 0.6f)) }
            put(if (hand == Practice.LEFT) left.first() else right.first(), (if (hand == Practice.LEFT) LeftHandColor else RightHandColor))
            putAll(sounding); putAll(touched)
        }
        val labels = (if (hand == Practice.LEFT) left.zip(leftFingers) else right.zip(rightFingers)).toMap().mapValues { it.value.toString() }
        Keyboard(
            low, high, lit = lit,
            onNoteOn = { m, v -> touched[m] = if (hand == Practice.LEFT) LeftHandColor else RightHandColor; sampler.noteOn(m, v) },
            onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().height(170.dp),
            labels = labels,
        )
        if (hand != Practice.LEFT) FingerLine("Right hand", rightFingers)
        if (hand != Practice.RIGHT) FingerLine("Left hand", leftFingers)
        if (!hasStandardFingering) Text("Fingering here is a suggestion from the app, not a textbook table.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hand == Practice.BOTH) Text("Both hands play together, the left an octave below the right. Numbers on the keys are the right hand.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Tempo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$bpm bpm", style = Numeric.merge(MaterialTheme.typography.titleMedium))
        }
        Slider(bpm.coerceIn(40, 160).toFloat(), { onBpm(it.toInt()) }, valueRange = 40f..160f)
        Text(if (stat.times == 0) "Not practised yet" else "Practised ${stat.times} ${if (stat.times == 1) "time" else "times"}, best tempo ${stat.bestBpm} bpm",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = {
                if (job != null) { stop(); return@FilledTonalButton }
                val msPerNote = 60_000L / bpm
                val rightSeq = pattern.sequence(Hand.RIGHT)
                val leftSeq = pattern.sequence(Hand.LEFT)
                val steps = when (hand) {
                    Practice.RIGHT -> rightSeq.map { listOf(it to RightHandColor) }
                    Practice.LEFT -> leftSeq.map { listOf(it to LeftHandColor) }
                    Practice.BOTH -> rightSeq.indices.map { listOf(rightSeq[it] to RightHandColor, leftSeq[it] to LeftHandColor) }
                }
                job = scope.launch {
                    val me = coroutineContext[Job]
                    try {
                        for (step in steps) {
                            step.forEach { (m, c) -> sounding[m] = c; sampler.noteOn(m, 85) }
                            delay(msPerNote * 9 / 10)
                            step.forEach { (m, _) -> sampler.noteOff(m); sounding.remove(m) }
                            delay(msPerNote / 10)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        sounding.keys.toList().forEach { sampler.noteOff(it) }
                        sounding.clear()
                        if (job === me) job = null
                    }
                }
            }, modifier = Modifier.height(52.dp)) {
                Icon(painterResource(if (job != null) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp)); Text(if (job != null) "Stop" else "Play it")
            }
            ChunkyButton(onClick = {
                stop()
                pattern.onStart(hand, bpm)
                onOpen(pattern.exercise(hand, bpm.toDouble()), pattern.title(hand))
            }, modifier = Modifier.weight(1f)) { Text("Practice it") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FingerLine(label: String, fingers: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
        Text(fingers.joinToString(" "), style = Numeric.merge(MaterialTheme.typography.bodyMedium))
    }
}

// ---------------------------------------------------------------------------------------------------------
// Warm-ups

private fun minutesWords(seconds: Double): String {
    val m = Math.round(seconds / 60.0).toInt()
    return if (m < 1) "under a minute" else "about $m min"
}

@Composable
private fun WarmupsTab(store: PracticeStore, onOpen: (Exercise, String) -> Unit, onEvent: (PracticeEvent) -> Unit) {
    var minutes by remember { mutableIntStateOf(store.int("warmup.minutes", 10)) }
    var level by remember { mutableIntStateOf(store.int("warmup.level", 1).coerceIn(1, 3)) }
    var seed by rememberSaveable { mutableIntStateOf(store.int("warmup.seed", 0)) }
    var browseRoot by remember { mutableIntStateOf(store.int("warmup.root", 0).coerceIn(0, 11)) }
    var opened by rememberSaveable { mutableStateOf(setOf<String>()) }
    @Suppress("UNUSED_VARIABLE") val unusedVersion = store.version.intValue
    val routine = remember(minutes, level, seed) { buildRoutine(minutes, level, seed) }
    val catalog = remember(browseRoot) { warmupCatalog(browseRoot) }

    fun open(w: Warmup, bpm: Int) {
        store.recordWarmup(w.id, bpm)
        opened = opened + w.id
        onEvent(PracticeEvent(PracticeKind.WARMUP, w.id, w.title, bpm))
        onOpen(w.at(bpm), w.title)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PupSays(Pose.GET_READY, "A few minutes of warm-up now saves your hands later.")
        Text("Warm-up routine", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(5, 10, 15)
            options.forEachIndexed { i, m ->
                SegmentedButton(minutes == m, { minutes = m; store.putInt("warmup.minutes", m) }, SegmentedButtonDefaults.itemShape(i, options.size), icon = {}) { Text("$m min") }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(1 to "Easy", 2 to "Medium", 3 to "Hard")
            options.forEachIndexed { i, (l, name) ->
                SegmentedButton(level == l, { level = l; store.putInt("warmup.level", l) }, SegmentedButtonDefaults.itemShape(i, options.size), icon = {}) { Text(name) }
            }
        }
        Text("${routine.size} exercises, ${minutesWords(routineSeconds(routine))}, in the key of ${scaleRootName(routineRoot(seed), false)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        routine.forEachIndexed { i, item -> RoutineRow(i + 1, item, done = item.warmup.id in opened) { open(item.warmup, item.bpm) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChunkyButton(onClick = {
                val next = routine.firstOrNull { it.warmup.id !in opened } ?: routine.first()
                open(next.warmup, next.bpm)
            }, modifier = Modifier.weight(1f)) { Text(if (opened.isEmpty()) "Start routine" else "Next exercise") }
            TextButton(onClick = { seed++; store.putInt("warmup.seed", seed); opened = emptySet() }) { Text("Another key") }
        }

        Text("All warm-ups", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..11).forEach { r -> FilterChip(browseRoot == r, { browseRoot = r; store.putInt("warmup.root", r) }, label = { Text(scaleRootName(r, false)) }) }
        }
        catalog.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val stat = store.stat("warmup:${w.id}")
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(w.title, style = MaterialTheme.typography.titleMedium)
                    Text(w.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Level ${w.level}, ${w.bpmMin} to ${w.bpmMax} bpm" + if (stat.times > 0) ", done ${stat.times}x" else "",
                        style = Numeric.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { open(w, w.defaultBpm) }) { Text("Practice") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RoutineRow(number: Int, item: RoutineItem, done: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (done) "Done" else "$number", style = Numeric.merge(MaterialTheme.typography.labelLarge),
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(width = 40.dp, height = 20.dp))
        Column(Modifier.weight(1f)) {
            Text(item.warmup.title, style = MaterialTheme.typography.titleSmall)
            Text("${item.reps} ${if (item.reps == 1) "time" else "times"} at ${item.bpm} bpm, ${minutesWords(item.seconds)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOpen) { Text("Open") }
    }
}
