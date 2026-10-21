package com.bigpapituna.sharpears.ui.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.practice.PitchSmoother
import com.bigpapituna.sharpears.engine.practice.PitchTracker
import com.bigpapituna.sharpears.engine.practice.TunerReading
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.onScreen
import com.bigpapituna.sharpears.ui.reducedMotion
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cents either side of a note that count as in tune. */
private const val IN_TUNE_CENTS = 5.0

/**
 * The tuner: listens through the mic's raw tap, finds the pitch about 20 times a second off the main thread,
 * and swings a glowing needle across a large arc from -50 to +50 cents, with the note inside the arc. The arc
 * turns green within 5 cents. The mic is started here if nothing else is using it and stopped again when the
 * screen goes (or the app leaves the foreground).
 */
@Composable
fun TunerScreen(mic: MicInput, sampler: Sampler, modifier: Modifier = Modifier) {
    var allowed by remember { mutableStateOf(mic.hasPermission()) }
    var refused by remember { mutableStateOf(false) }       // refused twice, Android stops asking: say where to allow it
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; refused = !it }
    val visible = onScreen()
    LaunchedEffect(visible) { if (visible && !allowed) allowed = mic.hasPermission() }
    var micFailed by remember { mutableStateOf(false) }
    val tracker = remember { PitchTracker(MicInput.RATE) }
    var reading by remember { mutableStateOf<TunerReading?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(allowed, visible) {
        var startedHere = false
        var tapped = false
        if (allowed && visible) {
            if (!mic.listening) { startedHere = mic.start(autoRecord = false); micFailed = !startedHere }
            if (mic.listening) {
                tracker.clear()
                mic.tap = { buf, n -> tracker.feed(buf, n) }
                tapped = true
            }
        }
        onDispose {
            if (tapped) mic.tap = null
            if (startedHere) mic.stop()
        }
    }
    LaunchedEffect(allowed, visible, tracker) {
        if (!allowed || !visible) { reading = null; return@LaunchedEffect }
        val smoother = PitchSmoother()
        withContext(Dispatchers.Default) {
            while (isActive) {
                reading = smoother.update(tracker.analyze(), System.currentTimeMillis())
                delay(50)                                              // about 20 readings a second
            }
        }
    }

    var tone by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { tone?.cancel(); sampler.noteOff(69) } }
    val c = MaterialTheme.colorScheme

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (!allowed) {
            Spacer(Modifier.height(12.dp))
            PupLine(Pose.LISTENING, "Let me listen and I will tell you if a note is sharp or flat. I only listen, nothing is recorded.")
            Gauge(null, Modifier.fillMaxWidth().height(230.dp), quiet = true)
            ChunkyButton(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Allow microphone")
            }
            if (refused) com.bigpapituna.sharpears.ui.MicRefusedNote()
        } else {
            val r = reading
            val inTune = r != null && abs(r.cents) <= IN_TUNE_CENTS
            Gauge(r, Modifier.fillMaxWidth().height(222.dp))
            Text(when {
                micFailed -> "The microphone could not start. Another app may be using it."
                r == null -> "Play a single note and let it ring"
                inTune -> "In tune"
                r.cents < 0 -> "A little flat, tune up"
                else -> "A little sharp, tune down"
            }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                color = if (inTune) c.tertiary else c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (r == null) "No note heard" else "${com.bigpapituna.sharpears.engine.spokenKey(r.result.midi)}, ${com.bigpapituna.sharpears.engine.plural(r.cents.roundToInt(), "cent")}"
                })
        }
        Spacer(Modifier.height(4.dp))
        ReferenceTone(playing = tone?.isActive == true) {
            if (tone?.isActive == true) { tone?.cancel(); sampler.noteOff(69); tone = null }
            else tone = scope.launch { try { sampler.noteOn(69, 100); delay(3000) } finally { sampler.noteOff(69); tone = null } }
        }
    }
}

/** The reference A as a round button that glows and rings while it sounds. */
@Composable
private fun ReferenceTone(playing: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val ring = if (playing && !still) rememberInfiniteTransition(label = "tone").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "ring") else null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(76.dp)) {
                val t = ring?.value ?: return@Canvas
                drawCircle(c.primary.copy(alpha = 0.45f * (1 - t)), size.minDimension / 2 * (0.8f + 0.2f * t), style = Stroke(2.dp.toPx()))
            }
            Box(Modifier.pressScale(0.9f).size(60.dp).clip(CircleShape).background(if (playing) c.primary else c.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (playing) "Stop the reference A" else "Play the reference A, 440 hertz" },
                contentAlignment = Alignment.Center) {
                Text("A", style = MaterialTheme.typography.headlineSmall, color = if (playing) c.onPrimary else c.onSurface)
            }
        }
        Column {
            Text("Reference A", style = MaterialTheme.typography.titleMedium)
            Text("440 Hz, the piano's own A4", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}

/**
 * The gauge: a wide arc from -50 cents (left) to +50 (right) with ticks, a needle with a soft glow, the note
 * name large inside the arc and the cents under it. Within 5 cents the arc and needle turn green. [quiet]: no
 * reading yet (the needle rests at the top, dimmed).
 */
@Composable
private fun Gauge(r: TunerReading?, modifier: Modifier = Modifier, quiet: Boolean = false) {
    val target = (r?.cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat()
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "needle")
    val inTune = r != null && abs(r.cents) <= IN_TUNE_CENTS
    val c = MaterialTheme.colorScheme
    val good = c.tertiary
    val tint by animateColorAsState(if (inTune) good else c.outlineVariant, label = "arc")
    val needle by animateColorAsState(when { r == null -> c.outline; inTune -> good; else -> c.primary }, label = "needleColor")
    val ink = c.onSurfaceVariant
    val text = rememberTextMeasurer()
    val noteStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold)
    val octaveStyle = MaterialTheme.typography.titleMedium.merge(Numeric)
    val centsStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val labelStyle = TextStyle(color = ink, fontSize = 11.sp)
    val onSurface = c.onSurface
    // A soft breathing glow on the needle while it is in tune.
    val still = reducedMotion()
    val glow = remember { Animatable(0f) }
    LaunchedEffect(inTune) { if (inTune && !still) glow.animateTo(1f, tween(300)) else glow.snapTo(0f) }
    Canvas(modifier.semantics { contentDescription = if (r == null) "Tuner gauge, no note" else "Needle at ${com.bigpapituna.sharpears.engine.plural(r.cents.roundToInt(), "cent")}" }) {
        val stroke = 16.dp.toPx()
        val top = 26.dp.toPx()
        val radius = min(size.width * 0.44f, size.height - top - 30.dp.toPx())
        val center = Offset(size.width / 2, top + radius)
        val sweep = 72f                                                   // degrees either side of straight up
        fun deg(cents: Float) = 270f + cents / 50f * sweep
        fun point(cents: Float, rr: Float): Offset {
            val a = Math.toRadians(deg(cents).toDouble())
            return Offset(center.x + rr * cos(a).toFloat(), center.y + rr * sin(a).toFloat())
        }
        val topLeft = Offset(center.x - radius, center.y - radius)
        val box = Size(radius * 2, radius * 2)
        drawArc(tint.copy(alpha = if (inTune) 0.35f else 1f), deg(-50f), 2 * sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(good, deg(-IN_TUNE_CENTS.toFloat()), deg(IN_TUNE_CENTS.toFloat()) - deg(-IN_TUNE_CENTS.toFloat()), false, topLeft, box, style = Stroke(stroke))
        for (cents in -50..50 step 5) {
            val major = cents % 25 == 0
            val inner = radius - stroke * (if (major) 1.9f else 1.45f)
            drawLine(ink.copy(alpha = if (major) 0.8f else 0.35f), point(cents.toFloat(), radius - stroke * 0.95f), point(cents.toFloat(), inner),
                strokeWidth = if (major) 2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round)
        }
        for ((cents, label) in listOf(-50 to "-50", 0 to "0", 50 to "+50")) {
            val m = text.measure(label, labelStyle)
            val p = point(cents.toFloat(), radius + stroke * 1.3f)
            drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
        }
        // The needle: a wide soft stroke under a fine one, so it glows.
        val tip = point(shown, radius + stroke * 0.2f)
        val base = point(shown, radius * 0.72f)
        val a = if (quiet) 0.35f else 1f
        drawLine(Brush.linearGradient(listOf(needle.copy(alpha = 0f), needle.copy(alpha = 0.35f * a + 0.2f * glow.value)), base, tip), base, tip,
            strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
        drawLine(needle.copy(alpha = a), base, tip, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(needle.copy(alpha = a), 6.dp.toPx(), tip)
        drawCircle(needle.copy(alpha = 0.25f * a), 12.dp.toPx(), tip)

        // The note inside the arc, the cents under it.
        val letter = r?.result?.noteLetter ?: ""
        val nm = text.measure(letter, noteStyle.copy(color = if (inTune) good else if (r == null) ink.copy(alpha = 0.5f) else onSurface))
        val noteY = center.y - radius * 0.4f
        val octave = r?.result?.name?.takeLast(1)
        val om = octave?.let { text.measure(it, octaveStyle.copy(color = ink)) }
        val totalW = nm.size.width + (om?.size?.width ?: 0)
        val nx = size.width / 2 - totalW / 2f
        if (letter.isNotEmpty()) drawText(nm, topLeft = Offset(nx, noteY - nm.size.height / 2f))
        om?.let { drawText(it, topLeft = Offset(nx + nm.size.width + 2.dp.toPx(), noteY + nm.size.height * 0.12f)) }
        val centsText = when {
            r == null -> if (quiet) "Waiting for the microphone" else "Listening"
            else -> (if (r.cents >= 0.5) "+" else "") + com.bigpapituna.sharpears.engine.plural(r.cents.roundToInt(), "cent") + "   " + String.format(Locale.US, "%.1f Hz", r.result.freq)
        }
        val cm = text.measure(centsText, (if (r == null) labelStyle.copy(fontSize = 13.sp) else centsStyle).copy(color = ink))
        val cy = if (letter.isEmpty()) noteY - cm.size.height / 2f else noteY + nm.size.height / 2f + 2.dp.toPx()
        drawText(cm, topLeft = Offset(size.width / 2 - cm.size.width / 2f, cy))
    }
}

