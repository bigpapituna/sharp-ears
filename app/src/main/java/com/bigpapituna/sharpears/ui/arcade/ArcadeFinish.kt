package com.bigpapituna.sharpears.ui.arcade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeLines
import com.bigpapituna.sharpears.engine.arcade.RoundEnd
import com.bigpapituna.sharpears.engine.arcade.RoundResult
import com.bigpapituna.sharpears.engine.world.LevelSticker
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.playground.Painted
import com.bigpapituna.sharpears.ui.playground.PaintedPup
import com.bigpapituna.sharpears.ui.playground.star
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.world.StickerImage
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay

/**
 * A round's end over its playfield: the stars landing one by one with a chime each (the mic is off by now), the pup, his
 * words, the game's sticker flipping in the first time it earns three stars, and two big buttons: Again (the next round
 * at the level the book chose) and back to the games.
 */
@Composable
internal fun ArcadeFinish(game: ArcadeGame, result: RoundResult, end: RoundEnd?, sticker: LevelSticker?, onAgain: () -> Unit, onGames: () -> Unit) {
    val stars = end?.stars ?: result.stars
    val still = reducedMotion()
    val drops = remember { List(3) { Animatable(0f) } }
    val stickerIn = remember { Animatable(0f) }
    LaunchedEffect(stars) {
        delay(400)
        for (i in 0 until stars) {
            if (still) drops[i].snapTo(1f) else drops[i].animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            WorldSounds.star(i)
            delay(250)
        }
        if (sticker != null) { delay(300); WorldSounds.pop(); if (still) stickerIn.snapTo(1f) else stickerIn.animateTo(1f, spring(dampingRatio = 0.55f)) }
    }
    Box(Modifier.fillMaxSize().background(Stage.copy(alpha = 0.9f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(game.title, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = when (stars) { 3 -> "Every star"; 2 -> "Two stars"; else -> "One star" }
            }) {
                for (i in 0 until 3) Canvas(Modifier.size(64.dp).graphicsLayer {
                    val s = if (i < stars) drops[i].value else 1f
                    scaleX = s; scaleY = s
                }) {
                    val c = Offset(size.width / 2, size.height / 2)
                    if (i < stars) drawPath(star(c, size.width * 0.46f), PupGold)
                    else drawPath(star(c, size.width * 0.46f), Ink.copy(alpha = 0.35f), style = Stroke(3.dp.toPx()))
                }
            }
            PaintedPup(when (stars) { 3 -> Painted.CROWN; 2 -> Painted.DANCING; else -> Painted.GREAT_JOB }, size = 132.dp)
            Text(ArcadeLines.finish(stars), style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center)
            if (sticker != null) {
                StickerImage(sticker, Modifier.size(128.dp).graphicsLayer { rotationY = 180f * (1f - stickerIn.value); alpha = stickerIn.value })
                Text(ArcadeLines.STICKER, style = MaterialTheme.typography.titleMedium, color = PupGold, textAlign = TextAlign.Center)
            } else if (end?.paintedNew == true) Text(ArcadeLines.PAINTED, style = MaterialTheme.typography.titleMedium, color = PupGold, textAlign = TextAlign.Center)
            // 2.9: once today's goal is met, Tomorrow's surprise.
            com.bigpapituna.sharpears.ui.TomorrowSlot(onDark = true)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigPill("Again!", R.drawable.ic_repeat, primary = true, onClick = onAgain, modifier = Modifier.weight(1f))
                BigPill("Games", R.drawable.ic_arrow_back, primary = false, onClick = onGames, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A big pill button with an icon (a pre-reader reads the icon). */
@Composable
internal fun BigPill(label: String, icon: Int, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 60.dp).pressScale().clip(CircleShape).background(if (primary) PupGold else c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = if (primary) Color(0xFF2A1A0E) else c.onSurface, modifier = Modifier.size(24.dp))
        Text("  $label", style = MaterialTheme.typography.titleMedium, color = if (primary) Color(0xFF2A1A0E) else c.onSurface)
    }
}
