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
import com.bigpapituna.sharpears.engine.world.Album
import com.bigpapituna.sharpears.engine.world.AlbumPage
import com.bigpapituna.sharpears.engine.world.AlbumSlot
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.motion.pressScale

/** Each place's colour in the album and on its world tab: pastel, like the scenes. */
fun Place.colour(): Color = when (this) { Place.GARDEN -> Color(0xFF7DBE74); Place.POND -> Color(0xFF6FAFE0); Place.CONCERT -> Color(0xFFD97C8C) }

/** What the pup says over the album. */
fun albumLine(a: Album): String = when {
    a.collected == 0 -> "Finish a lesson or a song and your first sticker lands here."
    a.complete -> "Every sticker found. What an album."
    else -> "${a.collected} of ${a.size}. ${a.size - a.collected} still hiding."
}

/**
 * The sticker album: the pup holding a sticker with how many are in, then a page per place with its stickers in
 * order and the gaps showing ("7 of 10 in the pond"). Tapping a sticker (or a gap) opens it big. Not lazy: three
 * pages of ten fit any scroll the integrator puts it in.
 */
@Composable
fun AlbumView(album: Album, modifier: Modifier = Modifier, showPup: Boolean = true) {
    var open by remember { mutableStateOf<AlbumSlot?>(null) }
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showPup) PupSays(Pose.STICKER, albumLine(album), size = 84.dp)
        album.pages.forEach { page -> AlbumPageView(page, onSlot = { open = it }) }
    }
    open?.let { StickerSheet(it, onClose = { open = null }) }
}

/** One place's page: a coloured tab with its name, "7 of 10", a filling bar, then the ten slots in two rows. */
@Composable
fun AlbumPageView(page: AlbumPage, modifier: Modifier = Modifier, onSlot: (AlbumSlot) -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val tint = page.place.colour()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(tint, CircleShape))
            Text(page.place.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
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
                row.forEach { s -> SlotTile(s, Modifier.weight(1f)) { onSlot(s) } }
            }
        }
        if (page.complete) Text("Page complete", style = MaterialTheme.typography.labelLarge, color = c.primary)
    }
}

/** One slot: the sticker (with "x3" for doubles) or a dashed gap holding its faint shape and number. */
@Composable
private fun SlotTile(slot: AlbumSlot, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = slot.sticker
    val label = if (slot.owned) "${s.name}, ${s.rarity.label.lowercase()}" + (if (slot.count > 1) ", you have ${slot.count}" else "")
        else "Sticker ${s.n}, still to find"
    Box(modifier.aspectRatio(1f).pressScale().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLowest)
        .clickable(onClick = onClick).semantics { contentDescription = label; role = Role.Button }, contentAlignment = Alignment.Center) {
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
