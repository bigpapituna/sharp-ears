package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.Pace
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TempoTune
import com.bigpapituna.sharpears.engine.playground.TempoVerdict
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bigpapituna.sharpears.ui.pup.Pose

/** How far apart the pup plays the tune's notes for a round that wants [pace]. */
private fun gapFor(pace: Pace?) = when (pace) { Pace.FAST -> 240L; Pace.SLOW -> 600L; else -> 420L }

private fun ask(pace: Pace?) = when (pace) { Pace.FAST -> TreeSkyLines.NOW_FAST; Pace.SLOW -> TreeSkyLines.NOW_SLOW; else -> TreeSkyLines.USUAL }

/** What the pup says about a phrase of the fast and slow game, from its [v]erdict. */
internal fun tempoLine(v: TempoVerdict?): String? = when {
    v == null -> null
    v.target == null -> TreeSkyLines.HEARD_USUAL
    v.target == Pace.FAST -> if (v.heard == Pace.FAST) TreeSkyLines.FAST_YES else TreeSkyLines.FAST_NOT
    else -> if (v.heard == Pace.SLOW) TreeSkyLines.SLOW_YES else TreeSkyLines.SLOW_NOT
}

/**
 * 2.3 part B, fast and slow (the night sky's "Fast or slow"): up from C to G your usual way (it sets your pace), then
 * fast like a rabbit, then slow like a turtle ([TempoTune]). The pup plays each round at the pace it wants; as you play,
 * the rabbit or the turtle hops along a path of stars, a step a note; when the phrase is done the pup says how it went.
 * Heard note by note through the note matcher (the C position, strict keys); a key that is not the next note is a try,
 * three let the rest of the phrase go. A toy: it never holds the lesson up.
 */
@Composable
internal fun TempoStage(game: StageGame.Tempo, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                        modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val tempo = remember(game) { TempoTune(game.rounds) }
    val keys = remember(ex) { com.bigpapituna.sharpears.engine.parseTokens(ex.right).flatMap { it.keys }.toSet() }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val hop = remember { Animatable(0f) }
    var cheer by remember { mutableIntStateOf(0) }
    var steps by remember { mutableIntStateOf(0) }
    val verdictLine = remember { arrayOfNulls<String>(1) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    // The demo plays at the round's pace: the longest is the slow one.
    val slowDemo = 1_300 + 5 * gapFor(Pace.SLOW) + 650
    val st = rememberStage(ex, "$lessonId-tempo", lessonId, "tempo", deps, inject, onEnded,
        keys = { keys },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()).also { a -> if (a is Answer.Found) { tempo.note(SystemClock.uptimeMillis()); steps++ } } },
        foundLine = { _, _ -> verdictLine[0].also { verdictLine[0] = null } },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        onFound = { _ ->
            val r = runRef[0]
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                val round = r.done
                val v = tempo.phraseDone(round)
                Log.d(TAG, "playground: tempo round ${round + 1} -> $v (usual ${tempo.anchorMs})")
                if (v?.match == true && v.target != null) cheer++
                verdictLine[0] = if (round + 1 >= game.rounds.size) TreeSkyLines.FAST_AND_SLOW else tempoLine(v)
            }
        },
        onLetGo = { tempo.skipped() },
        coach = { RoundCoach(firstDemoMs = 1_300 + 1_300 + 5 * gapFor(null) + 650, demoMs = slowDemo, againDemoMs = 1_000 + 5 * gapFor(Pace.SLOW) + 650) },
        prompt = { run -> ask(game.rounds.getOrNull(run.done)) },
        phrases = true,
        demoGapMs = gapFor(game.rounds.getOrNull(runRef[0]?.done ?: 0)),
    )
    runRef[0] = st.run
    val run = st.run
    val v = run.version
    val round = remember(v) { run.done }
    val target = game.rounds.getOrNull(round)
    LaunchedEffect(round) { steps = 0 }
    LaunchedEffect(steps) { if (steps > 0 && !still) { hop.snapTo(1f); hop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 500f)) } }
    // The demo walks the animal along too, a step a note; then it goes back to the start for you.
    LaunchedEffect(st.demoIndex) { st.demoIndex?.let { steps = it + 1 } }
    LaunchedEffect(st.demoKeys) { if (st.demoKeys.isEmpty()) steps = run.rounds.doneInRound }
    val line = stageLine(st, when {
        run.finished -> TreeSkyLines.FAST_AND_SLOW
        st.demoKeys.isNotEmpty() -> "Listen!"
        else -> ask(target)
    })

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        SkyBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo) { StageLine(line) }
            // The path: five stars, the rabbit (fast), the turtle (slow) or the pup (your usual way) hopping along it.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "A path of five stars" }) {
                val w = maxWidth; val h = maxHeight
                val animal = (h * 0.42f).coerceIn(56.dp, 130.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height * 0.78f
                    drawLine(Ink.copy(alpha = 0.3f), Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    for (k in 0 until 5) {
                        val c = Offset(size.width * (0.14f + 0.18f * k), y)
                        drawPath(star(c, 12.dp.toPx()), if (k < steps) PupGold else Color.White.copy(alpha = 0.25f))
                        drawPath(star(c, 12.dp.toPx()), Color.White.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
                    }
                }
                val x = w * (0.14f + 0.18f * (steps - 1).coerceIn(0, 4)) - animal / 2
                Box(Modifier.offset { IntOffset(x.roundToPx(), (h * 0.78f - animal).roundToPx() - (hop.value * 18.dp.toPx()).roundToInt()) }.size(animal)) {
                    when (target) {
                        Pace.FAST -> Canvas(Modifier.fillMaxSize()) { rabbit(Ink) }
                        Pace.SLOW -> Canvas(Modifier.fillMaxSize()) { turtle(Ink) }
                        else -> StagePup(if (run.finished) Pose.GREAT_JOB else Pose.STANDING, size = animal, fx = st.fx)
                    }
                }
            }
            BigKeys(57, 71, "Piano keys from A3 to B4. Play C, D, E, F and G.", Modifier.fillMaxWidth().height((roomH * 0.3f).coerceIn(110.dp, 190.dp)),
                fill = { m -> if (st.demoIndex?.let { st.demoKeys.getOrNull(it) } == m) PupGold else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(500); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}
