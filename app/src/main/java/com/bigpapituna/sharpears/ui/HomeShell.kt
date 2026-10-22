package com.bigpapituna.sharpears.ui

import androidx.compose.animation.AnimatedContent
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
import com.bigpapituna.sharpears.engine.nav.NavTab
import com.bigpapituna.sharpears.ui.motion.ScreenMotion

/** The grown-ups' five tabs (2.7, engine/nav/Nav.kt): Today, Songs, Learn, Practice, You, each with its icon. */
enum class HomeTab(val nav: NavTab, val icon: Int) {
    TODAY(NavTab.TODAY, R.drawable.ic_today),
    SONGS(NavTab.SONGS, R.drawable.ic_library_music),
    LEARN(NavTab.LEARN, R.drawable.ic_school),
    PRACTICE(NavTab.PRACTICE, R.drawable.ic_practice),
    YOU(NavTab.YOU, R.drawable.ic_person);

    val label: String get() = nav.label
}

/**
 * The five home destinations: a bottom bar on the phone, a side rail on the unfolded screen. Settings is not a tab:
 * it opens from the gear on You. [content] gets the tab to draw, so the old and new tab can cross-fade.
 */
@Composable
fun HomeShell(tab: HomeTab, onTab: (HomeTab) -> Unit, badge: Map<HomeTab, Int> = emptyMap(), content: @Composable (HomeTab) -> Unit) {
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val still = reducedMotion()
    @Composable fun body(m: Modifier) = AnimatedContent(tab, m, transitionSpec = { ScreenMotion.tab(still) }, label = "tab") { t ->
        androidx.compose.runtime.CompositionLocalProvider(LocalLeaving provides (LocalLeaving.current || t != tab)) { content(t) }
    }
    // Learn shows how many chord review items are due today as a badge on its icon (Learn chords holds them).
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
            body(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            body(Modifier.weight(1f))
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(t == tab, { onTab(t) }, icon = { icon(t) }, label = { Text(t.label) })
                }
            }
        }
    }
}
