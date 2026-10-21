package com.bigpapituna.sharpears.ui.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.bigpapituna.sharpears.engine.world.WearItem

/*
 * Things for his face, in the eyes' frame: centred between his eyes at (302, 195), tilted 18.4 degrees up to the right
 * with them. In it the eyes are at (-79, 0) and (79, 0), the nose at (5, 44), the cheeks at (-114, 36) and (113, 34).
 */

private inline fun DrawScope.onFace(block: DrawScope.() -> Unit) = rotate(-18.4f, Offset(302f, 195f)) { translate(302f, 195f) { block() } }

/** Glasses arms toward the ears and a bridge between the lenses. */
private fun DrawScope.frames(a: Float, color: Color = WorldInk) {
    val arms = path { moveTo(-125f, -8f); lineTo(-176f, -22f); moveTo(125f, -8f); lineTo(172f, -26f) }
    drawPath(arms, color, alpha = a, style = Stroke(14f, cap = StrokeCap.Round))
    val bridge = path { moveTo(-30f, -10f); quadraticTo(0f, -32f, 30f, -10f) }
    drawPath(bridge, color, alpha = a, style = Stroke(14f, cap = StrokeCap.Round))
}

internal fun DrawScope.faceFront(item: WearItem, a: Float) {
    when (item) {
        WearItem.SUNGLASSES -> onFace {
            frames(a)
            for (x in listOf(-79f, 79f)) {
                drawOval(WorldInk, Offset(x - 56f, -50f), Size(112f, 100f), alpha = a)
                drawOval(Brush.verticalGradient(listOf(Color(0xFF4A4D63), Color(0xFF1E1D26)), startY = -42f, endY = 42f),
                    Offset(x - 46f, -40f), Size(92f, 80f), alpha = a)
                rotate(-35f, Offset(x - 12f, -14f)) {
                    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x - 30f, -22f), Size(36f, 12f), CornerRadius(6f), alpha = a)
                }
            }
        }
        WearItem.STAR_GLASSES -> onFace {
            frames(a, Color(0xFFE5484D))
            for (x in listOf(-79f, 79f)) {
                starShape(Offset(x, 0f), 66f, Color(0xFFF59BC0), a)
                starShape(Offset(x, 2f), 40f, Color(0xFFFFE6F0).copy(alpha = 0.55f), a * 0.9f)
                shine(x - 26f, -24f, 20f, 9f, a)
            }
        }
        WearItem.HEART_GLASSES -> onFace {
            frames(a, Color(0xFFE5484D))
            for (x in listOf(-79f, 79f)) {
                val heart = path {
                    moveTo(x, 44f)
                    cubicTo(x - 70f, 4f, x - 64f, -54f, x - 22f, -46f)
                    quadraticTo(x - 6f, -42f, x, -24f)
                    quadraticTo(x + 6f, -42f, x + 22f, -46f)
                    cubicTo(x + 64f, -54f, x + 70f, 4f, x, 44f)
                    close()
                }
                wearShape(heart, Color(0xFFE5484D), a, 7f)
                drawPath(heart, Color(0xFFFF8FA6).copy(alpha = 0.5f), alpha = a)
                shine(x - 36f, -30f, 22f, 10f, a)
            }
        }
        WearItem.HERO_MASK -> onFace {
            val mask = path {
                moveTo(-150f, -20f); cubicTo(-140f, -62f, -40f, -60f, 0f, -36f); cubicTo(40f, -60f, 140f, -62f, 150f, -20f)
                cubicTo(156f, 26f, 110f, 54f, 40f, 34f); quadraticTo(0f, 22f, -40f, 34f); cubicTo(-110f, 54f, -156f, 26f, -150f, -20f); close()
                addOval(Rect(Offset(-79f, 0f), 34f)); addOval(Rect(Offset(79f, 0f), 34f))
                fillType = PathFillType.EvenOdd
            }
            // The ties flutter out past his ear.
            val ties = path { moveTo(-146f, -14f); quadraticTo(-200f, -40f, -232f, -10f); moveTo(-146f, -10f); quadraticTo(-196f, 10f, -226f, 34f) }
            drawPath(ties, WorldInk, alpha = a, style = Stroke(26f, cap = StrokeCap.Round))
            drawPath(ties, Color(0xFFE5484D), alpha = a, style = Stroke(14f, cap = StrokeCap.Round))
            drawPath(mask, WorldInk, alpha = a, style = Stroke(14f))
            drawPath(mask, Color(0xFFE5484D), alpha = a)
            drawPath(mask, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), -60f, 10f), alpha = a)
        }
        WearItem.MONOCLE -> onFace {
            val c = Offset(79f, 0f)
            val chain = path { moveTo(c.x + 30f, c.y + 48f); quadraticTo(c.x + 60f, 120f, c.x + 20f, 170f) }
            drawPath(chain, Gold, alpha = a, style = Stroke(6f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            drawCircle(Color(0xFFDFF3FF).copy(alpha = 0.35f), 52f, c, alpha = a)
            drawCircle(WorldInk, 58f, c, alpha = a, style = Stroke(22f))
            drawCircle(Gold, 58f, c, alpha = a, style = Stroke(12f))
            drawArc(Color.White.copy(alpha = 0.7f), 200f, 60f, false, Offset(c.x - 40f, c.y - 40f), Size(80f, 80f), alpha = a, style = Stroke(7f, cap = StrokeCap.Round))
        }
        WearItem.FRECKLES -> onFace {
            val dot = Color(0xFFB0683E)
            for ((cx, cy) in listOf(-114f to 36f, 113f to 34f)) {
                for ((dx, dy, r) in listOf(Triple(-18f, -10f, 7f), Triple(4f, -16f, 6f), Triple(22f, -4f, 7f), Triple(-6f, 8f, 6f), Triple(14f, 14f, 5f), Triple(-26f, 12f, 5f)))
                    drawCircle(dot, r, Offset(cx + dx, cy + dy), alpha = a * 0.85f)
            }
        }
        WearItem.EYE_PATCH -> onFace {
            val string = path { moveTo(-150f, -40f); quadraticTo(-40f, -110f, 168f, -62f) }
            drawPath(string, Black, alpha = a, style = Stroke(10f, cap = StrokeCap.Round))
            inkLine(path { moveTo(-120f, 10f); lineTo(-176f, 30f) }, 10f, a, Black)
            val patch = path { addOval(Rect(-132f, -46f, -26f, 48f)) }
            wearShape(patch, Black, a, 6f)
            shine(-112f, -30f, 30f, 12f, a)
        }
        WearItem.CARROT_NOSE -> onFace {
            val n = Offset(5f, 44f)
            val carrot = path { moveTo(n.x - 6f, n.y - 30f); quadraticTo(n.x + 60f, n.y - 10f, n.x + 128f, n.y + 22f); quadraticTo(n.x + 50f, n.y + 34f, n.x - 4f, n.y + 30f); close() }
            wearShape(carrot, Color(0xFFF68A3A), a, 7f)
            for (k in 1..3) inkLine(path { moveTo(n.x + k * 28f, n.y - 12f + k * 6f); lineTo(n.x + k * 28f + 4f, n.y + 4f + k * 6f) }, 5f, a * 0.6f, Color(0xFFC9611E))
            drawCircle(WorldInk, 34f, n, alpha = a)
            drawCircle(Color(0xFFF68A3A), 27f, n, alpha = a)
            shine(n.x - 18f, n.y - 20f, 18f, 9f, a)
        }
        WearItem.WHISKERS -> onFace {
            for (s in listOf(-1f, 1f)) {
                val from = if (s < 0) Offset(-40f, 62f) else Offset(60f, 56f)
                for ((dx, dy) in listOf(110f to -22f, 118f to 8f, 108f to 40f)) {
                    inkLine(path { moveTo(from.x + s * 8f, from.y + dy * 0.2f); quadraticTo(from.x + s * dx * 0.55f, from.y + dy * 0.7f - 6f, from.x + s * dx, from.y + dy) }, 7f, a)
                }
            }
            // The cat's little nose spots.
            for (s in listOf(-1f, 1f)) for (k in 0..2) drawCircle(WorldInk, 5f, Offset(10f + s * (34f + k * 14f), 76f + (k % 2) * 10f), alpha = a * 0.7f)
        }
        else -> {}
    }
}
