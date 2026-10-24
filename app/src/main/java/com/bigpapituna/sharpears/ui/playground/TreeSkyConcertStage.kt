package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.ConcertSong
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Embedded
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.Nunito
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.ui.pup.PupClips
import com.bigpapituna.sharpears.ui.pup.PupClip

/**
 * 2.3, the treehouse's and the night sky's concerts ([com.bigpapituna.sharpears.engine.playground.Game.CONCERT]; ConcertStage
 * hands them here with the step's [song] from concertPlan): the song in its [place] (the lanterns in the tree, the moon
 * and the stars). The pup plays its start first (the embedded player in Listen mode, "Let me show you"), then you play it
 * in time as written (Perform; the band plays the other hand, or with both hands, no band but the clicks). A run at the
 * pass mark ([com.bigpapituna.sharpears.engine.playground.CONCERT_PASS], two stars) brings the pup dancing, "Bravo!",
 * lanterns or shooting stars, before the lesson moves on; one under it goes back to the lesson's "Try again", which
 * lets the step go after three tries (StepRetry).
 */
@Composable
internal fun TreeSkyConcertStage(song: ConcertSong, place: Place, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                                 modifier: Modifier = Modifier) {
    val ex = song.exercise
    var shown by remember { mutableStateOf(false) }
    var bravo by remember { mutableStateOf<Report?>(null) }
    val ended by rememberUpdatedState(onEnded)
    val still = reducedMotion()
    val tree = place == Place.TREEHOUSE
    val hands = when (ex.practice) { Practice.BOTH -> "Both hands"; Practice.LEFT -> "Left hand, with the band"; Practice.RIGHT -> "With the band" }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Column(Modifier.fillMaxSize()) {
            // The place along the top: the treehouse's lanterns or the night sky, the song's title over it.
            Box(Modifier.fillMaxWidth().height(64.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
                PlaceBackdrop(place, Modifier.fillMaxSize(), floorFrom = 0.9f)
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).background(Color(0xB3110F0D), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(song.title, color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hands, color = PupGold, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!shown) {
                    // The demo: the song's first two bars, played by the app with the names showing.
                    LaunchedEffect(Unit) { deps.voice.speak(PlaygroundLines.LET_ME_SHOW) }
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
                        PlayerStart(Mode.PERFORM, ex.practice, mic = deps.prefs.micDefault && deps.mic.hasPermission(), inject = inject),
                        onBack = {},
                        onFinished = { r ->
                            val passed = step.copy(exercise = ex).passed(r)
                            Log.d(TAG, "playground: concert ${song.title} ${r.recap?.accuracy ?: 0} of ${ex.pass} needed, passed=$passed")
                            if (passed) bravo = r else ended(r)
                        },
                        embedded = Embedded(if (ex.practice == Practice.BOTH) "Both hands" else "With the band"),
                        modifier = Modifier.fillMaxSize())
                }
            }
        }
        bravo?.let { r ->
            LaunchedEffect(r) { deps.voice.speak(if (tree) TreeSkyLines.TREE_BRAVO else TreeSkyLines.SKY_BRAVO); delay(3_600); ended(r) }
            Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))) {
                PlaceBackdrop(place, Modifier.fillMaxSize(), floorFrom = 0.78f)
                Box(Modifier.fillMaxSize().background(Color(0x66110F0D)))
                ShootingStars(Modifier.fillMaxSize(), still, gold = tree)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    // The bow (2.10): the pup's wave clip when the app has it, else his jump, then a cheer.
                    PupClip(PupClips.WAVE, size = (roomH * 0.4f).coerceIn(110.dp, 220.dp), fallback = Pose.DANCING, then = PupMood.CHEER)
                    Text("Bravo!", color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                }
                Confetti(r, Modifier.fillMaxSize())
            }
        }
    }
}

/** Stars shooting across the sky (gold lanterns' sparks in the tree), for a bravo; a still sprinkle with animations off. */
@Composable
private fun ShootingStars(modifier: Modifier, still: Boolean, gold: Boolean) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (!still) t.animateTo(1f, tween(3_200, easing = LinearEasing)) }
    val streaks = remember { val r = java.util.Random(23); List(7) { Triple(r.nextFloat() * 0.6f, 0.05f + r.nextFloat() * 0.45f, it * 0.1f) } }
    Canvas(modifier) {
        val colour = if (gold) PupGold else Color.White
        streaks.forEach { (x0, y0, start) ->
            val p = if (still) 0.5f else ((t.value - start) / 0.45f).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEach
            val head = Offset(size.width * (x0 + 0.5f * p), size.height * (y0 + 0.25f * p))
            val tail = head - Offset(size.width * 0.14f, size.height * 0.07f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, colour.copy(alpha = 1f - p)), tail, head), tail, head, 3f * density, cap = StrokeCap.Round)
            drawPath(star(head, 6f * density), colour.copy(alpha = 1f - p))
        }
    }
}
