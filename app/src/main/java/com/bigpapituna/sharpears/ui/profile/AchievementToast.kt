package com.bigpapituna.sharpears.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.progress.Achievement
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay

/** How long a toast stays before it slides away. */
private const val SHOW_MS = 3000L

/**
 * A small banner that slides in from the top when an achievement unlocks (just a fade with animations off), stays for
 * three seconds and calls [onDismiss]. Pass the next achievement to show, or null for none. A tap dismisses it early.
 */
@Composable
fun AchievementToast(achievement: Achievement?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    var shown by remember { mutableStateOf<Achievement?>(null) }     // kept while it slides out
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(achievement) {
        if (achievement == null) { visible = false; return@LaunchedEffect }
        shown = achievement
        visible = true
        delay(SHOW_MS)
        visible = false
        delay(350)                                                    // let it slide out first
        onDismiss()
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible,
            enter = if (still) fadeIn() else slideInVertically { -it } + fadeIn(),
            exit = if (still) fadeOut() else slideOutVertically { -it } + fadeOut(),
        ) {
            shown?.let { a ->
                val c = MaterialTheme.colorScheme
                Row(
                    Modifier.padding(12.dp).widthIn(max = 480.dp).fillMaxWidth()
                        .shadow(6.dp, MaterialTheme.shapes.medium)
                        .background(c.primaryContainer, MaterialTheme.shapes.medium)
                        .clickable { visible = false; onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Achievement unlocked: ${a.title}. ${a.description}" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AchievementBadge(a.icon, got = true, size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Achievement unlocked", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer.copy(alpha = 0.75f))
                        Text(a.title, style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Both overlays in one place, for the root of the app: the level-up moment (above everything) and the achievement
 * toasts (one at a time, held back while a level-up is showing). Fed by [ProgressStore].
 */
@Composable
fun ProgressOverlays(store: ProgressStore, modifier: Modifier = Modifier,
                     /** Integration: keep a pending level-up back (a Celebration is showing); toasts still come. */
                     holdLevelUp: Boolean = false) {
    Box(modifier.fillMaxSize()) {
        val up = store.levelUp.value?.takeIf { !holdLevelUp }
        AchievementToast(if (up == null) store.toasts.firstOrNull() else null, onDismiss = store::popToast, modifier = Modifier.align(Alignment.TopCenter))
        if (up != null) LevelUpOverlay(up, store.xp, onDone = store::consumeLevelUp)
    }
}
