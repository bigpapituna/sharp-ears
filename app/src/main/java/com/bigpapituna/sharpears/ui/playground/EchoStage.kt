package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.Place
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.pageOf
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.skin.colourNotesOn
import com.bigpapituna.sharpears.ui.skin.noteColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far apart the pup plays a tune's notes when he shows it. */
private const val TUNE_GAP_MS = 450L

/**
 * 2.2, copy the pup (the pond's lesson 3, the concert hall's lesson 9): each round is a little tune. The pup plays it
 * (a frog on a lily pad sings each note at the pond, a spotlight shines for each in the hall, in the note's colour, and
 * the key rings gold), then hears it back note by note through the note matcher (the C position, strict keys). Each
 * note played lights its frog or light again; a key that is not the next note makes the pup tilt his head and ask
 * "Which note comes next?"; a quiet stretch plays the tune again (the kid goes on from where they were); three tries
 * and the rest of the tune goes by with a small star. The keys can be tapped too.
 */
@Composable
internal fun EchoStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val pond = pageOf(lessonId)?.place != Place.CONCERT_HALL
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    var tiltSeq by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableIntStateOf(0) }
    // The tune just copied, shown whole and happy until the next one begins.
    var copied by remember { mutableStateOf<List<Int>?>(null) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    val longest = remember(ex) { parseTokens(ex.right).fold(listOf(0)) { acc, t -> if (t.rest) acc + 0 else acc.dropLast(1) + (acc.last() + 1) }.max() }
    val demoMs = longest * TUNE_GAP_MS + 900
    val st = rememberStage(ex, "$lessonId-echo", lessonId, "echo", deps, inject, onEnded,
        keys = { StageKeys.FIVE },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> when {
            run.finished -> PageLines.ALL_COPIED
            run.rounds.doneInRound == 0 -> (if (pond) PageLines.POND_COPIED else PageLines.HALL_COPIED)[(run.done - 1).coerceAtLeast(0) % 3]
            else -> null
        } },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        onFound = { _ ->
            // Before the round moves on: is this the tune's last note?
            val r = runRef[0]
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                copied = r.rounds.demoKeys(); cheer++
                Log.d(TAG, "playground: tune ${r.done + 1} copied: ${copied!!.map(::noteName)}")
            }
        },
        onMiss = { tiltSeq++ },
        coach = { RoundCoach(firstDemoMs = 1_300 + demoMs, demoMs = demoMs, againDemoMs = 1_000 + demoMs) },
        phrases = true, demoGapMs = TUNE_GAP_MS,
    )
    runRef[0] = st.run
    val run = st.run
    val demo = st.demoKeys
    LaunchedEffect(demo) { if (demo.isNotEmpty()) copied = null }
    LaunchedEffect(cheer) { if (cheer > 0) { delay(2_200); copied = null } }
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    val v = run.version
    val tune = remember(v, demo, copied) { copied ?: if (demo.isNotEmpty()) demo else run.rounds.demoKeys() }
    val answered = remember(v, copied) { if (copied != null) tune.size else run.rounds.doneInRound }
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> PageLines.ALL_COPIED
        demo.isNotEmpty() -> "Listen!"
        run.phase == RoundCoach.Phase.LISTEN -> PlaygroundLines.YOUR_TURN
        else -> null
    })
    val clock = rememberInfiniteTransition(label = "echo")
    val drift by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "drift")
    val pulse by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { if (pond) pondWater(if (still) 0.3f else drift) else hallStage(0.66f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            // The pup and what he says, then the tune's singers across the stage, then the keys.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when {
                    finished || copied != null -> Painted.GREAT_JOB
                    tiltNow -> Painted.HEAD_TILT
                    demo.isNotEmpty() -> Painted.POINTING
                    else -> Painted.LISTENING
                }, size = (roomH * 0.2f).coerceIn(64.dp, 120.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                key(tune) {
                    Singers(tune, playing = if (demo.isNotEmpty()) st.demoIndex else null, answered = answered, pond = pond,
                        waiting = run.phase == RoundCoach.Phase.LISTEN && copied == null, pulse = if (still) 0.5f else pulse,
                        modifier = Modifier.fillMaxWidth().heightIn(max = (roomH * 0.32f).coerceAtLeast(72.dp)))
                }
            }
            BigKeys(57, 71, "Piano keys from A3 to B4. The tune uses C, D, E, F and G.",
                Modifier.fillMaxWidth().height((roomH * 0.28f).coerceIn(104.dp, 180.dp)),
                fill = { m -> if (m in demo && st.demoIndex?.let { demo.getOrNull(it) } == m) PupGold else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/**
 * The tune's singers, one per note: frogs on lily pads at the pond, spotlights in the hall. The note [playing] in the
 * demo sings (bounces, glows in its colour); the first [answered] notes are lit (played back); while [waiting], the
 * next one breathes.
 */
@Composable
private fun Singers(tune: List<Int>, playing: Int?, answered: Int, pond: Boolean, waiting: Boolean, pulse: Float, modifier: Modifier = Modifier) {
    val colours = colourNotesOn()
    Row(modifier.semantics { contentDescription = "${tune.size} notes, $answered played back" }, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom) {
        tune.forEachIndexed { i, midi ->
            val colour = if (colours) noteColour(midi) else PupGold
            val lit = i == playing || i < answered
            val next = waiting && i == answered
            val hop = remember { Animatable(0f) }
            LaunchedEffect(i == playing, i < answered) {
                if (lit) { hop.snapTo(1f); hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
            }
            Box(Modifier.weight(1f).widthIn(max = 112.dp).aspectRatio(0.9f), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height * (if (pond) 0.62f else 0.2f))
                    if (lit) drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = 0.75f), Color.Transparent), c, size.width * 0.62f), size.width * 0.62f, c)
                    if (next) drawCircle(Color.White.copy(alpha = 0.25f + 0.35f * pulse), size.width * 0.44f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    if (pond) lilyPad(Offset(size.width / 2, size.height * 0.88f), size.width * 0.48f, tilt = (i % 2) * 6f - 3f)
                    else spotlight(Offset(size.width / 2, size.height * 0.18f), size.width * 0.26f, colour, if (lit) 1f else 0.08f, size.height * 0.98f)
                }
                if (pond) Image(painterResource(R.drawable.sticker_pond_1), contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f).padding(bottom = 4.dp).graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        val s = 1f + 0.18f * hop.value
                        scaleX = s; scaleY = s
                        alpha = if (lit || next) 1f else 0.55f
                    })
            }
        }
    }
}
