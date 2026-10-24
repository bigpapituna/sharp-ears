package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.NextOutfit
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.Wardrobe
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.engine.world.WearSlot
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.rememberPupController

/** What the pup says when something goes on (its own line), one thing comes off ([stillWearing] the rest), or everything comes off. */
fun wearLine(item: WearItem?, on: Boolean, stillWearing: Boolean = false): String = when {
    item != null && !on && stillWearing -> "Off it comes."
    item == null || !on -> "Back to just me."
    else -> item.line
}

/** Before anything is chosen: what he says about the wardrobe ([open] items open). */
fun wardrobeLine(outfit: Outfit, open: Int): String = when {
    open == 0 -> "Play a little and I get something to wear."
    outfit.isEmpty -> "Dress me up! Tap something to try it on."
    else -> "Mix and match!"
}

/** A slot's tab picture: an item that says it at a glance. */
private val SLOT_ICON = mapOf(WearSlot.HEAD to WearItem.TOP_HAT, WearSlot.FACE to WearItem.SUNGLASSES, WearSlot.BODY to WearItem.CAPE_RED, WearSlot.BACK to WearItem.BOOTS)

/**
 * Dress the pup (2.5): him big in his sitting base pose wearing the outfit, how close the next outfit is
 * ([NextOutfitMeter]: a ring for pre-readers, a bar for readers), then the wardrobe by slot (head, face, body, feet and
 * back; one of each at a time, so four combine). Each tile shows the item on him, its name and its rarity (rare in blue,
 * shiny with a sparkle); a locked one says how it is earned. Tapping puts it on or takes it off; he says so. The outfit
 * is this profile's and shows wherever he sits in his base pose.
 */
@Composable
fun DressUp(world: WorldStore, modifier: Modifier = Modifier, reader: Boolean = true) {
    val s = world.state
    val sounds = rememberWorldSounds()
    val pup = rememberPupController(idle = false)
    var said by remember { mutableStateOf<String?>(null) }
    var slot by remember { mutableStateOf(WearSlot.HEAD) }
    Column(modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SpeechBubble(said ?: wardrobeLine(s.outfit, s.wardrobe.size))
        // Room above him for a hat (or a halo).
        Box(Modifier.padding(top = 30.dp)) {
            Pup(Pose.WEAR_BASE, size = 220.dp, controller = pup, idle = false, outfit = s.outfit, bounce = BounceSize.SMALL, onTap = {})
        }
        NextOutfitMeter(s.nextOutfit, reader)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WearSlot.entries.forEach { sl ->
                val open = s.unlocked.count { it.slot == sl }
                SlotTab(sl, selected = sl == slot, worn = s.outfit.inSlot(sl), open = open, modifier = Modifier.weight(1f)) { slot = sl }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 520.dp) 5 else 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WearItem.entries.filter { it.slot == slot }.sortedBy { if (it in s.wardrobe) 0 else 1 }.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            WearTile(item, worn = item in s.outfit.items, unlocked = item in s.wardrobe, modifier = Modifier.weight(1f)) {
                                if (item !in s.wardrobe) { said = Wardrobe.how(item); sounds.pop(0.7f); return@WearTile }
                                val on = item !in s.outfit.items
                                val stillWearing = (s.outfit.items - item).isNotEmpty()
                                world.wear(item)
                                sounds.pop(if (on) 1.15f else 0.85f)
                                said = wearLine(item, on, stillWearing)
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (!s.outfit.isEmpty) TextButton(onClick = {
            world.dress(Outfit.NONE)
            sounds.pop(0.8f)
            said = wearLine(null, false)
        }) { Text("Take it all off") }
    }
}

/** A slot's tab: what it is, and its picture (what he wears there, else a sample). */
@Composable
private fun SlotTab(slot: WearSlot, selected: Boolean, worn: WearItem?, open: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
        .then(if (selected) Modifier.border(2.dp, c.primary, shape) else Modifier)
        .clickable(role = Role.Tab, onClick = onClick)
        .semantics { this.selected = selected; contentDescription = slot.label + (worn?.let { ", wearing the ${it.label.lowercase()}" } ?: "") }
        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WearIcon(worn ?: SLOT_ICON.getValue(slot), Modifier.size(48.dp), locked = open == 0)
        Text(slot.label, style = MaterialTheme.typography.labelMedium, color = c.onSurface, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WearTile(item: WearItem, worn: Boolean, unlocked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val edge = when {
        worn -> Brush.linearGradient(listOf(c.primary, c.primary))
        !unlocked -> null
        item.rarity == Rarity.SHINY -> Brush.linearGradient(ShinyPrism)
        item.rarity == Rarity.RARE -> Brush.linearGradient(listOf(RareBlue, RareBlue))
        else -> null
    }
    Column(modifier.pressScale().clip(shape).background(if (worn) c.primaryContainer else c.surfaceContainerHigh)
        .then(if (edge != null) Modifier.border(if (worn) 3.dp else 2.dp, edge, shape) else Modifier)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics {
            selected = worn
            role = Role.Button
            contentDescription = "${item.nickname}, ${item.label.lowercase()}" + (if (item.rarity.special) ", ${item.rarity.label.lowercase()}" else "") +
                when { !unlocked -> ". " + Wardrobe.how(item); worn -> ", wearing it"; else -> "" }
        }
        .padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f), contentAlignment = Alignment.Center) {
            if (unlocked && item.rarity.special) Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf((if (item.rarity == Rarity.SHINY) ShinyPrism[1] else RareBlue).copy(alpha = 0.45f), Color.Transparent)), size.minDimension * 0.6f)
            }
            WearIcon(item, Modifier.fillMaxSize(), locked = !unlocked)
            if (!unlocked) Icon(painterResource(R.drawable.ic_profile_lock), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
            if (unlocked && item.rarity == Rarity.SHINY) Twinkle(Modifier.fillMaxSize())
        }
        Text(item.nickname, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
            color = if (unlocked) c.onSurface else c.onSurfaceVariant)
        if (item.rarity.special) Text(item.rarity.label, style = MaterialTheme.typography.labelSmall, color = if (unlocked) item.rarity.colour() else c.onSurfaceVariant)
    }
}

/** A shiny item's sparkles, pulsing in turn (still with animations off). */
@Composable
private fun Twinkle(modifier: Modifier) {
    val still = reducedMotion()
    val t = if (still) null else rememberInfiniteTransition(label = "twinkle").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier.clearAndSetSemantics { }) {
        val v = t?.value ?: 0.3f
        listOf(Offset(0.15f, 0.2f), Offset(0.85f, 0.15f), Offset(0.88f, 0.8f)).forEachIndexed { i, o ->
            val k = (0.5f + 0.5f * kotlin.math.sin((v + i / 3f) * 2f * Math.PI.toFloat())).coerceIn(0f, 1f)
            sparkle(Offset(o.x * size.width, o.y * size.height), size.minDimension * (0.04f + 0.05f * k), PupGold, alpha = 0.4f + 0.6f * k)
        }
    }
}

/**
 * How close the next outfit is, without a number: for a pre-reader a ring filling round the next item's shape with
 * "Almost there!" under it; for a reader (and grown-ups) a bar with "Next outfit" and the same words. Says so once the
 * practice track is done.
 */
@Composable
fun NextOutfitMeter(next: NextOutfit?, reader: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    if (next == null) {
        Text("Every outfit from practice is yours!", style = MaterialTheme.typography.titleMedium, color = c.primary, modifier = modifier)
        return
    }
    val item = next.items.first()
    val label = "Next outfit: ${next.words.lowercase().trimEnd('!')}"
    if (!reader) {
        Column(modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressRing(next.share, 112.dp) { WearIcon(item, Modifier.size(84.dp).clip(CircleShape), locked = true) }
            Text(next.words, style = MaterialTheme.typography.titleLarge, color = c.onSurface)
        }
    } else {
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surfaceContainerHigh).padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WearIcon(item, Modifier.size(52.dp), locked = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The bar says how close; the words were a small repeat of the tab's ring (QA 2.8.3). TalkBack still reads them.
                Text("Next outfit", style = MaterialTheme.typography.titleSmall)
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(c.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(next.share.coerceIn(0.04f, 1f)).height(12.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(PupGold, c.primary))))
                }
            }
        }
    }
}

/** A ring filling to [share] round [content], gold on a soft track. */
@Composable
fun ProgressRing(share: Float, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val w = this.size.width * 0.09f
            val inset = w / 2f
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w), style = Stroke(w))
            drawArc(Brush.sweepGradient(listOf(PupGold, fill, PupGold)), -90f, 360f * share.coerceIn(0.02f, 1f), false, Offset(inset, inset),
                Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}
