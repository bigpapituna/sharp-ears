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
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * The parent gate for actions (docs/KIDS-PLAN.md section 2): wrap what only a grown-up should reach in [guard] (always
 * asks: profile management, the report token, grown-up mode) or [guardForKids] (asks only while a kid is playing:
 * Settings, Microphone setup). 2.14 (Priya's P3 and P4): a pass keeps the gate open for two minutes from the last thing
 * done behind it ([ParentGateRules.OPEN_MS]); "Lock now" ([LockNowRow]) closes it at once, and so do a switch of player
 * and more than 30 s in the background (MainActivity). The time is kept in [Profiles] so it survives the app closing.
 * [ParentGateHost] at the root draws the gate while one is asked.
 */
@Stable
class ParentGateController(private val profiles: Profiles?, private val clock: () -> Long = System::currentTimeMillis) {
    /** What runs once the gate is passed, while it is showing. */
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set
    /** When it was passed or last used (epoch ms, 0 closed): Compose state, so "Lock now" shows and goes with it. */
    private var passedAt by androidx.compose.runtime.mutableLongStateOf(profiles?.gatePassedAt ?: 0L)
    /** Wrong answers in a row and the wait they started (QA 2.8.3), kept in [Profiles] so a recreate does not end a wait. */
    var lockout by mutableStateOf(ParentGateRules.Lockout.decode(profiles?.gateLockout))
        private set

    val isOpen: Boolean get() = ParentGateRules.isOpen(passedAt, clock())

    /** When the open gate closes by itself (epoch ms), 0 when closed: the Lock now row hides itself then. */
    fun closesAt(): Long = ParentGateRules.closesAt(passedAt, clock())

    private fun store(at: Long) { passedAt = at; profiles?.gatePassedAt = at }

    /** The gate's clock (the sum pad counts a wait down on it). */
    fun now(): Long = clock()

    /** A wrong sum: the third in a row makes the gate wait (30 s, then 60 s, 120 s, doubling, at most ten minutes). */
    fun wrong() {
        val before = lockout
        lockout = before.wrongAt(clock())
        profiles?.gateLockout = lockout.encode()
        if (lockout.waits > before.waits) Log.d(TAG, "gate: ${ParentGateRules.TRIES} wrong answers, waits ${lockout.secondsLeft(clock())} s")
        else Log.d(TAG, "gate: wrong answer, new sum")
    }

    /**
     * Runs [action] now when the gate is open (its two minutes start again from now), otherwise after the gate is passed
     * (and not at all when it is left).
     */
    fun guard(action: () -> Unit) = guard(action, null)

    /** [guard], and [onCancel] when the gate is left instead (2.15: the microphone card asked for a kid falls back to touch). */
    fun guard(action: () -> Unit, onCancel: (() -> Unit)?) {
        if (isOpen) { store(ParentGateRules.used(passedAt, clock())); action() } else { pending = action; pendingCancel = onCancel; Log.d(TAG, "gate: asked") }
    }
    private var pendingCancel: (() -> Unit)? = null

    /** Like [guard] while a kid is playing; for a grown-up [action] runs at once. */
    fun guardForKids(action: () -> Unit) {
        if (profiles?.current?.isKid == true) guard(action) else action()
    }

    /** Called by the gate screen when the answer was right. */
    fun passed() {
        store(clock())
        if (lockout != ParentGateRules.Lockout()) { lockout = ParentGateRules.Lockout(); profiles?.gateLockout = null }
        Log.d(TAG, "gate: passed, open for ${ParentGateRules.OPEN_MS / 60_000} min from the last use")
        val a = pending
        pending = null; pendingCancel = null
        a?.invoke()
    }

    fun cancel() { val c = pendingCancel; pending = null; pendingCancel = null; Log.d(TAG, "gate: left"); c?.invoke() }

    /**
     * Bumps each time an open gate is closed (Lock now, a switch of player, the app away, a corner shut): grown-ups' screens
     * a kid could otherwise keep using (the corner, a kid's Settings) close when it changes.
     */
    var closings by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    /** Closes the gate at once (after a grown-up hands the phone back, say, or "Lock now"). */
    fun close() {
        if (passedAt == 0L && (profiles?.gatePassedAt ?: 0L) == 0L) return
        val wasOpen = isOpen
        store(0L)
        if (wasOpen) closings++
    }

    /** The app went to the background now (MainActivity.onStop): kept, so the next start knows how long it was away. */
    fun away() { profiles?.gateAwayAt = clock() }

    /** The app is back on screen (onStart, or a fresh start): more than 30 s away closes the gate (ParentGateRules.closesAfterAway). */
    fun back() {
        val awayAt = profiles?.gateAwayAt ?: 0L
        profiles?.gateAwayAt = 0L
        if (ParentGateRules.closesAfterAway(awayAt, clock()) && isOpen) { close(); Log.d(TAG, "gate: closed, the app was away ${(clock() - awayAt) / 1000} s") }
    }
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
 * "Grown-ups only": hold the pup for three seconds while a ring fills, then answer a question on a number pad (2.14: a
 * two-digit sum with a carry or a times table, "23 plus 48", "6 times 7"). A wrong answer brings a new one; the third in a
 * row makes [gate] wait ("Try again in 30 seconds.", the pad off meanwhile). [onPass] after the right answer; [onCancel] on
 * Back or "Not now".
 */
@Composable
fun ParentGate(onPass: () -> Unit, onCancel: () -> Unit, gate: ParentGateController = LocalParentGate.current) {
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
            if (!held) HoldThePup { held = true } else SumPad(onPass, gate)
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
    // 2.15, the Plain look (a big kid's profile): the ring round the app's mark, and no "me".
    val plain = com.bigpapituna.sharpears.ui.LocalPlain.current.on
    SpeechBubble(if (pressed) "Keep holding" else if (plain) "Hold for three seconds" else "Hold me for three seconds", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(232.dp).semantics(mergeDescendants = true) { contentDescription = if (plain) "Press and hold for three seconds." else "The pup. Press and hold for three seconds." }
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
        if (plain) com.bigpapituna.sharpears.ui.PlainMark(120.dp, Modifier.graphicsLayer { scaleX = squeeze; scaleY = squeeze })
        else Pup(Pose.SHH, size = 176.dp, ground = false, idle = false, modifier = Modifier.graphicsLayer { scaleX = squeeze; scaleY = squeeze })
    }
    Text("Kids, ask a grown-up to help.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
}

/**
 * Step two: "What is 23 plus 48?" typed on a number pad. A wrong answer gives a new question and a little shake; while [gate]
 * waits after three in a row, the pup says how long ("Try again in 30 seconds.", counting down) and the pad is off.
 */
@Composable
private fun SumPad(onPass: () -> Unit, gate: ParentGateController) {
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    var sum by remember { mutableStateOf(ParentGateRules.nextQuestion()) }
    var typed by remember { mutableStateOf("") }
    var missed by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val c = MaterialTheme.colorScheme
    // The wait's countdown: the clock read once a second while it runs; when it ends, a fresh sum.
    var now by remember { androidx.compose.runtime.mutableLongStateOf(gate.now()) }
    val lock = gate.lockout
    LaunchedEffect(lock) {
        now = gate.now()
        // The seconds shown change when the time left passes a whole second: sleep until then.
        while (lock.locked(now)) { kotlinx.coroutines.delay(((lock.until - now) % 1000).let { if (it == 0L) 1000L else it }); now = gate.now() }
        if (lock.until > 0) { missed = false; typed = "" }
    }
    val waiting = lock.locked(now)
    fun check() {
        if (waiting) return
        if (sum.isRight(typed)) { com.bigpapituna.sharpears.analytics.Events.gateOpened(); onPass(); return }
        gate.wrong(); com.bigpapituna.sharpears.analytics.Events.gateFailed()
        missed = true; typed = ""; sum = ParentGateRules.nextQuestion(previous = sum)
        if (!still) scope.launch { shake.snapTo(1f); shake.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 900f)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pup(Pose.THINKING, size = 88.dp, idle = false)
        SpeechBubble(when {
            waiting -> ParentGateRules.waitLine(lock.secondsLeft(now))
            missed -> "Not quite. Try this one: what is ${sum.spoken}?"
            else -> "What is ${sum.spoken}?"
        }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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
                        .graphicsLayer { alpha = if (waiting) 0.4f else 1f }
                        .clickable(enabled = !waiting && (!main || typed.isNotEmpty()), role = Role.Button, onClickLabel = label) {
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
