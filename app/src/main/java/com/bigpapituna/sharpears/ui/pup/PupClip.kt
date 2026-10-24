package com.bigpapituna.sharpears.ui.pup

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The pup's mini clips (2.10): short looped animations (animated WebP, 12 fps, with alpha) in assets/pup/<name>.webp,
 * played by the platform's AnimatedImageDrawable (API 28, the app's minSdk). The slot is wired on the kid finale ([DANCE])
 * and the concert's bow ([WAVE]); a clip that is not there yet falls back to the rig: [fallback]'s pose doing the HAPPY
 * jump, then holding [then]. With animations off it is always the rig, holding still.
 */
object PupClips {
    const val JUMP = "jump"
    const val DANCE = "dance"
    const val WAVE = "wave"
    const val HOP = "hop"
    const val DIR = "pup"

    /** Whether assets/pup/<[name]>.webp is in the app. */
    fun has(context: Context, name: String): Boolean = runCatching { context.assets.list(DIR)?.contains("$name.webp") == true }.getOrDefault(false)

    /** The clip, decoded and ready to start; null when it is missing or not an animation. */
    fun load(context: Context, name: String): AnimatedImageDrawable? = runCatching {
        if (!has(context, name)) return null
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, "$DIR/$name.webp")) as? AnimatedImageDrawable
    }.onFailure { Log.d(TAG, "pup clip $name: could not decode ($it)") }.getOrNull()
}

private sealed interface ClipState {
    data object Loading : ClipState
    data object Missing : ClipState
    class Ready(val drawable: AnimatedImageDrawable) : ClipState
}

/** A clip of the pup, [size] square, feet on the bottom edge; or the rig's HAPPY when the clip is missing (see [PupClips]). */
@Composable
fun PupClip(name: String, modifier: Modifier = Modifier, size: Dp = 160.dp, fallback: Pose = Pose.GREAT_JOB,
            then: PupMood = fallback.mood, bpm: Int? = null) {
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
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
                onDispose { d.stop() }
            }
            val ratio = remember(d) { d.intrinsicWidth.toFloat() / d.intrinsicHeight.coerceAtLeast(1) }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            // He stands in the same glow, over the same shadow, as the rig's pup (the clip's feet sit a little above its edge).
            Box(modifier.size(size).drawBehind { pupGround(dark) }, contentAlignment = Alignment.BottomCenter) {
                AndroidView(factory = { c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_XY; setImageDrawable(d) } },
                    modifier = if (ratio <= 1f) Modifier.fillMaxHeight().aspectRatio(ratio) else Modifier.fillMaxWidth().aspectRatio(ratio))
            }
        }
        else -> {
            val pup = rememberPupController(idle = false)
            if (s == ClipState.Missing) LaunchedEffect(Unit) { pup.play(PupMood.HAPPY) }
            Pup(fallback, mood = then, modifier = modifier, size = size, controller = pup, idle = false, bpm = bpm)
        }
    }
}
