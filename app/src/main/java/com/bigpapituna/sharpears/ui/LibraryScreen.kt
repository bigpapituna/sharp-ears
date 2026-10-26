package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.bigpapituna.sharpears.engine.MySongs
import com.bigpapituna.sharpears.engine.SongArt
import com.bigpapituna.sharpears.engine.nav.Simple
import com.bigpapituna.sharpears.ui.chords.popBrowseItems
import com.bigpapituna.sharpears.ui.motion.pressScale

/** One library row: the song, whether its file is present, and saved progress. */
data class LibraryEntry(val meta: SongMeta, val available: Boolean, val progress: SongProgress,
                        /** The other arrangement (easy or full) when the piece has one. */
                        val pair: SongMeta? = null)

class LibraryActions(
    val onOpen: (SongMeta) -> Unit,
    /** Pick a MIDI file; [into] is the locked entry it unlocks, or null for a new song. */
    val onImport: (into: SongMeta?) -> Unit,
    /** An imported song's menu: a new title (its scores stay), or its file removed from this phone. */
    val onRename: (SongMeta, String) -> Unit = { _, _ -> },
    val onDelete: (SongMeta) -> Unit = {},
    /** My songs' "Add a song from a file" (2.8.1): the picker, then the title dialog. */
    val onAdd: () -> Unit = {},
)

/**
 * My songs' rows (2.14): the songs added from files, newest first, then the older imports. A bundled entry that names a file
 * the learner has not brought ("Alicia (Clair Obscur)", Roy's own, locked on every fresh install) is not listed until that
 * file exists (Dana, Joan: a locked soundtrack row on a stranger's phone).
 */
internal fun myShelf(entries: List<LibraryEntry>): List<LibraryEntry> =
    entries.filter { it.meta.mine } + entries.filter { it.meta.user && it.available }

private val GROUPS = listOf("easy" to "Easy", "medium" to "Medium", "hard" to "Hard")

/** The Songs tab's two sections (2.7): the library, and songs by chords (the Chords tab's Songs before). */
enum class SongsSection(val label: String) { LIBRARY("Library"), BY_CHORDS(com.bigpapituna.sharpears.engine.nav.Names.SONGS_BY_CHORDS) }

/**
 * Songs by chords in the Songs tab: the catalogue, the chords known (Leitner box 3), where the list opens, and the song a tap
 * opens with the list's choices at that moment (so Back from the song finds them again).
 */
class PopSection(val songs: List<com.bigpapituna.sharpears.engine.chords.PopSong>, val known: Set<com.bigpapituna.sharpears.engine.Chord>,
                 val start: com.bigpapituna.sharpears.ui.chords.PopStart,
                 val onOpen: (com.bigpapituna.sharpears.engine.chords.PopSong, com.bigpapituna.sharpears.ui.chords.PopStart) -> Unit)

/**
 * The Songs tab (2.7): everything playable in one place. The Play corner at the top (just for fun), then one switch between
 * the library (Start here on a fresh install, My songs with Add a song from a file, rename and delete, then plain rows
 * grouped by difficulty) and songs by chords ([pop]). [played] says whether any song was opened yet: until then the library starts with an easy
 * first song. Today is the home since 2.7; its Continue and plan used to sit here.
 */
@Composable
fun LibraryScreen(entries: List<LibraryEntry>, played: Boolean, a: LibraryActions,
                  /** The Play corner (ui/play/PlayRow): the Jam, Pup says and Clap along. */
                  play: (@Composable () -> Unit)? = null,
                  /** Songs by chords (ui/chords), the tab's second section; null while there are no pop songs. */
                  pop: PopSection? = null, section: SongsSection = SongsSection.LIBRARY, onSection: (SongsSection) -> Unit = {}) {
    val first = if (played) null else entries.firstOrNull { it.available && it.meta.difficulty == "easy" }
    // Tile art and lengths, read off the main thread and filled in as they arrive.
    val arts = rememberSongArts(remember(entries) { entries.map { it.meta } })
    val popState = pop?.let { com.bigpapituna.sharpears.ui.chords.rememberPopBrowseState(it.start) }
    // The layouts (2.8, engine/nav/Simple.kt): Simple keeps four-chord and easiest songs, until "Show more"; Advanced has them all.
    val layout = LocalLayout.current.layout
    var morePop by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val popSongs = remember(pop?.songs, layout, morePop) {
        pop?.songs?.let { com.bigpapituna.sharpears.engine.nav.Simple.popSongs(it, com.bigpapituna.sharpears.engine.nav.Simple.shown(layout, morePop)) }.orEmpty()
    }
    val popModel = if (pop != null && popState != null) com.bigpapituna.sharpears.ui.chords.rememberPopBrowseModel(popSongs, pop.known, popState) else null
    val showPop = section == SongsSection.BY_CHORDS && pop != null && popState != null && popModel != null
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "head") {
            Text(com.bigpapituna.sharpears.engine.nav.Names.SONGS, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp).semantics { heading() })
        }
        play?.let { p -> item(key = "play") { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { p() } } }
        // The two sections, one switch: the library, or songs by chords.
        if (pop != null) item(key = "sections") {
            com.bigpapituna.sharpears.ui.practice.SlidingSegments(SongsSection.entries.map { it.label }, section.ordinal, { onSection(SongsSection.entries[it]) },
                Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), height = 40.dp)
        }
        if (showPop && pop != null && popState != null && popModel != null) {
            popBrowseItems(popModel, popState, pop.known, onOpen = { s -> pop.onOpen(s, popState.now) }, title = false)
            item(key = "pop-more") { LayoutMore(com.bigpapituna.sharpears.engine.nav.Simple.Place.SONGS_BY_CHORDS, morePop, { morePop = true }) }
            return@LazyColumn
        }
        // Nothing played yet: an easy first song first.
        first?.let { e -> item(key = "start") { StartHereRow(e, a, arts[e.meta.id]) } }
        // My songs (2.8.1): the songs the learner added from files (and the older imports), in every layout.
        if (Simple.songsShows(Simple.SongsPart.MY_SONGS, layout)) {
            val mine = myShelf(entries)
            item(key = "h-mine") { GroupHeader(MySongs.Words.SECTION, mine, stars = 0) }
            if (mine.none { it.available }) item(key = "mine-empty") {
                Text(MySongs.Words.EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
            }
            items(mine, key = { it.meta.id }) { SongRow(it, a, arts[it.meta.id]) }
            if (Simple.songsShows(Simple.SongsPart.ADD_SONG, layout)) item(key = "mine-add") { AddSongButton(a) }
        }
        GROUPS.forEach { (key, label) ->
            val group = entries.filter { !it.meta.user && it.meta.difficulty == key }
            if (group.isEmpty()) return@forEach
            item(key = "h-$key") { GroupHeader(label, group, stars = SongArt.stars(key)) }
            items(group, key = { it.meta.id }) { SongRow(it, a, arts[it.meta.id]) }
        }
    }
}

/** My songs' one button: pick a MIDI file (the title dialog follows), and where the file stays. */
@Composable
private fun AddSongButton(a: LibraryActions) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
        TextButton(onClick = a.onAdd) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(MySongs.Words.ADD)
        }
        Text(MySongs.Words.NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 480.dp))
    }
}

/** "Start here" before any song was played: an easy first song's tile, why it is a good start, and a round play button. */
@Composable
private fun StartHereRow(e: LibraryEntry, a: LibraryActions, art: SongArt?) {
    Row(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).heightIn(min = 72.dp)
            .pressScale().clip(RoundedCornerShape(16.dp)).clickable { a.onOpen(e.meta) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SongTile(art, locked = !e.available, size = 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Start here", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(e.meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("An easy first song, melody and bass.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
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
        // The songs that open (a locked import is not one), as "N of M played" counts them.
        Text("${if (open > 0) open else group.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (open > 0) Text("$played of $open played", style = Numeric.merge(MaterialTheme.typography.labelMedium),
            color = if (played > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One song: its tile, title, composer (or the bar to work on; My songs: its hands), difficulty and length, then where it
 * stands. A song of your own (My songs, an older import) has a menu: its overflow button, or a long press on the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(e: LibraryEntry, a: LibraryActions, art: SongArt?) {
    val m = e.meta
    // An imported song could be neither renamed nor removed (a wrong import stayed for good).
    val ownMenu = (m.user && e.available && m.difficulty == "imported") || m.mine
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).pressScale()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onLongClick = if (ownMenu) ({ menu = true }) else null,
                    onLongClickLabel = if (ownMenu) "Rename or delete" else null) {
                    when { e.available -> a.onOpen(m); m.mine -> a.onAdd(); else -> a.onImport(m) }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SongTile(art, locked = !e.available)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val trouble = e.progress.troubleBar
                Text(when {
                        !e.available -> "Import its MIDI file to unlock"
                        trouble != null -> "Work on bar $trouble"
                        m.mine -> if (m.bothHands) MySongs.Words.BOTH_HANDS else MySongs.Words.MELODY_ONLY
                        else -> m.composer.ifEmpty { "Imported" }
                    },
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
            if (ownMenu) SongMenu(m, a, menu) { menu = it }
        }
    }
}

/** A song of your own's menu ([open], from its button or a long press): rename it, or delete its file from this phone (asked once). */
@Composable
private fun SongMenu(m: SongMeta, a: LibraryActions, open: Boolean, onOpen: (Boolean) -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onOpen(true) }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More for ${m.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { onOpen(false) }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { onOpen(false); renaming = true })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { onOpen(false); deleting = true })
        }
    }
    if (renaming) {
        // The caret starts after the old title, so typing adds to it (QA D8: it started before it, "Elise for meFur Elise").
        var field by remember { mutableStateOf(caretAtEnd(m.title)) }
        val title = field.text
        AlertDialog(onDismissRequest = { renaming = false }, title = { Text("Rename ${m.title}") },
            text = { OutlinedTextField(field, { field = it.copy(text = it.text.take(MySongs.TITLE_MAX)) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { a.onRename(m, title.trim()); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${m.title}?") },
        text = { Text(if (m.mine) MySongs.Words.DELETE_LINE else "Its file is removed from this phone. You can import it again any time.") },
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
        p.lastPositionMs > 0 -> Text("Paused at " + formatTime(p.lastPositionMs.toDouble()), style = Numeric.merge(MaterialTheme.typography.labelLarge),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {}                                   // untouched songs carry no label; every song starts that way
    }
}
