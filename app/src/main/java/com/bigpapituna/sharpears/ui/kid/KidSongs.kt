package com.bigpapituna.sharpears.ui.kid

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.kid.KidSongs
import com.bigpapituna.sharpears.engine.kid.ShelfSong
import com.bigpapituna.sharpears.engine.kid.SongFacts
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.skin.drawAnimal
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.world.WorldInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A kid song: its library entry and the tune's opening, as keys to colour. */
private class KidSong(val meta: SongMeta, val opening: List<Int>)

/** What the songs' notes say about them, read once per run of the app (the library's notes do not change). */
private val factsCache = HashMap<String, Pair<SongFacts, List<Int>>>()

/**
 * The kid's songs (2.2): only the songs played by colour ([KidSongs]: easy, the app's own, every note a white key, so
 * each is one colour and one animal), as big tiles with the tune's first notes in their colours and the name. No
 * stats, no records, no imports. A tap opens the song in the player (wait mode, colour notes on).
 *
 * 2.4, the parent brief: songs a grown-up put on the [shelf] (Teacher mode) come first, each with the grown-up's note
 * (tap the note and [say] reads it aloud: it is made on the phone, like every line with a name); [duetWith] names the
 * grown-up when the song picked here opens as a duet with them (Practise together).
 *
 * 2.6, songs by chords: [onChordSongs] puts one more tile after the shelf, "Songs by chords", that opens the kids' chord
 * songs (ui/chords, the kid page: colour blocks, letters for a [reader], animals for a child who cannot read yet).
 */
@Composable
fun KidSongsScreen(songs: List<SongMeta>, repo: SongRepository, onSong: (SongMeta) -> Unit, onBack: () -> Unit,
                   shelf: List<ShelfSong> = emptyList(), duetWith: String? = null, say: ((String) -> Unit)? = null,
                   onChordSongs: (() -> Unit)? = null, reader: Boolean = true) {
    val assigned = remember(shelf, songs) { shelf.mapNotNull { s -> songs.firstOrNull { it.id == s.songId && repo.available(it) }?.let { it to s.note } } }
    var list by remember { mutableStateOf<List<KidSong>?>(null) }
    LaunchedEffect(songs) {
        list = withContext(Dispatchers.Default) {
            val facts = songs.mapNotNull { m ->
                factsCache[m.id] ?: runCatching {
                    val available = repo.available(m)
                    val song = if (available && !m.user && m.difficulty == "easy") repo.load(m) else null
                    val f = SongFacts(m.id, m.title, m.difficulty, m.user, available, song?.notes?.map { it.midi }.orEmpty())
                    val opening = KidSongs.opening(song?.notes.orEmpty().filter { it.hand == Hand.RIGHT }.map { it.startMs to it.midi })
                    (f to opening).also { factsCache[m.id] = it }
                }.onFailure { Log.d(TAG, "kid songs: ${m.id} unreadable: $it") }.getOrNull()
            }
            // In the kid order (simplest first), not the library's.
            val picked = KidSongs.pick(facts.map { it.first }).map { it.id }
            Log.d(TAG, "kid songs: ${picked.size} of ${songs.size} play by colour: ${picked.joinToString()}")
            val byId = facts.associateBy { it.first.id }
            picked.mapNotNull { id -> songs.firstOrNull { it.id == id }?.let { m -> KidSong(m, byId.getValue(id).second) } }
        }
    }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to the map", tint = c.onSurface, modifier = Modifier.size(30.dp))
            }
            Text("Songs", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        LazyVerticalGrid(GridCells.Adaptive(minSize = 200.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PupSays(Pose.PRACTICING, when {
                    duetWith != null -> "Pick a song to play with $duetWith!"
                    list?.isEmpty() == true && assigned.isEmpty() -> "No colour songs on this phone yet."
                    else -> "Pick a song! Follow the colours."
                }, Modifier.widthIn(max = 560.dp).padding(bottom = 4.dp), size = 84.dp)
            }
            items(assigned, key = { "shelf:" + it.first.id }) { (m, note) -> ShelfTile(m, note, say) { onSong(m) } }
            // Not while a grown-up waits for a duet: a chord song has no duet.
            if (onChordSongs != null && duetWith == null) item(key = "chordsongs") { ChordSongsTile(reader, onChordSongs) }
            items(list.orEmpty().filter { s -> assigned.none { it.first.id == s.meta.id } }, key = { it.meta.id }) { s -> SongTile(s) { onSong(s.meta) } }
        }
    }
}

/** A song a grown-up put on the shelf: "From your grown-up", its name, big, and their note (tap to hear it). */
@Composable
private fun ShelfTile(meta: SongMeta, note: String, say: ((String) -> Unit)?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).pressScale().clip(RoundedCornerShape(24.dp)).background(c.primaryContainer)
        .clickable(role = Role.Button, onClickLabel = "Play ${meta.title}", onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("From your grown-up", style = MaterialTheme.typography.labelLarge, color = c.onPrimaryContainer.copy(alpha = 0.8f))
        Text(meta.title, style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(if (say != null) Modifier.clickable(role = Role.Button, onClickLabel = "Hear the note") { say(note) } else Modifier)
                .background(c.surface.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * The way into the kids' chord songs: three chord blocks in their colours (C, F and G, the first chords most of them use),
 * with the letters for a reader and the animals for a child who cannot read yet, then the name.
 */
@Composable
private fun ChordSongsTile(reader: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).pressScale().clip(RoundedCornerShape(24.dp)).background(c.secondaryContainer)
        .clickable(role = Role.Button, onClickLabel = "Open songs by chords", onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 65, 67).forEach { midi ->
                val colour = Color(NoteColours.colour(midi))
                val ink = Color(NoteColours.inkFor(NoteColours.colour(midi)))
                val animal = NoteColours.animal(midi)
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp)).background(colour), contentAlignment = Alignment.Center) {
                    if (!reader && animal != null) Canvas(Modifier.size(36.dp)) { drawAnimal(animal, center, size.minDimension, ink, colour) }
                    else Text(noteLetter(midi), style = MaterialTheme.typography.titleLarge, color = ink)
                }
            }
        }
        Text("Songs by chords", style = MaterialTheme.typography.titleLarge, color = c.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun noteLetter(midi: Int) = listOf("C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B")[Math.floorMod(midi, 12)]

/** One song: its opening notes as coloured balls on a little line, then its name, big. */
@Composable
private fun SongTile(song: KidSong, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).pressScale().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Play ${song.meta.title}", onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) { OpeningBalls(song.opening) }
        Text(song.meta.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The tune's first notes as balls in their colours, higher notes a little higher, on a faint line. */
@Composable
private fun OpeningBalls(keys: List<Int>) {
    if (keys.isEmpty()) return
    val lo = keys.min(); val hi = keys.max()
    val colours = keys.map(::noteColour)
    Canvas(Modifier.fillMaxWidth().height(52.dp)) {
        val r = minOf(size.height * 0.24f, size.width / (keys.size * 2.6f))
        val step = (size.width - 2 * r) / maxOf(1, keys.size - 1).toFloat()
        drawLine(Color.Gray.copy(alpha = 0.25f), Offset(0f, size.height - r * 0.6f), Offset(size.width, size.height - r * 0.6f), r * 0.12f)
        keys.forEachIndexed { i, k ->
            val lift = if (hi == lo) 0.5f else (k - lo).toFloat() / (hi - lo)
            val cy = size.height - r * 1.2f - lift * (size.height - r * 2.4f)
            val center = Offset(r + i * step, cy)
            drawCircle(colours[i], r, center)
            drawCircle(WorldInk.copy(alpha = 0.6f), r, center, style = Stroke(r * 0.12f))
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.32f, center + Offset(-r * 0.3f, -r * 0.3f))
        }
    }
}
