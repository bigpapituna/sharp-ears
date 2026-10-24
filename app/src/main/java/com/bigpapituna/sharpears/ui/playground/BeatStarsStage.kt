package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.Clicks
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.playground.BeatSound
import com.bigpapituna.sharpears.engine.playground.BeatStars
import com.bigpapituna.sharpears.engine.playground.ClapTries
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose

private enum class BeatPhase { READY, DEMO, COUNT_IN, PLAYING, DONE, NOTHING_HEARD }

/**
 * 2.3 part B, a key on every beat (the night sky's steady beat and metronome game, [BeatStars]): a star for each beat
 * of the exercise hangs across the sky. The pup shows a bar first (he plays middle C on each beat, the stars lighting),
 * then the drum or the metronome counts in a bar and keeps the beat, and every C heard within a quarter of a second of
 * a beat lights its star (the matcher's own delay taken off). A key between the beats or another key is answered on
 * the keys but costs nothing; a toy that never fails. Nothing heard: the pup asks again by himself, three silent tries
 * let it go ([ClapTries]); a grown-up can skip.
 */
@Composable
internal fun BeatStarsStage(game: StageGame.Beat, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                            modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val hits = remember(ex) { parseTokens(ex.right).filter { !it.rest }.map { it.startBeat } }
    val stars = remember(ex) { BeatStars(hits, ex.bpm) }
    val length = remember(hits) { ceil(hits.last() + 1.0).toInt() }
    val beatMs = stars.beatMs
    val context = LocalContext.current
    val drum = remember { if (game.sound == BeatSound.DRUM) runCatching { DrumSound(context) }.getOrNull() else null }
    val clicks = remember { if (game.sound == BeatSound.CLICK) runCatching { Clicks(context) }.getOrNull() else null }
    DisposableEffect(Unit) { onDispose { drum?.release(); clicks?.release() } }
    fun tick(accent: Boolean) {
        if (!deps.prefs.sound) return
        drum?.play(accent); clicks?.play(accent)
    }
    val still = reducedMotion()
    val scope = rememberCoroutineScope()
    val ended by rememberUpdatedState(onEnded)
    val fx = remember { StageFx() }
    val mic = deps.mic
    val voice = deps.voice

    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(BeatPhase.READY) }
    var demoLit by remember { mutableIntStateOf(-1) }
    var beatNow by remember { mutableIntStateOf(-99) }
    val lit = remember { mutableStateListOf<Int>() }
    var keysHeard by remember { mutableIntStateOf(0) }
    var startAt by remember { mutableLongStateOf(0L) }
    val pulse = remember { Animatable(0f) }
    var line by remember { mutableStateOf<String?>(null) }
    var lineSeq by remember { mutableIntStateOf(0) }
    fun say(text: String) { line = text; lineSeq++; voice.sayOnStage(text, mic) }
    fun beatPulse() { if (!still) scope.launch { pulse.snapTo(1f); pulse.animateTo(0f, tween((beatMs * 0.7).toInt())) } }

    /** A key heard at [onset] (uptime ms): a C lights the star of its beat, if it is on one. */
    fun key(midi: Int, onset: Long) {
        if (phase != BeatPhase.COUNT_IN && phase != BeatPhase.PLAYING) return
        keysHeard++
        if (midi % 12 != 0) return
        val ms = (onset - startAt) - BeatStars.COUNT_IN * beatMs
        val star = stars.heard(ms)
        Log.d(TAG, "playground: C at ${"%.0f".format(ms)} ms from beat one -> ${star?.let { "star ${it + 1}" } ?: "between beats"}")
        if (star != null && star !in lit) { lit += star; fx.found(midi); com.bigpapituna.sharpears.ui.world.WorldSounds.stagePop() }
    }

    val listening = rememberStageEar(mic, voice, phase == BeatPhase.COUNT_IN || phase == BeatPhase.PLAYING, inject?.takeIf { attempt == 0 }, setOf(60)) { s ->
        fx.heard(s)
        if (s.final && s.by == HeardBy.NAMED && s.midi != null && s.midi % 12 != 0) fx.wrong(s.midi, 60)
    }
    val listeningNow by rememberUpdatedState(listening)
    // The beat needs the strike's own time: the matcher decides a little after the key, and says how long after.
    LaunchedEffect(mic) {
        mic.events.collect { e ->
            if (!listeningNow) return@collect
            val now = SystemClock.uptimeMillis()
            when (e) {
                is MicEvent.Strike -> if (e.inferredFrom == null) key(e.midi, now - (e.delayMs ?: 110.0).toLong())
                is MicEvent.Wrong -> key(e.midi, now - 110)
            }
        }
    }

    // The demo, once the voice has read the instruction: the pup plays the first bar, the stars light, "Your turn!".
    val speaking = voice.speaking.value
    var demoDone by remember { mutableStateOf(false) }
    var go by remember { mutableIntStateOf(-1) }
    var demoGo by remember { mutableStateOf(false) }
    LaunchedEffect(speaking, deps.ready) {
        if (demoGo || phase != BeatPhase.READY || demoDone || speaking || !deps.ready) return@LaunchedEffect
        delay(500)
        demoGo = true
    }
    // Its own effect: the pup speaking inside it must not restart it.
    LaunchedEffect(demoGo) {
        if (!demoGo || demoDone) return@LaunchedEffect
        phase = BeatPhase.DEMO
        say(PlaygroundLines.LET_ME_SHOW)
        delay(1_300)
        val bar = hits.indices.filter { hits[it] < 4.0 }
        val t0 = withFrameNanos { it }
        for (k in bar) {
            val due = t0 + (hits[k] * beatMs * 1e6).toLong()
            while (withFrameNanos { it } < due) { }
            tick(accent = hits[k] == 0.0)
            if (deps.prefs.sound) { deps.sampler.noteOn(60, 80); scope.launch { delay((beatMs * 0.6).toLong()); deps.sampler.noteOff(60) } }
            demoLit = k; beatPulse()
            fx.ripple(60, PupGold, fill = true)
        }
        delay(beatMs.toLong())
        demoLit = -1
        say(PlaygroundLines.YOUR_TURN)
        delay(900)
        demoDone = true
        phase = BeatPhase.READY
    }
    LaunchedEffect(attempt, demoDone, speaking) {
        if (!demoDone || go == attempt || phase != BeatPhase.READY || speaking) return@LaunchedEffect
        delay(500)
        go = attempt
    }
    LaunchedEffect(go) {
        if (go < 0) return@LaunchedEffect
        stars.reset(); lit.clear(); keysHeard = 0
        phase = BeatPhase.COUNT_IN
        val t0 = withFrameNanos { it }
        startAt = SystemClock.uptimeMillis()
        Log.d(TAG, "playground: beat stars at ${ex.bpm.toInt()} for ${hits.size} stars (try ${attempt + 1}), ${game.sound}")
        for (b in -BeatStars.COUNT_IN until length) {
            val due = t0 + ((b + BeatStars.COUNT_IN) * beatMs * 1e6).toLong()
            while (withFrameNanos { it } < due) { }
            beatNow = b
            if (b == 0) phase = BeatPhase.PLAYING
            tick(accent = Math.floorMod(b, 4) == 0)
            beatPulse()
        }
        delay((beatMs + 350).toLong())                   // the last beat, and the matcher's word on it
        val heardAny = keysHeard > 0
        phase = if (heardAny) BeatPhase.DONE else BeatPhase.NOTHING_HEARD
        Log.d(TAG, "playground: beat stars done, ${lit.size} of ${hits.size} lit, $keysHeard keys")
        when (ClapTries.after(attempt, heardAny)) {
            ClapTries.Next.DONE -> {
                say(when { lit.size == hits.size -> TreeSkyLines.BEAT_ALL; lit.isNotEmpty() -> TreeSkyLines.BEAT_SOME; else -> TreeSkyLines.BEAT_NEAR })
                mic.deafFor(800); com.bigpapituna.sharpears.ui.world.WorldSounds.ding()
                delay(1_800)
                ended(stars.report())
            }
            ClapTries.Next.AGAIN -> {
                say(TreeSkyLines.BEAT_AGAIN)
                delay(2_200)
                if (phase == BeatPhase.NOTHING_HEARD) { attempt++; phase = BeatPhase.READY }
            }
            ClapTries.Next.LET_GO -> {
                say(PlaygroundLines.LET_GO)
                delay(2_200)
                if (phase == BeatPhase.NOTHING_HEARD) ended(Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0))
            }
        }
    }

    val shown = rememberFleeting(lineSeq, line, ms = (2_400 * FxSlow.factor).toLong()) ?: when (phase) {
        BeatPhase.COUNT_IN -> listOf("One", "Two", "Three", "Four")[Math.floorMod(beatNow, 4)]
        BeatPhase.PLAYING -> TreeSkyLines.ON_THE_BEAT
        BeatPhase.READY -> TreeSkyLines.PLAY_C
        else -> null
    }
    val playingBeat = if (phase == BeatPhase.PLAYING) hits.indexOfFirst { it >= beatNow.toDouble() - 0.01 && it < beatNow + 1.0 } else -1

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        // No moon: the beat's stars arc across its corner.
        SkyBackdrop(Modifier.fillMaxSize(), moon = false)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // The stars across the sky, one a beat.
            Canvas(Modifier.fillMaxWidth().height((roomH * 0.24f).coerceIn(64.dp, 150.dp)).semantics { contentDescription = "${lit.size} of ${hits.size} stars lit" }) {
                beatStars(hits.size, if (phase == BeatPhase.DEMO) (0..demoLit).toSet() else lit.toSet(), if (phase == BeatPhase.DEMO) demoLit else playingBeat, pulse.value)
            }
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((roomH * 0.26f).coerceIn(80.dp, 150.dp)), contentAlignment = Alignment.Center) {
                    if (game.sound == BeatSound.CLICK) Canvas(Modifier.fillMaxSize()) { metronome(beatNow, pulse.value, still) }
                    else StagePup(if (phase == BeatPhase.DONE) Pose.GREAT_JOB else if (phase == BeatPhase.NOTHING_HEARD) Pose.HEAD_TILT else Pose.DRUMMING,
                        size = (roomH * 0.26f).coerceIn(80.dp, 150.dp), squash = { pulse.value }, fx = fx)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    if (game.sound == BeatSound.CLICK) StagePup(if (phase == BeatPhase.DONE) Pose.GREAT_JOB else Pose.LISTENING,
                        size = (roomH * 0.16f).coerceIn(56.dp, 96.dp), fx = fx)
                    StageLine(shown)
                }
            }
            if (phase == BeatPhase.NOTHING_HEARD) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { ended(Report(emptyList(), 0, 0, null, groups = 0, extraScore = 0)) }) { Text("Skip") }
                ChunkyButton(onClick = { attempt++; phase = BeatPhase.READY }) { Text("Play again") }
            }
            BigKeys(57, 67, "Piano keys from A3 to G4. Play middle C on every beat.", Modifier.fillMaxWidth().height((roomH * 0.3f).coerceIn(110.dp, 190.dp)),
                fill = { m -> if (m == 60) PupGold.copy(alpha = 0.25f + 0.45f * pulse.value) else null }, fx = fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!listening || mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(500); deps.sampler.noteOff(m) } }
                    fx.heard(com.bigpapituna.sharpears.engine.playground.StageSound(SystemClock.uptimeMillis(), HeardBy.MATCHER, m, final = true))
                    key(m, SystemClock.uptimeMillis())
                })
        }
        if (phase == BeatPhase.DONE && lit.isNotEmpty()) Confetti(attempt, Modifier.fillMaxSize())
    }
}

/** [n] stars along a gentle arc, [lit] ones gold, the one on the beat now ([now]) ringed and pulsing. */
private fun DrawScope.beatStars(n: Int, lit: Set<Int>, now: Int, pulse: Float) {
    val r = minOf(size.height * 0.22f, size.width / (n * 2.6f))
    for (i in 0 until n) {
        val t = if (n == 1) 0.5f else i / (n - 1f)
        val c = Offset(size.width * (0.06f + 0.88f * t), size.height * (0.62f - 0.38f * sin(PI * t).toFloat()))
        val on = i in lit
        if (on) drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.6f), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
        drawPath(star(c, r * (if (i == now) 1f + 0.25f * pulse else 1f), inner = 0.45f), if (on) PupGold else Color.White.copy(alpha = 0.18f))
        drawPath(star(c, r, inner = 0.45f), Color.White.copy(alpha = if (on) 0.9f else 0.4f), style = Stroke(1.2.dp.toPx()))
        if (i == now) drawCircle(Ink.copy(alpha = 0.6f), r * 1.5f, c, style = Stroke(1.5.dp.toPx()))
    }
}

/** A wooden metronome, its arm swinging with the beat ([beat]: the beat number; [pulse] 1 on the click). */
private fun DrawScope.metronome(beat: Int, pulse: Float, still: Boolean) {
    val w = size.width; val h = size.height
    val body = Path().apply { moveTo(w * 0.3f, h * 0.92f); lineTo(w * 0.7f, h * 0.92f); lineTo(w * 0.58f, h * 0.1f); lineTo(w * 0.42f, h * 0.1f); close() }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF9A6A40), Color(0xFF5E3A20))))
    drawRoundRect(Color(0xFF3B2616), Offset(w * 0.4f, h * 0.55f), Size(w * 0.2f, h * 0.25f), CornerRadius(w * 0.03f))
    // The arm leans to one side on even beats and the other on odd ones, passing the middle on the click.
    val side = if (Math.floorMod(beat, 2) == 0) 1f else -1f
    val angle = if (still) 0f else side * 26f * (1f - pulse)
    rotate(angle, Offset(w / 2, h * 0.8f)) {
        drawLine(InkDim, Offset(w / 2, h * 0.8f), Offset(w / 2, h * 0.16f), w * 0.03f, cap = StrokeCap.Round)
        drawRoundRect(PupGold, Offset(w * 0.45f, h * 0.3f), Size(w * 0.1f, h * 0.07f), CornerRadius(w * 0.02f))
    }
    if (pulse > 0.05f) drawCircle(PupGold.copy(alpha = 0.5f * pulse), w * 0.06f * (1f + pulse), Offset(w / 2, h * 0.16f))
}
