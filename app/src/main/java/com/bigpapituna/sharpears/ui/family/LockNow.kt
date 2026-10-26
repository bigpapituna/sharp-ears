package com.bigpapituna.sharpears.ui.family

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import kotlinx.coroutines.delay

/**
 * 2.14 (Priya's P4: "I found no lock now"): the first row of every grown-ups' screen while the parent gate is open. "Lock
 * now" closes the gate at once and [onLocked] leaves the screen when a kid is playing (a screen asked at its door, such as
 * Settings, would stay usable otherwise). The row goes by itself when the gate's two minutes run out.
 */
@Composable
fun LockNowRow(modifier: Modifier = Modifier, gate: ParentGateController = LocalParentGate.current, onLocked: () -> Unit = {}) {
    val closesAt = gate.closesAt()
    var shown by remember(closesAt) { mutableStateOf(closesAt > 0L) }
    LaunchedEffect(closesAt) {
        if (closesAt <= 0L) return@LaunchedEffect
        delay((closesAt - gate.now()).coerceAtLeast(0L))
        shown = false
    }
    if (!shown) return
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp))
        .padding(start = 14.dp, end = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_profile_lock), contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        Text(LOCK_LINE, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { gate.close(); Log.d(TAG, "gate: locked now"); onLocked() }) { Text(LOCK_NOW) }
    }
}

/** The row's words. */
const val LOCK_LINE = "The grown-ups' corner is open."
const val LOCK_NOW = "Lock now"
