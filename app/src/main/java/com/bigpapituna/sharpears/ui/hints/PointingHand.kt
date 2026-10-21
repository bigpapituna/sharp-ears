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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.motion.DarkStageHand
import com.bigpapituna.sharpears.ui.motion.HandPose
import com.bigpapituna.sharpears.ui.motion.drawPaintedHand
import com.bigpapituna.sharpears.ui.motion.handTip
import com.bigpapituna.sharpears.ui.motion.rememberHandFrames
import com.bigpapituna.sharpears.ui.reducedMotion

/**
 * Hint step b: the painted hand over the keyboard, [finger] (1 thumb .. 5, the note's fingering; the index finger when
 * unknown) tapping [midi] (the key wait mode is stuck on).
 * Placed like the player's ghost hand (fingertips up the keys, shortened to the keys' depth); the finger presses and
 * lifts every 0.7 s, with a soft spot of the hand colour on its tip. Still with animations off. Without the painted
 * frames (they failed to load) the spot alone marks the key. Draw it over the keyboard, the same size.
 */
@Composable
fun PointingHand(midi: Int, right: Boolean, lowMidi: Int, highMidi: Int, modifier: Modifier = Modifier, finger: Int = 2) {
    val f0 = finger.coerceIn(1, 5)
    val pose = HandPose.press(f0)
    val frames by rememberHandFrames()
    val still = reducedMotion()
    val tap by rememberInfiniteTransition(label = "point").animateFloat(0f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tap")
    val press = if (still) 1f else tap
    Canvas(modifier.clipToBounds().semantics { contentDescription = "The hand points at ${com.bigpapituna.sharpears.engine.spokenKey(midi)}" }) {
        if (midi !in lowMidi..highMidi || size.width <= 0f) return@Canvas
        val l = KeyboardLayout(lowMidi, highMidi, size.width, size.height)
        val k = l.rect(midi)
        val w = l.whiteWidth
        val h = size.height
        val drop = handTip(right, HandPose.REST, 1, f0, 0f, 0f, w).y
        val squash = (h * 0.5f / drop).coerceIn(0.4f, 1f)
        var ay = h * 0.86f - drop * squash
        if (k.black) ay -= h * 0.25f
        // Anchored on the pressing fingertip, which stays over the key's centre in every pose.
        val ax = k.centerX
        val colour = if (right) RightHandColor else LeftHandColor
        val f = frames
        if (f != null) drawPaintedHand(f, right, HandPose.REST, pose, press, f0, ax, ay, w, squash = squash, filter = DarkStageHand)
        val tip = if (f != null) handTip(right, HandPose.REST, pose, press, f0, f0, ax, ay, w, squash = squash)
            else Offset(k.centerX, k.touchY)
        drawCircle(colour, w * (0.3f + 0.14f * press), tip, alpha = 0.35f + 0.35f * press)
    }
}
