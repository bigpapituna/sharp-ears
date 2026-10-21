package com.bigpapituna.sharpears.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Call inside a full-screen Dialog (its own window, drawn under the bars): the window starts with light status bar
 * icons, white on a light page, so the clock and battery vanished over "Someone new" and the duet picker. The icons
 * follow the page's background instead.
 */
@Composable
fun DialogBarsFollowTheme() {
    val view = LocalView.current
    val lightPage = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightPage
            isAppearanceLightNavigationBars = lightPage
        }
    }
}
