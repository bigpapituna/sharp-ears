package com.bigpapituna.sharpears.ui.motion

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The painted hand (art/hand, the owner's Gemini frames, cut by scripts/cut_hand.py): a soft-realistic hand from
 * above, fingers up the keys, sweater cuff toward the player. A pose is one frame; the hand is placed so a chosen
 * fingertip (as it sits in the resting frame) lands on a key, and sized so its fingers sit one white key apart.
 * Pose changes cross-fade; [PaintedHand] adds the spring glide for screens that are not driven by a scene clock.
 */

/** The painted poses. PRESS_n: the resting hand with finger n down (2 and 4 are made from the resting frame). */
enum class HandPose { LOOSE, BALL, REST, PRESS_1, PRESS_2, PRESS_3, PRESS_4, PRESS_5;
    companion object {
        /** The pose pressing [finger] (1 thumb .. 5); REST for anything else. */
        fun press(finger: Int) = when (finger) { 1 -> PRESS_1; 2 -> PRESS_2; 3 -> PRESS_3; 4 -> PRESS_4; 5 -> PRESS_5; else -> REST }
    }
}

/** Index into [HAND_FRAMES] / [HAND_ANCHORS]. */
private fun frameOf(pose: HandPose, right: Boolean) = (if (right) 0 else 8) + when (pose) {
    HandPose.LOOSE -> 0; HandPose.BALL -> 1; HandPose.REST -> 2; HandPose.PRESS_3 -> 3
    HandPose.PRESS_1 -> 4; HandPose.PRESS_5 -> 5; HandPose.PRESS_2 -> 6; HandPose.PRESS_4 -> 7
}

/** The decoded frames (right then left, as [HAND_FRAMES]) and the two soft shadows. Decoded once per process. */
class HandFrames internal constructor(internal val images: Array<ImageBitmap>, internal val shadows: Array<ImageBitmap>)

private object HandArt {
    @Volatile var frames: HandFrames? = null
    @Volatile var failed = false

    fun load(context: Context): HandFrames? {
        frames?.let { return it }
        if (failed) return null
        return try {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            fun decode(id: Int): ImageBitmap = BitmapFactory.decodeResource(context.resources, id, opts)!!.also { it.prepareToDraw() }.asImageBitmap()
            HandFrames(Array(HAND_FRAMES.size) { decode(HAND_FRAMES[it]) }, arrayOf(decode(R.drawable.hand_shadow_r), decode(R.drawable.hand_shadow_l)))
                .also { frames = it }
        } catch (e: Exception) {
            android.util.Log.w("SharpEars", "painted hand frames failed to load", e)
            failed = true
            null
        } catch (e: OutOfMemoryError) {
            android.util.Log.w("SharpEars", "painted hand frames: out of memory", e)
            failed = true
            null
        }
    }
}

/** The painted hand frames, decoded off the main thread the first time; null until then, or for good if they failed. */
@Composable
fun rememberHandFrames(): State<HandFrames?> {
    val context = LocalContext.current.applicationContext
    return produceState(HandArt.frames, context) { if (value == null) value = withContext(Dispatchers.IO) { HandArt.load(context) } }
}

/** True once loading the frames has failed (the caller draws its own hand instead). */
fun handFramesFailed() = HandArt.failed

/** The resting frame's finger spacing (index to ring), in frame widths: one white key at scale 1. */
private val SPACING = HAND_ANCHORS[2].let { (it.x[3] - it.x[1]) / 2f }

/** The hand frame's width in px when its fingers sit [whiteWidth] apart. */
internal fun handFrameWidth(whiteWidth: Float) = whiteWidth / SPACING

/** The hand frame's height in px for [whiteWidth]. */
internal fun handFrameHeight(whiteWidth: Float) = handFrameWidth(whiteWidth) / HAND_FRAME_ASPECT

/**
 * Where fingertip [finger] (1..5) of [pose] is when the hand is placed with [anchorFinger]'s tip over [anchorX] and,
 * as it sits in the resting frame, at [anchorY] (a pressing finger drops below it, toward the player). Each frame is
 * lined up on the anchor finger across the keys, since the painted left hand's thumb splays wider than the mirrored
 * presses'. [stretch] widens the hand across the keys (1 = fingers a white key apart).
 */
internal fun handTip(right: Boolean, pose: HandPose, finger: Int, anchorFinger: Int, anchorX: Float, anchorY: Float, whiteWidth: Float, stretch: Float = 1f, squash: Float = 1f): Offset {
    val rest = HAND_ANCHORS[frameOf(HandPose.REST, right)]
    val a = HAND_ANCHORS[frameOf(pose, right)]
    val w = handFrameWidth(whiteWidth) * stretch; val h = handFrameHeight(whiteWidth) * squash
    val i = finger - 1; val k = anchorFinger - 1
    return Offset(anchorX + (a.x[i] - a.x[k]) * w, anchorY + (a.y[i] - rest.y[k]) * h)
}

/** [handTip] between two poses, [mix] 0 at [from] and 1 at [to]. */
internal fun handTip(right: Boolean, from: HandPose, to: HandPose, mix: Float, finger: Int, anchorFinger: Int, anchorX: Float, anchorY: Float, whiteWidth: Float, stretch: Float = 1f, squash: Float = 1f): Offset {
    val a = handTip(right, from, finger, anchorFinger, anchorX, anchorY, whiteWidth, stretch, squash)
    if (mix <= 0f || from == to) return a
    val b = handTip(right, to, finger, anchorFinger, anchorX, anchorY, whiteWidth, stretch, squash)
    return Offset(a.x + (b.x - a.x) * mix, a.y + (b.y - a.y) * mix)
}

/** Where the wrist (the middle of the cuff's top) is, placed as in [handTip]. */
internal fun handWrist(right: Boolean, anchorFinger: Int, anchorX: Float, anchorY: Float, whiteWidth: Float, stretch: Float = 1f): Offset {
    val rest = HAND_ANCHORS[frameOf(HandPose.REST, right)]
    val w = handFrameWidth(whiteWidth) * stretch; val h = handFrameHeight(whiteWidth)
    val k = anchorFinger - 1
    return Offset(anchorX + (rest.x[5] - rest.x[k]) * w, anchorY + (rest.y[5] - rest.y[k]) * h)
}

/** A gentle darkening for a dark stage, where the frames' baked-in light would glow (null on a light one). */
internal val DarkStageHand by lazy { ColorFilter.lighting(Color(0xFFDCD6D2), Color.Black) }

/**
 * Draws the painted hand placed as in [handTip], cross-fading from [from] to [to] by [mix]. [lift] (0 on the keys,
 * 1 hovering) moves its soft shadow out and fades it; [alpha] is the whole hand's; [squash] shortens it up the keys
 * (a flat keyboard seen at a low angle). Allocates nothing.
 */
internal fun DrawScope.drawPaintedHand(
    frames: HandFrames, right: Boolean, from: HandPose, to: HandPose, mix: Float,
    anchorFinger: Int, anchorX: Float, anchorY: Float, whiteWidth: Float,
    stretch: Float = 1f, alpha: Float = 1f, lift: Float = 0f, filter: ColorFilter? = null, squash: Float = 1f,
) {
    if (alpha <= 0.01f) return
    val rest = HAND_ANCHORS[frameOf(HandPose.REST, right)]
    val w = handFrameWidth(whiteWidth) * stretch; val h = handFrameHeight(whiteWidth) * squash
    val k = anchorFinger - 1
    val top = anchorY - rest.y[k] * h
    val size = IntSize(w.roundToInt(), h.roundToInt())
    val left = anchorX - rest.x[k] * w
    // The soft shadow, down and to the right (the light is up left), further out and fainter as the hand lifts.
    val shadow = frames.shadows[if (right) 0 else 1]
    val sx = whiteWidth * (0.12f + 0.5f * lift); val sy = whiteWidth * (0.2f + 0.7f * lift)
    drawImage(shadow, IntOffset.Zero, IntSize(shadow.width, shadow.height), IntOffset((left + sx).roundToInt(), (top + sy).roundToInt()), size,
        alpha = alpha * (0.34f - 0.14f * lift))
    val m = mix.coerceIn(0f, 1f)
    val ta = frameOf(to, right); val fa = frameOf(from, right)
    val dstA = IntOffset((anchorX - HAND_ANCHORS[ta].x[k] * w).roundToInt(), top.roundToInt())
    val dstB = IntOffset((anchorX - HAND_ANCHORS[fa].x[k] * w).roundToInt(), top.roundToInt())
    val a = frames.images[ta]
    if (from == to || m >= 1f) { drawImage(a, IntOffset.Zero, IntSize(a.width, a.height), dstA, size, alpha = alpha, colorFilter = filter); return }
    val b = frames.images[fa]
    if (m <= 0f) { drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), dstB, size, alpha = alpha, colorFilter = filter); return }
    // Cross-fade without an offscreen layer: the new frame first, the old one over it, weighted so the overlap keeps
    // the hand's alpha (opaque: the old fades out over the new; translucent: a*m, then a(1-m)/(1-a*m)).
    val opaque = alpha >= 0.99f
    val aNew = if (opaque) 1f else alpha * m
    val aOld = if (opaque) 1f - m else alpha * (1f - m) / (1f - alpha * m)
    drawImage(a, IntOffset.Zero, IntSize(a.width, a.height), dstA, size, alpha = aNew, colorFilter = filter)
    drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), dstB, size, alpha = aOld, colorFilter = filter)
}

/**
 * The painted hand over a keyboard, for screens without a scene clock: it glides on a spring when [anchorX] (where
 * [anchorFinger]'s tip goes; [anchorY] down from the top of this box) changes, and cross-fades over 250 ms when
 * [pose] does. [whiteWidth] sizes it; [visible] fades it. Animations off: it jumps. Decorative.
 */
@Composable
fun PaintedHand(
    right: Boolean, anchorFinger: Int, anchorX: Float, anchorY: Float, whiteWidth: Float, pose: HandPose,
    modifier: Modifier = Modifier, alpha: Float = 1f, visible: Boolean = true, dark: Boolean = false,
) {
    val frames by rememberHandFrames()
    val still = reducedMotion()
    val x = remember { Animatable(anchorX) }
    val y = remember { Animatable(anchorY) }
    val shown = remember { Animatable(if (visible) 1f else 0f) }
    val mix = remember { Animatable(1f) }
    var from by remember { mutableStateOf(pose) }
    var to by remember { mutableStateOf(pose) }
    LaunchedEffect(anchorX, anchorY, still) {
        if (still) { x.snapTo(anchorX); y.snapTo(anchorY); return@LaunchedEffect }
        val s = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        launch { x.animateTo(anchorX, s) }
        y.animateTo(anchorY, s)
    }
    LaunchedEffect(pose, still) {
        if (pose == to) return@LaunchedEffect
        from = if (mix.value >= 0.5f) to else from
        to = pose
        if (still) mix.snapTo(1f) else { mix.snapTo(0f); mix.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(visible, still) {
        val want = if (visible) 1f else 0f
        if (still) shown.snapTo(want) else shown.animateTo(want, tween(if (visible) 250 else 400))
    }
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        val f = frames ?: return@Canvas
        drawPaintedHand(f, right, from, to, mix.value, anchorFinger, x.value, y.value, whiteWidth,
            alpha = alpha * shown.value, filter = if (dark) DarkStageHand else null)
    }
}
