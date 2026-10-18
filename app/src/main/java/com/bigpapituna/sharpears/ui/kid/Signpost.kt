package com.bigpapituna.sharpears.ui.kid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.play.ConcertCardLayout
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.WorldInk
import com.bigpapituna.sharpears.engine.play.Box as CardBox

/**
 * Pup's quest on the map (2.2): not a card but a little wooden signpost planted by the pup, its board holding the day's
 * three stars, gold once found ([found] of three), pale until then. The pup points at it now and then and says
 * "Three stars to find today!"; tapping it opens the quest ([onTap]). A star found since it last showed pops in.
 * Drawn in the world's style (thin brown outline, warm wood), the same at dusk.
 */
@Composable
fun Signpost(found: Int, onTap: () -> Unit, modifier: Modifier = Modifier, width: Dp = 64.dp) {
    val still = reducedMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(found) { if (found > 0 && !still) { pop.snapTo(0.82f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f)) } }
    Canvas(modifier.size(width, width * 1.15f)
        .graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
        .clickable(role = Role.Button, onClickLabel = "Open Pup's quest", onClick = onTap)
        .semantics { contentDescription = KidLines.signpost(found) }) {
        drawSignpost(found.coerceIn(0, 3))
    }
}

private val Wood = Color(0xFFD9A066)
private val WoodDeep = Color(0xFFB27A45)
private val StarPale = Color(0xFFFFF4DC)

/** The post, its board (a touch tilted, as signposts are) and the three stars on it, filling the canvas. */
private fun DrawScope.drawSignpost(found: Int) {
    val w = size.width; val h = size.height
    val line = w * 0.045f
    // The post, from under the board into a tuft of grass.
    val postW = w * 0.14f
    drawRoundRect(WorldInk, Offset(w / 2 - postW / 2 - line / 2, h * 0.30f), Size(postW + line, h * 0.66f), CornerRadius(line))
    drawRoundRect(WoodDeep, Offset(w / 2 - postW / 2, h * 0.30f), Size(postW, h * 0.65f), CornerRadius(line * 0.6f))
    val tuft = Path().apply {
        val y = h * 0.97f; val x = w / 2; val s = w * 0.07f
        moveTo(x - s * 2.2f, y); lineTo(x - s * 1.3f, y - s * 1.5f); lineTo(x - s * 0.6f, y); lineTo(x, y - s * 2f)
        lineTo(x + s * 0.6f, y); lineTo(x + s * 1.4f, y - s * 1.6f); lineTo(x + s * 2.2f, y)
    }
    drawPath(tuft, Color(0xFF6FAE63), style = Stroke(line * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // The board.
    rotate(-4f, Offset(w / 2, h * 0.28f)) {
        val top = h * 0.06f; val bh = h * 0.40f; val left = w * 0.02f; val bw = w * 0.96f
        drawRoundRect(WorldInk, Offset(left - line / 2, top - line / 2), Size(bw + line, bh + line), CornerRadius(w * 0.12f))
        drawRoundRect(Wood, Offset(left, top), Size(bw, bh), CornerRadius(w * 0.1f))
        // Grain.
        drawLine(WoodDeep.copy(alpha = 0.55f), Offset(left + bw * 0.08f, top + bh * 0.82f), Offset(left + bw * 0.42f, top + bh * 0.82f), line * 0.5f, StrokeCap.Round)
        drawLine(WoodDeep.copy(alpha = 0.55f), Offset(left + bw * 0.6f, top + bh * 0.16f), Offset(left + bw * 0.9f, top + bh * 0.16f), line * 0.5f, StrokeCap.Round)
        // Three stars across it.
        val s = bw * 0.27f
        for (i in 0 until 3) {
            val cx = left + bw * (0.2f + 0.3f * i); val cy = top + bh * 0.5f
            val pts = ConcertCardLayout.starPoints(CardBox(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2))
            val star = Path().apply { pts.forEachIndexed { k, (x, y) -> if (k == 0) moveTo(x, y) else lineTo(x, y) }; close() }
            if (i < found) {
                drawPath(star, PupGold)
                drawPath(star, WorldInk, style = Stroke(line * 0.55f, join = StrokeJoin.Round))
            } else {
                drawPath(star, StarPale.copy(alpha = 0.7f))
                drawPath(star, WorldInk.copy(alpha = 0.45f), style = Stroke(line * 0.45f, join = StrokeJoin.Round))
            }
        }
    }
}
