package com.bigpapituna.sharpears.ui.world

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.House
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.Scenery
import com.bigpapituna.sharpears.engine.world.TreatJar
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.motion.pressScale

/** The open profile's world, for any screen (the integrator provides it from MainActivity's openApp). Null in previews. */
val LocalWorld = staticCompositionLocalOf<WorldStore?> { null }

/** The world screen's tabs. */
enum class WorldTab(val label: String) { WORLD("World"), ALBUM("Album"), DRESS("Dress up"), RECORDS("Records") }

/** The line under the garden: how the dog house grows. */
fun houseLine(unique: Int): String = when (val n = House.toNext(unique)) {
    null -> "The dog house is finished. Chimney, flag and porch light."
    else -> "$n more ${if (n == 1) "sticker" else "stickers"} and the dog house grows."
}

/**
 * The pup's world as one pushed screen (from the You tab): the world (a scene per place with the dog house, the
 * stuck stickers, the audience; the treat jar and the egg under it), the album, dress-up and the records shelf.
 * [onSong] opens a song from the shelf.
 */
@Composable
fun WorldScreen(world: WorldStore, songs: List<SongMeta>, onBack: () -> Unit, start: WorldTab = WorldTab.WORLD, onSong: (SongMeta) -> Unit = {}) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(start) }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = c.onSurface) }
            Text("Pup's world", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            Text(world.state.album.line, style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            WorldTab.entries.forEachIndexed { i, t ->
                SegmentedButton(selected = tab == t, onClick = { tab = t }, shape = SegmentedButtonDefaults.itemShape(i, WorldTab.entries.size),
                    icon = {}, label = { Text(t.label, maxLines = 1) })
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tab) {
                WorldTab.WORLD -> WorldTabBody(world)
                WorldTab.ALBUM -> AlbumView(world.state.album)
                WorldTab.DRESS -> DressUp(world)
                WorldTab.RECORDS -> RecordsShelf(world.state.records, songs, onSong = onSong)
            }
        }
    }
}

@Composable
private fun WorldTabBody(world: WorldStore) {
    val s = world.state
    var place by rememberSaveable { mutableStateOf(Place.GARDEN) }
    var open by remember { mutableStateOf<com.bigpapituna.sharpears.engine.world.AlbumSlot?>(null) }
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Place.entries.forEach { p ->
            FilterChip(selected = place == p, onClick = { place = p },
                label = { Text("${p.title}  ${p.sceneLabel(s)}", maxLines = 1) },
                leadingIcon = { Box(Modifier.size(10.dp).background(p.colour(), CircleShape)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer))
        }
    }
    PlaceScene(place, s, Modifier.widthIn(max = 720.dp),
        onSticker = { st -> open = com.bigpapituna.sharpears.engine.world.AlbumSlot(st, s.history.count(st.id)) })
    if (place == Place.GARDEN) Text(houseLine(s.unique), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (place == Place.CONCERT) Text(
        Scenery.audience(s.history.owned).size.let { n ->
            if (n == 0) "The seats fill up with the animals you collect." else "$n of ${Scenery.SEATS} seats taken by the animals you collected."
        },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TreatJarPanel(world)
    if (s.egg) Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        EggView(EggLook.CLOSED, Modifier.size(56.dp, 72.dp))
        Text("A mystery egg. It hatches when you finish your next lesson.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
    open?.let { StickerSheet(it, onClose = { open = null }) }
}

/**
 * The world on the You tab: the garden in small with the dog house, how many stickers, the jar filling, and a way into
 * each part. One raised panel; [onOpen] opens [WorldScreen] at a tab.
 */
@Composable
fun WorldCard(world: WorldStore, onOpen: (WorldTab) -> Unit, modifier: Modifier = Modifier) {
    val s = world.state
    val c = MaterialTheme.colorScheme
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pup's world", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            Text(s.album.line, style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "Open the world") { onOpen(WorldTab.WORLD) }) {
            PlaceScene(Place.GARDEN, s, Modifier.fillMaxWidth(), stop = { })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorldTab.entries.drop(1).forEach { t ->
                Column(Modifier.weight(1f).pressScale().clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHighest)
                    .clickable(role = Role.Button) { onOpen(t) }.padding(vertical = 10.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (t) {
                        WorldTab.ALBUM -> Pup(Pose.STICKER, size = 44.dp, idle = false)
                        WorldTab.DRESS -> Pup(Pose.WEAR_BASE, size = 44.dp, idle = false)
                        else -> MedalDisc(com.bigpapituna.sharpears.engine.world.Medal.GOLD, Modifier.size(40.dp))
                    }
                    Text(t.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // The jar, as a thin bar: how close the pup's next treat is.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = if (TreatJar.full(s.jar)) "Treat jar full" else "Treat jar filling" }) {
            TreatJarView(s.jar, Modifier.size(26.dp, 32.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(c.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(TreatJar.fraction(s.jar)).height(8.dp).clip(CircleShape).background(c.primary))
            }
            Text(if (TreatJar.full(s.jar)) "Full" else "Treat jar", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
    }
}
