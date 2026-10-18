package com.bigpapituna.sharpears.ui.playground

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.engine.world.Scenery as WorldScenery
import com.bigpapituna.sharpears.engine.world.Sticker
import com.bigpapituna.sharpears.engine.world.Stickers
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.LocalWorld
import com.bigpapituna.sharpears.ui.world.StickerImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The pond and the concert hall as the 2.2 stages draw them (the garden's stages draw their own dusk and night): the
 * water with its lily pads and reeds, the hall's stage with curtains and spotlights, the audience (the pond's frogs and
 * ducks, the hall's animals the kid collected), and the pictures on the new songs' cards. Code-drawn like the garden's
 * stages; the audience uses the world's sticker art.
 */

private val WaterTop = Color(0xFF16404F)
private val WaterBottom = Color(0xFF0B2630)
private val PadLight = Color(0xFF5FA046)
private val PadDark = Color(0xFF356B2B)
private val ReedGreen = Color(0xFF3F6E34)
private val Cattail = Color(0xFF6B4A2E)
private val HallTop = Color(0xFF1D1222)
private val HallBottom = Color(0xFF2A1A27)
private val Boards = Color(0xFF5B3B26)
private val BoardsDark = Color(0xFF3E2718)
private val CurtainRed = Color(0xFF8E2F2A)
private val CurtainDeep = Color(0xFF5C1C19)

/** The pond at dusk: deep water, slow rings drifting, reeds at both edges. [t] 0..1 loops. */
internal fun DrawScope.pondWater(t: Float) {
    drawRect(Brush.verticalGradient(listOf(WaterTop, WaterBottom)))
    // Rings on the water, widening and fading, each at its own spot.
    for (k in 0 until 4) {
        val p = (t + k * 0.25f) % 1f
        val c = Offset(size.width * (0.18f + 0.22f * k), size.height * (0.25f + 0.17f * ((k * 3) % 4)))
        val r = size.minDimension * (0.04f + 0.12f * p)
        drawOval(Color.White.copy(alpha = 0.10f * (1f - p)), Offset(c.x - r, c.y - r * 0.35f), Size(2 * r, 0.7f * r), style = Stroke(1.5.dp.toPx()))
    }
    // Reeds and cattails at the edges.
    for (side in listOf(0f, 1f)) for (k in 0 until 4) {
        val x = if (side == 0f) size.width * (0.02f + k * 0.025f) else size.width * (0.98f - k * 0.025f)
        val top = size.height * (0.30f + 0.08f * ((k * 5) % 3))
        val sway = sin((t * 2 * PI + k).toFloat()) * 4.dp.toPx()
        drawLine(ReedGreen, Offset(x, size.height), Offset(x + sway, top), 3.dp.toPx(), cap = StrokeCap.Round)
        if (k % 2 == 0) drawRoundRect(Cattail, Offset(x + sway - 3.dp.toPx(), top - 4.dp.toPx()), Size(6.dp.toPx(), 16.dp.toPx()), CornerRadius(3.dp.toPx()))
    }
}

/** A lily pad around [c], [r] wide, with its notch and a vein or two. */
internal fun DrawScope.lilyPad(c: Offset, r: Float, tilt: Float = 0f) {
    rotate(tilt, c) {
        val pad = Path().apply {
            moveTo(c.x, c.y)
            arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r * 0.45f, c.x + r, c.y + r * 0.45f), -60f, 330f, false)
            close()
        }
        drawPath(pad, Brush.radialGradient(listOf(PadLight, PadDark), c, r))
        drawPath(pad, Color.Black.copy(alpha = 0.25f), style = Stroke(1.5.dp.toPx()))
        for (a in listOf(150.0, 210.0, 270.0)) {
            val e = Offset(c.x + (r * 0.8f * cos(a * PI / 180)).toFloat(), c.y + (r * 0.36f * sin(a * PI / 180)).toFloat())
            drawLine(PadDark.copy(alpha = 0.8f), c, e, 1.2.dp.toPx())
        }
    }
}

/** The concert hall's stage: a dark hall, red curtains drawn back at both sides, the boards at the bottom. */
internal fun DrawScope.hallStage(floorFrom: Float = 0.78f) {
    drawRect(Brush.verticalGradient(listOf(HallTop, HallBottom)))
    val floorY = size.height * floorFrom
    drawRect(Brush.verticalGradient(listOf(Boards, BoardsDark), floorY, size.height), Offset(0f, floorY), Size(size.width, size.height - floorY))
    for (k in 1 until 8) drawLine(Color.Black.copy(alpha = 0.18f), Offset(size.width * k / 8f, floorY), Offset(size.width * (k / 8f - 0.5f) * 1.25f + size.width / 2, size.height), 1.dp.toPx())
    val w = size.width * 0.09f
    for (side in listOf(0, 1)) {
        val left = if (side == 0) 0f else size.width - w
        drawRect(Brush.horizontalGradient(listOf(CurtainRed, CurtainDeep, CurtainRed), left, left + w), Offset(left, 0f), Size(w, floorY))
    }
    // A thin valance along the top, its scallops clear of the stage's top row (the round stars, the beat dots).
    val v = 6.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(CurtainDeep, CurtainRed), 0f, v), Offset.Zero, Size(size.width, v))
    val n = (size.width / 22.dp.toPx()).toInt().coerceAtLeast(6)
    for (k in 0 until n) drawArc(CurtainRed, 0f, 180f, true, Offset(size.width * k / n, v - size.width / n / 2), Size(size.width / n, size.width / n * 0.8f))
}

/** A spotlight hung at [c]: its metal can and lens, and when [on] (0..1) a beam down to [beamTo] in [colour]. */
internal fun DrawScope.spotlight(c: Offset, r: Float, colour: Color, on: Float, beamTo: Float) {
    if (on > 0.15f) {
        val beam = Path().apply { moveTo(c.x - r * 0.6f, c.y + r * 0.6f); lineTo(c.x + r * 0.6f, c.y + r * 0.6f); lineTo(c.x + r * 2.2f, beamTo); lineTo(c.x - r * 2.2f, beamTo); close() }
        drawPath(beam, Brush.verticalGradient(listOf(colour.copy(alpha = 0.6f * on), colour.copy(alpha = 0.06f * on)), c.y, beamTo))
        drawOval(colour.copy(alpha = 0.4f * on), Offset(c.x - r * 2.2f, beamTo - r * 0.45f), Size(r * 4.4f, r * 0.9f))
    }
    // The can, hung from a bar, its lens facing down.
    drawLine(Color(0xFF8A7A6A), Offset(c.x, 0f), Offset(c.x, c.y - r), 2.dp.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF6B6460), Color(0xFF3A3532)), c.y - r, c.y + r), Offset(c.x - r * 0.8f, c.y - r), Size(r * 1.6f, r * 1.6f),
        CornerRadius(r * 0.35f))
    drawOval(Color(0xFF1E1A17), Offset(c.x - r * 0.75f, c.y + r * 0.35f), Size(r * 1.5f, r * 0.5f))
    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.2f + 0.8f * on), colour.copy(alpha = 0.35f + 0.65f * on)), Offset(c.x, c.y + r * 0.6f), r * 0.7f),
        Offset(c.x - r * 0.65f, c.y + r * 0.4f), Size(r * 1.3f, r * 0.42f))
}

/**
 * Who listens: the pond's frogs and ducks, or the concert hall's animals the kid collected (the world's audience; the
 * pond's friends sit in when none are collected yet). [cheer] makes them bounce. Decorative.
 */
@Composable
internal fun Audience(place: Place, modifier: Modifier = Modifier, size: Dp = 40.dp, cheer: Boolean = false, max: Int = 8) {
    val world = LocalWorld.current
    val friends = remember(place) { listOf(Stickers.ALL.first { it.id == "pond-1" }, Stickers.ALL.first { it.id == "pond-2" }) }
    val seats: List<Sticker> = when (place) {
        Place.CONCERT_HALL -> world?.state?.history?.owned?.let(WorldScenery::audience)?.takeIf { it.isNotEmpty() } ?: friends
        else -> friends
    }.let { s -> List(minOf(max, if (place == Place.CONCERT_HALL) maxOf(s.size, 2) else max)) { s[it % s.size] } }
    val still = reducedMotion()
    val clock = rememberInfiniteTransition(label = "audience")
    val bob by clock.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(if (cheer) 700 else 2600, easing = LinearEasing)), label = "bob")
    Row(modifier.semantics { contentDescription = if (place == Place.POND) "Frogs and ducks are listening" else "Your animal friends are listening" },
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        seats.forEachIndexed { i, s ->
            StickerImage(s, Modifier.size(size).graphicsLayer {
                if (!still) translationY = -(if (cheer) 8f else 2.5f) * density * ((sin(bob + i * 1.3f) + 1f) / 2f)
            }, tilt = (i % 3 - 1) * 5f, shine = false, shadow = false)
        }
    }
}

/**
 * A key's badge: a disc around [c] of radius [r] in the key's colour (cream without colour notes) with its letter, or
 * its animal before reading (the hunts' found keys, the chord's three keys).
 */
internal fun DrawScope.keyBadge(midi: Int, c: Offset, r: Float, reader: Boolean, colours: Boolean, measurer: TextMeasurer) {
    val face = if (colours) noteColour(midi) else BadgeCream
    val ink = if (colours) inkOn(midi) else Color(0xFF3A2A20)
    drawCircle(face, r, c)
    drawCircle(if (colours) Color.Black.copy(alpha = 0.35f) else Accent, r, c, style = Stroke(r * 0.18f))
    val animal = NoteColours.animal(midi)
    if (!reader && colours && animal != null) drawAnimal(animal, c, r * 1.42f, ink, face)
    else {
        val t = measurer.measure(NoteColours.letter(midi), TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (r * 0.8f / density).sp, color = ink))
        drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
    }
}

private val BadgeCream = Color(0xFFF6EBDD)

// ---- The new songs' pictures (the shelf and the pond's concert) --------------------------------------------------------

/** Draws [picture] for a song card. */
internal fun DrawScope.songPicture(picture: String) = when (picture) {
    "star" -> starPicture()
    "boat" -> boatPicture()
    "farm" -> farmPicture()
    "bridge" -> bridgePicture()
    "waves" -> wavesPicture()
    "hat" -> hatPicture()
    "buns" -> bunsPicture()
    "lamb" -> lambPicture()
    else -> fivePicture()
}

private fun DrawScope.starPicture() {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension * 0.42f
    drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.5f), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
    drawPath(star(c, r, inner = 0.48f), PupGold)
    drawCircle(Color(0xFF3A2A20), r * 0.07f, c + Offset(-r * 0.18f, -r * 0.05f))
    drawCircle(Color(0xFF3A2A20), r * 0.07f, c + Offset(r * 0.18f, -r * 0.05f))
    drawArc(Color(0xFF3A2A20), 20f, 140f, false, c + Offset(-r * 0.16f, -r * 0.02f), Size(r * 0.32f, r * 0.24f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
}

private fun DrawScope.boatPicture() {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2F6F8A), Color(0xFF1A4456))), Offset(0f, h * 0.62f), Size(w, h * 0.38f))
    val hull = Path().apply { moveTo(w * 0.12f, h * 0.55f); lineTo(w * 0.88f, h * 0.55f); lineTo(w * 0.74f, h * 0.74f); lineTo(w * 0.26f, h * 0.74f); close() }
    drawPath(hull, Color(0xFFC8693A))
    drawLine(Color(0xFF8A4A2A), Offset(w * 0.2f, h * 0.6f), Offset(w * 0.8f, h * 0.6f), w * 0.02f)
    drawLine(Color(0xFF6B4A2E), Offset(w * 0.3f, h * 0.5f), Offset(w * 0.08f, h * 0.8f), w * 0.03f, cap = StrokeCap.Round)
    drawLine(Color(0xFF6B4A2E), Offset(w * 0.7f, h * 0.5f), Offset(w * 0.92f, h * 0.8f), w * 0.03f, cap = StrokeCap.Round)
    for (k in 0 until 3) drawArc(Color.White.copy(alpha = 0.6f), 200f, 140f, false, Offset(w * (0.1f + k * 0.3f), h * 0.8f), Size(w * 0.2f, h * 0.1f), style = Stroke(w * 0.015f))
}

private fun DrawScope.farmPicture() {
    val w = size.width; val h = size.height
    drawRect(Good.copy(alpha = 0.8f), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
    drawRect(Accent, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.4f))
    val roof = Path().apply { moveTo(w * 0.16f, h * 0.44f); lineTo(w * 0.5f, h * 0.16f); lineTo(w * 0.84f, h * 0.44f); close() }
    drawPath(roof, Color(0xFF7A2E25))
    drawRect(Color(0xFFF7F2EA), Offset(w * 0.4f, h * 0.56f), Size(w * 0.2f, h * 0.26f))
    drawLine(Accent, Offset(w * 0.4f, h * 0.56f), Offset(w * 0.6f, h * 0.82f), w * 0.02f)
    drawLine(Accent, Offset(w * 0.6f, h * 0.56f), Offset(w * 0.4f, h * 0.82f), w * 0.02f)
    drawCircle(Color(0xFFF7F2EA), w * 0.05f, Offset(w * 0.5f, h * 0.34f))
}

private fun DrawScope.bridgePicture() {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2F6F8A), Offset(0f, h * 0.72f), Size(w, h * 0.28f))
    drawArc(Color(0xFFB9A58C), 180f, 180f, false, Offset(w * 0.08f, h * 0.36f), Size(w * 0.84f, h * 0.7f), style = Stroke(w * 0.07f))
    drawLine(Color(0xFFB9A58C), Offset(w * 0.04f, h * 0.42f), Offset(w * 0.96f, h * 0.42f), w * 0.06f, cap = StrokeCap.Round)
    for (k in 0 until 5) drawLine(Color(0xFF8C7A64), Offset(w * (0.2f + k * 0.15f), h * 0.42f), Offset(w * (0.2f + k * 0.15f), h * 0.62f), w * 0.025f)
}

private fun DrawScope.wavesPicture() {
    val w = size.width; val h = size.height
    for (k in 0 until 3) {
        val y = h * (0.35f + k * 0.18f)
        val p = Path().apply {
            moveTo(w * 0.05f, y)
            for (i in 0 until 4) relativeQuadraticTo(w * 0.1125f, -h * 0.12f, w * 0.225f, 0f)
        }
        drawPath(p, listOf(LeftHandColor, Color(0xFF7DB8FF), Color(0xFFB7E0FF))[k], style = Stroke(w * 0.045f, cap = StrokeCap.Round))
    }
    drawPath(Path().apply { moveTo(w * 0.62f, h * 0.18f); lineTo(w * 0.62f, h * 0.04f); lineTo(w * 0.8f, h * 0.16f); close() }, Accent)
}

private fun DrawScope.hatPicture() {
    val w = size.width; val h = size.height
    drawOval(Color(0xFF3B5B9A), Offset(w * 0.1f, h * 0.58f), Size(w * 0.8f, h * 0.16f))
    drawRoundRect(Color(0xFF3B5B9A), Offset(w * 0.28f, h * 0.3f), Size(w * 0.44f, h * 0.36f), CornerRadius(w * 0.08f))
    drawRect(Accent, Offset(w * 0.28f, h * 0.52f), Size(w * 0.44f, h * 0.07f))
    val feather = Path().apply { moveTo(w * 0.66f, h * 0.5f); quadraticTo(w * 0.98f, h * 0.2f, w * 0.86f, h * 0.06f); quadraticTo(w * 0.8f, h * 0.32f, w * 0.66f, h * 0.5f) }
    drawPath(feather, PupGold)
}

private fun DrawScope.bunsPicture() {
    val r = size.minDimension * 0.22f
    for ((dx, dy) in listOf(-0.55f to 0.25f, 0.55f to 0.25f, 0f to -0.3f)) {
        val c = Offset(size.width / 2 + dx * r * 1.6f, size.height / 2 + dy * r * 1.6f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE9B36A), Color(0xFFB9783A)), c, r), r, c)
        drawLine(Color(0xFFFFF4E0), c - Offset(r * 0.6f, 0f), c + Offset(r * 0.6f, 0f), r * 0.18f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFFF4E0), c - Offset(0f, r * 0.6f), c + Offset(0f, r * 0.6f), r * 0.18f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.lambPicture() {
    val w = size.width; val h = size.height
    for (k in 0 until 3) drawLine(Color(0xFF3A2A20), Offset(w * (0.36f + k * 0.14f), h * 0.6f), Offset(w * (0.36f + k * 0.14f), h * 0.86f), w * 0.06f, cap = StrokeCap.Round)
    for ((x, y, r) in listOf(Triple(0.4f, 0.5f, 0.17f), Triple(0.55f, 0.45f, 0.18f), Triple(0.68f, 0.52f, 0.15f), Triple(0.5f, 0.6f, 0.16f), Triple(0.33f, 0.4f, 0.13f)))
        drawCircle(Color(0xFFF7F2EA), w * r, Offset(w * x, h * y))
    drawOval(Color(0xFF3A2A20), Offset(w * 0.12f, h * 0.3f), Size(w * 0.24f, h * 0.22f))
    drawCircle(Color.White, w * 0.025f, Offset(w * 0.2f, h * 0.38f))
}

private fun DrawScope.fivePicture() {
    val w = size.width; val h = size.height
    val colors = listOf(Accent, PupGold, Good, LeftHandColor, Color(0xFFB98AD9))
    for (k in 0 until 5) {
        val x = w * (0.14f + k * 0.18f)
        drawRoundRect(Color(0xFFF7F2EA), Offset(x - w * 0.075f, h * 0.55f), Size(w * 0.15f, h * 0.4f), CornerRadius(w * 0.03f))
        drawCircle(colors[k], w * 0.07f, Offset(x, h * (0.3f + 0.08f * kotlin.math.abs(k - 2) / 2f)))
    }
}
