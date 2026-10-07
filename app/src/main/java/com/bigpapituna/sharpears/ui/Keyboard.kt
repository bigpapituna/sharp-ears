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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.KeyRect
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.noteName

val RightHandColor = Color(0xFFFF9F43)
val LeftHandColor = Color(0xFF4DA3FF)

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
) {
    // Read only inside the Canvas, so the pulse redraws the keyboard without recomposing the screen.
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "pulse")
    var layout by remember { mutableStateOf<KeyboardLayout?>(null) }
    val on by rememberUpdatedState(onNoteOn)
    val off by rememberUpdatedState(onNoteOff)

    Box(
        modifier
            .semantics { contentDescription = "Piano keyboard ${noteName(lowMidi)} to ${noteName(highMidi)}" }
            .onGloballyPositioned { c ->
                val l = KeyboardLayout(lowMidi, highMidi, c.size.width.toFloat(), c.size.height.toFloat())
                if (layout?.width != l.width || layout?.height != l.height) {
                    layout = l
                    // Debug aid: window coordinates of each key, so adb taps hit known keys.
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
                        val l = layout ?: continue
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
            fun fill(midi: Int, default: Color) = lit[midi] ?: pulsing[midi]?.copy(alpha = a)?.compositeOver(default) ?: default
            l.whiteKeys.forEach { drawKey(it, fill(it.midi, Color.White), Color(0xFF2A2A2E)) }
            l.blackKeys.forEach { drawKey(it, fill(it.midi, Color(0xFF1B1B1F)), Color.Black) }
        }
    }
}

private fun DrawScope.drawKey(k: KeyRect, fill: Color, edge: Color) {
    val topLeft = Offset(k.left, k.top)
    val size = Size(k.right - k.left, k.bottom - k.top)
    val r = CornerRadius(size.width * 0.12f)
    drawRoundRect(fill, topLeft, size, r)
    drawRoundRect(edge, topLeft, size, r, style = Stroke(width = 2f))
}
