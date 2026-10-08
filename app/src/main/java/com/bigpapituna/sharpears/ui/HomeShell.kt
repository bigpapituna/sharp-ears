package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R

enum class HomeTab(val label: String, val icon: Int) {
    SONGS("Songs", R.drawable.ic_library_music),
    LEARN("Learn", R.drawable.ic_school),
    CHORDS("Chords", R.drawable.ic_piano),
    SETTINGS("Settings", R.drawable.ic_settings),
}

/** The three home destinations: a bottom bar on the phone, a side rail on the unfolded screen. */
@Composable
fun HomeShell(tab: HomeTab, onTab: (HomeTab) -> Unit, badge: Map<HomeTab, Int> = emptyMap(), content: @Composable () -> Unit) {
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    // Chords shows how many lesson items are due today as a badge on its icon.
    @Composable fun icon(t: HomeTab) = BadgedBox(badge = { badge[t]?.takeIf { it > 0 }?.let { Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text("$it") } } }) {
        Icon(painterResource(t.icon), contentDescription = null)
    }
    if (wide) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            NavigationRail(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                HomeTab.entries.forEach { t ->
                    NavigationRailItem(t == tab, { onTab(t) }, icon = { icon(t) }, label = { Text(t.label) })
                }
            }
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Box(Modifier.weight(1f)) { content() }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(t == tab, { onTab(t) }, icon = { icon(t) }, label = { Text(t.label) })
                }
            }
        }
    }
}
