package com.bigpapituna.sharpears.ui.ear

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

/** Everything a game screen needs from the app, bundled so the hub, the games and the workout share one set. */
internal class EarEnv(
    val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val micDefault: Boolean,
    val clicks: Clicks, val sound: () -> Boolean, val report: (EarEvent) -> Unit,
)

/** The first letter lowercased, for a level name inside a sentence. */
internal fun String.lowerFirst() = replaceFirstChar { it.lowercase() }

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
internal fun StatColumn(label: String, value: Int) = Column(horizontalAlignment = Alignment.End) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("$value", style = Numeric.merge(MaterialTheme.typography.headlineSmall))
}

/** A thin bar for how far through the ten rounds the player is. */
@Composable
internal fun RoundBar(fraction: Float, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "round bar")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}

/**
 * The top of every game screen: the game and level (with [label], such as "Game 2 of 3", above), End game, a
 * progress bar, and the pup beside the round number and the prompt, with score and streak on the right.
 */
@Composable
internal fun GameHeader(trainer: EarTrainer, label: String?, pose: Pose, prompt: String, onExit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(trainer.kind.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Level ${trainer.level + 1}: ${trainer.kind.levels[trainer.level].lowerFirst()}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onExit) { Text("End game") }
        }
        RoundBar((trainer.round + if (trainer.roundOver) 1 else 0).toFloat() / trainer.rounds)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(pose, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Text("Round ${(trainer.round + 1).coerceAtMost(trainer.rounds)} of ${trainer.rounds}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(prompt, style = MaterialTheme.typography.headlineSmall)
            }
            StatColumn("Score", trainer.score)
            StatColumn("Streak", trainer.streak)
        }
    }
}

/** The words on the cheer bar for a round that earned no point. */
internal fun missTitle(seed: Int) = listOf("Not quite", "Close", "Next time", "Almost")[Math.floorMod(seed, 4)]
