package com.bigpapituna.sharpears.ui.ear

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.ear.Choice
import com.bigpapituna.sharpears.engine.ear.ProgressionGame
import com.bigpapituna.sharpears.engine.ear.chordLabel
import com.bigpapituna.sharpears.engine.ear.chordsOf
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.reducedMotion

/** One beat of a progression as it plays (the default beat of engine/ear progressionSteps). */
internal const val PROG_BEAT_MS = 620L

/**
 * Four slots across the top, one per beat. While the progression plays the slot sounding now lights and
 * pulses and the ones played stay warm; they show no numerals, so the answer stays a thing to hear. Once the
 * round is over ([revealed]) each slot fills with its numeral and chord name; a chord held for two beats shows
 * its numeral again, faded, in its second slot.
 */
@Composable
internal fun ProgressionSlots(game: ProgressionGame, active: Int, heard: Int, revealed: Boolean, clean: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val still = reducedMotion()
    val pulse = if (!still) rememberInfiniteTransition(label = "slot").animateFloat(1f, 1.07f,
        infiniteRepeatable(tween(PROG_BEAT_MS.toInt() / 2), RepeatMode.Reverse), label = "pulse") else null
    // Beat by beat: which chord each slot belongs to, and whether it is the chord's first beat.
    val target = game.target
    val chords = chordsOf(target, game.key)
    val slots = target.steps.flatMapIndexed { i, s -> List(s.beats) { b -> i to (b == 0) } }
    val said = if (revealed) slots.joinToString(", ") { (i, first) -> if (first) target.steps[i].numeral else "held" } else "${slots.size} beats"
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said },
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.forEachIndexed { beat, (i, first) ->
            val playing = !revealed && beat == active
            val played = !revealed && !playing && beat < heard
            val bg by animateColorAsState(when {
                playing -> c.primary
                revealed && first -> if (clean) c.tertiary.copy(alpha = 0.2f) else c.surfaceContainerHighest
                played -> c.primary.copy(alpha = 0.22f)
                else -> c.surfaceContainerHigh
            }, tween(if (still) 0 else 160), label = "slot")
            Column(Modifier.weight(1f).height(96.dp).graphicsLayer { val s = if (playing) pulse?.value ?: 1f else 1f; scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(16.dp)).background(bg)
                .then(if (revealed && first && clean) Modifier.border(2.dp, c.tertiary, RoundedCornerShape(16.dp)) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (revealed) {
                    Text(target.steps[i].numeral, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
                        color = if (first) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(if (first) chordLabel(chords[i], game.key, target.minor) else "held", style = MaterialTheme.typography.labelLarge,
                        color = c.onSurfaceVariant)
                } else {
                    // A little bar of sound: a wider dash for the slot sounding now.
                    Box(Modifier.height(6.dp).fillMaxWidth(if (playing) 0.5f else 0.24f).clip(RoundedCornerShape(3.dp))
                        .background(if (playing) c.onPrimary else if (played) c.primary else c.outline))
                    Spacer(Modifier.height(10.dp))
                    Text("${beat + 1}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = if (playing) c.onPrimary else c.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Melody memory: a dot per note of the melody. While it plays the dots fill in the accent one by one ([heard]);
 * while the learner plays it back they fill in green ([played]), so the length and the place in it are always
 * in view.
 */
@Composable
internal fun MelodyDots(total: Int, heard: Int, played: Int, listening: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.semantics(mergeDescendants = true) {
        contentDescription = if (listening) "Melody of $total notes" else "$played of $total notes played back"
    }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val on = if (listening) i < heard else i < played
            val now = if (listening) i == heard - 1 else i == played
            val fill by animateColorAsState(when {
                on && listening -> c.primary
                on -> c.tertiary
                else -> c.surfaceContainerHighest
            }, tween(140), label = "dot")
            Box(Modifier.size(if (now) 22.dp else 18.dp).clip(CircleShape).background(fill)
                .then(if (now && !listening) Modifier.border(2.dp, c.tertiary, CircleShape) else Modifier))
        }
    }
}

/**
 * Big answer cards for the choice-only games: the label large in Nunito (Roman numerals) and the nickname
 * under it, two to a row on a phone and all in one row on a wide window.
 */
@Composable
internal fun AnswerCards(choices: List<Choice>, stateOf: (Choice) -> ChoiceState, enabled: Boolean, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth >= 600.dp) choices.size.coerceAtLeast(1) else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { choice ->
                        val state = stateOf(choice)
                        val shape = RoundedCornerShape(20.dp)
                        val bg = when (state) {
                            ChoiceState.RIGHT -> c.tertiary.copy(alpha = 0.2f)
                            ChoiceState.WRONG -> c.surfaceContainerHighest
                            else -> c.surfaceContainerHigh
                        }
                        val edge = when (state) { ChoiceState.RIGHT -> c.tertiary; ChoiceState.WRONG -> c.outline; else -> c.outlineVariant }
                        Column(Modifier.weight(1f).heightIn(min = 104.dp).pressScale().clip(shape).background(bg)
                            .border(if (state == ChoiceState.RIGHT || state == ChoiceState.WRONG) 2.dp else 1.dp, edge, shape)
                            .clickable(enabled = enabled, role = Role.Button) { onPick(choice.id) }
                            .graphicsLayer { alpha = if (state == ChoiceState.DIMMED) 0.55f else 1f }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(choice.label, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold),
                                color = c.onSurface, textAlign = TextAlign.Center)
                            choice.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
