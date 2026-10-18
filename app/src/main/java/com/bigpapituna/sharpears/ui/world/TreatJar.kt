package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.engine.world.TreatJar
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.isKid
import com.bigpapituna.sharpears.ui.rememberPupController
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val Biscuit = Color(0xFFE1A85E)
private val BiscuitDeep = Color(0xFFC88A44)
private val Lid = Color(0xFFE38E5A)

/** One bone-shaped biscuit centred on [c], [len] long, turned [deg]. */
fun DrawScope.drawBiscuit(c: Offset, len: Float, deg: Float, alpha: Float = 1f) {
    rotate(deg, c) {
        val r = len * 0.2f
        val bone = Path().apply {
            addRoundRect(RoundRect(c.x - len * 0.36f, c.y - r * 0.75f, c.x + len * 0.36f, c.y + r * 0.75f, CornerRadius(r * 0.5f)))
            for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + sx * len * 0.38f, c.y + sy * r * 0.8f), r))
        }
        drawPath(bone, WorldInk, alpha = alpha, style = Stroke(len * 0.07f, join = StrokeJoin.Round))
        drawPath(bone, Biscuit, alpha = alpha)
        drawLine(BiscuitDeep, Offset(c.x - len * 0.2f, c.y + r * 0.25f), Offset(c.x + len * 0.2f, c.y + r * 0.25f), len * 0.05f, alpha = alpha)
    }
}

/**
 * The treat jar drawn in code: a glass jar with the accent lid and a paw on the glass, holding one bone biscuit per
 * [TreatJar.PER_BISCUIT] notes (stacked from the bottom, a little jumbled). [shown] biscuits can be fewer than the
 * notes give while they pour out during a tip.
 */
@Composable
fun TreatJarView(notes: Int, modifier: Modifier = Modifier, shown: Int = TreatJar.biscuits(notes)) {
    Canvas(modifier.semantics { contentDescription = if (TreatJar.full(notes)) "The treat jar, full" else "The treat jar, ${(TreatJar.fraction(notes) * 100).toInt()} percent full" }) {
        val w = size.width; val h = size.height
        val line = w * 0.035f
        val body = Path().apply {
            addRoundRect(RoundRect(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.97f, CornerRadius(w * 0.18f)))
            addRoundRect(RoundRect(w * 0.22f, h * 0.12f, w * 0.78f, h * 0.28f, CornerRadius(w * 0.06f)))
        }
        drawPath(body, Color(0xFFDCEFF8).copy(alpha = 0.55f))
        // Biscuits, four to a row from the bottom, each nudged by a fixed jitter.
        val n = shown.coerceIn(0, TreatJar.BISCUITS)
        for (i in 0 until n) {
            val row = i / 4; val col = i % 4
            val jx = ((i * 37) % 7 - 3) * w * 0.008f
            val x = w * (0.24f + col * 0.175f) + (if (row % 2 == 1) w * 0.06f else 0f) + jx
            val y = h * (0.88f - row * 0.13f)
            drawBiscuit(Offset(x.coerceAtMost(w * 0.8f), y), w * 0.22f, ((i * 53) % 50 - 25).toFloat())
        }
        // The glass over them: outline, a highlight down the left side, a paw on the front.
        drawPath(body, WorldInk, style = Stroke(line, join = StrokeJoin.Round))
        drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(w * 0.15f, h * 0.32f), Size(w * 0.06f, h * 0.48f), CornerRadius(w * 0.03f))
        val paw = Offset(w * 0.66f, h * 0.36f)
        drawOval(Color.White.copy(alpha = 0.5f), Offset(paw.x - w * 0.05f, paw.y - w * 0.02f), Size(w * 0.1f, w * 0.08f))
        listOf(-0.055f to -0.04f, -0.02f to -0.07f, 0.02f to -0.07f, 0.055f to -0.04f).forEach { (dx, dy) ->
            drawCircle(Color.White.copy(alpha = 0.5f), w * 0.018f, Offset(paw.x + dx * w, paw.y + dy * w))
        }
        // The lid.
        val lid = Path().apply { addRoundRect(RoundRect(w * 0.16f, h * 0.03f, w * 0.84f, h * 0.14f, CornerRadius(w * 0.05f))) }
        drawPath(lid, Lid)
        drawPath(lid, WorldInk, style = Stroke(line, join = StrokeJoin.Round))
        for (k in 1..4) drawLine(Color(0xFFC86F3E), Offset(w * (0.16f + 0.136f * k), h * 0.045f), Offset(w * (0.16f + 0.136f * k), h * 0.125f), line * 0.6f)
    }
}

/** What the pup says about the jar: nothing to count for kids, the notes left for grown-ups. Never a nag. */
fun jarLine(notes: Int, kid: Boolean, treats: Int): String = when {
    TreatJar.full(notes) -> if (kid) "The jar is full! Tip it for me?" else "The jar is full. Tip it and I get a treat."
    notes == 0 && treats > 0 -> "Yum. Every note you play drops another crumb in."
    notes == 0 -> "Every note you play drops a crumb in my jar."
    kid -> if (TreatJar.fraction(notes) >= 0.75f) "Almost full! A few more notes." else "Play some notes to fill it up."
    else -> "${TreatJar.toFull(notes)} more notes and it's full."
}

/**
 * The treat jar with the pup beside it. Tapping a full jar (or "Tip the jar") tips it: the jar swings over, the biscuits
 * tumble to the pup, he eats ([PupReaction.TREAT], a munch), and the jar is empty again. A jar not full yet wobbles and
 * the pup peeks at it. With animations off it simply empties and he eats.
 */
@Composable
fun TreatJarPanel(world: WorldStore, modifier: Modifier = Modifier) {
    val state = world.state
    val kid = isKid()
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val pup = rememberPupController()
    val sounds = rememberWorldSounds()
    val tip = remember { Animatable(0f) }          // 0 upright .. 1 swung over
    val pour = remember { Animatable(0f) }         // biscuits on their way, 0..1
    val wobble = remember { Animatable(0f) }
    var pouring by remember { mutableIntStateOf(0) }       // biscuits in the air
    var busy by remember { mutableStateOf(false) }
    val full = TreatJar.full(state.jar)
    fun go() {
        if (busy) return
        if (!full) {
            pup.react(PupReaction.PEEK)
            if (!still) scope.launch { wobble.snapTo(1f); wobble.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 300f)) }
            return
        }
        busy = true
        scope.launch {
            if (!still) {
                tip.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                pouring = TreatJar.BISCUITS
                launch { delay(350); sounds.munch() }
                pour.snapTo(0f); pour.animateTo(1f, tween(900, easing = LinearEasing))
            } else sounds.munch()
            world.tip()
            pup.react(PupReaction.TREAT)
            pouring = 0
            if (!still) tip.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 220f))
            busy = false
        }
    }
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SpeechBubble(if (pouring > 0 || pup.poseFor(Pose.WEAR_BASE) == Pose.TREAT) "Crunch! Thank you." else jarLine(state.jar, kid, state.treats),
            style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
            val jarW = 130.dp
            val pupSize = 170.dp
            Box(Modifier.offset(x = 12.dp, y = 30.dp).size(jarW, 165.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0.92f, 0.95f)
                rotationZ = 112f * tip.value + 6f * wobble.value * sin(wobble.value * 9f)
            }.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = ::go)
                .semantics { role = Role.Button }) {
                TreatJarView(state.jar, Modifier.fillMaxSize(), shown = if (pouring > 0) 0 else TreatJar.biscuits(state.jar))
            }
            Pup(Pose.WEAR_BASE, controller = pup, size = pupSize, onTap = ::go,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp))
            if (pouring > 0) {
                // The biscuits arc from the jar's mouth to the pup, one after another.
                Canvas(Modifier.fillMaxSize()) {
                    val from = Offset(12.dp.toPx() + jarW.toPx() * 1.05f, 30.dp.toPx() + 30.dp.toPx())
                    val to = Offset(size.width - 8.dp.toPx() - pupSize.toPx() * 0.42f, size.height - pupSize.toPx() * 0.52f)
                    for (i in 0 until pouring) {
                        val k = ((pour.value * 1.6f - i * 0.03f)).coerceIn(0f, 1f)
                        if (k <= 0f || k >= 1f) continue
                        val x = from.x + (to.x - from.x) * k
                        val y = from.y + (to.y - from.y) * k - sin(k * PI.toFloat()) * 60.dp.toPx()
                        drawBiscuit(Offset(x, y), 24.dp.toPx(), k * 540f + i * 30f, alpha = 1f - k * 0.3f)
                    }
                }
            }
        }
        if (full && !busy) ChunkyButton(onClick = ::go, modifier = Modifier.fillMaxWidth()) { Text("Tip the jar") }
        else Text(if (state.treats > 0) "Treats so far: ${state.treats}" else "", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
