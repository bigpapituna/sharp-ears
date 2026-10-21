package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Section

val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0)

fun formatTime(ms: Double): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }
private fun speedLabel(s: Double) = "${s}x".replace(".0x", "x")

/** Everything the bar shows; the player owns the state. */
data class BarState(
    val playing: Boolean,
    val ready: Boolean,
    val positionMs: Double,
    val durationMs: Long,
    val speed: Double,
    val mode: Mode,
    val practice: Practice,
    val loopStart: Double?,
    val loopEnd: Double?,
    val sections: List<Section>,
    val midiDevice: String? = null,
    val micOn: Boolean = false,
    /** Read only while drawing the level ring, so level updates do not recompose the player. */
    val micLevelDb: () -> Float = { -120f },
    val title: String = "",
    val subtitle: String = "",
    /** null: the song has no tutorial video. */
    val videoOn: Boolean? = null,
    /** Short live status, e.g. the Perform streak. */
    val status: String = "",
    val metronome: Boolean = false,
    /** The ramp's current percentage while the speed ramp is on, else null. */
    val ramp: Int? = null,
    /** The Auto speed's percentage while Auto is on, else null. */
    val auto: Int? = null,
    /** null: the song has no chord labels. */
    val chords: Boolean? = null,
    /** null: not a progression (no backing to offer). */
    val backing: Boolean? = null,
    /** Semitones the song is moved (-6..6). */
    val transpose: Int = 0,
    /** Duet (2.1): null hides the pill (one profile, or not a library song), "" offers it, a label says it is on. */
    val duet: String? = null,
)

class BarActions(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSpeed: (Double) -> Unit,
    val onMode: (Mode) -> Unit,
    val onPractice: (Practice) -> Unit,
    val onSeek: (Double) -> Unit,
    /** One button walks the loop: set its start, set its end, clear it. */
    val onLoop: () -> Unit,
    val onMic: () -> Unit = {},
    val onVideo: () -> Unit = {},
    val onMetronome: () -> Unit = {},
    val onChords: () -> Unit = {},
    /** Turns the speed ramp on (it starts at 50%). Picking a fixed speed turns it off. */
    val onRamp: () -> Unit = {},
    /** Turns Auto on: the tempo managed last time, nudged up on good days. */
    val onAuto: () -> Unit = {},
    val onBacking: () -> Unit = {},
    /** "That wasn't right": flags the last 30 s for review (null hides the button). */
    val onFlag: (() -> Unit)? = null,
    /** A new transpose, in semitones. */
    val onTranspose: (Int) -> Unit = {},
    /** Duet: pick a partner (or stop the duet when one runs). */
    val onDuet: () -> Unit = {},
)

/**
 * Two rows. Top: back, title, hands, mode, play. Bottom: the timeline with loop marks, then the loop,
 * speed, microphone and video controls. Icons carry spoken labels for TalkBack.
 */
@Composable
fun TransportBar(s: BarState, a: BarActions, modifier: Modifier = Modifier) {
    // Two fixed-height rows across a phone held sideways: at the phone's largest font sizes (up to 2x) the hands, the modes
    // and the play button ran off the right edge. The bar's text stops growing at 1.15x, where the song slider still has
    // room; the rest of the player follows the phone.
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(MAX_FONT_SCALE))) { Bar(s, a, modifier) }
}

private const val MAX_FONT_SCALE = 1.15f

@Composable
private fun Bar(s: BarState, a: BarActions, modifier: Modifier) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = a.onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.subtitle.isNotEmpty()) Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Segments(listOf("Both" to Practice.BOTH, "L" to Practice.LEFT, "R" to Practice.RIGHT), s.practice, a.onPractice,
                spoken = mapOf("L" to "Left hand only", "R" to "Right hand only", "Both" to "Both hands"))
            Segments(listOf("Listen" to Mode.LISTEN, "Wait" to Mode.WAIT, "Perform" to Mode.PERFORM), s.mode, a.onMode)
            FilledIconButton(onClick = a.onPlayPause, enabled = s.ready, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = if (!s.ready) "Loading sounds" else if (s.playing) "Pause" else "Play")
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatTime(s.positionMs), style = Numeric.merge(MaterialTheme.typography.labelLarge), modifier = Modifier.padding(start = 12.dp))
            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                val accent = MaterialTheme.colorScheme.primary
                val marks = MaterialTheme.colorScheme.outline
                Canvas(Modifier.matchParentSize().padding(horizontal = 10.dp)) {
                    if (s.durationMs <= 0) return@Canvas
                    fun x(ms: Double) = (size.width * ms / s.durationMs).toFloat()
                    s.sections.forEach { drawLine(marks, Offset(x(it.startMs.toDouble()), 8f), Offset(x(it.startMs.toDouble()), 16f), 2f) }
                    val a0 = s.loopStart; val b0 = s.loopEnd
                    if (a0 != null && b0 != null) drawLine(accent.copy(alpha = 0.35f), Offset(x(a0), size.height / 2), Offset(x(b0), size.height / 2), 14f)
                    a0?.let { drawLine(accent, Offset(x(it), 10f), Offset(x(it), size.height - 10f), 3f) }
                    b0?.let { drawLine(accent, Offset(x(it), 10f), Offset(x(it), size.height - 10f), 3f) }
                }
                Slider(
                    value = (s.positionMs / s.durationMs.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                    onValueChange = { a.onSeek(it.toDouble() * s.durationMs) },
                    colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Song position" },
                )
            }
            Text(formatTime(s.durationMs.toDouble()), style = Numeric.merge(MaterialTheme.typography.labelLarge), color = dim)
            Spacer(Modifier.width(4.dp))
            LoopButton(s, a.onLoop)
            // One button walks the speeds: 0.25x, 0.5x, 0.75x, 1x, Ramp (50% rising to 100% with clean passes), then Auto.
            val changed = s.speed != 1.0 || s.ramp != null || s.auto != null
            TextButton(onClick = {
                when {
                    s.auto != null -> a.onSpeed(SPEEDS.first())
                    s.ramp != null -> a.onAuto()
                    s.speed == 1.0 -> a.onRamp()
                    else -> a.onSpeed(SPEEDS[(SPEEDS.indexOf(s.speed) + 1) % SPEEDS.size])
                }
            }, modifier = Modifier.semantics {
                contentDescription = (s.auto?.let { "Auto speed, $it percent" } ?: s.ramp?.let { "Speed ramp at $it percent" } ?: "Speed ${speedLabel(s.speed)}") + ", tap to change"
            }) {
                Icon(painterResource(R.drawable.ic_speed), contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (changed) MaterialTheme.colorScheme.primary else dim)
                Spacer(Modifier.width(6.dp))
                Text(s.auto?.let { "Auto $it%" } ?: s.ramp?.let { "Ramp $it%" } ?: speedLabel(s.speed), style = Numeric.merge(MaterialTheme.typography.labelLarge),
                    color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            s.duet?.let { Toggle(it.ifEmpty { "Duet" }, it.isNotEmpty(), a.onDuet) }
            s.chords?.let { Toggle("Chords", it, a.onChords) }
            Toggle("Metronome", s.metronome, a.onMetronome)
            s.backing?.let { Toggle("Backing", it, a.onBacking) }
            MicToggle(s, a.onMic)
            s.videoOn?.let { on ->
                IconToggleButton(on, { a.onVideo() }, modifier = Modifier.semantics { stateDescription = if (on) "shown" else "hidden" }) {
                    Icon(painterResource(R.drawable.ic_smart_display), contentDescription = "Tutorial video",
                        tint = if (on) MaterialTheme.colorScheme.primary else dim)
                }
            }
            if (s.status.isNotEmpty() || s.midiDevice != null) Text(
                s.status.ifEmpty { if (s.micOn) "MIDI judges" else "MIDI" },
                style = Numeric.merge(MaterialTheme.typography.labelLarge), maxLines = 1,
                color = if (s.status.isNotEmpty()) MaterialTheme.colorScheme.primary else dim,
                modifier = Modifier.padding(end = 8.dp),
            )
            a.onFlag?.let { flag ->
                IconButton(onClick = flag) {
                    Icon(painterResource(R.drawable.ic_flag), contentDescription = "That wasn't right: flag the last 30 seconds for review", tint = dim)
                }
            }
            MoreMenu(s, a)
        }
    }
}

/** The loop walks through three states on one button; the label always says what the next tap does or what is set. */
@Composable
private fun LoopButton(s: BarState, onLoop: () -> Unit) {
    val start = s.loopStart; val end = s.loopEnd
    val active = start != null && end != null
    val (label, spoken) = when {
        start == null -> "Loop" to "Loop: tap to mark the start here"
        end == null -> "End here" to "Loop starts at ${formatTime(start)}, tap to mark the end here"
        else -> "${formatTime(start)}-${formatTime(end)}" to "Looping ${formatTime(start)} to ${formatTime(end)}, tap to clear"
    }
    TextButton(onClick = onLoop, modifier = Modifier.semantics { contentDescription = spoken }) {
        Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (start != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(label, style = if (active) Numeric.merge(MaterialTheme.typography.labelLarge) else MaterialTheme.typography.labelLarge,
            color = if (start != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Microphone on/off with a ring that brightens with the room level (read while drawing only). */
@Composable
private fun MicToggle(s: BarState, onMic: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    IconToggleButton(s.micOn, { onMic() }, modifier = Modifier.semantics { stateDescription = if (s.micOn) "listening" else "off" }) {
        if (s.micOn) Canvas(Modifier.size(36.dp)) {
            drawCircle(accent.copy(alpha = ((s.micLevelDb() + 70f) / 60f).coerceIn(0.12f, 1f)), style = Stroke(width = 3f))
        }
        Icon(painterResource(if (s.micOn) R.drawable.ic_mic else R.drawable.ic_mic_off), contentDescription = "Microphone",
            tint = if (s.micOn) accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> Segments(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit, spoken: Map<String, String> = emptyMap()) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = value == selected, onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size), icon = {},
                modifier = Modifier.semantics { spoken[label]?.let { contentDescription = it } },
            ) { Text(label, maxLines = 1) }
        }
    }
}


/** "Up 2", "Down 1", "Off". */
fun transposeLabel(n: Int) = when { n > 0 -> "Up $n"; n < 0 -> "Down ${-n}"; else -> "Off" }

/** The overflow at the end of the second row: Transpose (-6..+6 semitones) for now. */
@Composable
private fun MoreMenu(s: BarState, a: BarActions) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options",
                tint = if (s.transpose != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Transpose", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = { a.onTranspose(s.transpose - 1) }, enabled = s.transpose > -6) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = "Down a semitone")
                }
                Text(transposeLabel(s.transpose), style = Numeric.merge(MaterialTheme.typography.labelLarge),
                    modifier = Modifier.width(56.dp).semantics { contentDescription = "Transpose ${if (s.transpose == 0) "off" else transposeLabel(s.transpose) + " semitones"}" },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (s.transpose != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { a.onTranspose(s.transpose + 1) }, enabled = s.transpose < 6) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Up a semitone")
                }
            }
            if (s.transpose != 0) androidx.compose.material3.DropdownMenuItem(text = { Text("Back to the written key") },
                onClick = { a.onTranspose(0); open = false })
        }
    }
}

/** A text pill that switches something on the stage (metronome, backing, chord names). */
@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(on, onClick, label = { Text(label, maxLines = 1) },
        modifier = Modifier.semantics { stateDescription = if (on) "on" else "off" })
}
