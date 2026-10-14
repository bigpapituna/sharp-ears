package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R

/** The Golden Coach Pup's poses (art/mascot/sheet.jpg, cut by scripts/cut_mascot.py), each with its own small motion. */
enum class Pose(val res: Int) {
    WELCOME(R.drawable.pup_welcome), PRACTICING(R.drawable.pup_practicing), GREAT_JOB(R.drawable.pup_great_job),
    MISTAKE(R.drawable.pup_mistake), LEARNING(R.drawable.pup_learning), LEVEL_UP(R.drawable.pup_level_up),
    STREAK(R.drawable.pup_streak), LISTENING(R.drawable.pup_listening), GET_READY(R.drawable.pup_get_ready),
    RESTING(R.drawable.pup_resting),
}

/**
 * The pup in [pose], standing in a soft gold glow with his shadow on the ground. A new pose hops in; then he keeps
 * moving a little the way the pose suggests (a wave, a jump, a head tilt, slow breathing), and his shadow shrinks
 * as he leaves the ground. Between poses he is alive: he blinks every few seconds, looks around every 6 to 10 s,
 * wiggles when tapped ([onTap]) and drifts sleepy when left alone ([sleepyAfterMs]). All of it is transforms
 * of the one pose image. Still when the phone's animations are off. Decorative: the text beside him says what
 * he means, so screen readers skip him.
 */
@Composable
fun Pup(
    pose: Pose, modifier: Modifier = Modifier, size: Dp = 88.dp,
    /** Tapping him: a happy wiggle, then this. Without it he takes no touches (and stays out of the way). */
    onTap: (() -> Unit)? = null,
    /** After this long with no new pose and no tap he gets sleepy: slower sway, a slow nod. The home screen uses 30 s. */
    sleepyAfterMs: Long? = null,
) {
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(1f) }
    LaunchedEffect(pose) {
        if (still) return@LaunchedEffect
        hop.snapTo(0.6f)
        hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    // One 0..1 cycle, advanced by a frame clock so its speed can ease down when he is sleepy; each pose turns it
    // into its own motion below. Read only in the layer and draw blocks, so he never recomposes per frame.
    val period = when (pose) { Pose.RESTING -> 3200; Pose.GREAT_JOB, Pose.LEVEL_UP -> 900; Pose.STREAK -> 420; Pose.PRACTICING -> 500; else -> 1600 }
    val cycle = remember { mutableFloatStateOf(0f) }
    val blink = remember { Animatable(0f) }       // 0 open .. 1 eyes shut (a quick squash of the whole pup)
    val look = remember { Animatable(0f) }        // -1 left .. 1 right
    val wiggle = remember { Animatable(1f) }      // 0..1 through a wiggle; 1 = none
    val sleepy = remember { Animatable(0f) }      // 0 awake .. 1 drowsy
    var taps by remember { mutableIntStateOf(0) }
    val periodNow by rememberUpdatedState(period)
    if (!still) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) withFrameNanos { now ->
                if (last != 0L) {
                    val speed = 1f - 0.6f * sleepy.value
                    cycle.floatValue = (cycle.floatValue + (now - last) / 1e6f / periodNow * speed) % 1f
                }
                last = now
            }
        }
        LaunchedEffect(Unit) {
            val r = java.util.Random()
            while (true) {
                delay(2600L + r.nextInt(3400) + (sleepy.value * 2500).toLong())
                val shut = 70 + (sleepy.value * 260).toInt()                    // drowsy blinks are slow
                blink.animateTo(1f, tween(shut)); blink.animateTo(0f, tween(110 + shut / 2))
                if (r.nextInt(5) == 0 && sleepy.value < 0.5f) { blink.animateTo(1f, tween(60)); blink.animateTo(0f, tween(100)) }
            }
        }
        LaunchedEffect(Unit) {
            val r = java.util.Random()
            while (true) {
                delay(6000L + r.nextInt(4000))
                if (sleepy.value > 0.5f) continue
                look.animateTo((if (r.nextBoolean()) 1f else -1f) * (0.6f + 0.4f * r.nextFloat()), spring(dampingRatio = 0.6f, stiffness = 120f))
                delay(700L + r.nextInt(900))
                look.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 90f))
            }
        }
        if (sleepyAfterMs != null) LaunchedEffect(pose, taps, sleepyAfterMs) {
            sleepy.animateTo(0f, tween(400))
            delay(sleepyAfterMs)
            sleepy.animateTo(1f, tween(2500))
        }
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val m = if (still) 0f else 1f
    val liftOf = when (pose) { Pose.GREAT_JOB, Pose.LEVEL_UP -> 0.12f; Pose.STREAK -> 0.04f; Pose.PRACTICING -> 0.02f; else -> 0f }
    fun bounce() = kotlin.math.abs(kotlin.math.sin(cycle.floatValue * Math.PI)).toFloat()   // 0..1..0, a hop per cycle
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures {
            taps++
            if (!still) scope.launch { wiggle.snapTo(0f); wiggle.animateTo(1f, tween(700, easing = LinearEasing)) }
            onTap()
        }
    }
    Box(modifier.size(size).then(tap).drawBehind {
        // The glow he stands in, and his shadow (smaller while he is in the air).
        val lift = liftOf * bounce() * m
        val glow = Offset(this.size.width / 2, this.size.height * 0.56f)
        val r = this.size.width * 0.56f
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = if (dark) 0.22f else 0.30f), Color.Transparent), glow, r), r, glow)
        val w = this.size.width * 0.58f * (1f - 2.2f * lift) * (1f + 0.03f * look.value * look.value)
        drawOval(if (dark) Color.Black.copy(alpha = 0.32f) else Color(0xFF5A4030).copy(alpha = 0.16f),
            topLeft = Offset((this.size.width - w) / 2 + this.size.width * 0.03f * look.value, this.size.height * 0.93f), size = Size(w, this.size.height * 0.07f))
    }) {
        Image(painterResource(pose.res), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)                 // moves from the feet
            val c = cycle.floatValue
            val wave = kotlin.math.sin(c * 2 * Math.PI).toFloat()      // -1..1, smooth
            val bounce = bounce()
            val lift = liftOf * bounce * m
            val s = hop.value
            scaleX = s; scaleY = s
            translationY = -size.toPx() * lift
            when (pose) {
                Pose.WELCOME -> rotationZ = 4f * wave * m
                Pose.GREAT_JOB, Pose.LEVEL_UP -> scaleY = s * (1f - 0.04f * (1 - bounce) * m)   // squash on landing
                Pose.STREAK -> rotationZ = -3f * m
                Pose.MISTAKE -> rotationZ = (if (wave > 0) 5f * wave else 0f) * m   // a thoughtful tilt, then back
                Pose.LEARNING -> rotationZ = 1.5f * wave * m
                Pose.LISTENING -> rotationZ = 3f * wave * m                         // swaying to the music
                Pose.GET_READY -> { val p = 1f + 0.05f * bounce * m; scaleX = s * p; scaleY = s * p }
                Pose.RESTING -> scaleY = s * (1f + 0.025f * wave * m)               // slow breathing
                Pose.PRACTICING -> {}
            }
            // Idle life on top of the pose: a blink (a quick squash reads as one from a distance), a look around,
            // a sleepy nod, and a wiggle when tapped.
            val b = blink.value
            scaleY *= 1f - 0.06f * b
            scaleX *= 1f + 0.025f * b
            val lk = look.value
            rotationZ += 4f * lk
            translationX = size.toPx() * 0.03f * lk
            val z = sleepy.value
            rotationZ += z * 3f * kotlin.math.sin(c * 2 * Math.PI + 1.3).toFloat()
            translationY += size.toPx() * 0.015f * z
            val wg = wiggle.value
            if (wg < 1f) {
                val fade = 1f - wg
                rotationZ += 9f * kotlin.math.sin(wg * 6 * Math.PI).toFloat() * fade
                val pop = 1f + 0.08f * kotlin.math.sin(wg * Math.PI).toFloat()
                scaleX *= pop; scaleY *= pop
            }
        })
    }
}

private val TAIL = 9.dp

/** What the pup says: a gold speech bubble with its tail toward him (on the left). Pops in when the line changes. */
@Composable
fun SpeechBubble(line: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    val density = LocalDensity.current
    val shape = remember(density) {
        val tail = with(density) { TAIL.toPx() }
        val r = with(density) { 18.dp.toPx() }
        val at = with(density) { 26.dp.toPx() }
        GenericShape { s, _ ->
            addRoundRect(RoundRect(tail, 0f, s.width, s.height, CornerRadius(r)))
            val y = minOf(at, s.height / 2)
            moveTo(tail + 1, y - tail); lineTo(0f, y + tail * 0.2f); lineTo(tail + 1, y + tail * 0.6f); close()
        }
    }
    val still = reducedMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(line) { if (!still) { pop.snapTo(0.9f); pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) } }
    Text(line, style = style, color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0f, 0.3f) }
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .padding(start = TAIL + 14.dp, end = 16.dp, top = 12.dp, bottom = 13.dp))
}

/** The pup with what he says beside him, for the moments he speaks up (greetings, feedback, results). */
@Composable
fun PupSays(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 72.dp,
            onTap: (() -> Unit)? = null, sleepyAfterMs: Long? = null, style: TextStyle? = null) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pup(pose, size = size, onTap = onTap, sleepyAfterMs = sleepyAfterMs)
        SpeechBubble(line, Modifier.weight(1f, fill = false), style ?: MaterialTheme.typography.titleMedium)
    }

/**
 * The main action on a screen: a pill that sits on a darker lip and sinks into it when pressed, then springs back,
 * the same bounce the pup has. Secondary actions stay plain outlined or text buttons.
 */
@Composable
fun ChunkyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lip = 4.dp
    val sink by animateDpAsState(if (pressed && enabled) lip else 0.dp, spring(dampingRatio = 0.45f, stiffness = 1100f), label = "sink")
    val c = MaterialTheme.colorScheme
    val face = if (enabled) c.primary else c.surfaceContainerHighest
    val edge = if (enabled) lerp(c.primary, Color.Black, 0.3f) else c.outlineVariant
    // Min constraints pass through, so a fillMaxWidth button fills with its face, not just its lip.
    Box(modifier.heightIn(min = 52.dp).clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().padding(top = lip).background(edge, CircleShape))
        CompositionLocalProvider(LocalContentColor provides if (enabled) c.onPrimary else c.onSurfaceVariant,
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)) {
            Row(Modifier.offset { IntOffset(0, sink.roundToPx()) }.padding(bottom = lip).background(face, CircleShape)
                .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

/** A burst of confetti in the accent, the pup's gold and the hand colours, once per [key]. Nothing with animations off. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier) {
    if (reducedMotion()) return
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    // Each bit: sideways speed, upward speed, spin, size; colours in turn.
    val bits = remember(key) {
        val r = java.util.Random(key.hashCode().toLong())
        val colors = listOf(Accent, PupGold, Good, LeftHandColor)
        List(80) { floatArrayOf(r.nextFloat() * 1.3f - 0.65f, -(0.5f + r.nextFloat() * 0.9f), r.nextFloat() * 720f - 360f, 0.6f + r.nextFloat() * 0.8f) to colors[it % 4] }
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val sec = p * 2.4f
        val origin = Offset(size.width / 2, size.height * 0.3f)
        bits.forEach { (b, color) ->
            val x = origin.x + b[0] * size.width * 0.9f * sec
            val y = origin.y + (b[1] * sec + 0.75f * sec * sec) * size.height * 0.45f
            val w = 9.dp.toPx() * b[3]; val h = 5.dp.toPx() * b[3]
            rotate(b[2] * sec, Offset(x, y)) {
                drawRect(color.copy(alpha = if (p > 0.75f) (1 - p) * 4 else 1f), Offset(x - w / 2, y - h / 2), Size(w, h))
            }
        }
    }
}
