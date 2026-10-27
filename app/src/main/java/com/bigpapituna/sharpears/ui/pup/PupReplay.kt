package com.bigpapituna.sharpears.ui.pup

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.pup.PupMotion
import com.bigpapituna.sharpears.engine.pup.PupTap
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.sparkle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the pup's voice speaks (a kid's lines, said by the recorded voice): set once at the root by MainActivity, null where
 * there is no voice (a demo, a test). A finish screen's pup says his line through it the first time he is tapped.
 */
val LocalPupVoice = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * Tap the pup to play it again (2.14, Roy): on a finish screen where a clip or a beat played as he landed, a tap replays it
 * from the start, with a tiny sparkle, as many times as anyone likes. His voice line is said the first time only. It never
 * waits on anything and never holds up the Next button (the pup takes the touch; nothing else does), and with the phone's
 * animations off the replay is the plain 150 ms fade of his glow (PupController.play does that) and the sparkle a fade too.
 *
 * Make one with [rememberPupReplay] and give [onTap] to the pup (use [ReplayPup], which draws the sparkle too).
 * PupWiringTest checks that every finish screen's pup is a [ReplayPup] or hands [onTap] on.
 */
@Stable
class PupReplay internal constructor(
    private val ctl: PupController,
    private val clip: String?,
    private val hold: Boolean,
    private val then: PupMood?,
    private val voice: String?,
    private val say: (() -> ((String) -> Unit)?),
    private val scope: CoroutineScope,
) {
    /** Taps so far (the sparkle follows it). */
    var taps by mutableIntStateOf(0)
        private set
    private var spoke = false

    /** What a tap does. */
    val onTap: () -> Unit = {
        taps++
        Log.d(TAG, "pup replay #$taps" + (clip?.let { " ($it)" } ?: " (the beat)"))
        if (clip != null) ctl.playClip(clip, fallback = PupMood.HAPPY, hold = hold, then = then)
        else {
            ctl.play(PupMood.HAPPY)
            then?.let { m -> scope.launch { delay(PupMotion.HAPPY_MS.toLong()); ctl.mood = m } }
        }
        if (PupTap.speaks(spoke, voice)) { spoke = true; say()?.invoke(voice!!) }
    }
}

/**
 * A [PupReplay] for the pup driven by [controller]: [clip] is the clip that played as he landed (null: the rig's HAPPY beat),
 * held on its last frame when [hold]; [then] the mood he holds after the beat (CHEER on a party). [voice] is his line, said
 * the first tap only, through [LocalPupVoice]; null: no line.
 */
@Composable
fun rememberPupReplay(controller: PupController, clip: String? = null, hold: Boolean = false, then: PupMood? = null, voice: String? = null): PupReplay {
    val scope = rememberCoroutineScope()
    val voiceNow = androidx.compose.runtime.rememberUpdatedState(LocalPupVoice.current)
    return remember(controller, clip, hold, then, voice) { PupReplay(controller, clip, hold, then, voice, { voiceNow.value }, scope) }
}

/**
 * The pup on a finish screen: [Pup] with [replay] on his tap and a tiny sparkle over his head at each tap. [modifier] sizes
 * and moves the whole (the landing's rise); the sparkle only draws, it takes no touch.
 */
@Composable
fun ReplayPup(
    pose: Pose, replay: PupReplay, controller: PupController, size: Dp, modifier: Modifier = Modifier,
    mood: PupMood = pose.mood, clips: Boolean = false, idle: Boolean = false,
    outfit: com.bigpapituna.sharpears.engine.world.Outfit = com.bigpapituna.sharpears.ui.world.LocalPupOutfit.current,
    bounce: com.bigpapituna.sharpears.ui.motion.BounceSize = com.bigpapituna.sharpears.ui.motion.BounceSize.SMALL,
) {
    // 2.15: no pup and no tap-to-replay in the Plain look; a finish screen draws PlainMark in his place (ui/Plain.kt).
    if (com.bigpapituna.sharpears.ui.LocalPlain.current.on) return
    Box(modifier.size(size)) {
        Pup(pose, mood, size = size, onTap = replay.onTap, idle = idle, controller = controller, clips = clips, outfit = outfit, bounce = bounce)
        TapSparkle(replay.taps, Modifier.fillMaxSize())
    }
}

/**
 * A few gold and white four-point sparkles that fly out from over his head for half a second at each tap ([taps] counts them);
 * with animations off they are in place and fade over [PupMotion.CALM_MS].
 */
@Composable
internal fun TapSparkle(taps: Int, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val p = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(if (still) PupMotion.CALM_MS else PupTap.SPARKLE_MS, easing = LinearEasing))
    }
    Canvas(modifier) {
        val k = p.value
        if (k >= 1f) return@Canvas
        val c = Offset(size.width * 0.5f, size.height * 0.36f)
        for (i in 0 until PupTap.SPARKLES) {
            val a = (i * 2 * PI / PupTap.SPARKLES - PI / 2).toFloat()
            val out = if (still) 0.32f else 0.14f + 0.22f * (1f - (1f - k) * (1f - k))
            val d = size.minDimension * out * (0.85f + 0.3f * (i % 2))
            val r = size.minDimension * 0.05f * (if (still) 1f else 1f - 0.5f * k)
            sparkle(Offset(c.x + cos(a) * d, c.y + sin(a) * d), r, if (i % 2 == 0) PupGold else Color.White, alpha = 1f - k)
        }
    }
}
