package com.bigpapituna.sharpears.ui.hints

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlin.math.abs

/**
 * The octave hint's arrow over the keyboard: from the key that was played ([expected] + 12 x [playedOctaveOffset],
 * the keyboard's edge when it is off the keys) to [expected], pointing down the keys ("try lower") or up. A ring
 * marks the right key; light runs along the arrow toward it every 1.2 s. Static with animations off. Draw it over the
 * keyboard, the same size (or over the lane's bottom edge; it only needs the key columns).
 */
@Composable
fun OctaveArrow(expected: Int, playedOctaveOffset: Int, lowMidi: Int, highMidi: Int, modifier: Modifier = Modifier, colour: Color = Accent) {
    val still = reducedMotion()
    val run by rememberInfiniteTransition(label = "octave").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "run")
    val label = "Arrow from ${noteName(expected + 12 * playedOctaveOffset)} to ${noteName(expected)}: " +
        if (playedOctaveOffset > 0) "one octave lower" else "one octave higher"
    Canvas(modifier.clipToBounds().semantics { contentDescription = label }) {
        if (size.width <= 0f || expected !in lowMidi..highMidi) return@Canvas
        val l = KeyboardLayout(lowMidi, highMidi, size.width, size.height)
        val w = l.whiteWidth
        val to = l.rect(expected).centerX
        val played = expected + 12 * playedOctaveOffset
        val from = if (played in lowMidi..highMidi) l.rect(played).centerX else if (playedOctaveOffset > 0) size.width - w / 2 else w / 2
        val y = size.height * 0.22f
        val dir = if (to < from) -1f else 1f
        val head = minOf(w * 0.9f, abs(to - from) * 0.4f).coerceAtLeast(w * 0.4f)
        val stroke = w * 0.22f
        // The shaft and the head, then a ring around the right key.
        drawLine(colour, Offset(from, y), Offset(to - dir * head * 0.6f, y), stroke, StrokeCap.Round, alpha = 0.9f)
        val tip = Path().apply {
            moveTo(to, y)
            lineTo(to - dir * head, y - head * 0.6f)
            lineTo(to - dir * head, y + head * 0.6f)
            close()
        }
        drawPath(tip, colour)
        drawCircle(colour, w * 0.42f, Offset(to, l.rect(expected).touchY), style = Stroke(stroke * 0.7f), alpha = 0.9f)
        if (!still) {
            val x = from + (to - from) * run
            drawCircle(Color.White, stroke * 0.9f, Offset(x, y), alpha = 0.55f * (1f - run))
        }
    }
}
