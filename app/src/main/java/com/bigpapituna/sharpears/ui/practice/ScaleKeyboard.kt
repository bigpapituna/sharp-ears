package com.bigpapituna.sharpears.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.ui.GeistMono
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.reducedMotion

/*
 * The Scales screen's keyboard, drawn for this screen (the shared player keyboard has a 3D lip and labels sized
 * for play). Flat keys under a fallboard shadow, black keys proportioned and offset like a real piano, lit keys in
 * a soft gradient of the hand colour with the finger number in that colour, and a glow above a key while it
 * sounds. During a demo a finger dot glides from key to key, ghost-hand style.
 */

/** Black key centres relative to the gap between their white neighbours, in white-key widths (C sharp sits left, D sharp right...). */
private val BLACK_SHIFT = mapOf(1 to -0.09f, 3 to 0.09f, 6 to -0.11f, 8 to 0f, 10 to 0.11f)

/** Key geometry for [low]..[high] (white keys at both ends) in a [width] x [height] box below [top]. */
internal class PianoGeometry(val low: Int, val high: Int, val width: Float, val height: Float, val top: Float) {
    val whites = (low..high).filter { !isBlack(it) }
    val ww = width / whites.size
    val bw = ww * 0.58f
    val bh = height * 0.63f
    private val index = whites.withIndex().associate { it.value to it.index }

    fun rect(m: Int): Rect = if (!isBlack(m)) {
        val i = index.getValue(m)
        Rect(i * ww, top, (i + 1) * ww, top + height)
    } else {
        val gap = (index.getValue(m - 1) + 1) * ww
        val cx = gap + (BLACK_SHIFT[m % 12] ?: 0f) * ww
        Rect(cx - bw / 2, top, cx + bw / 2, top + bh)
    }

    fun contains(m: Int) = m in low..high

    fun keyAt(x: Float, y: Float): Int? {
        if (y < top || y > top + height) return null
        val black = (low..high).filter(::isBlack).firstOrNull { rect(it).let { r -> x >= r.left && x < r.right && y < r.bottom } }
        if (black != null) return black
        val i = (x / ww).toInt().coerceIn(0, whites.lastIndex)
        return whites[i]
    }
}

/** A finger dot in a demo: [midi] is under [finger] of [hand]. */
internal data class FingerDot(val midi: Int, val finger: Int)

private val WhiteTop = Color(0xFFFFFCF7)
private val WhiteBottom = Color(0xFFECE5DB)
private val Gap = Color(0xFF15110F)
private val BlackTop = Color(0xFF3A322C)
private val BlackBottom = Color(0xFF110E0C)

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, 1f)
private fun handColor(h: Hand) = if (h == Hand.RIGHT) RightHandColor else LeftHandColor

/** The number colour on a lit white key: deep enough to read on the tint. */
private fun numberInk(h: Hand) = if (h == Hand.RIGHT) RightHandColor.darken(0.5f) else LeftHandColor.darken(0.45f)

/**
 * [right] and [left]: the lit keys of each hand and their fingers. [sounding]: keys sounding now (a demo or a
 * touch) and the hand that plays them. [dots]: the demo's finger per hand (null when not playing). [labelRows]
 * puts the left hand's numbers a row above the right's (both hands shown). Touching a key plays it.
 */
@Composable
internal fun ScaleKeyboard(
    low: Int, high: Int,
    right: Map<Int, Int>, left: Map<Int, Int>,
    sounding: Map<Int, Hand>,
    dots: Map<Hand, FingerDot>,
    onNoteOn: (Int) -> Unit, onNoteOff: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val glow = with(density) { 16.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val geo = remember(low, high, size) {
        if (size.width == 0) null else PianoGeometry(low, high, size.width.toFloat(), size.height - glow, glow)
    }
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val still = reducedMotion()
    val both = right.isNotEmpty() && left.isNotEmpty()

    // Where each hand's number sits on a key: right at the bottom, left a row above it when both hands show.
    fun numberAt(g: PianoGeometry, m: Int, hand: Hand): Offset {
        val r = g.rect(m)
        val row = g.ww * (if (isBlack(m)) 0.5f else 0.62f)
        val up = if (both && hand == Hand.LEFT) g.ww * (if (isBlack(m)) 0.95f else 1.05f) else 0f
        return Offset(r.center.x, r.bottom - row - up)
    }

    // The gliding finger dots, one per hand.
    val dotPos = remember { mutableStateMapOf<Hand, Animatable<Offset, *>>() }
    val dotsNow by rememberUpdatedState(dots)
    for (hand in listOf(Hand.RIGHT, Hand.LEFT)) {
        val d = dots[hand]
        LaunchedEffect(d?.midi, geo) {
            val g = geo ?: return@LaunchedEffect
            if (d == null || !g.contains(d.midi)) { dotPos.remove(hand); return@LaunchedEffect }
            val target = numberAt(g, d.midi, hand)
            val a = dotPos.getOrPut(hand) { Animatable(target, Offset.VectorConverter) }
            if (still) a.snapTo(target) else a.animateTo(target, tween(150, easing = FastOutSlowInEasing))
        }
    }

    val onOn by rememberUpdatedState(onNoteOn)
    val onOff by rememberUpdatedState(onNoteOff)
    Canvas(modifier.onSizeChanged { size = it }.semantics { contentDescription = description }.pointerInput(geo) {
        val g = geo ?: return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            var key = g.keyAt(down.position.x, down.position.y)
            if (key == null) return@awaitEachGesture
            down.consume()
            onOn(key!!)
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val k = g.keyAt(ch.position.x, ch.position.y)
                if (k != key) { key?.let(onOff); key = k; k?.let(onOn) }
                ch.consume()
            }
            key?.let(onOff)
        }
    }) {
        val g = geo ?: return@Canvas
        val textCache = HashMap<String, TextLayoutResult>()
        val numberPx = (g.ww * 0.46f).coerceIn(9.dp.toPx(), 17.dp.toPx())
        fun label(s: String, px: Float): TextLayoutResult = textCache.getOrPut("$s|$px") {
            measurer.measure(s, TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = with(density) { px.toSp() }))
        }
        fun drawNumber(s: String, at: Offset, color: Color, px: Float = numberPx) {
            val l = label(s, px)
            drawText(l, color, Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
        }
        val drawn = dotsNow

        // Glows rise above the keys that sound now.
        for ((m, hand) in sounding) {
            if (!g.contains(m)) continue
            val r = g.rect(m)
            val c = handColor(hand)
            val bright = lerp(c, Color.White, 0.35f)
            val rad = g.ww * 1.4f
            scale(1f, 0.55f, Offset(r.center.x, glow)) {
                drawCircle(Brush.radialGradient(listOf(bright.copy(alpha = 0.75f), c.copy(alpha = 0.25f), c.copy(alpha = 0f)), Offset(r.center.x, glow), rad),
                    rad, Offset(r.center.x, glow))
            }
        }

        // The keybed behind the keys shows through as the gaps.
        drawRect(Gap, Offset(0f, glow), Size(size.width.toFloat(), g.height))
        val whiteR = CornerRadius(g.ww * 0.12f)
        val gapPx = 1.dp.toPx()
        for (m in g.whites) {
            val r = g.rect(m)
            val x = r.left + gapPx / 2; val w = r.width - gapPx
            drawPath(bottomRounded(Rect(x, r.top, x + w, r.bottom), whiteR), Brush.verticalGradient(listOf(WhiteTop, WhiteBottom), startY = r.top, endY = r.bottom))
            val hand = keyHand(m, right, left)
            if (hand != null) litWhite(Offset(x, r.top), Size(w, r.height), handColor(hand), whiteR, m in sounding)
        }
        // Fallboard: the keys run in under a soft shadow (and it hides their square tops).
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startY = glow, endY = glow + g.height * 0.09f),
            Offset(0f, glow), Size(size.width.toFloat(), g.height * 0.09f))
        for ((m, hand) in sounding) if (g.contains(m) && !isBlack(m)) {
            val r = g.rect(m)
            drawRect(lerp(handColor(hand), Color.White, 0.4f), Offset(r.left + gapPx, r.top), Size(r.width - gapPx * 2, 2.dp.toPx()))
        }
        for (m in (g.low..g.high).filter(::isBlack)) {
            val r = g.rect(m)
            val br = CornerRadius(g.bw * 0.16f)
            // A soft shadow to the lower right, then the key itself: no front lip, just a polished top.
            drawRoundRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), startX = r.right, endX = r.right + g.bw * 0.22f),
                Offset(r.right - 1f, r.top), Size(g.bw * 0.22f, r.height * 0.97f), br)
            val hand = keyHand(m, right, left)
            val on = m in sounding
            val c = hand?.let(::handColor)
            val brush = when {
                c == null -> Brush.verticalGradient(listOf(BlackTop, BlackBottom), startY = r.top, endY = r.bottom)
                on -> Brush.verticalGradient(listOf(c, lerp(c, Color.White, 0.3f)), startY = r.top, endY = r.bottom)
                else -> Brush.verticalGradient(listOf(lerp(BlackBottom, c, 0.55f), lerp(BlackBottom, c, 0.92f)), startY = r.top, endY = r.bottom)
            }
            drawPath(bottomRounded(r, br), brush)
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = if (c == null) 0.09f else 0.16f)),
                startY = r.top, endY = r.bottom), Offset(r.left + r.width * 0.2f, r.top + r.height * 0.1f), Size(r.width * 0.6f, r.height * 0.8f), CornerRadius(r.width * 0.3f))
        }
        // Finger numbers on every lit key, in the hand's colour; the finger dot covers the key it is on.
        fun numbers(map: Map<Int, Int>, hand: Hand) {
            for ((m, f) in map) {
                if (!g.contains(m) || drawn[hand]?.midi == m) continue
                val at = numberAt(g, m, hand)
                val ink = when {
                    m in sounding -> Color.White
                    isBlack(m) -> Color.White
                    else -> numberInk(hand)
                }
                drawNumber("$f", at, ink)
            }
        }
        numbers(right, Hand.RIGHT)
        numbers(left, Hand.LEFT)
        for ((hand, d) in drawn) {
            val a = dotPos[hand] ?: continue
            val c = handColor(hand)
            val rad = (g.ww * 0.42f).coerceAtMost(16.dp.toPx())
            drawCircle(c.copy(alpha = 0.28f), rad * 1.55f, a.value)
            drawCircle(c, rad, a.value)
            drawCircle(Color.White.copy(alpha = 0.9f), rad, a.value, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawNumber("${d.finger}", a.value, Color.White, numberPx * 0.95f)
        }
    }
}

private fun keyHand(m: Int, right: Map<Int, Int>, left: Map<Int, Int>): Hand? = when (m) {
    in right -> Hand.RIGHT
    in left -> Hand.LEFT
    else -> null
}

/** A lit white key: a soft wash of the hand colour, strongest at the front, with a glow low on the key. */
private fun DrawScope.litWhite(at: Offset, size: Size, c: Color, r: CornerRadius, sounding: Boolean) {
    val a = if (sounding) floatArrayOf(0.35f, 0.75f, 1f) else floatArrayOf(0.06f, 0.26f, 0.55f)
    val shape = bottomRounded(Rect(at, size), r)
    drawPath(shape, Brush.verticalGradient(0f to c.copy(alpha = a[0]), 0.55f to c.copy(alpha = a[1]), 1f to c.copy(alpha = a[2]),
        startY = at.y, endY = at.y + size.height))
    val glowAt = Offset(at.x + size.width / 2, at.y + size.height * 0.86f)
    drawPath(shape, Brush.radialGradient(listOf(Color.White.copy(alpha = if (sounding) 0.35f else 0.22f), Color.Transparent), glowAt, size.width * 0.9f))
}

/** A key's outline: square at the top (it runs under the fallboard), rounded at the front. */
private fun bottomRounded(r: Rect, c: CornerRadius) = Path().apply {
    addRoundRect(RoundRect(r, topLeft = CornerRadius.Zero, topRight = CornerRadius.Zero, bottomRight = c, bottomLeft = c))
}
