package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.Loudness
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.TuneLoudness
import com.bigpapituna.sharpears.engine.playground.TuneVerdict
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose

/** How far apart the pup plays the tune's notes when he shows it. */
private const val TUNE_NOTE_MS = 420L

/** What the pup says about a phrase, from its [v]erdict (null: nothing heard to grade). */
internal fun tuneLine(round: Int, v: TuneVerdict?): String? = when {
    v == null -> null
    round == 0 || v.target == null -> PageLines.HEARD_USUAL
    v.target == Loudness.LOUD -> if (v.heard == Loudness.LOUD) PageLines.HEARD_LOUD else PageLines.HEARD_NOT_LOUD
    v.heard == Loudness.SOFT -> PageLines.HEARD_SOFT
    v.heard == Loudness.LOUD -> PageLines.HEARD_LOUD_IN_SOFT
    else -> PageLines.HEARD_IN_BETWEEN
}

/** What each round asks for, as the pup says it before showing it. */
private fun tunePrompt(round: Int): String? = listOf(PageLines.TUNE_USUAL, PageLines.TUNE_SOFT, PageLines.TUNE_LOUD, PageLines.TUNE_SOFT_AGAIN).getOrNull(round)

/**
 * 2.2, soft and loud for real (the concert hall's lesson 6): the pup naps on his cushion under the moon while the kid
 * plays Ode to Joy's first phrase, once the usual way (that sets the level), then softly, loudly and softly again. Each
 * phrase is shown first, the pup playing it at the loudness the round wants, then heard note by note (the C position,
 * strict keys: a key that is not the next note is a try). When it is done its notes' middle rise is graded
 * ([TuneLoudness]): soft keeps him asleep, in between makes him stir, loud wakes him with a jump. A meter shows the guess;
 * like the garden's nap it is a toy and says so. Keys tapped on screen count at the loudness the round asks for.
 */
@Composable
internal fun TuneStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val offset = LocalProfile.current.micOffsetDb
    val tune = remember(ex) { TuneLoudness(offsetDb = offset) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    var nap by remember { mutableStateOf(Nap.ASLEEP) }
    val jump = remember { Animatable(0f) }
    var lastLevel by remember { mutableStateOf<Double?>(null) }
    var tiltSeq by remember { mutableIntStateOf(0) }
    var cheer by remember { mutableIntStateOf(0) }
    val verdictLine = remember { arrayOfNulls<String>(1) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }

    fun react(v: TuneVerdict) {
        scope.launch {
            when {
                v.woke -> {
                    nap = Nap.AWAKE
                    if (!still) { jump.snapTo(0f); jump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 260f)) }
                    delay(1500); nap = Nap.YAWN; delay(1100)
                    if (!still) jump.animateTo(0f, tween(400))
                    nap = Nap.ASLEEP
                }
                v.target == Loudness.SOFT && v.heard == Loudness.MEDIUM -> { nap = Nap.STIR; delay(1200); nap = Nap.ASLEEP }
                else -> nap = Nap.ASLEEP
            }
        }
    }
    val demoMs = 8 * TUNE_NOTE_MS + 900
    val st = rememberStage(ex, "$lessonId-tune", lessonId, "soft_loud_tune", deps, inject, onEnded,
        keys = { StageKeys.FIVE },
        demoKeys = { run -> run.rounds.demoKeys() },
        // The pup plays the round the way it wants it: the usual way, softly or loudly.
        velocity = { run -> when (TuneLoudness.ROUNDS.getOrNull(run.done)) { Loudness.SOFT -> 30; Loudness.LOUD -> 118; else -> 72 } },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()).also { a -> if (a is Answer.Found) s.riseDb?.let(tune::note) } },
        foundLine = { _, _ -> verdictLine[0].also { verdictLine[0] = null } },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        onFound = { _ ->
            val r = runRef[0]
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                val round = r.done
                val v = tune.phraseDone(round)
                Log.d(TAG, "playground: tune phrase ${round + 1} -> $v (anchor ${tune.anchorDb})")
                v?.let { react(it); if (it.match) cheer++ }
                v?.let { lastLevel = it.levelDb }
                verdictLine[0] = tuneLine(round, v)
            }
        },
        onMiss = { tiltSeq++ },
        onLetGo = { tune.skipped() },
        coach = { RoundCoach(firstDemoMs = 2_600 + demoMs, demoMs = 1_300 + demoMs, againDemoMs = 1_000 + demoMs) },
        prompt = { run -> tunePrompt(run.done) },
        phrases = true, demoGapMs = TUNE_NOTE_MS,
    )
    runRef[0] = st.run
    val run = st.run
    val demo = st.demoKeys
    val v = run.version
    val round = remember(v) { run.done }
    val inRound = remember(v) { run.rounds.doneInRound }
    val notes = remember(v) { run.rounds.roundGroups.size.coerceAtLeast(1) }
    val finished = run.finished
    val line = stageLine(st, when {
        finished -> "Goodnight. That was lovely."
        demo.isNotEmpty() -> "Listen!"
        else -> tunePrompt(round)
    })
    val clock = rememberInfiniteTransition(label = "tune")
    val breathe by clock.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "breathe")
    val twinkle by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "twinkle")
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { night(if (still) 0.5f else twinkle) }
        val pupSize = (maxHeight * 0.28f).coerceIn(84.dp, 170.dp)
        val keysH = (maxHeight * 0.26f).coerceIn(96.dp, 170.dp)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            key(round) { RoundIcons(TuneLoudness.ROUNDS.drop(1).map { it ?: Loudness.MEDIUM }, round - 1) }
            StageLine(line, Modifier.padding(top = 8.dp))
            PhraseDots(notes, if (demo.isNotEmpty()) (st.demoIndex ?: -1) + 1 else inRound, demo.isNotEmpty(), Modifier.padding(top = 8.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxWidth().height(pupSize * 0.32f)) {
                    drawRoundRect(Cushion, Offset(size.width / 2 - pupSize.toPx() * 0.6f, size.height * 0.3f), Size(pupSize.toPx() * 1.2f, size.height * 0.6f),
                        CornerRadius(size.height * 0.3f))
                }
                val lift = jump.value
                StagePup(when (nap) { Nap.ASLEEP, Nap.STIR -> Pose.SLEEPING; Nap.AWAKE -> Pose.SURPRISED; Nap.YAWN -> Pose.GOODNIGHT },
                    Modifier.offset { IntOffset(if (nap == Nap.STIR && !still) (sin(breathe * 6) * 4).roundToInt() else 0,
                        -(lift * pupSize.toPx() * 0.35f).roundToInt() - (pupSize.toPx() * 0.12f).roundToInt()) },
                    size = pupSize, lift = { lift }, fx = st.fx)   // asleep, the rig's SLEEP breathes for him (2.10)
                if (nap == Nap.ASLEEP || nap == Nap.STIR) Canvas(Modifier.fillMaxSize()) { zs(if (still) 0.5f else (breathe / (2 * Math.PI)).toFloat(), nap == Nap.STIR, measurer) }
            }
            Meter(lastLevel, tune.anchorDb, offset, Modifier.fillMaxWidth().padding(vertical = 6.dp))
            BigKeys(57, 71, "Piano keys from A3 to B4. Ode to Joy uses C, D, E, F and G.",
                Modifier.fillMaxWidth().height(keysH),
                fill = { m -> if (st.demoIndex?.let { demo.getOrNull(it) } == m) PupGold else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    val want = TuneLoudness.ROUNDS.getOrNull(run.done)
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) {
                        deps.sampler.noteOn(m, when (want) { Loudness.SOFT -> 30; Loudness.LOUD -> 118; else -> 72 }); scope.launch { delay(600); deps.sampler.noteOff(m) }
                    }
                    // A tap is as soft or as loud as the round asks (the screen cannot tell).
                    val base = tune.anchorDb ?: -32.0
                    st.tap(m, riseDb = when (want) { Loudness.SOFT -> base - 8; Loudness.LOUD -> base + 8; else -> base })
                })
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}

/** One dot per note of the phrase, the ones played (or, during the demo, sung) filled. */
@Composable
private fun PhraseDots(total: Int, done: Int, demo: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = (total * 18).dp, height = 12.dp).semantics { contentDescription = "$done of $total notes" }) {
        for (i in 0 until total) {
            val c = Offset(size.height / 2 + i * 18.dp.toPx(), size.height / 2)
            if (i < done) drawCircle(if (demo) PupGold else Ink, size.height * 0.42f, c)
            else drawCircle(InkDim.copy(alpha = 0.5f), size.height * 0.28f, c)
        }
    }
}
