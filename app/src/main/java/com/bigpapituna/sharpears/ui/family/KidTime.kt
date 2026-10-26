package com.bigpapituna.sharpears.ui.family

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.skin.Bedtime
import com.bigpapituna.sharpears.engine.skin.TimeCap
import com.bigpapituna.sharpears.ui.skin.BedtimeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 2.14, a kid's time in the grown-ups' corner (Priya's P6), behind the parent gate: the daily time (Off, 10, 15, 20, 30 or
 * 45 min on screen; engine/skin/TimeCap.kt) with what is left today, then "After the goal: wind down" with its honest line
 * and, when on, "Close Songs and the Jam too". Each change asks the gate (it is open already in the corner, and each use
 * keeps it open two more minutes) and is logged for the brief ("capMin", "bedtime", "bedtimeSongs"). [onChanged] lets the
 * app read the kid's day again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KidTimeControls(kid: Profile, modifier: Modifier = Modifier, gate: ParentGateController = LocalParentGate.current, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(kid.id) { BedtimeStore(context.applicationContext, kid.id) }
    var cap by remember(kid.id) { androidx.compose.runtime.mutableIntStateOf(store.capMin) }
    var windDown by remember(kid.id) { mutableStateOf(store.bedtime) }
    var closeSongs by remember(kid.id) { mutableStateOf(store.closeSongs) }
    var left by remember(kid.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(kid.id, cap) {
        left = if (cap <= 0) null else withContext(Dispatchers.IO) { runCatching { TimeCap.minutesLeft(cap, Briefs.screenMsToday(kid.id)) }.getOrNull() }
    }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Daily time for ${kid.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(TimeCap.ABOUT, style = MaterialTheme.typography.bodySmall, color = dim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeCap.CHOICES.forEach { m ->
                FilterChip(selected = cap == m, onClick = { gate.guard {
                    store.capMin = m; cap = m; onChanged()
                    Log.d(TAG, "time: ${kid.id}'s daily time ${TimeCap.label(m)}")
                } }, label = { Text(TimeCap.label(m)) })
            }
        }
        left?.let { l -> Text(if (l <= 0) TimeCap.DONE_TODAY else "$l min left today.", style = MaterialTheme.typography.bodySmall, color = dim) }
        KidSwitch(Bedtime.LABEL, if (windDown && closeSongs) Bedtime.LINE_ALL else Bedtime.LINE, windDown) { v ->
            gate.guard { store.bedtime = v; windDown = v; onChanged(); Log.d(TAG, "time: ${kid.id}'s wind down after the goal $v") }
        }
        if (windDown) KidSwitch(Bedtime.SONGS_SWITCH, null, closeSongs) { v ->
            gate.guard { store.closeSongs = v; closeSongs = v; onChanged(); Log.d(TAG, "time: ${kid.id}'s songs close after the goal $v") }
        }
    }
}

@Composable
private fun KidSwitch(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles, as Settings' switches do.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}
