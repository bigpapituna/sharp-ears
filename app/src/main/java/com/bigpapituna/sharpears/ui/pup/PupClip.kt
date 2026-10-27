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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * The pup's mini clips (2.10, new moves 2.12): short animations (animated WebP, 318 x 400, 12 fps, with alpha, ground line
 * row 380) in assets/pup/<name>.webp, played by the platform's AnimatedImageDrawable (API 28, the app's minSdk). Generated
 * videos of the pup cut to clips (C:\dev\tools\pup-veo and its gemini folder): [DANCE] (Veo) loops on the kid finale and,
 * until a bow clip ships, the concerts' bow; [SLEEP] (Gemini) on the goodnight screen ([PupClip]); [JUMP] (Gemini) is the
 * HAPPY of a pup with `clips` on (three stars, a sticker revealed or placed, the surprise opened), played once; [WAVE]
 * (Gemini) his hello on the kid home and his call while a surprise waits; [CELEBRATE] (Flow) plays once after a lesson and
 * on "Done for today", holding its last frame (it starts with a paw up and ends with paws at the chest); [BOW], optional,
 * plays once at a concert's bravo when assets/pup/bow.webp is there ([PupBow]). A clip that is not there falls back to
 * the rig; with animations off it is always the rig, holding still. A clip never takes a touch and never holds up the
 * screen around it.
 */
object PupClips {
    const val JUMP = "jump"
    const val DANCE = "dance"
    const val WAVE = "wave"
    const val SLEEP = "sleep"
    const val CELEBRATE = "celebrate"
    const val BOW = "bow"
    const val DIR = "pup"

    /**
     * One clip's row: how much its view scales him so his head matches the others' (a 116 px headband at 1), whether it
     * plays once and stops (never looped: its ends differ), and whether the app may ship without it.
     */
    class Spec(val scale: Float = 1f, val oneShot: Boolean = false, val optional: Boolean = false)

    /**
     * Every clip by name (PupWiringTest: each one in assets/pup unless optional, and nothing else there). The jump, the
     * wave and the celebrate have full-size heads (116 to 118 px); the sleep is a lying pup, width-limited to a 99.6 px
     * headband, so its view scales 1.15 (114.5 px, as the dance's).
     */
    val SPECS: Map<String, Spec> = linkedMapOf(
        JUMP to Spec(oneShot = true),
        DANCE to Spec(),
        WAVE to Spec(),
        SLEEP to Spec(scale = 1.15f),
        CELEBRATE to Spec(oneShot = true),
        BOW to Spec(oneShot = true, optional = true),
    )
    val ALL: List<String> = SPECS.keys.toList()
    val SLEEP_SCALE = scale(SLEEP)

    /**
     * All share one canvas and one ground line. [STAND_H] canvas px map to the pup's box, so the clips' heads (a 116 px
     * headband) come out 0.38 of the box, as the stills' do; feet on row [FEET].
     */
    const val CANVAS_W = 318f
    const val CANVAS_H = 400f
    const val FEET = 380f
    const val STAND_H = 306f
    /** A clip that never reports its end is let go after this long a time through (the longest, the celebrate, is 3.6 s). */
    const val MAX_LOOP_MS = 4_500L

    /** How much [name]'s view scales him up so his head matches the others'. */
    fun scale(name: String): Float = SPECS[name]?.scale ?: 1f

    /** Whether [name] plays once and stays on its last frame wherever it shows (never looped). */
    fun oneShot(name: String): Boolean = SPECS[name]?.oneShot == true

    /** Whether assets/pup/<[name]>.webp is in the app. */
    fun has(context: Context, name: String): Boolean = runCatching { context.assets.list(DIR)?.contains("$name.webp") == true }.getOrDefault(false)

    /** The clip, decoded and ready to start; null when it is missing or not an animation. */
    fun load(context: Context, name: String): AnimatedImageDrawable? = runCatching {
        if (!has(context, name)) return null
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, "$DIR/$name.webp")) as? AnimatedImageDrawable
    }.onFailure { Log.d(TAG, "pup clip $name: could not decode ($it)") }.getOrNull()
}

/** A clip shown in a pup's place, and how much its view scales him ([PupClips.scale]). */
internal class ShownClip(val drawable: AnimatedImageDrawable, val scale: Float, val name: String = "")

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
        now.value = ShownClip(d, PupClips.scale(name), name)
        d.stop()                    // a clip played again (a tap on the pup) starts from its first frame
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
 * A clip of the pup, looping ([once], by default for a one-shot clip: played once, staying on its last frame), [size] square, feet on the bottom edge (and
 * [scale] for a clip drawn smaller); or the rig when the clip is missing: [fallback]'s pose, a HAPPY jump first when [jump],
 * then holding [then] (see [PupClips]). [ground] false: no glow or shadow (a screen with its own light).
 */
@Composable
fun PupClip(name: String, modifier: Modifier = Modifier, size: Dp = 160.dp, fallback: Pose = Pose.GREAT_JOB,
            then: PupMood = fallback.mood, bpm: Int? = null, jump: Boolean = true, ground: Boolean = true,
            scale: Float = PupClips.scale(name), once: Boolean = PupClips.oneShot(name),
            /** 2.14, tap the pup to play it again: each change of [replay] plays the clip again from its first frame (the rig: his HAPPY beat); [onTap] takes the touch, if any. */
            replay: Int = 0, onTap: (() -> Unit)? = null) {
    // 2.15: no clips in the Plain look (ui/Plain.kt).
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) return
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
            androidx.compose.runtime.LaunchedEffect(replay) { if (replay > 0) { d.stop(); d.start() } }
            val tap = if (onTap == null) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { onTap() } }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            // He stands in the same glow, over the same shadow, and at the same size as the rig's pup.
            ClipView(d, size, (if (ground) modifier.drawBehind { pupGround(dark) } else modifier).then(tap), scale)
        }
        else -> {
            val pup = rememberPupController(idle = false)
            if (s == ClipState.Missing && jump) LaunchedEffect(Unit) { pup.play(PupMood.HAPPY) }
            if (replay > 0) LaunchedEffect(replay) { pup.play(PupMood.HAPPY) }
            Pup(fallback, mood = then, modifier = modifier, size = size, controller = pup, idle = false, bpm = bpm, ground = ground, onTap = onTap,
                bounce = com.bigpapituna.sharpears.ui.motion.BounceSize.NONE)
        }
    }
}

/**
 * The concert's bow (2.12): the bow clip once, staying on its last frame, when the app has assets/pup/bow.webp; until then
 * the dance clip, looping, as in 2.10. Without either (or with animations off) the rig: [fallback]'s pose, a HAPPY jump, then
 * holding [then].
 */
@Composable
fun PupBow(size: Dp, modifier: Modifier = Modifier, fallback: Pose = Pose.DANCING, then: PupMood = PupMood.CHEER) {
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) return       // 2.15: the Plain look
    val context = LocalContext.current
    val bow = remember { PupClips.has(context, PupClips.BOW) }
    PupClip(if (bow) PupClips.BOW else PupClips.DANCE, modifier, size, fallback, then)
}
