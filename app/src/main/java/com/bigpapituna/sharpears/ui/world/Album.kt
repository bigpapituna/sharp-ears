package com.bigpapituna.sharpears.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.world.Album
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.engine.world.LevelStickers
import com.bigpapituna.sharpears.engine.world.WorldState
import androidx.compose.foundation.layout.Spacer
import com.bigpapituna.sharpears.engine.world.AlbumPage
import com.bigpapituna.sharpears.engine.world.AlbumSlot
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.motion.pressScale

/** Each place's colour in the album and on its world tab: pastel, like the scenes. */
fun Place.colour(): Color = when (this) {
    Place.GARDEN -> Color(0xFF7DBE74); Place.POND -> Color(0xFF6FAFE0); Place.CONCERT -> Color(0xFFD97C8C)
    Place.WORKSHOP -> Color(0xFFE0A866); Place.TREEHOUSE -> Color(0xFF8FB560); Place.NIGHT_SKY -> Color(0xFF7C80D0); Place.FAIR -> Color(0xFFE79AC2)
}

/** What the pup says over the album. */
fun albumLine(a: Album): String = when {
    a.collected == 0 -> "Finish a lesson or a song and your first sticker lands here."
    a.complete -> "Every sticker found. What an album."
    else -> "${a.collected} of ${a.size}. ${a.size - a.collected} still hiding."
}

/** A trail page's title and colour: the Playground's pages by their place, the big kids' road, then the arcade (2.7). */
private fun trailPage(id: String): Pair<String, Color> {
    if (id == LevelStickers.ARCADE) return "The arcade" to Color(0xFFE0A866)
    val page = PLAYGROUND_PAGES.firstOrNull { it.id == id } ?: return "The big kids' road" to Color(0xFF8A9A8E)
    return page.title to KidPages.worldPlace(page).colour()
}

/**
 * The sticker album (2.5): page by page in the map's order, first the stones' own stickers in stone order (the main
 * collection, earned by finishing each stone), then that place's bonus stickers from the drops and the egg ("7 of 10 in
 * the pond"), and last the big kids' road. Gaps show the sticker's shape. Tapping a sticker (or a gap) opens it big.
 * Not lazy: it fits any scroll the integrator puts it in.
 */
@Composable
fun AlbumView(state: WorldState, modifier: Modifier = Modifier, showPup: Boolean = true) {
    var open by remember { mutableStateOf<AlbumSlot?>(null) }
    var openLevel by remember { mutableStateOf<Pair<LevelSticker, Boolean>?>(null) }
    val album = state.album
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showPup) PupSays(Pose.STICKER, trailAlbumLine(state), size = 84.dp)
        LevelStickers.PAGES.forEach { id ->
            LevelPageView(id, state.levels, onSticker = { l, earned -> openLevel = l to earned })
            PLAYGROUND_PAGES.firstOrNull { it.id == id }?.let { p ->
                AlbumPageView(album.page(KidPages.worldPlace(p)), onSlot = { open = it }, bonus = true)
            }
        }
    }
    open?.let { StickerSheet(it, onClose = { open = null }) }
    openLevel?.let { (l, earned) -> LevelStickerSheet(l, earned, onClose = { openLevel = null }) }
}

/** What the pup says over the album: the trail's stickers first. */
fun trailAlbumLine(state: WorldState): String = when {
    state.levels.isEmpty() && state.unique == 0 -> "Finish a game on the map and its sticker lands here."
    state.levels.size == LevelStickers.ALL.size -> "Every sticker found. What an album."
    state.tray.isNotEmpty() -> "Stick your stickers on the map. Tap one at the bottom."
    else -> "Each stone on the map has a sticker to find."
}

/** A trail page's stones' stickers, in stone order: earned ones in colour, the rest as their shape. */
@Composable
fun LevelPageView(pageId: String, earned: Set<String>, modifier: Modifier = Modifier, onSticker: ((LevelSticker, Boolean) -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val (title, tint) = trailPage(pageId)
    val stickers = LevelStickers.of(pageId)
    val got = stickers.count { it.lessonId in earned }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(tint, CircleShape))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$got of ${stickers.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                color = if (got == stickers.size) c.primary else c.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "$got of ${stickers.size} stones' stickers" })
        }
        stickers.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { l ->
                    val has = l.lessonId in earned
                    val label = if (has) l.name else "A stone's sticker, still to find"
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerLowest)
                        .then(if (onSticker == null) Modifier.semantics { contentDescription = label }
                            else Modifier.clickable { onSticker(l, has) }.semantics { contentDescription = label; role = Role.Button }),
                        contentAlignment = Alignment.Center) {
                        if (has) StickerImage(l, Modifier.fillMaxSize().padding(4.dp))
                        else StickerSilhouette(l, c.onSurface.copy(alpha = 0.1f), Modifier.fillMaxSize().padding(7.dp))
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A stone's sticker big: its name and what the pup said, or its shape and where to find it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelStickerSheet(l: LevelSticker, earned: Boolean, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (earned) StickerImage(l, Modifier.size(200.dp), tilt = 0f) else StickerSilhouette(l, c.onSurface.copy(alpha = 0.12f), Modifier.size(200.dp))
            Text(if (earned) l.name else "Still to find", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(if (earned) l.line else "Finish its stone on the map to earn it.", style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** One place's page: a coloured tab with its name, "7 of 10", a filling bar, then the ten slots in two rows. */
@Composable
fun AlbumPageView(page: AlbumPage, modifier: Modifier = Modifier, onSlot: ((AlbumSlot) -> Unit)? = null, bonus: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val tint = page.place.colour()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(tint, CircleShape))
            Text(if (bonus) "Bonus stickers" else page.place.title, style = if (bonus) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text("${page.collected} of ${page.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                color = if (page.complete) c.primary else c.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = page.line })
        }
        // Ten little segments, filled for each sticker in.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            page.slots.forEach { s -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (s.owned) tint else c.surfaceContainerHighest)) }
        }
        page.slots.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s -> SlotTile(s, Modifier.weight(1f), onSlot?.let { f -> { f(s) } }) }
            }
        }
        if (page.complete) Text("Page complete", style = MaterialTheme.typography.labelLarge, color = c.primary)
    }
}

/** One slot: the sticker (with "x3" for doubles) or a dashed gap holding its faint shape and number. */
@Composable
private fun SlotTile(slot: AlbumSlot, modifier: Modifier, onClick: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    val s = slot.sticker
    val label = if (slot.owned) "${s.name}, ${s.rarity.label.lowercase()}" + (if (slot.count > 1) ", you have ${slot.count}" else "")
        else "Sticker ${s.n}, still to find"
    Box(modifier.aspectRatio(1f).pressScale().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLowest)
        // Only a page that opens its stickers (the album) makes them buttons; the finale's page is a picture (TalkBack
        // found ten buttons there that did nothing).
        .then(if (onClick == null) Modifier.semantics { contentDescription = label }
            else Modifier.clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Button }), contentAlignment = Alignment.Center) {
        if (slot.owned) {
            StickerImage(s, Modifier.fillMaxSize().padding(5.dp))
            if (slot.count > 1) Text("x${slot.count}", style = Numeric.merge(MaterialTheme.typography.labelSmall), color = c.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).background(c.surfaceContainerHighest, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp))
        } else {
            val dash = c.outline
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val w = 1.6.dp.toPx()
                drawRoundRect(dash, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(11.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            StickerSilhouette(s, c.onSurface.copy(alpha = 0.07f), Modifier.fillMaxSize().padding(8.dp), tilt = s.tilt())
            Text("${s.n}", style = Numeric.merge(MaterialTheme.typography.labelLarge), color = c.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

/** A sticker big, with its name, rarity, place and how many; a gap shows its shape and where it can come from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSheet(slot: AlbumSlot, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = slot.sticker
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (slot.owned) StickerImage(s, Modifier.size(200.dp), tilt = 0f)
            else StickerSilhouette(s, c.onSurface.copy(alpha = 0.12f), Modifier.size(200.dp))
            Text(if (slot.owned) s.name else "Still to find", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (slot.owned) {
                    Box(Modifier.size(10.dp).background(s.rarity.colour(), CircleShape))
                    Text(s.rarity.label, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                }
                Text(s.place.title, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
            Text(
                when {
                    !slot.owned -> "It could drop from any lesson or song. " + (if (s.rarity == Rarity.COMMON) "Keep playing." else "This one is hard to find.")
                    slot.count == 1 -> "Your only one so far."
                    else -> "You have ${slot.count}."
                },
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}
