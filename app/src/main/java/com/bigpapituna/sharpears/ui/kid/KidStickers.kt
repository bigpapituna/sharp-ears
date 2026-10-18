package com.bigpapituna.sharpears.ui.kid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.Medal
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.skin.PawCalendarCard
import com.bigpapituna.sharpears.ui.world.AlbumView
import com.bigpapituna.sharpears.ui.world.DressUp
import com.bigpapituna.sharpears.ui.world.MedalDisc
import com.bigpapituna.sharpears.ui.world.RecordsShelf
import com.bigpapituna.sharpears.ui.world.WorldInk
import kotlinx.coroutines.launch

/** The pages of My stickers, in order. */
enum class StickerPage(val label: String) { ALBUM("Stickers"), DRESS("Dress up"), RECORDS("Records"), PAWS("My week") }

/**
 * My stickers (2.2), the kid home's third button: one simple screen of pages to swipe through, the album first, then
 * dress the pup, the records shelf, and this week's paws. Four big round picture tabs at the top show where you are
 * and jump there. The world team's pieces, as they are (ui/world, ui/skin); [onSong] opens a song from the shelf.
 */
@Composable
fun KidStickersScreen(world: WorldStore, songs: List<SongMeta>, onBack: () -> Unit, start: StickerPage = StickerPage.ALBUM, onSong: (SongMeta) -> Unit = {}) {
    val pager = rememberPagerState(initialPage = start.ordinal) { StickerPage.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { android.util.Log.d(com.bigpapituna.sharpears.TAG, "kid stickers: page ${StickerPage.entries[pager.currentPage]}") }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to the map", tint = c.onSurface, modifier = Modifier.size(30.dp))
            }
            Text("My stickers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            StickerPage.entries.forEach { p ->
                PageTab(p, selected = pager.currentPage == p.ordinal) { scope.launch { pager.animateScrollToPage(p.ordinal) } }
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (StickerPage.entries[i]) {
                    StickerPage.ALBUM -> AlbumView(world.state.album)
                    StickerPage.DRESS -> DressUp(world)
                    StickerPage.RECORDS -> RecordsShelf(world.state.records, songs, onSong = onSong)
                    StickerPage.PAWS -> {
                        PupSays(Pose.WELCOME, "Every day you play, a paw lands here.", Modifier.widthIn(max = 560.dp), size = 84.dp)
                        PawCalendarCard(kid = true, modifier = Modifier.widthIn(max = 560.dp))
                    }
                }
            }
        }
    }
}

/** A page's round picture tab: the pup with a sticker, the pup dressed, a gold record, a paw. */
@Composable
private fun PageTab(page: StickerPage, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable(role = Role.Tab, onClick = onClick).padding(4.dp).semantics { this.selected = selected; contentDescription = page.label }) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
            .border(3.dp, if (selected) PupGold else c.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            when (page) {
                StickerPage.ALBUM -> Image(painterResource(Pose.STICKER.res), null, Modifier.size(50.dp))
                StickerPage.DRESS -> Image(painterResource(Pose.WEAR_BASE.res), null, Modifier.size(50.dp))
                StickerPage.RECORDS -> MedalDisc(Medal.GOLD, Modifier.size(40.dp))
                StickerPage.PAWS -> Canvas(Modifier.size(34.dp)) { drawPawAt(Offset(size.width / 2, size.height / 2), size.width, WorldInk.copy(alpha = 0.75f), filled = true) }
            }
        }
        Text(page.label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onSurface else c.onSurfaceVariant)
    }
}
