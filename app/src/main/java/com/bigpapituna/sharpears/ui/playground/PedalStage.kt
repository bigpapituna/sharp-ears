package com.bigpapituna.sharpears.ui.playground

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.playground.NoteJudge
import com.bigpapituna.sharpears.engine.playground.PageLines
import com.bigpapituna.sharpears.engine.playground.PlaygroundLines
import com.bigpapituna.sharpears.engine.playground.RoundCoach
import com.bigpapituna.sharpears.engine.playground.StageKeys
import com.bigpapituna.sharpears.engine.playground.WorkshopLines
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.ui.theory.rememberStageInk
import com.bigpapituna.sharpears.ui.theory.softFoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the pup's play back with the pedal rings before the foot lifts. */
private const val RING_MS = 2_200L

/**
 * 2.3, the workshop's pedal ([com.bigpapituna.sharpears.engine.playground.Game.PEDAL]): a little tune a round (C, E, G
 * up, then down). The pup shows it the usual way, each note let go (without the pedal), the kid plays it back note by
 * note, and then the pup plays the kid's own tune again, at the kid's own pace, with the pedal down: the soft foot
 * presses the pedal under the keys and every note rings on together until the foot lifts. Playing is never graded on the
 * pedal (that needs MIDI): the round is the notes, the pedal is what the kid hears. A tune let go after three tries is
 * played back with the pedal too.
 */
@Composable
internal fun PedalStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    val ex = step.exercise ?: return
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val foot = remember { Animatable(0f) }
    var ringing by remember { mutableStateOf<List<Int>>(emptyList()) }
    var replays by remember { mutableIntStateOf(0) }
    // The kid's notes in this round and when they came, for the play back at their own pace.
    val heard = remember { mutableListOf<Pair<Int, Long>>() }
    val lastTune = remember { arrayOf<List<Int>>(emptyList()) }
    val holder = remember { arrayOfNulls<StageState>(1) }

    /** The pup plays [keys] back with the pedal down, [gaps] ms apart, the mic deaf while it rings. */
    fun playBack(keys: List<Int>, gaps: List<Long>) {
        val st = holder[0] ?: return
        scope.launch {
            delay(450)
            val total = gaps.sum() + RING_MS + 600
            deps.mic.deafFor(total)
            st.call(WorkshopLines.REPLAY)
            delay(1_300)
            Log.d(TAG, "playground: pedal play back ${keys.map(::noteName)}")
            launch { if (still) foot.snapTo(1f) else foot.animateTo(1f, tween(180)) }
            delay(220)
            for ((i, k) in keys.withIndex()) {
                if (deps.prefs.sound) deps.sampler.noteOn(k, 82)
                ringing = ringing + k
                st.fx.ripple(k, PupGold, fill = true)
                delay(gaps.getOrElse(i) { 450L })
            }
            delay(RING_MS)
            keys.forEach { deps.sampler.noteOff(it) }
            ringing = emptyList()
            if (still) foot.snapTo(0f) else foot.animateTo(0f, tween(260))
            replays++
        }
    }

    val st = rememberStage(ex, "$lessonId-pedal", lessonId, "pedal", deps, inject, onEnded,
        keys = { StageKeys.FIVE },
        demoKeys = { run -> run.rounds.demoKeys() },
        judge = { s, run -> NoteJudge.judge(s, run.rounds.expectedKey()) },
        foundLine = { run, _ -> if (run.finished) WorkshopLines.PEDAL_DONE else null },
        missLine = { _, _ -> PageLines.NEXT_NOTE },
        onFound = { k ->
            heard += k to SystemClock.uptimeMillis()
            val r = holder[0]?.run
            // The tune's last note: play it back with the pedal, at the kid's pace (each gap kept between a third and one second).
            if (r != null && r.rounds.doneInRound == r.rounds.roundGroups.size - 1) {
                val keys = r.rounds.roundGroups.map { it.notes.first().midi }
                val times = heard.takeLast(keys.size).map { it.second }
                val gaps = times.zipWithNext { a, b -> (b - a).coerceIn(330L, 1_000L) } + 450L
                heard.clear()
                playBack(keys, gaps)
            }
        },
        // A tune let go: the pup still plays it with the pedal, evenly.
        onLetGo = { heard.clear(); lastTune[0].takeIf { it.isNotEmpty() }?.let { playBack(it, List(it.size) { 450L }) } },
        coach = { RoundCoach(firstDemoMs = 1_300 + 3 * 450L + 900, demoMs = 3 * 450L + 900, againDemoMs = 1_000 + 3 * 450L + 900) },
        phrases = true, demoGapMs = 450,
        // Room for the play back between one tune and the next.
        gapMs = 6_200,
    )
    holder[0] = st
    val run = st.run
    val demo = st.demoKeys
    LaunchedEffect(demo) { if (demo.isNotEmpty()) { lastTune[0] = demo; heard.clear() } }
    val line = stageLine(st, when {
        run.finished -> WorkshopLines.PEDAL_DONE
        ringing.isNotEmpty() -> WorkshopLines.REPLAY
        demo.isNotEmpty() -> WorkshopLines.DRY
        run.phase == RoundCoach.Phase.LISTEN -> PlaygroundLines.YOUR_TURN
        else -> null
    })
    val ink = rememberStageInk()
    val colours = com.bigpapituna.sharpears.ui.skin.colourNotesOn()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomH = maxHeight
        Canvas(Modifier.fillMaxSize()) { workshopRoom(0.95f) }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            StageTop(run.count, run.done, letGo = run.letGo)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PaintedPup(when {
                    run.finished -> Painted.GREAT_JOB
                    ringing.isNotEmpty() -> Painted.DANCING
                    demo.isNotEmpty() -> Painted.POINTING
                    else -> Painted.LISTENING
                }, size = (roomH * 0.18f).coerceIn(60.dp, 110.dp), poke = st.fx.poke)
                Spacer(Modifier.width(8.dp))
                StageLine(line)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                BigKeys(57, 71, "Piano keys from A3 to B4. The tune uses C, E and G.", Modifier.fillMaxSize(),
                    fill = { m -> if (m in ringing) PupGold.copy(alpha = 0.7f) else if (m == st.demoIndex?.let { demo.getOrNull(it) }) PupGold
                        else if (colours && m in StageKeys.FIVE) com.bigpapituna.sharpears.ui.skin.noteColour(m).copy(alpha = 0.45f) else null },
                    last = run.last, fx = st.fx,
                    onKey = { m ->
                        if (deps.prefs.sound && (!st.listening || deps.mic.headphonesOn())) { deps.sampler.noteOn(m, 90); scope.launch { delay(600); deps.sampler.noteOff(m) } }
                        st.tap(m)
                    })
            }
            // The foot by the pedal, under the keys: it presses while the pup's play back rings.
            Canvas(Modifier.fillMaxWidth().height((roomH * 0.26f).coerceIn(90.dp, 170.dp)).padding(top = 6.dp)
                .semantics { contentDescription = if (foot.value > 0.5f) "The foot presses the pedal" else "The foot rests by the pedal" }) {
                softFoot(ink, Offset.Zero, size.width, size.height, foot.value)
            }
        }
    }
}
