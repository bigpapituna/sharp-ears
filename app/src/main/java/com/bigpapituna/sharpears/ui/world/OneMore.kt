package com.bigpapituna.sharpears.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.world.NextDrop
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay

/** What the next lesson holds, in a line under its name (the surprise kept: a shape, never the sticker). */
fun oneMoreLine(next: NextDrop): String = when {
    next.eggHatches -> "Your mystery egg hatches when you finish it."
    next.rareDue -> "A rare sticker is waiting in it."
    next.eggLays -> "It drops a sticker and a mystery egg."
    else -> "It drops a ${next.sticker.place.short()} sticker."
}

private fun Place.short() = when (this) { Place.GARDEN -> "garden"; Place.POND -> "pond"; Place.CONCERT -> "concert hall" }

/**
 * "One more": the next lesson's button for the end of a celebration, with a preview of its drop: the sticker's dark
 * shape with a question mark, bobbing (a blue ring when a rare is due, the egg cracking at the corner when it
 * hatches), the next lesson's [title] and what it drops ([oneMoreLine]). It rises in [delayMs] after it is shown, so
 * the celebration lands first.
 */
@Composable
fun OneMoreButton(next: NextDrop, onClick: () -> Unit, modifier: Modifier = Modifier, title: String? = null, label: String = "One more", delayMs: Long = 900) {
    val still = reducedMotion()
    val rise = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) { delay(delayMs); rise.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f)) } }
    val bob = if (still) null else rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val c = MaterialTheme.colorScheme
    val line = oneMoreLine(next)
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth().graphicsLayer {
        alpha = rise.value.coerceIn(0f, 1f); translationY = (1f - rise.value) * 80f
    }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = (title?.let { "Next: $it. " } ?: "") + line },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerLowest)
                .then(if (next.rareDue) Modifier.border(3.dp, RareBlue, RoundedCornerShape(16.dp)) else Modifier)
                .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (next.rareDue) Canvas(Modifier.fillMaxSize()) { drawCircle(Brush.radialGradient(listOf(RareBlue.copy(alpha = 0.4f), Color.Transparent))) }
                StickerSilhouette(next.sticker, c.onSurface.copy(alpha = 0.78f), Modifier.size(58.dp).graphicsLayer {
                    val b = bob?.value ?: 0f
                    translationY = kotlin.math.sin(b * 2f * Math.PI.toFloat()) * 3.dp.toPx()
                    rotationZ = kotlin.math.sin(b * 2f * Math.PI.toFloat() + 1f) * 4f
                })
                Text("?", style = Numeric.merge(MaterialTheme.typography.headlineSmall), color = c.surfaceContainerLowest)
                if (next.eggHatches) EggView(EggLook.CRACKING, Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(26.dp, 33.dp), crack = 0.6f)
                if (next.rareDue) Canvas(Modifier.fillMaxSize()) { sparkle(Offset(size.width * 0.82f, size.height * 0.2f), size.width * 0.1f, Color.White) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title?.let { "Next: $it" } ?: "Next lesson", style = MaterialTheme.typography.titleMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        ChunkyButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}
