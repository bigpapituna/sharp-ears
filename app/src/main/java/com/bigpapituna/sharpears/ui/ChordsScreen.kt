package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.bigpapituna.sharpears.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.chordTitle
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.practice.FingerDot
import com.bigpapituna.sharpears.ui.practice.KeyPicker
import com.bigpapituna.sharpears.ui.practice.PanelColor
import com.bigpapituna.sharpears.ui.practice.Pill
import com.bigpapituna.sharpears.ui.practice.PupLine
import com.bigpapituna.sharpears.ui.practice.ScaleKeyboard
import com.bigpapituna.sharpears.ui.practice.SlidingSegments
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import com.bigpapituna.sharpears.engine.voiceLead
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup

private const val LOW = 48   // C3
private const val HIGH = 84  // C6

/**
 * Where Learn chords opens (2.7): the daily review and the packs, with the dictionary's sheet up, or the free chord practice.
 * Before 2.7 these were the Chords tab's Daily, Dictionary and Practice tabs.
 */
enum class LearnChordsStart { REVIEW, DICTIONARY, PRACTICE }

/** A chord page's top: Back and the page's name, as on Settings. */
@Composable
internal fun PageTop(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
    }
}

/**
 * Learn chords (2.7, at the top of Learn): the daily chord review as one line, the dictionary as a sheet, free practice on any
 * chord, and the lesson packs. The Chords tab's Daily, Dictionary and Practice merged into one page. [onShowPlayable] opens
 * the Songs tab's songs by chords narrowed to what the chords learned already play; [onOpenPopSong] a song after a session.
 */
@Composable
fun LearnChordsScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput, prefs: Prefs, lessons: LessonStore, micDefault: Boolean,
    start: LearnChordsStart = LearnChordsStart.REVIEW, inject: String? = null,
    onPlayProgression: (Progression, Int) -> Unit,
    popSongs: List<com.bigpapituna.sharpears.engine.chords.PopSong> = emptyList(),
    onOpenPopSong: (com.bigpapituna.sharpears.engine.chords.PopSong) -> Unit = {},
    onShowPlayable: () -> Unit = {},
    onBack: () -> Unit,
) {
    var drill by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(start == LearnChordsStart.PRACTICE) }
    // The layouts (2.8, engine/nav/Simple.kt): the six beginner chords until Full (Advanced keeps the song packs whole), until
    // "Show more".
    var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val shown = com.bigpapituna.sharpears.engine.nav.Simple.shown(LocalLayout.current.layout, more)
    val only = com.bigpapituna.sharpears.engine.nav.Simple.chords(shown)
    var dictionary by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(start == LearnChordsStart.DICTIONARY) }
    // The chords learned so far (Leitner box 3 or more) decide which songs "you can play".
    val known = remember(lessons.version.intValue) { com.bigpapituna.sharpears.engine.chords.PopFind.known(lessons.mastery()) }
    val playable = remember(popSongs, known) { com.bigpapituna.sharpears.engine.chords.PopFind.playableCount(popSongs, known) }
    Column(Modifier.fillMaxSize()) {
        PageTop(if (drill) "Practise any chord" else com.bigpapituna.sharpears.engine.nav.Names.LEARN_CHORDS,
            onBack = { if (drill) drill = false else onBack() })
        if (drill) {
            BackHandler { drill = false }                 // Back leaves the practice, not Learn chords
            Practice(sampler, midi, mic, inject, sound = { prefs.sound }, only = only)
        } else Lessons(lessons, sampler, midi, mic, micDefault, inject, onPlayProgression, sound = { prefs.sound }, layout = shown,
            footer = { LayoutMore(com.bigpapituna.sharpears.engine.nav.Simple.Place.LEARN_CHORDS, more, { more = true }, edge = 4.dp) },   // in line with the packs
            songsAfter = if (popSongs.isEmpty()) null else SessionSongs(popSongs, onOpenPopSong, onShowPlayable),
            header = {
                // The count of songs the learned chords play; a tap shows them in the Songs tab.
                if (popSongs.isNotEmpty()) com.bigpapituna.sharpears.ui.chords.PlayableLine(playable, Modifier.padding(start = 4.dp, bottom = 8.dp), onShowPlayable)
            },
            tools = {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("Dictionary", if (only != null) "The six first chords: C, F, G, Am, Dm and Em, with fingers." else "Every chord in every key and inversion, with fingers.",
                        R.drawable.ic_book, Modifier.weight(1f)) { dictionary = true }
                    ToolTile("Practise any chord", "One chord at a time, by touch, keyboard or mic.", R.drawable.ic_hand, Modifier.weight(1f)) { drill = true }
                }
            })
    }
    if (dictionary) DictionarySheet(onDismiss = { dictionary = false }) { Dictionary(sampler, sound = { prefs.sound }, only = only) }
}

/** One of Learn chords' two ways in beside the packs: an icon, a name and a line, the whole tile a button. */
@Composable
private fun ToolTile(title: String, detail: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxSize().pressScale().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The dictionary as a sheet over Learn chords: tall, with its own close. Its keys are played by touch, so dragging on the
 * sheet never moves it (only the close and Back put it away).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DictionarySheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = null) {
        val tall = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() } * 0.88f
        Column(Modifier.fillMaxWidth().height(tall).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dictionary", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                androidx.compose.material3.IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close the dictionary") }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** Games (2.7, at the top of Learn; the Chords tab's Play before): the chord games' picker, or the game being played. */
@Composable
fun ChordGamesPage(sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs, micDefault: Boolean, start: GameStart, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTop(com.bigpapituna.sharpears.engine.nav.Names.GAMES, onBack)
        ChordGamesScreen(sampler, midi, mic, voice, prefs, micDefault, start)    // the layout is read inside (engine/nav/Simple.kt games)
    }
}

/** Progressions (2.7, at the top of Learn; the Chords tab's Progressions before): hear each one, then practise it in the player. */
@Composable
fun ProgressionsPage(sampler: Sampler, prefs: Prefs, onPlay: (Progression, Int) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTop(com.bigpapituna.sharpears.engine.nav.Names.PROGRESSIONS, onBack)
        var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        Progressions(sampler, onPlay, sound = { prefs.sound }, layout = com.bigpapituna.sharpears.engine.nav.Simple.shown(LocalLayout.current.layout, more),
            footer = { LayoutMore(com.bigpapituna.sharpears.engine.nav.Simple.Place.PROGRESSIONS, more, { more = true }) })
    }
}

/** The dictionary's qualities in three groups, in the order a learner meets them. */
private val QUALITY_GROUPS = listOf(
    "Triads" to listOf(ChordType.MAJOR, ChordType.MINOR, ChordType.DIM, ChordType.AUG, ChordType.SUS2, ChordType.SUS4),
    "Sevenths" to listOf(ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7, ChordType.DIM7, ChordType.HALF_DIM),
    "More" to listOf(ChordType.SIX, ChordType.MIN6, ChordType.ADD9, ChordType.NINE),
)

/** What each note of a chord is, from the root, for the pup's line (a diminished seventh's top note is its seventh, not a sixth). */
internal fun intervalWord(semitones: Int, type: ChordType) = when (semitones) {
    2 -> "a second"; 3 -> "a minor third"; 4 -> "a major third"; 5 -> "a fourth"; 6 -> "a flat fifth"; 7 -> "a fifth"
    8 -> "a sharp fifth"; 9 -> if (type == ChordType.DIM7) "a diminished seventh" else "a sixth"; 10 -> "a minor seventh"
    11 -> "a major seventh"; 14 -> "a ninth"; else -> "the root"
}

private fun ordinalWord(i: Int) = listOf("Root position", "First inversion", "Second inversion", "Third inversion")[i]

/**
 * The chord dictionary: the chord's name and a dark keyboard panel pinned at the top (fingers on every key, a
 * Play button that walks the notes one by one with a finger dot, then strikes them together), and under it one
 * scrolling strip of roots, one of qualities grouped as triads, sevenths and more, and the inversions as a
 * segmented control. Never a wrapping wall of chips.
 */
@Composable
internal fun Dictionary(sampler: Sampler, sound: () -> Boolean, initial: Chord = Chord(0, ChordType.MAJOR),
                        /** The layout (2.8, Simple and Advanced): just these chords, in root position (no roots, qualities or inversions to pick). */
                        only: List<Chord>? = null) {
    var root by remember { mutableIntStateOf((only?.firstOrNull() ?: initial).root) }
    var type by remember { mutableStateOf((only?.firstOrNull() ?: initial).type) }
    var inversion by remember { mutableIntStateOf(0) }
    val chord = Chord(root, type)
    if (inversion >= chord.inversionCount) inversion = 0
    val notes = chord.voicing(inversion)
    val fingers = chord.fingering(inversion)
    val scope = rememberCoroutineScope()
    val sounding = remember { mutableStateMapOf<Int, Hand>() }
    val touched = remember { mutableStateMapOf<Int, Hand>() }
    val dots = remember { mutableStateMapOf<Hand, FingerDot>() }
    var job by remember { mutableStateOf<Job?>(null) }
    fun stop() { job?.cancel(); job = null }
    DisposableEffect(root, type, inversion) { onDispose { stop() } }
    fun play() {
        Log.d(TAG, "chords: play ${chord.name} inv=$inversion $notes")
        job = scope.launch {
            val me = coroutineContext[Job]
            try {
                // One by one with the finger on each, then all together.
                for ((m, f) in notes.zip(fingers)) {
                    sounding[m] = Hand.RIGHT; dots[Hand.RIGHT] = FingerDot(m, f); if (sound()) sampler.noteOn(m, 80)
                    delay(380)
                    sampler.noteOff(m); sounding.remove(m)
                    delay(40)
                }
                dots.clear()
                delay(160)
                notes.forEach { sounding[it] = Hand.RIGHT; if (sound()) sampler.noteOn(it, 90) }
                delay(1500)
            } finally {
                notes.forEach { sampler.noteOff(it) }
                sounding.clear(); dots.clear()
                if (job === me) job = null
            }
        }
    }
    // Two octaves from middle C, moved up (or widened) only when the chord reaches past them.
    var low = 60
    while (notes.max() > low + 23 && notes.min() >= low + 12) low += 12
    val high = if (notes.max() > low + 23) low + 35 else low + 23
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        // Pinned: the chord and its keyboard.
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PanelColor)
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chordTitle(chord), style = MaterialTheme.typography.displaySmall, color = Ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(notes.joinToString("  ") { chord.spell(it).code }, style = Numeric.merge(MaterialTheme.typography.titleSmall), color = InkDim)
                }
                Box(Modifier.pressScale(0.9f).size(56.dp).clip(CircleShape).background(RightHandColor)
                    .clickable(role = Role.Button) { if (job != null) stop() else play() }
                    .semantics { contentDescription = if (job != null) "Stop" else "Play ${com.bigpapituna.sharpears.engine.chordSpoken(chord)}" },
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (job != null) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null,
                        tint = PanelColor, modifier = Modifier.size(28.dp))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val whites = (low..high).count { !isBlack(it) }
                val keyH = (maxWidth / whites * 4.2f).coerceIn(120.dp, 200.dp)
                ScaleKeyboard(low, high, notes.zip(fingers).toMap(), emptyMap(), sounding + touched, dots,
                    onNoteOn = { m -> touched[m] = Hand.RIGHT; if (sound()) sampler.noteOn(m, 90) },
                    onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
                    description = "${chord.name}: " + notes.zip(fingers).joinToString(", ") { (n, f) -> "${chord.spell(n).code} finger $f" },
                    modifier = Modifier.fillMaxWidth().height(keyH + 16.dp))
            }
            Text("Right hand fingers, 1 is the thumb", style = MaterialTheme.typography.labelMedium, color = InkDim, modifier = Modifier.padding(start = 4.dp))
        }
        // Under it, the choices: one strip each.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (only != null) com.bigpapituna.sharpears.ui.practice.PillRow(only.map { it.name }, only.indexOfFirst { it.root == root && it.type == type }.coerceAtLeast(0),
                { root = only[it].root; type = only[it].type; inversion = 0 })
            if (only == null) KeyPicker((0..11).map { rootName(it) }, root, List(12) { 0 }, { root = it })
            val strip = rememberLazyListState(initialFirstVisibleItemIndex = 0)
            if (only == null) {
            LazyRow(Modifier.fillMaxWidth(), state = strip, contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                QUALITY_GROUPS.forEachIndexed { g, (name, types) ->
                    item(key = name) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
                            modifier = Modifier.padding(start = if (g == 0) 0.dp else 12.dp, end = 2.dp))
                    }
                    items(types.size, key = { types[it].name }) { i ->
                        val t = types[i]
                        Pill(t.label.replaceFirstChar { it.uppercase() }, t == type, { type = t })
                    }
                }
            }
            SlidingSegments((0 until chord.inversionCount).map { if (it == 0) "Root" else listOf("1st", "2nd", "3rd")[it - 1] }, inversion,
                { inversion = it }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), height = 40.dp,
                descriptions = (0 until chord.inversionCount).map(::ordinalWord))
            }
            val built = type.intervals.drop(1).joinToString(", ") { intervalWord(it, type) }.let { s ->
                val i = s.lastIndexOf(", "); if (i < 0) s else s.substring(0, i) + " and " + s.substring(i + 2)
            }
            PupLine(if (job != null) Pose.LISTENING else Pose.LEARNING,
                "${chordTitle(chord)}: the root, then $built." + if (inversion > 0) " ${ordinalWord(inversion)}: ${chord.toneName(notes.first())} is at the bottom." else "",
                Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Chord families offered for practice. */
private val FAMILIES = listOf(
    "Major" to listOf(ChordType.MAJOR), "Minor" to listOf(ChordType.MINOR),
    "Sevenths" to listOf(ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7), "Sus and dim" to listOf(ChordType.SUS2, ChordType.SUS4, ChordType.DIM),
)

@Composable
private fun Practice(sampler: Sampler, midi: MidiDevices, mic: MicInput, inject: String?, sound: () -> Boolean,
                     /** The layout (2.8, Simple and Advanced): only these chords, and no families to pick. */
                     only: List<Chord>? = null) {
    var families by remember { mutableStateOf(setOf("Major", "Minor")) }
    val pool = remember(families, only) { only ?: (0..11).flatMap { r -> FAMILIES.filter { it.first in families }.flatMap { it.second }.map { Chord(r, it) } } }
    // Injected (debug) runs use a fixed seed so the chord order is known in advance.
    val drill = remember(pool) { ChordDrill(pool.ifEmpty { listOf(Chord(0, ChordType.MAJOR)) }, seed = if (inject != null) 1 else System.nanoTime()) }
    ChordTrainer(drill, sampler, midi, mic, micStart = inject != null, inject = inject, sound = sound,
        header = {
            // One row of pills (several can be on), never a wrapping cloud.
            if (only != null) Text("The six first chords: " + only.dropLast(1).joinToString(", ") { it.name } + " and ${only.last().name}.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FAMILIES.forEach { (name, _) ->
                    Pill(name, name in families, { families = if (name in families && families.size > 1) families - name else families + name })
                }
            }
        },
        skip = { drill.next() }, pup = true)
}

/**
 * One chord at a time from [drill], played by touch, MIDI or the mic. [onDone] gets each finished chord and
 * whether it was played without a wrong note; [skip] runs on the Skip button.
 */
@Composable
private fun ChordTrainer(
    drill: ChordDrill, sampler: Sampler, midi: MidiDevices, mic: MicInput, micStart: Boolean, inject: String?,
    /** Settings > App sound: off, the touch keys are silent. */
    sound: () -> Boolean,
    header: @Composable () -> Unit, skip: () -> Unit, onDone: (Chord, Boolean) -> Unit = { _, _ -> },
    /** The pup beside the chord, reacting to each one (the Practice tab; a session shows him in its own header). */
    pup: Boolean = false,
    /**
     * 2.14 (engine/nav/DayOne.kt ChordWords): a beginner's words, "Play a C chord" over the keys already lit and "C, E and G
     * are lit. Any octave counts.", never octave numbers or "any order".
     */
    plain: Boolean = false,
) {
    var version by remember { mutableIntStateOf(0) }
    var showNotes by remember { mutableStateOf(plain) }
    var micOn by remember { mutableStateOf(micStart && mic.hasPermission() || inject != null) }
    var flash by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val keyFlash = remember { mutableStateMapOf<Int, Color>() }               // a burst on the keys: green when a chord lands, warm on a slip
    val scope = rememberCoroutineScope()
    val touched = remember { mutableStateMapOf<Int, Color>() }
    // 2.15: through the microphone card; its Not now stays on the keys, quietly.
    val askMic = rememberMicAsk(mic) { if (it) micOn = true else if (!mic.micNotNow) flash = MIC_REFUSED_LINE to false }

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
            ChordDrill.Result.DONE -> {
                onDone(before, drill.lastClean)
                val keys = before.voicing(0)
                keys.forEach { keyFlash[it] = Good }
                scope.launch { flash = "${before.name}, all notes heard" to true; delay(700); flash = null; keys.forEach { if (keyFlash[it] == Good) keyFlash.remove(it) } }
            }
            ChordDrill.Result.MISS -> { keyFlash[m] = WrongGlow; scope.launch { flash = "${noteName(m)} is not in ${before.name}" to false; delay(700); flash = null; if (keyFlash[m] == WrongGlow) keyFlash.remove(m) } }
            ChordDrill.Result.PART -> {}
        }
        version++
    }

    LaunchedEffect(midi, drill) {
        midi.events.collect { e -> if (e is NoteEvent.On) { touched[e.midi] = RightHandColor; strike(e.midi, "midi") } else touched.remove(e.midi) }
    }
    val visible = onScreen()
    DisposableEffect(micOn, visible) {
        val on = micOn && visible
        if (on) {
            if (inject != null) mic.startInjection(inject) else if (!mic.start()) micOn = false
            chordStart = 0L                                   // the matcher's sample clock restarts with the mic
        }
        onDispose { if (on) mic.stop() }          // only the mic this effect started: never the next screen's
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

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        header()
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (pup) Pup(when {
                flash?.second == true -> Pose.GREAT_JOB
                flash?.second == false -> Pose.MISTAKE
                drill.streak >= 3 -> Pose.STREAK
                else -> Pose.GET_READY
            }, size = 72.dp, onTap = {})
            Column(Modifier.weight(1f)) {
                if (plain) Text(com.bigpapituna.sharpears.engine.nav.ChordWords.ask(current), style = MaterialTheme.typography.headlineMedium)
                else {
                    Text("Play", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current.name, style = MaterialTheme.typography.displayMedium)
                }
            }
            // The running counts belong to the open-ended Practice tab: a session's drill is one chord, so they read 0 or
            // 1 and went back to 0 on every chord ("1 of 5" says where a session stands).
            if (pup) { Stat("Correct", drill.correct); Stat("Streak", drill.streak) }
        }
        // Feedback line: fixed height so the keyboard does not jump when it appears.
        Text(flash?.first ?: "", color = if (flash?.second == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.height(24.dp))
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(micOn, {
                if (micOn) micOn = false else if (mic.hasPermission()) micOn = true else askMic.ask()
            }, label = { Text(if (micOn) "Listening" else "Use mic") },
                leadingIcon = { Icon(painterResource(if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off), contentDescription = null, modifier = Modifier.size(18.dp)) })
            FilterChip(showNotes, { showNotes = !showNotes }, label = { Text("Show notes") })
            TextButton(onClick = { skip(); version++ }) { Text("Skip") }
        }
        Text(
            // 2.14 (Dana): the notes by name, not "between C3 and G4": which keys make the chord, in any order and any octave;
            // a beginner's plain words over the keys already lit (levels-ages, engine/nav/DayOne.kt ChordWords).
            if (plain) com.bigpapituna.sharpears.engine.nav.ChordWords.keysLine(current)
            else com.bigpapituna.sharpears.engine.chordPrompt(voicing.map(current::toneName).distinct(), micOn),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lit = buildMap {
            if (showNotes) voicing.forEach { put(it, RightHandColor.copy(alpha = 0.5f)) }
            voicing.filter { it % 12 in drill.heard }.forEach { put(it, Good) }
            putAll(touched)
        }
        Keyboard(
            LOW, HIGH, lit = lit,
            onNoteOn = { m, v -> touched[m] = Good; if (sound() && (!micOn || mic.headphonesOn())) sampler.noteOn(m, v); strike(m, "touch") },
            onNoteOff = { m -> touched.remove(m); sampler.noteOff(m) },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            flash = keyFlash,
            labels = if (showNotes) voicing.zip(current.fingering(0)).associate { (n, f) -> n to f.toString() } else emptyMap(),
        )
    }
}

@Composable
private fun Stat(label: String, value: Int) = Column(horizontalAlignment = Alignment.End) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$value", style = Numeric.merge(MaterialTheme.typography.headlineSmall))
}

@Composable
private fun Progressions(sampler: Sampler, onPlay: (Progression, Int) -> Unit, sound: () -> Boolean,
                         /** The layout (2.8): the pop progressions only until Full (engine/nav/Simple.kt). */
                         layout: com.bigpapituna.sharpears.engine.nav.Layout = com.bigpapituna.sharpears.engine.nav.Layout.FULL, footer: @Composable () -> Unit = {}) {
    var key by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Pair<Int, Int>?>(null) }      // progression, chord sounding
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(key) { onDispose { job?.cancel() } }
    val c = MaterialTheme.colorScheme
    fun hear(pi: Int) {
        job?.cancel()
        val p = PROGRESSIONS[pi]
        val chords = p.degrees.map { Chord((key + it.semitones) % 12, it.type) }
        val voicings = voiceLead(chords)
        job = scope.launch {
            val me = coroutineContext[Job]
            var sounding = emptyList<Int>()
            try {
                // A blues runs twelve bars: each chord a little quicker so the whole form fits in a breath.
                val ms = if (chords.size > 4) 520L else 760L
                chords.forEachIndexed { i, ch ->
                    sounding = listOf(48 + ch.root % 12) + voicings[i]
                    playing = pi to i
                    if (sound()) sounding.forEach { sampler.noteOn(it, 80) }
                    delay(ms)
                    sounding.forEach(sampler::noteOff)
                }
            } finally {
                sounding.forEach(sampler::noteOff)
                if (job === me) { playing = null; job = null }
            }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SlidingSegments(PROGRESSION_KEYS.map { rootName(it) }, PROGRESSION_KEYS.indexOf(key), { key = PROGRESSION_KEYS[it] },
            Modifier.padding(horizontal = 20.dp).widthIn(max = 520.dp).fillMaxWidth(), height = 40.dp,
            descriptions = PROGRESSION_KEYS.map { "Key of ${rootName(it)}" })
        PupLine(if (playing != null) Pose.LISTENING else Pose.LEARNING,
            "Tap a row to hear it. Practise opens it in the player: chords in the right hand, roots in the left.",
            Modifier.padding(horizontal = 16.dp))
        val shown = com.bigpapituna.sharpears.engine.nav.Simple.progressions(PROGRESSIONS, layout)
        PROGRESSIONS.forEachIndexed { pi, p ->
            if (p !in shown) return@forEachIndexed
            val chords = p.degrees.map { Chord((key + it.semitones) % 12, it.type) }
            Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .clickable(onClickLabel = "Hear it") { if (playing?.first == pi) job?.cancel() else hear(pi) }.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { job?.cancel(); onPlay(p, key) }) { Text("Practise") }
                }
                // The chords as tiles that light one after another while it plays.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chords.forEachIndexed { i, ch ->
                        val lit = playing == pi to i
                        val bg by animateColorAsState(if (lit) c.primary else c.surfaceContainerHigh, tween(120), label = "tile")
                        Column(Modifier.size(width = if (chords.size > 4) 56.dp else 72.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)).background(bg),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
                                color = if (lit) c.onPrimary else c.onSurface, maxLines = 1)
                            Text(p.degrees[i].numeral, style = MaterialTheme.typography.labelSmall, color = if (lit) c.onPrimary else c.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        footer()
    }
}

/** A plain row: title, one line of detail, and a text action on the right. No card. */
@Composable
private fun ActionRow(title: String, detail: String, action: String?, onAction: () -> Unit, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * After a chord session in Learn chords (a pack's Practise, or today's chords): the popular songs those chords play
 * ("Songs that use these chords", ui/chords), [onOpen] a song's page, [onMore] the list narrowed to what can be played.
 */
private class SessionSongs(val songs: List<com.bigpapituna.sharpears.engine.chords.PopSong>,
                           val onOpen: (com.bigpapituna.sharpears.engine.chords.PopSong) -> Unit, val onMore: () -> Unit)

@Composable
private fun Lessons(
    store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, inject: String?,
    onPlayProgression: (Progression, Int) -> Unit, sound: () -> Boolean, songsAfter: SessionSongs? = null,
    /** Above the review line: "You can play N songs" (Learn chords). */
    header: @Composable () -> Unit = {},
    /** Under the review line: the dictionary and free practice (Learn chords). */
    tools: @Composable () -> Unit = {},
    /** The layout (2.8): the packs cut to the six beginner chords until Full, song packs whole from Advanced (engine/nav/Simple.kt). */
    layout: com.bigpapituna.sharpears.engine.nav.Layout = com.bigpapituna.sharpears.engine.nav.Layout.FULL,
    /** At the very bottom: the layout's "Show more". */
    footer: @Composable () -> Unit = {},
) {
    val v = store.version.intValue
    if (store.session.isNotEmpty()) {
        BackHandler { store.endSession() }                 // Back leaves the session, not Learn chords
        Session(store, sampler, midi, mic, micDefault, inject, onPlayProgression, sound = sound, songsAfter = songsAfter); return
    }
    val now = remember(v) { System.currentTimeMillis() }
    val songPacks = store.songPacks.value
    val cur = remember(v, songPacks, layout) {
        store.curriculum().let { com.bigpapituna.sharpears.engine.Curriculum(com.bigpapituna.sharpears.engine.nav.Simple.packs(it.packs, layout)) }
    }
    val mastery = remember(v) { store.mastery() }
    val today = remember(cur, mastery) { cur.queue(mastery, now) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
      Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        header()
        // The daily chord review as one line (2.7; the Daily tab's panel before): how many are due, and Start.
        val due = today.count { mastery[it.id]?.seen == true }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(if (today.isEmpty()) Pose.RESTING else Pose.GET_READY, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (today.isEmpty()) "All caught up" else "${com.bigpapituna.sharpears.engine.plural(today.size, "chord")} to practise", style = MaterialTheme.typography.titleLarge)
                Text(when {
                    today.isEmpty() -> "Come back tomorrow, or practise any pack below."
                    due == 0 -> "All new. Chords you play cleanly come back less often."
                    else -> "$due to review and ${today.size - due} new."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (today.isNotEmpty()) ChunkyButton(onClick = { store.startSession(today) }) { Text("Start") }
        }
        tools()
        val (authored, fromSongs) = cur.packs.partition { it.level < 6 }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Packs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() })
            authored.sortedBy { it.level }.forEach { PackRow(it, store, cur.unlocked(it, mastery), cur, mastery, now) }
            Text("From your songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() })
            if (fromSongs.isEmpty()) PupSays(Pose.LISTENING, "Finding the chords in your songs.", size = 48.dp, style = MaterialTheme.typography.bodyMedium)
            fromSongs.forEach { PackRow(it, store, true, cur, mastery, now) }
        }
        footer()
      }
    }
}

@Composable
private fun PackRow(pack: LessonPack, store: LessonStore, unlocked: Boolean, cur: com.bigpapituna.sharpears.engine.Curriculum,
                    mastery: Map<String, com.bigpapituna.sharpears.engine.Mastery>, now: Long) {
    val c = cur.counts(pack, mastery, now)
    val learned = pack.items.size - c.new
    ActionRow(
        title = pack.title,
        detail = when {
            !unlocked -> "Opens after " + pack.requires.mapNotNull { r -> cur.packs.firstOrNull { it.id == r }?.title?.lowercase() }.joinToString(" and ")
            pack.level >= 6 -> pack.items.joinToString("  ") { (it as? LessonItem.ChordItem)?.chord?.name ?: "" }
            else -> "$learned of ${pack.items.size} started" + (if (c.due > 0) ", ${c.due} due" else "") + (if (c.mastered > 0) ", ${c.mastered} mastered" else "")
        },
        action = if (unlocked) "Practise" else null,
        onAction = { store.startSession(cur.queue(mastery, now, newLimit = 8, only = pack).ifEmpty { pack.items.take(8) }) },
        dim = !unlocked,
    )
}

/**
 * The chord part of "Practice today": the due chords ([itemIds]) one after another, as in Learn chords.
 * [onEnd] runs with how many were answered when the last one is done or the rest is skipped.
 */
@Composable
fun DailyChords(store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, itemIds: List<String>,
                inject: String?, sound: () -> Boolean = { true },
                /** 2.14: a beginner's plain chord words with the keys lit (engine/nav/DayOne.kt plainChords). */
                plain: Boolean = false,
                onEnd: (answered: Int) -> Unit) {
    LaunchedEffect(itemIds) {
        val all = store.curriculum().packs.flatMap { it.items }.distinctBy { it.id }.associateBy { it.id }
        store.startSession(itemIds.mapNotNull { all[it] })
    }
    if (store.session.isEmpty()) return
    Session(store, sampler, midi, mic, micDefault, inject, onPlayProgression = { _, _ -> }, sound = sound, plain = plain,
        onEnd = { val n = store.sessionIndex; store.endSession(); onEnd(n) })
}

@Composable
private fun Session(
    store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, inject: String?,
    onPlayProgression: (Progression, Int) -> Unit,
    /** Set inside "Practice today": the end of the queue (or End) hands back to the session instead of a summary. */
    onEnd: (() -> Unit)? = null,
    sound: () -> Boolean,
    songsAfter: SessionSongs? = null,
    plain: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val item = store.session.getOrNull(store.sessionIndex)
    val n = store.session.size
    if (item == null && onEnd != null) { LaunchedEffect(Unit) { onEnd() }; return }
    if (item == null) {
        // The songs these chords play: the ones learned before and the ones just practised (still new to the Leitner boxes).
        val chords = remember(n) {
            com.bigpapituna.sharpears.engine.chords.PopFind.known(store.mastery()) + store.session.mapNotNull { (it as? LessonItem.ChordItem)?.chord }
        }
        val found = remember(n) { songsAfter?.let { com.bigpapituna.sharpears.engine.chords.PopFind.using(it.songs, chords, limit = 3) }.orEmpty() }
        LaunchedEffect(n) { if (songsAfter != null) Log.d(TAG, "pop: songs for the session's chords: " + found.joinToString { it.id }.ifEmpty { "none" }) }
        Celebration("Session done!", "The ones that were not clean come back tomorrow.",
            listOf(Stat("${store.sessionCorrect}/$n", "Clean"), Stat("$n", "Practised")),
            primary = "Back to Learn chords", onPrimary = store::endSession, sampler = sampler.takeIf { sound() },
            reward = if (songsAfter == null || found.isEmpty()) null else { {
                com.bigpapituna.sharpears.ui.chords.SongsForChordsCard(songsAfter.songs, chords,
                    onOpen = { song -> store.endSession(); songsAfter.onOpen(song) },
                    onMore = { store.endSession(); songsAfter.onMore() })
            } })
        return
    }
    val progress = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pup(Pose.PRACTICING, size = 56.dp)
            Text("${store.sessionIndex + 1} of $n", style = Numeric.merge(MaterialTheme.typography.titleMedium), modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { if (onEnd != null) onEnd() else store.endSession() }) { Text(if (onEnd != null) "Skip the rest" else "End session") }
        }
    }
    when (item) {
        is LessonItem.ChordItem -> Box(Modifier.fillMaxSize()) {
            val drill = remember(store.sessionIndex) { ChordDrill(listOf(item.chord), seed = 1) }
            // How the chord went (null until played); the pup's bar then waits on Continue, like a lesson step.
            var result by remember(store.sessionIndex) { mutableStateOf<Boolean?>(null) }
            ChordTrainer(drill, sampler, midi, mic, micStart = micDefault, inject = inject, sound = sound, header = progress, plain = plain,
                skip = { store.answer(false) },
                onDone = { _, clean -> if (result == null) result = clean })
            result?.let { clean ->
                CheerBar(if (clean) Cheer.GOOD else Cheer.MISS,
                    if (clean) cheerTitle(store.sessionIndex + store.sessionCorrect) else "Almost",
                    if (clean) "Played cleanly." else "Not clean yet, so it comes back sooner.",
                    "Continue", Modifier.align(Alignment.BottomCenter)) { store.answer(clean) }
            }
        }
        is LessonItem.ProgressionItem -> Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            progress()
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            // The pup's line over the progression playing on the staff and keys (tap to hear it again).
            PupSays(Pose.LISTENING, "Play it through in wait mode: right hand the chords, left hand the roots. Then come back and say how it went.",
                size = 56.dp, style = MaterialTheme.typography.bodyMedium)
            var replay by remember(store.sessionIndex) { mutableIntStateOf(0) }
            val visual = remember(item) { com.bigpapituna.sharpears.engine.progressionVisual(PROGRESSIONS[item.progression], item.key) }
            Box(Modifier.fillMaxWidth().widthIn(max = 560.dp).aspectRatio(1.3f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                com.bigpapituna.sharpears.ui.theory.ScenePreview(visual, Modifier.fillMaxSize().padding(6.dp), sampler = sampler, replay = replay,
                    description = "${item.title}, chord by chord on the staff and keys")
                androidx.compose.material3.IconButton(onClick = { replay++ }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(painterResource(R.drawable.ic_repeat), contentDescription = "Hear it again")
                }
            }
            ChunkyButton(onClick = { onPlayProgression(PROGRESSIONS[item.progression], item.key) }) { Text("Open in the player") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { store.answer(true) }) { Text("Went well") }
                TextButton(onClick = { store.answer(false) }) { Text("Needs work") }
            }
        }
    }
}
