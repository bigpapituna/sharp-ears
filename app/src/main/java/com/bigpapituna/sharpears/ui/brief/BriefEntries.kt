package com.bigpapituna.sharpears.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.DialogBarsFollowTheme
import com.bigpapituna.sharpears.ui.family.Gated
import com.bigpapituna.sharpears.ui.family.ParentGateHost
import com.bigpapituna.sharpears.ui.family.PupAvatar

/*
 * The doors into the brief. Each is a full screen dialog, so none needs a new Screen in MainActivity: put the composable
 * where the entry should be (ui/brief/INTEGRATION.md has the list).
 */

/**
 * The parent corner's brief: [children] as avatar tabs, behind the parent gate. Leaving the gate or pressing Back calls
 * [onClose]. [initial] opens a child's tab and [period] a tab (the weekly notification opens that child's week).
 */
@Composable
fun ParentBriefDialog(
    env: BriefEnv, children: List<Profile>, onClose: () -> Unit, initial: String? = null, period: Period = Period.WEEK,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Gated(onCancel = onClose) { ParentBriefScreen(env, children, onBack = onClose, initialChild = initial, initialPeriod = period) }
            // An action card asks the gate again once it has shut: drawn in this window, over the brief (not under it).
            ParentGateHost()
        }
    }
}

/** "My week" for a grown-up: the same screens for the current person, no gate, no avatar tabs. */
@Composable
fun MyWeekDialog(env: BriefEnv, me: Profile, onClose: () -> Unit, period: Period = Period.WEEK) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ParentBriefScreen(env, listOf(me), onBack = onClose, initialPeriod = period, showChildren = false, title = "My week")
            ParentGateHost()
        }
    }
}

/**
 * The You tab's "My week" card for a grown-up (the same pattern as the world and paw cards): a tap opens [MyWeekDialog].
 * It shows nothing written, so showing the tab never calls a writer; the week is read when the card is opened.
 */
@Composable
fun MyWeekCard(env: BriefEnv, me: Profile, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Open my week") { open = true }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PupAvatar(me.avatar, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text("My week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("How your playing went, and one or two things to try.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp).rotate(-90f))
    }
    if (open) MyWeekDialog(env, me, onClose = { open = false })
}
