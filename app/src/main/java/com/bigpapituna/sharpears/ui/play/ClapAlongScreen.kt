package com.bigpapituna.sharpears.ui.play

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.play.ClapAlong
import com.bigpapituna.sharpears.engine.playground.ClapCounter
import com.bigpapituna.sharpears.engine.play.ClapSpeed
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Clap along: the pup drums a steady beat at one of three speeds, the player taps the big drum or claps. Tapping is
 * the default (the drum sounds on every beat). Clapping uses the mic through the clap counter; then the pup drums
 * quietly (his drum through the speaker would be counted as claps) unless headphones are on. No score and no end:
 * beats in time light up in a row, and the pup cheers a run.
 */

/** How the player keeps the beat. */
internal enum class ClapMode { TAP, CLAP }

/**
 * Clap along. [frames] is the raw mic audio for the clap counter (without it there is no clapping, only tapping);
 * [clicks] sounds the pup's drum ([sound] says whether the app may make sound at all).
 */
@Composable
fun ClapAlongScreen(mic: MicInput, frames: FrameTap?, clicks: Clicks?, voice: Voice?, sound: () -> Boolean, onExit: () -> Unit) {
    val kid = LocalProfile.current.isKid
    var speed by remember { mutableStateOf(ClapSpeed.SLOW) }
    var mode by remember { mutableStateOf(ClapMode.TAP) }
    val along = remember(speed, mode) { ClapAlong(speed, nowMs() + 1500) }
    var beat by remember { mutableIntStateOf(-1) }
    var version by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf(if (kid) "Tap the drum with me!" else "Tap the drum on my beat.") }
    var glow by remember { mutableIntStateOf(0) }                          // counts claps in time, for the drum's flash
    val still = reducedMotion()
    val squash = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun judge(t: Long, src: String) {
        val c = along.clap(t)
        Log.d(TAG, "clap: $src ${c.verdict} beat ${c.beat} off ${c.offMs} ms, run ${along.streak}")
        when (c.verdict) {
            ClapAlong.Verdict.IN_TIME -> { glow++; along.cheer()?.let { line = it; voice?.speak(it) } }
            ClapAlong.Verdict.EARLY -> if (!kid) line = "A little early. Listen for my drum."
            ClapAlong.Verdict.LATE -> if (!kid) line = "A little late. Clap right on the drum."
            else -> {}
        }
        version++
    }

    // The beat: the pup drums (and the drum sounds when it may), and beats that passed with no clap end the run.
    LaunchedEffect(along) {
        beat = -1
        line = when (mode) {
            ClapMode.TAP -> if (kid) "Tap the drum with me!" else "Tap the drum on my beat."
            ClapMode.CLAP -> if (mic.headphonesOn()) "Clap with my drum!" else "Clap with me! I'll drum quietly so I can hear you."
        }
        voice?.speak(line, interrupt = true)
        var i = 0
        while (true) {
            val wait = along.beatAt(i) - nowMs()
            if (wait > 0) delay(wait)
            beat = i
            if (sound() && (mode == ClapMode.TAP || mic.headphonesOn())) clicks?.play(i % 4 == 0)
            if (!still) scope.launch { squash.snapTo(0.9f); squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 700f)) }
            if (along.tick(nowMs())) version++
            i++
        }
    }

    // Clapping: the mic runs (no notes expected) and the clap counter reads the raw frames on the audio thread.
    val clapping = mode == ClapMode.CLAP && frames != null
    val micState = rememberMicStrikes(mic, listen = clapping, expected = emptySet(), groups = emptyList(), label = "clap") {}
    if (frames != null) {
        val heard = remember { Channel<Long>(Channel.UNLIMITED) }
        // The collector outlives speed and mode changes, so it calls whatever judge and mode are current.
        val judgeNow by rememberUpdatedState<(Long, String) -> Unit> { t, src -> judge(t, src) }
        val modeNow by rememberUpdatedState(mode)
        DisposableEffect(clapping && micState == MicState.LISTENING) {
            if (clapping && micState == MicState.LISTENING) {
                // When each clap began, on the sample clock: the moment the first chunk arrived (less its own length)
                // plus the clap's sample index. Arrival time minus a confirmation delay would wobble with the audio thread.
                val counter = ClapCounter(MicInput.RATE)
                var firstMs = -1L
                counter.listener = { clap -> heard.trySend(firstMs + clap.atSample * 1000 / counter.sampleRate) }
                frames.register { chunk ->
                    if (firstMs < 0) firstMs = nowMs() - chunk.size * 1000L / counter.sampleRate
                    counter.process(chunk)
                }
            }
            onDispose { frames.register(null) }
        }
        LaunchedEffect(heard) { for (t in heard) if (modeNow == ClapMode.CLAP) judgeNow(t, "clap") }
    }

    val dots = remember(version, beat, along) {
        val first = maxOf(0, beat - 7)
        (first..first + 7).map { i -> if (i > beat) null else along.hit(i) }
    }
    ClapAlongContent(
        speed, mode, canClap = frames != null, line = if (mode == ClapMode.CLAP && micState == MicState.NO_PERMISSION) "I need the microphone to hear claps. Tapping works too." else line,
        dots = dots, beatTick = beat, glow = glow, squash = { squash.value }, still = still,
        onSpeed = { speed = it }, onMode = { mode = it }, onTap = { judge(nowMs(), "tap") }, onExit = onExit,
    )
}

@Composable
internal fun ClapAlongContent(
    speed: ClapSpeed, mode: ClapMode, canClap: Boolean, line: String, dots: List<Boolean?>, beatTick: Int, glow: Int,
    squash: () -> Float, still: Boolean, onSpeed: (ClapSpeed) -> Unit, onMode: (ClapMode) -> Unit, onTap: () -> Unit, onExit: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ExitPill(onExit)
            Spacer(Modifier.weight(1f))
            Text("Clap along", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            ClapSpeed.entries.forEach { s -> FilterChip(selected = s == speed, onClick = { onSpeed(s) }, label = { Text(s.word) }) }
        }
        if (canClap) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            FilterChip(selected = mode == ClapMode.TAP, onClick = { onMode(ClapMode.TAP) }, label = { Text("Tap") })
            FilterChip(selected = mode == ClapMode.CLAP, onClick = { onMode(ClapMode.CLAP) }, label = { Text("Clap") })
        }
        // The pup drums and talks from beside his drum (the bubble's tail points at him).
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row(Modifier.widthIn(max = 600.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PupPicture(R.drawable.pup_drumming, 190.dp, Modifier.graphicsLayer {
                    val s = squash(); scaleY = s; scaleX = 2f - s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                })
                SpeechBubble(line, Modifier.weight(1f, fill = false))
            }
        }
        BeatDots(dots)
        // The drum: a big pad. It flashes the pup's gold on every beat and green on a clap in time, then fades back
        // (with animations off it just holds the colour a moment: a change of colour, no movement).
        val tap by androidx.compose.runtime.rememberUpdatedState(onTap)     // the time of the touch-down is the tap
        val rest = c.surfaceContainerHighest
        val pad = remember { androidx.compose.animation.Animatable(rest) }
        suspend fun flash(colour: androidx.compose.ui.graphics.Color) {
            pad.snapTo(colour)
            if (still) { delay(180); pad.snapTo(rest) } else pad.animateTo(rest, tween(320))
        }
        LaunchedEffect(beatTick) { if (beatTick >= 0) flash(PupGold.copy(alpha = 0.55f)) }
        LaunchedEffect(glow) { if (glow > 0) flash(Good) }
        Box(Modifier.align(Alignment.CenterHorizontally).size(168.dp).clip(CircleShape).background(pad.value)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); tap() } }
            .semantics { role = Role.Button; contentDescription = "Drum. Tap on the beat"; onClick { onTap(); true } },
            contentAlignment = Alignment.Center) {
            Text(if (mode == ClapMode.TAP) "Tap" else "Clap", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = c.onSurface)
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** The last eight beats: filled when a clap landed in time, a ring for one that passed, nothing yet for the ones to come. */
@Composable
private fun BeatDots(dots: List<Boolean?>) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "${dots.count { it == true }} of the last beats in time" },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        dots.forEach { d ->
            Box(Modifier.size(18.dp).clip(CircleShape).background(when (d) { true -> Good; false -> c.surfaceContainerHighest; null -> c.surfaceContainerLow }))
        }
    }
}

// ---- previews ----

@Preview(name = "Clap along, tapping", widthDp = 400, heightDp = 820)
@Composable
private fun ClapAlongPreview() = SharpEarsTheme(dark = false) {
    Surface {
        ClapAlongContent(ClapSpeed.WALK, ClapMode.TAP, canClap = true, line = "You're right with me!",
            dots = listOf(true, true, false, true, true, true, true, null), beatTick = 6, glow = 0, squash = { 1f }, still = false,
            onSpeed = {}, onMode = {}, onTap = {}, onExit = {})
    }
}

@Preview(name = "Clap along, clapping, dark", widthDp = 400, heightDp = 820)
@Composable
private fun ClapAlongClapPreview() = SharpEarsTheme(dark = true) {
    Surface {
        ClapAlongContent(ClapSpeed.RUN, ClapMode.CLAP, canClap = true, line = "Clap with me! I'll drum quietly so I can hear you.",
            dots = listOf(true, false, true, null, null, null, null, null), beatTick = 2, glow = 0, squash = { 0.92f }, still = true,
            onSpeed = {}, onMode = {}, onTap = {}, onExit = {})
    }
}
