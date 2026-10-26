package com.bigpapituna.sharpears.ui.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.motion.pressScale
import com.bigpapituna.sharpears.ui.play.Toy

/**
 * The kid home's Play button (2.2): a sheet with the three toys as big tiles, each the pup doing it (dancing for the
 * Jam, pointing for Pup says, drumming for Clap along), one word under him. No score anywhere; a tap opens the toy.
 * 2.7: [onGames] adds a wide Games tile under them, three of the arcade's pictures (ui/arcade).
 * 2.14: [closed] (today's time is up, or wind down closed Songs and the Jam): every tile is a sleeping pup and "Tomorrow";
 * a tap only calls [onClosed] (the pup says why).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidPlaySheet(onToy: (Toy) -> Unit, onDismiss: () -> Unit, onGames: (() -> Unit)? = null, closed: Boolean = false, onClosed: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Let's play!", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Toy.entries.forEach { t ->
                    Column(Modifier.weight(1f).height(196.dp).pressScale().clip(RoundedCornerShape(24.dp))
                        .background(if (closed) c.surfaceContainer else c.surfaceContainerHigh)
                        .clickable(role = Role.Button, onClickLabel = if (closed) "${t.title}, tomorrow" else "Open ${t.title}") { if (closed) onClosed() else onToy(t) }
                        .padding(vertical = 14.dp, horizontal = 6.dp)
                        .semantics(mergeDescendants = true) { },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                        Pup(if (closed) com.bigpapituna.sharpears.ui.pup.Pose.SLEEPING else t.pose, size = if (closed) 96.dp else 120.dp, idle = false)
                        Text(t.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1)
                        if (closed) Text(com.bigpapituna.sharpears.engine.skin.KidDoors.TOMORROW, style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                }
            }
            if (onGames != null) Row(Modifier.fillMaxWidth().height(132.dp).pressScale().clip(RoundedCornerShape(24.dp))
                .background(if (closed) c.surfaceContainer else c.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = if (closed) "Games, tomorrow" else "Open the games", onClick = if (closed) onClosed else onGames).padding(12.dp)
                .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (closed) Pup(com.bigpapituna.sharpears.ui.pup.Pose.SLEEPING, size = 96.dp, idle = false)
                else com.bigpapituna.sharpears.ui.arcade.GamesPicture(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.then(if (closed) Modifier.weight(1f) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Games", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1)
                    if (closed) Text(com.bigpapituna.sharpears.engine.skin.KidDoors.TOMORROW, style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }
        }
    }
}
