package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.playground.HeardBy
import com.bigpapituna.sharpears.engine.playground.JAM_KEYS
import com.bigpapituna.sharpears.engine.playground.JamStar
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.StarJam
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class JamPhase { READY, DEMO, JAM, END }

/**
 * 2.3 part B, make up a tune on the black keys (the night sky's "Black key tunes", [StarJam]): the pup plays a few
 * black keys first (each draws a star), then the band plays along in the black keys' own key, and every black key you
 * play draws a star across the sky (higher keys higher, left to right as the band goes on), joined into your tune's
 * constellation. Any black key counts; a white key is answered on the keys and the pup points at the black ones, once.
 * No grading beyond "you played". A quiet stretch: the pup plays a few notes to start you off; three end the jam
 * early. The microphone is deaf for a moment at each of the band's notes (they must not be taken for yours).
 */
@Composable
internal fun StarJamStage(game: StageGame.Jam, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                          modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val jam = remember(game) { StarJam(game.bars, ex.bpm) }
    val beatMs = jam.beatMs
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val ended by rememberUpdatedState(onEnded)
    val fx = remember { StageFx() }
    val mic = deps.mic
    val voice = deps.voice
    var phase by remember { mutableStateOf(JamPhase.READY) }
    val stars = remember { mutableStateListOf<JamStar>() }
    val demoStars = remember { mutableStateListOf<JamStar>() }
    var startAt by remember { mutableStateOf(0L) }
    var beat by remember { mutableIntStateOf(-1) }
    var saidBlack by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    var line by remember { mutableStateOf<String?>(null) }
    var lineSeq by remember { mutableIntStateOf(0) }
    /** The band's notes and when each sounded (uptime): a named key the band just played is the band's. */
    val bandAt = remember { HashMap<Int, Long>() }
    fun say(text: String) { line = text; lineSeq++; voice.sayOnStage(text, mic) }
    fun play(keys: List<Int>, velocity: Int, ms: Long) {
        if (phase == JamPhase.JAM) mic.deafFor(StarJam.DEAF_MS)
        if (!deps.prefs.sound) return
        keys.forEach { deps.sampler.noteOn(it, velocity) }
        scope.launch { delay(ms); keys.forEach { deps.sampler.noteOff(it) } }
    }

    /** The pup's few notes: each draws a star of his own (in the demo) and sounds, the mic deaf for them. */
    suspend fun nudge(own: Boolean) {
        mic.deafFor(StarJam.NUDGE.size * 380L + 600)
        for ((i, k) in StarJam.NUDGE.withIndex()) {
            if (deps.prefs.sound) { deps.sampler.noteOn(k, 82); scope.launch { delay(340); deps.sampler.noteOff(k) } }
            fx.ripple(k, PupGold, fill = true)
            if (own) demoStars += JamStar(k, 0.08f + 0.06f * i, ((k - 54) / 34f).coerceIn(0.05f, 0.95f))
            delay(380)
        }
    }

    // The pup's tune first, then the band.
    val speaking = voice.speaking.value
    var begin by remember { mutableStateOf(false) }
    LaunchedEffect(speaking, deps.ready) {
        if (begin || phase != JamPhase.READY || speaking || !deps.ready) return@LaunchedEffect
        delay(500)
        begin = true
    }
    // Its own effect: the pup speaking inside it must not restart it.
    LaunchedEffect(begin) {
        if (!begin) return@LaunchedEffect
        phase = JamPhase.DEMO
        say(PlaygroundLines.LET_ME_SHOW)
        delay(1_300)
        nudge(own = true)
        delay(500)
        say(TreeSkyLines.JAM_GO)
        delay(1_800)
        demoStars.clear()
        phase = JamPhase.JAM
    }
    // The band, from the jam's start to its end, a beat at a time; the jam's clock with it.
    LaunchedEffect(phase == JamPhase.JAM) {
        if (phase != JamPhase.JAM) return@LaunchedEffect
        jam.start()
        val t0 = withFrameNanos { it }
        startAt = SystemClock.uptimeMillis()
        Log.d(TAG, "playground: jam with the band, ${game.bars} bars at ${ex.bpm.toInt()}")
        var b = 0
        while (phase == JamPhase.JAM) {
            val now = withFrameNanos { it }
            val ms = (now - t0) / 1e6
            if (ms >= b * beatMs && b < game.bars * 4) {
                val (_, keys) = StarJam.BAND_BAR[b % 4]
                play(keys, if (keys.size == 1) 64 else 46, (beatMs * 0.9).toLong())
                keys.forEach { bandAt[it] = SystemClock.uptimeMillis() }
                beat = b
                if (!still) scope.launch { bounce.snapTo(1f); bounce.animateTo(0f, tween((beatMs * 0.6).toInt())) }
                b++
            }
            when (jam.tick(ms)) {
                StarJam.Tick.Nudge -> { say(TreeSkyLines.JAM_NUDGE); scope.launch { delay(900); nudge(own = false) } }
                StarJam.Tick.LetGo -> { say(PlaygroundLines.LET_GO); phase = JamPhase.END }
                StarJam.Tick.End -> phase = JamPhase.END
                null -> {}
            }
        }
    }
    LaunchedEffect(phase == JamPhase.END) {
        if (phase != JamPhase.END) return@LaunchedEffect
        Log.d(TAG, "playground: jam ended, ${jam.stars.size} stars, ${jam.whites} white keys, ${jam.tries} quiet tries")
        if (jam.stars.isNotEmpty()) {
            mic.deafFor(1_000); WorldSounds.ding()
            say(TreeSkyLines.JAM_END)
            if (still) glow.snapTo(1f) else glow.animateTo(1f, tween(1_200))
            delay(2_400)
        } else delay(1_800)
        ended(jam.report())
    }
    val listening = rememberStageEar(mic, voice, phase == JamPhase.JAM, inject, JAM_KEYS) { s ->
        fx.heard(s)
        if (!s.final || (s.by != HeardBy.MATCHER && s.by != HeardBy.NAMED)) return@rememberStageEar
        val k = s.midi ?: return@rememberStageEar
        // A named key the band itself just played is the band's.
        if (s.by == HeardBy.NAMED && bandAt[k]?.let { SystemClock.uptimeMillis() - it < 900 } == true) return@rememberStageEar
        val star = jam.heard(k, (SystemClock.uptimeMillis() - startAt).toDouble())
        if (star != null) { stars += star; fx.found(k) }
        else {
            fx.wrong(k, JAM_KEYS.minByOrNull { kotlin.math.abs(it - k) })
            if (!saidBlack) { saidBlack = true; say(TreeSkyLines.JAM_BLACK) }
        }
    }
    val shown = rememberFleeting(lineSeq, line, ms = (2_600 * FxSlow.factor).toLong()) ?: when (phase) {
        JamPhase.JAM -> if (stars.isEmpty()) "Any black key!" else null
        else -> null
    }
    val colours = colourNotesOn()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        SkyBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            // The sky the tune is drawn in.
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "${stars.size} stars drawn" }) {
                val all = demoStars + stars
                val pts = all.map { Offset(size.width * (0.04f + 0.92f * it.x), size.height * (0.92f - 0.84f * it.y)) }
                val lineInk = Color.White.copy(alpha = 0.35f + 0.45f * glow.value)
                pts.zipWithNext { a, b -> drawLine(lineInk, a, b, (1.5f + 1.5f * glow.value) * density, cap = StrokeCap.Round) }
                all.forEachIndexed { i, s ->
                    val c = pts[i]
                    val colour = if (colours) noteColour(s.midi) else PupGold
                    val r = (7f + 5f * glow.value) * density
                    drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.6f), Color.Transparent), c, r * 3), r * 3, c)
                    drawPath(star(c, r, inner = 0.45f), colour)
                    drawPath(star(c, r, inner = 0.45f), Color.White.copy(alpha = 0.8f), style = Stroke(1f * density))
                }
                // Where the band is: a faint line moving across the sky.
                if (phase == JamPhase.JAM && beat >= 0) {
                    val x = size.width * (0.04f + 0.92f * ((beat + 1f) / (game.bars * 4f)).coerceIn(0f, 1f))
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(x, size.height * 0.05f), Offset(x, size.height * 0.95f), 2f * density)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when (phase) { JamPhase.END -> Painted.DANCING; JamPhase.DEMO -> Painted.POINTING; else -> if (stars.isEmpty()) Painted.LISTENING else Painted.DANCING },
                    size = (roomH * 0.17f).coerceIn(56.dp, 100.dp), squash = { bounce.value }, poke = fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(shown)
            }
            BigKeys(60, 83, "Piano keys from C4 to B5. Play any black keys.", Modifier.fillMaxWidth().height((roomH * 0.27f).coerceIn(100.dp, 180.dp)),
                fill = { m -> if (isBlack(m)) PupGold.copy(alpha = 0.18f + 0.25f * bounce.value) else null }, fx = fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!listening || mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(500); deps.sampler.noteOff(m) } }
                    if (phase == JamPhase.JAM) {
                        fx.heard(com.bigpapituna.sharpears.engine.playground.StageSound(SystemClock.uptimeMillis(), HeardBy.MATCHER, m, final = true))
                        jam.heard(m, (SystemClock.uptimeMillis() - startAt).toDouble())?.let { stars += it; fx.found(m) }
                    }
                })
        }
    }
}
