package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.doneIn
import com.bigpapituna.sharpears.engine.SessionWords
import kotlinx.coroutines.delay

/** How long the Go ring takes to fill once the voice has finished, before the part opens by itself. */
private const val COUNTDOWN_MS = 2400

/**
 * Between two parts of "Practice today": the pup, large, says what comes next with its title in quotes (the voice
 * says the same words), the session's parts sit in a row of chips with this one raised and the done ones ticked,
 * and a Go button fills its ring once the voice has finished; when the ring closes the part opens by itself. Go
 * opens it straight away; Skip passes over it; End for today stops the session. [parts], [index] and [done] are
 * the session (without them the chips are left out and the line is quoted from [line]).
 *
 * A kid (2.1) hears and reads the kid words (SessionWords: no counts, no bar numbers, no minutes) and sees no part
 * numbers either.
 */
@Composable
fun Interstitial(line: String, voice: Voice, onGo: () -> Unit, onSkip: () -> Unit, onEnd: () -> Unit,
                 parts: List<SessionPart> = emptyList(), index: Int = -1, done: List<SessionPart> = emptyList(), kid: Boolean = false) {
    val ring = remember(line) { Animatable(0f) }
    val part = parts.getOrNull(index)
    val spoken = if (kid && part != null) SessionWords.kidNext(part) else line
    LaunchedEffect(line) {
        Log.d(TAG, "daily: $spoken")
        voice.speak(spoken, interrupt = true)
        delay(400)
        while (voice.speaking.value) delay(50)
        ring.animateTo(1f, tween(COUNTDOWN_MS, easing = LinearEasing))
        onGo()
    }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        if (part != null) Text(if (kid) "Practice today" else "Your plan, part ${index + 1} of ${parts.size}", style = Numeric.merge(MaterialTheme.typography.labelLarge),
            color = c.primary)
        Pup(Pose.GET_READY, size = 176.dp)
        UpBubble(part?.let { SessionWords.quoted(it, kid) } ?: SessionWords.quoted(line), Modifier.widthIn(max = 480.dp).semantics { heading() })
        part?.let {
            Text(SessionWords.about(it, kid), style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        }
        if (parts.size > 1) PartChips(parts, index, done, Modifier.padding(top = 4.dp), kid)
        ChunkyButton(onClick = onGo, modifier = Modifier.padding(top = 8.dp).widthIn(min = 200.dp, max = 360.dp).fillMaxWidth()) {
            CountdownRing(ring)
            Spacer(Modifier.size(10.dp))
            Text("Go")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSkip) { Text("Skip") }
            TextButton(onClick = onEnd) { Text("End for today") }
        }
    }
}

/** The icon for a session part: the keys for chords, the cap for a lesson, the loop for a bar, a note for a song. */
private fun SessionPart.icon() = when (this) {
    is SessionPart.Chords -> R.drawable.ic_piano
    is SessionPart.Lesson -> R.drawable.ic_school
    is SessionPart.Loop -> R.drawable.ic_repeat
    is SessionPart.Song -> R.drawable.ic_music_note
}

/** The session as a row of chips: done ones ticked, this one raised in the accent, the rest outlined. */
@Composable
private fun PartChips(parts: List<SessionPart>, index: Int, done: List<SessionPart>, modifier: Modifier, kid: Boolean) {
    val c = MaterialTheme.colorScheme
    FlowRow(modifier.widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { i, p ->
            val now = i == index
            val ticked = i < index && p.doneIn(done)       // a chord part ends with the chords answered: still done
            val base = Modifier.semantics(mergeDescendants = true) {
                contentDescription = SessionWords.chip(p, kid) + when { now -> ", next"; ticked -> ", done"; i < index -> ", skipped"; else -> "" }
            }
            val look = when {
                now -> base.background(c.primary, CircleShape)
                ticked -> base.background(c.surfaceContainerHigh, CircleShape)
                i < index -> base.graphicsLayer { alpha = 0.5f }.border(1.dp, c.outlineVariant, CircleShape)     // skipped
                else -> base.border(1.dp, c.outlineVariant, CircleShape)
            }
            Row(look.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val ink = if (now) c.onPrimary else c.onSurfaceVariant
                Icon(painterResource(if (ticked) R.drawable.ic_check else p.icon()), contentDescription = null, tint = if (ticked) c.primary else ink,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(SessionWords.chip(p, kid), style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** The ring on Go, filling as the part's start comes up. */
@Composable
private fun CountdownRing(progress: Animatable<Float, *>) {
    val ink = LocalContentColor.current
    Canvas(Modifier.size(22.dp).clearAndSetSemantics { }) {
        val w = 3.dp.toPx()
        val s = Size(size.width - w, size.height - w)
        drawArc(ink.copy(alpha = 0.3f), 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
        drawArc(ink, -90f, 360f * progress.value, false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** The pup's line in a bubble under him, its tail pointing up at him. */
@Composable
private fun UpBubble(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = remember(density) {
        val tail = with(density) { 10.dp.toPx() }; val r = with(density) { 20.dp.toPx() }
        GenericShape { s, _ ->
            addRoundRect(RoundRect(0f, tail, s.width, s.height, CornerRadius(r)))
            moveTo(s.width / 2 - tail, tail + 1); lineTo(s.width / 2, 0f); lineTo(s.width / 2 + tail, tail + 1); close()
        }
    }
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer, shape).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 16.dp))
}
