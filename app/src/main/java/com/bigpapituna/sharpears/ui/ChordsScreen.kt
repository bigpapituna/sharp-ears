package com.bigpapituna.sharpears.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private const val LOW = 48   // C3
private const val HIGH = 84  // C6

enum class ChordsTab { PLAY, LESSONS, DICTIONARY, PRACTICE, PROGRESSIONS, SONGS }

@Composable
fun ChordsScreen(
    sampler: Sampler, midi: MidiDevices, mic: MicInput, voice: Voice, prefs: Prefs,
    lessons: LessonStore, micDefault: Boolean,
    startTab: ChordsTab = ChordsTab.PLAY, inject: String? = null, gameStart: GameStart = GameStart(),
    onPlayProgression: (Progression, Int) -> Unit,
    /** Songs by chords (ui/chords): the list shown in the Songs tab, and the song a tap opens. */
    popSongs: List<com.bigpapituna.sharpears.engine.chords.PopSong> = emptyList(),
    popStart: com.bigpapituna.sharpears.ui.chords.PopStart = com.bigpapituna.sharpears.ui.chords.PopStart(),
    onOpenPopSong: (com.bigpapituna.sharpears.engine.chords.PopSong) -> Unit = {},
) {
    var tab by remember { mutableStateOf(startTab) }
    // Where Songs opens: the caller's start, or "Songs I can play" after a tap on the count or the session's card.
    var popOpen by remember { mutableStateOf(popStart) }
    fun showPlayable() { popOpen = com.bigpapituna.sharpears.ui.chords.PopStart(sort = com.bigpapituna.sharpears.engine.chords.PopFind.Sort.CAN_PLAY); tab = ChordsTab.SONGS }
    // The chords learned so far (Leitner box 3 or more) decide which songs "you can play".
    val known = remember(lessons.version.intValue) { com.bigpapituna.sharpears.engine.chords.PopFind.known(lessons.mastery()) }
    val playable = remember(popSongs, known) { com.bigpapituna.sharpears.engine.chords.PopFind.playableCount(popSongs, known) }
    Column {
        Text("Chords", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() })
        PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background,
            divider = {}) {
            Tab(tab == ChordsTab.PLAY, { tab = ChordsTab.PLAY }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Play") })
            Tab(tab == ChordsTab.LESSONS, { tab = ChordsTab.LESSONS }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Daily") })
            Tab(tab == ChordsTab.DICTIONARY, { tab = ChordsTab.DICTIONARY }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Dictionary") })
            Tab(tab == ChordsTab.PRACTICE, { tab = ChordsTab.PRACTICE }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Practice") })
            Tab(tab == ChordsTab.PROGRESSIONS, { tab = ChordsTab.PROGRESSIONS }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Progressions") })
            if (popSongs.isNotEmpty()) Tab(tab == ChordsTab.SONGS, { tab = ChordsTab.SONGS }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant, text = { Text("Songs") })
        }
        // The count of songs the learned chords play, on every tab but the songs' own (which says it itself).
        if (popSongs.isNotEmpty() && playable > 0 && tab != ChordsTab.SONGS) com.bigpapituna.sharpears.ui.chords.PlayableLine(playable,
            Modifier.padding(start = 20.dp, top = 2.dp, bottom = 4.dp)) { showPlayable() }
        when (tab) {
            ChordsTab.PLAY -> ChordGamesScreen(sampler, midi, mic, voice, prefs, micDefault, gameStart.copy(inject = inject ?: gameStart.inject))
            ChordsTab.LESSONS -> Lessons(lessons, sampler, midi, mic, micDefault, inject, onPlayProgression, sound = { prefs.sound },
                songsAfter = if (popSongs.isEmpty()) null else SessionSongs(popSongs, onOpenPopSong, ::showPlayable))
            ChordsTab.DICTIONARY -> Dictionary(sampler, sound = { prefs.sound })
            ChordsTab.PRACTICE -> Practice(sampler, midi, mic, inject, sound = { prefs.sound })
            ChordsTab.PROGRESSIONS -> Progressions(sampler, onPlayProgression, sound = { prefs.sound })
            ChordsTab.SONGS -> androidx.compose.runtime.key(popOpen) {
                com.bigpapituna.sharpears.ui.chords.PopBrowse(popSongs, known, onOpenPopSong, Modifier.fillMaxSize(), start = popOpen)
            }
        }
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
internal fun Dictionary(sampler: Sampler, sound: () -> Boolean, initial: Chord = Chord(0, ChordType.MAJOR)) {
    var root by remember { mutableIntStateOf(initial.root) }
    var type by remember { mutableStateOf(initial.type) }
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
            KeyPicker((0..11).map { rootName(it) }, root, List(12) { 0 }, { root = it })
            val strip = rememberLazyListState(initialFirstVisibleItemIndex = 0)
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
private fun Practice(sampler: Sampler, midi: MidiDevices, mic: MicInput, inject: String?, sound: () -> Boolean) {
    var families by remember { mutableStateOf(setOf("Major", "Minor")) }
    val pool = remember(families) { (0..11).flatMap { r -> FAMILIES.filter { it.first in families }.flatMap { it.second }.map { Chord(r, it) } } }
    // Injected (debug) runs use a fixed seed so the chord order is known in advance.
    val drill = remember(pool) { ChordDrill(pool.ifEmpty { listOf(Chord(0, ChordType.MAJOR)) }, seed = if (inject != null) 1 else System.nanoTime()) }
    ChordTrainer(drill, sampler, midi, mic, micStart = inject != null, inject = inject, sound = sound,
        header = {
            // One row of pills (several can be on), never a wrapping cloud.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
) {
    var version by remember { mutableIntStateOf(0) }
    var showNotes by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(micStart && mic.hasPermission() || inject != null) }
    var flash by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val keyFlash = remember { mutableStateMapOf<Int, Color>() }               // a burst on the keys: green when a chord lands, warm on a slip
    val scope = rememberCoroutineScope()
    val touched = remember { mutableStateMapOf<Int, Color>() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) micOn = true else flash = MIC_REFUSED_LINE to false }

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
                Text("Play", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.name, style = MaterialTheme.typography.displayMedium)
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
                if (micOn) micOn = false else if (mic.hasPermission()) micOn = true else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }, label = { Text(if (micOn) "Listening" else "Use mic") },
                leadingIcon = { Icon(painterResource(if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off), contentDescription = null, modifier = Modifier.size(18.dp)) })
            FilterChip(showNotes, { showNotes = !showNotes }, label = { Text("Show notes") })
            TextButton(onClick = { skip(); version++ }) { Text("Skip") }
        }
        Text(
            if (micOn) "Play it between ${noteName(micNotes.first())} and ${noteName(micNotes.last())}, in any order. Notes heard turn green."
            else "Any octave, any order. Notes heard so far turn green.",
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
private fun Progressions(sampler: Sampler, onPlay: (Progression, Int) -> Unit, sound: () -> Boolean) {
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
        PROGRESSIONS.forEachIndexed { pi, p ->
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
 * After a chord session in Daily (a pack's Practise, or today's chords): the popular songs those chords play
 * ("Songs that use these chords", ui/chords), [onOpen] a song's page, [onMore] the list narrowed to what can be played.
 */
private class SessionSongs(val songs: List<com.bigpapituna.sharpears.engine.chords.PopSong>,
                           val onOpen: (com.bigpapituna.sharpears.engine.chords.PopSong) -> Unit, val onMore: () -> Unit)

@Composable
private fun Lessons(
    store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, inject: String?,
    onPlayProgression: (Progression, Int) -> Unit, sound: () -> Boolean, songsAfter: SessionSongs? = null,
) {
    val v = store.version.intValue
    if (store.session.isNotEmpty()) {
        BackHandler { store.endSession() }                 // Back leaves the session, not the Chords tab
        Session(store, sampler, midi, mic, micDefault, inject, onPlayProgression, sound = sound, songsAfter = songsAfter); return
    }
    val now = remember(v) { System.currentTimeMillis() }
    val songPacks = store.songPacks.value
    val cur = remember(v, songPacks) { store.curriculum() }
    val mastery = remember(v) { store.mastery() }
    val today = remember(cur, mastery) { cur.queue(mastery, now) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).verticalScroll(rememberScrollState())) {
        // The one raised surface on this tab: today's session.
        Column(
            Modifier.fillMaxWidth().widthIn(max = 720.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val due = today.count { mastery[it.id]?.seen == true }
            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (today.isEmpty()) "All caught up" else "${com.bigpapituna.sharpears.engine.plural(today.size, "chord")} to practise", style = MaterialTheme.typography.headlineMedium)
            if (today.isEmpty()) PupSays(Pose.RESTING, "Come back tomorrow, or practise any pack below.", Modifier.padding(vertical = 4.dp), size = 56.dp,
                style = MaterialTheme.typography.bodyLarge)
            else Text(
                if (due == 0) "All new. Chords you play cleanly come back less often." else "$due to review and ${today.size - due} new.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (today.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                ChunkyButton(onClick = { store.startSession(today) }) { Text("Start practice") }
            }
        }
        val (authored, fromSongs) = cur.packs.partition { it.level < 6 }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Packs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() })
            authored.sortedBy { it.level }.forEach { PackRow(it, store, cur.unlocked(it, mastery), cur, mastery, now) }
            Text("From your songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() })
            if (fromSongs.isEmpty()) PupSays(Pose.LISTENING, "Finding the chords in your songs.", size = 48.dp, style = MaterialTheme.typography.bodyMedium)
            fromSongs.forEach { PackRow(it, store, true, cur, mastery, now) }
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
 * The chord part of "Practice today": the due chords ([itemIds]) one after another, as in the Daily tab.
 * [onEnd] runs with how many were answered when the last one is done or the rest is skipped.
 */
@Composable
fun DailyChords(store: LessonStore, sampler: Sampler, midi: MidiDevices, mic: MicInput, micDefault: Boolean, itemIds: List<String>,
                inject: String?, sound: () -> Boolean = { true }, onEnd: (answered: Int) -> Unit) {
    LaunchedEffect(itemIds) {
        val all = store.curriculum().packs.flatMap { it.items }.distinctBy { it.id }.associateBy { it.id }
        store.startSession(itemIds.mapNotNull { all[it] })
    }
    if (store.session.isEmpty()) return
    Session(store, sampler, midi, mic, micDefault, inject, onPlayProgression = { _, _ -> }, sound = sound,
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
            primary = "Back to lessons", onPrimary = store::endSession, sampler = sampler.takeIf { sound() },
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
            ChordTrainer(drill, sampler, midi, mic, micStart = micDefault, inject = inject, sound = sound, header = progress,
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
