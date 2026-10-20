package com.bigpapituna.sharpears.ui.profile

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-screen moment for a new level: the pup bounces up inside a completed ring, confetti bursts, "Level 12: Chord
 * Builder" pops in and the XP counts up to its total. Covers the screen until [onDone]. Place it at the root, above
 * everything (see ProgressOverlays).
 */
@Composable
fun LevelUpOverlay(levelUp: LevelUp, xp: Int, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val still = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val rise = remember(levelUp) { Animatable(if (still) 1f else 0f) }
    val pop = remember(levelUp) { Animatable(if (still) 1f else 0f) }
    val rest = remember(levelUp) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(levelUp) {
        if (still) return@LaunchedEffect
        launch { rise.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 180f)) }
        delay(320); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(180); rest.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val c = MaterialTheme.colorScheme
    val shownLevel = rememberCountUp(levelUp.to, 900, from = levelUp.from)
    val shownXp = rememberCountUp(xp, 1500, from = Progression.xpToReach(levelUp.from))
    val toNext = Progression.xpToNext(xp)
    val px = with(LocalDensity.current) { 320.dp.toPx() }
    // A pane over the screen: TalkBack reads it alone (the screen under it was still reachable and its buttons usable).
    Box(Modifier.fillMaxSize().paper().pointerInput(Unit) { detectTapGestures { } }.semantics { paneTitle = "Level up" }
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Confetti(levelUp, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(236.dp).graphicsLayer {
                translationY = (1 - rise.value) * px
                val s = 0.4f + 0.6f * rise.value
                scaleX = s; scaleY = s; alpha = rise.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }, contentAlignment = Alignment.Center) {
                ProgressRing(1f, Modifier.size(224.dp), stroke = 10.dp) { Pup(Pose.LEVEL_UP, size = 160.dp) }
                Text("Level $shownLevel", style = MaterialTheme.typography.titleMedium.mono(), color = c.onPrimary,
                    modifier = Modifier.align(Alignment.BottomCenter).background(c.primary, CircleShape).padding(horizontal = 18.dp, vertical = 6.dp))
            }
            Text("Level up!", style = MaterialTheme.typography.labelLarge, color = c.primary,
                modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) })
            Text("Level ${levelUp.to}: ${Progression.title(levelUp.to)}", style = MaterialTheme.typography.displaySmall, color = c.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }.graphicsLayer {
                    val s = 0.6f + 0.4f * pop.value; scaleX = s; scaleY = s; alpha = pop.value.coerceIn(0f, 1f)
                })
            Text("${java.text.NumberFormat.getIntegerInstance().format(shownXp)} XP", style = MaterialTheme.typography.headlineSmall.mono(),
                color = c.onSurfaceVariant, modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }
                    .semantics { contentDescription = "$xp experience points" })
            Text(if (toNext > 0) "$toNext XP to level ${levelUp.to + 1}" else "You reached the top level.",
                style = MaterialTheme.typography.bodyMedium.mono(), color = c.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = rest.value.coerceIn(0f, 1f) })
        }
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)
            .graphicsLayer { val k = ((rest.value - 0.5f) * 2).coerceIn(0f, 1f); alpha = k; translationY = (1 - k) * 60f },
            horizontalAlignment = Alignment.CenterHorizontally) {
            ChunkyButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Keep going") }
        }
    }
}
