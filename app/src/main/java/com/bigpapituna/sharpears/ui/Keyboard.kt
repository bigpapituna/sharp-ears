package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.KeyRect
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.noteName

// Off-white and off-black keys (no pure white or black), slate edges to match the stage.
private val WhiteTop = Color(0xFFFDFDFE)
private val WhiteKey = Color(0xFFF0F2F5)
private val WhiteLow = Color(0xFFDCE0E6)
private val WhiteLip = Color(0xFFB6BDC7)
private val BlackTop = Color(0xFF3A404A)
private val BlackKey = Color(0xFF111317)
private val BlackFront = Color(0xFF4A515C)
private val KeyEdge = Color(0xFF0E1014)
private val KeyLabel = Color(0xFF5D6672)

/**
 * Multi-touch piano keyboard. Each finger maps to one key; sliding onto another key re-triggers.
 * [lit] keys are drawn in their color (pressed keys, or keys the song wants next).
 */
@Composable
fun Keyboard(
    lowMidi: Int,
    highMidi: Int,
    lit: Map<Int, Color>,
    onNoteOn: (midi: Int, velocity: Int) -> Unit,
    onNoteOff: (midi: Int) -> Unit,
    modifier: Modifier = Modifier,
    pulsing: Map<Int, Color> = emptyMap(),
    /** Text drawn near the bottom of a key, e.g. fingering numbers. */
    labels: Map<Int, String> = emptyMap(),
) {
    val text = rememberTextMeasurer()
    val labelCache = remember(text) { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
    // Read only inside the Canvas, so the pulse redraws the keyboard without recomposing the screen.
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "pulse")
    // The geometry follows both the size and the range (a transpose can change the range under a laid-out keyboard).
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val layout = remember(box, lowMidi, highMidi) {
        if (box.width == 0 || box.height == 0) null else KeyboardLayout(lowMidi, highMidi, box.width.toFloat(), box.height.toFloat())
    }
    val currentLayout by rememberUpdatedState(layout)
    val on by rememberUpdatedState(onNoteOn)
    val off by rememberUpdatedState(onNoteOff)

    Box(
        modifier
            .semantics { contentDescription = "Piano keyboard ${noteName(lowMidi)} to ${noteName(highMidi)}" }
            .onGloballyPositioned { c ->
                if (c.size != box) {
                    box = c.size
                    // Debug aid: window coordinates of each key, so adb taps hit known keys.
                    val l = KeyboardLayout(lowMidi, highMidi, c.size.width.toFloat(), c.size.height.toFloat())
                    val o = c.positionInWindow()
                    (l.whiteKeys + l.blackKeys).forEach {
                        Log.d(TAG, "keyRect ${noteName(it.midi)} = ${(o.x + it.centerX).toInt()},${(o.y + it.touchY).toInt()}")
                    }
                }
            }
            .pointerInput(lowMidi, highMidi) {
                awaitPointerEventScope {
                    val held = HashMap<PointerId, Int>()
                    while (true) {
                        val event = awaitPointerEvent()
                        val l = currentLayout ?: continue
                        for (change in event.changes) {
                            val key = if (change.pressed) l.keyAt(change.position.x, change.position.y) else null
                            val old = held[change.id]
                            if (key != old) {
                                // ponytail: two fingers on one key release it when either lifts; fine for a learning keyboard
                                if (old != null) { held.remove(change.id); off(old) }
                                if (key != null) { held[change.id] = key; on(key, 100) }
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val l = layout ?: return@Canvas
            val a = if (pulsing.isEmpty()) 1f else pulse.value
            drawRect(KeyEdge)                                  // shows through the gaps between keys
            val keyH = l.whiteKeys.firstOrNull()?.let { it.bottom - it.top } ?: size.height
            val whiteBody = Brush.verticalGradient(listOf(WhiteTop, WhiteKey, WhiteLow), startY = 0f, endY = keyH)
            l.whiteKeys.forEach { k -> drawWhiteKey(k, lit[k.midi], pulsing[k.midi]?.copy(alpha = a * 0.75f), whiteBody) }
            // The rail the keys tuck under: a soft shadow along the top edge.
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = keyH * 0.08f), size = Size(size.width, keyH * 0.08f))
            l.blackKeys.forEach { k -> drawBlackKey(k, lit[k.midi], pulsing[k.midi]?.copy(alpha = a * 0.85f)) }
            for ((midi, label) in labels) {
                if (midi !in lowMidi..highMidi) continue
                val k = l.rect(midi)
                val m = labelCache.getOrPut("$label|${k.black}") {
                    text.measure(label, TextStyle(color = if (k.black) Ink else KeyLabel, fontSize = 14.sp, fontFamily = Geist, fontWeight = FontWeight.SemiBold))
                }
                val lip = (k.bottom - k.top) * if (k.black) 0.12f else 0.07f
                // Centred on the key, but kept inside it (and the keyboard), so "C2" on the first key is never cut.
                val x = (k.centerX - m.size.width / 2f).coerceIn(minOf(k.left + 3f, size.width - m.size.width), maxOf(k.left + 3f, minOf(k.right - m.size.width - 3f, size.width - m.size.width - 3f)))
                drawText(m, topLeft = Offset(x, k.bottom - lip - m.size.height - 6f))
            }
        }
        // Remembered once; it reads the latest callbacks when TalkBack activates a key.
        val tap = remember { { m: Int -> on(m, 90); off(m) } }
        layout?.let { KeySemantics(it, tap) }
    }
}

/**
 * Invisible nodes over each key so TalkBack reads and plays key names ("C4", "F#3"). They take no touches;
 * the Canvas above handles fingers. Skips recomposition while the layout is unchanged.
 */
@Composable
private fun KeySemantics(l: KeyboardLayout, tap: (Int) -> Unit) {
    val density = LocalDensity.current
    (l.whiteKeys + l.blackKeys).forEach { k ->
        with(density) {
            Box(
                Modifier.offset { IntOffset(k.left.toInt(), k.top.toInt()) }
                    .size((k.right - k.left).toDp(), (k.bottom - k.top).toDp())
                    .clearAndSetSemantics {
                        contentDescription = noteName(k.midi)
                        role = Role.Button
                        onClick { tap(k.midi); true }
                    },
            )
        }
    }
}

/**
 * A white key with depth: a lit-from-above body, a darker front lip, and a press that sinks it (the lip
 * shrinks and the body moves down). [lit] is a pressed or sounding key's colour, [hint] a pulsing "play me".
 */
private fun DrawScope.drawWhiteKey(k: KeyRect, lit: Color?, hint: Color?, body: Brush) {
    val gap = 1.2f
    val x = k.left + gap; val w = k.right - k.left - 2 * gap
    val h = k.bottom - k.top
    val pressed = lit != null
    val lip = h * if (pressed) 0.03f else 0.07f
    val sink = if (pressed) h * 0.015f else 0f
    val r = CornerRadius(w * 0.10f)
    drawRoundRect(if (pressed) lit!!.darken(0.55f) else WhiteLip, Offset(x, k.top), Size(w, h), r)
    if (pressed) drawRoundRect(Brush.verticalGradient(listOf(lit!!.lighten(0.25f), lit), startY = k.top, endY = k.bottom), Offset(x, k.top + sink), Size(w, h - lip - sink), r)
    else drawRoundRect(body, Offset(x, k.top), Size(w, h - lip), r)
    hint?.let { drawRoundRect(it, Offset(x, k.top + sink), Size(w, h - lip - sink), r) }
}

/** A black key: drop shadow on the whites, glossy body, lighter sloped front, a faint top highlight. */
private fun DrawScope.drawBlackKey(k: KeyRect, lit: Color?, hint: Color?) {
    val x = k.left; val w = k.right - k.left; val h = k.bottom - k.top
    val pressed = lit != null
    val r = CornerRadius(w * 0.14f)
    drawRoundRect(Color.Black.copy(alpha = if (pressed) 0.25f else 0.40f), Offset(x + w * 0.10f, k.top), Size(w, h + h * 0.05f), r)
    val front = h * if (pressed) 0.06f else 0.12f
    val sink = if (pressed) h * 0.02f else 0f
    drawRoundRect(if (pressed) lit!!.darken(0.5f) else BlackFront, Offset(x, k.top), Size(w, h), r)
    drawRoundRect(
        if (pressed) Brush.verticalGradient(listOf(lit!!.lighten(0.15f), lit.darken(0.8f)), startY = k.top, endY = k.bottom)
        else Brush.verticalGradient(listOf(BlackTop, BlackKey), startY = k.top, endY = k.bottom),
        Offset(x, k.top + sink), Size(w, h - front - sink), r,
    )
    hint?.let { drawRoundRect(it, Offset(x, k.top + sink), Size(w, h - front - sink), r) }
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(x + w * 0.18f, k.top + h * 0.05f), Size(w * 0.64f, h * 0.30f), CornerRadius(w * 0.2f))
}

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)