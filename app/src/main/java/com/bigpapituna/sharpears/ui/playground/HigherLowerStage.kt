package com.bigpapituna.sharpears.ui.playground

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.HigherLowerJudge
import com.bigpapituna.sharpears.engine.playground.MissWhy
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.engine.playground.Way
import com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove
import com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bigpapituna.sharpears.ui.pup.Pose

/**
 * 2.3 part B, higher or lower (the night sky's "Up or down"): the pup sits on a star at the height of his note, plays
 * it and asks for a higher one or a lower one ("Play a higher one!"); the first round he shows one himself. Any key the
 * right way finds the round and the pup flies up or floats down to it; his own note ("That's my note! Go higher.") or the
 * other way ("That one went down. Try higher!") is a try; three tries and it goes by with a small star. The side to
 * play glows on the keys. Heard through the note matcher on twelve keys around the pup's note (strict keys), a key
 * outside them by its wrong-key search and its height.
 */
@Composable
internal fun HigherLowerStage(game: StageGame.HigherLower, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                              modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val rounds = game.rounds
    val low = remember(rounds) { whiteAtOrBelow(rounds.minOf { it.ref } - 10) }
    val high = remember(rounds) { whiteAtOrAbove(rounds.maxOf { it.ref } + 10) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val height = remember { Animatable(0.5f) }
    var cheer by remember { mutableIntStateOf(0) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    fun roundOf(run: StageRun) = rounds[run.done.coerceIn(0, rounds.lastIndex)]
    /** How high the pup sits for [key] against round [r]'s note: the middle is his note, an octave either way the edges. */
    fun heightOf(key: Int, ref: Int) = (0.5f + (key - ref) / 26f).coerceIn(0.06f, 0.94f)
    fun fly(to: Float) = scope.launch { if (still) height.snapTo(to) else height.animateTo(to, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    val st = rememberStage(ex, "$lessonId-updown", lessonId, "higher_lower", deps, inject, onEnded,
        keys = { run -> roundOf(run).keys },
        // The first round the pup plays his note and then one the right way; after that only his note.
        demoKeys = { run -> roundOf(run).let { r -> if (run.done == 0) listOf(r.ref, r.demo) else listOf(r.ref) } },
        judge = { s, run -> HigherLowerJudge.judge(s, roundOf(run)).also { a ->
            when (a) {
                is Answer.Found -> fly(heightOf(a.key, roundOf(run).ref))
                is Answer.Miss -> a.key?.let { fly(heightOf(it, roundOf(run).ref)) }
                else -> {}
            }
        } },
        foundLine = { run, _ -> if (run.finished) TreeSkyLines.UP_AND_DOWN else TreeSkyLines.went(rounds[(run.done - 1).coerceAtLeast(0)].way) },
        missLine = { a, run -> when (a.why) {
            MissWhy.MIDDLE -> TreeSkyLines.same(roundOf(run).way)
            else -> TreeSkyLines.otherWay(roundOf(run).way)
        } },
        velocity = { 84 },
        onFound = { cheer++ },
        onMiss = { tiltSeq++ },
        coach = { RoundCoach(firstDemoMs = 4_800, demoMs = 2_900, againDemoMs = 2_400) },
        prompt = { run -> TreeSkyLines.want(roundOf(run).way) },
        demoGapMs = 700,
    )
    val run = st.run
    val v = run.version
    val round = remember(v) { roundOf(run) }
    // Each round starts with the pup back on his own note's star.
    LaunchedEffect(run.done) { delay(1_300); fly(0.5f) }
    LaunchedEffect(st.demoKeys) { if (st.demoKeys.size == 2) { delay(700); fly(heightOf(round.demo, round.ref)) } }
    var tiltNow by remember { mutableStateOf(false) }
    LaunchedEffect(tiltSeq) { if (tiltSeq > 0) { tiltNow = true; delay(1300); tiltNow = false } }
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> TreeSkyLines.UP_AND_DOWN
        st.demoKeys.isNotEmpty() -> "Listen!"
        else -> TreeSkyLines.want(round.way)
    })
    val up = round.way == Way.HIGHER

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        SkyBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo) { StageLine(line) }
            // The sky the pup moves in: his note's star in the middle, an arrow the way to go.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = if (up) "Play a key higher than the pup's" else "Play a key lower than the pup's" }) {
                val skyH = maxHeight
                val pupSize = (skyH * 0.32f).coerceIn(56.dp, 120.dp)
                Canvas(Modifier.fillMaxSize()) {
                    val mid = Offset(size.width * 0.3f, size.height * 0.5f)
                    drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.4f), Color.Transparent), mid, size.height * 0.2f), size.height * 0.2f, mid)
                    drawPath(star(mid + Offset(0f, pupSize.toPx() * 0.42f), size.height * 0.07f), PupGold)
                    // The arrow, the way to go.
                    val x = size.width * 0.72f
                    val from = Offset(x, size.height * 0.5f)
                    val to = Offset(x, size.height * (if (up) 0.1f else 0.9f))
                    drawLine(LeftHandColor.copy(alpha = 0.8f), from, to, 6.dp.toPx())
                    val s = 16.dp.toPx() * (if (up) 1f else -1f)
                    drawLine(LeftHandColor.copy(alpha = 0.8f), to, to + Offset(-s * 0.9f, s), 6.dp.toPx())
                    drawLine(LeftHandColor.copy(alpha = 0.8f), to, to + Offset(s * 0.9f, s), 6.dp.toPx())
                    drawCircle(Color.White.copy(alpha = 0.3f), 8.dp.toPx(), from, style = Stroke(2.dp.toPx()))
                }
                val y = with(androidx.compose.ui.platform.LocalDensity.current) { ((1f - height.value) * (skyH - pupSize).toPx()).roundToInt() }
                StagePup(when {
                    finished -> Pose.GREAT_JOB
                    tiltNow -> Pose.HEAD_TILT
                    height.value > 0.56f -> Pose.FLYING
                    else -> Pose.STANDING
                }, Modifier.offset { IntOffset((maxWidth * 0.3f - pupSize / 2).roundToPx(), y) }, size = pupSize, lift = { (height.value - 0.5f).coerceAtLeast(0f) * 2 },
                    fx = st.fx)
            }
            Spacer(Modifier.height(6.dp))
            BigKeys(low, high, "Piano keys from ${noteName(low)} to ${noteName(high)}. The pup's note is ${noteName(round.ref)}.",
                Modifier.fillMaxWidth().height((roomH * 0.3f).coerceIn(110.dp, 190.dp)),
                fill = { m -> when {
                    m in st.demoKeys -> PupGold
                    m == round.ref -> PupGold.copy(alpha = 0.55f)
                    (m > round.ref) == up -> LeftHandColor.copy(alpha = 0.22f)
                    else -> null
                } },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                    st.tap(m)
                })
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}
