package com.bigpapituna.sharpears.ui.play

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.skin.NoteColours
import com.bigpapituna.sharpears.ui.onScreen

/**
 * Where a Play screen gets raw mic audio (Clap along's clap counter, the Jam's chord ear): a registration that takes
 * a sink, or null to let go. Screens get it from [MicInput.frameTap].
 */
fun interface FrameTap {
    fun register(sink: ((FloatArray) -> Unit)?)
}

/** Raw frames from [MicInput.rawFrames]: registering a sink replaces this tap's last one, null removes it. */
fun MicInput.frameTap(): FrameTap {
    var remove: (() -> Unit)? = null
    return FrameTap { sink -> remove?.invoke(); remove = sink?.let { rawFrames.add(it) } }
}

/** A key's Boomwhacker colour as a Compose colour (the skin's table; a black key is the blend of its neighbours). */
fun noteColour(midi: Int) = Color(NoteColours.blend(midi))

/** The way out of a Play screen: a small pill at the top, "Done" with a cross. No score and no timer to leave behind. */
@Composable
internal fun ExitPill(onExit: () -> Unit, modifier: Modifier = Modifier, label: String = "Done") {
    Row(
        modifier.heightIn(min = 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clickable(role = Role.Button, onClick = onExit).semantics { contentDescription = label }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** What a Play screen's mic is doing, for its little status line. */
internal enum class MicState { LISTENING, PAUSED, NO_PERMISSION }

/**
 * The mic for a Play screen, the way the ear games do it: while [listen] (and the app is on screen) the mic runs and
 * the matcher listens for [expected], each group in [groups]; strikes from before listening began are dropped, and
 * [onStrike] gets the rest on the main thread. Asks for the permission once. Returns the mic's state.
 */
@Composable
internal fun rememberMicStrikes(
    mic: MicInput, listen: Boolean, expected: Set<Int>, groups: List<Set<Int>>, label: String,
    onStrike: (MicEvent.Strike) -> Unit,
): MicState {
    var allowed by remember { mutableStateOf(mic.hasPermission()) }
    // 2.15: through the microphone card (a kid's: the gate first); after its Not now the toy opens on its keys, unasked.
    val ask = com.bigpapituna.sharpears.ui.rememberMicAsk(mic) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.ask(auto = true) }
    val active = listen && allowed && onScreen()
    val activeNow by rememberUpdatedState(active)
    val strike by rememberUpdatedState(onStrike)
    var listenFrom by remember { mutableLongStateOf(0L) }
    DisposableEffect(active) {
        if (active && !mic.start()) Log.d(TAG, "play: $label could not start the mic")
        onDispose { if (active) { mic.matcher.expected = emptySet(); mic.stop() } }
    }
    LaunchedEffect(active, expected, groups) {
        mic.matcher.expected = if (active) expected else emptySet()
        mic.matcher.groups = groups
        listenFrom = mic.matcher.samplesProcessed
    }
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!activeNow || e !is MicEvent.Strike) return@collect
            val onset = e.sample - ((e.delayMs ?: 0.0) * MicInput.RATE / 1000).toLong()
            if (onset < listenFrom) { Log.d(TAG, "play: $label ignored ${noteName(e.midi)} (struck before listening)"); return@collect }
            strike(e)
        }
    }
    return when { !allowed -> MicState.NO_PERMISSION; active -> MicState.LISTENING; else -> MicState.PAUSED }
}

/** Milliseconds on the clock every Play screen uses for its timing (monotonic). */
internal fun nowMs() = System.nanoTime() / 1_000_000
