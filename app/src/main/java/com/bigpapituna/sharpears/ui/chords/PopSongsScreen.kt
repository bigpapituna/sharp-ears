package com.bigpapituna.sharpears.ui.chords

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopPlay
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.plural
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.practice.PillRow
import com.bigpapituna.sharpears.ui.skin.inkOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.motion.pressScale

/**
 * Songs by chords (popular-song chord mode), the Chords tab's "Songs" section: browse by style, order by what is easiest
 * or newest, or narrow to the songs the chords a profile has learned already play ("Songs I can play", "One new chord
 * away"). Each song is a tile of its chords, difficulty bars and how sure the content is. Chords, keys and tempos only.
 *
 * Takes its list as a parameter: the sample list while the real one is not on this branch (PopCatalogue).
 */

/** Where the browse screen opens: a style chip and a sort (a debug route, and "You can play N songs" taps). */
data class PopStart(val chip: PopFind.Chip = PopFind.Chip.ALL, val sort: PopFind.Sort = PopFind.Sort.EASIEST)

@Composable
fun PopBrowse(
    songs: List<PopSong>, known: Set<Chord>, onOpen: (PopSong) -> Unit,
    modifier: Modifier = Modifier, kid: Boolean = false, start: PopStart = PopStart(),
) {
    if (kid) { PopKidBrowse(songs, onOpen, modifier); return }
    var chip by rememberSaveable { mutableStateOf(start.chip) }
    var sort by rememberSaveable { mutableStateOf(start.sort) }
    val chips = remember(songs) { PopFind.chipsFor(songs) }
    val list = remember(songs, chip, sort, known) { PopFind.browse(songs, chip, sort, known) }
    val playable = remember(songs, known) { PopFind.playableCount(songs, known) }
    val c = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item(key = "head") {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Songs by chords", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Pick a song, see its chords, then play them with the band.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                PlayableLine(playable, Modifier.padding(top = 4.dp)) { sort = PopFind.Sort.CAN_PLAY; chip = PopFind.Chip.ALL }
            }
        }
        item(key = "chips") {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillRow(chips.map { it.label }, chips.indexOf(chip).coerceAtLeast(0), { chip = chips[it] })
                PillRow(PopFind.Sort.entries.map { it.label }, sort.ordinal, { sort = PopFind.Sort.entries[it] })
            }
        }
        if (list.isEmpty()) item(key = "empty") {
            Text(emptyLine(chip, sort), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp, vertical = 28.dp))
        }
        items(list, key = { it.id }) { s ->
            PopSongTile(s, known, Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 12.dp), showNeeds = sort == PopFind.Sort.ONE_AWAY) { onOpen(s) }
        }
    }
}

private fun emptyLine(chip: PopFind.Chip, sort: PopFind.Sort) = when {
    sort == PopFind.Sort.CAN_PLAY -> "No song is ready yet. Practise a few chords in Daily and the songs they play show up here."
    sort == PopFind.Sort.ONE_AWAY -> "No song is one chord away yet."
    chip == PopFind.Chip.ALL -> "No songs yet."
    else -> "No songs in ${chip.label.lowercase()} yet."
}

/** "You can play 3 songs": the count from the chords learned so far; a tap shows them. */
@Composable
fun PlayableLine(count: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val text = if (count == 0) "Learn a few chords and the songs they play appear here." else "You can play ${plural(count, "song")}"
    Row(modifier.clip(RoundedCornerShape(50)).then(if (onClick != null && count > 0) Modifier.clickable(role = Role.Button, onClickLabel = "Show them", onClick = onClick) else Modifier)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (count > 0) Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (count > 0) c.primary else c.onSurfaceVariant)
    }
}

/**
 * A song as a tile: the key in its colour, title, artist and year, the chords it needs (the ones already learned
 * tinted, the others outlined), the difficulty as bars, and how sure the content is. [showNeeds] names the one missing chord.
 */
@Composable
fun PopSongTile(song: PopSong, known: Set<Chord>, modifier: Modifier = Modifier, showNeeds: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val flats = PopPlay.flats(song.key, song.minor)
    val chords = remember(song) { PopFind.chordsOf(song) }
    val missing = remember(song, known) { PopFind.missing(song, known) }
    val four = remember(song) { PopFind.fourChord(song) }
    Row(modifier.pressScale(0.985f).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerLow)
        .clickable(role = Role.Button, onClickLabel = "Open ${song.title}", onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        KeyBadge(song.key, PopPlay.keyName(song.key, song.minor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} · ${song.year}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ChordChips(chords.map { it to PopPlay.name(com.bigpapituna.sharpears.engine.chords.PopChord(it.root, it.type), flats) }, known)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ConfidenceNote(song.confidence)
                if (four) FourChordBadge()
                if (showNeeds && missing.size == 1)
                    Text("needs " + PopPlay.name(com.bigpapituna.sharpears.engine.chords.PopChord(missing[0].root, missing[0].type), flats), style = MaterialTheme.typography.labelMedium,
                        color = c.primary, maxLines = 1)
            }
        }
        DifficultyBars(song.difficulty)
    }
}

/** The song's key as a rounded square in its note colour with the key's name on it. */
@Composable
internal fun KeyBadge(key: Int, label: String, modifier: Modifier = Modifier) {
    val midi = 60 + key
    Box(modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(noteColour(midi)).clearAndSetSemantics { contentDescription = "Key of $label" },
        contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold), color = inkOn(midi), maxLines = 1)
    }
}

/** The chords as small chips, learned ones tinted, up to [max] and a "+n". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChordChips(chords: List<Pair<Chord, String>>, known: Set<Chord>, max: Int = 7) {
    val c = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chords.take(max).forEach { (chord, name) ->
            val have = chord in known
            Text(name, style = MaterialTheme.typography.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
                color = if (have) c.onPrimaryContainer else c.onSurface, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (have) c.primaryContainer else Color.Transparent)
                    .then(if (have) Modifier else Modifier.border(BorderStroke(1.dp, c.outline), RoundedCornerShape(10.dp)))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics { contentDescription = name + if (have) ", you know it" else "" })
        }
        if (chords.size > max) Text("+${chords.size - max}", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    }
}

/** One to four small rising bars, never a number. */
@Composable
fun DifficultyBars(level: Int, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val n = level.coerceIn(1, 4)
    val word = listOf("easy", "gentle", "tricky", "hard")[n - 1]
    Row(modifier.semantics { contentDescription = "Difficulty: $word" }, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 4) Box(Modifier.width(5.dp).height((7 + i * 4).dp).clip(RoundedCornerShape(2.dp)).background(if (i < n) c.primary else c.outlineVariant))
    }
}

/** A quiet "checked" tick, or "close enough" / "a sketch" in plain words. */
@Composable
fun ConfidenceNote(confidence: PopSong.Confidence) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (confidence == PopSong.Confidence.CHECKED) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.tertiary, modifier = Modifier.size(14.dp))
        Text(PopFind.confidenceWord(confidence), style = MaterialTheme.typography.labelMedium,
            color = if (confidence == PopSong.Confidence.CHECKED) c.tertiary else c.onSurfaceVariant)
    }
}

/** For the I, IV, V and vi songs. */
@Composable
fun FourChordBadge() {
    val c = MaterialTheme.colorScheme
    Text("four chords", style = MaterialTheme.typography.labelMedium, color = c.onSecondaryContainer, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp))
}

// --- the kids' list: big blocks, only the kids' songs, no numbers ---

@Composable
private fun PopKidBrowse(songs: List<PopSong>, onOpen: (PopSong) -> Unit, modifier: Modifier) {
    val list = remember(songs) { songs.filter { PopSong.Style.KIDS in it.styles }.sortedWith(PopFind.easiest) }
    val c = MaterialTheme.colorScheme
    LazyVerticalGrid(GridCells.Adaptive(minSize = 200.dp), modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            com.bigpapituna.sharpears.ui.PupSays(Pose.PRACTICING, if (list.isEmpty()) "No chord songs yet." else "Pick a song! Play its colours.",
                Modifier.widthIn(max = 560.dp).padding(bottom = 4.dp), size = 84.dp)
        }
        items(list, key = { it.id }) { s ->
            Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).pressScale().clip(RoundedCornerShape(24.dp)).background(c.primaryContainer)
                .clickable(role = Role.Button, onClickLabel = "Play ${s.title}") { onOpen(s) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PopFind.chordsOf(s).take(5).forEach { ch -> ChordDot(ch.root) }
                }
            }
        }
    }
}

/** A round dot in a chord's note colour, for a kid's tile. */
@Composable
internal fun ChordDot(root: Int) {
    Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(noteColour(60 + root)))
}

// --- cards that lead here ---

/** The Songs tab's card for grown-ups: songs by chords, with how many are ready. */
@Composable
fun PopSongsCard(playable: Int, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Open songs by chords", onClick = onOpen).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(painterResource(R.drawable.ic_library_music), contentDescription = null, tint = c.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Songs by chords", style = MaterialTheme.typography.titleMedium)
            Text(if (playable > 0) "You can play ${plural(playable, "song")} already" else "Popular songs, just their chords, with a band",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(180f))
    }
}

/**
 * After a chord pack is finished: songs that use only chords the profile knows (the pack's included), the easiest first.
 * Draws nothing when there is none. [onOpen] gets a song; [onMore] opens the list sorted to what can be played.
 */
@Composable
fun SongsForChordsCard(songs: List<PopSong>, chords: Set<Chord>, modifier: Modifier = Modifier, onOpen: (PopSong) -> Unit, onMore: () -> Unit = {}) {
    val found = remember(songs, chords) { PopFind.using(songs, chords, limit = 3) }
    if (found.isEmpty()) return
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Songs that use these chords", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp).semantics { heading() })
        found.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${s.title}") { onOpen(s) }.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyBadge(s.key, PopPlay.keyName(s.key, s.minor), Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DifficultyBars(s.difficulty)
            }
        }
        androidx.compose.material3.TextButton(onClick = onMore, modifier = Modifier.padding(horizontal = 10.dp)) { Text("See all the songs you can play") }
    }
}
