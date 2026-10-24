package com.bigpapituna.sharpears.ui.playground

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Answer
import com.bigpapituna.sharpears.engine.playground.Loudness
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageGame
import com.bigpapituna.sharpears.engine.playground.TreeSkyLines
import com.bigpapituna.sharpears.engine.playground.TuneLoudness
import com.bigpapituna.sharpears.engine.playground.TuneVerdict
import com.bigpapituna.sharpears.ui.Confetti
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.bigpapituna.sharpears.ui.pup.Pose

/** What the pup says about one note of the loud and quiet game, from its [v]erdict. */
internal fun loudQuietLine(v: TuneVerdict?): String? = when {
    v == null -> null
    v.target == null -> TreeSkyLines.HEARD_USUAL
    v.target == Loudness.LOUD -> if (v.heard == Loudness.LOUD) TreeSkyLines.LOUD_YES else TreeSkyLines.LOUD_NOT
    v.heard == Loudness.SOFT -> TreeSkyLines.QUIET_YES
    v.heard == Loudness.LOUD -> TreeSkyLines.QUIET_LOUD
    else -> TreeSkyLines.QUIET_IN_BETWEEN
}

private fun ask(target: Loudness?) = when (target) { null -> TreeSkyLines.USUAL; Loudness.LOUD -> TreeSkyLines.NOW_LOUD; else -> TreeSkyLines.NOW_QUIET }

/**
 * 2.3 part B, loud and quiet (the night sky's "Loud or quiet"): middle C your usual way first (it sets the level), then
 * loud like thunder and quiet like a mouse, a note a round ([TuneLoudness] over one-note phrases). The pup plays each
 * round the way it wants it; the big star in the sky bursts for a loud note and shrinks to a twinkle for a quiet one,
 * the meter shows the guess. Like the other loudness games a toy, and the lesson says so: it never holds the lesson up.
 */
@Composable
internal fun LoudQuietStage(game: StageGame.LoudQuiet, step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit,
                            modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val offset = LocalProfile.current.micOffsetDb
    val tune = remember(game) { TuneLoudness(game.rounds, offset) }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val glow = remember { Animatable(0.4f) }
    var lastLevel by remember { mutableStateOf<Double?>(null) }
    var lastHeard by remember { mutableStateOf<Loudness?>(null) }
    var cheer by remember { mutableIntStateOf(0) }
    val verdictLine = remember { arrayOfNulls<String>(1) }
    val runRef = remember { arrayOfNulls<StageRun>(1) }
    fun react(v: TuneVerdict) {
        lastHeard = v.heard
        val to = when (v.heard) { Loudness.LOUD -> 1f; Loudness.SOFT -> 0.12f; Loudness.MEDIUM -> 0.45f }
        scope.launch { if (still) glow.snapTo(to) else { glow.animateTo(to, spring(dampingRatio = 0.4f, stiffness = 260f)); delay(1_600); glow.animateTo(0.4f, tween(600)) } }
    }
    val st = rememberStage(ex, "$lessonId-loudquiet", lessonId, "loud_quiet", deps, inject, onEnded,
        keys = { setOf(60) },
        demoKeys = { run -> run.rounds.demoKeys() },
        velocity = { run -> when (game.rounds.getOrNull(run.done)) { Loudness.SOFT -> 28; Loudness.LOUD -> 118; else -> 72 } },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()).also { a -> if (a is Answer.Found) s.riseDb?.let(tune::note) } },
        foundLine = { _, _ -> verdictLine[0].also { verdictLine[0] = null } },
        missLine = { _, _ -> PageLines.where('C', true) },
        onFound = { _ ->
            val r = runRef[0]
            if (r != null) {
                val round = r.done
                val v = tune.phraseDone(round)
                Log.d(TAG, "playground: loud or quiet round ${round + 1} -> $v (anchor ${tune.anchorDb})")
                v?.let { react(it); lastLevel = it.levelDb; if (it.match && it.target != null) cheer++ }
                verdictLine[0] = if (r.done + 1 >= game.rounds.size) TreeSkyLines.LOUD_AND_QUIET else loudQuietLine(v)
            }
        },
        onLetGo = { tune.skipped() },
        coach = { RoundCoach(firstDemoMs = 1_300 + 1_300 + 1_300, demoMs = 1_300 + 1_300, againDemoMs = 2_300) },
        prompt = { run -> ask(game.rounds.getOrNull(run.done)) },
        phrases = true,
    )
    runRef[0] = st.run
    val run = st.run
    val v = run.version
    val round = remember(v) { run.done }
    val line = stageLine(st, when {
        run.finished -> TreeSkyLines.LOUD_AND_QUIET
        st.demoKeys.isNotEmpty() -> "Listen!"
        else -> ask(game.rounds.getOrNull(round))
    })

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        SkyBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            RoundIcons(game.rounds.drop(1).map { it ?: Loudness.MEDIUM }, round - 1)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StagePup(when (lastHeard) { Loudness.LOUD -> Pose.SURPRISED; Loudness.SOFT -> Pose.LISTENING; else -> Pose.STANDING },
                    size = (roomH * 0.18f).coerceIn(60.dp, 110.dp), fx = st.fx)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            // The big star: a burst for loud, a tiny twinkle for quiet.
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = when (lastHeard) {
                Loudness.LOUD -> "A big loud star"; Loudness.SOFT -> "A tiny quiet star"; else -> "A star" } }) {
                val c = Offset(size.width / 2, size.height / 2)
                val g = glow.value
                val r = size.minDimension * (0.08f + 0.28f * g)
                drawCircle(Brush.radialGradient(listOf(PupGold.copy(alpha = 0.55f * g + 0.1f), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
                if (g > 0.7f) for (k in 0 until 12) {
                    val a = 2 * PI * k / 12
                    drawLine(PupGold.copy(alpha = (g - 0.7f) * 3f), c + Offset((r * 1.3f * cos(a)).toFloat(), (r * 1.3f * sin(a)).toFloat()),
                        c + Offset((r * 2f * cos(a)).toFloat(), (r * 2f * sin(a)).toFloat()), 4.dp.toPx(), cap = StrokeCap.Round)
                }
                drawPath(star(c, r, inner = 0.45f), PupGold)
            }
            Meter(lastLevel, tune.anchorDb, offset, Modifier.fillMaxWidth().padding(vertical = 6.dp))
            BigKeys(57, 67, "Piano keys from A3 to G4. Play middle C, loud or quiet.", Modifier.fillMaxWidth().height((roomH * 0.26f).coerceIn(100.dp, 170.dp)),
                fill = { m -> if (m in st.demoKeys) PupGold else if (m == 60) PupGold.copy(alpha = 0.3f) else null },
                last = run.last, fx = st.fx,
                onKey = { m ->
                    val want = game.rounds.getOrNull(run.done)
                    if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) {
                        deps.sampler.noteOn(m, when (want) { Loudness.SOFT -> 28; Loudness.LOUD -> 118; else -> 72 }); scope.launch { delay(600); deps.sampler.noteOff(m) }
                    }
                    // A tap is as loud or as quiet as the round asks (the screen cannot tell).
                    val base = tune.anchorDb ?: -32.0
                    st.tap(m, riseDb = when (want) { Loudness.SOFT -> base - 8; Loudness.LOUD -> base + 8; else -> base })
                })
        }
        if (cheer > 0) Confetti(cheer, Modifier.fillMaxSize())
    }
}
