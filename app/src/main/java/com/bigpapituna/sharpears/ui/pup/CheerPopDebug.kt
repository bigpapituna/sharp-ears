package com.bigpapituna.sharpears.ui.pup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.lessons.CheerEntry
import com.bigpapituna.sharpears.engine.lessons.CheerSide
import com.bigpapituna.sharpears.engine.lessons.CheerTrigger
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.StageRaised

/**
 * Debug only (2.13): `--es screen cheerpop` fires the cheer pops for QA through the same [CheerPops] a lesson uses (the mic
 * and voice rules, the sparkle, the log line `cheer: <trigger> <audience> "<line>" voice=<mode>`), over a stand-in lesson: the
 * header it pops over, the pup's instruction and the dark step under it. One button per trigger, Kid or Grown-up, each way
 * in (Slide, Pop up, Peek, or Roll as a lesson does), and Lesson or Song (the song player's lane, whose two top corners
 * alternate). No tester event; the waits do not apply. Reads no launch extras (DebugRoutesWiringTest); `--ef fxSlow n` (read
 * by MainActivity) slows the pop for a recording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheerPopDebug(sampler: Sampler, mic: MicInput, voice: Voice, prefs: Prefs, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    SharpEarsTheme {
        val kid = remember { mutableStateOf(true) }
        val entry = remember { mutableStateOf<CheerEntry?>(null) }
        val song = remember { mutableStateOf(false) }
        val cheers = rememberCheerPops(sampler, mic, voice, prefs, kid.value, key = "debug", telemetry = false)
        val header = remember { mutableStateOf<Rect?>(null) }
        val lane = remember { mutableStateOf<Rect?>(null) }
        val at = remember { mutableStateOf(Offset.Zero) }
        val turn = remember { mutableStateOf(false) }
        val passed = remember { mutableStateOf<Pair<Int, String>?>(null) }
        fun fire(t: CheerTrigger) {
            // The song's lane alternates its corners as a song does; a lesson's header has the one.
            val place: ((com.bigpapituna.sharpears.engine.lessons.Cheer) -> CheerSide?)? =
                if (song.value) ({ c -> if (c.other) CheerSide.START else CheerSide.END }) else null
            cheers.debugFire(t, entry.value, CheerSituation(betweenNotes = !turn.value, place = place))
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { at.value = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // The stand-in header: a back arrow, a title, the step dots (what a lesson's pop sits over).
                Column(Modifier.fillMaxWidth().onGloballyPositioned { header.value = it.boundsInRoot() }) {
                    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.padding(12.dp))
                        Text("Cheer pops", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { i -> Box(Modifier.height(8.dp).width(if (i == 1) 22.dp else 8.dp).clip(CircleShape)
                            .background(if (i == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
                    }
                }
                PupSays(com.bigpapituna.sharpears.ui.pup.Pose.PRACTICING, if (kid.value) "Play C, D, E with me!" else "Right hand, the notes wait.",
                    size = 56.dp, idle = false)
                // The step: the dark raised panel a lesson's player sits in (a song's lane when Song is chosen).
                Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(StageRaised)
                    .onGloballyPositioned { lane.value = it.boundsInRoot() })
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kid.value, { kid.value = true }, { Text("Kid") })
                        FilterChip(!kid.value, { kid.value = false }, { Text("Grown-up") })
                        Spacer(Modifier.size(8.dp))
                        FilterChip(!song.value, { song.value = false }, { Text("Lesson") })
                        FilterChip(song.value, { song.value = true }, { Text("Song") })
                        Spacer(Modifier.size(8.dp))
                        FilterChip(turn.value, { turn.value = !turn.value }, { Text("Learner's turn") })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(entry.value == null, { entry.value = null }, { Text("Roll") })
                        for (e in CheerEntry.entries) FilterChip(entry.value == e, { entry.value = e }, {
                            Text(when (e) { CheerEntry.SLIDE -> "Slide"; CheerEntry.POP_UP -> "Pop up"; CheerEntry.PEEK -> "Peek" })
                        })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in CheerTrigger.entries) OutlinedButton(onClick = {
                            // A step passed is the step pop's bubble over the step, as a lesson shows it.
                            if (t == CheerTrigger.STEP_PASSED) cheers.stepPassed()?.let { passed.value = (passed.value?.first ?: 0) + 1 to it }
                            else fire(t)
                        }) { Text(t.wire.replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                    }
                    val last = cheers.cue
                    Text(if (last == null) "Nothing yet" else "${last.cheer.trigger.wire}, ${last.cheer.entry.name.lowercase()}, " +
                        "${last.side.name.lowercase()}${if (last.fromTop) " from the top" else ""}, voice ${last.voice.name}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            StepPopLine(passed.value?.first, passed.value?.second, area = { lane.value?.translate(-at.value) }, Modifier.fillMaxSize())
            // As a lesson draws it over its header, or as a song draws it in the lane's top band.
            if (song.value) CheerPop(cheers.cue, Modifier.inRect {
                lane.value?.let { r -> Rect(r.left, r.top, r.right, r.top + r.height * com.bigpapituna.sharpears.engine.lessons.CheerPlace.LANE_BAND).translate(-at.value) }
            }, maxPupDp = 48f)
            else CheerPop(cheers.cue, Modifier.inRect { header.value?.translate(-at.value) })
        }
    }
}
