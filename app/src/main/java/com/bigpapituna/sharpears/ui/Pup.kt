package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.pup.PupController
import com.bigpapituna.sharpears.ui.pup.mood

/*
 * The pup's words and the app's chunky buttons. The pup himself (his poses, his rig and his controller) lives in ui/pup
 * since 2.10.
 */

private val TAIL = 9.dp

/** What the pup says: a gold speech bubble with its tail toward him (on the left). Pops in when the line changes. */
@Composable
fun SpeechBubble(line: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    // 2.15, the Plain look: no pup to point at, so no gold bubble or tail: the words on a quiet surface.
    if (LocalPlain.current.on) { PlainWords(line, modifier, style); return }
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

/** The pup with what he says beside him, for the moments he speaks up (greetings, feedback, results). [mood] as [Pup]'s. */
@Composable
fun PupSays(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 72.dp,
            onTap: (() -> Unit)? = null, sleepyAfterMs: Long? = null, style: TextStyle? = null,
            controller: PupController? = null, bounce: BounceSize = BounceSize.SMALL,
            /** False where the pose is the message (a "not quite", a pointing pup): no idle acts, no sleep. */
            idle: Boolean = true, mood: PupMood = pose.mood) {
    // 2.15, the Plain look: the words alone (a lesson's instruction, a hint, an empty list's line), no pup and no bubble tail.
    if (LocalPlain.current.on) { PlainWords(line, modifier, style ?: MaterialTheme.typography.titleMedium); return }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pup(pose, mood, size = size, onTap = onTap, sleepyAfterMs = sleepyAfterMs, controller = controller, bounce = bounce, idle = idle)
        SpeechBubble(line, Modifier.weight(1f, fill = false), style ?: MaterialTheme.typography.titleMedium)
    }
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
    // 2.15: none in the Plain look either (PlainLook.party).
    if (reducedMotion() || LocalPlain.current.on) return
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
