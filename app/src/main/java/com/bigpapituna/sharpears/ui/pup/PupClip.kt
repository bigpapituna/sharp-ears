package com.bigpapituna.sharpears.ui.pup

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.MutableState
import com.bigpapituna.sharpears.engine.pup.PupMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The pup's mini clips (2.10): short animations (animated WebP, 318 x 400, 12 fps, with alpha, ground line row 380) in
 * assets/pup/<name>.webp, played by the platform's AnimatedImageDrawable (API 28, the app's minSdk). All five are Google Veo
 * videos of the pup cut to loops (C:\dev\tools\pup-veo, judged the best of three sources): [DANCE] loops on the kid
 * finale and the concerts' bow, [SLEEP] on the goodnight screen ([PupClip]); [JUMP] is the HAPPY of a pup with `clips` on
 * (three stars, a sticker revealed or placed, the surprise opened); [WAVE] his hello on the kid home and his call while a
 * surprise waits; [CELEBRATE] plays once after a lesson and on "Done for today", holding its last frame (its loop's wrap
 * jumps from a laughing sit to a calm one). A clip that is not there falls back to the rig; with animations off it is
 * always the rig, holding still. A clip never takes a touch and never holds up the screen around it.
 */
object PupClips {
    const val JUMP = "jump"
    const val DANCE = "dance"
    const val WAVE = "wave"
    const val SLEEP = "sleep"
    const val CELEBRATE = "celebrate"
    const val DIR = "pup"
    /** Every clip the app ships (PupWiringTest: these and nothing else in assets/pup). */
    val ALL = listOf(JUMP, DANCE, WAVE, SLEEP, CELEBRATE)

    /**
     * All five share one canvas and one ground line. [STAND_H] canvas px map to the pup's box, so the clips' heads (a 116 px
     * headband) come out 0.38 of the box, as the stills' do; feet on row [FEET].
     */
    const val CANVAS_W = 318f
    const val CANVAS_H = 400f
    const val FEET = 380f
    const val STAND_H = 306f
    /** The jump (drawn small so the leap stays on the canvas) and the sleep (a lying pup, width-limited) are about 15% smaller. */
    const val JUMP_SCALE = 1.15f
    const val SLEEP_SCALE = 1.15f
    /** A clip that never reports its end is let go after this long a time through (the longest, the wave, is 3.7 s). */
    const val MAX_LOOP_MS = 4_500L

    /** How much [name]'s view scales him up so his head matches the others'. */
    fun scale(name: String): Float = when (name) { JUMP -> JUMP_SCALE; SLEEP -> SLEEP_SCALE; else -> 1f }

    /** Whether assets/pup/<[name]>.webp is in the app. */
    fun has(context: Context, name: String): Boolean = runCatching { context.assets.list(DIR)?.contains("$name.webp") == true }.getOrDefault(false)

    /** The clip, decoded and ready to start; null when it is missing or not an animation. */
    fun load(context: Context, name: String): AnimatedImageDrawable? = runCatching {
        if (!has(context, name)) return null
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, "$DIR/$name.webp")) as? AnimatedImageDrawable
    }.onFailure { Log.d(TAG, "pup clip $name: could not decode ($it)") }.getOrNull()
}

/** A clip shown in a pup's place, and how much its view scales him ([PupClips.scale]). */
internal class ShownClip(val drawable: AnimatedImageDrawable, val scale: Float)

private sealed interface ClipState {
    data object Loading : ClipState
    data object Missing : ClipState
    class Ready(val drawable: AnimatedImageDrawable) : ClipState
}

/**
 * [d] in a [size] box the way the rig draws a pup there: as tall as the box when he stands, feet on its bottom edge (a
 * jump reaches above it). The view takes no touches.
 */
@Composable
internal fun ClipView(d: AnimatedImageDrawable, size: Dp, modifier: Modifier = Modifier, scale: Float = 1f) {
    val k = size / PupClips.STAND_H * scale
    Box(modifier.size(size), contentAlignment = Alignment.BottomCenter) {
        AndroidView(factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_XY; setImageDrawable(d); isClickable = false } },
            update = { v -> if (v.drawable !== d) v.setImageDrawable(d) },
            modifier = Modifier.wrapContentSize(Alignment.BottomCenter, unbounded = true)
                .requiredSize(k * PupClips.CANVAS_W, k * PupClips.CANVAS_H)
                .offset(y = k * (PupClips.CANVAS_H - PupClips.FEET)))
    }
}

/**
 * Plays [name] [loops] times in a pup's place ([ready] when it was decoded ahead, else decoded now off the main thread),
 * faded in over him ([fade] 0 to 1) and out again at its end; [hold]: once, staying on its last frame over him until he
 * leaves the screen. False (and nothing shown) when the app has not got the clip.
 */
internal suspend fun playClip(context: Context, name: String, loops: Int, now: MutableState<ShownClip?>,
                              fade: Animatable<Float, AnimationVector1D>, ready: AnimatedImageDrawable? = null, hold: Boolean = false): Boolean {
    val d = ready ?: withContext(Dispatchers.IO) { PupClips.load(context, name) } ?: return false
    val done = CompletableDeferred<Unit>()
    val times = if (hold) 1 else loops
    d.repeatCount = times - 1
    val end = object : Animatable2.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) { done.complete(Unit) }
    }
    d.registerAnimationCallback(end)
    Log.d(TAG, "pup clip $name: playing x$times" + if (hold) ", holding its last frame" else "")
    var kept = false
    try {
        now.value = ShownClip(d, PupClips.scale(name))
        d.start()
        fade.animateTo(1f, tween(PupMotion.SWAP_MS))
        withTimeoutOrNull(PupClips.MAX_LOOP_MS * times) { done.await() }
        // Played once and left where it ends (an AnimatedImageDrawable that has run out shows its last frame).
        if (hold) { kept = true; return true }
        fade.animateTo(0f, tween(PupMotion.SWAP_MS))
    } finally {
        d.unregisterAnimationCallback(end)
        if (!kept) {
            d.stop()
            withContext(NonCancellable) { fade.snapTo(0f) }
            if (now.value?.drawable === d) now.value = null
        }
    }
    return true
}

/**
 * A clip of the pup, looping ([once]: played once, staying on its last frame), [size] square, feet on the bottom edge (and
 * [scale] for a clip drawn smaller); or the rig when the clip is missing: [fallback]'s pose, a HAPPY jump first when [jump],
 * then holding [then] (see [PupClips]). [ground] false: no glow or shadow (a screen with its own light).
 */
@Composable
fun PupClip(name: String, modifier: Modifier = Modifier, size: Dp = 160.dp, fallback: Pose = Pose.GREAT_JOB,
            then: PupMood = fallback.mood, bpm: Int? = null, jump: Boolean = true, ground: Boolean = true,
            scale: Float = PupClips.scale(name), once: Boolean = false) {
    val context = LocalContext.current
    val still = reducedMotion()
    val state by produceState<ClipState>(ClipState.Loading, name, still) {
        value = if (still) ClipState.Missing else withContext(Dispatchers.IO) { PupClips.load(context, name) }?.let { ClipState.Ready(it) } ?: ClipState.Missing
        Log.d(TAG, "pup clip $name: " + when { value is ClipState.Ready -> "playing"; still -> "animations off, the rig holds still"; else -> "missing, the rig's HAPPY" })
    }
    when (val s = state) {
        is ClipState.Ready -> {
            val d = s.drawable
            DisposableEffect(d) {
                d.repeatCount = if (once) 0 else AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
                onDispose { d.stop() }
            }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            // He stands in the same glow, over the same shadow, and at the same size as the rig's pup.
            ClipView(d, size, if (ground) modifier.drawBehind { pupGround(dark) } else modifier, scale)
        }
        else -> {
            val pup = rememberPupController(idle = false)
            if (s == ClipState.Missing && jump) LaunchedEffect(Unit) { pup.play(PupMood.HAPPY) }
            Pup(fallback, mood = then, modifier = modifier, size = size, controller = pup, idle = false, bpm = bpm, ground = ground,
                bounce = com.bigpapituna.sharpears.ui.motion.BounceSize.NONE)
        }
    }
}
