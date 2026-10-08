package com.bigpapituna.sharpears.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Sampler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One number on a celebration: "3:12" over "Time". */
data class Stat(val value: String, val label: String)

/**
 * The finish line, Duolingo style: the pup bounces up from below and lands in a victory jump, confetti bursts, a
 * short piano flourish plays (with the app sound on) and the phone taps once; then the headline pops, the stats
 * slide in one by one and Continue rises last. Covers the screen until [onPrimary] or [onSecondary].
 */
@Composable
fun Celebration(
    title: String, line: String, stats: List<Stat>, primary: String, onPrimary: () -> Unit,
    secondary: String? = null, onSecondary: (() -> Unit)? = null,
    pose: Pose = Pose.LEVEL_UP, sampler: Sampler? = null,
) {
    BackHandler { (onSecondary ?: onPrimary)() }
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val rise = remember { Animatable(if (still) 1f else 0f) }     // the pup coming up
    val pop = remember { Animatable(if (still) 1f else 0f) }      // the headline
    val rest = remember { Animatable(if (still) 1f else 0f) }     // stats, then the buttons
    LaunchedEffect(Unit) {
        sampler?.let { fanfare(it) }
        if (still) return@LaunchedEffect
        launch { rise.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 180f)) }
        delay(320); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(180); rest.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val px = with(LocalDensity.current) { 320.dp.toPx() }
    Box(Modifier.fillMaxSize().paper().pointerInput(Unit) { detectTapGestures { } }
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Confetti(title, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Pup(pose, size = 200.dp, modifier = Modifier.graphicsLayer {
                translationY = (1 - rise.value) * px
                val s = 0.4f + 0.6f * rise.value
                scaleX = s; scaleY = s; alpha = rise.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            })
            Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }.graphicsLayer {
                    val s = 0.6f + 0.4f * pop.value; scaleX = s; scaleY = s; alpha = pop.value.coerceIn(0f, 1f)
                })
            Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) })
            if (stats.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.forEachIndexed { i, st ->
                    // Each one in turn, sliding up.
                    val k = (rest.value * (stats.size + 1) - i).coerceIn(0f, 1f)
                    Column(Modifier.graphicsLayer { alpha = k; translationY = (1 - k) * 40f }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.value, style = Numeric.merge(MaterialTheme.typography.titleLarge), color = MaterialTheme.colorScheme.onSurface)
                        Text(st.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)
            .graphicsLayer { val k = ((rest.value - 0.5f) * 2).coerceIn(0f, 1f); alpha = k; translationY = (1 - k) * 60f },
            horizontalAlignment = Alignment.CenterHorizontally) {
            ChunkyButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
            if (secondary != null && onSecondary != null) TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 6.dp)) { Text(secondary) }
        }
    }
}

/** C, E, G, then the C above, rolled like a little "ta-da" on the piano. */
private suspend fun fanfare(sampler: Sampler) {
    listOf(72, 76, 79).forEach { sampler.noteOn(it, 70); delay(90) }
    sampler.noteOn(84, 88)
    delay(1400)
    listOf(72, 76, 79, 84).forEach(sampler::noteOff)
}

/** A cheer bar's look: a win (gold, high-five) or a miss (neutral, the pup thinking it over). */
enum class Cheer { GOOD, MISS }

/**
 * After one step, Duolingo style: a bar slides up from the bottom with the pup peeking over its edge, a short
 * [title], the feedback [line] and one big button. Nothing moves on until the button is pressed.
 */
@Composable
fun CheerBar(cheer: Cheer, title: String, line: String?, button: String, modifier: Modifier = Modifier, onButton: () -> Unit) {
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val up = remember(title, line) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(title, line) {
        if (cheer == Cheer.GOOD) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        up.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 420f))
    }
    val c = MaterialTheme.colorScheme
    val good = cheer == Cheer.GOOD
    Box(modifier.fillMaxWidth().graphicsLayer { translationY = (1 - up.value) * size.height * 1.2f }) {
        Column(Modifier.padding(top = 44.dp).fillMaxWidth()
            .background(if (good) c.primaryContainer else c.surfaceContainerHighest, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = if (good) c.onPrimaryContainer else c.onSurface,
                modifier = Modifier.padding(start = 92.dp))
            line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (good) c.onPrimaryContainer else c.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            ChunkyButton(onClick = onButton, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
        // The pup peeks over the top edge of the bar.
        Pup(if (good) Pose.GREAT_JOB else Pose.MISTAKE, size = 96.dp, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Short, varied words for a passed step, so the bar does not say the same thing every time. */
fun cheerTitle(seed: Int) = listOf("Nice work!", "Great job!", "That's it!", "Well played!", "You got it!", "Spot on!")[Math.floorMod(seed, 6)]
