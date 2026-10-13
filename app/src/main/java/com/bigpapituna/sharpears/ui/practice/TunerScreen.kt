package com.bigpapituna.sharpears.ui.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.practice.PitchSmoother
import com.bigpapituna.sharpears.engine.practice.PitchTracker
import com.bigpapituna.sharpears.engine.practice.TunerReading
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.onScreen
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
 * and swings a needle across -50..+50 cents. The mic is started here if nothing else is using it and stopped
 * again when the screen goes (or the app leaves the foreground).
 */
@Composable
fun TunerScreen(mic: MicInput, sampler: Sampler, modifier: Modifier = Modifier) {
    var allowed by remember { mutableStateOf(mic.hasPermission()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val visible = onScreen()
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

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!allowed) {
            Text("The tuner needs the microphone", style = MaterialTheme.typography.titleLarge)
            Text("It listens to one note at a time and shows how far it is from the nearest pitch. Nothing is recorded.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
        } else {
            val r = reading
            CentsGauge(r?.cents, Modifier.fillMaxWidth().height(210.dp))
            val inTune = r != null && abs(r.cents) <= IN_TUNE_CENTS
            val noteColor = if (inTune) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(r?.result?.noteLetter ?: "-", style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 92.sp), color = noteColor)
                if (r != null) Text(r.result.name.takeLast(1), style = Numeric.merge(MaterialTheme.typography.headlineSmall), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
            }
            Text(when {
                micFailed -> "The microphone could not start. Another app may be using it."
                r == null -> "Play a single note and let it ring"
                inTune -> "In tune"
                r.cents < 0 -> "${abs(r.cents).roundToInt()} cents flat, tune up"
                else -> "${r.cents.roundToInt()} cents sharp, tune down"
            }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = if (r == null) "No note heard" else "${r.result.name}, ${r.cents.roundToInt()} cents" },
                color = if (inTune) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (r == null) " " else String.format(Locale.US, "%.1f Hz", r.result.freq), style = Numeric.merge(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text("Reference tone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        OutlinedButton(onClick = {
            if (tone?.isActive == true) { tone?.cancel(); sampler.noteOff(69); tone = null }
            else tone = scope.launch { try { sampler.noteOn(69, 100); delay(3000) } finally { sampler.noteOff(69) } }
        }) { Text("Play A4, 440 Hz") }
        Text("This is the piano sample of A4. If the microphone picks it up, the needle should sit in the middle.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The arc and needle: -50 cents at the left end, 0 at the top, +50 at the right; the green zone is +-5 cents. */
@Composable
private fun CentsGauge(cents: Double?, modifier: Modifier = Modifier) {
    val target = (cents ?: 0.0).coerceIn(-50.0, 50.0).toFloat()
    val shown by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "needle")
    val inTune = cents != null && abs(cents) <= IN_TUNE_CENTS
    val track = MaterialTheme.colorScheme.outlineVariant
    val zone = MaterialTheme.colorScheme.tertiary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val needle = if (cents == null) MaterialTheme.colorScheme.outline else if (inTune) zone else MaterialTheme.colorScheme.primary
    val text = rememberTextMeasurer()
    val labelStyle = TextStyle(color = ink, fontSize = 12.sp)
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val radius = min(size.width * 0.44f, size.height - 40.dp.toPx())
        val center = Offset(size.width / 2, radius + stroke)
        val sweep = 70f                                                  // degrees either side of straight up
        fun deg(c: Float) = 270f + c / 50f * sweep
        fun point(c: Float, r: Float): Offset {
            val a = Math.toRadians(deg(c).toDouble())
            return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        }
        val topLeft = Offset(center.x - radius, center.y - radius)
        val box = Size(radius * 2, radius * 2)
        drawArc(track, deg(-50f), 2 * sweep, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(zone, deg(-IN_TUNE_CENTS.toFloat()), deg(IN_TUNE_CENTS.toFloat()) - deg(-IN_TUNE_CENTS.toFloat()), false, topLeft, box, style = Stroke(stroke))
        for (c in -50..50 step 10) {
            val major = c == 0 || c == -50 || c == 50
            drawLine(ink.copy(alpha = if (major) 0.9f else 0.5f), point(c.toFloat(), radius - stroke * 0.9f), point(c.toFloat(), radius - stroke * (if (major) 1.9f else 1.5f)),
                strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx())
        }
        for ((c, label) in listOf(-50 to "-50", 0 to "0", 50 to "+50")) {
            val m = text.measure(label, labelStyle)
            val p = point(c.toFloat(), radius - stroke * 3.1f)
            drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
        }
        val tip = point(shown, radius - stroke * 0.5f)
        drawLine(needle, center, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(needle, 9.dp.toPx(), center)
    }
}
