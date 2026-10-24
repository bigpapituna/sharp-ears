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
import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.world.Decal
import com.bigpapituna.sharpears.engine.world.Medal
import com.bigpapituna.sharpears.ui.world.StickerImage
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.skin.PawCalendarCard
import com.bigpapituna.sharpears.ui.world.AlbumView
import com.bigpapituna.sharpears.ui.world.DressUp
import com.bigpapituna.sharpears.ui.world.MedalDisc
import com.bigpapituna.sharpears.ui.world.RecordsShelf
import com.bigpapituna.sharpears.ui.world.WorldInk
import kotlinx.coroutines.launch
import com.bigpapituna.sharpears.ui.pup.Pup

/** The pages of My stickers, in order. */
enum class StickerPage(val label: String) { ALBUM("Stickers"), DRESS("Dress up"), RECORDS("Records"), PAWS("My week") }

/**
 * My stickers (2.2), the kid home's third button: one simple screen of pages to swipe through, the album first, then
 * dress the pup, the records shelf, and this week's paws. Four big round picture tabs at the top show where you are
 * and jump there. The world team's pieces, as they are (ui/world, ui/skin); [onSong] opens a song from the shelf.
 */
@Composable
fun KidStickersScreen(world: WorldStore, songs: List<SongMeta>, onBack: () -> Unit, start: StickerPage = StickerPage.ALBUM, onSong: (SongMeta) -> Unit = {},
                      /** 2.5: a sticker tapped in the tray: off to the map in decorate mode, holding it (null: no tray). */
                      onDecorate: ((String) -> Unit)? = null, reader: Boolean = true) {
    val pager = rememberPagerState(initialPage = start.ordinal) { StickerPage.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { android.util.Log.d(com.bigpapituna.sharpears.TAG, "kid stickers: page ${StickerPage.entries[pager.currentPage]}") }
    val c = MaterialTheme.colorScheme
    val tray = world.state.tray.mapNotNull(Decal::byKey)
    val next = world.state.nextOutfit
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
                PageTab(p, selected = pager.currentPage == p.ordinal, ring = if (p == StickerPage.DRESS) next?.share else null) { scope.launch { pager.animateScrollToPage(p.ordinal) } }
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { i ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (StickerPage.entries[i]) {
                    StickerPage.ALBUM -> AlbumView(world.state)
                    StickerPage.DRESS -> DressUp(world, reader = reader)
                    StickerPage.RECORDS -> RecordsShelf(world.state.records, songs, onSong = onSong)
                    StickerPage.PAWS -> {
                        PupSays(Pose.WELCOME, "Every day you play, a paw lands here.", Modifier.widthIn(max = 560.dp), size = 84.dp)
                        PawCalendarCard(kid = true, modifier = Modifier.widthIn(max = 560.dp))
                    }
                }
            }
        }
        // The tray belongs to the album (QA 2.8.3: it sat under Dress up, Records and My week too, taking their room).
        if (onDecorate != null && tray.isNotEmpty() && pager.currentPage == StickerPage.ALBUM.ordinal) StickerTray(tray, onDecorate)
    }
}

/**
 * The tray at the bottom of My stickers (2.5): the stickers not stuck on the map yet, newest first, each wiggling a
 * little ("Put me somewhere!"). A tap takes that sticker to the map in decorate mode.
 */
@Composable
private fun StickerTray(tray: List<Decal>, onDecorate: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val still = com.bigpapituna.sharpears.ui.reducedMotion()
    val wiggle = if (still) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "tray")
        .animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "w")
    Column(Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(KidLines.PUT_ME, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tray, key = { it.key }) { s ->
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClickLabel = "Stick it on the map") { onDecorate(s.key) }
                    .semantics { contentDescription = s.name + ", put me somewhere" }.padding(8.dp), contentAlignment = Alignment.Center) {
                    StickerImage(s, Modifier.fillMaxSize().graphicsLayer { rotationZ = 5f * (wiggle?.value ?: 0f) * (if (s.key.hashCode() % 2 == 0) 1f else -1f) })
                }
            }
        }
    }
}

/** A page's round picture tab: the pup with a sticker, the pup dressed, a gold record, a paw. */
@Composable
private fun PageTab(page: StickerPage, selected: Boolean, ring: Float? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable(role = Role.Tab, onClick = onClick).padding(4.dp).semantics { this.selected = selected; contentDescription = page.label }) {
        // Dress up's tab carries the next outfit's ring (2.5), filling as the kid practises.
        if (ring != null) com.bigpapituna.sharpears.ui.world.ProgressRing(ring, 78.dp) { TabFace(page, selected) } else TabFace(page, selected)
        Text(page.label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onSurface else c.onSurfaceVariant)
    }
}

@Composable
private fun TabFace(page: StickerPage, selected: Boolean) {
    val c = MaterialTheme.colorScheme
    run {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
            .border(3.dp, if (selected) PupGold else c.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
            when (page) {
                StickerPage.ALBUM -> Pup(Pose.STICKER, size = 50.dp, live = false, ground = false)
                StickerPage.DRESS -> Pup(Pose.WEAR_BASE, size = 50.dp, live = false, ground = false)
                StickerPage.RECORDS -> MedalDisc(Medal.GOLD, Modifier.size(40.dp))
                StickerPage.PAWS -> Canvas(Modifier.size(34.dp)) { drawPawAt(Offset(size.width / 2, size.height / 2), size.width, WorldInk.copy(alpha = 0.75f), filled = true) }
            }
        }
    }
}
