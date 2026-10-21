package com.bigpapituna.sharpears.ui.world

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.world.Outfit
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.engine.world.Rarity
import com.bigpapituna.sharpears.engine.world.Wardrobe
import com.bigpapituna.sharpears.engine.world.WearItem
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.motion.BounceSize
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.rememberPupController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Something new to wear, at a finish screen (2.5): the pup's card flips in like a sticker drop with the rarity's
 * jingle (confetti for a shiny one), he is wearing the new [items] already (put on at once, over whatever he wore), and
 * he asks "Keep it on?". Yes keeps it; No puts back what he wore before. Without a world (a demo) it only shows.
 */
@Composable
fun WardrobeReveal(items: List<WearItem>, modifier: Modifier = Modifier, say: ((String) -> Unit)? = null) {
    if (items.isEmpty()) return
    val world = LocalWorld.current
    val sounds = rememberWorldSounds()
    val still = reducedMotion()
    val pup = rememberPupController(idle = false)
    val rarity = items.maxOf { it.rarity }
    // What he wore before, kept for "No"; the new items go on at once.
    val before = rememberSaveable(items.map { it.id }) { mutableStateOf(world?.state?.outfit?.items?.map { it.id }.orEmpty()) }
    var answered by rememberSaveable(items.map { it.id }) { mutableStateOf<Boolean?>(null) }
    val flip = remember(items) { Animatable(if (still) 1f else 0f) }
    val enter = remember(items) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(items) {
        world?.let { w -> if (answered == null) w.dress(items.fold(w.state.outfit) { o, i -> o.with(i) }) }
        if (!still) {
            enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
            delay(200)
            launch { flip.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            delay(260)
        }
        sounds.jingle(rarity)
        pup.react(if (rarity.special) PupReaction.SURPRISED else PupReaction.DANCE)
        say?.invoke(Wardrobe.revealLine(items))
        Log.d(TAG, "wardrobe: reveal ${items.joinToString { it.id }}")
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (rarity == Rarity.SHINY && flip.value >= 0.5f) Confetti(items, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
            .border(if (rarity.special) 3.dp else 0.dp, if (rarity == Rarity.SHINY) Brush.linearGradient(ShinyPrism) else Brush.linearGradient(listOf(RareBlue, RareBlue)), RoundedCornerShape(20.dp))
            .padding(16.dp).semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = Wardrobe.revealLine(items) + " " + items.joinToString { "${it.nickname}, ${it.label.lowercase()}" }
            },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (items.size > 1) "A whole new outfit!" else "Something new to wear!", style = MaterialTheme.typography.titleLarge,
                color = c.primary, textAlign = TextAlign.Center)
            Box(Modifier.padding(top = 26.dp).graphicsLayer {
                cameraDistance = 14f * density
                val e = enter.value
                scaleX = 0.4f + 0.6f * e; scaleY = 0.4f + 0.6f * e
                alpha = e.coerceIn(0f, 1f)
                rotationY = (1f - flip.value) * 180f
            }) {
                // Turned away (the card's back), he is still his plain self; turned round, he wears it.
                val wearing = if (flip.value >= 0.5f) world?.state?.outfit ?: Outfit(items.toSet()) else Outfit.NONE
                Pup(Pose.WEAR_BASE, size = 170.dp, controller = pup, idle = false, outfit = wearing, bounce = BounceSize.NONE)
            }
            Text(items.joinToString(" and ") { it.nickname }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(items.first().line, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            if (rarity.special) Text(rarity.label, style = MaterialTheme.typography.labelLarge, color = rarity.colour())
            when (answered) {
                null -> if (world != null) {
                    Text("Keep it on?", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChunkyButton(onClick = { answered = true; sounds.pop(1.15f); Log.d(TAG, "wardrobe: kept ${items.joinToString { it.id }}") }) { Text("Yes!") }
                        OutlinedButton(onClick = {
                            answered = false
                            world.dress(Outfit(before.value.mapNotNull(WearItem::byId).toSet()))
                            sounds.pop(0.85f)
                            Log.d(TAG, "wardrobe: took off ${items.joinToString { it.id }}")
                        }) { Text("Not now") }
                    }
                }
                true -> Text("Looking good! Change it any time in Dress up.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                false -> Text("It waits for you in Dress up.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
