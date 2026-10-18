package com.bigpapituna.sharpears.ui.hints

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.ui.reducedMotion

/**
 * Hint step a: the keys wait mode is stuck on glow, breathing every 0.9 s, each in its hand colour ([keys]: midi to
 * colour). A soft fill and a brighter rim over the key, under the hand. The skin team's keyboard can take this over
 * (ui/hints/INTEGRATION.md); until then it is drawn over the keyboard, the same size. Steady with animations off.
 */
@Composable
fun KeyGlow(keys: Map<Int, Color>, lowMidi: Int, highMidi: Int, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val breath by rememberInfiniteTransition(label = "glow").animateFloat(0f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val b = if (still) 0.6f else breath
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        if (size.width <= 0f) return@Canvas
        val l = KeyboardLayout(lowMidi, highMidi, size.width, size.height)
        for ((m, c) in keys) {
            if (m !in lowMidi..highMidi) continue
            val k = l.rect(m)
            val inset = l.whiteWidth * 0.06f
            val r = CornerRadius(l.whiteWidth * 0.18f)
            val tl = Offset(k.left + inset, k.top + inset)
            val sz = Size(k.right - k.left - 2 * inset, k.bottom - k.top - 2 * inset)
            drawRoundRect(c, tl, sz, r, alpha = 0.18f + 0.22f * b)
            drawRoundRect(c, tl, sz, r, style = Stroke(l.whiteWidth * (0.08f + 0.05f * b)), alpha = 0.55f + 0.4f * b)
        }
    }
}
