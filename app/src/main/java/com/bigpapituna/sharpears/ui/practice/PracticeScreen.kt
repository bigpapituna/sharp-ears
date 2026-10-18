package com.bigpapituna.sharpears.ui.practice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
import com.bigpapituna.sharpears.engine.practice.Scale
import com.bigpapituna.sharpears.engine.practice.ScaleType
import com.bigpapituna.sharpears.engine.practice.arpeggioLine
import com.bigpapituna.sharpears.engine.practice.scaleLine
import com.bigpapituna.sharpears.engine.practice.scaleRootName
import com.bigpapituna.sharpears.engine.practice.spokenTitle
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.ScreenMotion
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.motion.rememberHaptics
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class PracticeTab(val label: String) { SCALES("Scales"), ARPEGGIOS("Arpeggios"), WARMUPS("Warm-ups"), METRONOME("Metronome"), TUNER("Tuner") }

enum class PracticeKind { SCALE, ARPEGGIO, WARMUP }

/** Reported through `onEvent` when the player is about to open a practice item, for XP, streaks and goals. */
data class PracticeEvent(val kind: PracticeKind, val id: String, val title: String, val bpm: Int)

/** The tab row: scales and arpeggios share the first tab (a toggle on the screen switches between them). */
private val TOP_TABS = listOf("Scales", "Warm-ups", "Metronome", "Tuner")
private fun topIndex(t: PracticeTab) = when (t) { PracticeTab.SCALES, PracticeTab.ARPEGGIOS -> 0; PracticeTab.WARMUPS -> 1; PracticeTab.METRONOME -> 2; PracticeTab.TUNER -> 3 }

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
    /** A tab was picked (integration: remembers it across the player and counts the metronome and tuner as used). */
    onTab: (PracticeTab) -> Unit = {},
    /** Settings > App sound: off, "Play it" and the touch keys are silent (the tuner's A is the tool itself). */
    sound: () -> Boolean = { true },
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    // Which of scales and arpeggios the first tab shows (kept while another tab is open).
    var lastPattern by rememberSaveable { mutableStateOf(if (startTab == PracticeTab.ARPEGGIOS) PracticeTab.ARPEGGIOS else PracticeTab.SCALES) }
    fun pick(t: PracticeTab) {
        tab = t
        if (t == PracticeTab.SCALES || t == PracticeTab.ARPEGGIOS) lastPattern = t
        onTab(t)
    }
    val still = reducedMotion()
    Column(modifier) {
        Text("Practice", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 2.dp).semantics { heading() })
        PrimaryTabRow(selectedTabIndex = topIndex(tab), containerColor = Color.Transparent, divider = {}) {
            TOP_TABS.forEachIndexed { i, label ->
                Tab(topIndex(tab) == i, {
                    pick(when (i) { 0 -> lastPattern; 1 -> PracticeTab.WARMUPS; 2 -> PracticeTab.METRONOME; else -> PracticeTab.TUNER })
                }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 13.dp))
                }
            }
        }
        AnimatedContent(topIndex(tab), Modifier.weight(1f).fillMaxWidth(), transitionSpec = { ScreenMotion.tab(still) }, label = "practiceTab") { i ->
            when (i) {
                0 -> PatternScreen(lastPattern == PracticeTab.ARPEGGIOS,
                    { arps -> pick(if (arps) PracticeTab.ARPEGGIOS else PracticeTab.SCALES) }, sampler, store, onOpenExercise, onEvent, sound)
                1 -> WarmupsScreen(store, onOpenExercise, onEvent)
                2 -> MetronomeScreen(store)
                else -> TunerScreen(mic, sampler)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Scales and arpeggios: one screen with a toggle. Choices on top, then the keyboard (the hero), the pup's line,
// Practice it and Play it, and the stats.

/** What the screen shows for the current choices; built for a scale or an arpeggio. */
private class PatternModel(
    val kind: PracticeKind,
    val title: String,
    val notes: (Hand) -> List<Int>,
    val upDown: (Hand) -> List<Int>,
    val fingers: (Hand) -> List<Int>,
    val fingersUpDown: (Hand) -> List<Int>,
    val line: (Practice) -> String,
    val exercise: (Practice, Int) -> Exercise,
    val exerciseTitle: (Practice) -> String,
    val byTheBook: Boolean,
)

/** Scale types in the order the row offers them: the common ones first. */
private val SCALE_ORDER = CORE_SCALES + listOf(ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC, ScaleType.BLUES,
    ScaleType.DORIAN, ScaleType.MIXOLYDIAN, ScaleType.CHROMATIC)

private val HANDS = listOf(Practice.LEFT, Practice.RIGHT, Practice.BOTH)

@Composable
private fun PatternScreen(
    arps: Boolean, onMode: (Boolean) -> Unit, sampler: Sampler, store: PracticeStore,
    onOpen: (Exercise, String) -> Unit, onEvent: (PracticeEvent) -> Unit, sound: () -> Boolean,
) {
    val p = if (arps) "arp" else "scale"
    var root by remember(arps) { mutableIntStateOf(store.int("$p.root", 0).coerceIn(0, 11)) }
    var scaleType by remember { mutableStateOf(store.enum("scale.type", ScaleType.MAJOR)) }
    var arpType by remember { mutableStateOf(store.enum("arp.type", ArpeggioType.MAJOR)) }
    var octaves by remember(arps) { mutableIntStateOf(store.int("$p.octaves", 1).coerceIn(1, 2)) }
    var hand by remember(arps) { mutableStateOf(store.enum("$p.hand", Practice.RIGHT)) }
    var bpm by remember(arps) { mutableIntStateOf(store.int("$p.bpm", if (arps) 60 else 72).coerceIn(30, 200)) }
    var loop by remember { mutableStateOf(store.bool("practice.loop", false)) }
    val typeName = if (arps) arpType.name else scaleType.name
    val minorFamily = if (arps) arpType.minorFamily else scaleType.minorFamily
    fun statId(r: Int) = "$p:$typeName:$r"

    val model = remember(arps, root, scaleType, arpType, octaves) {
        if (arps) {
            val a = Arpeggio(root, arpType, octaves)
            PatternModel(PracticeKind.ARPEGGIO, spokenTitle(a.rootName, arpType.label) + " arpeggio", a::ascending, a::upAndDown, a::fingering,
                a::fingeringUpAndDown, { arpeggioLine(a, it) }, { pr, b -> a.toExercise(pr, b.toDouble()) }, a::title, byTheBook = false)
        } else {
            val s = Scale(root, scaleType, octaves)
            PatternModel(PracticeKind.SCALE, spokenTitle(s.rootName, scaleType.label), s::ascending, s::upAndDown, s::fingering,
                s::fingeringUpAndDown, { scaleLine(s, it) }, { pr, b -> s.toExercise(pr, b.toDouble()) }, s::title, byTheBook = s.standardFingering)
        }
    }

    // ---- the demo ("Play it") ----
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sounding = remember { mutableStateMapOf<Int, Hand>() }
    val touched = remember { mutableStateMapOf<Int, Hand>() }
    val dots = remember { mutableStateMapOf<Hand, FingerDot>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var nudge by remember { mutableStateOf(false) }
    val bpmNow by rememberUpdatedState(bpm)
    val loopNow by rememberUpdatedState(loop)
    fun stop() { job?.cancel(); job = null }
    // A different scale, hand or range: whatever was playing stops, and the nudge goes.
    DisposableEffect(arps, root, typeName, hand, octaves) { onDispose { stop(); nudge = false } }

    fun play() {
        nudge = false
        val hands = when (hand) { Practice.RIGHT -> listOf(Hand.RIGHT); Practice.LEFT -> listOf(Hand.LEFT); Practice.BOTH -> listOf(Hand.RIGHT, Hand.LEFT) }
        val lines = hands.associateWith { model.upDown(it) zip model.fingersUpDown(it) }
        val n = lines.values.minOf { it.size }
        job = scope.launch {
            val me = coroutineContext[Job]
            var finished = false
            try {
                do {
                    for (i in 0 until n) {
                        val ms = 60_000L / bpmNow
                        for ((h, line) in lines) {
                            val (m, f) = line[i]
                            sounding[m] = h; dots[h] = FingerDot(m, f); if (sound()) sampler.noteOn(m, 85)
                        }
                        haptics.tick()
                        delay(ms * 9 / 10)
                        for ((_, line) in lines) { sampler.noteOff(line[i].first); sounding.remove(line[i].first) }
                        delay(ms / 10)
                    }
                    if (loopNow) delay(60_000L / bpmNow)
                } while (loopNow)
                finished = true
            } catch (e: CancellationException) {
                throw e
            } finally {
                sounding.keys.toList().forEach { sampler.noteOff(it) }
                sounding.clear(); dots.clear()
                if (job === me) job = null
                if (finished) { nudge = true; haptics.success() }
            }
        }
    }
    fun practise() {
        stop(); nudge = false
        store.putInt("$p.bpm", bpm)
        if (arps) store.recordArpeggio(typeName, root, bpm) else store.recordScale(typeName, root, bpm)
        onEvent(PracticeEvent(model.kind, statId(root), model.exerciseTitle(hand), bpm))
        onOpen(model.exercise(hand, bpm), model.exerciseTitle(hand))
    }

    @Suppress("UNUSED_VARIABLE") val unusedVersion = store.version.intValue    // stats below follow the store
    val names = (0..11).map { scaleRootName(it, minorFamily) }
    val counts = (0..11).map { store.stat(statId(it)).times }
    val stat = store.stat(statId(root))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SlidingSegments(listOf("Scales", "Arpeggios"), if (arps) 1 else 0, { onMode(it == 1) },
                Modifier.padding(horizontal = 20.dp).fillMaxWidth())
            KeyPicker(names, root, counts, { root = it; store.putInt("$p.root", it) })
            if (arps) PillRow(ArpeggioType.entries.map { it.label }, arpType.ordinal, { arpType = ArpeggioType.entries[it]; store.putEnum("arp.type", arpType) })
            else PillRow(SCALE_ORDER.map { it.label }, SCALE_ORDER.indexOf(scaleType), { scaleType = SCALE_ORDER[it]; store.putEnum("scale.type", scaleType) })

            KeyboardPanel(model, hand, { hand = it; store.putEnum("$p.hand", it) }, octaves, { octaves = it; store.putInt("$p.octaves", it) },
                bpm, { bpm = it; store.putInt("$p.bpm", it) }, loop, { loop = it; store.putBool("practice.loop", it) },
                sounding + touched, dots, sampler, touched, Modifier.padding(horizontal = 12.dp), sound)

            PupLine(if (job != null) Pose.LISTENING else Pose.LEARNING, model.line(hand), Modifier.padding(horizontal = 16.dp))

            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (job != null) stop() else play() }, modifier = Modifier.height(52.dp).pressScale()) {
                    Icon(painterResource(if (job != null) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (job != null) "Stop" else "Play it")
                }
                ChunkyButton(onClick = ::practise, modifier = Modifier.weight(1f)) { Text("Practice it") }
            }

            Text("Your ${typeWords(arps, if (arps) arpType.label else scaleType.label)}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
            StatTiles(stat.times, stat.bestBpm, (0..11).filter { counts[it] > 0 }.toSet(), root, names, Modifier.padding(horizontal = 20.dp))
        }
        AnimatedVisibility(nudge, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            NudgeBar("Now try it yourself", "You heard it. Play it back with the app listening.", "Practice it", ::practise, { nudge = false })
        }
    }
}

private fun typeWords(arps: Boolean, label: String) = label.lowercase() + if (arps) " arpeggios" else " scales"

/**
 * The hero: a dark panel like the player's stage, with the key's name and the tempo on top, the keyboard, and
 * the hand, octave and loop toggles under it.
 */
@Composable
private fun KeyboardPanel(
    model: PatternModel, hand: Practice, onHand: (Practice) -> Unit, octaves: Int, onOctaves: (Int) -> Unit,
    bpm: Int, onBpm: (Int) -> Unit, loop: Boolean, onLoop: (Boolean) -> Unit,
    sounding: Map<Int, Hand>, dots: Map<Hand, FingerDot>, sampler: Sampler, touched: MutableMap<Int, Hand>, modifier: Modifier = Modifier,
    sound: () -> Boolean = { true },
) {
    val right = if (hand != Practice.LEFT) model.notes(Hand.RIGHT).zip(model.fingers(Hand.RIGHT)).toMap() else emptyMap()
    val left = if (hand != Practice.RIGHT) model.notes(Hand.LEFT).zip(model.fingers(Hand.LEFT)).toMap() else emptyMap()
    val shown = right.keys + left.keys
    var low = shown.min().let { if (isBlack(it)) it - 1 else it }
    var high = shown.max().let { if (isBlack(it)) it + 1 else it }
    // A white key of room either side when it fits, so the scale sits on a piano and not on its own keys.
    if ((low..high).count { !isBlack(it) } < 20) {
        low -= if (isBlack(low - 1)) 2 else 1
        high += if (isBlack(high + 1)) 2 else 1
    }
    val whites = (low..high).count { !isBlack(it) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PanelColor).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (model.byTheBook) "Fingering by the book" else "Suggested fingering", style = MaterialTheme.typography.labelMedium, color = InkDim)
            }
            DragNumber(bpm, onBpm, 30..200, "bpm", stage = true)
        }
        val keyH = (347f / whites * 4.4f).coerceIn(120f, 168f).dp
        ScaleKeyboard(low, high, right, left, sounding, dots,
            onNoteOn = { m -> touched[m] = if (m in left && m !in right) Hand.LEFT else Hand.RIGHT; if (sound()) sampler.noteOn(m, 90) },
            onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
            description = model.title + ", " + when (hand) { Practice.RIGHT -> "right hand"; Practice.LEFT -> "left hand"; Practice.BOTH -> "both hands" } +
                ". Fingers " + (if (hand == Practice.LEFT) left else right).values.joinToString(" "),
            modifier = Modifier.fillMaxWidth().height(keyH + 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlidingSegments(listOf("Left", "Right", "Both"), HANDS.indexOf(hand), { onHand(HANDS[it]) }, Modifier.width(186.dp), stage = true, height = 34.dp,
                dots = listOf(listOf(LeftHandColor), listOf(RightHandColor), listOf(LeftHandColor, RightHandColor)),
                descriptions = listOf("Left hand", "Right hand", "Both hands"))
            SlidingSegments(listOf("1 oct", "2 oct"), octaves - 1, { onOctaves(it + 1) }, Modifier.width(104.dp), stage = true, height = 34.dp,
                descriptions = listOf("One octave", "Two octaves"))
            Spacer(Modifier.weight(1f))
            Box(Modifier.pressScale(0.9f).size(34.dp).clip(CircleShape).background(if (loop) RightHandColor else PanelWell)
                .clickable(role = Role.Switch) { onLoop(!loop) }
                .semantics { contentDescription = "Loop the demo"; stateDescription = if (loop) "On" else "Off" },
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, tint = if (loop) PanelColor else InkDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}
