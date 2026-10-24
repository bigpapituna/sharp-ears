package com.bigpapituna.sharpears.ui.pup

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.pup.PupMotion
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.reducedMotion
import kotlinx.coroutines.delay

/**
 * Debug only (2.10): `--es screen pup` puts the rig on its own for QA, every mood on a button. Options, for screen
 * recordings: `--es pose <name>` (default welcome), `--es mood <name>` (held, or a beat played on launch), `--ei every <ms>`
 * (play that beat again every so often), `--ei size <dp>` (default 220), `--ei bpm <n>`, `--es clip jump|dance|wave|hop`
 * (the clip slot instead of the rig), `--es theme light|dark`, `--ef slow n` (everything n times slower, for a recording),
 * `--es act blink|flick` (one every `every` ms, 2 s by default).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PupDebug(intent: Intent, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val dark = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    fun <T : Enum<T>> pick(all: List<T>, name: String?, default: T) = all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
    var pose by remember { mutableStateOf(pick(Pose.entries, intent.getStringExtra("pose"), Pose.WELCOME)) }
    val first = remember { intent.getStringExtra("mood")?.let { m -> PupMood.entries.firstOrNull { it.name.equals(m, ignoreCase = true) } } }
    var held by remember { mutableStateOf(first?.takeIf { !it.beat }) }
    var size by remember { mutableIntStateOf(intent.getIntExtra("size", 220)) }
    var bpm by remember { mutableIntStateOf(intent.getIntExtra("bpm", PupMotion.CHEER_BPM)) }
    var clip by remember { mutableStateOf(intent.getStringExtra("clip")) }
    var notes by remember { mutableIntStateOf(0) }
    val every = intent.getIntExtra("every", 0)
    remember { PupClock.slow = intent.getFloatExtra("slow", 1f).coerceIn(1f, 20f); 0 }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PupClock.slow = 1f } }
    val pup = rememberPupController(idle = false)
    val still = reducedMotion()
    LaunchedEffect(Unit) {
        Log.d(TAG, "pup debug: pose $pose, mood $first, every $every ms, size $size dp, animations ${if (still) "off" else "on"}")
        val act = intent.getStringExtra("act")
        if (act == "blink" || act == "flick") {
            delay(900)
            while (true) { if (act == "blink") pup.blinks++ else pup.flicks++; delay(if (every > 0) every.toLong() else 2000L) }
        }
        val beat = first?.takeIf { it.beat } ?: return@LaunchedEffect
        delay(900)
        pup.play(beat)
        if (every > 0) while (true) { delay(every.toLong()); pup.play(beat) }
    }
    LaunchedEffect(held) { pup.mood = held }
    LaunchedEffect(bpm) { pup.bpm = bpm }
    SharpEarsTheme(dark = dark) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("The pup's rig", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("${pose.name.lowercase()}, ${(held ?: pose.mood).name.lowercase()}" + if ((held ?: pose.mood) == PupMood.CHEER) " at $bpm bpm" else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height((size * 1.45f).dp), contentAlignment = Alignment.BottomCenter) {
                val c = clip
                if (c != null) PupClip(c, size = size.dp, fallback = pose)
                else Pup(pose, held ?: pose.mood, size = size.dp, controller = pup, onTap = {}, idle = false)
            }
            Text("Beats", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in PupMood.entries.filter { it.beat }) OutlinedButton(onClick = { clip = null; pup.play(m) }) { Text(m.name.lowercase()) }
                OutlinedButton(onClick = { Juice.onStrike(48 + (notes++ % 4) * 12) }) { Text("note") }
                OutlinedButton(onClick = { pup.blinks++ }) { Text("blink") }
                OutlinedButton(onClick = { pup.flicks++ }) { Text("flick") }
            }
            Text("Held", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(held == null, onClick = { held = null }, label = { Text("the pose's") })
                for (m in PupMood.entries.filter { !it.beat }) FilterChip(held == m, onClick = { clip = null; held = m }, label = { Text(m.name.lowercase()) })
                OutlinedButton(onClick = { held = PupMood.POINT; pup.pointAt = -1f }) { Text("point left") }
                OutlinedButton(onClick = { held = PupMood.POINT; pup.pointAt = 1f }) { Text("point right") }
                OutlinedButton(onClick = { pup.lean = -pup.lean }) { Text("listen the other way") }
                OutlinedButton(onClick = { bpm = (bpm - 10).coerceAtLeast(40) }) { Text("slower") }
                OutlinedButton(onClick = { bpm = (bpm + 10).coerceAtMost(200) }) { Text("faster") }
            }
            Text("Clips", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(clip == null, onClick = { clip = null }, label = { Text("the rig") })
                for (c in listOf(PupClips.JUMP, PupClips.DANCE, PupClips.WAVE, PupClips.HOP))
                    FilterChip(clip == c, onClick = { clip = c }, label = { Text(c) })
            }
            Text("Size", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in listOf(48, 88, 160, 220)) FilterChip(size == s, onClick = { size = s }, label = { Text("$s") })
            }
            Text("Pose", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in Pose.entries) FilterChip(p == pose, onClick = { pose = p }, label = { Text(p.name.lowercase().replace('_', ' ')) })
            }
        }
    }
}
