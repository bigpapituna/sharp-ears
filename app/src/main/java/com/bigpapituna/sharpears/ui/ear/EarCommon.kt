package com.bigpapituna.sharpears.ui.ear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.ear.Choice
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.ear.EarTrainer
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.reducedMotion

/** Everything a game screen needs from the app, bundled so the hub, the games and the workout share one set. */
internal class EarEnv(
    val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val micDefault: Boolean,
    val clicks: Clicks, val sound: () -> Boolean, val report: (EarEvent) -> Unit,
)

/** The first letter lowercased, for a level name inside a sentence. */
/** "Five notes" to "five notes", but "C major" stays (a note name is a capital letter on its own). */
internal fun String.lowerFirst() = if (length > 1 && this[1].isLowerCase()) replaceFirstChar { it.lowercase() } else this

internal enum class ChoiceState { NEUTRAL, RIGHT, WRONG, DIMMED }

/** An answer button: a pill with the label and a small second line. Green outline when right, red when it was the wrong pick. */
@Composable
internal fun ChoiceButton(choice: Choice, state: ChoiceState, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg = when (state) {
        ChoiceState.RIGHT -> c.tertiary.copy(alpha = 0.22f)
        ChoiceState.WRONG -> c.error.copy(alpha = 0.18f)
        else -> c.surfaceContainerHigh
    }
    val edge = when (state) { ChoiceState.RIGHT -> c.tertiary; ChoiceState.WRONG -> c.error; else -> c.outlineVariant }
    val shape = RoundedCornerShape(50)
    Column(
        modifier.heightIn(min = 52.dp).widthIn(min = 76.dp).clip(shape).background(bg, shape)
            .border(if (state == ChoiceState.RIGHT || state == ChoiceState.WRONG) 2.dp else 1.dp, edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (state == ChoiceState.DIMMED) c.onSurfaceVariant else c.onSurface
        Text(choice.label, style = MaterialTheme.typography.titleMedium, color = fg, textAlign = TextAlign.Center)
        choice.sub?.let { Text(it, style = if (it.all(Char::isDigit)) Numeric.merge(MaterialTheme.typography.labelSmall) else MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
internal fun StatColumn(label: String, value: Int, hot: Boolean = false) = Column(horizontalAlignment = Alignment.End) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$value", style = Numeric.merge(MaterialTheme.typography.headlineSmall),
        color = if (hot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
}

/** A thin bar for how far through the ten rounds the player is. On a streak ([hot]) it glows. */
@Composable
internal fun RoundBar(fraction: Float, modifier: Modifier = Modifier, hot: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "round bar")
    val still = reducedMotion()
    val glow = when {
        hot && !still -> rememberInfiniteTransition(label = "streak glow")
            .animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow").value
        hot -> 0.4f
        else -> 0f
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(6.dp).drawBehind {
        if (glow > 0f) drawRoundRect(accent.copy(alpha = glow * 0.5f), Offset(-3.dp.toPx(), -3.dp.toPx()),
            Size(size.width * f + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(6.dp.toPx()))
    }.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(accent, CircleShape))
    }
}

/**
 * The pup with a listening ripple: while [listening] a soft ring breathes out from him, and each new [pings] (a
 * sound the app just played) sends a stronger ring. It says "listen" without showing which key sounds.
 */
@Composable
internal fun ListeningPup(pose: Pose, listening: Boolean, pings: Int, size: Dp, modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val ring = remember { Animatable(1f) }
    LaunchedEffect(pings) { if (pings > 0 && !still) { ring.snapTo(0f); ring.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) } }
    val breathe = if (listening && !still) rememberInfiniteTransition(label = "listen")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "breathe") else null
    val color = MaterialTheme.colorScheme.primary
    Box(modifier.size(size).drawBehind {
        val c = Offset(this.size.width / 2, this.size.height * 0.55f)
        val r0 = this.size.minDimension * 0.42f
        if (listening && still) drawCircle(color.copy(alpha = 0.3f), r0 * 1.2f, c, style = Stroke(2.dp.toPx()))
        breathe?.value?.let { t -> drawCircle(color.copy(alpha = 0.4f * (1 - t)), r0 * (1f + 0.55f * t), c, style = Stroke(2.dp.toPx())) }
        val p = ring.value
        if (p < 1f) drawCircle(color.copy(alpha = 0.65f * (1 - p)), r0 * (1f + 0.8f * p), c, style = Stroke((1f + 3f * (1 - p)) * density))
    }) { Pup(pose, size = size) }
}

/**
 * The top of every game screen: the game and level (with [label], such as "Game 2 of 3", above), End game, a
 * progress bar, and the pup beside the round number and the prompt, with score and streak on the right.
 * [listening]: the app is playing the question (the pup's ripple); [pings] counts the sounds played.
 */
@Composable
internal fun GameHeader(trainer: EarTrainer, label: String?, pose: Pose, prompt: String, onExit: () -> Unit,
                        listening: Boolean = false, pings: Int = 0) =
    GameHeader(trainer.kind.title, "Level ${trainer.level + 1}: ${trainer.kind.levels[trainer.level].lowerFirst()}", label,
        trainer.round, trainer.rounds, trainer.roundOver, trainer.score, trainer.streak, pose, prompt, onExit, listening, pings)

/** The same header for any game family (the chord games use it too). */
@Composable
internal fun GameHeader(
    title: String, levelLine: String, label: String?, round: Int, rounds: Int, roundOver: Boolean, score: Int, streak: Int,
    pose: Pose, prompt: String, onExit: () -> Unit, listening: Boolean = false, pings: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(levelLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onExit) { Text("End game") }
        }
        RoundBar((round + if (roundOver) 1 else 0).toFloat() / rounds, hot = streak >= 3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ListeningPup(pose, listening, pings, 64.dp)
            Column(Modifier.weight(1f)) {
                Text("Round ${(round + 1).coerceAtMost(rounds)} of $rounds", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(prompt, style = MaterialTheme.typography.headlineSmall)
            }
            StatColumn("Score", score)
            StatColumn("Streak", streak, hot = streak >= 3)
        }
    }
}

/** The words on the cheer bar for a round that earned no point. */
internal fun missTitle(seed: Int) = listOf("Not quite", "Close", "Next time", "Almost")[Math.floorMod(seed, 4)]
