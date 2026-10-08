package com.bigpapituna.sharpears.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongProgress

/** One library row: the song, whether its file is present, and saved progress. */
data class LibraryEntry(val meta: SongMeta, val available: Boolean, val progress: SongProgress,
                        /** The other arrangement (easy or full) when the piece has one. */
                        val pair: SongMeta? = null)

/** The "Practice today" panel: the plan's length and parts, or today's summary once it is done. */
data class TodayPanel(val about: String, val parts: List<String>, val summary: String?)

class LibraryActions(
    val onOpen: (SongMeta) -> Unit,
    /** Starts "Practice today". */
    val onToday: () -> Unit = {},
    /** Pick a MIDI file; [into] is the locked entry it unlocks, or null for a new song. */
    val onImport: (into: SongMeta?) -> Unit,
    val onMic: () -> Unit,
)

private val GROUPS = listOf("easy" to "Easy", "medium" to "Medium", "hard" to "Hard")

/**
 * Songs: a "continue" panel (the one raised surface), then plain rows grouped by difficulty.
 * [continueWith] is the song last played, or null on a fresh install (the panel then suggests a first song).
 */
@Composable
fun LibraryScreen(entries: List<LibraryEntry>, continueWith: LibraryEntry?, micLabel: String, a: LibraryActions, practice: String? = null,
                  today: TodayPanel? = null) {
    val first = continueWith ?: entries.firstOrNull { it.available && it.meta.difficulty == "easy" }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) { Header(micLabel, a.onMic, practice) } }
        // "Practice today" is the one raised panel; Continue sits under it as a plain row.
        if (today != null) {
            item { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) { TodayPanelView(today, a.onToday) } }
            first?.let { e -> item { ContinueRow(e, fresh = continueWith == null, a) } }
        } else first?.let { e -> item { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) { ContinuePanel(e, fresh = continueWith == null, a) } } }
        GROUPS.forEach { (key, label) ->
            val group = entries.filter { !it.meta.user && it.meta.difficulty == key }
            if (group.isEmpty()) return@forEach
            item(key = "h-$key") { GroupHeader(label, group.size) }
            items(group, key = { it.meta.id }) { SongRow(it, a) }
        }
        val yours = entries.filter { it.meta.user }
        item(key = "h-yours") { GroupHeader("Your songs", yours.size) }
        items(yours, key = { it.meta.id }) { SongRow(it, a) }
        item(key = "import") {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { a.onImport(null) }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Import a MIDI file")
                }
                Text("Files stay on this phone. Two tracks become the two hands; one track splits at middle C.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 480.dp))
            }
        }
    }
}

@Composable
private fun Header(micLabel: String, onMic: () -> Unit, practice: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // The logo always sits on its own dark tile, as on the launcher, so the white sharp shows in light mode too.
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Stage), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sharp Ears", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            // Practice this week, from the time the player ran; no line until there is some.
            practice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TextButton(onClick = onMic) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(micLabel)
        }
    }
}

@Composable
private fun ContinuePanel(e: LibraryEntry, fresh: Boolean, a: LibraryActions) {
    val p = e.progress
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (fresh) "Start here" else "Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(e.meta.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                fresh -> "An easy first song: melody in the right hand, one bass note in the left."
                p.bestAccuracy >= 0 -> "${e.meta.composer}. Best run ${p.bestAccuracy}%."
                p.lastPositionMs > 0 && !p.completed -> "${e.meta.composer}. Paused at ${formatTime(p.lastPositionMs.toDouble())}."
                else -> e.meta.composer
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Button(onClick = { a.onOpen(e.meta) }, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (!fresh && p.lastPositionMs > 0 && !p.completed) "Resume" else "Play")
        }
    }
}

@Composable
private fun TodayPanelView(t: TodayPanel, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Practice today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (t.summary != null) {
            Text("Done for today", style = MaterialTheme.typography.headlineMedium)
            Text(t.summary.removePrefix("Done today: ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onStart, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Practice again") }
        } else {
            Text(t.about.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineMedium)
            // One line per part, in the order they run.
            if (t.parts.isEmpty()) Text("Nothing is due. Pick any song below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.parts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text("Start")
            }
        }
    }
}

/** Continue as a plain row under the panel: what was played last and where it stopped. */
@Composable
private fun ContinueRow(e: LibraryEntry, fresh: Boolean, a: LibraryActions) {
    val p = e.progress
    Row(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp)).clickable { a.onOpen(e.meta) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (fresh) "Start here" else "Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    fresh -> "An easy first song, melody and bass."
                    p.lastPositionMs > 0 && !p.completed -> "Paused at ${formatTime(p.lastPositionMs.toDouble())}"
                    p.bestAccuracy >= 0 -> "Best run ${p.bestAccuracy}%"
                    else -> e.meta.composer
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = if (!fresh && p.lastPositionMs > 0 && !p.completed) "Resume" else "Play",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GroupHeader(label: String, count: Int) {
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(8.dp))
        Text("$count", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun SongRow(e: LibraryEntry, a: LibraryActions) {
    val m = e.meta
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(12.dp))
                .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) { if (e.available) a.onOpen(m) else a.onImport(m) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val trouble = e.progress.troubleBar
                Text(when { !e.available -> "Import its MIDI file to unlock"; trouble != null -> "Work on bar $trouble"; else -> m.composer },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (e.available && trouble != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                // The piece's other arrangement, one tap away.
                e.pair?.let { other ->
                    TextButton(onClick = { a.onOpen(other) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).semantics { contentDescription = "Open the ${if (other.easy) "easy" else "full"} version" }) {
                        Text(if (other.easy) "Easy version" else "Full version", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Status(e)
        }
    }
}

/** Where the song stands: best Perform score, played through, paused position, or locked. */
@Composable
private fun Status(e: LibraryEntry) {
    val p = e.progress
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        !e.available -> Text("Locked", style = MaterialTheme.typography.labelMedium, color = dim)
        p.bestAccuracy >= 0 -> Text("${p.bestAccuracy}%", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary)
        p.completed -> Icon(painterResource(R.drawable.ic_check), contentDescription = "Played through", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        p.lastPositionMs > 0 -> Text(formatTime(p.lastPositionMs.toDouble()), style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurface)
        else -> {}                                   // untouched songs carry no label; every song starts that way
    }
}
