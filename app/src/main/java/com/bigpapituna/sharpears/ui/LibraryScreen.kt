package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongProgress

/** One library row: the song, whether its file is present, and saved progress. */
data class LibraryEntry(val meta: SongMeta, val available: Boolean, val progress: SongProgress)

class LibraryActions(
    val onOpen: (SongMeta) -> Unit,
    /** Pick a MIDI file; [into] is the locked entry it unlocks, or null for a new song. */
    val onImport: (into: SongMeta?) -> Unit,
    val onChords: () -> Unit,
    val onMic: () -> Unit,
    val onSettings: () -> Unit,
)

private val FILTERS = listOf("All", "Easy", "Medium", "Hard", "Yours")

@Composable
fun LibraryScreen(entries: List<LibraryEntry>, chordsDue: Int, micSummary: String, a: LibraryActions) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val shown = entries.filter {
        when (filter) {
            "All" -> true
            "Yours" -> it.meta.user
            else -> it.meta.difficulty.equals(filter, ignoreCase = true)
        }
    }
    val pad = WindowInsets.safeDrawing.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp + pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = 16.dp + pad.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = 12.dp + pad.calculateTopPadding(), bottom = 24.dp + pad.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Chords", if (chordsDue > 0) "$chordsDue to practice today" else "Lessons, drills, dictionary", RightHandColor, Modifier.weight(1f), a.onChords)
                Tile("Microphone", micSummary, LeftHandColor, Modifier.weight(1f), a.onMic)
                Tile("Settings", "Labels, sound, about", InkDim, Modifier.weight(1f), a.onSettings)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f) }) }
                }
            }
        }
        items(shown, key = { it.meta.id }) { e -> SongCard(e, a) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedCard(onClick = { a.onImport(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("+  Import a MIDI file", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Your own songs stay on this phone. Two tracks become the two hands; one track splits at middle C.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(60.dp))
        }
        Column {
            Text("Sharp Ears", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Play along on your piano. The phone listens.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tile(title: String, sub: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 88.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(width = 28.dp, height = 4.dp).clip(CircleShape).background(accent))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun difficultyColor(d: String) = when (d.lowercase()) {
    "easy" -> Good
    "medium" -> RightHandColor
    "hard" -> Bad
    else -> LeftHandColor
}

@Composable
private fun SongCard(e: LibraryEntry, a: LibraryActions) {
    val m = e.meta
    val accent = difficultyColor(m.difficulty)
    Card(
        onClick = { if (e.available) a.onOpen(m) else a.onImport(m) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.25f)))),
                contentAlignment = Alignment.Center,
            ) { Text(m.title.take(1), style = MaterialTheme.typography.titleLarge, color = Stage) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (e.available) m.composer else "Tap to import your MIDI file", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Pill(m.difficulty.replaceFirstChar { it.uppercase() }, accent)
                    if (m.videos.isNotEmpty()) Pill("Tutorial", LeftHandColor)
                }
            }
            Badge(e)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))

/** Progress badge: best Perform score, else played through, else where you left off, else new. */
@Composable
private fun Badge(e: LibraryEntry) {
    val p = e.progress
    val (text, color) = when {
        !e.available -> "Locked" to InkDim
        p.bestAccuracy >= 0 -> "Best ${p.bestAccuracy}%" to Good
        p.completed -> "✓ Played" to Good
        p.lastPositionMs > 0 -> "Resume ${formatTime(p.lastPositionMs.toDouble())}" to RightHandColor
        else -> "New" to LeftHandColor
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}
