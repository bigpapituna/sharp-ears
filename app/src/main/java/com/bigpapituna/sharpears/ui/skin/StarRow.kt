package com.bigpapituna.sharpears.ui.skin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.skin.Stars
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val StarEdge = Color(0xFFC98A1E)

/**
 * One to three stars (engine/skin/Stars), the middle one larger and raised. After [startDelayMs] each star earned
 * lands in turn: it springs in from small with a turn, throws a ring of sparks, and the [sampler] rings a chime (C, E,
 * then G, higher with each star). Stars not earned stay as quiet outlines, so the row always shows three. With the
 * phone's animations off the stars are simply there; the chimes still play. TalkBack reads "Two stars of three".
 */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 56.dp, sampler: Sampler? = null, startDelayMs: Long = 0L) {
    val earned = stars.coerceIn(1, Stars.MAX)
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val land = remember(earned) { List(Stars.MAX) { Animatable(if (still) 1f else 0f) } }
    val spark = remember(earned) { List(Stars.MAX) { Animatable(if (still) 1f else 0f) } }
    LaunchedEffect(earned) {
        delay(startDelayMs)
        for (i in 0 until earned) {
            sampler?.let { s -> launch { chime(s, i) } }
            if (!still) {
                launch { land[i].animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
                launch { spark[i].animateTo(1f, tween(650, easing = LinearEasing)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(if (still) 260 else 380)
        }
    }
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val emptyEdge = MaterialTheme.colorScheme.outline
    Row(modifier.clearAndSetSemantics { contentDescription = Stars.spoken(earned) },
        horizontalArrangement = Arrangement.spacedBy(size * 0.12f), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until Stars.MAX) {
            val big = i == 1
            val s = if (big) size * 1.22f else size
            val path = remember { Path() }
            Box(Modifier.size(s).offset(y = if (big) -size * 0.18f else 0.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(s)) { drawStar(path, empty, emptyEdge) }           // the slot, always there
                if (i < earned) Canvas(Modifier.size(s).graphicsLayer {
                    val k = land[i].value
                    scaleX = 0.2f + 0.8f * k; scaleY = 0.2f + 0.8f * k
                    rotationZ = (1f - k.coerceIn(0f, 1f)) * -40f
                    alpha = (k * 3f).coerceIn(0f, 1f)
                }) {
                    drawStar(path, PupGold, StarEdge)
                    // A shine on the upper left arm.
                    drawCircle(Color.White, this.size.minDimension * 0.07f, Offset(this.size.width * 0.38f, this.size.height * 0.36f), alpha = 0.55f)
                }
                if (i < earned && !still) Canvas(Modifier.size(s * 1.9f)) { sparks(spark[i].value) }
            }
        }
    }
}

/** A five-point star filling the canvas: [fill] with a rounded [edge]. */
private fun DrawScope.drawStar(path: Path, fill: Color, edge: Color) {
    val c = Offset(size.width / 2f, size.height * 0.53f)
    val outer = size.minDimension * 0.47f
    val inner = outer * 0.48f
    path.reset()
    for (j in 0 until 10) {
        val r = if (j % 2 == 0) outer else inner
        val a = -PI / 2 + j * PI / 5
        val x = c.x + (r * cos(a)).toFloat(); val y = c.y + (r * sin(a)).toFloat()
        if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fill)
    drawPath(path, edge, style = Stroke(width = size.minDimension * 0.06f, join = StrokeJoin.Round))
}

/** Eight sparks flying out from the star as [t] goes 0 to 1, fading. */
private fun DrawScope.sparks(t: Float) {
    if (t <= 0f || t >= 1f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val r0 = size.minDimension * (0.22f + 0.28f * t)
    val r1 = r0 + size.minDimension * 0.08f * (1f - t)
    for (j in 0 until 8) {
        val a = j * PI / 4 + PI / 8
        drawLine(PupGold, Offset(c.x + (r0 * cos(a)).toFloat(), c.y + (r0 * sin(a)).toFloat()),
            Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
            strokeWidth = size.minDimension * 0.035f, cap = StrokeCap.Round, alpha = 1f - t)
    }
}

/** The chime for star [i]: C6, E6, G6, each a soft bell (the note and its octave). */
private suspend fun chime(sampler: Sampler, i: Int) {
    val note = listOf(84, 88, 91)[i.coerceIn(0, 2)]
    sampler.noteOn(note, 62); sampler.noteOn(note + 12, 34)
    delay(900)
    sampler.noteOff(note); sampler.noteOff(note + 12)
}
