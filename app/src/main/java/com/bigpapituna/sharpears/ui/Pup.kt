package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.remember
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
 * as he leaves the ground. Still when the phone's animations are off. Decorative: the text beside him says what
 * he means, so screen readers skip him.
 */
@Composable
fun Pup(pose: Pose, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val still = reducedMotion()
    val hop = remember { Animatable(1f) }
    LaunchedEffect(pose) {
        if (still) return@LaunchedEffect
        hop.snapTo(0.6f)
        hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val t = rememberInfiniteTransition(label = "pup")
    // One 0..1 cycle; each pose turns it into its own motion below.
    val period = when (pose) { Pose.RESTING -> 3200; Pose.GREAT_JOB, Pose.LEVEL_UP -> 900; Pose.STREAK -> 420; Pose.PRACTICING -> 500; else -> 1600 }
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "cycle")
    val wave = kotlin.math.sin(c * 2 * Math.PI).toFloat()                  // -1..1, smooth
    val bounce = kotlin.math.abs(kotlin.math.sin(c * Math.PI)).toFloat()   // 0..1..0, a hop per cycle
    val m = if (still) 0f else 1f
    val lift = when (pose) { Pose.GREAT_JOB, Pose.LEVEL_UP -> 0.12f; Pose.STREAK -> 0.04f; Pose.PRACTICING -> 0.02f; else -> 0f } * bounce * m
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.size(size).drawBehind {
        // The glow he stands in, and his shadow (smaller while he is in the air).
        val glow = Offset(this.size.width / 2, this.size.height * 0.56f)
        val r = this.size.width * 0.56f
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = if (dark) 0.22f else 0.30f), Color.Transparent), glow, r), r, glow)
        val w = this.size.width * 0.58f * (1f - 2.2f * lift)
        drawOval(if (dark) Color.Black.copy(alpha = 0.32f) else Color(0xFF5A4030).copy(alpha = 0.16f),
            topLeft = Offset((this.size.width - w) / 2, this.size.height * 0.93f), size = Size(w, this.size.height * 0.07f))
    }) {
        Image(painterResource(pose.res), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)                 // moves from the feet
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
fun PupSays(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 72.dp) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pup(pose, size = size)
        SpeechBubble(line, Modifier.weight(1f, fill = false))
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
