package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** The mystery egg's three looks: waiting, cracking (light shows through), hatched (the shell open, empty). */
enum class EggLook { CLOSED, CRACKING, HATCHED }

private val Shell = Color(0xFFFFF4DC)
private val ShellShade = Color(0xFFF0DDB8)
private val Spots = listOf(Color(0xFFC7B3F2), Color(0xFFB5EAD7), Color(0xFFFFB3C1), Color(0xFF9AD0F5))
private val Light = Color(0xFFFFE9A0)

/** An egg outline in a box of [w] x [h] at the origin: narrow on top, round below. */
private fun eggPath(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.5f, h * 0.03f)
    cubicTo(w * 0.80f, h * 0.03f, w * 0.97f, h * 0.48f, w * 0.95f, h * 0.66f)
    cubicTo(w * 0.93f, h * 0.88f, w * 0.74f, h * 0.98f, w * 0.5f, h * 0.98f)
    cubicTo(w * 0.26f, h * 0.98f, w * 0.07f, h * 0.88f, w * 0.05f, h * 0.66f)
    cubicTo(w * 0.03f, h * 0.48f, w * 0.20f, h * 0.03f, w * 0.5f, h * 0.03f)
    close()
}

/** The zigzag the shell breaks along, across the middle; [k] 0..1 how far the crack has run. */
private fun crackPoints(w: Float, h: Float): List<Offset> =
    (0..8).map { i -> Offset(w * (0.04f + 0.92f * i / 8f), h * (if (i % 2 == 0) 0.50f else 0.42f)) }

/**
 * The mystery egg drawn in code: a pastel spotted egg with the pup-style outline. [look] picks the state; [crack]
 * (0..1) is how far the crack has run while CRACKING; [open] (0..1) how far the top has flown off when HATCHED;
 * [wobble] (-1..1) tilts it on its base. A closed egg waiting in the world wobbles by itself now and then.
 */
@Composable
fun EggView(look: EggLook, modifier: Modifier = Modifier, crack: Float = 1f, open: Float = 1f, wobble: Float? = null) {
    val still = reducedMotion()
    // A closed egg rocks twice (0.8 s), then rests 2.4 s; no frames while it rests.
    val rock = remember { Animatable(1f) }
    if (wobble == null && look == EggLook.CLOSED && !still) LaunchedEffect(Unit) {
        while (true) {
            rock.snapTo(0f)
            rock.animateTo(1f, tween(800, easing = LinearEasing))
            delay(2400)
        }
    }
    Canvas(modifier.semantics {
        contentDescription = when (look) { EggLook.CLOSED -> "A mystery egg"; EggLook.CRACKING -> "The egg is cracking"; EggLook.HATCHED -> "The egg hatched" }
    }) {
        val tilt = wobble ?: rock.value.let { sin(it * 4f * PI.toFloat()) * (1f - it) }
        rotate(tilt * 12f, Offset(size.width / 2f, size.height * 0.97f)) { drawEgg(look, crack, open) }
    }
}

private fun DrawScope.drawEgg(look: EggLook, crack: Float, open: Float) {
    val w = size.width; val h = size.height
    val line = w * 0.04f
    val egg = eggPath(w, h)
    val zig = crackPoints(w, h)
    fun shellBody() {
        drawPath(egg, Shell)
        // Shade on the right, spots, a highlight on the upper left.
        clipPath(egg) {
            drawCircle(ShellShade, w * 0.62f, Offset(w * 1.02f, h * 0.7f))
            listOf(Triple(0.32f, 0.30f, 0.07f), Triple(0.66f, 0.24f, 0.05f), Triple(0.24f, 0.62f, 0.06f), Triple(0.58f, 0.56f, 0.08f),
                Triple(0.78f, 0.78f, 0.05f), Triple(0.40f, 0.84f, 0.05f)).forEachIndexed { i, (x, y, r) ->
                drawCircle(Spots[i % Spots.size], w * r, Offset(w * x, h * y))
            }
            drawOval(Color.White.copy(alpha = 0.6f), Offset(w * 0.22f, h * 0.16f), Size(w * 0.12f, h * 0.2f))
        }
    }
    when (look) {
        EggLook.CLOSED -> {
            shellBody()
            drawPath(egg, WorldInk, style = Stroke(line, join = StrokeJoin.Round))
        }
        EggLook.CRACKING -> {
            // Light glows out of it, then the crack runs across with light in it.
            drawCircle(Brush.radialGradient(listOf(Light.copy(alpha = 0.6f * crack), Color.Transparent)), w * 0.75f, Offset(w / 2f, h * 0.48f))
            shellBody()
            drawPath(egg, WorldInk, style = Stroke(line, join = StrokeJoin.Round))
            val n = (crack * (zig.size - 1)).coerceIn(0f, (zig.size - 1).toFloat())
            val path = Path().apply {
                moveTo(zig[0].x, zig[0].y)
                for (i in 1..n.toInt()) lineTo(zig[i].x, zig[i].y)
                val i = n.toInt()
                if (i < zig.size - 1) { val f = n - i; lineTo(zig[i].x + (zig[i + 1].x - zig[i].x) * f, zig[i].y + (zig[i + 1].y - zig[i].y) * f) }
            }
            drawPath(path, Light, style = Stroke(line * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, WorldInk, style = Stroke(line * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        EggLook.HATCHED -> {
            val below = Path().apply {
                moveTo(0f, h); lineTo(0f, zig[0].y); zig.forEach { lineTo(it.x, it.y) }; lineTo(w, zig.last().y); lineTo(w, h); close()
            }
            val above = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, zig.last().y); zig.reversed().forEach { lineTo(it.x, it.y) }; lineTo(0f, zig[0].y); close()
            }
            // The bottom half stays, its inside showing; the top flies up and off to the right, turning.
            clipPath(below) {
                drawPath(egg, Color(0xFFFFFBF0))
                shellBody()
                drawOval(Color(0xFFFFF8E8), Offset(w * 0.1f, h * 0.38f), Size(w * 0.8f, h * 0.16f))
                drawPath(egg, WorldInk, style = Stroke(line, join = StrokeJoin.Round))
            }
            drawPath(Path().apply { moveTo(zig[0].x, zig[0].y); zig.forEach { lineTo(it.x, it.y) } }, WorldInk, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            translate(w * 0.45f * open, -h * 0.35f * open) {
                rotate(50f * open, Offset(w / 2f, h * 0.45f)) {
                    clipPath(above) { shellBody(); drawPath(egg, WorldInk, style = Stroke(line, join = StrokeJoin.Round)) }
                    drawPath(Path().apply { moveTo(zig[0].x, zig[0].y); zig.forEach { lineTo(it.x, it.y) } }, WorldInk, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** A nest of twigs for the egg to sit in (in the garden). */
fun DrawScope.drawNest(c: Offset, w: Float) {
    val nest = Path().apply { addOval(Rect(c.x - w / 2f, c.y - w * 0.16f, c.x + w / 2f, c.y + w * 0.16f)) }
    drawPath(nest, Color(0xFFB07A4A))
    drawPath(nest, WorldInk, style = Stroke(w * 0.04f))
    for (k in 0 until 6) {
        val x = c.x - w * 0.4f + k * w * 0.16f
        drawLine(Color(0xFF8A5A34), Offset(x, c.y - w * 0.08f), Offset(x + w * 0.12f, c.y + w * 0.06f), w * 0.03f, StrokeCap.Round)
    }
}
