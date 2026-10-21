package com.bigpapituna.sharpears.ui.brief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bigpapituna.sharpears.data.BriefAi
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.ParentGateController

/**
 * The parent corner's AI brief switches (Settings > Family, under everyone): "AI briefs" (on by default on a build with a key;
 * off, every brief, card and answer is written on the phone) and "Write briefs on mobile data" (off by default: the AI then
 * writes only on Wi-Fi). Each change asks the parent gate, like everything else in Family. On a build without a key both are
 * shown off and say why.
 */
@Composable
fun BriefAiSettings(gate: ParentGateController = LocalParentGate.current) {
    val context = LocalContext.current.applicationContext
    val hasKey = BriefAi.hasKey
    var on by remember { mutableStateOf(BriefAi.switchOn(context)) }
    var mobile by remember { mutableStateOf(BriefAi.mobileData(context)) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Parent brief", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text("A brief on each person's playing, in Family and in the kids' corner. The words are written from numbers only: minutes, " +
            "stars, which lessons. Never a name, a recording or a note you typed.", style = MaterialTheme.typography.bodySmall, color = dim)
        SwitchRow("AI briefs",
            if (!hasKey) "This build has no AI key, so the phone writes every brief."
            else "An AI model writes the briefs, the pup's card and the answers. Off, or offline, the phone writes them.",
            checked = hasKey && on, enabled = hasKey) { v -> gate.guard { BriefAi.setOn(context, v); on = v } }
        SwitchRow("Write briefs on mobile data",
            "Off: the AI writes only on Wi-Fi, and the phone writes on mobile data.",
            checked = hasKey && on && mobile, enabled = hasKey && on) { v -> gate.guard { BriefAi.setMobileData(context, v); mobile = v } }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    // The whole row toggles (one switch for TalkBack), as in the rest of Settings.
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) c.onSurface else c.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
