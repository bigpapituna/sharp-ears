package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.LaneGeometry
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.isBlack

val LaneBackground = Color(0xFF101014)
private val LaneGuide = Color(0xFF1E1E26)
private val HitLine = Color(0xFFE8E8F0)

/**
 * Falling notes for [song] at [positionMs]. Columns line up with a [Keyboard] of the same width and range.
 * Vertical drags scrub: dragging down pulls the notes toward the hit line, which moves time forward.
 */
@Composable
fun NoteLane(
    song: Song,
    positionMs: Double,
    lowMidi: Int,
    highMidi: Int,
    onScrubStart: () -> Unit,
    onScrub: (deltaMs: Double) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val longest = remember(song) { song.notes.maxOfOrNull { it.durationMs } ?: 0L }
    val scrubDelta by rememberUpdatedState(onScrub)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrubEnd by rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { scrubStart() },
                onDragEnd = { scrubEnd() },
                onDragCancel = { scrubEnd() },
            ) { change, dy ->
                change.consume()
                scrubDelta(dy / LaneGeometry(size.height.toFloat()).pxPerMs.toDouble())
            }
        }
    ) {
        drawRect(LaneBackground)
        val keys = KeyboardLayout(lowMidi, highMidi, size.width, size.height)
        val lane = LaneGeometry(size.height)
        // Faint guides at each C so the eye can find octaves.
        keys.whiteKeys.filter { it.midi % 12 == 0 }.forEach {
            drawLine(LaneGuide, Offset(it.left, 0f), Offset(it.left, size.height), strokeWidth = 2f)
        }
        for (n in lane.visible(song, positionMs, longest)) {
            if (n.midi !in lowMidi..highMidi) continue
            val k = keys.rect(n.midi)
            val inset = if (isBlack(n.midi)) 2f else (k.right - k.left) * 0.08f
            val top = lane.topY(n, positionMs) + 3f          // gap so repeated notes on one key stay separate
            val bottom = lane.bottomY(n, positionMs)
            val base = if (n.hand == Hand.RIGHT) RightHandColor else LeftHandColor
            val color = if (isBlack(n.midi)) base.copy(red = base.red * 0.8f, green = base.green * 0.8f, blue = base.blue * 0.8f) else base
            drawRoundRect(color, Offset(k.left + inset, top), Size(k.right - k.left - 2 * inset, bottom - top), CornerRadius(8f))
        }
        drawLine(HitLine, Offset(0f, lane.hitLineY - 2f), Offset(size.width, lane.hitLineY - 2f), strokeWidth = 4f)
    }
}
