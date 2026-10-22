package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.ui.motion.pressScale

/**
 * The launch chooser (2.8): a grown-up who never chose a layout (engine/nav/Simple.kt `asks`) picks one before Today. Three
 * cards, Simple, Advanced and Full, one line each; a tap saves it ([onPick]) and the home opens. A kid never sees it, and
 * someone who had 2.7's Simple switch set is never asked (Prefs.layout carries it over).
 */
@Composable
fun LayoutChooser(name: String, returning: Boolean, onPick: (Layout) -> Unit) {
    // Centred on a tall screen (the unfolded Fold), scrolling when the font is large or the phone is held sideways.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val tall = maxHeight
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = tall).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val hello = if (name.isBlank()) (if (returning) "Welcome back." else "Hi.") else if (returning) "Welcome back, $name." else "Hi, $name."
            PupSays(Pose.WELCOME, "$hello One quick choice and we can start.", Modifier.fillMaxWidth(), size = 96.dp)
            Text(Layout.QUESTION, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).semantics { heading() })
            Layout.entries.forEach { l -> LayoutCard(l, onClick = { onPick(l) }) }
            Text(Layout.NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
    }
}

/** One layout: a small page with one, two or three lines lit (how much is on screen), its name and its line. */
@Composable
private fun LayoutCard(l: Layout, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).pressScale().clip(shape).border(1.dp, c.outlineVariant, shape)
        .clickable(role = Role.Button, onClickLabel = "Choose ${l.title}", onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.size(width = 40.dp, height = 44.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                Box(Modifier.width(if (i == 2) 16.dp else 24.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i <= l.ordinal) c.primary else c.outlineVariant))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l.title, style = MaterialTheme.typography.titleMedium)
            Text(l.line, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}
