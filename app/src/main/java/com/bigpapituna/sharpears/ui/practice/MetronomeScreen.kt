package com.bigpapituna.sharpears.ui.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.clickSamples
import com.bigpapituna.sharpears.engine.practice.MAX_BPM
import com.bigpapituna.sharpears.engine.practice.MIN_BPM
import com.bigpapituna.sharpears.engine.practice.MetronomeConfig
import com.bigpapituna.sharpears.engine.practice.RandomMute
import com.bigpapituna.sharpears.engine.practice.Subdivision
import com.bigpapituna.sharpears.engine.practice.TapTempo
import com.bigpapituna.sharpears.engine.practice.TickLevel
import com.bigpapituna.sharpears.engine.practice.TickSchedule
import com.bigpapituna.sharpears.engine.practice.TimeSig
import com.bigpapituna.sharpears.engine.practice.Trainer
import com.bigpapituna.sharpears.engine.practice.clampBpm
import com.bigpapituna.sharpears.engine.practice.tempoName
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.motion.rememberHaptics
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.reducedMotion
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The metronome's sounds through SoundPool, with a volume per [TickLevel]. It uses the same synthesized clicks as
 * audio/Clicks.kt (accent and beat samples from engine/Metronome.kt) but needs more than two loudnesses, so it
 * keeps its own small pool.
 */
class MetronomeVoice(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val accent: Int
    private val beat: Int

    init {
        fun load(name: String, isAccent: Boolean) =
            pool.load(File(context.cacheDir, name).also { Wav.write(it, clickSamples(isAccent), 48_000) }.path, 1)
        accent = load("practice-click-accent.wav", true)
        beat = load("practice-click-beat.wav", false)
    }

    fun play(level: TickLevel) {
        when (level) {
            TickLevel.STRONG -> pool.play(accent, 1f, 1f, 2, 0, 1f)
            TickLevel.MEDIUM -> pool.play(accent, 0.55f, 0.55f, 2, 0, 1f)
            TickLevel.WEAK -> pool.play(beat, 0.9f, 0.9f, 2, 0, 1f)
            TickLevel.SUB -> pool.play(beat, 0.35f, 0.35f, 2, 0, 0.9f)
        }
    }

    fun release() = pool.release()
}

private val MUTE_CHANCES = listOf(0.15, 0.25, 0.4)

/**
 * The metronome as an instrument: a huge tempo number you drag, inside a ring of beat dots that fill through the
 * bar with a pulse on each click; Start, and a round Tap button that ripples. Time signature and clicks per beat
 * are pill rows; the speed trainer and muted bars sit behind More.
 */
@Composable
fun MetronomeScreen(store: PracticeStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val voice = remember { MetronomeVoice(context) }
    DisposableEffect(voice) { onDispose { voice.release() } }

    var bpm by remember { mutableIntStateOf(clampBpm(store.int("metro.bpm", 80))) }
    var sig by remember { mutableStateOf(store.enum("metro.sig", TimeSig.FOUR_FOUR)) }
    var sub by remember { mutableStateOf(store.enum("metro.sub", Subdivision.NONE)) }
    var trainerOn by remember { mutableStateOf(store.bool("metro.trainer", false)) }
    var trainerStep by remember { mutableIntStateOf(store.int("metro.trainer.step", 5)) }
    var trainerBars by remember { mutableIntStateOf(store.int("metro.trainer.bars", 4)) }
    var trainerMax by remember { mutableIntStateOf(store.int("metro.trainer.max", 160)) }
    var muteOn by remember { mutableStateOf(store.bool("metro.mute", false)) }
    var muteChance by remember { mutableIntStateOf(store.int("metro.mute.chance", 1).coerceIn(0, MUTE_CHANCES.lastIndex)) }
    var more by remember { mutableStateOf(trainerOn || muteOn) }

    var running by remember { mutableStateOf(false) }
    var beatNow by remember { mutableIntStateOf(-1) }
    var barNow by remember { mutableIntStateOf(0) }
    var barMuted by remember { mutableStateOf(false) }
    var liveBpm by remember { mutableIntStateOf(bpm) }
    val tap = remember { TapTempo() }

    fun setBpm(v: Int) { bpm = clampBpm(v); store.putInt("metro.bpm", bpm) }

    // The metronome stops when the app leaves the screen.
    val visible = onScreen()
    LaunchedEffect(visible) { if (!visible) running = false }

    // The clock. Restarts when the pattern changes; a tempo change is picked up on the next beat.
    LaunchedEffect(running, sig, sub, trainerOn, trainerStep, trainerBars, trainerMax, muteOn, muteChance) {
        if (!running) { beatNow = -1; barNow = 0; barMuted = false; return@LaunchedEffect }
        withContext(Dispatchers.Default) {
            val config = MetronomeConfig(
                bpm = bpm, timeSig = sig, subdivision = sub,
                trainer = if (trainerOn) Trainer(trainerStep, trainerBars, trainerMax) else null,
                mute = if (muteOn) RandomMute(MUTE_CHANCES[muteChance]) else null,
                seed = System.nanoTime(),
            )
            val schedule = TickSchedule(config)
            // Every tick is aimed at a wall-clock instant, so a late wake-up never shifts the ones after it.
            val start = System.nanoTime() + 150_000_000L
            var userBpm = bpm
            while (isActive) {
                if (bpm != userBpm) { userBpm = bpm; schedule.setBpm(userBpm) }
                val tick = schedule.next()
                val target = start + (tick.timeMs * 1_000_000).toLong()
                val waitMs = (target - System.nanoTime()) / 1_000_000
                if (waitMs > 2) delay(waitMs - 1)
                while (System.nanoTime() < target) { /* the last millisecond is spent spinning */ }
                val lateMs = (System.nanoTime() - target) / 1_000_000
                if (!tick.muted && lateMs < 120) voice.play(tick.level)
                if (tick.onBeat) { beatNow = tick.beat; barNow = tick.bar; barMuted = tick.muted; liveBpm = tick.bpm }
            }
        }
    }

    val c = MaterialTheme.colorScheme
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {

        BeatDial(sig, beatNow, barNow, running, barMuted && running, Modifier.size(272.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DragNumber(bpm, ::setBpm, MIN_BPM..MAX_BPM, "bpm", big = true)
                Text(if (running && trainerOn && liveBpm != bpm) "Now at $liveBpm" else tempoName(bpm),
                    style = MaterialTheme.typography.titleMedium, color = c.primary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = { setBpm(bpm - 1) }, modifier = Modifier.size(44.dp).pressScale(0.9f)) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = "Slower")
            }
            Text(if (running) "Bar ${barNow + 1}${if (barMuted) ", silent" else ""}" else sig.grouping,
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            FilledTonalIconButton(onClick = { setBpm(bpm + 1) }, modifier = Modifier.size(44.dp).pressScale(0.9f)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Faster")
            }
        }

        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkyButton(onClick = { running = !running }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (running) "Stop" else "Start")
            }
            TapButton { tap.tap(System.currentTimeMillis())?.let { setBpm(it) } }
        }

        Label("Time signature")
        PillRow(TimeSig.entries.map { it.label }, sig.ordinal, { sig = TimeSig.entries[it]; store.putEnum("metro.sig", sig) })
        Label("Clicks per beat")
        PillRow(Subdivision.entries.map { it.label }, sub.ordinal, { sub = Subdivision.entries[it]; store.putEnum("metro.sub", sub) })

        // More: the speed trainer and muted bars.
        val turn by animateFloatAsState(if (more) 180f else 0f, label = "more")
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(CircleShape).clickable { more = !more }.padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics { contentDescription = if (more) "Fewer options" else "More options: speed trainer and muted bars" },
            verticalAlignment = Alignment.CenterVertically) {
            Text("More", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(listOfNotNull("Trainer".takeIf { trainerOn }, "Muted bars".takeIf { muteOn }).joinToString(", ").ifEmpty { "Speed trainer, muted bars" },
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Canvas(Modifier.padding(start = 8.dp).size(12.dp).graphicsLayer { rotationZ = turn }) {
                val w = size.width; val h = size.height
                drawLine(c.onSurfaceVariant, Offset(w * 0.15f, h * 0.35f), Offset(w * 0.5f, h * 0.7f), 1.8.dp.toPx())
                drawLine(c.onSurfaceVariant, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.85f, h * 0.35f), 1.8.dp.toPx())
            }
        }
        AnimatedVisibility(more, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow("Speed trainer", if (trainerOn) "Gets faster by $trainerStep every ${if (trainerBars == 1) "bar" else "$trainerBars bars"}, up to $trainerMax" else "Speeds up on its own while you play", trainerOn) {
                    trainerOn = it; store.putBool("metro.trainer", it)
                }
                if (trainerOn) {
                    Stepper("Faster by", "$trainerStep bpm", { trainerStep = (trainerStep - 1).coerceAtLeast(1); store.putInt("metro.trainer.step", trainerStep) },
                        { trainerStep = (trainerStep + 1).coerceAtMost(30); store.putInt("metro.trainer.step", trainerStep) })
                    Stepper("Every", com.bigpapituna.sharpears.engine.plural(trainerBars, "bar"), { trainerBars = (trainerBars - 1).coerceAtLeast(1); store.putInt("metro.trainer.bars", trainerBars) },
                        { trainerBars = (trainerBars + 1).coerceAtMost(32); store.putInt("metro.trainer.bars", trainerBars) })
                    Stepper("Up to", "$trainerMax bpm", { trainerMax = (trainerMax - 5).coerceAtLeast(MIN_BPM); store.putInt("metro.trainer.max", trainerMax) },
                        { trainerMax = (trainerMax + 5).coerceAtMost(MAX_BPM); store.putInt("metro.trainer.max", trainerMax) })
                }
                SwitchRow("Mute random bars", if (muteOn) "Some bars go silent. Keep the beat, then listen for the click to come back." else "Checks that you keep time without the click", muteOn) {
                    muteOn = it; store.putBool("metro.mute", it)
                }
                if (muteOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MUTE_CHANCES.forEachIndexed { i, ch ->
                        Pill("${(ch * 100).toInt()} percent", muteChance == i, { muteChance = i; store.putInt("metro.mute.chance", i) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp))
}

/**
 * The beat ring: one dot per beat around the tempo, the first at the top. Through a bar the dots fill one by one
 * in the accent; the beat just struck swells and a ring pulses out from the centre. A silent bar shows grey.
 */
@Composable
private fun BeatDial(sig: TimeSig, beat: Int, bar: Int, running: Boolean, silent: Boolean, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val still = reducedMotion()
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(beat, bar, running) {
        if (!running || beat < 0 || still) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(520, easing = LinearOutSlowInEasing))
    }
    val c = MaterialTheme.colorScheme
    val on = if (silent) c.onSurfaceVariant else c.primary
    val track = c.outlineVariant
    val dotOff = c.surfaceContainerHighest
    Box(modifier.semantics { contentDescription = if (running && beat >= 0) "Beat ${beat + 1} of ${sig.beats}" else "${sig.beats} beats a bar" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = min(size.width, size.height) / 2 - 16.dp.toPx()
            val o = Offset(size.width / 2, size.height / 2)
            val p = pulse.value
            // The glow and the ring that pulses out on each click.
            if (running) drawCircle(Brush.radialGradient(listOf(on.copy(alpha = 0.10f + 0.12f * p), Color.Transparent), o, r * 1.05f), r * 1.05f, o)
            if (p > 0f) drawCircle(on.copy(alpha = 0.45f * p), r * (0.72f + 0.3f * (1 - p)), o, style = Stroke(2.dp.toPx() + 3.dp.toPx() * p))
            drawCircle(track, r, o, style = Stroke(1.5.dp.toPx()))
            for (i in 0 until sig.beats) {
                val a = Math.toRadians(-90.0 + 360.0 * i / sig.beats)
                val at = Offset(o.x + r * cos(a).toFloat(), o.y + r * sin(a).toFloat())
                val strong = sig.accents.getOrNull(i) == TickLevel.STRONG
                val medium = sig.accents.getOrNull(i) == TickLevel.MEDIUM
                val base = (if (strong) 13.dp else if (medium) 11.dp else 9.dp).toPx()
                val filled = running && beat >= 0 && i <= beat
                val now = running && i == beat
                val size = base * (if (now) 1f + 0.45f * p else 1f)
                if (now) drawCircle(on.copy(alpha = 0.25f * p), size * 1.9f, at)
                drawCircle(if (filled) on else dotOff, size, at)
                if (!filled) drawCircle(track, size, at, style = Stroke(1.5.dp.toPx()))
            }
        }
        center()
    }
}

/** Tap tempo as a big round button: each tap sends a ripple out and a light tick through the phone. */
@Composable
private fun TapButton(onTap: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val ripples = remember { mutableStateListOf<Animatable<Float, *>>() }
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            for (a in ripples) {
                val t = a.value
                drawCircle(c.primary.copy(alpha = 0.5f * (1 - t)), size.minDimension / 2 * (1f + 0.6f * t), style = Stroke(2.dp.toPx()))
            }
        }
        Box(Modifier.pressScale(0.9f).size(64.dp).clip(CircleShape).background(c.surfaceContainerHigh)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                haptics.tick()
                onTap()
                if (!still) {
                    val a = Animatable(0f)
                    ripples += a
                    scope.launch { a.animateTo(1f, tween(600, easing = FastOutSlowInEasing)); ripples.remove(a) }
                }
            }.semantics { contentDescription = "Tap tempo: tap along to set the speed" },
            contentAlignment = Alignment.Center) {
            Text("Tap", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onSurface)
        }
    }
}

@Composable
internal fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(40.dp)) { Icon(painterResource(R.drawable.ic_remove), contentDescription = "Less $label") }
        Text(value, Modifier.padding(horizontal = 12.dp).widthIn(min = 76.dp), style = MaterialTheme.typography.titleMedium.merge(Numeric), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) { Icon(painterResource(R.drawable.ic_add), contentDescription = "More $label") }
    }
}

