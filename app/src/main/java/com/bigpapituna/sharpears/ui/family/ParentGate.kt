package com.bigpapituna.sharpears.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.ParentGateRules
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.launch

/**
 * The parent gate for actions (docs/KIDS-PLAN.md section 2): wrap what only a grown-up should reach in [guard] (always
 * asks: profile management, the report token, grown-up mode) or [guardForKids] (asks only while a kid is playing:
 * Settings, Microphone setup). A pass keeps the gate open for ten minutes ([ParentGateRules.OPEN_MS]); the time is
 * kept in [Profiles] so it survives the app closing. [ParentGateHost] at the root draws the gate while one is asked.
 */
@Stable
class ParentGateController(private val profiles: Profiles?, private val clock: () -> Long = System::currentTimeMillis) {
    /** What runs once the gate is passed, while it is showing. */
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set
    private var passedAt = 0L                          // in memory when there is no store (previews)

    val isOpen: Boolean get() = ParentGateRules.isOpen(profiles?.gatePassedAt ?: passedAt, clock())

    /** Runs [action] now when the gate is open, otherwise after the gate is passed (and not at all when it is left). */
    fun guard(action: () -> Unit) {
        if (isOpen) action() else { pending = action; Log.d(TAG, "gate: asked") }
    }

    /** Like [guard] while a kid is playing; for a grown-up [action] runs at once. */
    fun guardForKids(action: () -> Unit) {
        if (profiles?.current?.isKid == true) guard(action) else action()
    }

    /** Called by the gate screen when the sum was right. */
    fun passed() {
        val now = clock()
        if (profiles != null) profiles.gatePassedAt = now else passedAt = now
        Log.d(TAG, "gate: passed, open for ${ParentGateRules.OPEN_MS / 60_000} min")
        val a = pending
        pending = null
        a?.invoke()
    }

    fun cancel() { pending = null; Log.d(TAG, "gate: left") }

    /** Closes the gate at once (after a grown-up hands the phone back, say). */
    fun close() { if (profiles != null) profiles.gatePassedAt = 0L else passedAt = 0L }
}

/** The app's gate, provided by MainActivity next to [LocalProfile]. Without a provider the gate keeps its pass in memory. */
val LocalParentGate = staticCompositionLocalOf { ParentGateController(null) }

/** Draws the gate over everything while [controller] has an action waiting. Put it last in the root, above the screens. */
@Composable
fun ParentGateHost(controller: ParentGateController = LocalParentGate.current) {
    if (controller.pending != null) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ParentGate(onPass = controller::passed, onCancel = controller::cancel)
    }
}

/**
 * A screen that only shows [content] to a grown-up: the gate first unless it is open. Leaving the gate calls [onCancel].
 * For whole pushed screens; for buttons, use [ParentGateController.guard].
 */
@Composable
fun Gated(onCancel: () -> Unit, gate: ParentGateController = LocalParentGate.current, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(gate.isOpen) }
    if (open) content() else ParentGate(onPass = { gate.passed(); open = true }, onCancel = onCancel)
}

/**
 * "Grown-ups only": hold the pup for three seconds while a ring fills, then answer a sum of two numbers from 2 to 9 on
 * a number pad. A wrong answer brings a new sum. [onPass] after the right answer; [onCancel] on Back or "Not now".
 */
@Composable
fun ParentGate(onPass: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var held by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Not now, go back") }
        }
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Grown-ups only", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (!held) HoldThePup { held = true } else SumPad(onPass)
        }
    }
}

/** Step one: press and hold the pup; the ring fills in three seconds and drains back if he is let go early. */
@Composable
internal fun HoldThePup(onHeld: () -> Unit) {
    val still = reducedMotion()
    val ring = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            val left = ((1f - ring.value) * ParentGateRules.HOLD_MS).toInt()
            // Real seconds whatever the system's animation scale: at 0 ("remove animations") the tween ended at once and
            // a short tap passed the hold. The ring is the hold's clock, not decoration.
            kotlinx.coroutines.withContext(object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 1f }) {
                ring.animateTo(1f, tween(left, easing = LinearEasing))
            }
            Log.d(TAG, "gate: held for three seconds")
            onHeld()
        } else ring.animateTo(0f, tween(if (still) 0 else 350))
    }
    SpeechBubble(if (pressed) "Keep holding" else "Hold me for three seconds", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(232.dp).semantics(mergeDescendants = true) { contentDescription = "The pup. Press and hold for three seconds." }
        .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }) },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            val inset = w / 2
            drawCircle(PupGold.copy(alpha = 0.22f), radius = size.minDimension / 2 - w)
            drawArc(c.outlineVariant, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
            drawArc(c.primary, -90f, 360f * ring.value, false, Offset(inset, inset), Size(size.width - w, size.height - w),
                style = Stroke(w, cap = StrokeCap.Round))
        }
        val squeeze = if (still) 1f else 1f - 0.06f * ring.value
        Image(painterResource(R.drawable.pup_shh), contentDescription = null,
            modifier = Modifier.size(176.dp).graphicsLayer { scaleX = squeeze; scaleY = squeeze })
    }
    Text("Kids, ask a grown-up to help.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
}

/** Step two: "What is 7 plus 5?" typed on a number pad. A wrong answer gives a new sum and a little shake. */
@Composable
private fun SumPad(onPass: () -> Unit) {
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    var sum by remember { mutableStateOf(ParentGateRules.nextSum()) }
    var typed by remember { mutableStateOf("") }
    var missed by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val c = MaterialTheme.colorScheme
    fun check() {
        if (sum.isRight(typed)) { onPass(); return }
        Log.d(TAG, "gate: wrong answer, new sum")
        missed = true; typed = ""; sum = ParentGateRules.nextSum(previous = sum)
        if (!still) scope.launch { shake.snapTo(1f); shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 900f)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.pup_thinking), contentDescription = null, modifier = Modifier.size(88.dp))
        SpeechBubble(if (missed) "Not quite. Try this one: what is ${sum.spoken}?" else "What is ${sum.spoken}?",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Box(Modifier.graphicsLayer { translationX = shake.value * 18.dp.toPx() }.widthIn(min = 140.dp).height(72.dp)
        .border(2.dp, if (typed.isEmpty()) c.outlineVariant else c.primary, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp)
        .semantics { contentDescription = if (typed.isEmpty()) "Answer, empty" else "Answer, $typed" },
        contentAlignment = Alignment.Center) {
        Text(typed.ifEmpty { " " }, style = Numeric.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold), color = c.onSurface)
    }
    val keys = listOf("123", "456", "789", "<0=")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { k ->
                    val label = when (k) { '<' -> "Delete"; '=' -> "Done"; else -> "$k" }
                    val main = k == '='
                    Box(Modifier.size(76.dp).clip(CircleShape)
                        .background(if (main) (if (typed.isEmpty()) c.surfaceContainerHighest else c.primary) else c.surfaceContainerHigh)
                        .clickable(enabled = !main || typed.isNotEmpty(), role = Role.Button, onClickLabel = label) {
                            if (main) check() else typed = ParentGateRules.type(typed, k)
                        }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center) {
                        when (k) {
                            '<' -> Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = c.onSurface)
                            '=' -> Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                                color = if (typed.isEmpty()) c.onSurfaceVariant else c.onPrimary)
                            else -> Text("$k", style = Numeric.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium), color = c.onSurface)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
