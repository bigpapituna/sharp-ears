package com.bigpapituna.sharpears.ui.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.reducedMotion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    var muteChance by remember { mutableStateOf(store.int("metro.mute.chance", 1).coerceIn(0, MUTE_CHANCES.lastIndex)) }

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

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // The tempo: drag the number sideways, or use the buttons and the slider.
        val step = with(LocalDensity.current) { 10.dp.toPx() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(onClick = { setBpm(bpm - 1) }, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = "Slower")
            }
            Column(Modifier.weight(1f).pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    val n = (acc / step).toInt()
                    if (n != 0) { acc -= n * step; setBpm(bpm + n) }
                }
            }.semantics { contentDescription = "Tempo $bpm beats per minute, ${tempoName(bpm)}" },
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$bpm", style = Numeric.merge(MaterialTheme.typography.displayLarge).copy(fontSize = 88.sp, lineHeight = 96.sp), textAlign = TextAlign.Center)
                Text("${tempoName(bpm)}, beats per minute", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (running && trainerOn && liveBpm != bpm) "Now at $liveBpm" else "Drag the number to change it",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = { setBpm(bpm + 1) }, modifier = Modifier.size(52.dp)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Faster")
            }
        }
        Slider(value = bpm.toFloat(), onValueChange = { setBpm(it.toInt()) }, valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            modifier = Modifier.semantics { contentDescription = "Tempo slider" })

        BeatDots(sig.beats, beatNow, barMuted && running)
        Text(if (running) "Bar ${barNow + 1}${if (barMuted) ", silent" else ""}" else sig.grouping,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChunkyButton(onClick = { running = !running }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (running) "Stop" else "Start")
            }
            OutlinedButton(onClick = { tap.tap(System.currentTimeMillis())?.let { setBpm(it) } }, modifier = Modifier.height(52.dp)) { Text("Tap tempo") }
        }

        Text("Time signature", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeSig.entries.forEach { t ->
                FilterChip(sig == t, { sig = t; store.putEnum("metro.sig", t) }, label = { Text(t.label) })
            }
        }
        Text("Clicks per beat", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Subdivision.entries.forEach { s ->
                FilterChip(sub == s, { sub = s; store.putEnum("metro.sub", s) }, label = { Text(s.label) })
            }
        }

        SwitchRow("Speed trainer", if (trainerOn) "Gets faster by $trainerStep every $trainerBars bars, up to $trainerMax" else "Speeds up on its own while you play", trainerOn) {
            trainerOn = it; store.putBool("metro.trainer", it)
        }
        if (trainerOn) {
            Stepper("Faster by", "$trainerStep bpm", { trainerStep = (trainerStep - 1).coerceAtLeast(1); store.putInt("metro.trainer.step", trainerStep) },
                { trainerStep = (trainerStep + 1).coerceAtMost(30); store.putInt("metro.trainer.step", trainerStep) })
            Stepper("Every", "$trainerBars bars", { trainerBars = (trainerBars - 1).coerceAtLeast(1); store.putInt("metro.trainer.bars", trainerBars) },
                { trainerBars = (trainerBars + 1).coerceAtMost(32); store.putInt("metro.trainer.bars", trainerBars) })
            Stepper("Up to", "$trainerMax bpm", { trainerMax = (trainerMax - 5).coerceAtLeast(MIN_BPM); store.putInt("metro.trainer.max", trainerMax) },
                { trainerMax = (trainerMax + 5).coerceAtMost(MAX_BPM); store.putInt("metro.trainer.max", trainerMax) })
        }

        SwitchRow("Mute random bars", if (muteOn) "Some bars go silent. Keep the beat, then listen for the click to come back." else "Checks that you keep time without the click", muteOn) {
            muteOn = it; store.putBool("metro.mute", it)
        }
        if (muteOn) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MUTE_CHANCES.forEachIndexed { i, c ->
                FilterChip(muteChance == i, { muteChance = i; store.putInt("metro.mute.chance", i) }, label = { Text("${(c * 100).toInt()} percent of bars") })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** One dot per beat; the current one is filled in the accent and swells on the tick. A silent bar shows grey. */
@Composable
private fun BeatDots(beats: Int, current: Int, silent: Boolean) {
    val still = reducedMotion()
    val on = if (silent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    FlowRow(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = if (current >= 0) "Beat ${current + 1} of $beats" else "$beats beats a bar" },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.Center) {
        repeat(beats) { i ->
            val active = i == current
            val scale by animateFloatAsState(if (active) 1.4f else 1f, if (still) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.4f, stiffness = 900f), label = "dot")
            val size = if (i == 0) 26.dp else 20.dp
            Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.background(if (active) on else off, CircleShape))
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
        Text(value, Modifier.padding(horizontal = 12.dp).widthIn(min = 76.dp), style = Numeric.merge(MaterialTheme.typography.titleMedium), textAlign = TextAlign.Center)
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) { Icon(painterResource(R.drawable.ic_add), contentDescription = "More $label") }
    }
}
