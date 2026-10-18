package com.bigpapituna.sharpears.ui.play

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.play.ChordEar
import com.bigpapituna.sharpears.engine.play.Jam
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.play.QuestTask
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.SpeechBubble
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The Jam: every note does something, nothing is asked. Mic notes (and MIDI, and the keys on screen) make the key's
 * colour burst, the pup jump to the pitch, chords throw confetti and a quick rising run paints a rainbow. No score,
 * no timer, no sounds of its own: the piano is the sound. The effects are drawn here on one Canvas (not the shared
 * celebration pieces), and with the phone's animations off they hold still: a glow, a row of dots, a still arc.
 */

/** The effects alive on screen, each with its start time on [nowMs]'s clock. [frameMs] is the clock the Canvas draws at. */
internal class JamFx {
    class Burst(val id: Long, val midi: Int, val colour: Color, val start: Long)
    class Shower(val id: Long, val colours: List<Color>, val start: Long)
    class Arc(val low: Int, val high: Int, val start: Long)

    val bursts = mutableStateListOf<Burst>()
    val showers = mutableStateListOf<Shower>()
    val arcs = mutableStateListOf<Arc>()
    var frameMs by mutableLongStateOf(0L)
    var wake by mutableIntStateOf(0)
    private var ids = 0L

    fun burst(midi: Int, at: Long) { bursts += Burst(ids++, midi, noteColour(midi), at); wake++ }
    fun shower(notes: List<Int>, at: Long) { showers += Shower(ids++, notes.map(::noteColour), at); wake++ }
    fun arc(notes: List<Int>, at: Long) { arcs += Arc(notes.min(), notes.max(), at); wake++ }

    /** Drops what has finished; true while anything is still alive. */
    fun prune(now: Long): Boolean {
        bursts.removeAll { now - it.start > BURST_MS }
        showers.removeAll { now - it.start > SHOWER_MS }
        arcs.removeAll { now - it.start > ARC_MS }
        return bursts.isNotEmpty() || showers.isNotEmpty() || arcs.isNotEmpty()
    }

    companion object {
        const val BURST_MS = 700L
        const val SHOWER_MS = 1800L
        const val ARC_MS = 1700L
    }
}

/**
 * The Jam. [mic] and [midi] bring the notes; [frames] (raw mic audio) lets the chord ear find a chord's other notes
 * (without it, chords count when the matcher or MIDI hears three keys). [hint] replaces the pup's opening line (the
 * quest card can pass "Can you find all the Cs?"). Cs heard count toward the quest's "Find all the Cs".
 */
@Composable
fun JamScreen(mic: MicInput, midi: MidiDevices?, frames: FrameTap?, onExit: () -> Unit, hint: String? = null) {
    val profile = LocalProfile.current
    val kid = profile.isKid
    val context = LocalContext.current
    val store = remember(profile.id) { PlayStore(context, profile.id) }
    val jam = remember { Jam() }
    val fx = remember { JamFx() }
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(0f) }
    var lastNote by remember { mutableLongStateOf(0L) }
    var lastChord by remember { mutableLongStateOf(0L) }
    var pupRes by remember { mutableIntStateOf(R.drawable.pup_listening) }
    var line by remember { mutableStateOf(hint ?: openingLine(kid)) }
    val lit = remember { mutableStateMapOf<Int, Color>() }
    val flash = remember { mutableStateMapOf<Int, Color>() }
    val recentHits = remember { HashMap<Int, Long>() }

    fun hit(m: Int, at: Long, src: String) {
        recentHits[m] = at
        lastNote = nowMs()
        for (e in jam.strike(m, at)) when (e) {
            is Jam.Effect.Burst -> {
                fx.burst(m, nowMs())
                val c = noteColour(m)
                lit[m] = c; flash[m] = c
                scope.launch { delay(260); if (lit[m] == c) lit.remove(m); delay(200); if (flash[m] == c) flash.remove(m) }
            }
            is Jam.Effect.Bounce -> {
                pupRes = when {
                    nowMs() - lastChord < 1500 -> R.drawable.pup_clapping
                    e.height > 0.75f -> R.drawable.pup_flying
                    else -> R.drawable.pup_dancing
                }
                if (!still) scope.launch {
                    bounce.animateTo(e.height, tween(110, easing = FastOutSlowInEasing))
                    bounce.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
                }
            }
            is Jam.Effect.Chord -> {
                fx.shower(e.notes, nowMs()); lastChord = nowMs(); pupRes = R.drawable.pup_clapping
                line = if (kid) "A chord! Confetti!" else "A chord. Confetti for that."
                Log.d(TAG, "jam: chord ${e.notes.joinToString(" ") { noteName(it) }}")
            }
            is Jam.Effect.Rainbow -> {
                fx.arc(e.notes, nowMs())
                line = if (kid) "A rainbow run!" else "A rising run: a rainbow."
                Log.d(TAG, "jam: rainbow ${noteName(e.notes.first())} to ${noteName(e.notes.last())}")
            }
        }
        Log.d(TAG, "jam: ${noteName(m)} ($src)")
        if (m % 12 == 0) {
            val stars: List<QuestTask> = store.recordQuest(QuestEvent.NotePlayed(m), kid)
            if (stars.isNotEmpty()) line = if (kid) "You found all the Cs! A quest star!" else "All the Cs: a quest star."
        }
    }

    // The chord ear listens to the raw audio while the mic runs.
    val ear = remember { if (frames != null) ChordEar() else null }
    val micState = rememberMicStrikes(mic, listen = true, expected = jam.micNotes(), groups = jam.micGroups(), label = "jam") { e ->
        val onset = e.sample - ((e.delayMs ?: 0.0) * MicInput.RATE / 1000).toLong()
        val at = nowMs() - (mic.matcher.samplesProcessed - onset) * 1000 / MicInput.RATE
        if (jam.micEcho(e.midi, at)) { Log.d(TAG, "jam: ${noteName(e.midi)} dropped (an echo of a higher note)"); return@rememberMicStrikes }
        if (recentHits[e.midi]?.let { at - it < 200 } == true) return@rememberMicStrikes       // the chord ear already showed it
        hit(e.midi, at, "mic")
        if (ear != null) scope.launch {
            // Ask the chord ear once the audio after the onset is in (about 200 ms), then show the chord's other keys.
            val until = nowMs() + 700
            var notes: Set<Int>? = null
            while (notes == null && nowMs() < until) { delay(40); notes = ear.notesAt(onset) }
            notes.orEmpty().filter { n -> n in CHORD_KEYS && n != e.midi && abs(n - e.midi) != 1 && recentHits[n]?.let { at - it < 200 } != true }
                .forEach { hit(it, at, "chord ear") }
        }
    }
    if (ear != null && frames != null) DisposableEffect(micState == MicState.LISTENING) {
        if (micState == MicState.LISTENING) {
            // The ring keeps the matcher's sample clock: counted here chunk by chunk, set from the matcher at the start
            // and whenever the two drift apart by more than a few chunks (a mic restart), so it does not matter whether
            // the frames arrive just before or just after the matcher sees them.
            var at = -1L
            frames.register { chunk ->
                val m = mic.matcher.samplesProcessed
                if (at < 0 || abs(m - at) > 4 * chunk.size.coerceAtLeast(512)) at = m
                ear.feed(chunk, chunk.size, at)
                at += chunk.size
            }
        }
        onDispose { frames.register(null) }
    }
    if (midi != null) LaunchedEffect(midi) {
        midi.events.collect { e -> if (e is NoteEvent.On) hit(e.midi, nowMs(), "midi") }
    }
    // The effects' frame clock: runs only while something is on screen.
    LaunchedEffect(fx) {
        snapshotFlow { fx.wake }.collect {
            var alive = true
            while (alive) alive = withFrameNanos { fx.frameMs = it / 1_000_000; fx.prune(fx.frameMs) }
        }
    }
    // A while with no notes: the pup settles, then says he is ready (once per pause, never a nag).
    LaunchedEffect(lastNote) {
        if (lastNote == 0L) return@LaunchedEffect
        delay(1600); if (pupRes == R.drawable.pup_clapping || pupRes == R.drawable.pup_flying) pupRes = R.drawable.pup_dancing
        delay(2400); pupRes = R.drawable.pup_listening
        delay(2000); line = if (kid) "Play some more! I'm ready." else "Ready when you are."
    }

    val shown = if (micState != MicState.NO_PERMISSION) line
        else if (kid) "I need the microphone to hear you. Ask a grown-up, or tap the keys here."
        else "The microphone is off, so I can't hear the piano. The keys here still work."
    JamContent(shown, pupRes, micState, { bounce.value }, lit, flash, fx, still, onExit,
        onKey = { m -> hit(m, nowMs(), "touch") })
}

/** The keys on screen: middle C to C5, the Jam's range. */
private const val LOW = Jam.LOW
private const val HIGH = Jam.HIGH
/** Keys the chord ear may add (it hears wider than the screen; past C3 and C6 it is guessing). */
private val CHORD_KEYS = 48..84

private fun openingLine(kid: Boolean) =
    if (kid) "Play anything! Every key has a colour." else "Play anything. Every note makes something happen."

/** The Jam's look, without the mic: for the screen and its previews. */
@Composable
internal fun JamContent(
    line: String, pupRes: Int, micState: MicState, bounce: () -> Float, lit: Map<Int, Color>, flash: Map<Int, Color>, fx: JamFx,
    still: Boolean, onExit: () -> Unit, onKey: (Int) -> Unit,
) {
    var keys by remember { mutableStateOf(Rect.Zero) }
    var pup by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExitPill(onExit)
                Spacer(Modifier.weight(1f))
                Text(when (micState) { MicState.LISTENING -> "Listening"; MicState.PAUSED -> "Mic paused"; MicState.NO_PERMISSION -> "Mic off" },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (micState == MicState.LISTENING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SpeechBubble(line, Modifier.padding(start = 8.dp))
            // The pup jumps up to two fifths of the space above the keys, scaled to the pitch (read at layout, so the
            // jump never recomposes the screen).
            var room by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxWidth().weight(1f).onGloballyPositioned { room = it.size.height.toFloat() }, contentAlignment = Alignment.BottomCenter) {
                PupPicture(pupRes, 168.dp, Modifier
                    .offset { IntOffset(0, -(bounce() * room * 0.4f).roundToInt()) }
                    .onGloballyPositioned { pup = it.boundsInRoot() })
            }
            Keyboard(LOW, HIGH, lit = lit, onNoteOn = { m, _ -> onKey(m) }, onNoteOff = {}, flash = flash, sheen = false,
                modifier = Modifier.fillMaxWidth().height(200.dp).onGloballyPositioned { keys = it.boundsInRoot() })
        }
        Canvas(Modifier.fillMaxSize()) {
            if (keys == Rect.Zero) return@Canvas
            val k = keys.translate(-origin)
            val layout = KeyboardLayout(LOW, HIGH, k.width, k.height)
            val now = fx.frameMs
            fx.arcs.forEach { drawArcFx(it, layout, k, now, still) }
            fx.bursts.forEach { drawBurst(it, layout, k, now, still) }
            fx.showers.forEach { drawShower(it, pup.translate(-origin), now, still) }
        }
    }
}

/** Where a key's burst starts: the top of the key, at its middle (keys off the screen burst from the nearest end). */
private fun keyTop(midi: Int, layout: KeyboardLayout, k: Rect): Offset {
    val m = midi.coerceIn(LOW, HIGH)
    val r = layout.rect(m)
    return Offset(k.left + r.centerX, k.top)
}

private fun ease(p: Float) = 1f - (1f - p) * (1f - p)

/** A ring and a fan of sparks out of the key, in its colour. Still: a glow over the key that fades. */
private fun DrawScope.drawBurst(b: JamFx.Burst, layout: KeyboardLayout, k: Rect, now: Long, still: Boolean) {
    val p = ((now - b.start).toFloat() / JamFx.BURST_MS).coerceIn(0f, 1f)
    val o = keyTop(b.midi, layout, k)
    val alpha = 1f - p
    if (still) { drawCircle(b.colour.copy(alpha = 0.55f * alpha), 34.dp.toPx(), o); return }
    val e = ease(p)
    drawCircle(b.colour.copy(alpha = 0.35f * alpha), 14.dp.toPx() + 60.dp.toPx() * e, o)
    drawCircle(b.colour.copy(alpha = 0.9f * alpha), 16.dp.toPx() + 70.dp.toPx() * e, o, style = Stroke((5f * (1 - p) + 1f) * density))
    val sparks = 10
    for (i in 0 until sparks) {
        val a = Math.toRadians(195.0 + i * 150.0 / (sparks - 1))                      // a fan upward
        val d = 28.dp.toPx() + 95.dp.toPx() * e * (0.8f + 0.4f * ((b.id + i) % 3) / 2f)
        drawCircle(b.colour.copy(alpha = alpha), 5.dp.toPx() * (1f - 0.7f * p), Offset(o.x + (d * cos(a)).toFloat(), o.y + (d * sin(a)).toFloat()))
    }
}

/** Confetti in the chord's colours, out of the pup. Still: a row of the chord's colours under him. */
private fun DrawScope.drawShower(s: JamFx.Shower, pup: Rect, now: Long, still: Boolean) {
    val t = (now - s.start) / 1000f
    val p = (t * 1000 / JamFx.SHOWER_MS).coerceIn(0f, 1f)
    val c = if (pup == Rect.Zero) Offset(size.width / 2, size.height / 3) else pup.center
    if (still) {
        val r = 9.dp.toPx(); val gap = 26.dp.toPx()
        s.colours.forEachIndexed { i, col -> drawCircle(col.copy(alpha = if (p > 0.8f) (1 - p) * 5 else 1f), r, Offset(c.x + (i - (s.colours.size - 1) / 2f) * gap, pup.bottom + 14.dp.toPx())) }
        return
    }
    val r = java.util.Random(s.id * 7919)
    repeat(70) { i ->
        val vx = (r.nextFloat() * 2 - 1) * size.width * 0.55f
        val vy = -(0.35f + r.nextFloat() * 0.6f) * size.height * 0.9f
        val spin = r.nextFloat() * 720f - 360f
        val scale = 0.6f + r.nextFloat() * 0.8f
        val x = c.x + vx * t
        val y = c.y + vy * t + 0.5f * size.height * 1.6f * t * t
        val w = 10.dp.toPx() * scale; val h = 6.dp.toPx() * scale
        val col = s.colours[i % s.colours.size]
        rotate(spin * t, Offset(x, y)) { drawRect(col.copy(alpha = if (p > 0.7f) (1 - p) / 0.3f else 1f), Offset(x - w / 2, y - h / 2), Size(w, h)) }
    }
}

/** A rainbow from the run's lowest key to its highest, over the keys: drawn out left to right, then fading. */
private fun DrawScope.drawArcFx(a: JamFx.Arc, layout: KeyboardLayout, k: Rect, now: Long, still: Boolean) {
    val ms = now - a.start
    val grow = if (still) 1f else ease((ms / 450f).coerceIn(0f, 1f))
    val fade = ((JamFx.ARC_MS - ms) / 600f).coerceIn(0f, 1f)
    val from = keyTop(a.low, layout, k); val to = keyTop(a.high, layout, k)
    val cx = (from.x + to.x) / 2
    val radius = maxOf(abs(to.x - from.x) / 2 + 24.dp.toPx(), 70.dp.toPx())
    val band = 7.dp.toPx()
    RAINBOW.forEachIndexed { i, col ->
        val r = radius - i * band
        drawArc(col.copy(alpha = 0.85f * fade), 180f, 180f * grow, useCenter = false,
            topLeft = Offset(cx - r, k.top - r), size = Size(2 * r, 2 * r), style = Stroke(band))
    }
}

/** Red outside to purple inside: the Boomwhacker colours C to B. */
private val RAINBOW = listOf(60, 62, 64, 65, 67, 69, 71).map(::noteColour)

// ---- previews ----

private fun previewFx(at: Long) = JamFx().apply {
    burst(64, at - 250); burst(67, at - 120); shower(listOf(60, 64, 67), at - 500); arc(listOf(60, 62, 64, 65, 67), at - 300)
    frameMs = at
}

@Preview(name = "Jam, mid-chord", widthDp = 400, heightDp = 820)
@Composable
private fun JamPreview() = SharpEarsTheme(dark = false) {
    Surface {
        JamContent("A chord! Confetti!", R.drawable.pup_clapping, MicState.LISTENING, bounce = { 0.5f },
            lit = mapOf(64 to noteColour(64), 67 to noteColour(67)), flash = emptyMap(), fx = previewFx(10_000), still = false, onExit = {}, onKey = {})
    }
}

@Preview(name = "Jam, dark, still, no mic", widthDp = 400, heightDp = 820)
@Composable
private fun JamStillPreview() = SharpEarsTheme(dark = true) {
    Surface {
        JamContent("The microphone is off, so I can't hear the piano. The keys here still work.", R.drawable.pup_listening, MicState.NO_PERMISSION,
            bounce = { 0f }, lit = emptyMap(), flash = emptyMap(), fx = previewFx(10_000), still = true, onExit = {}, onKey = {})
    }
}
