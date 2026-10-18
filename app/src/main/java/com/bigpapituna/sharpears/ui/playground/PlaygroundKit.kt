package com.bigpapituna.sharpears.ui.playground

import android.content.Context
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.engine.EARLY_HIT_MS
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.ClapSynth
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.practice.PianoGeometry
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File

/*
 * What the Playground's stages share: a wait-mode run of the step's exercise, the microphone while a stage listens,
 * a big keyboard to draw and touch, the painted pup poses, and the little row of round stars.
 */

// ---- The painted pup ------------------------------------------------------------------------------------------------

/**
 * The painted poses the Playground draws (res/drawable-nodpi/pup_*.webp). ui/Pup.kt's Pose (the world team's) lists
 * ten of the twenty-two; these are drawn here directly until it has them all.
 */
enum class Painted(val res: Int) {
    FLYING(R.drawable.pup_flying), STANDING(R.drawable.pup_standing), SLEEPING(R.drawable.pup_sleeping),
    GOODNIGHT(R.drawable.pup_goodnight), SURPRISED(R.drawable.pup_surprised), DRUMMING(R.drawable.pup_drumming),
    CLAPPING(R.drawable.pup_clapping), POINTING(R.drawable.pup_pointing), HEAD_TILT(R.drawable.pup_head_tilt),
    DANCING(R.drawable.pup_dancing), CROWN(R.drawable.pup_crown), GREAT_JOB(R.drawable.pup_great_job),
    LISTENING(R.drawable.pup_listening),
}

/**
 * A painted pose with the pup's manners (as ui/Pup.kt): standing in a soft gold glow over his shadow, a new pose
 * cross-fading in with a little hop. [lift] (0..1) raises him off his shadow, which shrinks; [squash] presses him down
 * (a landing, a drum beat). Decorative: what he means is said beside him. Still with the phone's animations off.
 */
@Composable
fun PaintedPup(pose: Painted, modifier: Modifier = Modifier, size: Dp = 120.dp, lift: () -> Float = { 0f }, squash: () -> Float = { 0f },
               shadow: Boolean = true) {
    val still = reducedMotion()
    val hop = remember { Animatable(1f) }
    LaunchedEffect(pose) { if (!still) { hop.snapTo(0.82f); hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) } }
    Box(modifier.size(size).drawBehind {
        val l = lift().coerceIn(0f, 1f)
        val c = Offset(this.size.width / 2, this.size.height * 0.55f)
        val r = this.size.width * 0.55f
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.28f), Color.Transparent), c, r), r, c)
        if (shadow) {
            val w = this.size.width * 0.56f * (1f - 0.6f * l)
            drawOval(Color.Black.copy(alpha = 0.32f * (1f - 0.5f * l)), Offset((this.size.width - w) / 2, this.size.height * 0.93f),
                Size(w, this.size.height * 0.06f))
        }
    }) {
        Crossfade(pose, animationSpec = if (still) snap() else tween(220), label = "pose") { p ->
            Image(painterResource(p.res), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val s = hop.value
                val q = squash().coerceIn(0f, 1f)
                scaleX = s * (1f + 0.06f * q); scaleY = s * (1f - 0.08f * q)
            })
        }
    }
}

// ---- A run of the step's exercise ------------------------------------------------------------------------------------

/** One key the stage heard (the mic) or felt (a touch), what the transport made of it, and a count to key effects on. */
data class Struck(val midi: Int, val result: InputResult, val seq: Int, val riseDb: Double? = null)

/**
 * The step's exercise in wait mode, as the embedded player would run it but with no lane: the stage decides what to
 * show. [key] feeds a key to the transport; [version] bumps on every change, so drawing can follow. In a game the time
 * between rounds is only pacing, so a key played before the song reaches the next round skips ahead to it rather than
 * being early (a child does not wait for a beat they cannot see).
 */
@Stable
class PlaygroundRun(val song: Song, practice: Practice) {
    val transport = Transport(song).apply { mode = Mode.WAIT; this.practice = practice }
    /** Rounds the player has to play (groups with a required note). */
    val rounds = song.groups.count { g -> g.notes.any(transport::required) }
    var version by mutableIntStateOf(0)
        internal set
    var last by mutableStateOf<Struck?>(null)
        private set
    var started by mutableStateOf(false)
        internal set
    var ended by mutableStateOf(false)
        internal set
    /** Rounds done so far. */
    val done get() = transport.log.groups
    /** The song is at the next round (or nearly): a key now answers it rather than being early. */
    val ready get() = transport.waiting || transport.nextGroup?.let { it.startMs - transport.positionMs <= EARLY_HIT_MS } == true
    /** Where the transport's note events go (the sampler), set by [rememberPlaygroundRun]. */
    internal var onEvents: (List<NoteEvent>) -> Unit = {}

    fun key(midi: Int, riseDb: Double? = null): InputResult {
        if (started && !ended && !ready) transport.nextGroup?.let { g -> onEvents(transport.tick((g.startMs - transport.positionMs) / transport.speed)) }
        val r = transport.noteInput(midi)
        last = Struck(midi, r, (last?.seq ?: 0) + 1, riseDb)
        version++
        Log.d(TAG, "playground: ${noteName(midi)} -> $r (${transport.log.groups}/$rounds)")
        return r
    }
}

/**
 * Runs [exercise] in wait mode while composed: it starts once the voice has read the instruction (and the sounds are
 * loaded), its clock follows the frames, accompaniment sounds through the sampler when [sound] allows, and [onEnded]
 * gets the run's report when the last round is played and the song has run out.
 */
@Composable
internal fun rememberPlaygroundRun(exercise: Exercise, id: String, deps: LessonDeps, sound: () -> Boolean, onEnded: (Report) -> Unit): PlaygroundRun {
    val run = remember(exercise, id) { PlaygroundRun(exercise.toSong(id, id), exercise.practice) }
    val ended by rememberUpdatedState(onEnded)
    val soundNow by rememberUpdatedState(sound)
    run.onEvents = { events ->
        val on = soundNow()
        for (e in events) when (e) { is NoteEvent.On -> if (on) deps.sampler.noteOn(e.midi, e.velocity); is NoteEvent.Off -> deps.sampler.noteOff(e.midi) }
    }
    val speaking = deps.voice.speaking.value
    LaunchedEffect(run, speaking, deps.ready) {
        if (run.started || speaking || !deps.ready) return@LaunchedEffect
        delay(600)
        run.transport.play()
        run.started = true
        Log.d(TAG, "playground: run ${run.song.id} started, ${run.rounds} rounds")
    }
    LaunchedEffect(run) {
        snapshotFlow { run.started }.first { it }
        var last = withFrameNanos { it }
        var waiting = false
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e6).coerceIn(0.0, 100.0)
            last = now
            val events = run.transport.tick(dt)
            run.onEvents(events)
            if (events.isNotEmpty() || run.transport.waiting != waiting) { waiting = run.transport.waiting; run.version++ }
            if (!run.transport.playing) {
                run.ended = true
                Log.d(TAG, "playground: run ${run.song.id} ended, ${run.done}/${run.rounds} rounds, ${run.transport.log.wrongKeys} slips")
                ended(run.transport.report())
                break
            }
        }
    }
    DisposableEffect(run) { onDispose { run.transport.pause().forEach { if (it is NoteEvent.Off) deps.sampler.noteOff(it.midi) } } }
    return run
}

// ---- The microphone ---------------------------------------------------------------------------------------------------

/**
 * The microphone while a stage listens: started while [active] once the voice has let go of it (or, in a debug build,
 * the [inject]ed audio instead, once). [raw] gets every chunk on the audio thread (MicInput.rawFrames; ClapCounter and
 * PitchHeight read it there); the note matcher is asked for [keys], each on its own, and [onStrike] hears its strikes
 * on the main thread. Returns whether it is listening, so the stage can keep the app's own sound out of it.
 */
@Composable
internal fun rememberListening(mic: MicInput, voice: Voice, active: Boolean, inject: String?, keys: Set<Int> = emptySet(),
                               raw: ((FloatArray, Int) -> Unit)? = null, onStrike: (MicEvent.Strike) -> Unit = {}): Boolean {
    val context = LocalContext.current
    val injected = remember { mutableStateOf(false) }
    val listen = active && !voice.holdsMic && (inject != null || mic.hasPermission())
    val strike by rememberUpdatedState(onStrike)
    DisposableEffect(listen) {
        if (!listen) return@DisposableEffect onDispose {}
        val removeRaw = raw?.let { r -> mic.rawFrames.add { chunk -> r(chunk, chunk.size) } }
        mic.matcher.expected = keys; mic.matcher.groups = keys.map { setOf(it) }
        val spec = inject?.takeIf { !injected.value }?.let { debugInjection(context, it) }
        when {
            spec != null -> { injected.value = true; mic.startInjection(spec); Log.d(TAG, "playground: mic injecting $spec") }
            inject != null -> Log.d(TAG, "playground: injection already played")
            mic.start() -> Log.d(TAG, "playground: mic listening for ${keys.map(::noteName)}")
            else -> Log.d(TAG, "playground: mic could not start")
        }
        onDispose { removeRaw?.invoke(); mic.stop(); mic.matcher.expected = emptySet(); mic.matcher.groups = emptyList() }
    }
    LaunchedEffect(keys, listen) { if (listen) { mic.matcher.expected = keys; mic.matcher.groups = keys.map { setOf(it) } } }
    LaunchedEffect(mic) {
        mic.events.collect { e -> if (e is MicEvent.Strike && e.inferredFrom == null) strike(e) }
    }
    return listen
}

/**
 * Debug injections the Playground understands, on top of MicInput's own ("synth:..." and WAV paths, passed through):
 * "claps:400,1150,1900" (synthetic claps at those ms) and "dyn:60/-20,60/-30,60/-8@1600~1500" (notes at gains in dB,
 * [step] ms apart from [first]). Written to a WAV in the cache.
 */
internal fun debugInjection(context: Context, spec: String): String {
    val rate = 48_000
    val samples = when {
        spec.startsWith("claps:") -> spec.removePrefix("claps:").split(',').map { it.trim().toDouble() }.let { t -> ClapSynth.render(t, t.max() + 1500, rate) }
        spec.startsWith("dyn:") -> {
            val (list, timing) = spec.removePrefix("dyn:").split('@').let { it[0] to it.getOrElse(1) { "" } }
            val step = timing.substringBefore('~').toDoubleOrNull() ?: 1600.0
            val first = timing.substringAfter('~', "").toDoubleOrNull() ?: 1500.0
            val notes = list.split(',').mapIndexed { i, n -> n.split('/').let { (m, g) -> SynthNote(m.toInt(), first + i * step, 700.0, gainDb = g.toDouble()) } }
            PianoSynth.render(notes, first + notes.size * step + 1000, rate)
        }
        else -> return spec
    }
    val file = File(context.cacheDir, "playground-inject.wav")
    Wav.write(file, samples, rate)
    return file.path
}

// ---- The keyboard -------------------------------------------------------------------------------------------------------

private val WhiteTop = Color(0xFFFFFCF7)
private val WhiteBottom = Color(0xFFE9E1D6)
private val KeyGap = Color(0xFF15110F)
private val BlackTop = Color(0xFF3A322C)
private val BlackBottom = Color(0xFF110E0C)

/**
 * Draws a flat piano in [g]: white keys with a soft gradient, black keys polished, keys in [fill] washed in their colour
 * (a found group, the side to play), and [flash] (0..1) lighting one key bright for a moment.
 */
internal fun DrawScope.piano(g: PianoGeometry, fill: (Int) -> Color? = { null }, flash: Pair<Int, Float>? = null) {
    drawRect(KeyGap, Offset(0f, g.top), Size(g.width, g.height))
    val gap = 1.5f
    val wr = CornerRadius(g.ww * 0.14f)
    for (m in g.whites) {
        val r = g.rect(m)
        drawRoundRect(Brush.verticalGradient(listOf(WhiteTop, WhiteBottom), r.top, r.bottom), Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr)
        fill(m)?.let { c -> drawRoundRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.85f)), r.top, r.bottom),
            Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr) }
        if (flash?.first == m && flash.second > 0f) drawRoundRect(Color.White.copy(alpha = 0.55f * flash.second), Offset(r.left + gap / 2, r.top), Size(r.width - gap, r.height), wr)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), g.top, g.top + g.height * 0.08f), Offset(0f, g.top), Size(g.width, g.height * 0.08f))
    for (m in (g.low..g.high).filter(::isBlack)) {
        val r = g.rect(m)
        val br = CornerRadius(g.bw * 0.18f)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(r.left + g.bw * 0.12f, r.top), Size(r.width, r.height + 2f), br)
        // A lit black key stays a solid, darker shade of its colour (see-through, it would look white).
        val c = fill(m)?.let { lerp(BlackBottom, it.copy(alpha = 1f), 0.2f + 0.4f * it.alpha) }
        val brush = if (c == null) Brush.verticalGradient(listOf(BlackTop, BlackBottom), r.top, r.bottom)
            else Brush.verticalGradient(listOf(lerp(BlackBottom, c, 0.6f), c), r.top, r.bottom)
        drawRoundRect(brush, Offset(r.left, r.top), Size(r.width, r.height), br)
        drawRoundRect(Color.White.copy(alpha = if (c == null) 0.08f else 0.18f), Offset(r.left + r.width * 0.22f, r.top + r.height * 0.08f),
            Size(r.width * 0.56f, r.height * 0.78f), CornerRadius(r.width * 0.3f))
        if (flash?.first == m && flash.second > 0f) drawRoundRect(Color.White.copy(alpha = 0.5f * flash.second), Offset(r.left, r.top), Size(r.width, r.height), br)
    }
}

/**
 * A big keyboard from [low] to [high] (white keys at both ends) to look at and to tap: [fill] washes keys in a colour,
 * the [last] key struck flashes, and a tap calls [onKey]. [overlay] draws on top, in the same geometry.
 */
@Composable
internal fun BigKeys(low: Int, high: Int, description: String, modifier: Modifier = Modifier, fill: (Int) -> Color? = { null }, last: Struck? = null,
                     onKey: ((Int) -> Unit)? = null, topRoom: Dp = 0.dp, overlay: DrawScope.(PianoGeometry) -> Unit = {}) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val room = with(androidx.compose.ui.platform.LocalDensity.current) { topRoom.toPx() }
    val geo = remember(low, high, size, room) { if (size.width == 0) null else PianoGeometry(low, high, size.width.toFloat(), size.height - room, room) }
    val flash = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(last?.seq) { if (last != null) { flash.snapTo(1f); flash.animateTo(0f, if (still) snap(400) else tween(600)) } }
    val tap by rememberUpdatedState(onKey)
    Canvas(modifier.onSizeChanged { size = it }.semantics { contentDescription = description }.pointerInput(geo, onKey != null) {
        val g = geo ?: return@pointerInput
        if (onKey == null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            g.keyAt(down.position.x, down.position.y)?.let { down.consume(); tap?.invoke(it) }
        }
    }) {
        val g = geo ?: return@Canvas
        piano(g, fill, last?.let { it.midi to flash.value })
        overlay(g)
    }
}

// ---- Small pieces -----------------------------------------------------------------------------------------------------

/** One star per round, filled as rounds are done: progress without a number. */
@Composable
internal fun RoundStars(rounds: Int, done: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = (rounds * 22).dp, height = 22.dp).semantics { contentDescription = "$done of $rounds done" }) {
        val r = size.height * 0.42f
        for (i in 0 until rounds) {
            val c = Offset(size.height / 2 + i * size.height, size.height / 2)
            val p = star(c, r)
            if (i < done) drawPath(p, PupGold) else drawPath(p, Ink.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** A five-pointed star around [c]. */
internal fun star(c: Offset, r: Float, inner: Float = 0.45f): Path = Path().apply {
    for (k in 0 until 10) {
        val a = Math.PI * (k / 5.0) - Math.PI / 2
        val rr = if (k % 2 == 0) r else r * inner
        val x = c.x + (rr * Math.cos(a)).toFloat(); val y = c.y + (rr * Math.sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A short line the pup calls out inside a stage (not spoken: the mic is listening), in a small gold-edged bubble. */
@Composable
internal fun StageLine(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(modifier.background(Color(0xFF2A221C), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/** The stage's top row: the round stars on the left, anything else on the right. */
@Composable
internal fun StageTop(rounds: Int, done: Int, modifier: Modifier = Modifier, end: @Composable () -> Unit = {}) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        RoundStars(rounds, done)
        end()
    }
}

/** Keeps a short line on screen for [ms] after [key] changes. */
@Composable
internal fun rememberFleeting(key: Any?, text: String?, ms: Long = 1600): String? {
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(key) { shown = text; if (text != null) { delay(ms); shown = null } }
    return shown
}
