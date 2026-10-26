package com.bigpapituna.sharpears.ui.chords

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.BandPlayer
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.chords.BandBed
import com.bigpapituna.sharpears.engine.chords.PopChord
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopPlay
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSongs
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Dictionary
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.practice.Pill
import com.bigpapituna.sharpears.ui.practice.PillRow
import com.bigpapituna.sharpears.ui.practice.PupLine
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.drawNoteColour
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A song's page (popular-song chord mode): its chords laid out by section as bars, in the key and at the tempo chosen,
 * with the pattern the band plays them in. "Hear the band" plays the drum-and-bass bed (and, if asked, a soft piano) and
 * lights the chart bar by bar; "Play along" hands the song to the player as chord blocks. A chord tapped opens the
 * dictionary's voicing for it (a kid hears it instead). Chords, keys and tempos only: no melody, no words.
 *
 * The three things a learner changes, [shift] (semitones from the song's own key, -6..6), [pattern] and [bpm], are held by
 * the caller so they survive a trip to the player and back ([onState]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopSongPage(
    song: PopSong, known: Set<Chord>, sampler: Sampler, sound: () -> Boolean,
    shift: Int, pattern: PopSong.Pattern, bpm: Int, onState: (shift: Int, pattern: PopSong.Pattern, bpm: Int) -> Unit,
    onPlayAlong: (PopPlay.Run) -> Unit, onBack: () -> Unit,
    /** Debug: start "Hear the band" as the page opens. */
    hearAtOpen: Boolean = false,
    /** Debug: show the kid variant whoever is playing. */
    kidOverride: Boolean? = null,
    /** Debug: the kid page for a child who cannot read yet (animals, not letters). */
    preReader: Boolean = false,
) {
    val profile = LocalProfile.current
    val kid = kidOverride ?: profile.kidSkin      // 2.14: a big kid gets the whole page (key, tempo, patterns, the band)
    val reader = (profile.reader || !profile.isKid) && !preReader
    val c = MaterialTheme.colorScheme
    val key = Math.floorMod(song.key + shift, 12)
    val flats = PopPlay.flats(key, song.minor)
    val built = remember(song, pattern, key, bpm) { PopPlay.toSong(song, pattern, key, bpm) }
    val bed = remember(song, pattern, key, bpm) { PopPlay.bandBed(song, pattern, key, bpm) }
    val bars = remember(song, shift) { PopPlay.bars(song, shift) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- hearing the band ---
    var hearing by remember { mutableStateOf(false) }
    var withPiano by rememberSaveable { mutableStateOf(true) }
    var heardMs by remember { mutableDoubleStateOf(-1.0) }
    val band = remember { BandPlayer(context, sampler) }
    DisposableEffect(Unit) { onDispose { band.release() } }
    LaunchedEffect(hearAtOpen) { if (hearAtOpen) { delay(900); hearing = true } }
    LaunchedEffect(hearing, built, bed, withPiano) {
        if (!hearing) return@LaunchedEffect
        Log.d(TAG, "pop: hearing ${song.id} ${PopPlay.keyName(key, song.minor)} ${pattern.name} at $bpm bpm, piano=$withPiano")
        val chords = built.notes.filter { it.hand == Hand.RIGHT }
        var next = 0
        val sounding = ArrayList<Note>()
        var last = -1.0
        val t0 = withFrameNanos { it }
        try {
            while (true) {
                val t = (withFrameNanos { it } - t0) / 1_000_000.0
                heardMs = t
                if (t > bed.durationMs + 600) break
                if (sound()) {
                    band.play(bed.cuesIn(last, t))
                    if (withPiano) {
                        while (next < chords.size && chords[next].startMs <= t) {
                            val n = chords[next++]
                            if (n.startMs >= last) { sampler.noteOn(n.midi, (n.velocity * 0.85f).toInt()); sounding += n }
                        }
                    }
                }
                sounding.removeAll { n -> (n.startMs + n.durationMs <= t).also { if (it) sampler.noteOff(n.midi) } }
                last = t
            }
        } finally {
            sounding.forEach { sampler.noteOff(it.midi) }
            band.stop()
            heardMs = -1.0
            hearing = false
        }
    }
    val beatNow = if (heardMs < 0) -1.0 else heardMs / (60_000.0 / bpm)

    // --- a chord tapped ---
    var sheet by remember { mutableStateOf<Chord?>(null) }
    fun tapChord(ch: PopChord) {
        val chord = PopPlay.chord(ch)
        if (!kid) { sheet = chord; return }
        // A kid hears it, with the right hand's voicing.
        val notes = chord.voicing(0)
        scope.launch {
            if (sound()) notes.forEach { sampler.noteOn(it, 85) }
            delay(900)
            notes.forEach { sampler.noteOff(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to the songs", tint = c.onSurface, modifier = Modifier.size(26.dp))
            }
            Text(if (kid) "Songs" else "Songs by chords", style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Header(song, key, bpm, kid, Modifier.padding(horizontal = 20.dp))
                // The two main buttons, and what is sounding now.
                FlowRow(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { hearing = !hearing }, modifier = Modifier.height(52.dp)) {
                        Icon(painterResource(if (hearing) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (hearing) "Stop the band" else "Hear the band")
                    }
                    ChunkyButton(onClick = { hearing = false; onPlayAlong(PopPlay.Run(built, bed, PopPlay.chordTrack(song, key, bpm))) }) { Text("Play along") }
                    // The band is drums and bass; the piano plays the chords softly over it.
                    if (!kid) Pill("Piano with it", withPiano, { withPiano = !withPiano })
                }
                if (!kid) {
                    VoiceCard(song, shift, bpm, key, onShift = { onState(it.coerceIn(-6, 6), pattern, bpm) },
                        onTempo = { onState(shift, pattern, it.coerceIn(minOf(40, song.bpm), maxOf(200, song.bpm))) }, Modifier.padding(horizontal = 20.dp))
                    PatternPicker(pattern, song.pattern, { onState(shift, it, bpm) })
                }
                Chart(song, bars, flats, beatNow, kid, reader, ::tapChord)
                // A kid hears the pup's own line (a song's note is for readers and may have counting in it).
                if (kid) PupLine(if (hearing) Pose.LISTENING else Pose.LEARNING, "Tap a colour to hear it. Then play them in a row!", Modifier.padding(horizontal = 16.dp))
                else if (song.note.isNotBlank()) PupLine(if (hearing) Pose.LISTENING else Pose.LEARNING, song.note, Modifier.padding(horizontal = 16.dp))
                if (!kid) {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chords in this song", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        // The names move with the key, and so does what counts as known: Am is not Bm.
                        ChordChips(remember(song, shift) { PopFind.chordsOf(PopSongs.transposed(song, shift)) }.map { ch ->
                            ch to PopPlay.name(PopChord(ch.root, ch.type), flats)
                        }, known, max = 12)
                        Text("Chords you have practised are tinted. Tap any chord in the chart to see how to play it.",
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
            }
        }
    }

    sheet?.let { ch ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            // Most of the window's height (the window, not the configuration: right on the Fold's two screens and in split screen).
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            Box(Modifier.height(windowHeight * 0.82f)) { Dictionary(sampler, sound, initial = ch) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(song: PopSong, key: Int, bpm: Int, kid: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(song.title, style = (if (kid) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium).copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.semantics { heading() })
        if (!kid) {
            Text("${song.artist}, ${song.year}", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            // Wraps on a narrow screen (the Fold's cover), so the confidence note ("a sketch") is never cut off.
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyBadge(key, PopPlay.keyName(key, song.minor), Modifier.size(40.dp))
                    Column {
                        Text("Key of ${PopPlay.keyName(key, song.minor)}", style = MaterialTheme.typography.titleSmall)
                        Text("$bpm bpm", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
                DifficultyBars(song.difficulty)
                if (PopSongs.isFourChord(song)) FourChordBadge()
                ConfidenceNote(song.confidence)
            }
        }
    }
}

/** "Transpose to my voice" and the tempo, each a stepper with its value between two round buttons. */
@Composable
private fun VoiceCard(song: PopSong, shift: Int, bpm: Int, key: Int, onShift: (Int) -> Unit, onTempo: (Int) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Key and tempo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val keyStepper = @Composable { Column(Modifier.weight(1f)) { Stepper(
            value = "Key of ${PopPlay.keyName(key, song.minor)}",
            detail = when { shift == 0 -> "the original key"; shift > 0 -> "up ${semis(shift)}"; else -> "down ${semis(-shift)}" },
            down = "Down a semitone", up = "Up a semitone",
            onDown = { onShift(shift - 1) }, onUp = { onShift(shift + 1) },
            downOn = shift > -6, upOn = shift < 6)
            if (shift != 0) TextButton(onClick = { onShift(0) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to the original key") }
        } }
        val tempoStepper = @Composable { Column(Modifier.weight(1f)) { Stepper(
            value = "$bpm bpm", detail = if (bpm == song.bpm) "the song's tempo" else "the song is ${song.bpm}",
            down = "Slower", up = "Faster",
            onDown = { onTempo(bpm - 4) }, onUp = { onTempo(bpm + 4) }, downOn = bpm > 40, upOn = bpm < 200) } }
        BoxWithConstraints {
            // Side by side when there is room, one over the other on a narrow screen.
            if (maxWidth >= 560.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { keyStepper(); tempoStepper() }
            else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Row { keyStepper() }; Row { tempoStepper() } }
        }
    }
}

private fun semis(n: Int) = if (n == 1) "a semitone" else "$n semitones"

@Composable
private fun Stepper(value: String, detail: String, down: String, up: String, onDown: () -> Unit, onUp: () -> Unit, downOn: Boolean, upOn: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundStep(R.drawable.ic_remove, down, downOn, onDown)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        RoundStep(R.drawable.ic_add, up, upOn, onUp)
    }
}

@Composable
private fun RoundStep(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, if (enabled) c.outline else c.outlineVariant, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = label, tint = if (enabled) c.onSurface else c.outlineVariant, modifier = Modifier.size(22.dp))
    }
}

/** The seven patterns as pills, the chosen one's feel under them in a line. */
@Composable
private fun PatternPicker(pattern: PopSong.Pattern, suggested: PopSong.Pattern, onPick: (PopSong.Pattern) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How to play it", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() })
        // The layouts (2.8, engine/nav/Simple.kt): Simple has Whole and Halves, Advanced adds Quarters and Broken, Full all seven.
        val options = com.bigpapituna.sharpears.engine.nav.Simple.patterns(PopSong.Pattern.entries, com.bigpapituna.sharpears.ui.LocalLayout.current.layout)
        PillRow(options.map { PopPlay.patternName(it) }, options.indexOf(pattern).coerceAtLeast(0), { onPick(options[it]) })
        Text(PopPlay.feel(pattern) + if (pattern == suggested) " The band suggests it for this song." else "",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

// --- the chart ---

/**
 * The chords by section as bars. A bar with two chords splits in two; the dots under a chord are its beats. While the
 * band plays, the bar sounding is lit and the chord in it brighter. A kid's chart is big colour blocks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chart(song: PopSong, bars: List<PopPlay.BarView>, flats: Boolean, beatNow: Double, kid: Boolean, reader: Boolean, onChord: (PopChord) -> Unit) {
    val c = MaterialTheme.colorScheme
    val bpb = song.beatsPerBar
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = when { maxWidth >= 640.dp -> 4; maxWidth >= 420.dp -> 3; else -> 2 }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            song.sections.forEachIndexed { si, sec ->
                val inSection = bars.filter { it.section == si }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sec.name, style = (if (kid) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.SemiBold),
                        color = if (kid) c.onSurface else c.primary, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
                    FlowRow(maxItemsInEachRow = perRow, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        inSection.forEach { bar ->
                            val barActive = beatNow >= 0 && (beatNow / bpb).toInt() == bar.index
                            if (kid) KidBar(bar, flats, reader, barActive, beatNow, Modifier.weight(1f), onChord)
                            else BarCell(bar, flats, barActive, beatNow, Modifier.weight(1f), onChord)
                        }
                        repeat((perRow - inSection.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarCell(bar: PopPlay.BarView, flats: Boolean, active: Boolean, beatNow: Double, modifier: Modifier, onChord: (PopChord) -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) c.primaryContainer else c.surfaceContainerHigh, tween(if (reducedMotion()) 0 else 120), label = "bar")
    Row(modifier.height(68.dp).clip(RoundedCornerShape(14.dp)).background(bg)) {
        bar.slots.forEachIndexed { i, slot ->
            val now = active && beatNow >= slot.startBeat && beatNow < slot.startBeat + slot.beats
            val name = PopPlay.name(slot.chord, flats)
            Column(Modifier.weight(slot.beats.toFloat()).fillMaxHeight().then(if (now) Modifier.background(c.primary.copy(alpha = 0.22f)) else Modifier)
                .clickable(role = Role.Button, onClickLabel = "See how to play $name") { onChord(slot.chord) }
                .semantics { contentDescription = "$name, ${slot.beats} ${if (slot.beats == 1) "beat" else "beats"}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = if (name.length > 5) 14.sp else 18.sp),
                    color = if (active) c.onPrimaryContainer else c.onSurface, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(slot.beats) { b ->
                        val lit = now && beatNow < slot.startBeat + b + 1 && beatNow >= slot.startBeat + b
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (lit) c.primary else c.onSurfaceVariant.copy(alpha = 0.45f)))
                    }
                }
            }
            if (i < bar.slots.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(c.outlineVariant))
        }
    }
}

/** A kid's bar: a colour block per chord (the root's note colour), its letter, or its animal for a child who cannot read. */
@Composable
private fun KidBar(bar: PopPlay.BarView, flats: Boolean, reader: Boolean, active: Boolean, beatNow: Double, modifier: Modifier, onChord: (PopChord) -> Unit) {
    Row(modifier.height(104.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bar.slots.forEach { slot ->
            val now = active && beatNow >= slot.startBeat && beatNow < slot.startBeat + slot.beats
            val grow by animateFloatAsState(if (now) 1.06f else 1f, tween(if (reducedMotion()) 0 else 120), label = "grow")
            val midi = 60 + slot.chord.root
            val name = PopPlay.name(PopChord(slot.chord.root, slot.chord.type), flats)
            val animal = NoteColours.animal(midi)
            Box(Modifier.weight(slot.beats.toFloat()).fillMaxHeight().scale(grow)
                .drawBehind { drawNoteColour(midi, Offset.Zero, size, CornerRadius(20.dp.toPx()), alpha = if (active && !now) 0.78f else 1f) }
                .then(if (now) Modifier.border(4.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "Hear $name") { onChord(slot.chord) }
                .semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
                if (!reader && animal != null) {
                    val ink = Color(NoteColours.inkFor(NoteColours.colour(midi)))
                    Canvas(Modifier.size(56.dp)) { drawAnimal(animal, center, size.minDimension, ink, Color(NoteColours.colour(midi))) }
                } else Text(name, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp),
                    color = inkOn(midi), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** For the kid page when nothing is known about the song yet (never shown now; kept so a missing id has a face). */
@Composable
fun PopSongMissing(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PupSays(Pose.HEAD_TILT, "I could not find that song.")
        TextButton(onClick = onBack) { Text("Back") }
    }
}
