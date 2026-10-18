package com.bigpapituna.sharpears.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind

/** What a profile is, in a few words: "Kid, reads", "Kid, not reading yet", "Grown-up", "Grown-up, this phone's owner". */
fun kindLabel(p: Profile) = when {
    p.isKid && p.reader -> "Kid, reads"
    p.isKid -> "Kid, not reading yet"
    p.isOwner -> "Grown-up, this phone's owner"
    else -> "Grown-up"
}

/**
 * The Family section of Settings: everyone on the phone, with rename, reader, grown-up or kid mode and delete, plus
 * "Add someone". Every change goes through the parent gate ([ParentGateController.guard]), also for grown-ups: it is
 * about kids reaching settings on a shared phone. Delete asks twice. Adding here does not switch who is playing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilySettings(profiles: Profiles, gate: ParentGateController = LocalParentGate.current) {
    val roster by profiles.rosterFlow.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Pair<Profile, Int>?>(null) }      // the profile and which ask (1, then 2)
    var adding by rememberSaveable { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Text("Everyone who plays on this phone. Each has their own progress, goal and stickers; the microphone setup is shared.",
        style = MaterialTheme.typography.bodyMedium, color = dim)
    roster.profiles.forEach { p ->
        val expanded = open == p.id
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = if (expanded) "Hide" else "Change") {
            open = if (expanded) null else p.id
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PupAvatar(p.avatar, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name + if (p.id == roster.currentId) " (playing now)" else "", style = MaterialTheme.typography.titleSmall)
                Text(kindLabel(p), style = MaterialTheme.typography.bodySmall, color = dim)
            }
            Text(if (expanded) "Hide" else "Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) FlowRow(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { gate.guard { renaming = p } }) { Text("Rename") }
            if (p.isKid) {
                TextButton(onClick = { gate.guard { profiles.update(p.id) { it.copy(reader = !it.reader) } } }) {
                    Text(if (p.reader) "Not reading yet" else "Reads now")
                }
                TextButton(onClick = { gate.guard { profiles.setKind(p.id, ProfileKind.ADULT) } }) { Text("Switch to grown-up mode") }
            } else if (!p.isOwner) {
                TextButton(onClick = { gate.guard { profiles.setKind(p.id, ProfileKind.KID) } }) { Text("Switch to kid mode") }
            }
            if (!p.isOwner) TextButton(onClick = { gate.guard { deleting = p to 1 } }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (!profiles.isFull) TextButton(onClick = { gate.guard { adding = true } }) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp)); Text("Add someone")
    } else Text("Eight people is the most one phone holds.", style = MaterialTheme.typography.bodySmall, color = dim)

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename ${p.name}") },
            text = { OutlinedTextField(name, { name = it.take(Profile.MAX_NAME) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = Profile.cleanName(name).isNotEmpty(), onClick = { profiles.rename(p.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
    deleting?.let { (p, ask) ->
        if (ask == 1) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${p.name}?") },
            text = { Text("${p.name}'s progress, practice time and stickers are removed from this phone.") },
            confirmButton = { TextButton(onClick = { deleting = p to 2 }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
        else AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Are you sure?") },
            text = { Text("This cannot be undone. Export progress first if you might want ${p.name}'s back.") },
            confirmButton = { TextButton(onClick = { profiles.delete(p.id); deleting = null; open = null }) {
                Text("Delete ${p.name} for good", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
    if (adding) Dialog(onDismissRequest = { adding = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NewProfileFlow(onDone = { profiles.create(it, switch = false); adding = false }, onCancel = { adding = false })
        }
    }
}
