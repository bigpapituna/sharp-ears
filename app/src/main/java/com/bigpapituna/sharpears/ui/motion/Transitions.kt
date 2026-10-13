package com.bigpapituna.sharpears.ui.motion

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.launch

/*
 * Shared motion for screens: AnimatedContent transitions, a press scale for tappable rows, a loading shimmer and
 * haptic patterns. Every one of them stands still (or cuts) when the system's animations are off.
 */

/** Screen-change transitions for AnimatedContent. Pass reducedMotion() as [still]: then screens cut. */
object ScreenMotion {
    private const val IN_MS = 300
    private const val OUT_MS = 180

    /** Deeper into the app: the new screen slides in a little from the right as the old one drifts left. */
    fun forward(still: Boolean): ContentTransform =
        if (still) cut() else (slideInHorizontally(tween(IN_MS, easing = FastOutSlowInEasing)) { it / 5 } +
            fadeIn(tween(IN_MS - 60, delayMillis = 60))) togetherWith
            (slideOutHorizontally(tween(IN_MS, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(OUT_MS)))

    /** Back out: the mirror of [forward]. */
    fun back(still: Boolean): ContentTransform =
        if (still) cut() else (slideInHorizontally(tween(IN_MS, easing = FastOutSlowInEasing)) { -it / 5 } +
            fadeIn(tween(IN_MS - 60, delayMillis = 60))) togetherWith
            (slideOutHorizontally(tween(IN_MS, easing = FastOutSlowInEasing)) { it / 10 } + fadeOut(tween(OUT_MS)))

    /**
     * Into the player, like house lights going down: the old screen dims away, a beat of dark stage, then the
     * player fades up from slightly large. Also fine for leaving it.
     */
    fun curtain(still: Boolean): ContentTransform =
        if (still) cut() else (fadeIn(tween(380, delayMillis = 200, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(520, delayMillis = 200, easing = FastOutSlowInEasing), initialScale = 1.04f)) togetherWith
            (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.98f))

    /** Between tabs of one shell: a quick cross-fade, no travel. */
    fun tab(still: Boolean): ContentTransform =
        if (still) cut() else fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(120))

    private fun cut(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None
}

/**
 * One transitionSpec for a whole screen stack: [isStage] marks screens that get the curtain (the player), and
 * [depth] orders the rest (deeper goes forward, shallower goes back, equal cross-fades like tabs).
 *
 *     AnimatedContent(screen, transitionSpec = screenTransitions(still, isStage = { it is Screen.Player }, depth = ::depthOf)) { ... }
 */
fun <S> screenTransitions(still: Boolean, isStage: (S) -> Boolean, depth: (S) -> Int): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
    when {
        isStage(targetState) || isStage(initialState) -> ScreenMotion.curtain(still)
        depth(targetState) > depth(initialState) -> ScreenMotion.forward(still)
        depth(targetState) < depth(initialState) -> ScreenMotion.back(still)
        else -> ScreenMotion.tab(still)
    }.apply { targetContentZIndex = depth(targetState).toFloat() }
}

/**
 * Tappable rows and tiles shrink to [pressed] while a finger is down and spring back on release. It only
 * watches the touch (it does not consume it), so put it next to the row's own clickable. Nothing with
 * animations off.
 */
fun Modifier.pressScale(pressed: Float = 0.97f): Modifier = composed {
    if (reducedMotion()) return@composed Modifier
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { scale.animateTo(pressed, spring(stiffness = 1400f)) }
                // Final pass: a list that starts scrolling consumes the move, which counts as a cancel.
                waitForUpOrCancellation(PointerEventPass.Final)
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }
            }
        }
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * A placeholder block for content that is still loading: a soft surface with a light sweeping across it every
 * 1.3 s. Static with animations off. Invisible to screen readers (say "Loading" next to it if it matters).
 */
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val still = reducedMotion()
    val sweep = if (still) null else rememberInfiniteTransition(label = "shimmer")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(modifier.clip(shape).background(base).clearAndSetSemantics { }.drawWithCache {
        val band = size.width * 0.45f
        val brush = Brush.horizontalGradient(listOf(Color.Transparent, light, Color.Transparent), startX = 0f, endX = band)
        onDrawBehind {
            val t = sweep?.value ?: return@onDrawBehind
            translate(left = -band + t * (size.width + band)) { drawRect(brush, size = Size(band, size.height)) }
        }
    })
}

/**
 * Haptic patterns for practice: [tick] on a right note (light, at most one per 40 ms so fast runs do not buzz),
 * [miss] a double tap, [success] a little rising pattern for a finished run. They follow the phone's own touch
 * feedback setting; [enabled] adds an app-level switch.
 */
class Haptics(private val view: View) {
    var enabled = true
    private var lastTick = 0L

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 40) return
        lastTick = now
        perform(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun miss() {
        perform(HapticFeedbackConstants.KEYBOARD_TAP)
        view.postDelayed({ perform(HapticFeedbackConstants.KEYBOARD_TAP) }, 90)
    }

    fun success() {
        perform(HapticFeedbackConstants.CLOCK_TICK)
        view.postDelayed({ perform(HapticFeedbackConstants.CLOCK_TICK) }, 90)
        view.postDelayed({ perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS) }, 200)
    }

    private fun perform(kind: Int) { if (enabled) view.performHapticFeedback(kind) }
}

/** [Haptics] for the current view. */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
