package com.bigpapituna.sharpears.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.ui.DialogBarsFollowTheme
import com.bigpapituna.sharpears.ui.paper

/**
 * "Family" from the kids' grown-ups corner: the Family section of Settings on its own, behind the parent gate (the corner
 * already asked it). Pick a child and there is "Read the brief": grown-ups corner > Family > Mia, the path the brief's emails
 * give for each suggestion (the emails themselves are set in Settings > Briefs and email since 2.8). Closing it is the
 * caller's (it shuts the gate on a kid's screen).
 */
@Composable
fun FamilyDialog(profiles: Profiles, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogBarsFollowTheme()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Gated(onCancel = onClose) {
                BackHandler(onBack = onClose)
                Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                        Text("Family", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockNowRow(onLocked = onClose)          // 2.14: Lock now shuts the gate and Family with it
                        FamilySettings(profiles)
                    }
                }
            }
            ParentGateHost()
        }
    }
}
