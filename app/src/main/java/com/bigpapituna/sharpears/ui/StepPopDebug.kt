package com.bigpapituna.sharpears.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.lessons.PopVariant
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.rememberPupController

/**
 * Debug only (2.11): `--es screen steppop` fires the step pop for QA through the same [StepPops.fire] a lesson uses (the mic
 * rule, the log line `step pop: streak N, chime PLAY|PLAY_DEAF|SKIP, <variant>`), with the pup's beat beside it. Streak 1,
 * 3 and 6 roll the picture as a lesson does (the chime in C: C E G, E G B, A C E); Burst, Confetti, Rainbow and Paw show
 * that one at the streak last chosen. No tester event. Reads no launch extras (DebugRoutesWiringTest); `--ef fxSlow n` (read
 * by MainActivity) slows the pictures for a recording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepPopDebug(sampler: Sampler, mic: MicInput, prefs: Prefs, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    SharpEarsTheme {
        val pops = rememberStepPops(sampler, mic, sound = { prefs.sound }, key = Unit, tonic = 0, telemetry = false)
        val pup = rememberPupController(idle = false)
        val still = reducedMotion()
        val area = remember { mutableStateOf<Rect?>(null) }
        val at = remember { mutableStateOf(Offset.Zero) }
        val streak = remember { androidx.compose.runtime.mutableIntStateOf(1) }
        val rolls = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        fun pop(force: PopVariant? = null) {
            val cue = pops.fire(streak.intValue, StepPopRules.seedOf("debug", rolls.intValue++), force)
            if (!still) pup.play(StepPopRules.pupBeat(cue.variant))
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { at.value = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step pop", style = MaterialTheme.typography.headlineSmall)
                PupSays(Pose.GREAT_JOB, "Fire a pop at a streak.", controller = pup, size = 72.dp, idle = false)
                // A stand-in for the step: the dark raised panel a lesson's player sits in.
                Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(StageRaised)
                    .onGloballyPositioned { area.value = it.boundsInRoot() })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (n in listOf(1, 3, 6)) OutlinedButton(onClick = { streak.intValue = n; pop() }) { Text("Streak $n") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (v in PopVariant.entries) OutlinedButton(onClick = { pop(v) }) { Text(v.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
                val last = pops.cue
                Text((if (last == null) "Nothing yet" else "Streak ${last.streak}, ${last.variant.name.lowercase()}, chime ${last.chime.name}") +
                    if (prefs.sound) "." else ". App sound is off.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StepPop(pops.cue, Modifier.fillMaxSize(), area = { area.value?.translate(-at.value) })
        }
    }
}
