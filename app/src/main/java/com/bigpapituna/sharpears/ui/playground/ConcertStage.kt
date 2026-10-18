package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.CONCERT_SONGS
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.playground.ConcertSong
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Accent
import com.bigpapituna.sharpears.ui.Embedded
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val Curtain = Color(0xFF8E2F2A)
private val CurtainDeep = Color(0xFF5C1C19)

/**
 * Lesson 12, "Pup's concert": the child picks one of the three songs ([CONCERT_SONGS]), the curtains open, the pup
 * plays its start first ("Let me show you", the embedded player in Listen mode, 2.1.1: every round shows itself), and
 * then they play it in time (Perform, right hand, the band on the left). A run at the pass mark brings fireworks and the
 * pup dancing before the lesson moves on; one under it goes back to the lesson's "Try again", which after three tries
 * lets the lesson go on ("Good try, let's keep going", StepRetry).
 */
@Composable
internal fun ConcertStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    var chosen by remember { mutableStateOf<ConcertSong?>(null) }
    var shown by remember { mutableStateOf(false) }                // the pup has played the song's start
    var bravo by remember { mutableStateOf<Report?>(null) }
    val ended by rememberUpdatedState(onEnded)
    val still = reducedMotion()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        val song = chosen
        if (song == null) SongPicker(Modifier.fillMaxSize()) { chosen = it; Log.d(TAG, "playground: concert song ${it.title}") }
        else Column(Modifier.fillMaxSize()) {
            val open = remember(song) { Animatable(if (still) 1f else 0f) }
            LaunchedEffect(song) { if (!still) open.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
            Box(Modifier.fillMaxWidth().height(44.dp)) {
                Canvas(Modifier.fillMaxSize()) { lights() }
                Text(song.title, Modifier.align(Alignment.Center), color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val ex = song.exercise
                if (!shown) {
                    // The demo: the song's first two bars, played by the app with the names showing.
                    LaunchedEffect(song) { deps.voice.speak(PlaygroundLines.LET_ME_SHOW) }
                    PlayerScreen(Bites.cut(ex, 2.0 * ex.beats).toSong("$lessonId-concert-demo-${song.picture}", song.title), null, deps.sampler, deps.midi,
                        deps.mic, deps.voice, deps.prefs, deps.ready, 0.0, false,
                        PlayerStart(Mode.LISTEN, Practice.BOTH, mic = false),
                        onBack = {},
                        onFinished = {
                            Log.d(TAG, "playground: concert demo of ${song.title} done")
                            deps.voice.speak(PlaygroundLines.YOUR_TURN)
                            shown = true
                        },
                        embedded = Embedded("Listen first"),
                        modifier = Modifier.fillMaxSize())
                } else {
                    PlayerScreen(ex.toSong("$lessonId-concert-${song.picture}", song.title), null, deps.sampler, deps.midi, deps.mic, deps.voice, deps.prefs, deps.ready,
                        0.0, false,
                        PlayerStart(Mode.PERFORM, Practice.RIGHT, mic = deps.prefs.micDefault && deps.mic.hasPermission(), inject = inject),
                        onBack = {},
                        onFinished = { r ->
                            val passed = step.copy(exercise = ex).passed(r)
                            Log.d(TAG, "playground: concert ${song.title} ${r.recap?.accuracy ?: 0} of ${ex.pass} needed, passed=$passed")
                            if (passed) bravo = r else ended(r)
                        },
                        embedded = Embedded("With the band"),
                        modifier = Modifier.fillMaxSize())
                }
                // The curtains part as the song begins.
                if (open.value < 1f) Canvas(Modifier.fillMaxSize()) { curtains(open.value) }
            }
        }
        bravo?.let { r ->
            LaunchedEffect(r) { delay(3400); ended(r) }
            Box(Modifier.fillMaxSize().background(Color(0xCC110F0D)), contentAlignment = Alignment.Center) {
                Fireworks(Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PaintedPup(Painted.DANCING, size = (roomH * 0.4f).coerceIn(110.dp, 220.dp))
                    Text("Bravo!", color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                }
            }
        }
    }
}

/** The three songs as big cards, each with its picture. */
@Composable
private fun SongPicker(modifier: Modifier, onPick: (ConcertSong) -> Unit) {
    BoxWithConstraints(modifier.padding(12.dp)) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(Painted.CROWN, size = 72.dp)
                Spacer(Modifier.width(8.dp))
                StageLine("Which song tonight?")
            }
            Spacer(Modifier.height(10.dp))
            val cards: @Composable (Modifier) -> Unit = { m -> CONCERT_SONGS.forEach { s -> SongCard(s, m, stacked = wide) { onPick(s) } } }
            if (wide) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards(Modifier.weight(1f).fillMaxHeight()) }
            else Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { cards(Modifier.weight(1f).fillMaxWidth()) }
        }
    }
}

@Composable
private fun SongCard(song: ConcertSong, modifier: Modifier, stacked: Boolean, onTap: () -> Unit) {
    val card = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF241F1B)).clickable(role = Role.Button, onClick = onTap)
        .semantics { contentDescription = "Play ${song.title}" }.padding(12.dp)
    val picture: @Composable (Modifier) -> Unit = { m -> Canvas(m) { when (song.picture) { "buns" -> buns(); "lamb" -> lamb(); else -> fiveFingers() } } }
    val title: @Composable () -> Unit = {
        Text(song.title, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = if (stacked) TextAlign.Center else TextAlign.Start)
    }
    // Side by side (a wide screen), each card stands up: the picture over its title; stacked rows lie down.
    if (stacked) Column(card, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        picture(Modifier.fillMaxWidth(0.7f).heightIn(max = 140.dp).aspectRatio(1f))
        Spacer(Modifier.height(10.dp))
        title()
    } else Row(card, verticalAlignment = Alignment.CenterVertically) {
        picture(Modifier.fillMaxHeight(0.85f).heightIn(max = 120.dp).aspectRatio(1f))
        Spacer(Modifier.width(12.dp))
        title()
    }
}

/** Three golden buns with their crosses. */
private fun DrawScope.buns() {
    val r = size.minDimension * 0.22f
    for ((dx, dy) in listOf(-0.55f to 0.25f, 0.55f to 0.25f, 0f to -0.3f)) {
        val c = Offset(size.width / 2 + dx * r * 1.6f, size.height / 2 + dy * r * 1.6f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE9B36A), Color(0xFFB9783A)), c, r), r, c)
        drawLine(Color(0xFFFFF4E0), c - Offset(r * 0.6f, 0f), c + Offset(r * 0.6f, 0f), r * 0.18f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFFF4E0), c - Offset(0f, r * 0.6f), c + Offset(0f, r * 0.6f), r * 0.18f, cap = StrokeCap.Round)
    }
}

/** A woolly lamb: a cloud of a body, a dark face, little legs. */
private fun DrawScope.lamb() {
    val w = size.width; val h = size.height
    for (k in 0 until 3) drawLine(Color(0xFF3A2A20), Offset(w * (0.36f + k * 0.14f), h * 0.6f), Offset(w * (0.36f + k * 0.14f), h * 0.86f), w * 0.06f, cap = StrokeCap.Round)
    for ((x, y, r) in listOf(Triple(0.4f, 0.5f, 0.17f), Triple(0.55f, 0.45f, 0.18f), Triple(0.68f, 0.52f, 0.15f), Triple(0.5f, 0.6f, 0.16f), Triple(0.33f, 0.4f, 0.13f)))
        drawCircle(Color(0xFFF7F2EA), w * r, Offset(w * x, h * y))
    drawOval(Color(0xFF3A2A20), Offset(w * 0.12f, h * 0.3f), Size(w * 0.24f, h * 0.22f))
    drawCircle(Color.White, w * 0.025f, Offset(w * 0.2f, h * 0.38f))
}

/** A hand's five fingertips on five keys, each its own colour. */
private fun DrawScope.fiveFingers() {
    val w = size.width; val h = size.height
    val colors = listOf(Accent, PupGold, Good, LeftHandColor, Color(0xFFB98AD9))
    for (k in 0 until 5) {
        val x = w * (0.14f + k * 0.18f)
        drawRoundRect(Color(0xFFF7F2EA), Offset(x - w * 0.075f, h * 0.55f), Size(w * 0.15f, h * 0.4f), CornerRadius(w * 0.03f))
        drawCircle(colors[k], w * 0.07f, Offset(x, h * (0.3f + 0.08f * kotlin.math.abs(k - 2) / 2f)))
    }
}

/** Stage lights along the top: warm bulbs. */
private fun DrawScope.lights() {
    drawRect(Brush.verticalGradient(listOf(CurtainDeep, Color(0xFF241F1B))))
    val n = 9
    for (k in 0 until n) {
        val c = Offset(size.width * (k + 0.5f) / n, size.height * 0.2f)
        drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.6f), Color.Transparent), c, 14.dp.toPx()), 14.dp.toPx(), c)
        drawCircle(Color(0xFFFFE3A8), 3.dp.toPx(), c)
    }
}

/** Two red curtains, drawn apart by [open] (0 closed, 1 gone). */
private fun DrawScope.curtains(open: Float) {
    val half = size.width / 2 * (1f - open)
    for (side in listOf(0, 1)) {
        val left = if (side == 0) 0f else size.width - half
        drawRect(Brush.horizontalGradient(listOf(Curtain, CurtainDeep, Curtain, CurtainDeep), left, left + half), Offset(left, 0f), Size(half, size.height))
        val folds = 5
        for (f in 1 until folds) {
            val x = left + half * f / folds
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
}

/** Fireworks: bursts of sparks in the celebration colours, rising and falling; still stars with animations off. */
@Composable
private fun Fireworks(modifier: Modifier) {
    val still = reducedMotion()
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!still) t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    val bursts = remember {
        val r = java.util.Random(12)
        List(6) { Triple(0.15f + 0.7f * r.nextFloat(), 0.15f + 0.35f * r.nextFloat(), it * 0.12f) }
    }
    val colors = listOf(Accent, PupGold, Good, LeftHandColor, Color(0xFFF3EEE9))
    Canvas(modifier) {
        bursts.forEachIndexed { i, (bx, by, start) ->
            val p = if (still) 0.5f else ((t.value - start) / 0.55f).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEachIndexed
            val c = Offset(size.width * bx, size.height * by)
            val color = colors[i % colors.size]
            for (k in 0 until 18) {
                val a = 2 * Math.PI * k / 18
                val d = minOf(size.width, size.height) * 0.22f * p
                val pos = c + Offset((d * cos(a)).toFloat(), (d * sin(a)).toFloat() + size.height * 0.06f * p * p)
                drawCircle(color.copy(alpha = 1f - p), 3.dp.toPx() * (1.2f - 0.6f * p), pos)
            }
        }
        if (still) bursts.forEach { (bx, by, _) -> drawPath(star(Offset(size.width * bx, size.height * by), 10.dp.toPx()), PupGold) }
    }
}
