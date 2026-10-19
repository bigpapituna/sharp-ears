package com.bigpapituna.sharpears.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.engine.world.Medal
import com.bigpapituna.sharpears.engine.world.Records
import com.bigpapituna.sharpears.engine.world.ShelfRecord
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SongTile
import com.bigpapituna.sharpears.ui.family.isKid
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.rememberSongArts

/** The record label's colour per medal (data, like rarity). */
fun Medal.colour(): Color = when (this) { Medal.BRONZE -> Color(0xFFC98A55); Medal.SILVER -> Color(0xFFBAC3D0); Medal.GOLD -> Color(0xFFE9B83C) }

/** A vinyl record: black grooves, a label in the medal's colour with a little star, the hole in the middle. */
@Composable
fun MedalDisc(medal: Medal, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val c = center; val r = size.minDimension / 2f
        drawCircle(WorldInk, r, c)
        drawCircle(Color(0xFF2B2628), r * 0.93f, c)
        for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.07f), r * (0.5f + 0.1f * k), c, style = Stroke(r * 0.02f))
        drawArc(Color.White.copy(alpha = 0.16f), 200f, 50f, false, Offset(c.x - r * 0.78f, c.y - r * 0.78f), Size(r * 1.56f, r * 1.56f), style = Stroke(r * 0.07f))
        drawCircle(medal.colour(), r * 0.42f, c)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), Offset(c.x - r * 0.15f, c.y - r * 0.15f), r * 0.4f), r * 0.42f, c)
        sparkle(Offset(c.x, c.y - r * 0.22f), r * 0.12f, Color.White, alpha = 0.9f)
        drawCircle(Color(0xFF2B2628), r * 0.06f, c)
    }
}

/** "3 gold, 2 silver, 1 bronze" (only the medals there are). */
fun medalLine(bests: Map<String, Int>): String = Records.counts(bests).entries.reversed().filter { it.value > 0 }
    .joinToString(", ") { "${it.value} ${it.key.label.lowercase()}" }

/**
 * The records shelf: every song with a medal stands on wooden shelves, three to a shelf, its tile from the library as
 * the sleeve with the record peeking out in bronze, silver or gold; then "Songs I can play" as a list. Tapping one
 * opens it ([onSong]). [bests] are best Perform accuracies per song id (WorldState.records).
 */
@Composable
fun RecordsShelf(bests: Map<String, Int>, songs: List<SongMeta>, modifier: Modifier = Modifier, onSong: (SongMeta) -> Unit = {}) {
    val kid = isKid()
    val shelf = remember(bests) { Records.shelf(bests) }
    val byId = remember(songs) { songs.associateBy { it.id } }
    val shown = shelf.filter { it.songId in byId }
    val arts = rememberSongArts(shown.mapNotNull { byId[it.songId] })
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (shown.isEmpty()) {
            PupSays(Pose.POINTING, if (kid) "Play a whole song in Perform and win your first record." else "Finish a song in Perform at 60% or more for your first record.", size = 84.dp, idle = false)
            return@Column
        }
        PupSays(Pose.CROWN, shelfLine(shown.size, medalLine(bests.filterKeys { it in byId }), kid), size = 84.dp)
        shown.chunked(3).forEach { row ->
            // The records stand on the board; their names hang under it.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    row.forEach { r -> RecordOnShelf(r, byId.getValue(r.songId), arts[r.songId], Modifier.weight(1f), onSong) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
                Plank()
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        Text(byId.getValue(r.songId).title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Text("Songs I can play", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp).semantics { heading() })
        shown.forEach { r ->
            val m = byId.getValue(r.songId)
            val medal = r.medal!!
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onSong(m) }
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${m.title}, ${medal.label.lowercase()} record" + if (kid) "" else ", best ${r.best} percent" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MedalDisc(medal, Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(medal.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!kid) Text("${r.best}%", style = Numeric.merge(MaterialTheme.typography.titleMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One record standing on the shelf: the song's tile as its sleeve, the disc peeking out, the title under it. */
@Composable
private fun RecordOnShelf(r: ShelfRecord, m: SongMeta, art: com.bigpapituna.sharpears.engine.SongArt?, modifier: Modifier, onSong: (SongMeta) -> Unit) {
    val medal = r.medal ?: return
    Column(modifier.pressScale().clickable(role = Role.Button) { onSong(m) }.semantics(mergeDescendants = true) {
        contentDescription = "${m.title}, ${medal.label.lowercase()} record"; role = Role.Button
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(104.dp).height(80.dp)) {
            MedalDisc(medal, Modifier.size(74.dp).offset(x = 28.dp, y = 3.dp))
            SongTile(art, locked = false, size = 80.dp)
        }
    }
}

/** A wooden shelf board with a soft shadow under it. */
@Composable
private fun Plank() {
    Canvas(Modifier.fillMaxWidth().height(18.dp).clearAndSetSemantics { }) {
        drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(6f, size.height * 0.55f), Size(size.width - 12f, size.height * 0.5f), CornerRadius(8f))
        drawRoundRect(WorldInk, Offset(0f, 0f), Size(size.width, size.height * 0.62f), CornerRadius(6f))
        drawRoundRect(Color(0xFFC99A66), Offset(3f, 3f), Size(size.width - 6f, size.height * 0.62f - 6f), CornerRadius(4f))
        drawLine(Color(0xFFB5844F), Offset(10f, size.height * 0.31f), Offset(size.width - 10f, size.height * 0.31f), 2f)
    }
}

/** The pup's line over the shelf: counts and medals for a grown-up, no numbers for a kid ("2 records: 1 gold" was). */
internal fun shelfLine(records: Int, medals: String, kid: Boolean): String =
    if (kid) "Look at your records on the shelf!" else "$records ${if (records == 1) "record" else "records"} on the shelf: $medals."
