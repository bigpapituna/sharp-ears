package com.bigpapituna.sharpears.ui.playground

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.DarkStageHand
import com.bigpapituna.sharpears.ui.motion.HandPose
import com.bigpapituna.sharpears.ui.motion.drawPaintedHand
import com.bigpapituna.sharpears.ui.motion.handTip
import com.bigpapituna.sharpears.ui.motion.rememberHandFrames
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlin.math.PI
import kotlin.math.sin

/*
 * 2.3: what the treehouse's and the night sky's stages share (CopyStage, TapStage, BeatStarsStage, HigherLowerStage,
 * LoudQuietStage, TempoStage, SkyChordStage, StarJamStage, their concerts, and ClapStage on those pages): the two
 * places' backdrops, the painted hand over a stage's keys and the finger badges.
 */

/** Says [text] the way a stage must: over the microphone when it listens (deaf meanwhile), as an ordinary line when not. */
internal fun Voice.sayOnStage(text: String, mic: MicInput) { if (mic.listening) speakOverMic(text) else speak(text) }

// ---- The places ----------------------------------------------------------------------------------------------------

/** The night sky behind a stage: the moon, twinkling stars, faint constellations, now and then a shooting star. */
@Composable
internal fun SkyBackdrop(modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val clock = rememberInfiniteTransition(label = "sky")
    val twinkle by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "twinkle")
    val drift by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(7_000, easing = LinearEasing)), label = "drift")
    Canvas(modifier.clearAndSetSemantics { }) { starrySky(if (still) 0.5f else twinkle, if (still) -1f else drift) }
}

/** The treehouse behind a stage: leaves, the trunk, lanterns swaying on their string, the planks of the platform. */
@Composable
internal fun TreeBackdrop(modifier: Modifier = Modifier, floorFrom: Float = 0.8f) {
    val still = reducedMotion()
    val sway by rememberInfiniteTransition(label = "tree").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5_200, easing = LinearEasing)), label = "sway")
    Canvas(modifier.clearAndSetSemantics { }) { treehouse(if (still) 0f else sway, floorFrom) }
}

/**
 * A page's backdrop behind a stage: the treehouse's and the night sky's move (the lanterns sway, the stars twinkle), the
 * other pages' are [placeBackdrop]'s still pictures. The garden's stages draw their own (nothing here).
 */
@Composable
internal fun PlaceBackdrop(place: Place, modifier: Modifier = Modifier, floorFrom: Float = 0.8f) = when (place) {
    Place.TREEHOUSE -> TreeBackdrop(modifier, floorFrom)
    Place.NIGHT_SKY -> SkyBackdrop(modifier)
    Place.GARDEN -> {}
    else -> Canvas(modifier.clearAndSetSemantics { }) { placeBackdrop(place, 0.3f, floorFrom) }
}

private val ConstellationInk = Color(0x33CFE0FF)

/** [night] with two constellations and, every few seconds ([drift] 0..1, negative for none), a shooting star. */
internal fun DrawScope.starrySky(twinkle: Float, drift: Float) {
    night(twinkle)
    // The Big Dipper, small, top left; a little kite under the moon.
    val dipper = listOf(0.08f to 0.10f, 0.15f to 0.13f, 0.21f to 0.12f, 0.27f to 0.15f, 0.29f to 0.24f, 0.37f to 0.25f, 0.37f to 0.15f)
    val kite = listOf(0.70f to 0.30f, 0.76f to 0.26f, 0.80f to 0.33f, 0.74f to 0.38f, 0.70f to 0.30f)
    for (shape in listOf(dipper, kite)) {
        val pts = shape.map { (x, y) -> Offset(size.width * x, size.height * y) }
        pts.zipWithNext { a, b -> drawLine(ConstellationInk, a, b, 1.dp(this)) }
        pts.forEach { drawCircle(Color.White.copy(alpha = 0.55f + 0.3f * twinkle), 1.6f * density, it) }
    }
    if (drift in 0f..0.18f) {
        val p = drift / 0.18f
        val head = Offset(size.width * (0.15f + 0.6f * p), size.height * (0.05f + 0.18f * p))
        val tail = head - Offset(size.width * 0.12f, size.height * 0.04f)
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - p))), tail, head), tail, head, 2.dp(this), cap = StrokeCap.Round)
    }
}

private fun Float.dp(scope: DrawScope) = this * scope.density
private fun Int.dp(scope: DrawScope) = this * scope.density

private val TreeSkyTop = Color(0xFF243552)
private val TreeSkyBottom = Color(0xFF4A3F5C)
private val LeafDark = Color(0xFF26422A)
private val LeafMid = Color(0xFF35603A)
private val LeafLight = Color(0xFF4E7F45)
private val Bark = Color(0xFF5A3B24)
private val BarkDark = Color(0xFF3B2616)
private val Plank = Color(0xFF8A5A36)
private val PlankDark = Color(0xFF5E3A20)
private val Rope = Color(0xFFC7A46C)
private val Lantern = Color(0xFFFFC66B)

/** The treehouse at dusk: [sway] (radians) moves the lanterns and the ladder; the planks start at [floorFrom] of the height. */
internal fun DrawScope.treehouse(sway: Float, floorFrom: Float = 0.8f) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(TreeSkyTop, TreeSkyBottom)))
    // The trunk on the left, its bark in long strokes.
    val trunkW = w * 0.11f
    drawRect(Brush.horizontalGradient(listOf(BarkDark, Bark, BarkDark), 0f, trunkW), Offset.Zero, Size(trunkW, h))
    for (k in 0 until 5) {
        val x = trunkW * (0.2f + 0.15f * k)
        drawLine(BarkDark.copy(alpha = 0.6f), Offset(x, h * 0.05f * k), Offset(x + trunkW * 0.05f, h * (0.5f + 0.1f * k)), 1.5f * density)
    }
    // Leaves along the top: clusters of round leaves, darker behind, lighter in front.
    val leaves = listOf(Triple(0.02f, 0.02f, 0.16f), Triple(0.18f, -0.02f, 0.13f), Triple(0.34f, 0.01f, 0.12f), Triple(0.52f, -0.03f, 0.14f),
        Triple(0.70f, 0.0f, 0.12f), Triple(0.88f, -0.02f, 0.16f), Triple(1.0f, 0.05f, 0.14f))
    for ((i, l) in leaves.withIndex()) {
        val c = Offset(w * l.first, h * l.second)
        val r = minOf(w, h) * l.third
        drawCircle(LeafDark, r * 1.15f, c + Offset(0f, r * 0.15f))
        drawCircle(Brush.radialGradient(listOf(LeafLight, LeafMid), c - Offset(r * 0.3f, r * 0.3f), r * 1.2f), r, c)
        if (i % 2 == 0) drawCircle(LeafLight.copy(alpha = 0.5f), r * 0.35f, c + Offset(r * 0.35f, r * 0.25f))
    }
    // A string of lanterns across, swaying a little.
    val y0 = h * 0.2f
    val string = Path().apply { moveTo(trunkW, y0); quadraticTo(w * 0.55f, y0 + h * 0.08f, w, y0 - h * 0.02f) }
    drawPath(string, Rope.copy(alpha = 0.6f), style = Stroke(1.5f * density))
    for (k in 0 until 5) {
        val t = (k + 0.5f) / 5f
        val x = trunkW + (w - trunkW) * t
        val y = y0 + h * 0.08f * 4 * t * (1 - t) - h * 0.02f * t + 6 * density
        val dx = sin(sway + k) * 3 * density
        val c = Offset(x + dx, y + 10 * density)
        drawCircle(Brush.radialGradient(listOf(Lantern.copy(alpha = 0.45f), Color.Transparent), c, 26 * density), 26 * density, c)
        drawLine(Rope, Offset(x, y), c - Offset(0f, 7 * density), 1.dp(this))
        drawRoundRect(Lantern, c - Offset(5 * density, 7 * density), Size(10 * density, 14 * density), CornerRadius(4 * density))
        drawLine(Color(0xFFB0701F), c - Offset(5 * density, 0f), c + Offset(5 * density, 0f), 1.dp(this))
    }
    // The rope ladder on the right, hanging from the leaves.
    val lx = w * 0.93f + sin(sway * 0.5f) * 2 * density
    for (side in listOf(-1f, 1f)) drawLine(Rope, Offset(lx + side * 9 * density, h * 0.1f), Offset(lx + side * 9 * density, h * floorFrom), 2.dp(this))
    var ry = h * 0.16f
    while (ry < h * floorFrom - 6 * density) { drawLine(Rope, Offset(lx - 9 * density, ry), Offset(lx + 9 * density, ry), 2.dp(this)); ry += 16 * density }
    // The platform's planks.
    val floorY = h * floorFrom
    drawRect(Brush.verticalGradient(listOf(Plank, PlankDark), floorY, h), Offset(0f, floorY), Size(w, h - floorY))
    var py = floorY
    while (py < h) { drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, py), Offset(w, py), 1.5f * density); py += 14 * density }
    for (k in 0 until 9) drawCircle(Color.Black.copy(alpha = 0.35f), 1.5f * density, Offset(w * (k + 0.5f) / 9, floorY + 7 * density))
}

// ---- The painted hand over a stage's keys -----------------------------------------------------------------------------

/**
 * What the painted hand does over a stage's keys: the [right] or left hand with [finger] over [key], pressing when
 * [pressed], its number shown on the fingertip when [number].
 */
internal data class HandShow(val right: Boolean, val finger: Int, val key: Int, val pressed: Boolean, val number: Boolean = true)

/**
 * The painted hand over a stage's keys ([BigKeys] from [low] to [high] in the same box, [topRoom] above them): placed
 * like the player's ghost hand (fingertips up the keys, shortened to their depth), gliding on a spring from key to key,
 * the pressing finger landing on its key (reaching in for a black one), its number on the tip. Null hides it. Decorative.
 */
@Composable
internal fun HandOverKeys(low: Int, high: Int, show: HandShow?, modifier: Modifier = Modifier, topRoom: Float = 0f) {
    val frames by rememberHandFrames()
    val still = reducedMotion()
    val x = remember { Animatable(Float.NaN) }
    val press = remember { Animatable(0f) }
    var target by remember { mutableStateOf<HandShow?>(null) }
    val shown = remember { Animatable(0f) }
    var boxW by remember { mutableFloatStateOf(0f) }
    var boxH by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(show) { if (show != null) target = show }
    LaunchedEffect(show == null, still) { val want = if (show == null) 0f else 1f; if (still) shown.snapTo(want) else shown.animateTo(want, tween(300)) }
    LaunchedEffect(show?.pressed, show?.key, still) {
        val want = if (show?.pressed == true) 1f else 0f
        if (still) press.snapTo(want) else press.animateTo(want, tween(if (want > 0f) 110 else 220, easing = FastOutSlowInEasing))
    }
    // Where the pressing fingertip goes, gliding there.
    LaunchedEffect(target, boxW, boxH, still) {
        val t = target ?: return@LaunchedEffect
        if (boxW <= 0f) return@LaunchedEffect
        val g = PianoGeometry(low, high, boxW, boxH - topRoom, topRoom)
        val kx = g.rect(t.key.coerceIn(low, high)).center.x
        // Quick enough to keep up with a phrase played a note every half second.
        if (x.value.isNaN() || still) x.snapTo(kx) else x.animateTo(kx, spring(dampingRatio = 0.85f, stiffness = 900f))
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(modifier.clipToBounds().onSizeChanged { boxW = it.width.toFloat(); boxH = it.height.toFloat() }.clearAndSetSemantics { }) {
        val t = target ?: return@Canvas
        val f = frames ?: return@Canvas
        if (x.value.isNaN() || shown.value <= 0.01f) return@Canvas
        val g = PianoGeometry(low, high, size.width, size.height - topRoom, topRoom)
        val w = g.ww
        val h = g.height
        val pose = HandPose.press(t.finger)
        val p = press.value
        val drop = handTip(t.right, HandPose.REST, 1, 3, 0f, 0f, w).y
        val squash = (h * 0.5f / drop).coerceIn(0.4f, 1f)
        var ay = g.top + h * 0.86f - drop * squash
        if (isBlack(t.key)) ay -= h * 0.28f
        // Anchored on finger three, moved so the pressing finger sits over its key in the pressing pose.
        val ax = x.value - (handTip(t.right, pose, t.finger, 3, 0f, ay, w, squash = squash).x)
        drawPaintedHand(f, t.right, HandPose.REST, pose, p, 3, ax, ay, w, alpha = shown.value, filter = DarkStageHand, squash = squash)
        if (t.number) {
            val tip = handTip(t.right, HandPose.REST, pose, p, t.finger, 3, ax, ay, w, squash = squash)
            fingerBadge(t.finger, tip, minOf(w * 0.3f, 13f * density), if (t.right) RightHandColor else LeftHandColor, lit = p > 0.3f, measurer = measurer,
                alpha = shown.value)
        }
    }
}

/** A finger's number in a round badge at [c] (on a fingertip, or on the front of a key). */
internal fun DrawScope.fingerBadge(finger: Int, c: Offset, r: Float, colour: Color, lit: Boolean, measurer: TextMeasurer, alpha: Float = 1f) {
    drawCircle(Color.Black.copy(alpha = 0.25f * alpha), r * 1.1f, c + Offset(0f, 1.2f * density))
    drawCircle(if (lit) colour else Color.White, r, c, alpha = alpha)
    drawCircle(colour, r, c, style = Stroke(1.5f * density), alpha = alpha)
    val l = measurer.measure("$finger", TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = (r * 1.15f / density).sp,
        color = if (lit) Color.White else Color(0xFF3A2A1E)))
    drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f), alpha = alpha)
}

/** The finger numbers of [fingers] (key to finger) on the fronts of their keys in [g]; [lit] keys' badges in the hand colour. */
internal fun DrawScope.keyFingers(g: PianoGeometry, fingers: Map<Int, Int>, right: (Int) -> Boolean, lit: Set<Int>, measurer: TextMeasurer, pulse: Float = 0f,
                                  next: Int? = null) {
    for ((k, f) in fingers) {
        if (k !in g.low..g.high) continue
        val r = g.rect(k)
        val c = Offset(r.center.x, r.bottom - minOf(r.width * 0.55f, 18f * density) - if (isBlack(k)) 0f else r.height * 0.04f)
        val rad = minOf(r.width * 0.36f, 12f * density) * (if (k == next) 1f + 0.12f * pulse else 1f)
        fingerBadge(f, c, rad, if (right(k)) RightHandColor else LeftHandColor, lit = k in lit, measurer = measurer)
    }
}

/**
 * The keyboard a stage shows for [keys]: a couple of keys either side, white at both ends, and at least [whites] white
 * keys, so a painted hand over them stays a hand's size (a stage of three keys would make it a giant's).
 */
internal fun stageRange(keys: Collection<Int>, whites: Int = 9): Pair<Int, Int> {
    var low = com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow(keys.min() - 2)
    var high = com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove(keys.max() + 2)
    var down = true
    while ((low..high).count { !isBlack(it) } < whites) {
        if (down) low = com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow(low - 1) else high = com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove(high + 1)
        down = !down
    }
    return low to high
}

/** A soft gold glow behind a key a round asks for. */
internal fun DrawScope.keyGlow(g: PianoGeometry, key: Int, strength: Float) {
    if (key !in g.low..g.high) return
    val r = g.rect(key)
    drawRoundRect(PupGold.copy(alpha = 0.25f + 0.35f * strength), Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(g.ww * 0.14f))
}
