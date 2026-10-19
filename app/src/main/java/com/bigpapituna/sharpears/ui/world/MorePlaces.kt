package com.bigpapituna.sharpears.ui.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.bigpapituna.sharpears.engine.world.Place
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * 2.3: the four new places of the pup's world, drawn like the garden, the pond and the concert hall (thin brown outlines,
 * pastel colour, day in the light theme and dusk in the dark one): the workshop, where a piano is being built; the
 * treehouse in its big tree; the night sky over a dark blue hill; the fair with its tents, carousel, balloons and the
 * strongman's bell. Each scene's still parts are painted once into PlaceScene's bitmap; the few things that move (clouds,
 * falling leaves, stars and fireflies, balloons and carousel horses) are drawn on each tick of the scene clock. The night
 * sky is night in both themes.
 */

private val Ink = WorldInk

/** A place with something that moves (the workshop and the concert hall are still: no clock, painted once). */
internal val Place.moves: Boolean get() = this != Place.CONCERT && this != Place.WORKSHOP

private fun DrawScope.rr(l: Float, t: Float, r: Float, b: Float, k: Float) =
    Path().apply { addRoundRect(RoundRect(size.width * l, size.height * t, size.width * r, size.height * b, CornerRadius(size.width * k))) }

private fun pick(night: Boolean, day: Long, dusk: Long) = Color(if (night) dusk else day)

// ---- The workshop ------------------------------------------------------------------------------------------------------

/** The workshop's still parts: plank walls, a window, the pegboard, the workbench, the upright piano being built, the floor. */
internal fun DrawScope.drawWorkshop(night: Boolean) {
    val w = size.width; val h = size.height
    val line = w * 0.0055f
    // The back wall: warm planks standing up, a knot here and there.
    val wallBottom = h * 0.53f
    drawRect(Brush.verticalGradient(listOf(pick(night, 0xFFF1D6AC, 0xFF6E5038), pick(night, 0xFFE3BE8A, 0xFF59412E)), endY = wallBottom),
        size = Size(w, wallBottom))
    for (k in 1 until 14) drawLine(pick(night, 0xFFD5A972, 0xFF4E3826), Offset(w * k / 14f, 0f), Offset(w * k / 14f, wallBottom), w * 0.004f)
    for ((x, y) in listOf(0.035f to 0.42f, 0.61f to 0.36f, 0.25f to 0.04f, 0.97f to 0.06f))
        drawOval(pick(night, 0xFFC99A60, 0xFF4A3424), Offset(w * x - w * 0.009f, h * y), Size(w * 0.018f, h * 0.03f))
    // A soft glow on the wall at dusk, as if a lamp were lit over the bench.
    if (night) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD58A).copy(alpha = 0.32f), Color.Transparent), Offset(w * 0.24f, h * 0.36f), w * 0.4f),
        w * 0.4f, Offset(w * 0.24f, h * 0.36f))

    // The window: day sky with a cloud, or the evening with a star; four panes and a sill.
    val win = Rect(w * 0.05f, h * 0.07f, w * 0.26f, h * 0.27f)
    drawRect(Brush.verticalGradient(listOf(pick(night, 0xFFBFE3F7, 0xFF1F2747), pick(night, 0xFFEAF6F4, 0xFF3D3A63)), win.top, win.bottom), win.topLeft, win.size)
    if (night) {
        drawCircle(Color(0xFFFFF0B8), w * 0.018f, Offset(w * 0.20f, h * 0.12f))
        drawCircle(Color(0xFF1F2747), w * 0.016f, Offset(w * 0.21f, h * 0.112f))
        drawCircle(Color.White, w * 0.004f, Offset(w * 0.09f, h * 0.11f), alpha = 0.8f)
    } else {
        val cl = Path().apply { addOval(Rect(Offset(w * 0.11f, h * 0.20f), w * 0.025f)); addOval(Rect(Offset(w * 0.14f, h * 0.19f), w * 0.032f)); addOval(Rect(Offset(w * 0.17f, h * 0.205f), w * 0.022f)) }
        drawPath(cl, Color.White, alpha = 0.95f)
    }
    drawRect(Ink, win.topLeft, win.size, style = Stroke(line * 1.6f))
    drawLine(Ink, Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), line * 1.2f)
    drawLine(Ink, Offset(win.left, win.center.y), Offset(win.right, win.center.y), line * 1.2f)
    outlined(rr(0.035f, 0.27f, 0.275f, 0.29f, 0.005f), pick(night, 0xFFC8915A, 0xFF7A5536), line)

    // The pegboard, where the tools hang (the collected ones sit here).
    outlined(rr(0.30f, 0.045f, 0.605f, 0.315f, 0.012f), pick(night, 0xFFD9B98E, 0xFF8C6C4D), line)
    for (i in 0 until 9) for (j in 0 until 7) drawCircle(Ink.copy(alpha = 0.22f), w * 0.0035f, Offset(w * (0.32f + i * 0.033f), h * (0.07f + j * 0.037f)))
    // Two hooks with a coil of string and a little saw outline painted on the board.
    drawArc(Ink, 0f, 180f, false, Offset(w * 0.565f, h * 0.22f), Size(w * 0.02f, w * 0.02f), style = Stroke(line))
    drawCircle(pick(night, 0xFFE5C07A, 0xFFB08A50), w * 0.022f, Offset(w * 0.575f, h * 0.27f))
    drawCircle(Ink, w * 0.022f, Offset(w * 0.575f, h * 0.27f), style = Stroke(line * 0.8f))
    drawCircle(Ink, w * 0.011f, Offset(w * 0.575f, h * 0.27f), style = Stroke(line * 0.6f))

    // The workbench: a thick top, a vise at its end, legs and a low shelf with planks on it.
    outlined(rr(0.025f, 0.395f, 0.43f, 0.435f, 0.006f), pick(night, 0xFFC98B52, 0xFF8A5C36), line)
    drawLine(pick(night, 0xFFA96E3E, 0xFF6E4A2C), Offset(w * 0.035f, h * 0.405f), Offset(w * 0.42f, h * 0.405f), line * 0.8f)
    for (x in listOf(0.05f, 0.385f)) outlined(rr(x, 0.435f, x + 0.035f, 0.535f, 0.004f), pick(night, 0xFFB57A46, 0xFF7A5232), line)
    outlined(rr(0.05f, 0.49f, 0.42f, 0.505f, 0.003f), pick(night, 0xFFC98B52, 0xFF8A5C36), line)
    outlined(rr(0.10f, 0.47f, 0.33f, 0.49f, 0.003f), pick(night, 0xFFE8C99A, 0xFFA48460), line * 0.8f)
    outlined(rr(0.012f, 0.375f, 0.06f, 0.455f, 0.006f), pick(night, 0xFFA9B0BC, 0xFF6D7380), line)
    drawLine(Ink, Offset(w * 0.0f, h * 0.418f), Offset(w * 0.02f, h * 0.418f), line * 1.6f, StrokeCap.Round)
    // A curl of wood shavings on the bench.
    for (k in 0 until 3) drawArc(pick(night, 0xFFF4DDB0, 0xFFB89A6E), 200f, 250f, false, Offset(w * (0.255f + k * 0.022f), h * 0.378f), Size(w * 0.02f, w * 0.02f),
        style = Stroke(line * 0.9f, cap = StrokeCap.Round))

    // The piano being built: an upright case open at the front, the gold frame with its strings, the keys going in.
    val wood = pick(night, 0xFF9B6438, 0xFF5E3C22)
    outlined(rr(0.615f, 0.115f, 0.965f, 0.15f, 0.006f), wood, line)
    outlined(rr(0.63f, 0.14f, 0.665f, 0.525f, 0.004f), wood, line)
    outlined(rr(0.915f, 0.14f, 0.95f, 0.525f, 0.004f), wood, line)
    drawRect(pick(night, 0xFF7A4C2A, 0xFF46301C), Offset(w * 0.665f, h * 0.15f), Size(w * 0.25f, h * 0.25f))
    val harp = Path().apply {
        moveTo(w * 0.68f, h * 0.17f); lineTo(w * 0.90f, h * 0.17f); lineTo(w * 0.90f, h * 0.37f)
        quadraticTo(w * 0.79f, h * 0.395f, w * 0.68f, h * 0.30f); close()
    }
    outlined(harp, pick(night, 0xFFE2B85A, 0xFFB08A3E), line)
    for (k in 0 until 14) {
        val x = w * (0.695f + k * 0.0148f)
        val bottom = h * (0.30f + 0.065f * k / 13f)
        drawLine(pick(night, 0xFFF4F1EA, 0xFFC9C3B6), Offset(x, h * 0.185f), Offset(x + w * 0.004f, bottom), w * 0.0022f)
        drawCircle(Ink, w * 0.0028f, Offset(x, h * 0.18f))
    }
    // The keybed, with keys in and a few still missing.
    outlined(rr(0.605f, 0.40f, 0.975f, 0.445f, 0.004f), wood, line)
    val keyTop = h * 0.385f; val keyBot = h * 0.418f
    val kw = w * 0.0175f
    for (k in 0 until 14) {
        if (k == 4 || k == 9 || k == 10) continue
        val x = w * 0.67f + k * kw
        drawRect(Color(0xFFFFFCF4), Offset(x, keyTop), Size(kw - w * 0.002f, keyBot - keyTop))
        drawRect(Ink, Offset(x, keyTop), Size(kw - w * 0.002f, keyBot - keyTop), style = Stroke(line * 0.5f))
    }
    for (k in listOf(0, 1, 3, 5, 7, 11, 12)) drawRect(Color(0xFF2B2421), Offset(w * 0.67f + (k + 0.65f) * kw, keyTop), Size(kw * 0.6f, (keyBot - keyTop) * 0.6f))
    // The legs and the toe blocks.
    for (x in listOf(0.64f, 0.92f)) outlined(rr(x, 0.445f, x + 0.03f, 0.535f, 0.004f), wood, line)

    // The floor: wide boards, darker toward the front, the joints staggered; a few shavings.
    val floorTop = h * 0.525f
    drawRect(Brush.verticalGradient(listOf(pick(night, 0xFFE2B47C, 0xFF8A6440), pick(night, 0xFFCF9C62, 0xFF6E4E31)), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
    drawLine(Ink, Offset(0f, floorTop), Offset(w, floorTop), line)
    val seam = pick(night, 0xFFB98552, 0xFF5A3F27)
    var y = floorTop; var row = 0
    while (y < h) {
        val bh = h * (0.055f + 0.012f * row)
        drawLine(seam, Offset(0f, y + bh), Offset(w, y + bh), w * 0.004f)
        val off = if (row % 2 == 0) 0.17f else 0.42f
        for (k in 0..2) drawLine(seam, Offset(w * ((off + k * 0.38f) % 1f), y), Offset(w * ((off + k * 0.38f) % 1f), y + bh), w * 0.003f)
        y += bh; row++
    }
    for ((x, yy) in listOf(0.05f to 0.585f, 0.93f to 0.60f, 0.12f to 0.99f, 0.97f to 0.83f))
        drawArc(pick(night, 0xFFF4DDB0, 0xFFB89A6E), 190f, 250f, false, Offset(w * x, h * yy), Size(w * 0.025f, w * 0.025f), style = Stroke(line, cap = StrokeCap.Round))
}

/** A measuring tape laid across the workshop floor: yellow, edged in ink, its marks along it. */
internal fun DrawScope.drawTapeTrail(trail: Path, w: Float, night: Boolean) {
    val tape = pick(night, 0xFFF6CF4A, 0xFFC9A63E)
    drawPath(trail, Ink.copy(alpha = 0.8f), style = Stroke(w * 0.78f + size.width * 0.01f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    drawPath(trail, tape, style = Stroke(w * 0.78f, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    // The marks: short ones close together, a long one every so often, across the tape (a dash as wide as the tape is long).
    drawPath(trail, Ink.copy(alpha = 0.7f), style = Stroke(w * 0.32f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.0035f, size.width * 0.0165f))))
    drawPath(trail, Ink.copy(alpha = 0.8f), style = Stroke(w * 0.62f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.004f, size.width * 0.096f))))
}

// ---- The treehouse -----------------------------------------------------------------------------------------------------

/** The treehouse's still parts: the lawn, the big tree with its leaves, the lower deck with its rope ladder, the hut up top. */
internal fun DrawScope.drawTreehouse(night: Boolean) {
    val p = Palette(night)
    val w = size.width; val h = size.height
    val line = w * 0.006f
    ground(p, top = 0.53f)
    val leaf = pick(night, 0xFF7CC173, 0xFF3F7048)
    val leafDeep = pick(night, 0xFF62A85C, 0xFF31593A)
    val bark = pick(night, 0xFFB98454, 0xFF7F5A3A)
    val plank = pick(night, 0xFFD9A86C, 0xFF8E6A46)
    // The leaves behind: big round clusters across the top, the deep ones first.
    val back = Path().apply {
        for ((x, y, r) in listOf(Triple(0.10f, 0.16f, 0.11f), Triple(0.30f, 0.07f, 0.13f), Triple(0.52f, 0.12f, 0.14f), Triple(0.74f, 0.06f, 0.12f),
            Triple(0.93f, 0.15f, 0.10f), Triple(0.40f, 0.24f, 0.10f), Triple(0.20f, 0.27f, 0.08f), Triple(0.64f, 0.26f, 0.09f)))
            addOval(Rect(Offset(w * x, h * y), w * r))
    }
    drawPath(back, Ink, style = Stroke(w * 0.012f))
    drawPath(back, leafDeep)
    // The trunk, wide at the roots, and its two big limbs.
    val trunk = Path().apply {
        moveTo(w * 0.38f, h * 0.585f)
        quadraticTo(w * 0.44f, h * 0.55f, w * 0.455f, h * 0.44f)
        lineTo(w * 0.46f, h * 0.20f)
        lineTo(w * 0.555f, h * 0.20f)
        lineTo(w * 0.56f, h * 0.44f)
        quadraticTo(w * 0.575f, h * 0.55f, w * 0.64f, h * 0.585f)
        close()
    }
    outlined(trunk, bark, line)
    outlined(Path().apply { moveTo(w * 0.47f, h * 0.32f); quadraticTo(w * 0.36f, h * 0.30f, w * 0.20f, h * 0.22f); lineTo(w * 0.205f, h * 0.20f)
        quadraticTo(w * 0.36f, h * 0.26f, w * 0.47f, h * 0.27f); close() }, bark, line)
    outlined(Path().apply { moveTo(w * 0.545f, h * 0.30f); quadraticTo(w * 0.70f, h * 0.30f, w * 0.88f, h * 0.36f); lineTo(w * 0.885f, h * 0.38f)
        quadraticTo(w * 0.70f, h * 0.335f, w * 0.545f, h * 0.345f); close() }, bark, line)
    for ((x, y0, y1) in listOf(Triple(0.48f, 0.24f, 0.36f), Triple(0.53f, 0.30f, 0.50f), Triple(0.50f, 0.46f, 0.56f)))
        drawLine(Ink.copy(alpha = 0.35f), Offset(w * x, h * y0), Offset(w * x + w * 0.004f, h * y1), line * 0.8f, StrokeCap.Round)
    // The owl's hollow in the trunk.
    drawOval(pick(night, 0xFF5C3A22, 0xFF2E1D12), Offset(w * 0.478f, h * 0.38f), Size(w * 0.055f, h * 0.06f))
    drawOval(Ink, Offset(w * 0.478f, h * 0.38f), Size(w * 0.055f, h * 0.06f), style = Stroke(line))

    // The lower deck on the left limb, its railing, and the rope ladder down to the grass.
    outlined(rr(0.07f, 0.355f, 0.43f, 0.385f, 0.004f), plank, line)
    for (k in 1 until 6) drawLine(Ink.copy(alpha = 0.35f), Offset(w * (0.07f + k * 0.06f), h * 0.357f), Offset(w * (0.07f + k * 0.06f), h * 0.383f), line * 0.6f)
    outlined(Path().apply { moveTo(w * 0.40f, h * 0.385f); lineTo(w * 0.465f, h * 0.45f); lineTo(w * 0.465f, h * 0.43f); lineTo(w * 0.415f, h * 0.385f); close() }, plank, line * 0.8f)
    for (x in listOf(0.08f, 0.16f, 0.24f, 0.32f, 0.41f)) drawLine(Ink, Offset(w * x, h * 0.355f), Offset(w * x, h * 0.315f), line * 1.4f, StrokeCap.Round)
    drawLine(Ink, Offset(w * 0.075f, h * 0.318f), Offset(w * 0.415f, h * 0.318f), line * 1.6f, StrokeCap.Round)
    drawLine(plank, Offset(w * 0.075f, h * 0.318f), Offset(w * 0.415f, h * 0.318f), line * 0.8f, StrokeCap.Round)
    val rope = pick(night, 0xFFD8C08A, 0xFF9E8A60)
    for (x in listOf(0.135f, 0.19f)) { drawLine(Ink, Offset(w * x, h * 0.385f), Offset(w * x, h * 0.57f), line * 1.3f); drawLine(rope, Offset(w * x, h * 0.385f), Offset(w * x, h * 0.57f), line * 0.6f) }
    var ry = h * 0.405f
    while (ry < h * 0.56f) { outlined(rr(0.13f, ry / h, 0.195f, ry / h + 0.009f, 0.002f), plank, line * 0.6f); ry += h * 0.034f }

    // The hut on the right limb: its platform, plank walls, a pointed roof, a round window and a door.
    outlined(rr(0.56f, 0.29f, 0.93f, 0.315f, 0.004f), plank, line)
    for (x in listOf(0.60f, 0.89f)) outlined(rr(x, 0.315f, x + 0.018f, 0.36f, 0.003f), plank, line * 0.8f)
    val walls = pick(night, 0xFFE58A6A, 0xFF9C5440)
    outlined(rr(0.615f, 0.17f, 0.865f, 0.29f, 0.004f), walls, line)
    for (k in 1 until 4) drawLine(Ink.copy(alpha = 0.3f), Offset(w * 0.62f, h * (0.17f + k * 0.03f)), Offset(w * 0.86f, h * (0.17f + k * 0.03f)), line * 0.6f)
    outlined(Path().apply { moveTo(w * 0.585f, h * 0.18f); lineTo(w * 0.74f, h * 0.075f); lineTo(w * 0.895f, h * 0.18f); close() }, pick(night, 0xFF8A5A3A, 0xFF5A3A26), line)
    val window = Offset(w * 0.68f, h * 0.215f)
    drawCircle(if (night) Color(0xFFFFE6A0) else Color(0xFFBFE3F7), w * 0.025f, window)
    if (night) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6A0).copy(alpha = 0.5f), Color.Transparent), window, w * 0.07f), w * 0.07f, window)
    drawCircle(Ink, w * 0.025f, window, style = Stroke(line))
    drawLine(Ink, window - Offset(w * 0.025f, 0f), window + Offset(w * 0.025f, 0f), line * 0.7f)
    outlined(Path().apply { moveTo(w * 0.77f, h * 0.29f); lineTo(w * 0.77f, h * 0.225f); quadraticTo(w * 0.80f, h * 0.195f, w * 0.83f, h * 0.225f); lineTo(w * 0.83f, h * 0.29f); close() },
        pick(night, 0xFF6E4630, 0xFF3E2618), line)
    // A few leaves in front, over the limbs.
    val front = Path().apply {
        for ((x, y, r) in listOf(Triple(0.05f, 0.30f, 0.06f), Triple(0.96f, 0.30f, 0.06f), Triple(0.35f, 0.17f, 0.06f), Triple(0.56f, 0.05f, 0.07f)))
            addOval(Rect(Offset(w * x, h * y), w * r))
    }
    drawPath(front, Ink, style = Stroke(w * 0.01f))
    drawPath(front, leaf)
    drawCircle(Color.White.copy(alpha = 0.16f), w * 0.03f, Offset(w * 0.54f, h * 0.035f))
    // Roots over the grass.
    for ((x, d) in listOf(0.38f to -1f, 0.64f to 1f)) drawLine(bark, Offset(w * x, h * 0.585f), Offset(w * (x + d * 0.05f), h * 0.60f), w * 0.016f, StrokeCap.Round)
}

/** Leaves drifting down past the tree. */
internal fun DrawScope.treehouseLife(t: Float) {
    val w = size.width; val h = size.height
    for (k in 0 until 4) {
        val f = (t * 5f + k * 0.27f) % 1f
        val x = w * (0.15f + 0.22f * k) + sin((f * 6f + k).toDouble()).toFloat() * w * 0.03f
        val y = h * (0.20f + 0.34f * f)
        rotate(f * 300f + k * 40f, Offset(x, y)) {
            drawOval(Color(0xFF8CC873), Offset(x - w * 0.012f, y - w * 0.006f), Size(w * 0.024f, w * 0.012f), alpha = 1f - f * 0.6f)
        }
    }
}

/** A dirt path with log slices to step on, under the big tree. */
internal fun DrawScope.drawLogTrail(trail: Path, w: Float, night: Boolean) {
    val p = Palette(night)
    drawPath(trail, p.line, style = Stroke(w + size.width * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(trail, pick(night, 0xFFE3C79A, 0xFF7E6648), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val gap = floatArrayOf(size.width * 0.001f, size.width * 0.075f)
    drawPath(trail, Ink.copy(alpha = 0.7f), style = Stroke(w * 0.62f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
    drawPath(trail, pick(night, 0xFFC98B52, 0xFF8A5C36), style = Stroke(w * 0.52f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
    drawPath(trail, pick(night, 0xFFEBC68E, 0xFFAE8458), style = Stroke(w * 0.30f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
}

// ---- The night sky -----------------------------------------------------------------------------------------------------

/** The night sky's sky, behind the still image: deep blue in both themes (a little lighter in the light one). */
internal fun DrawScope.nightSkyBase(night: Boolean) {
    drawRect(Brush.verticalGradient(listOf(pick(night, 0xFF22306A, 0xFF0F1632), pick(night, 0xFF4B4E92, 0xFF2D2B5C)), endY = size.height * 0.62f),
        size = Size(size.width, size.height * 0.62f + 2f))
}

/** The night sky's still parts: the moon, the far hill and the near one with its lone tree, grass against the sky. */
internal fun DrawScope.drawNightSky(night: Boolean) {
    val w = size.width; val h = size.height
    val line = w * 0.006f
    // The moon with its glow and three soft craters.
    val moon = Offset(w * 0.80f, h * 0.13f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.35f), Color.Transparent), moon, w * 0.16f), w * 0.16f, moon)
    drawCircle(Color(0xFFFFF0B8), w * 0.062f, moon)
    for ((dx, dy, r) in listOf(Triple(-0.018f, -0.012f, 0.014f), Triple(0.02f, 0.008f, 0.01f), Triple(-0.004f, 0.026f, 0.008f)))
        drawCircle(Color(0xFFEBD893), w * r, moon + Offset(w * dx, w * dy))
    // The far hill, bluish, then the near one with its rim of light.
    val far = Path().apply {
        moveTo(0f, h * 0.54f); quadraticTo(w * 0.30f, h * 0.44f, w * 0.62f, h * 0.50f); quadraticTo(w * 0.85f, h * 0.43f, w, h * 0.47f); lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(far, pick(night, 0xFF35507E, 0xFF1F3152))
    val near = Path().apply {
        moveTo(0f, h * 0.60f); quadraticTo(w * 0.22f, h * 0.49f, w * 0.48f, h * 0.505f); quadraticTo(w * 0.78f, h * 0.52f, w, h * 0.58f); lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(near, Brush.verticalGradient(listOf(pick(night, 0xFF2F6A73, 0xFF1D4550), pick(night, 0xFF214B58, 0xFF122E38)), startY = h * 0.49f, endY = h))
    drawPath(near, Color(0xFFBFD8FF).copy(alpha = 0.35f), style = Stroke(line))
    // Grass blades against the sky along the hill's top.
    val grass = pick(night, 0xFF2A5E64, 0xFF183E48)
    for (k in 0 until 26) {
        val x = w * (k / 25f)
        val top = h * (0.60f - 0.11f * sin((k / 25f) * PI.toFloat() * 0.95f))
        drawLine(grass, Offset(x, top + h * 0.01f), Offset(x + w * 0.006f, top - h * 0.018f), w * 0.004f, StrokeCap.Round)
    }
    // A lone round tree on the left, black against the sky.
    val treeInk = pick(night, 0xFF1D3B48, 0xFF0E222C)
    drawLine(treeInk, Offset(w * 0.10f, h * 0.56f), Offset(w * 0.10f, h * 0.44f), w * 0.016f, StrokeCap.Round)
    drawLine(treeInk, Offset(w * 0.10f, h * 0.47f), Offset(w * 0.15f, h * 0.43f), w * 0.009f, StrokeCap.Round)
    val crown = Path().apply { addOval(Rect(Offset(w * 0.085f, h * 0.36f), w * 0.06f)); addOval(Rect(Offset(w * 0.13f, h * 0.38f), w * 0.045f)); addOval(Rect(Offset(w * 0.05f, h * 0.39f), w * 0.04f)) }
    drawPath(crown, treeInk)
}

/** What moves in the night sky: stars twinkling, a shooting star now and then, fireflies blinking over the hill. */
internal fun DrawScope.nightSkyLife(t: Float) {
    val w = size.width; val h = size.height
    val r = java.util.Random(23)
    repeat(42) {
        val x = r.nextFloat() * w; val y = r.nextFloat() * h * 0.46f
        val tw = 0.5f + 0.5f * sin((t * 9f + it * 0.41f) * 2f * PI.toFloat())
        drawCircle(Color.White, w * (0.0028f + 0.0026f * (it % 3)), Offset(x, y), alpha = 0.3f + 0.6f * tw)
    }
    // Four bigger sparkles.
    for ((k, at) in listOf(0.06f to 0.06f, 0.46f to 0.05f, 0.24f to 0.30f, 0.68f to 0.33f).withIndex()) {
        val c = Offset(w * at.first, h * at.second)
        val s = w * 0.014f * (0.7f + 0.3f * sin((t * 7f + k) * 2f * PI.toFloat()))
        drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), w * 0.0025f, StrokeCap.Round, alpha = 0.85f)
        drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), w * 0.0025f, StrokeCap.Round, alpha = 0.85f)
    }
    // A shooting star across the top, once a turn of the clock in a few.
    val f = (t * 4f) % 1f
    if (f < 0.12f) {
        val k = f / 0.12f
        val head = Offset(w * (0.15f + 0.55f * k), h * (0.04f + 0.12f * k))
        drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.9f)), head - Offset(w * 0.12f, h * 0.026f), head),
            head - Offset(w * 0.12f, h * 0.026f), head, w * 0.004f, StrokeCap.Round, alpha = 1f - k * 0.5f)
    }
    // Fireflies.
    for (k in 0 until 7) {
        val x = w * (0.08f + 0.13f * k) + sin((t * 6f + k) * 2f * PI.toFloat()) * w * 0.02f
        val y = h * (0.66f + 0.05f * ((k * 3) % 5)) + cos((t * 5f + k) * 2f * PI.toFloat()) * h * 0.01f
        val on = (0.5f + 0.5f * sin((t * 11f + k * 0.7f) * 2f * PI.toFloat())).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFF39A).copy(alpha = 0.35f * on), w * 0.016f, Offset(x, y))
        drawCircle(Color(0xFFFFF7C4), w * 0.0045f, Offset(x, y), alpha = 0.4f + 0.6f * on)
    }
}

/** Glowing stepping stones up the night hill, a faint path between them. */
internal fun DrawScope.drawGlowTrail(trail: Path, w: Float, night: Boolean) {
    drawPath(trail, Color(0xFFBFD8FF).copy(alpha = if (night) 0.12f else 0.16f), style = Stroke(w * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val gap = floatArrayOf(size.width * 0.001f, size.width * 0.06f)
    drawPath(trail, Color(0xFFFFF0B0).copy(alpha = 0.22f), style = Stroke(w * 0.95f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
    drawPath(trail, Color(0xFFFFF0B0).copy(alpha = 0.55f), style = Stroke(w * 0.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
    drawPath(trail, Color(0xFFFFFBEA), style = Stroke(w * 0.26f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
}

// ---- The fair ------------------------------------------------------------------------------------------------------------

/** The fair's still parts: the lawn, a striped tent, the carousel's canopy and base, the strongman's pole and bell, bunting. */
internal fun DrawScope.drawFair(night: Boolean) {
    val p = Palette(night)
    val w = size.width; val h = size.height
    val line = w * 0.006f
    ground(p, top = 0.555f)
    val red = pick(night, 0xFFE0605A, 0xFFA8463F)
    val cream = pick(night, 0xFFFFF4E0, 0xFFC9BCA6)
    val gold = pick(night, 0xFFF2B84B, 0xFFC4923A)
    // The tent: striped walls, a cone roof with scallops, the door open, a flag on top.
    val tentL = 0.02f; val tentR = 0.30f; val eave = 0.42f; val base = 0.585f
    for (k in 0 until 7) {
        val x0 = tentL + (tentR - tentL) * k / 7f; val x1 = tentL + (tentR - tentL) * (k + 1) / 7f
        drawRect(if (k % 2 == 0) red else cream, Offset(w * x0, h * eave), Size(w * (x1 - x0) + 1f, h * (base - eave)))
    }
    drawRect(Ink, Offset(w * tentL, h * eave), Size(w * (tentR - tentL), h * (base - eave)), style = Stroke(line))
    val door = Path().apply { moveTo(w * 0.12f, h * base); lineTo(w * 0.16f, h * 0.46f); lineTo(w * 0.20f, h * base); close() }
    outlined(door, pick(night, 0xFF5A2E2A, 0xFF2E1816), line)
    val roof = Path().apply { moveTo(w * (tentL - 0.015f), h * eave); lineTo(w * 0.16f, h * 0.27f); lineTo(w * (tentR + 0.015f), h * eave); close() }
    outlined(roof, red, line)
    for (k in 1 until 4) drawLine(cream, Offset(w * 0.16f, h * 0.27f), Offset(w * (tentL - 0.015f + (tentR - tentL + 0.03f) * k / 4f), h * eave), w * 0.012f)
    drawPath(roof, Ink, style = Stroke(line, join = StrokeJoin.Round))
    for (k in 0 until 6) {
        val x = w * (tentL - 0.015f) + w * (tentR - tentL + 0.03f) * (k + 0.5f) / 6f
        drawArc(if (k % 2 == 0) cream else red, 0f, 180f, true, Offset(x - w * 0.026f, h * eave - w * 0.018f), Size(w * 0.052f, w * 0.04f))
        drawArc(Ink, 0f, 180f, false, Offset(x - w * 0.026f, h * eave - w * 0.018f), Size(w * 0.052f, w * 0.04f), style = Stroke(line * 0.8f))
    }
    drawLine(Ink, Offset(w * 0.16f, h * 0.27f), Offset(w * 0.16f, h * 0.22f), line * 1.2f)
    outlined(Path().apply { moveTo(w * 0.16f, h * 0.22f); lineTo(w * 0.21f, h * 0.235f); lineTo(w * 0.16f, h * 0.25f); close() }, gold, line * 0.8f)

    // The carousel: the base, its poles (the horses ride them: fairLife), the canopy with its scallops and a flag.
    val cx = 0.51f
    outlined(Path().apply { addOval(Rect(w * 0.335f, h * 0.515f, w * 0.685f, h * 0.575f)) }, pick(night, 0xFF7A5AA0, 0xFF4E3A68), line)
    outlined(rr(0.335f, 0.50f, 0.685f, 0.545f, 0.01f), pick(night, 0xFF9C7CC4, 0xFF65508A), line)
    for (k in 0 until 9) drawCircle(gold, w * 0.006f, Offset(w * (0.35f + k * 0.04f), h * 0.522f))
    for (k in 0 until 4) {
        val x = w * (0.385f + k * 0.083f)
        drawLine(Ink, Offset(x, h * 0.275f), Offset(x, h * 0.50f), line * 2.2f)
        drawLine(gold, Offset(x, h * 0.275f), Offset(x, h * 0.50f), line * 1.1f)
    }
    val canopy = Path().apply { moveTo(w * 0.325f, h * 0.275f); lineTo(w * cx, h * 0.15f); lineTo(w * 0.695f, h * 0.275f); close() }
    outlined(canopy, pick(night, 0xFFFCD860, 0xFFC4A44A), line)
    for (k in 1 until 6 step 2) {
        val a = 0.325f + (0.695f - 0.325f) * k / 6f; val b = 0.325f + (0.695f - 0.325f) * (k + 1) / 6f
        drawPath(Path().apply { moveTo(w * cx, h * 0.15f); lineTo(w * a, h * 0.275f); lineTo(w * b, h * 0.275f); close() }, red)
    }
    drawPath(canopy, Ink, style = Stroke(line, join = StrokeJoin.Round))
    for (k in 0 until 8) {
        val x = w * 0.325f + w * 0.37f * (k + 0.5f) / 8f
        drawArc(if (k % 2 == 0) red else cream, 0f, 180f, true, Offset(x - w * 0.023f, h * 0.275f - w * 0.016f), Size(w * 0.046f, w * 0.036f))
        drawArc(Ink, 0f, 180f, false, Offset(x - w * 0.023f, h * 0.275f - w * 0.016f), Size(w * 0.046f, w * 0.036f), style = Stroke(line * 0.8f))
    }
    drawLine(Ink, Offset(w * cx, h * 0.15f), Offset(w * cx, h * 0.10f), line * 1.2f)
    outlined(Path().apply { moveTo(w * cx, h * 0.10f); lineTo(w * (cx + 0.05f), h * 0.115f); lineTo(w * cx, h * 0.13f); close() }, red, line * 0.8f)

    // The strongman: a tall pole with its scale, the bell at the top, the pad at the bottom and the mallet leaning by it.
    val px = 0.865f
    outlined(rr(px - 0.012f, 0.12f, px + 0.012f, 0.57f, 0.004f), cream, line)
    val bands = listOf(0xFF7CC173, 0xFFB5D86A, 0xFFFCD860, 0xFFF6A654, 0xFFE0605A)
    bands.forEachIndexed { i, c -> drawRect(Color(c), Offset(w * (px - 0.012f), h * (0.52f - i * 0.075f)), Size(w * 0.024f, h * 0.05f)) }
    drawRect(Ink, Offset(w * (px - 0.012f), h * 0.145f), Size(w * 0.024f, h * 0.425f), style = Stroke(line * 0.8f))
    val bell = Path().apply {
        moveTo(w * (px - 0.045f), h * 0.135f); quadraticTo(w * (px - 0.04f), h * 0.07f, w * px, h * 0.065f)
        quadraticTo(w * (px + 0.04f), h * 0.07f, w * (px + 0.045f), h * 0.135f); close()
    }
    outlined(bell, gold, line)
    drawCircle(Color.White.copy(alpha = 0.35f), w * 0.01f, Offset(w * (px - 0.016f), h * 0.09f))
    outlined(rr(px - 0.05f, 0.565f, px + 0.05f, 0.595f, 0.006f), red, line)
    drawLine(Ink, Offset(w * 0.79f, h * 0.59f), Offset(w * 0.765f, h * 0.43f), line * 2.4f, StrokeCap.Round)
    drawLine(pick(night, 0xFFC98B52, 0xFF8A5C36), Offset(w * 0.79f, h * 0.59f), Offset(w * 0.765f, h * 0.43f), line * 1.2f, StrokeCap.Round)
    rotate(-9f, Offset(w * 0.765f, h * 0.43f)) { drawPath(rr(0.735f, 0.41f, 0.795f, 0.445f, 0.006f), red); drawPath(rr(0.735f, 0.41f, 0.795f, 0.445f, 0.006f), Ink, style = Stroke(line)) }

    // Bunting from the tent's top to the carousel's.
    val from = Offset(w * 0.20f, h * 0.30f); val to = Offset(w * 0.36f, h * 0.25f)
    val flags = listOf(0xFF7DB8FF, 0xFFFCD860, 0xFFE0605A, 0xFF7CC173)
    drawLine(Ink, from, to, line * 0.6f)
    for (k in 0 until 4) {
        val f = (k + 0.5f) / 4f
        val a = Offset(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f + h * 0.012f * sin(f * PI.toFloat()))
        val tri = Path().apply { moveTo(a.x - w * 0.013f, a.y); lineTo(a.x + w * 0.013f, a.y); lineTo(a.x, a.y + w * 0.03f); close() }
        outlined(tri, Color(flags[k]), line * 0.6f)
    }
    // Down on the lawn: a little ticket booth, and a queue rope on posts.
    outlined(rr(0.78f, 0.74f, 0.88f, 0.86f, 0.006f), cream, line)
    for (k in 0 until 3) drawRect(red, Offset(w * (0.78f + k * 0.04f), h * 0.74f), Size(w * 0.02f, h * 0.12f))
    drawRect(Ink, Offset(w * 0.78f, h * 0.74f), Size(w * 0.10f, h * 0.12f), style = Stroke(line))
    outlined(rr(0.795f, 0.775f, 0.865f, 0.81f, 0.004f), pick(night, 0xFF3A2E40, 0xFF241C28), line * 0.8f)
    outlined(Path().apply { moveTo(w * 0.765f, h * 0.745f); lineTo(w * 0.83f, h * 0.70f); lineTo(w * 0.895f, h * 0.745f); close() }, gold, line)
    for (x in listOf(0.04f, 0.16f, 0.28f)) {
        drawLine(Ink, Offset(w * x, h * 0.86f), Offset(w * x, h * 0.80f), line * 2.2f, StrokeCap.Round)
        drawCircle(gold, w * 0.008f, Offset(w * x, h * 0.80f))
    }
    for (k in 0 until 2) drawArc(red, 10f, 160f, false, Offset(w * (0.04f + k * 0.12f), h * 0.79f), Size(w * 0.12f, h * 0.04f), style = Stroke(line * 1.6f))

    // At dusk the bulbs glow along the tent's eaves and the carousel's rim.
    if (night) for (k in 0 until 14) {
        val c = if (k < 6) Offset(w * (0.03f + k * 0.05f), h * eave) else Offset(w * (0.34f + (k - 6) * 0.046f), h * 0.275f)
        drawCircle(Color(0xFFFFE3A0).copy(alpha = 0.35f), w * 0.016f, c)
        drawCircle(Color(0xFFFFF1C8), w * 0.005f, c)
    }
}

/** What moves at the fair: the carousel's horses riding up and down, a bunch of balloons bobbing on their strings. */
internal fun DrawScope.fairLife(night: Boolean, t: Float) {
    val w = size.width; val h = size.height
    val line = w * 0.005f
    for (k in 0 until 4) {
        val x = w * (0.385f + k * 0.083f)
        val y = h * (0.40f + 0.035f * sin((t * 14f + k * 0.5f) * 2f * PI.toFloat()))
        val coat = if (night) Color(0xFFD9D2C6) else Color(0xFFFFFCF4)
        carouselHorse(Offset(x, y), Size(w * 0.06f, h * 0.034f), if (k % 2 == 0) 1f else -1f, coat,
            listOf(Color(0xFFE0605A), Color(0xFF7DB8FF), Color(0xFF7CC173), Color(0xFFAA8CDC))[k], line * 0.9f)
    }
    // The balloons, tied to a post by the strongman.
    val knot = Offset(w * 0.72f, h * 0.50f)
    drawLine(Ink, knot, knot + Offset(0f, h * 0.08f), line * 1.4f)
    val colours = listOf(Color(0xFFE0605A), Color(0xFF7DB8FF), Color(0xFFFCD860), Color(0xFF7CC173))
    for (k in 0 until 4) {
        val sway = sin((t * 10f + k * 0.8f) * 2f * PI.toFloat()) * w * 0.008f
        val c = Offset(w * (0.68f + 0.03f * k) + sway, h * (0.255f + 0.04f * abs(k - 1.5f)))
        drawLine(Ink.copy(alpha = 0.6f), knot, c + Offset(0f, w * 0.03f), line * 0.6f)
        drawOval(Ink, Offset(c.x - w * 0.024f, c.y - w * 0.03f), Size(w * 0.048f, w * 0.06f))
        drawOval(colours[k], Offset(c.x - w * 0.02f, c.y - w * 0.026f), Size(w * 0.04f, w * 0.052f))
        drawCircle(Color.White.copy(alpha = 0.5f), w * 0.006f, c + Offset(-w * 0.008f, -w * 0.012f))
    }
}

/**
 * A carousel horse at [c], its body [body] big, facing right ([face] 1) or left (-1): the back legs straight, the front
 * ones lifted, a [mane] and tail in colour, a saddle; outlined [line] wide in the scenes' brown. The fair's map scene and
 * its carousel stage both draw it.
 */
internal fun DrawScope.carouselHorse(c: Offset, body: Size, face: Float, coat: Color, mane: Color, line: Float) {
    val x = c.x; val y = c.y
    val bw = body.width; val bh = body.height
    for ((lx, lift) in listOf(-0.32f to 0f, -0.18f to 0f, 0.22f to 1f, 0.34f to 1f)) {
        val top = Offset(x + face * bw * lx, y + bh * 0.3f)
        val foot = top + Offset(face * bw * 0.12f * lift, bh * (0.95f - 0.35f * lift))
        drawLine(Ink, top, foot, line * 2.2f, StrokeCap.Round); drawLine(coat, top, foot, line * 1.2f, StrokeCap.Round)
    }
    drawLine(mane, Offset(x - face * bw * 0.48f, y - bh * 0.1f), Offset(x - face * bw * 0.68f, y + bh * 0.5f), line * 2f, StrokeCap.Round)
    drawRoundRect(Ink, Offset(x - bw / 2 - line, y - bh / 2 - line), Size(bw + 2 * line, bh + 2 * line), CornerRadius(bh / 2 + line))
    drawRoundRect(coat, Offset(x - bw / 2, y - bh / 2), body, CornerRadius(bh / 2))
    val neck0 = Offset(x + face * bw * 0.3f, y - bh * 0.15f); val neck1 = Offset(x + face * bw * 0.5f, y - bh * 1.05f)
    drawLine(Ink, neck0, neck1, bh * 0.62f + 2 * line, StrokeCap.Round); drawLine(coat, neck0, neck1, bh * 0.62f, StrokeCap.Round)
    val head = Offset(x + face * bw * 0.66f, y - bh * 0.95f)
    rotate(face * 28f, head) {
        drawOval(Ink, Offset(head.x - bw * 0.2f - line, head.y - bh * 0.22f - line), Size(bw * 0.4f + 2 * line, bh * 0.44f + 2 * line))
        drawOval(coat, Offset(head.x - bw * 0.2f, head.y - bh * 0.22f), Size(bw * 0.4f, bh * 0.44f))
    }
    drawCircle(Ink, line * 0.8f, head + Offset(face * bw * 0.02f, -bh * 0.08f))
    drawPath(Path().apply { moveTo(neck1.x - face * bw * 0.02f, neck1.y - bh * 0.25f); lineTo(neck1.x + face * bw * 0.06f, neck1.y - bh * 0.6f)
        lineTo(neck1.x + face * bw * 0.1f, neck1.y - bh * 0.2f); close() }, coat)
    for (j in 0 until 3) drawCircle(mane, bh * 0.16f, Offset(neck0.x + (neck1.x - neck0.x) * (0.2f + 0.3f * j) - face * bh * 0.28f, neck0.y + (neck1.y - neck0.y) * (0.2f + 0.3f * j)))
    drawRoundRect(mane, Offset(x - bw * 0.16f, y - bh * 0.55f), Size(bw * 0.32f, bh * 0.3f), CornerRadius(bh * 0.1f))
}

/** The fair's midway: a sandy path with little lights along its middle (glowing at dusk). */
internal fun DrawScope.drawMidwayTrail(trail: Path, w: Float, night: Boolean) {
    val p = Palette(night)
    drawPath(trail, p.line, style = Stroke(w + size.width * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(trail, pick(night, 0xFFF2DCAE, 0xFF8E7656), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val gap = floatArrayOf(size.width * 0.001f, size.width * 0.045f)
    if (night) drawPath(trail, Color(0xFFFFE3A0).copy(alpha = 0.35f), style = Stroke(w * 0.42f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(gap)))
    val bulbs = listOf(Color(0xFFE0605A), Color(0xFFFCD860), Color(0xFF7DB8FF))
    // Three strings of bulbs, offset along the path, so the colours take turns.
    bulbs.forEachIndexed { i, c ->
        drawPath(trail, c, style = Stroke(w * 0.18f, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.001f, size.width * 0.134f), size.width * 0.045f * i)))
    }
}
