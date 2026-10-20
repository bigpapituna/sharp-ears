package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongProgress
import com.bigpapituna.sharpears.engine.SongArt
import com.bigpapituna.sharpears.ui.motion.pressScale

/** One library row: the song, whether its file is present, and saved progress. */
data class LibraryEntry(val meta: SongMeta, val available: Boolean, val progress: SongProgress,
                        /** The other arrangement (easy or full) when the piece has one. */
                        val pair: SongMeta? = null)

/** Today's goal and streak for the header and the pup (ProgressStore): "3 of 10 min today", met or not, days in a row. */
data class GoalState(val line: String, val met: Boolean, val streak: Int, val atRisk: Boolean)

/** What the pup says when he is tapped on the home screen: one at random, never the same twice in a row. */
private val PUP_TAPS = listOf(
    "Woof! You've got this.", "Every minute at the keys counts.", "Slow and steady makes it stick.", "Mistakes mean you're learning.",
    "Ready when you are.", "Your fingers remember more than you think.", "One more song? I'll listen.", "Small steps, big music.",
)

/** The "Practice today" panel: the plan's length and parts, or today's summary once it is done. */
data class TodayPanel(val about: String, val parts: List<String>, val summary: String?)

class LibraryActions(
    val onOpen: (SongMeta) -> Unit,
    /** Starts "Practice today". */
    val onToday: () -> Unit = {},
    /** Pick a MIDI file; [into] is the locked entry it unlocks, or null for a new song. */
    val onImport: (into: SongMeta?) -> Unit,
    val onMic: () -> Unit,
    /** An imported song's menu: a new title (its scores stay), or its file removed from this phone. */
    val onRename: (SongMeta, String) -> Unit = { _, _ -> },
    val onDelete: (SongMeta) -> Unit = {},
)

private val GROUPS = listOf("easy" to "Easy", "medium" to "Medium", "hard" to "Hard")

/**
 * Songs: a "continue" panel (the one raised surface), then plain rows grouped by difficulty.
 * [continueWith] is the song last played, or null on a fresh install (the panel then suggests a first song).
 */
@Composable
fun LibraryScreen(entries: List<LibraryEntry>, continueWith: LibraryEntry?, micLabel: String, a: LibraryActions, practice: String? = null,
                  today: TodayPanel? = null, goal: GoalState? = null,
                  /** The family wall (ui/family/FamilyWall.kt) under the pup; null on a phone with one profile. */
                  family: (@Composable () -> Unit)? = null,
                  /** Pup's quest (ui/play/QuestCard), under "Practice today". */
                  quest: (@Composable () -> Unit)? = null,
                  /** The play corner (ui/play/PlayRow): high up for a kid ([kid], the Jam is the app for a four-year-old), under the quest otherwise. */
                  play: (@Composable () -> Unit)? = null, kid: Boolean = false) {
    val first = continueWith ?: entries.firstOrNull { it.available && it.meta.difficulty == "easy" }
    val dressed = com.bigpapituna.sharpears.ui.world.LocalPupOutfit.current != com.bigpapituna.sharpears.engine.world.Outfit.NONE
    val pose = when {
        today?.summary != null -> Pose.RESTING
        goal?.met == true -> Pose.GREAT_JOB
        (goal?.streak ?: 0) >= 3 || goal == null && practice?.contains("in a row") == true -> Pose.STREAK
        dressed -> Pose.WEAR_BASE                    // in his outfit (2.1): the base pose is the one he wears it in
        else -> Pose.WELCOME
    }
    var tapLine by remember { mutableStateOf<String?>(null) }
    // Tile art and lengths, read off the main thread and filled in as they arrive.
    val arts = rememberSongArts(remember(entries) { entries.map { it.meta } })
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                Header(micLabel, a.onMic, practice, goal?.line)
                // The pup hosts the home screen: what he says depends on the day so far; a tap gets a word of encouragement,
                // and left alone for half a minute he dozes off.
                PupSays(pose, tapLine ?: remember(today?.summary, practice, goal) { greeting(today, practice, goal, fresh = continueWith == null) },
                    Modifier.padding(bottom = 14.dp), size = 104.dp,
                    onTap = { tapLine = (PUP_TAPS - tapLine).random() }, sleepyAfterMs = 30_000)
            }
        }
        family?.let { wall -> item(key = "family") { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) { wall() } } }
        val playItem: (() -> Unit)? = play?.let { p -> {
            item(key = "play") { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) { p() } }
        } }
        if (kid) playItem?.invoke()
        // "Practice today" is the one raised panel; the quest sits under it, Continue under that as a plain row.
        if (today != null) {
            item { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) { TodayPanelView(today, a.onToday) } }
            quest?.let { q -> item(key = "quest") { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) { q() } } }
            first?.let { e -> item { ContinueRow(e, fresh = continueWith == null, a, arts[e.meta.id]) } }
        } else {
            quest?.let { q -> item(key = "quest") { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { q() } } }
            first?.let { e -> item { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp)) { ContinuePanel(e, fresh = continueWith == null, a, arts[e.meta.id]) } } }
        }
        if (!kid) playItem?.invoke()
        GROUPS.forEach { (key, label) ->
            val group = entries.filter { !it.meta.user && it.meta.difficulty == key }
            if (group.isEmpty()) return@forEach
            item(key = "h-$key") { GroupHeader(label, group, stars = SongArt.stars(key)) }
            items(group, key = { it.meta.id }) { SongRow(it, a, arts[it.meta.id]) }
        }
        val yours = entries.filter { it.meta.user }
        item(key = "h-yours") { GroupHeader("Your songs", yours, stars = 0) }
        items(yours, key = { it.meta.id }) { SongRow(it, a, arts[it.meta.id]) }
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
private fun Header(micLabel: String, onMic: () -> Unit, practice: String?, goal: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // The logo always sits on its own dark tile, as on the launcher, so the white sharp shows in light mode too.
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Stage), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sharp Ears", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            // Today against the daily goal, then practice this week (no week line until there is some).
            goal?.let { Text(it, style = Numeric.merge(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.primary) }
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
private fun ContinuePanel(e: LibraryEntry, fresh: Boolean, a: LibraryActions, art: SongArt?) = Panel {
        val p = e.progress
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (fresh) "Start here" else "Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(e.meta.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SongFacts(e.meta.difficulty, art?.durationMs)
            }
            SongTile(art, locked = !e.available, size = 84.dp)
        }
        Text(
            when {
                fresh -> "An easy first song: melody in the right hand, one bass note in the left."
                // An imported song has no composer: "Imported", as its row in the list says, never a line starting ". ".
                p.bestAccuracy >= 0 -> "${e.meta.composer.ifEmpty { "Imported" }}. Best run ${p.bestAccuracy}%."
                p.lastPositionMs > 0 && !p.completed -> "${e.meta.composer.ifEmpty { "Imported" }}. Paused at ${formatTime(p.lastPositionMs.toDouble())}."
                else -> e.meta.composer.ifEmpty { "Imported" }
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        ChunkyButton(onClick = { a.onOpen(e.meta) }) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (!fresh && p.lastPositionMs > 0 && !p.completed) "Resume" else "Play")
        }
}

@Composable
private fun TodayPanelView(t: TodayPanel, onStart: () -> Unit) = Panel {
        Text("Practice today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (t.summary != null) {
            Text("Done for today", style = MaterialTheme.typography.headlineMedium)
            Text(t.summary.removePrefix("Done today: ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onStart, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Practice again") }
        } else {
            Text(t.about.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineMedium)
            // One line per part, in the order they run.
            if (t.parts.isEmpty()) PupSays(Pose.WELCOME, "Nothing is due. Pick any song below.", Modifier.padding(vertical = 4.dp), size = 56.dp,
                style = MaterialTheme.typography.bodyLarge)
            t.parts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.size(8.dp))
            ChunkyButton(onClick = onStart) {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text("Start")
            }
        }
}

/** The home screen's one raised panel. */
@Composable
private fun Panel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)

/** What the pup says on the home screen: today done, a first visit, the streak, or the time of day. */
private fun greeting(today: TodayPanel?, practice: String?, goal: GoalState?, fresh: Boolean): String {
    val streak = practice?.let { Regex("(\\d+) days in a row").find(it)?.groupValues?.get(1) }
    val hour = java.time.LocalTime.now().hour
    return when {
        today?.summary != null -> "That's today done. Rest those hands, see you tomorrow."
        fresh -> "Hi, I'm your coach. Let's start with an easy song."
        goal?.met == true -> "That's today's goal. Rest those hands."
        goal != null && goal.atRisk -> "Your ${goal.streak} day streak is waiting. ${goal.line.replaceFirstChar { it.uppercase() }}."
        streak != null -> "$streak days in a row. Let's keep it going."
        hour < 12 -> "Good morning. A few minutes at the keys?"
        hour < 18 -> "Good afternoon. Ready when you are."
        else -> "Good evening. A short session before the day ends?"
    }
}

/** Continue as a plain row under the panel: the song's tile, what was played last and where it stopped, and a round play button. */
@Composable
private fun ContinueRow(e: LibraryEntry, fresh: Boolean, a: LibraryActions, art: SongArt?) {
    val p = e.progress
    val resume = !fresh && p.lastPositionMs > 0 && !p.completed
    Row(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).heightIn(min = 72.dp)
            .pressScale().clip(RoundedCornerShape(16.dp)).clickable { a.onOpen(e.meta) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SongTile(art, locked = !e.available, size = 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (fresh) "Start here" else "Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    fresh -> "An easy first song, melody and bass."
                    resume -> "Paused at ${formatTime(p.lastPositionMs.toDouble())}"
                    p.bestAccuracy >= 0 -> "Best run ${p.bestAccuracy}%"
                    else -> e.meta.composer
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = if (resume) "Resume" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

/** A difficulty group's heading: its stars, its name and size, and how many of its songs have been played through. */
@Composable
private fun GroupHeader(label: String, group: List<LibraryEntry>, stars: Int) {
    val open = group.count { it.available }
    val played = group.count { it.available && (it.progress.completed || it.progress.bestAccuracy >= 0) }
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stars > 0) DifficultyStars(stars, size = 16.dp)
        else Icon(painterResource(R.drawable.ic_library_music), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("${group.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (open > 0) Text("$played of $open played", style = Numeric.merge(MaterialTheme.typography.labelMedium),
            color = if (played > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One song: its tile, title, composer (or the bar to work on), difficulty and length, then where it stands. */
@Composable
private fun SongRow(e: LibraryEntry, a: LibraryActions, art: SongArt?) {
    val m = e.meta
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).pressScale()
                .clip(RoundedCornerShape(16.dp))
                .clickable { if (e.available) a.onOpen(m) else a.onImport(m) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SongTile(art, locked = !e.available)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val trouble = e.progress.troubleBar
                Text(when { !e.available -> "Import its MIDI file to unlock"; trouble != null -> "Work on bar $trouble"; else -> m.composer.ifEmpty { "Imported" } },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (e.available && trouble != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.available) SongFacts(m.difficulty, art?.durationMs, Modifier.padding(top = 2.dp))
                // The piece's other arrangement, one tap away.
                e.pair?.let { other ->
                    TextButton(onClick = { a.onOpen(other) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).semantics { contentDescription = "Open the ${if (other.easy) "easy" else "full"} version" }) {
                        Text(if (other.easy) "Easy version" else "Full version", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Status(e)
            // An imported song could be neither renamed nor removed (a wrong import stayed for good).
            if (m.user && e.available && m.difficulty == "imported") SongMenu(m, a)
        }
    }
}

/** An imported song's own menu: rename it, or delete its file from this phone (asked first). */
@Composable
private fun SongMenu(m: SongMeta, a: LibraryActions) {
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More for ${m.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; renaming = true })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; deleting = true })
        }
    }
    if (renaming) {
        var title by remember { mutableStateOf(m.title) }
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Rename ${m.title}") },
            text = { OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { a.onRename(m, title.trim()); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${m.title}?") },
        text = { Text("Its file is removed from this phone. You can import it again any time.") },
        confirmButton = { TextButton(onClick = { a.onDelete(m); deleting = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep") } })
}

/** Where the song stands: best Perform score in a ring, played through, or the paused position. */
@Composable
private fun Status(e: LibraryEntry) {
    val p = e.progress
    when {
        !e.available -> {}
        p.bestAccuracy >= 0 || p.completed -> BestBadge(p.bestAccuracy.takeIf { it >= 0 }, p.completed)
        p.lastPositionMs > 0 -> Text(formatTime(p.lastPositionMs.toDouble()), style = Numeric.merge(MaterialTheme.typography.labelLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {}                                   // untouched songs carry no label; every song starts that way
    }
}
