package com.bigpapituna.sharpears.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.rememberPupController

/** What the pup says when something goes on, one thing comes off ([stillWearing] the rest), or everything comes off. */
fun wearLine(item: WearItem?, on: Boolean, stillWearing: Boolean = false): String = when {
    item != null && !on && stillWearing -> "Off it comes."
    item == null || !on -> "Back to just me."
    else -> item.line
}

/** Before anything is chosen: what he says about the wardrobe. */
fun wardrobeLine(outfit: Outfit, open: Int): String = when {
    open == 0 -> "Play a little and I get something to wear."
    outfit.isEmpty -> "Dress me up! Tap something to try it on."
    else -> "Mix and match!"
}

/**
 * Dress the pup: him big in his sitting base pose wearing the outfit, and the six things to wear (one per head, eyes
 * and neck at a time), each opening at a number of stickers. Tapping puts it on or takes it off; he says so. The
 * outfit is this profile's and shows wherever he sits in his base pose.
 */
@Composable
fun DressUp(world: WorldStore, modifier: Modifier = Modifier) {
    val s = world.state
    val sounds = rememberWorldSounds()
    val pup = rememberPupController(idle = false)
    var said by remember { mutableStateOf<String?>(null) }
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SpeechBubble(said ?: wardrobeLine(s.outfit, s.wardrobe.size))
        // Room above him for a hat.
        Box(Modifier.padding(top = 44.dp)) {
            Pup(Pose.WEAR_BASE, size = 210.dp, controller = pup, idle = false, outfit = s.outfit, bounce = BounceSize.SMALL, onTap = {})
        }
        WearItem.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    WearTile(item, worn = item in s.outfit.items, unlocked = item in s.wardrobe, need = 0, modifier = Modifier.weight(1f)) {
                        val on = item !in s.outfit.items
                        val stillWearing = (s.outfit.items - item).isNotEmpty()
                        world.wear(item)
                        sounds.pop(if (on) 1.15f else 0.85f)
                        said = wearLine(item, on, stillWearing)
                    }
                }
            }
        }
        if (!s.outfit.isEmpty) TextButton(onClick = {
            s.outfit.items.forEach(world::wear)
            sounds.pop(0.8f)
            said = wearLine(null, false)
        }) { Text("Take it all off") }
    }
}

@Composable
private fun WearTile(item: WearItem, worn: Boolean, unlocked: Boolean, need: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.pressScale().clip(shape).background(if (worn) c.primaryContainer else c.surfaceContainerHigh)
        .then(if (worn) Modifier.border(2.dp, c.primary, shape) else Modifier)
        .clickable(enabled = unlocked, role = Role.Button, onClick = onClick)
        .semantics {
            selected = worn
            role = Role.Button
            contentDescription = item.label + when { !unlocked -> ", opens with $need more ${if (need == 1) "sticker" else "stickers"}"; worn -> ", wearing it"; else -> "" }
        }
        .padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f), contentAlignment = Alignment.Center) {
            WearIcon(item, Modifier.fillMaxWidth().aspectRatio(1.4f), locked = !unlocked)
            if (!unlocked) Icon(painterResource(R.drawable.ic_profile_lock), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Text(item.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = if (unlocked) c.onSurface else c.onSurfaceVariant)
        if (!unlocked) Text("$need more ${if (need == 1) "sticker" else "stickers"}", style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
